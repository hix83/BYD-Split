package ru.logunov.bydsplit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class AppPreferences {
    static final String FILE_NAME = "split_selection";
    static final String KEY_DRIVER_APP = "driver_app";
    static final String KEY_FAR_APP = "far_app";
    static final String KEY_DRIVER_APPS = "driver_apps";
    static final String KEY_FAR_APPS = "far_apps";
    static final String KEY_DRIVER_APP_INDEX = "driver_app_index";
    static final String KEY_FAR_APP_INDEX = "far_app_index";
    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_FULLSCREEN = "fullscreen";
    static final String KEY_DEMO_MODE = "demo_mode";
    static final String KEY_PANEL_LAYOUT = "panel_layout";
    static final String KEY_PANEL_RATIO = "panel_ratio";
    static final String KEY_COMPACT_TARGET = "compact_target";
    static final String KEY_STEERING_SHORT_SCAN = "steering_short_scan";
    static final String KEY_STEERING_LONG_SCAN = "steering_long_scan";
    static final String KEY_QUICK_MUSIC_APP = "quick_music_app";
    static final String KEY_QUICK_MAX_APP = "quick_max_app";
    static final String KEY_PARKING_CAMERA_NAME = "parking_camera_name";
    static final String KEY_PARKING_CAMERA_URL = "parking_camera_url";
    static final String KEY_PARKING_CAMERA_AUTO = "parking_camera_auto";
    static final String KEY_CAMERA_PLACES = "camera_places";
    static final String DEFAULT_PARKING_CAMERA_NAME = "Парковка";
    static final String DEFAULT_PARKING_CAMERA_URL = "https://parking.napaster.ru";
    static final String PANEL_LAYOUT_ONE_TWO = "1_2";
    static final String PANEL_LAYOUT_TWO_ONE = "2_1";
    static final String PANEL_LAYOUT_CUSTOM = "custom";
    static final String COMPACT_TARGET_VEHICLE = "vehicle";
    static final int DEFAULT_STEERING_SHORT_SCAN = 290;
    static final int DEFAULT_STEERING_LONG_SCAN = 312;

    private AppPreferences() {
    }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    static boolean isAutoStartEnabled(Context context) {
        return get(context).getBoolean(KEY_AUTO_START, true);
    }

    static boolean isFullscreenEnabled(Context context) {
        return get(context).getBoolean(KEY_FULLSCREEN, true);
    }

    static boolean isDemoModeEnabled(Context context) {
        return get(context).getBoolean(KEY_DEMO_MODE, false);
    }

    static boolean isCompactPaneOnLeft(Context context) {
        SharedPreferences preferences = get(context);
        String layout = preferences.getString(
                KEY_PANEL_LAYOUT, PANEL_LAYOUT_ONE_TWO);
        if (PANEL_LAYOUT_CUSTOM.equals(layout)) {
            return preferences.getFloat(KEY_PANEL_RATIO, 1f / 3f) <= 0.5f;
        }
        return !PANEL_LAYOUT_TWO_ONE.equals(layout);
    }

    static void setCompactPaneOnLeft(Context context, boolean onLeft) {
        get(context).edit()
                .putString(KEY_PANEL_LAYOUT,
                        onLeft ? PANEL_LAYOUT_ONE_TWO : PANEL_LAYOUT_TWO_ONE)
                .remove(KEY_PANEL_RATIO)
                .apply();
    }

    static String getCompactTarget(Context context) {
        return get(context).getString(
                KEY_COMPACT_TARGET, COMPACT_TARGET_VEHICLE);
    }

    static void setCompactTarget(Context context, String target) {
        get(context).edit()
                .putString(KEY_COMPACT_TARGET, target)
                .apply();
    }

    static int getSteeringShortScan(Context context) {
        return get(context).getInt(
                KEY_STEERING_SHORT_SCAN, DEFAULT_STEERING_SHORT_SCAN);
    }

    static int getSteeringLongScan(Context context) {
        return get(context).getInt(
                KEY_STEERING_LONG_SCAN, DEFAULT_STEERING_LONG_SCAN);
    }

    static void setSteeringScans(
            Context context, int shortScan, int longScan) {
        get(context).edit()
                .putInt(KEY_STEERING_SHORT_SCAN, shortScan)
                .putInt(KEY_STEERING_LONG_SCAN, longScan)
                .apply();
    }

    static String getParkingCameraName(Context context) {
        return get(context).getString(KEY_PARKING_CAMERA_NAME,
                DEFAULT_PARKING_CAMERA_NAME);
    }

    static String getParkingCameraUrl(Context context) {
        return get(context).getString(KEY_PARKING_CAMERA_URL,
                DEFAULT_PARKING_CAMERA_URL);
    }

    static boolean isParkingCameraAutoEnabled(Context context) {
        return get(context).getBoolean(KEY_PARKING_CAMERA_AUTO, false);
    }

    static List<CameraPlace> getCameraPlaces(Context context) {
        List<CameraPlace> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(get(context).getString(KEY_CAMERA_PLACES, "[]"));
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item != null) {
                    result.add(new CameraPlace(item.optString("name", "Место"),
                            item.optDouble("lat"), item.optDouble("lon"),
                            (float) item.optDouble("radius", 50)));
                }
            }
        } catch (Exception ignored) {
            // A damaged preference is treated as an empty list.
        }
        return result;
    }

    static void setCameraPlaces(Context context, List<CameraPlace> places) {
        JSONArray array = new JSONArray();
        for (CameraPlace place : places) {
            JSONObject item = new JSONObject();
            try {
                item.put("name", place.name);
                item.put("lat", place.latitude);
                item.put("lon", place.longitude);
                item.put("radius", place.radiusMeters);
                array.put(item);
            } catch (Exception ignored) {
                // Primitive JSON values cannot fail in normal operation.
            }
        }
        get(context).edit().putString(KEY_CAMERA_PLACES, array.toString()).apply();
    }

    static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
