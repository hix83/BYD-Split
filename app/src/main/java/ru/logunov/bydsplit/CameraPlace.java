package ru.logunov.bydsplit;

final class CameraPlace {
    final String name;
    final double latitude;
    final double longitude;
    final float radiusMeters;

    CameraPlace(String name, double latitude, double longitude, float radiusMeters) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
    }
}
