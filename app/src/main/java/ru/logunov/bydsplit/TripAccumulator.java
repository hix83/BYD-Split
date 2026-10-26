package ru.logunov.bydsplit;

/** Numerical integration of observed samples only. Positive battery watts = discharge.
 * Independent of Android so loss of telemetry and hybrid accounting can be tested. */
final class TripAccumulator {
    long start, end, movingMs, observedMs, missingMs, powerMissingMs;
    double km, usedKwh, recoveredKwh, maxSpeed;
    boolean engineSeen;
    final TripEnergyCounter electricity = new TripEnergyCounter();
    final TripFuelCounter fuel = new TripFuelCounter();
    long lastTime;
    Double lastSpeed, lastWatts, lastOdometer;

    void add(long time, Double speed, Double watts, Double odometer, boolean engine) {
        if (start == 0) start = time;
        end = time;
        engineSeen |= engine;
        if (lastTime > 0 && time > lastTime) {
            long dt = time - lastTime;
            if (dt <= 30000 && speed != null && lastSpeed != null) {
                observedMs += dt;
                double distance = (speed + lastSpeed) * .5 * dt / 3600000.;
                if (odometer != null && lastOdometer != null) {
                    double delta = odometer - lastOdometer;
                    // Odometers quantize to 100 m; use speed integration consistently.
                    if (delta < 0 || delta > Math.max(1, 300. * dt / 3600000.)) missingMs += dt;
                }
                km += distance;
                if (speed > 1 || lastSpeed > 1) movingMs += dt;
                if (watts != null && lastWatts != null) {
                    // Integrate sign crossings separately, without cancelling recovery.
                    double hours = dt / 3600000.;
                    if (watts * lastWatts < 0) {
                        double fraction = Math.abs(lastWatts) / (Math.abs(lastWatts) + Math.abs(watts));
                        accumulate(lastWatts / 2, hours * fraction);
                        accumulate(watts / 2, hours * (1 - fraction));
                    } else accumulate((watts + lastWatts) / 2, hours);
                } else powerMissingMs += dt;
            } else missingMs += dt;
        }
        if (speed != null) maxSpeed = Math.max(maxSpeed, speed);
        lastTime = time; lastSpeed = speed; lastWatts = watts; lastOdometer = odometer;
    }
    private void accumulate(double watts, double hours) {
        if (watts >= 0) usedKwh += watts * hours / 1000.;
        else recoveredKwh -= watts * hours / 1000.;
    }
    double netKwh() { return usedKwh - recoveredKwh; }
    Double consumption() { return km >= .1 ? netKwh() * 100 / km : null; }
    Double automaticElectricityKwh() {
        Double counter = TripEnergyCounter.difference(electricity.startKwh, electricity.endKwh,
                electricity.invalid, end - start);
        if (counter != null && counter > 0) return counter;
        // Signed HV power subtracts regeneration and engine-to-battery charging. No SOC fallback on DM-i.
        if(missingMs == 0 && powerMissingMs == 0 && observedMs > 0)return Math.max(0, netKwh());
        // A valid positive lifetime counter can remain in the same 0.1 kWh bucket.
        // Display the resolution beside this estimate; missing counters still remain unknown.
        return counter != null && end > start ? counter : null;
    }
    Double electricityCost(double price, Double overrideKwh) {
        Double energy = overrideKwh != null ? overrideKwh : automaticElectricityKwh();
        return energy == null ? null : Math.round(energy * price * 100) / 100.;
    }
    Double estimatedCost(double electricityPrice, Double fuelLitres, double fuelPrice, Double overrideKwh) {
        Double electric = electricityCost(electricityPrice, overrideKwh);
        if (electric == null || fuelLitres == null) return null;
        return Math.round((electric + fuelLitres * fuelPrice) * 100) / 100.;
    }
}
