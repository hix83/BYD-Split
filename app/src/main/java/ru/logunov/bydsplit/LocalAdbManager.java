package ru.logunov.bydsplit;

import android.content.Context;
import android.util.Log;

import java.util.regex.Pattern;
import android.os.Build;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class LocalAdbManager {
    private static final String TAG = "BYD_LOCAL_ADB";
    private static final Pattern SAFE_APK_PATH =
            Pattern.compile("[A-Za-z0-9_./=+~-]+");
    private static LocalAdbManager instance;
    private static LocalAdbManager callInstance;
    static synchronized LocalAdbManager forCallRouting(Context context) {
        if(callInstance==null)callInstance=new LocalAdbManager(context.getApplicationContext());
        return callInstance;
    }

    private final Context context;
    private final LocalAdbClient client;
    private volatile Thread displayHelperThread;
    private final Map<Integer, String> immersiveDisplays = new HashMap<>();
    private boolean recoveredImmersive;

    // Persist ownership so a process crash can be cleaned up on the next start.
    private void updateEmbeddedImmersive() throws Exception {
        if (Build.VERSION.SDK_INT != 28) return;
        android.content.SharedPreferences prefs = AppPreferences.get(context);
        String current = client.shell("settings get global policy_control").trim();
        if ("null".equals(current)) current = "";
        String owned = prefs.getString("embedded_immersive_written", null);
        String original = prefs.getString("embedded_immersive_original", "");
        if (owned != null && !current.equals(owned)) {
            // A user or another app changed the setting: relinquish ownership.
            prefs.edit().remove("embedded_immersive_written").remove("embedded_immersive_original").commit();
            return;
        }
        if (owned == null) original = current;
        java.util.Set<String> packages = new java.util.TreeSet<>();
        if (AppPreferences.isFullscreenEnabled(context)) packages.addAll(immersiveDisplays.values());
        String desired = EmbeddedImmersivePolicy.withPackages(original, packages);
        if (!current.equals(desired)) {
            // Write the recovery record before changing the system setting.
            prefs.edit().putString("embedded_immersive_original", original)
                    .putString("embedded_immersive_written", desired).commit();
            String output = client.shell(desired.isEmpty() ? "settings delete global policy_control"
                    : "settings put global policy_control '" + desired.replace("'", "'\"'\"'") + "'");
            if (output.contains("Exception") || output.contains("Error")) throw new java.io.IOException(output);
        }
        if (packages.isEmpty()) prefs.edit().remove("embedded_immersive_written")
                .remove("embedded_immersive_original").commit();
    }

    synchronized void releaseEmbeddedImmersive(int displayId) {
        immersiveDisplays.remove(displayId);
        refreshEmbeddedImmersive();
    }

    synchronized void refreshEmbeddedImmersive() {
        try { client.connect(); updateEmbeddedImmersive(); }
        catch (Exception error) { Log.w(TAG, "Cannot update embedded immersive policy", error); }
    }

    private void ensureDisplayHelper(String apk) {
        if (ShellDisplayBridge.ready() || (displayHelperThread != null && displayHelperThread.isAlive())) return;
        displayHelperThread = new Thread(() -> {
            try (LocalAdbClient helper = new LocalAdbClient(new LocalAdbKeyStore(context))) {
                helper.persistentShell("CLASSPATH=" + apk
                        + " app_process /system/bin ru.logunov.bydsplit.ShellDisplayDaemon");
            } catch (Exception e) { Log.e(TAG, "Display helper transport stopped", e); }
        }, "BYD-display-shell");
        displayHelperThread.setDaemon(true);
        displayHelperThread.start();
    }

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

    synchronized String writeClusterMusic(String args) throws Exception {
        String apk=context.getApplicationInfo().sourceDir;
        if(!SAFE_APK_PATH.matcher(apk).matches()||!args.matches("[A-Za-z0-9+/= ]+"))return "ERROR";
        client.connect();
        return client.shell("CLASSPATH="+apk+" app_process /system/bin ru.logunov.bydsplit.ClusterMusicWriter "+args);
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
            if (Build.VERSION.SDK_INT == 28) {
                if (!recoveredImmersive) {
                    client.connect();
                    updateEmbeddedImmersive();
                    recoveredImmersive = true;
                }
                ensureDisplayHelper(apk);
            }
            String command = Build.VERSION.SDK_INT == 28 ? "" : daemonCommand(apk,
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
            command = (Build.VERSION.SDK_INT >= 31 ? "wm set-ignore-orientation-request -d 0 true; " : "")
                    + "cmd notification allow_listener ru.logunov.bydsplit/ru.logunov.bydsplit.MaxCallNotificationService; "
                    + command;
            if (Build.VERSION.SDK_INT == 28 && !includeSteering) client.shell(command);
            else client.shellDetached(command);
            if (Build.VERSION.SDK_INT == 28) {
                for (int i = 0; i < 60 && !ShellDisplayBridge.ready(); i++) Thread.sleep(50);
                return ShellDisplayBridge.ready();
            }
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
            if("ru.oneme.app".equals(packageName)&&MaxCallRouter.active()
                    && moveMaxTasksToDisplay(displayId))return true;
            if (!component.matches("[A-Za-z0-9._$/]+") || !packageName.matches("[A-Za-z0-9._]+")
                    || displayId < 1 || displayId > 999) return false;
            if (Build.VERSION.SDK_INT == 28) {
                immersiveDisplays.put(displayId, packageName);
                try { updateEmbeddedImmersive(); }
                catch (Exception policyError) { Log.w(TAG, "Embedded bars policy unavailable", policyError); }
            }
            String output = client.shell("am start --display " + displayId
                    + " -f 0x00010000 -n " + component + "; sleep 0.2");
            if (output.contains("Error:") || output.contains("Exception")) {
                Log.w(TAG, "Embedded launch rejected: " + output); return false;
            }
            String tasks = client.shell("am stack list");
            if (EmbeddedTaskParser.root(tasks, packageName, displayId, true) == null) {
                Integer root = EmbeddedTaskParser.root(tasks, packageName, null, false);
                if (root == null) return false;
                String moved = client.shell("am display move-stack " + root + " " + displayId);
                if (moved.contains("Error") || moved.contains("Exception")) return false;
            }
            // API 28 may open a delayed onboarding/entry Activity in a new stack on
            // display 0 even after the initial task was moved. Follow that startup
            // redirect for a bounded interval; never move another package's stack.
            int settleReads = android.os.Build.VERSION.SDK_INT == 28 ? 8 : 1;
            for (int i = 0; i < settleReads; i++) {
                tasks = client.shell("sleep 0.3; am stack list");
                Integer redirected = EmbeddedTaskParser.root(tasks, packageName, 0, true);
                if (redirected != null) {
                    String moved = client.shell("am display move-stack " + redirected + " " + displayId);
                    if (moved.contains("Error") || moved.contains("Exception")) return false;
                }
            }
            // A transient entry during a crash is not a successful launch.
            tasks = client.shell("am stack list");
            boolean visible = EmbeddedTaskParser.root(tasks, packageName, displayId, true) != null;
            // Reparenting a stack on API 28 can itself return focus to display 0.
            // Focus the guest task explicitly, never the host: a paused guest can
            // stop its GL renderer even though its UI overlays remain visible.
            if (visible && Build.VERSION.SDK_INT == 28) {
                Integer task = EmbeddedTaskParser.task(tasks, packageName, displayId);
                if (task != null) client.shell("am task focus " + task);
            }
            return visible;
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot launch on virtual display", error);
            return false;
        }
    }

    synchronized void focusEmbeddedTask(String packageName, int displayId) {
        if (Build.VERSION.SDK_INT != 28 || !packageName.matches("[A-Za-z0-9._]+") || displayId < 1) return;
        try {
            client.connect();
            Integer task = EmbeddedTaskParser.task(client.shell("am stack list"), packageName, displayId);
            if (task != null) client.shell("am task focus " + task);
        } catch (Exception error) { Log.w(TAG, "Cannot resume embedded task", error); }
    }

    synchronized boolean moveMaxTasksToDisplay(int displayId) {
        if(displayId<1||displayId>999)return false;
        try {
            client.connect();
            String output=client.shell("am stack list");
            java.util.List<Integer> roots=MaxTaskParser.rootsToMove(output,displayId);
            boolean found=MaxTaskParser.hasMaxOnDisplay(output,displayId);
            for(int root:roots) {
                client.shell("am display move-stack "+root+" "+displayId);
            }
            if(!roots.isEmpty()) {
                found=MaxTaskParser.hasMaxOnDisplay(client.shell("am stack list"),displayId);
                if(found)client.shell("am start -n ru.logunov.bydsplit/.MainActivity");
            }
            return found;
        } catch(Exception error) {Log.w(TAG,"MAX call routing failed",error);return false;}
    }

    synchronized boolean closeApp(
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
                            + "' '/^(RootTask|Stack) id=/{split($2,p,\"=\");"
                            + "root=p[2]; on_display=index($0,display)>0} "
                            + "on_display && index($0,target)>0{print root;"
                            + "exit}'); [ -z \"$task_id\" ] || "
                            + "am stack remove \"$task_id\" "
                            + ">/dev/null 2>&1; am force-stop "
                            + packageName + "; sleep 0.25; "
                            + "alive=$(ps -A | awk -v pkg='"
                            + packageName
                            + "' '$NF==pkg || index($NF,pkg \":\")==1"
                            + "{print 1; exit}'); [ -n \"$alive\" ] || "
                            + "echo BYD_SPLIT_CLOSE_OK");
            return output.contains("BYD_SPLIT_CLOSE_OK");
        } catch (Exception error) {
            client.close();
            Log.e(TAG, "Cannot fully close embedded app", error);
            return false;
        }
    }

    synchronized boolean injectBack(int displayId) {
        if (displayId < 0 || displayId > 999) return false;
        try {
            client.connect();
            String apk = context.getApplicationInfo().sourceDir;
            if (apk == null || !SAFE_APK_PATH.matcher(apk).matches()) return false;
            return client.shell("CLASSPATH=" + apk
                    + " timeout 4 app_process /system/bin ru.logunov.bydsplit.ShellBackKey "
                    + displayId).contains("BYD_SPLIT_BACK_OK");
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

    // Di2 sensor offsets are not established. Never run rules using Di5 IDs.
    synchronized Float[] readAutomationTemperatures() { return new Float[2]; }

    synchronized String applyComfortRule(int action, int level) {
        return "Обогрев руля и сидений через Di2 SDK пока не подтверждён";
    }

    private final Object batteryReadLock = new Object();
    private long batteryReadAt;
    private Map<String, Double> batteryValues = new HashMap<>();

    private Map<String, Double> readDi2Battery() {
        synchronized (batteryReadLock) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (batteryReadAt != 0 && now - batteryReadAt < 30_000) return batteryValues;
            batteryReadAt = now;
            batteryValues = new HashMap<>();
            // Dedicated connection: display-helper bootstrap must not block telemetry.
            try (LocalAdbClient reader = new LocalAdbClient(new LocalAdbKeyStore(context))) {
                reader.connect();
                String command = "";
                String[] labels = {"SOH", "MIN_TEMP", "MAX_TEMP", "VOLTAGE", "MIN_CELL", "MAX_CELL"};
                int[] tx = {5, 5, 5, 7, 7, 7};
                int[] dev = {1014, 1014, 1014, 1001, 1014, 1014};
                int[] fid = {1145045032, 1148190736, 1148190752, 1128267816, 1147142160, 1147142192};
                for (int i = 0; i < labels.length; i++) {
                    command += "r=$(service call autoservice " + tx[i] + " i32 " + dev[i]
                            + " i32 " + fid[i] + "); echo \"" + labels[i] + "=$r\"; ";
                }
                batteryValues = Di2BatteryValues.parse(reader.shell(command));
            } catch (Exception error) { Log.w(TAG, "Di2 battery supplement unavailable", error); }
            return batteryValues;
        }
    }

    JSONObject readDi2(String operation, Integer value) throws Exception {
        if (Build.VERSION.SDK_INT != 28) throw new IllegalStateException("Di2 requires Android 9");
        if (!operation.equals("read") && !operation.equals("climate")) {
            if(value==null)throw new IllegalArgumentException("Missing climate value");
            Di2VehicleSdk.command(operation,value);
        } else if(value!=null)throw new IllegalArgumentException("Unexpected value");
        if(operation.equals("read")) {
            try {
                JSONObject reply = DiPlusReader.read();
                JSONObject values = reply.getJSONObject("values");
                Map<String, Double> supplement = readDi2Battery();
                for (Map.Entry<String, Double> entry : supplement.entrySet()) {
                    if (!values.has(entry.getKey())) values.put(entry.getKey(), entry.getValue());
                }
                reply.put("batterySource", "autoservice Di2");
                return reply;
            }
            catch(Exception unavailable){
                JSONObject fallback=Di2VehicleBridge.local(context,operation,value);
                fallback.put("source","SDK");fallback.put("diplusError",unavailable.toString());return fallback;
            }
        }
        return Di2VehicleBridge.local(context,operation,value);
    }

    synchronized String setDi2Climate(String command, int value) {
        try {
            JSONObject reply = readDi2(command, value);
            if (reply.optBoolean("ok") && "confirmed".equals(reply.optString("result"))) return null;
            String result = reply.optString("result", reply.optString("error", "unavailable"));
            switch (result) {
                case "read_unavailable": return "Состояние недоступно · команда не отправлена";
                case "celsius_required": return "Сначала выберите °C в настройках автомобиля";
                case "unconfirmed": return "Изменение не подтверждено автомобилем";
                case "rejected": return "SDK автомобиля отклонил команду";
                case "SecurityException": return "У ADB shell нет разрешения BYD SDK";
                default: return "Климат Di2 недоступен: " + result;
            }
        } catch (Exception e) { return "Нет связи с помощником Di2: " + Di2VehicleSdk.errorName(e); }
    }

    static Map<String, Double> di2Values(JSONObject reply) {
        Map<String, Double> values = new HashMap<>();
        JSONObject data = reply.optJSONObject("values");
        if (data != null) {
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                double number = data.optDouble(key, Double.NaN);
                if (Double.isFinite(number)) values.put(key, number);
            }
        }
        return values;
    }

    TechSnapshot readTechDetails() {
        try {
            Map<String, Double> values = di2Values(readDi2("read", null));
            return new TechSnapshot(Di2Snapshot.battery(values), values);
        } catch (Exception e) { Log.w(TAG, "Di2 technical data unavailable", e); return null; }
    }

    BatteryDetailsSnapshot readBatteryDetails() {
        try { return Di2Snapshot.battery(di2Values(readDi2("read", null))); }
        catch (Exception e) { Log.w(TAG, "Di2 battery details unavailable", e); return null; }
    }

    VehicleTelemetrySnapshot readVehicleTelemetry() {
        try { return Di2Snapshot.vehicle(di2Values(readDi2("read", null))); }
        catch (Exception e) { Log.w(TAG, "Di2 telemetry unavailable", e); return null; }
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
