package ru.logunov.bydsplit;

/** No dependence on Android, so theme precedence can be tested on the host. */
final class ThemePolicy {
    static final String AUTO = "auto", DAY = "day", NIGHT = "night";

    static boolean isNight(String mode, int uiNightMode, Boolean carDaytime) {
        if (DAY.equals(mode)) return false;
        if (NIGHT.equals(mode)) return true;
        // Di2 SystemUI consumes Configuration.uiMode; this is the selected theme.
        if (uiNightMode == 32) return true;
        if (uiNightMode == 16) return false;
        // CarSettings is only a fallback when Android does not publish a theme.
        return carDaytime == null || !carDaytime;
    }
}
