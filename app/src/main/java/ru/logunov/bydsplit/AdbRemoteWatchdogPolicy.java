package ru.logunov.bydsplit;

final class AdbRemoteWatchdogPolicy {
    static final long COOLDOWN_MS = 120_000L;
    static boolean shouldRestart(boolean enabled, Boolean relayAlive, long now, long lastAttempt) {
        return enabled && Boolean.FALSE.equals(relayAlive)
                && (lastAttempt == 0 || now < lastAttempt || now - lastAttempt >= COOLDOWN_MS);
    }
}
