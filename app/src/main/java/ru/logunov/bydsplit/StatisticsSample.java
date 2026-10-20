package ru.logunov.bydsplit;

final class StatisticsSample {
    final Double speed, watts, odometer, lifetimeFuelLitres, lifetimeElectricityKwh, soc;
    final boolean engine, charging, engineKnown;
    final String chargeMode;
    StatisticsSample(VehicleTelemetrySnapshot vehicle, Double watts, Double odometer) {
        this(vehicle, watts, odometer, null);
    }
    StatisticsSample(VehicleTelemetrySnapshot vehicle, Double watts, Double odometer, Double lifetimeFuelLitres) {
        this(vehicle, watts, odometer, lifetimeFuelLitres, null);
    }
    StatisticsSample(VehicleTelemetrySnapshot vehicle, Double watts, Double odometer, Double lifetimeFuelLitres, Double lifetimeElectricityKwh) {
        this.lifetimeFuelLitres = lifetimeFuelLitres;
        this.lifetimeElectricityKwh = lifetimeElectricityKwh;
        soc = vehicle.batterySocPercent == null ? null : vehicle.batterySocPercent.doubleValue();
        speed = vehicle.speedKmh == null ? null : vehicle.speedKmh.doubleValue();
        this.watts = watts;
        this.odometer = odometer;
        engineKnown = vehicle.engineRpm != null;
        engine = vehicle.engineRpm != null && vehicle.engineRpm > 0;
        chargeMode = vehicle.chargeGunState != null && vehicle.chargeGunState == 3 ? "DC"
                : vehicle.chargeGunState != null && vehicle.chargeGunState == 2 ? "AC" : "UNKNOWN";
        // Cable inserted alone is not evidence of an active charging session.
        charging = vehicle.chargeGunState != null && vehicle.chargeGunState >= 2 && vehicle.chargeGunState <= 4
                && watts != null && watts < -100;
    }
}
