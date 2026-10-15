package ru.logunov.bydsplit;

import java.util.Map;

/** Only quantities with established Di2 units are allowed onto the dashboard. */
final class Di2Snapshot {
    static VehicleTelemetrySnapshot vehicle(Map<String, Double> v) {
        return new VehicleTelemetrySnapshot(integer(v, "speedKmh"),
                integer(v, "pressure_fl"), integer(v, "pressure_fr"),
                integer(v, "pressure_rl"), integer(v, "pressure_rr"),
                null, null, null, null, null, null, null,
                v.containsKey("socPercent") ? v.get("socPercent").floatValue() : null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                ClimateIconState.decode(orUnknown(v, "power"), orUnknown(v, "fan"), orUnknown(v, "wind")));
    }
    static Integer integer(Map<String, Double> v, String key) {
        Double value = v.get(key);
        return value == null ? null : (int) Math.round(value);
    }
    private static int orUnknown(Map<String, Double> v, String key) {
        Integer value = integer(v, key); return value == null ? -1 : value;
    }
}
