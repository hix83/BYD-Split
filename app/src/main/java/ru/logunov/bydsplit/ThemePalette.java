package ru.logunov.bydsplit;
import android.content.Context;
import android.content.res.Configuration;
/** Theme follows the DiLink / Android night-mode configuration. */
final class ThemePalette {
    static boolean dark(Context c){return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    static int color(Context c,int night,int day){return dark(c)?night:day;}
    static int text(Context c){return c.getColor(R.color.text_primary);}
    static int dialogTheme(Context c){return dark(c)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar;}
}
