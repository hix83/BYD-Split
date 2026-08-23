package ru.logunov.bydsplit;

final class VehicleTelemetrySnapshot {
    static final VehicleTelemetrySnapshot EMPTY = new VehicleTelemetrySnapshot(
            null, null, null, null, null,
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
            Float bearingDegrees) {
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
    }

    VehicleTelemetrySnapshot withBearing(Float bearing) {
        return new VehicleTelemetrySnapshot(
                speedKmh, tirePressFlKpa, tirePressFrKpa,
                tirePressRlKpa, tirePressRrKpa,
                tireTempFlC, tireTempFrC, tireTempRlC, tireTempRrC,
                driveMode, workMode, bearing);
    }

    VehicleTelemetrySnapshot withVehicleData(VehicleTelemetrySnapshot value) {
        return new VehicleTelemetrySnapshot(
                value.speedKmh, value.tirePressFlKpa, value.tirePressFrKpa,
                value.tirePressRlKpa, value.tirePressRrKpa,
                value.tireTempFlC, value.tireTempFrC,
                value.tireTempRlC, value.tireTempRrC,
                value.driveMode, value.workMode, bearingDegrees);
    }

    static VehicleTelemetrySnapshot demo() {
        return new VehicleTelemetrySnapshot(
                72, 240, 240, 230, 230,
                31, 32, 29, 30, 3, 3, 308f);
    }
}
