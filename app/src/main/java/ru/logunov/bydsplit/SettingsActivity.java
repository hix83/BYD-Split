package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility entry point for existing notifications; settings live inside MainActivity. */
public final class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, MainActivity.class).putExtra("open_settings", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
