package ru.logunov.bydsplit;

public final class ClimateIconStateTest {
    public static void main(String[] args) {
        VehicleTelemetrySnapshot base = VehicleTelemetrySnapshot.EMPTY;
        VehicleTelemetrySnapshot live = new VehicleTelemetrySnapshot(
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, ClimateIconState.decode(1, 5, 4));
        if (base.withVehicleData(live).withBearing(90f).climate.fanLevel != 5)
            throw new AssertionError("climate lost during telemetry merge");
        int[] expected = {0, 1, 3, 2, 6, 4, 7, 5};
        for (int mode = 1; mode <= 7; mode++) {
            ClimateIconState s = ClimateIconState.decode(1, mode, mode);
            int flags = (s.face ? 1 : 0) | (s.feet ? 2 : 0) | (s.glass ? 4 : 0);
            if (flags != expected[mode] || s.fanLevel != mode) throw new AssertionError(mode);
        }
        for (int power : new int[]{0, -2147482648, 65535}) {
            ClimateIconState s = ClimateIconState.decode(power, 7, 6);
            if (s.fanLevel != 0 || s.face || s.feet || s.glass) throw new AssertionError("off/unknown");
        }
        ClimateIconState invalid = ClimateIconState.decode(1, 65535, 65535);
        if (invalid.fanLevel != 0 || invalid.face || invalid.feet || invalid.glass) throw new AssertionError("invalid");
        if (ClimateIconState.decode(1, 0, 1).fanLevel != 0) throw new AssertionError("zero");
        System.out.println("ClimateIconStateTest passed: seven modes, fan levels, off and unavailable");
    }
}
