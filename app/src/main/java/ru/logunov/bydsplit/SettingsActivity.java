package ru.logunov.bydsplit;

/** Compatibility entry point for old shortcuts; settings live inside MainActivity. */
public final class SettingsActivity extends android.app.Activity {
    @Override protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        startActivity(new android.content.Intent(this, MainActivity.class)
                .putExtra("open_settings", true));
        finish();
    }
}
