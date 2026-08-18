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
                            + " -f 0x00010000 -n " + component
                            + "; sleep 0.2"
                            + "; task_id=$(am stack list | awk -v target='"
                            + packageName
                            + "/' -v display='displayId=" + displayId
                            + "' '/^RootTask id=/{split($2,p,\"=\");"
                            + "root=p[2]; on_display=index($0,display)>0} "
                            + "on_display && index($0,target)>0{print root;"
                            + "exit}'); if [ -z \"$task_id\" ]; then "
                            + "task_id=$(am stack list | awk -v target='"
                            + packageName
                            + "/' '/^RootTask id=/{split($2,p,\"=\");"
                            + "root=p[2]} index($0,target)>0{print root;"
                            + "exit}'); [ -z \"$task_id\" ] || "
                            + "am display move-stack \"$task_id\" "
                            + displayId + " >/dev/null 2>&1; sleep 0.1; fi"
                            + "; am stack list | awk -v target='"
                            + packageName
                            + "/' -v display='displayId=" + displayId
                            + "' '/^RootTask id=/{on_display="
                            + "index($0,display)>0} on_display && "
                            + "index($0,target)>0{print \"BYD_SPLIT_LAUNCH_OK\";"
                            + "exit}'");
            return output.contains("BYD_SPLIT_LAUNCH_OK");
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot launch on virtual display", error);
            return false;
        }
    }

    synchronized boolean removeFromDisplay(
            String packageName, int displayId) {
        if (!packageName.matches("[A-Za-z0-9._]+")
                || displayId < 1 || displayId > 999) {
            return false;
        }
        try {
            client.connect();
            String output = client.shell(
                    "task_id=$(am stack list | awk -v target='"
                            + packageName
                            + "/' -v display='displayId="
                            + displayId
                            + "' '/^RootTask id=/{split($2,p,\"=\");"
                            + "root=p[2]; on_display=index($0,display)>0} "
                            + "on_display && index($0,target)>0{print root;"
                            + "exit}'); [ -z \"$task_id\" ] || "
                            + "am stack remove \"$task_id\"");
            return !output.contains("Error:")
                    && !output.contains("Exception");
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot remove task from virtual display", error);
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
        return "for pid in $(pidof app_process); do "
                + "cmd=$(tr '\\0' ' ' </proc/$pid/cmdline); "
                + "case \"$cmd\" in "
                + "\"app_process /system/bin " + className + "\"*"
                + "|\"/system/bin/app_process /system/bin "
                + className + "\"*) "
                + "kill \"$pid\" 2>/dev/null;; esac; done; "
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
