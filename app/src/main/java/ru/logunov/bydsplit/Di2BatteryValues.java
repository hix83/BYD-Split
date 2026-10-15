package ru.logunov.bydsplit;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DiLink 2 units verified on Seagull; never apply the Di5 temperature offset. */
final class Di2BatteryValues {
    private static final Pattern PARCEL = Pattern.compile(
            "(?m)^(SOH|MIN_TEMP|MAX_TEMP|VOLTAGE|MIN_CELL|MAX_CELL)=Result: Parcel\\(00000000\\s+([0-9a-fA-F]{8})");

    static Map<String, Double> parse(String raw) {
        Map<String, Double> result = new HashMap<>();
        Matcher matcher = PARCEL.matcher(raw);
        while (matcher.find()) {
            int bits = (int) Long.parseLong(matcher.group(2), 16);
            String label = matcher.group(1);
            boolean floating = label.equals("VOLTAGE") || label.endsWith("CELL");
            double value = floating ? Float.intBitsToFloat(bits) : bits;
            if (!Double.isFinite(value)) continue;
            switch (label) {
                case "SOH": put(result, "batterySohPercent", value, 1, 100); break;
                case "MIN_TEMP": put(result, "batteryMinTempC", value, -40, 80); break;
                case "MAX_TEMP": put(result, "batteryMaxTempC", value, -40, 80); break;
                case "VOLTAGE": put(result, "auxiliaryVoltage", value, 9, 18); break;
                case "MIN_CELL": put(result, "minCellMv", value * 1000, 1500, 4500); break;
                case "MAX_CELL": put(result, "maxCellMv", value * 1000, 1500, 4500); break;
                default: break;
            }
        }
        Double min = result.get("minCellMv"), max = result.get("maxCellMv");
        if (min != null && max != null && min > max) {
            result.put("minCellMv", max); result.put("maxCellMv", min);
        }
        return result;
    }

    private static void put(Map<String, Double> values, String key, double value, double min, double max) {
        if (value >= min && value <= max) values.put(key, value);
    }
}
