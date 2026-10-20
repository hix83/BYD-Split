package ru.logunov.bydsplit;

import android.content.Context;
import android.content.res.Configuration;

final class ThemeMode {
    static Context wrap(Context base) {
        int mode = AppPreferences.get(base).getInt("theme_mode", 0);
        if (mode == 0) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (mode == 2 ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(config);
    }
}
