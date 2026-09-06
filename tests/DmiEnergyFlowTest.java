package ru.logunov.bydsplit;

public final class DmiEnergyFlowTest {
    private static DmiEnergyFlow state(int speed, int battery, int rpm, int generator, int motor, Boolean clutch) {
        return new DmiEnergyFlow(new VehicleTelemetrySnapshot(speed, null, null, null, null,
                null, null, null, null, null, 3, null, 68f, battery, rpm, generator, motor, 1, null), clutch);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        DmiEnergyFlow ev = state(72, 28, 0, 0, 26, false);
        check(ev.batteryToDrive && ev.motorToWheels && !ev.engineToWheels && !ev.driveToBattery, "EV direction");
        DmiEnergyFlow charge = state(0, -18, 1600, 20, 0, false);
        check(charge.engineToGenerator && charge.generatorToBattery && charge.driveToBattery && !charge.motorToWheels, "Stationary charging");
        DmiEnergyFlow series = state(72, 0, 1850, 28, 26, false);
        check(series.engineToGenerator && series.motorToWheels && !series.engineToWheels && !series.batteryToDrive, "Series without invented battery flow");
        DmiEnergyFlow parallel = state(90, 20, 2100, 0, 18, true);
        check(parallel.engineToWheels && parallel.motorToWheels && parallel.batteryToDrive && !parallel.engineToGenerator, "Parallel independent paths");
        DmiEnergyFlow regen = state(55, -22, 0, 0, -24, false);
        check(regen.wheelsToMotor && regen.driveToBattery && !regen.motorToWheels && !regen.batteryToDrive, "Regeneration direction");
        DmiEnergyFlow direct = state(90, 0, 1900, 0, 0, true);
        check(direct.engineToWheels && !direct.motorToWheels, "Engine-only drive");
        check(!state(90, 20, 2100, 0, 18, null).engineToWheels, "Unknown clutch must not imply parallel drive");
        DmiEnergyFlow unknown = new DmiEnergyFlow(VehicleTelemetrySnapshot.EMPTY, null);
        check(!unknown.engineToWheels && !unknown.motorToWheels && !unknown.driveToBattery && !unknown.batteryToDrive, "No fabricated flows for missing data");
        System.out.println("8 DM-i flow checks passed");
    }
}
