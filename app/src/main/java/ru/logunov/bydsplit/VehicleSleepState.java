package ru.logunov.bydsplit;

/** Missing telemetry and a dark screen are never evidence that the car is OFF. */
final class VehicleSleepState {
    static final long OFF_DELAY_MS = 90000;
    enum Mode { UNKNOWN, RUNNING, CHARGING, OFF_PENDING, SLEEP }
    private Integer power, cable;
    private long offSince = -1;
    private long lastTime = -1;
    private Mode mode = Mode.UNKNOWN;

    Mode mode() { return mode; }
    long confirmationAt() { return offSince < 0 ? -1 : offSince + OFF_DELAY_MS; }

    Mode sample(long now, Integer powerState, Integer gunState) {
        if (lastTime > now) offSince = -1;
        lastTime = now;
        power = powerState != null && powerState >= 0 && powerState <= 2 ? powerState : null;
        cable = gunState != null && gunState >= 1 && gunState <= 4 ? gunState : null;
        if (Integer.valueOf(2).equals(power)) { offSince = -1; return mode = Mode.RUNNING; }
        // Preserve charging and cable-connected sessions even when current is missing or charging is paused.
        if (cable != null && cable >= 2) { offSince = -1; return mode = Mode.CHARGING; }
        // Once OFF was confirmed, losing the observer is not evidence of a wake.
        // READY, a connected cable or explicit ACC still pass through their normal paths.
        if(mode==Mode.SLEEP&&!Integer.valueOf(1).equals(power))return mode;
        if (!Integer.valueOf(0).equals(power) || !Integer.valueOf(1).equals(cable)) {
            offSince = -1;
            return mode = Mode.UNKNOWN;
        }
        if (offSince < 0) offSince = now;
        return mode = now - offSince >= OFF_DELAY_MS ? Mode.SLEEP : Mode.OFF_PENDING;
    }

    Mode confirm(long now) { return sample(now, power, cable); }
    Mode invalidate(long now) { return sample(now, null, null); }
}
