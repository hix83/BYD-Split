package ru.logunov.bydsplit;

import java.util.LinkedHashMap;
import java.util.Map;

/** Di2.2H 26.1.1.2511070.1 SDK contract, recovered from boot-framework.vdex.
 * Values are SDK numbers, never Di5 Binder parcels or IEEE-754 int bits. */
final class Di2VehicleSdk {
    interface Access {
        Number call(String device, String method, int... arguments) throws Exception;
    }
    interface Pause { void waitForUpdate() throws InterruptedException; }

    static final class Reading {
        final Map<String, Double> values = new LinkedHashMap<>();
        final Map<String, String> errors = new LinkedHashMap<>();
    }
    static final class Command {
        final String method, readMethod;
        final int[] arguments, readArguments;
        final int wanted;
        Command(String method, int[] arguments, String readMethod, int[] readArguments, int wanted) {
            this.method = method; this.arguments = arguments;
            this.readMethod = readMethod; this.readArguments = readArguments; this.wanted = wanted;
        }
    }

    private final Access access;
    Di2VehicleSdk(Access access) { this.access = access; }

    Reading read() {
        Reading r = new Reading();
        read(r, "speedKmh", "speed", "getCurrentSpeed", 0, 300);
        read(r, "socPercent", "statistic", "getElecPercentageValue", 0, 100);
        // The SDK already multiplies the raw odometer by 0.1. Do not scale twice.
        read(r, "mileage", "statistic", "getTotalMileageValue", 0, 10000000);
        // Tyre SDK area order differs from instrument SDK: 1 LF, 2 RF, 3 LB, 4 RB.
        String[] wheels = {"fl", "fr", "rl", "rr"};
        for (int i = 0; i < wheels.length; i++)
            read(r, "pressure_" + wheels[i], "tyre", "getTyrePressureValue", 100, 500, i + 1);
        readClimate(r);
        return r;
    }

    Reading climate() { Reading r = new Reading(); readClimate(r); return r; }

    private void readClimate(Reading r) {
        read(r, "power", "ac", "getAcStartState", 0, 1);
        read(r, "fan", "ac", "getAcWindLevel", 0, 7);
        read(r, "wind", "ac", "getAcWindMode", 1, 7);
        read(r, "recirculation", "ac", "getAcCycleMode", 0, 1);
        read(r, "compressor", "ac", "getAcCompressorMode", 0, 1);
        read(r, "temperatureUnit", "ac", "getTemperatureUnit", 0, 1);
        // Firmware spells this method 'Temprature'. 1=main, 3=cabin, 4=outside.
        read(r, "temperature", "ac", "getTemprature", 17, 91, 1);
        Double unit = r.values.get("temperatureUnit"), temperature = r.values.get("temperature");
        if (temperature != null && (unit == null || (unit == 1 ? temperature > 33 : temperature < 64))) {
            r.values.remove("temperature");
            r.errors.put("temperature", "invalid_or_unsupported");
        }
        // Sensor conversion/offsets are not proven: retain raw values for diagnostics,
        // but do not feed them into temperature automations as degrees Celsius.
        read(r, "insideRaw", "ac", "getTemprature", -60, 255, 3);
        read(r, "outsideRaw", "ac", "getTemprature", -60, 255, 4);
    }

    private void read(Reading r, String key, String device, String method,
            double min, double max, int... args) {
        try {
            Number number = access.call(device, method, args);
            Double value = valid(number, min, max);
            if (value == null) r.errors.put(key, "invalid_or_unsupported");
            else r.values.put(key, value);
        } catch (Exception e) { r.errors.put(key, errorName(e)); }
    }

    static Double valid(Number number, double min, double max) {
        if (number == null) return null;
        double v = number.doubleValue();
        // SDK errors include 0x800003e8..0x800003eb; range checks reject them.
        return Double.isFinite(v) && v != 65535 && v != 1048575 && v >= min && v <= max ? v : null;
    }

    static String errorName(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getClass().getSimpleName();
    }

    /** Fixed allowlist: callers cannot supply SDK method names, feature IDs or zones. */
    static Command command(String name, int value) {
        switch (name) {
            case "power":
                range(value, 0, 1);
                return new Command(value == 1 ? "start" : "stop", new int[]{0},
                        "getAcStartState", new int[0], value);
            case "temperature":
                range(value, 18, 32);
                // type=1 main zone; value in C; source=0; unit=1 (C).
                return new Command("setAcTemperature", new int[]{1, value, 0, 1},
                        "getTemprature", new int[]{1}, value);
            case "fan": range(value, 0, 7); return pair("setAcWindLevel", "getAcWindLevel", value);
            case "wind": range(value, 1, 7); return pair("setAcWindMode", "getAcWindMode", value);
            case "recirculation": range(value, 0, 1); return pair("setAcCycleMode", "getAcCycleMode", value);
            case "compressor": range(value, 0, 1); return pair("setAcCompressorMode", "getAcCompressorMode", value);
            default: throw new IllegalArgumentException("Unknown climate command");
        }
    }
    private static Command pair(String write, String read, int value) {
        return new Command(write, new int[]{0, value}, read, new int[0], value);
    }
    private static void range(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Invalid climate value");
    }

    /** A nonnegative SDK return is only an acknowledgement. Success requires readback. */
    String set(String name, int value, Pause pause) {
        Command command = command(name, value); // validate before any access
        try {
            Number before = access.call("ac", command.readMethod, command.readArguments);
            double min = name.equals("temperature") ? 17 : name.equals("wind") ? 1 : 0;
            double max = name.equals("temperature") ? 91 : name.equals("fan") || name.equals("wind") ? 7 : 1;
            if (valid(before, min, max) == null) return "read_unavailable";
            // Explicit temperature commands use C. Never silently change the car's F/C setting.
            if (name.equals("temperature")) {
                Number unit = access.call("ac", "getTemperatureUnit");
                if (unit == null || unit.intValue() != 1) return "celsius_required";
                if (valid(before, 17, 33) == null) return "read_unavailable";
            }
            if (before.doubleValue() == command.wanted) return "confirmed";
            Number reply = access.call("ac", command.method, command.arguments);
            if (reply == null || reply.doubleValue() < 0) return "rejected";
            for (int i = 0; i < 4; i++) {
                pause.waitForUpdate();
                Number actual = access.call("ac", command.readMethod, command.readArguments);
                if (actual != null && actual.doubleValue() == command.wanted) return "confirmed";
            }
            return "unconfirmed";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return "interrupted";
        } catch (Exception e) { return errorName(e); }
    }
}
