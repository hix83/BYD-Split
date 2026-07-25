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

    static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
