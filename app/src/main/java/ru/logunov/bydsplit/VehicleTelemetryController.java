package ru.logunov.bydsplit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class VehicleTelemetryController implements LocationListener {
    private static final String TAG = "BYD_VEHICLE_DATA";
    private static final long POLL_INTERVAL_MS = 900L;

    private final Context context;
    private final Consumer<VehicleTelemetrySnapshot> listener;
    private final LocalAdbManager localAdb;
    private final LocationManager locationManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();

    private volatile VehicleTelemetrySnapshot latest =
            VehicleTelemetrySnapshot.EMPTY;
    private ScheduledFuture<?> polling;
    private boolean locationStarted;
    private volatile boolean demoActive;

    VehicleTelemetryController(
            Context context, Consumer<VehicleTelemetrySnapshot> listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        localAdb = LocalAdbManager.get(context);
        locationManager = (LocationManager) context.getSystemService(
                Context.LOCATION_SERVICE);
    }

    synchronized void start(boolean demo) {
        demoActive = demo;
        if (demo) {
            if (polling != null) {
                polling.cancel(true);
                polling = null;
            }
            latest = VehicleTelemetrySnapshot.demo();
            publish(latest);
        } else if (polling == null || polling.isCancelled()) {
            polling = executor.scheduleWithFixedDelay(
                    this::pollVehicle, 0L, POLL_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        }
        startLocationIfPermitted();
    }

    synchronized void stop() {
        if (polling != null) {
            polling.cancel(true);
            polling = null;
        }
        if (locationStarted) {
            locationManager.removeUpdates(this);
            locationStarted = false;
        }
    }

    synchronized void startLocationIfPermitted() {
        if (locationStarted || context.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 500L, 0.5f, this,
                    Looper.getMainLooper());
            locationStarted = true;
            Location last = locationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER);
            if (last != null) {
                acceptBearing(last);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "GPS course is unavailable", error);
        }
    }

    synchronized void close() {
        stop();
        executor.shutdownNow();
    }

    private void pollVehicle() {
        VehicleTelemetrySnapshot vehicle = localAdb.readVehicleTelemetry();
        if (vehicle == null || demoActive) {
            return;
        }
        SocHistory.record(context,vehicle);
        latest = latest.withVehicleData(vehicle);
        publish(latest);
    }

    private void publish(VehicleTelemetrySnapshot value) {
        mainHandler.post(() -> listener.accept(value));
    }

    @Override
    public void onLocationChanged(Location location) {
        acceptBearing(location);
    }

    private void acceptBearing(Location location) {
        if (!location.hasBearing()) {
            return;
        }
        float measured = normalize(location.getBearing());
        Float previous = latest.bearingDegrees;
        float smoothed = previous == null
                ? measured : smoothAngle(previous, measured, 0.28f);
        latest = latest.withBearing(smoothed);
        publish(latest);
    }

    private static float smoothAngle(float current, float target, float factor) {
        float delta = ((target - current + 540f) % 360f) - 180f;
        return normalize(current + delta * factor);
    }

    private static float normalize(float value) {
        float result = value % 360f;
        return result < 0f ? result + 360f : result;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
