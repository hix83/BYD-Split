package ru.logunov.bydsplit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ParkingCameraAutomation implements LocationListener {
    private final Context context;
    private final LocationManager locationManager;
    private final Set<String> inside = new HashSet<>();
    private boolean running;

    ParkingCameraAutomation(Context context) {
        this.context = context.getApplicationContext();
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    void start() {
        stop();
        if (!AppPreferences.isParkingCameraAutoEnabled(context)
                || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    3000L, 5f, this);
            running = true;
        } catch (RuntimeException ignored) {
            running = false;
        }
    }

    void stop() {
        if (running) {
            locationManager.removeUpdates(this);
        }
        running = false;
    }

    @Override
    public void onLocationChanged(Location location) {
        List<CameraPlace> places = AppPreferences.getCameraPlaces(context);
        Set<String> nowInside = new HashSet<>();
        for (CameraPlace place : places) {
            float[] distance = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    place.latitude, place.longitude, distance);
            String key = place.name + "|" + place.latitude + "|" + place.longitude;
            if (distance[0] <= place.radiusMeters) {
                nowInside.add(key);
                if (!inside.contains(key)) {
                    ParkingCameraOverlay.show(context);
                }
            }
        }
        inside.clear();
        inside.addAll(nowInside);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
}
