package ru.logunov.bydsplit;

import java.util.*;

public final class Di2VehicleSdkTest {
    static final class Fake implements Di2VehicleSdk.Access {
        final List<String> writes = new ArrayList<>();
        int power = 0, temperature = 22, fan = 3, unit = 1;
        boolean deny, offline, acknowledgeOnly;
        @Override public Number call(String device, String method, int... args) {
            if (deny) throw new SecurityException("no permission");
            switch (method) {
                case "getCurrentSpeed": return 52.4;
                case "getElecPercentageValue": return Double.NaN;
                case "getTotalMileageValue": return 12345;
                case "getTyrePressureValue": return args[0] == 2 ? 65535 : 230 + args[0];
                case "getAcStartState": return offline ? -2147482648 : power;
                case "getAcWindLevel": return fan;
                case "getAcWindMode": return 2;
                case "getAcCycleMode": case "getAcCompressorMode": return 0;
                case "getTemperatureUnit": return unit;
                case "getTemprature": return args[0] == 1 ? temperature : 70;
                default:
                    writes.add(method + Arrays.toString(args));
                    if (!acknowledgeOnly) {
                        if (method.equals("start")) power = 1;
                        if (method.equals("stop")) power = 0;
                        if (method.equals("setAcTemperature")) temperature = args[1];
                        if (method.equals("setAcWindLevel")) fan = args[1];
                    }
                    return 0;
            }
        }
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Fake fake = new Fake();
        Di2VehicleSdk sdk = new Di2VehicleSdk(fake);
        Di2VehicleSdk.Reading r = sdk.read();
        VehicleTelemetrySnapshot car = Di2Snapshot.vehicle(r.values);
        check(car.speedKmh == 52 && car.batterySocPercent == null, "typed speed; NaN SOC stays missing");
        check(car.tirePressFlKpa == 231 && car.tirePressFrKpa == null && car.tirePressRlKpa == 233
                && car.tirePressRrKpa == 234, "Di2 tyre areas; sentinel is not pressure");
        check(car.fuelPercent == null && car.engineRpm == null && car.batteryPowerKw == null,
                "no combustion or guessed battery data");
        TechSnapshot tech = new TechSnapshot(new BatteryDetailsSnapshot(car), r.values);
        check(tech.values.get("mileage") == 12345 && !tech.values.containsKey("inside"),
                "km not scaled twice; raw sensors cannot drive rules");
        check(tech.battery.lifetimeKwh == null && tech.battery.deltaMv() == null, "no fabricated zero metrics");
        check("confirmed".equals(sdk.set("power", 1, () -> {})), "power readback");
        check(fake.writes.get(0).equals("start[0]"), "correct source argument");
        check("confirmed".equals(sdk.set("temperature", 24, () -> {})), "temperature readback");
        check(fake.writes.get(1).equals("setAcTemperature[1, 24, 0, 1]"), "main/source/C argument order");
        check("confirmed".equals(sdk.set("fan", 5, () -> {})), "fan readback");
        check(fake.writes.get(2).equals("setAcWindLevel[0, 5]"), "source then fan");
        int n = fake.writes.size();
        check("confirmed".equals(sdk.set("fan", 5, () -> {})) && fake.writes.size() == n, "no redundant write");
        fake.acknowledgeOnly = true;
        check("unconfirmed".equals(sdk.set("power", 0, () -> {})), "ack alone is not success");
        fake.offline = true; n = fake.writes.size();
        check("read_unavailable".equals(sdk.set("power", 0, () -> {})) && fake.writes.size() == n,
                "do not write when preflight fails");
        fake.unit = 0;
        check("celsius_required".equals(sdk.set("temperature", 25, () -> {})), "do not change F/C implicitly");
        fake.unit = 1; fake.temperature = 55;
        check(!sdk.climate().values.containsKey("temperature"), "reject temperature outside selected unit range");
        check("read_unavailable".equals(sdk.set("temperature", 25, () -> {})), "invalid C preflight blocks write");
        for (String[] invalid : new String[][]{{"fan","8"},{"wind","0"},{"power","2"},
                {"temperature","17"},{"temperature","33"},{"recirculation","2"},{"arbitraryMethod","1"}}) {
            try { sdk.set(invalid[0], Integer.parseInt(invalid[1]), () -> {}); throw new AssertionError("bad command accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        fake.deny = true;
        check(sdk.read().values.isEmpty(), "permission denied yields no fake readings");
        check("SecurityException".equals(sdk.set("power", 1, () -> {})), "permission failure reported");
        System.out.println("Di2VehicleSdkTest passed");
    }
}
