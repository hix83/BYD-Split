package ru.logunov.bydsplit;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

final class AppEntry {
    final String label;
    final ComponentName component;
    final Drawable icon;

    AppEntry(String label, ComponentName component, Drawable icon) {
        this.label = label;
        this.component = component;
        this.icon = icon;
    }
}
