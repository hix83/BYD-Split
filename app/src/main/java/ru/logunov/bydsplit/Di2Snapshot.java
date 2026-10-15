package ru.logunov.bydsplit;

import java.util.Map;

/** Only quantities with established Di2 units are allowed onto the dashboard. */
final class Di2Snapshot {
    static VehicleTelemetrySnapshot vehicle(Map<String, Double> v) {
        return new VehicleTelemetrySnapshot(integer(v, "speedKmh"),
                integer(v, "pressure_fl"), integer(v, "pressure_fr"),
                integer(v, "pressure_rl"), integer(v, "pressure_rr"),
                null, null, null, null, integer(v,"driveMode"), null, null,
                v.containsKey("socPercent") ? v.get("socPercent").floatValue() : null,
                integer(v,"batteryPowerKw"), null, null, null, integer(v,"chargeGunState"), null,
                null, integer(v,"batterySohPercent"), integer(v,"batteryMinTempC"), integer(v,"batteryMaxTempC"), integer(v,"motorRpm"), null,
                v.containsKey("auxiliaryVoltage")?v.get("auxiliaryVoltage").floatValue():null,
                ClimateIconState.decode(orUnknown(v, "power"), orUnknown(v, "fan"), orUnknown(v, "wind")).withTargetTemperature(
                        v.containsKey("temperature") ? v.get("temperature").floatValue() : null));
    }
    static BatteryDetailsSnapshot battery(Map<String, Double> v) {
        int[] raw = {orUnknown(v, "minCellMv"), orUnknown(v, "maxCellMv"),
                Float.floatToIntBits(-1f), Float.floatToIntBits(-1f), -1};
        return new BatteryDetailsSnapshot(vehicle(v), raw);
    }
    static Integer integer(Map<String, Double> v, String key) {
        Double value = v.get(key);
        return value == null ? null : (int) Math.round(value);
    }
    private static int orUnknown(Map<String, Double> v, String key) {
        Integer value = integer(v, key); return value == null ? -1 : value;
    }
}
