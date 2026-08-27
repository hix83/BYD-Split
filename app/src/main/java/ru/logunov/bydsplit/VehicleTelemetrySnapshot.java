package ru.logunov.bydsplit;

final class VehicleTelemetrySnapshot {
    static final VehicleTelemetrySnapshot EMPTY = new VehicleTelemetrySnapshot(
            null, null, null, null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);

    final Integer speedKmh;
    final Integer tirePressFlKpa;
    final Integer tirePressFrKpa;
    final Integer tirePressRlKpa;
    final Integer tirePressRrKpa;
    final Integer tireTempFlC;
    final Integer tireTempFrC;
    final Integer tireTempRlC;
    final Integer tireTempRrC;
    final Integer driveMode;
    final Integer workMode;
    final Float bearingDegrees;
    final Float batterySocPercent;
    final Integer batteryPowerKw;
    final Integer engineRpm;
    final Integer generatorPowerKw;
    final Integer motorPowerKw;
    final Integer chargeGunState;
    final Integer chargingType;

    VehicleTelemetrySnapshot(
            Integer speedKmh,
            Integer tirePressFlKpa,
            Integer tirePressFrKpa,
            Integer tirePressRlKpa,
            Integer tirePressRrKpa,
            Integer tireTempFlC,
            Integer tireTempFrC,
            Integer tireTempRlC,
            Integer tireTempRrC,
            Integer driveMode,
            Integer workMode,
            Float bearingDegrees,
            Float batterySocPercent,
            Integer batteryPowerKw,
            Integer engineRpm,
            Integer generatorPowerKw,
            Integer motorPowerKw,
            Integer chargeGunState,
            Integer chargingType) {
        this.speedKmh = speedKmh;
        this.tirePressFlKpa = tirePressFlKpa;
        this.tirePressFrKpa = tirePressFrKpa;
        this.tirePressRlKpa = tirePressRlKpa;
        this.tirePressRrKpa = tirePressRrKpa;
        this.tireTempFlC = tireTempFlC;
        this.tireTempFrC = tireTempFrC;
        this.tireTempRlC = tireTempRlC;
        this.tireTempRrC = tireTempRrC;
        this.driveMode = driveMode;
        this.workMode = workMode;
        this.bearingDegrees = bearingDegrees;
        this.batterySocPercent = batterySocPercent;
        this.batteryPowerKw = batteryPowerKw;
        this.engineRpm = engineRpm;
        this.generatorPowerKw = generatorPowerKw;
        this.motorPowerKw = motorPowerKw;
        this.chargeGunState = chargeGunState;
        this.chargingType = chargingType;
    }

    VehicleTelemetrySnapshot withBearing(Float bearing) {
        return new VehicleTelemetrySnapshot(
                speedKmh, tirePressFlKpa, tirePressFrKpa,
                tirePressRlKpa, tirePressRrKpa,
                tireTempFlC, tireTempFrC, tireTempRlC, tireTempRrC,
                driveMode, workMode, bearing,
                batterySocPercent, batteryPowerKw,
                engineRpm, generatorPowerKw, motorPowerKw,
                chargeGunState, chargingType);
    }

    VehicleTelemetrySnapshot withVehicleData(VehicleTelemetrySnapshot value) {
        return new VehicleTelemetrySnapshot(
                value.speedKmh, value.tirePressFlKpa, value.tirePressFrKpa,
                value.tirePressRlKpa, value.tirePressRrKpa,
                value.tireTempFlC, value.tireTempFrC,
                value.tireTempRlC, value.tireTempRrC,
                value.driveMode, value.workMode, bearingDegrees,
                value.batterySocPercent, value.batteryPowerKw,
                value.engineRpm, value.generatorPowerKw,
                value.motorPowerKw, value.chargeGunState,
                value.chargingType);
    }

    static VehicleTelemetrySnapshot demo() {
        return new VehicleTelemetrySnapshot(
                72, 240, 240, 230, 230,
                31, 32, 29, 30, 3, 3, 308f,
                68f, 26, 1820, 18, 24, 1, null);
    }
}
