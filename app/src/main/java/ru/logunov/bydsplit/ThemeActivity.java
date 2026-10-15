package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

/** Apply a local override without changing the vehicle's system theme. */
abstract class ThemeActivity extends Activity {
    private boolean createdNight;
    private final ContentObserver carThemeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { refreshTheme(); }
    };

    private void refreshTheme() {
        if (createdNight != ThemeManager.isNight(getApplicationContext())) recreate();
    }

    @Override protected void attachBaseContext(Context base) {
        createdNight = ThemeManager.isNight(base);
        int systemNight = base.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (ThemePolicy.AUTO.equals(AppPreferences.get(base).getString(ThemeManager.KEY, ThemePolicy.AUTO))
                && systemNight != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            super.attachBaseContext(base);
            return;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (createdNight ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override protected void onResume() {
        super.onResume();
        refreshTheme();
        try {
            getContentResolver().registerContentObserver(Uri.parse("content://carsettings/global"), true, carThemeObserver);
        } catch (RuntimeException ignored) { /* Not every firmware exposes CarSettings. */ }
    }

    @Override protected void onPause() {
        getContentResolver().unregisterContentObserver(carThemeObserver);
        super.onPause();
    }
}
