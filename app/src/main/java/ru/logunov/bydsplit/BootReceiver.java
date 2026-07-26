package ru.logunov.bydsplit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BYD_SPLIT_BOOT";
    private static final String ACTION_QUICK_BOOT =
            "android.intent.action.QUICKBOOT_POWERON";
    private static final String ACTION_TEST_BOOT =
            "ru.logunov.bydsplit.action.TEST_BOOT";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean bootCompleted = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || ACTION_QUICK_BOOT.equals(action)
                || Intent.ACTION_USER_PRESENT.equals(action);
        boolean debugTest = AppPreferences.isDebuggable(context)
                && ACTION_TEST_BOOT.equals(action);
        if (!bootCompleted && !debugTest) {
            return;
        }
        if (!AppPreferences.isAutoStartEnabled(context)) {
            Log.i(TAG, "Autostart disabled, ignored " + action);
            return;
        }
        if (Intent.ACTION_USER_PRESENT.equals(action)
                && MainActivity.isActive()) {
            Log.i(TAG, "Already active, ignored USER_PRESENT");
            return;
        }

        Log.i(TAG, "Launching after " + action);
        Intent launchIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(launchIntent);
        } catch (RuntimeException error) {
            Log.e(TAG, "Autostart failed after " + action, error);
        }

        if (!AppPreferences.isDemoModeEnabled(context)) {
            PendingResult pendingResult = goAsync();
            Thread foregroundThread = new Thread(() -> {
                try {
                    boolean success = LocalAdbManager.get(context)
                            .startMainActivity();
                    Log.i(TAG, "Shell foreground result=" + success);
                } finally {
                    pendingResult.finish();
                }
            }, "byd-split-autostart");
            foregroundThread.start();
        }
    }
}
