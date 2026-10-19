package ru.logunov.bydsplit;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One serialized retry chain for the lifetime of the launcher, including time in the background. */
final class AdbHelperRecovery implements AutoCloseable {
    interface Scheduler { void schedule(Runnable task, long delayMillis); }
    private final Scheduler scheduler;
    private final BooleanSupplier ensure;
    private final Runnable available;
    private final Consumer<Boolean> status;
    private boolean started;
    private volatile boolean closed;

    AdbHelperRecovery(Scheduler scheduler, BooleanSupplier ensure, Runnable available) {
        this(scheduler, ensure, available, connected -> {});
    }

    AdbHelperRecovery(Scheduler scheduler, BooleanSupplier ensure, Runnable available,
                      Consumer<Boolean> status) {
        this.scheduler = scheduler;
        this.ensure = ensure;
        this.available = available;
        this.status = status;
    }

    synchronized void start() {
        if (started || closed) return;
        started = true;
        scheduler.schedule(this::check, 0);
    }

    private void check() {
        if (closed) return;
        boolean ready = false;
        try { ready = ensure.getAsBoolean(); }
        catch (RuntimeException ignored) { /* A failed probe is retried like an unavailable ADB. */ }
        finally {
            // Completion-based scheduling prevents overlapping checks after a slow ADB handshake.
            synchronized (this) {
                if (!closed) {
                    status.accept(ready);
                    if (ready) available.run();
                    if (!closed) scheduler.schedule(this::check, ready ? 15000 : 5000);
                }
            }
        }
    }

    @Override public synchronized void close() { closed = true; }
}
