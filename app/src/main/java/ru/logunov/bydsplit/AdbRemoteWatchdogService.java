package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.util.concurrent.*;

/** Checks the local relay process; an Internet outage alone must not relaunch it. */
public final class AdbRemoteWatchdogService extends Service {
    static final String KEY = "adb_remote_watchdog";
    private static final String PACKAGE = "ru.slksoft.dilink";
    private static final String LAST_ATTEMPT = "adb_remote_watchdog_last_attempt";
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile LocalAdbClient activeReader;
    private volatile boolean destroyed;
    private boolean duraSpeedProtected;

    static void sync(Context context) {
        Intent service = new Intent(context, AdbRemoteWatchdogService.class);
        if (AppPreferences.get(context).getBoolean(KEY, false) && Build.VERSION.SDK_INT == 28) {
            context.startForegroundService(service);
        } else context.stopService(service);
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("adb_remote_watchdog",
                "Контроль ADB Remote", NotificationManager.IMPORTANCE_MIN));
        PendingIntent settings = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(204, new Notification.Builder(this, "adb_remote_watchdog")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("Контроль DiLink Remote ADB")
                .setContentText("Автоматическое восстановление включено")
                .setContentIntent(settings).setOngoing(true).build());
        worker.scheduleWithFixedDelay(this::checkRelay, 3, 15, TimeUnit.SECONDS);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AppPreferences.get(this).getBoolean(KEY, false)) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    private void checkRelay() {
        if (destroyed || !AppPreferences.get(this).getBoolean(KEY, false)) return;
        try {
            getPackageManager().getPackageInfo(PACKAGE, 0);
            Boolean alive;
            try (LocalAdbClient reader = new LocalAdbClient(new LocalAdbKeyStore(this))) {
                activeReader = reader;
                reader.connect();
                if (!duraSpeedProtected) {
                    // MTK DuraSpeed force-stops even a foreground relay under CPU load.
                    // Add only our optional watchdog target; never disable the policy globally.
                    String protection = reader.shell("dumpsys duraspeed addwhitelist ru.slksoft.dilink; dumpsys duraspeed config");
                    duraSpeedProtected = protection.contains("PlatformWhitelist:")
                            && protection.contains("ru.slksoft.dilink");
                }
                String response = reader.shell("pidof ru.slksoft.dilink:relay; echo BYD_RELAY_CHECK_DONE");
                if (!response.contains("BYD_RELAY_CHECK_DONE")) return;
                String pids = response.substring(0, response.indexOf("BYD_RELAY_CHECK_DONE")).trim();
                if (!pids.isEmpty() && !pids.matches("[0-9 ]+")) return;
                alive = !pids.isEmpty();
            } finally { activeReader = null; }
            long now = System.currentTimeMillis();
            if (!AdbRemoteWatchdogPolicy.shouldRestart(AppPreferences.get(this).getBoolean(KEY, false), alive,
                    now, AppPreferences.get(this).getLong(LAST_ATTEMPT, 0))) return;
            AppPreferences.get(this).edit().putLong(LAST_ATTEMPT, now).apply();
            main.post(() -> {
                if (destroyed || !AppPreferences.get(this).getBoolean(KEY, false)) return;
                Intent launch = getPackageManager().getLaunchIntentForPackage(PACKAGE);
                if (launch == null) return;
                try {
                    startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    Log.i("BYD_ADB_WATCHDOG", "Restarted DiLink Remote ADB: relay process absent");
                } catch (RuntimeException error) { Log.w("BYD_ADB_WATCHDOG", "Relay restart rejected", error); }
            });
        } catch (Exception error) {
            // Unknown health is not evidence that the relay crashed.
            Log.d("BYD_ADB_WATCHDOG", "Relay check unavailable: " + error.getClass().getSimpleName());
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        LocalAdbClient reader = activeReader;
        if (reader != null) {
            Thread closer = new Thread(reader::close, "adb-watchdog-close");
            closer.setDaemon(true);
            closer.start();
        }
        worker.shutdownNow();
        stopForeground(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
