package ru.logunov.bydsplit;

import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;

final class ThemeManager {
    static final String KEY = "theme_mode";
    static boolean isNight(Context context) {
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        Boolean daytime = null;
        if (night == Configuration.UI_MODE_NIGHT_UNDEFINED) {
            try (Cursor cursor = context.getContentResolver().query(
                    Uri.parse("content://carsettings/global"), new String[]{"value"},
                    "name=?", new String[]{"sys_day_or_night"}, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String value = cursor.getString(0);
                    if ("1".equals(value) || "true".equals(value)) daytime = true;
                    else if ("0".equals(value) || "false".equals(value)) daytime = false;
                }
            } catch (RuntimeException ignored) { /* AOSP has no BYD provider. */ }
        }
        return ThemePolicy.isNight(AppPreferences.get(context).getString(KEY, ThemePolicy.AUTO), night, daytime);
    }
}
