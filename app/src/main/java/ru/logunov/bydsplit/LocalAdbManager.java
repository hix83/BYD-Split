package ru.logunov.bydsplit;

import android.content.Context;
import android.util.Log;

import java.util.regex.Pattern;

final class LocalAdbManager {
    private static final String TAG = "BYD_LOCAL_ADB";
    private static final Pattern SAFE_APK_PATH =
            Pattern.compile("[A-Za-z0-9_./=+~-]+");
    private static LocalAdbManager instance;

    private final Context context;
    private final LocalAdbClient client;

    static synchronized LocalAdbManager get(Context context) {
        if (instance == null) {
            instance = new LocalAdbManager(context.getApplicationContext());
        }
        return instance;
    }

    private LocalAdbManager(Context context) {
        this.context = context;
        client = new LocalAdbClient(new LocalAdbKeyStore(context));
    }

    synchronized boolean isConnected() {
        return client.isConnected();
    }

    synchronized boolean connect() {
        try {
            client.connect();
            return true;
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Local ADB authorization failed", error);
            return false;
        }
    }

    synchronized boolean startHelpers(boolean includeSteering) {
        String apk = context.getApplicationInfo().sourceDir;
        if (apk == null || !SAFE_APK_PATH.matcher(apk).matches()) {
            Log.e(TAG, "Unsafe APK path");
            return false;
        }
        try {
            String command = daemonCommand(apk,
                    "ru.logunov.bydsplit.ShellInputDaemon",
                    "byd-split-input.log", "");
            if (includeSteering) {
                String steeringArgs =
                        AppPreferences.getSteeringShortScan(context)
                                + " "
                                + AppPreferences.getSteeringLongScan(context);
                command += " " + daemonCommand(apk,
                        "ru.logunov.bydsplit.SteeringInputDaemon",
                        "byd-split-steering.log", steeringArgs);
                command += " " + accessibilityCommand();
            }
            client.shellV2(command);
            return true;
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot start embedded helpers", error);
            return false;
        }
    }

    synchronized boolean launchOnDisplay(
            String component, String packageName, int displayId) {
        try {
            client.connect();
            String output = client.shell(
                    "am start --display " + displayId
                            + " -n " + component
                            + "; sleep 0.2"
                            + "; task_id=$(am stack list | awk -v target='"
                            + packageName
                            + "/' '/^RootTask id=/{split($2,p,\"=\");"
                            + "root=p[2]} index($0,target)>0{print root;"
                            + "exit}'); [ -z \"$task_id\" ] || "
                            + "am display move-stack \"$task_id\" "
                            + displayId);
            return !output.contains("Error:")
                    && !output.contains("Exception");
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot launch on virtual display", error);
            return false;
        }
    }

    synchronized boolean injectBack(int displayId) {
        try {
            client.connect();
            client.shell("input -d " + displayId + " keyevent 4");
            return true;
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot inject Back", error);
            return false;
        }
    }

    synchronized boolean startMainActivity() {
        try {
            client.connect();
            String output = client.shell(
                    "am start -n ru.logunov.bydsplit/.MainActivity");
            return !output.contains("Error:")
                    && !output.contains("Exception");
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot foreground BYD Split", error);
            return false;
        }
    }

    private static String daemonCommand(
            String apk, String className, String logName, String arguments) {
        return "pkill -f '^app_process /system/bin "
                + className.replace(".", "\\.") + "( |$)' 2>/dev/null; "
                + "nohup env CLASSPATH=" + apk
                + " app_process /system/bin " + className
                + (arguments.isEmpty() ? "" : " " + arguments)
                + " </dev/null >/data/local/tmp/" + logName
                + " 2>&1 &";
    }

    private static String accessibilityCommand() {
        String service = "ru.logunov.bydsplit/"
                + "ru.logunov.bydsplit.SteeringAccessibilityService";
        return "service='" + service + "'; "
                + "enabled=$(settings get secure "
                + "enabled_accessibility_services); "
                + "case \":$enabled:\" in "
                + "*\":$service:\"*) ;; "
                + "\":null:\"|\"::\") enabled=\"$service\" ;; "
                + "*) enabled=\"$enabled:$service\" ;; "
                + "esac; "
                + "settings put secure enabled_accessibility_services "
                + "\"$enabled\"; "
                + "settings put secure accessibility_enabled 1;";
    }
}
