package ru.logunov.bydsplit;

/** Electrical and mechanical paths are independent; HEV is not a clutch signal. */
final class DmiEnergyFlow {
    final boolean engineToGenerator, batteryToDrive, driveToBattery;
    final boolean generatorToBattery, motorToWheels, wheelsToMotor, engineToWheels;

    DmiEnergyFlow(VehicleTelemetrySnapshot s, Boolean mechanicalCoupling) {
        if (s.energyState != null && s.energyState > 0) {
            int state = s.energyState;
            boolean electricDrive = between(state, 1, 3) || state == 7 || state == 8
                    || state == 9 || between(state, 24, 26);
            boolean feedback = between(state, 4, 6) || between(state, 14, 16)
                    || between(state, 27, 29);
            boolean seriesDrive = state == 19 || between(state, 30, 33);
            boolean generating = state == 18 || between(state, 20, 22)
                    || between(state, 11, 13) || seriesDrive;
            boolean directDrive = state == 7 || state == 8 || state == 9 || state == 17;

            engineToGenerator = generating;
            generatorToBattery = state == 18 || between(state, 20, 22);
            batteryToDrive = electricDrive;
            motorToWheels = electricDrive || seriesDrive;
            wheelsToMotor = feedback;
            driveToBattery = feedback || generatorToBattery;
            engineToWheels = directDrive;
            return;
        }

        boolean moving = s.speedKmh != null && s.speedKmh > 0;
        boolean engine = s.engineRpm != null && s.engineRpm > 0;
        boolean motorSpinning = s.motorRpm != null && Math.abs(s.motorRpm) > 20;
        boolean motorPowerTraction = s.motorPowerKw != null && s.motorPowerKw > 1;
        boolean motorPowerRegen = s.motorPowerKw != null && s.motorPowerKw < -1;
        boolean charging = s.batteryPowerKw != null && s.batteryPowerKw < -1;
        boolean discharging = s.batteryPowerKw != null && s.batteryPowerKw > 1;
        boolean generatorPower = s.generatorPowerKw != null && s.generatorPowerKw > 1;
        boolean generating = engine && (generatorPower || charging
                || s.workMode != null && s.workMode == 3
                && !Boolean.TRUE.equals(mechanicalCoupling));
        boolean traction = moving && (motorPowerTraction || motorSpinning && !charging);
        boolean regen = moving && (motorPowerRegen || motorSpinning && charging);
        engineToGenerator = generating;
        batteryToDrive = discharging && traction;
        driveToBattery = charging && (generating || regen);
        generatorToBattery = charging && generating;
        motorToWheels = moving && traction;
        wheelsToMotor = regen;
        // A nullable clutch signal deliberately leaves direct drive unconfirmed.
        engineToWheels = moving && engine && Boolean.TRUE.equals(mechanicalCoupling);
    }

    private static boolean between(int value, int low, int high) {
        return value >= low && value <= high;
    }
}
