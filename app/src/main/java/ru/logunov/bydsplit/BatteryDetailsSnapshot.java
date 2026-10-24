package ru.logunov.bydsplit;

/** Read-only battery metrics. Missing values are never interpreted as zero. */
final class BatteryDetailsSnapshot {
    final VehicleTelemetrySnapshot vehicle;
    final Integer minCellMv, maxCellMv;
    final Float lifetimeKwh, sessionKwh;
    final Integer bmsState;
    final Integer cellCount, temperatureSensorCount;
    BatteryDetailsSnapshot(VehicleTelemetrySnapshot vehicle, int[] raw) {
        this.vehicle = vehicle;
        Integer min = cell(raw[0]), max = cell(raw[1]);
        boolean ordered = min != null && max != null && min <= max;
        minCellMv = ordered ? min : null;
        maxCellMv = ordered ? max : null;
        lifetimeKwh = positiveFloat(raw[2]);
        sessionKwh = positiveFloat(raw[3]);
        bmsState = raw[4] >= 1 && raw[4] <= 20 ? raw[4] : null;
        Integer bodyCount = count(raw, 5), chargingCount = count(raw, 6);
        cellCount = bodyCount != null && chargingCount != null && !bodyCount.equals(chargingCount)
                ? null : bodyCount != null ? bodyCount : chargingCount;
        temperatureSensorCount = count(raw, 7);
    }
    Integer deltaMv() { return minCellMv == null ? null : maxCellMv - minCellMv; }
    private static Integer cell(int value) { return value >= 1500 && value <= 4500 ? value : null; }
    private static Integer count(int[] raw, int index) {
        // 0 means unavailable; 0x3ff and larger sentinel values are not counts.
        return index < raw.length && raw[index] > 0 && raw[index] < 1023 ? raw[index] : null;
    }
    private static Float positiveFloat(int raw) {
        float value = Float.intBitsToFloat(raw);
        return Float.isFinite(value) && value >= 0 && value <= 10000000 ? value : null;
    }
}
