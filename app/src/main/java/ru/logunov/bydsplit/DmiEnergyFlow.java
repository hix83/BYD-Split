package ru.logunov.bydsplit;

/** Electrical and mechanical paths are independent; HEV is not a clutch signal. */
final class DmiEnergyFlow {
    final boolean engineToGenerator, batteryToDrive, driveToBattery;
    final boolean generatorToBattery, motorToWheels, wheelsToMotor, engineToWheels;

    DmiEnergyFlow(VehicleTelemetrySnapshot s, Boolean mechanicalCoupling) {
        boolean moving = s.speedKmh != null && s.speedKmh > 0;
        boolean engine = s.engineRpm != null && s.engineRpm > 0;
        boolean generating = engine && s.generatorPowerKw != null && s.generatorPowerKw > 1;
        boolean traction = s.motorPowerKw != null && s.motorPowerKw > 1;
        boolean regen = moving && s.motorPowerKw != null && s.motorPowerKw < -1;
        boolean charging = s.batteryPowerKw != null && s.batteryPowerKw < -1;
        boolean discharging = s.batteryPowerKw != null && s.batteryPowerKw > 1;
        engineToGenerator = generating;
        batteryToDrive = discharging && traction;
        driveToBattery = charging && (generating || regen);
        generatorToBattery = charging && generating;
        motorToWheels = moving && traction;
        wheelsToMotor = regen;
        // A nullable clutch signal deliberately leaves direct drive unconfirmed.
        engineToWheels = moving && engine && Boolean.TRUE.equals(mechanicalCoupling);
    }
}
