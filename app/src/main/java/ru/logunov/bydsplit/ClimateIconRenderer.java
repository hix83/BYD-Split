package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Original UI7 vector paths split into independently illuminated indicators. */
final class ClimateIconRenderer {
    private final Drawable[] parts;
    ClimateIconRenderer(Context context) {
        int[] ids = {R.drawable.ui7_climate_person, R.drawable.ui7_climate_feet,
                R.drawable.ui7_climate_face, R.drawable.ui7_climate_glass,
                R.drawable.ui7_climate_fan_1, R.drawable.ui7_climate_fan_2,
                R.drawable.ui7_climate_fan_3, R.drawable.ui7_climate_fan_4,
                R.drawable.ui7_climate_fan_5, R.drawable.ui7_climate_fan_6,
                R.drawable.ui7_climate_fan_7};
        parts = new Drawable[ids.length];
        for (int i = 0; i < ids.length; i++) parts[i] = context.getDrawable(ids[i]).mutate();
    }

    void draw(Canvas canvas, int x, int y, int radius, ClimateIconState state) {
        for (int i = 0; i < parts.length; i++) {
            boolean active = i == 0 || (i == 1 && state.feet)
                    || (i == 2 && state.face) || (i == 3 && state.glass)
                    || (i >= 4 && i - 3 <= state.fanLevel);
            Drawable part = parts[i];
            part.setAlpha(active ? 255 : 89);
            part.setBounds(x-radius, y-radius, x+radius, y+radius);
            part.draw(canvas);
        }
    }
}
