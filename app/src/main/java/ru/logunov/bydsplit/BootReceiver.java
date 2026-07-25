package ru.logunov.bydsplit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    private static final String ACTION_TEST_BOOT =
            "ru.logunov.bydsplit.action.TEST_BOOT";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean bootCompleted =
                Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction());
        boolean debugTest = AppPreferences.isDebuggable(context)
                && ACTION_TEST_BOOT.equals(intent.getAction());
        if (!bootCompleted && !debugTest) {
            return;
        }
        if (!AppPreferences.isAutoStartEnabled(context)) {
            return;
        }

        Intent launchIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launchIntent);
    }
}
