package ru.logunov.bydsplit;

import java.util.ArrayDeque;

public final class AdbHelperRecoveryTest {
    private static final class Clock implements AdbHelperRecovery.Scheduler {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        long delay;
        public void schedule(Runnable task, long millis) { tasks.add(task); delay = millis; }
        void next() { tasks.remove().run(); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Clock clock = new Clock();
        boolean[] adb = {false};
        int[] probes = {0}, ready = {0};
        java.util.List<Boolean> states = new java.util.ArrayList<>();
        AdbHelperRecovery recovery = new AdbHelperRecovery(clock,
                () -> { probes[0]++; return adb[0]; }, () -> ready[0]++, states::add);
        recovery.start(); recovery.start();
        check(clock.tasks.size() == 1, "multiple starts must not duplicate the retry loop");
        // Hardware may enable ADB minutes after boot, beyond the former pane timeout.
        for (int i = 0; i < 60; i++) {
            clock.next();
            check(ready[0] == 0 && clock.delay == 5000, "no false recovery while ADB is absent");
        }
        adb[0] = true; clock.next();
        check(ready[0] == 1 && clock.delay == 15000, "late ADB recovers without recreating the controller");
        adb[0] = false; clock.next();
        check(ready[0] == 1 && clock.delay == 5000, "later outage resumes retry cadence");
        check(!states.get(states.size() - 1), "UI must receive disconnected state after a successful connection");
        adb[0] = true; clock.next();
        check(ready[0] == 2, "second recovery is delivered");
        check(states.get(states.size() - 1), "UI receives connected state on recovery");
        int stateCount = states.size();
        recovery.close(); int before = probes[0]; clock.next(); recovery.start();
        check(probes[0] == before && clock.tasks.isEmpty(), "destroyed owner must not probe or reschedule");
        check(states.size() == stateCount, "destroyed owner must not receive UI updates");

        Clock failing = new Clock();
        AdbHelperRecovery errors = new AdbHelperRecovery(failing,
                () -> { throw new IllegalStateException("transport disappeared"); },
                () -> { throw new AssertionError("failed probe reported as ready"); });
        errors.start(); failing.next();
        check(failing.tasks.size() == 1 && failing.delay == 5000, "probe exception must not stop recovery");
        errors.close(); failing.next();

        Clock closing = new Clock();
        AdbHelperRecovery[] owner = new AdbHelperRecovery[1];
        owner[0] = new AdbHelperRecovery(closing,
                () -> { owner[0].close(); return true; },
                () -> { throw new AssertionError("callback after owner closed during handshake"); });
        owner[0].start(); closing.next();
        check(closing.tasks.isEmpty(), "no callback/reschedule after close during a probe");
        System.out.println("ADB helper recovery: delayed startup, repeated outage, single loop and lifecycle passed");
    }
}
