package ru.logunov.bydsplit;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DiLink 2 units verified on Seagull; never apply the Di5 temperature offset. */
final class Di2BatteryValues {
    private static final Pattern PARCEL = Pattern.compile(
            "(?m)^(SOH|MIN_TEMP|MAX_TEMP|VOLTAGE|MIN_CELL|MAX_CELL|MOTOR_TEMP|INVERTER_TEMP|HV_VOLTAGE|HV_CURRENT|INSULATION|AC_COMPRESSOR)=Result: Parcel\\(00000000\\s+([0-9a-fA-F]{8})");

    static Map<String, Double> parse(String raw) {
        Map<String, Double> result = new HashMap<>();
        Matcher matcher = PARCEL.matcher(raw);
        while (matcher.find()) {
            int bits = (int) Long.parseLong(matcher.group(2), 16);
            String label = matcher.group(1);
            boolean floating = label.equals("VOLTAGE") || label.endsWith("CELL") || label.equals("HV_CURRENT");
            double value = floating ? Float.intBitsToFloat(bits) : bits;
            if (!Double.isFinite(value)) continue;
            switch (label) {
                case "AC_COMPRESSOR": put(result, "compressor", value, 0, 1); break;
                case "HV_VOLTAGE": if(value!=65535)put(result, "hv", value, 100, 600); break;
                case "HV_CURRENT": if(value!=-1)put(result, "current", value, -2000, 2000); break;
                case "INSULATION": if(value!=65535&&value!=1048575)put(result, "insulation", value*.001, 0, 10000); break;
                case "MOTOR_TEMP": put(result, "frontTemp", value, -40, 200); break;
                case "INVERTER_TEMP": put(result, "frontInv", value, -40, 200); break;
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
