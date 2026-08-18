package ru.logunov.bydsplit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

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
    static final String KEY_STEERING_SHORT_SCAN = "steering_short_scan";
    static final String KEY_STEERING_LONG_SCAN = "steering_long_scan";
    static final String PANEL_LAYOUT_ONE_TWO = "1_2";
    static final String PANEL_LAYOUT_TWO_ONE = "2_1";
    static final String PANEL_LAYOUT_CUSTOM = "custom";
    static final float MIN_PANEL_RATIO = 0.25f;
    static final float MAX_PANEL_RATIO = 0.75f;
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

    static boolean isDriverPaneLarge(Context context) {
        return getPanelRatio(context) > 0.5f;
    }

    static void setDriverPaneLarge(Context context, boolean driverPaneLarge) {
        get(context).edit()
                .putString(KEY_PANEL_LAYOUT,
                        driverPaneLarge
                                ? PANEL_LAYOUT_TWO_ONE
                                : PANEL_LAYOUT_ONE_TWO)
                .putFloat(KEY_PANEL_RATIO,
                        driverPaneLarge ? 2f / 3f : 1f / 3f)
                .apply();
    }

    static float getPanelRatio(Context context) {
        SharedPreferences preferences = get(context);
        if (preferences.contains(KEY_PANEL_RATIO)) {
            return clampPanelRatio(
                    preferences.getFloat(KEY_PANEL_RATIO, 1f / 3f));
        }
        return PANEL_LAYOUT_TWO_ONE.equals(preferences.getString(
                KEY_PANEL_LAYOUT, PANEL_LAYOUT_ONE_TWO))
                ? 2f / 3f : 1f / 3f;
    }

    static void setPanelRatio(Context context, float ratio) {
        get(context).edit()
                .putFloat(KEY_PANEL_RATIO, clampPanelRatio(ratio))
                .putString(KEY_PANEL_LAYOUT, PANEL_LAYOUT_CUSTOM)
                .apply();
    }

    static boolean isPanelRatioPreset(Context context, float preset) {
        return Math.abs(getPanelRatio(context) - preset) < 0.015f;
    }

    private static float clampPanelRatio(float ratio) {
        return Math.max(MIN_PANEL_RATIO, Math.min(MAX_PANEL_RATIO, ratio));
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

    static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
