package ru.logunov.bydsplit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

final class AppPreferences {
    static final String FILE_NAME = "split_selection";
    static final String KEY_DRIVER_APP = "driver_app";
    static final String KEY_FAR_APP = "far_app";
    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_DEMO_MODE = "demo_mode";
    static final String KEY_PANEL_LAYOUT = "panel_layout";
    static final String PANEL_LAYOUT_ONE_TWO = "1_2";
    static final String PANEL_LAYOUT_TWO_ONE = "2_1";

    private AppPreferences() {
    }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    static boolean isAutoStartEnabled(Context context) {
        return get(context).getBoolean(KEY_AUTO_START, true);
    }

    static boolean isDemoModeEnabled(Context context) {
        return get(context).getBoolean(KEY_DEMO_MODE, false);
    }

    static boolean isDriverPaneLarge(Context context) {
        return PANEL_LAYOUT_TWO_ONE.equals(
                get(context).getString(
                        KEY_PANEL_LAYOUT, PANEL_LAYOUT_ONE_TWO));
    }

    static void setDriverPaneLarge(Context context, boolean driverPaneLarge) {
        get(context).edit()
                .putString(KEY_PANEL_LAYOUT,
                        driverPaneLarge
                                ? PANEL_LAYOUT_TWO_ONE
                                : PANEL_LAYOUT_ONE_TWO)
                .apply();
    }

    static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
