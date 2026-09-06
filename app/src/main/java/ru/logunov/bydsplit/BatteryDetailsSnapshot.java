package ru.logunov.bydsplit;

/** Read-only battery metrics. Missing values are never interpreted as zero. */
final class BatteryDetailsSnapshot {
    final VehicleTelemetrySnapshot vehicle;
    final Integer minCellMv, maxCellMv;
    final Float lifetimeKwh, sessionKwh;
    final Integer bmsState;
    BatteryDetailsSnapshot(VehicleTelemetrySnapshot vehicle, int[] raw) {
        this.vehicle = vehicle;
        Integer min = cell(raw[0]), max = cell(raw[1]);
        boolean ordered = min != null && max != null && min <= max;
        minCellMv = ordered ? min : null;
        maxCellMv = ordered ? max : null;
        lifetimeKwh = positiveFloat(raw[2]);
        sessionKwh = positiveFloat(raw[3]);
        bmsState = raw[4] >= 1 && raw[4] <= 20 ? raw[4] : null;
    }
    Integer deltaMv() { return minCellMv == null ? null : maxCellMv - minCellMv; }
    private static Integer cell(int value) { return value >= 1500 && value <= 4500 ? value : null; }
    private static Float positiveFloat(int raw) {
        float value = Float.intBitsToFloat(raw);
        return Float.isFinite(value) && value >= 0 && value <= 10000000 ? value : null;
    }
}
