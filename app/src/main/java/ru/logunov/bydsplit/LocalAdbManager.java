package ru.logunov.bydsplit;

import android.content.Context;
import android.util.Log;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

final class LocalAdbManager {
    private static final String TAG = "BYD_LOCAL_ADB";
    private static final Pattern SAFE_APK_PATH =
            Pattern.compile("[A-Za-z0-9_./=+~-]+");
    private static final Pattern PARCEL_VALUE = Pattern.compile(
            "Parcel\\(00000000\\s+([0-9a-fA-F]{8})");
    private static LocalAdbManager instance;
    private static LocalAdbManager callInstance;
    static synchronized LocalAdbManager forCallRouting(Context context) {
        if(callInstance==null)callInstance=new LocalAdbManager(context.getApplicationContext());
        return callInstance;
    }

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
            command = "wm set-ignore-orientation-request -d 0 true; "
                    + "cmd notification allow_listener ru.logunov.bydsplit/ru.logunov.bydsplit.MaxCallNotificationService; "
                    + command;
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
            if("ru.oneme.app".equals(packageName)&&MaxCallRouter.active()
                    && moveMaxTasksToDisplay(displayId))return true;
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
                            + "' '/^RootTask id=/{split($2,p,\"=\");"
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

    synchronized Float[] readAutomationTemperatures() {
        Float[] result = new Float[2];
        try {
            client.connect();
            int[] fids = {1077936184, 1031798832};
            for (int i=0;i<2;i++) {
                Integer value = readAutomationInt(1000, fids[i]);
                if(value != null && value >= -60 && value <= 80) result[i]=value.floatValue();
            }
        } catch(Exception error) { Log.w(TAG,"Automation temperature unavailable",error); }
        return result;
    }

    private Integer readAutomationInt(int device, int fid) throws Exception {
        Matcher m=PARCEL_VALUE.matcher(client.shell("service call autoservice 5 i32 "+device+" i32 "+fid));
        return m.find() ? (int)Long.parseLong(m.group(1),16) : null;
    }

    // Song L setting API from the installed framework, not Leopard climate switches.
    synchronized String applyComfortRule(int action, int level) {
        int[] reads={0x4290000e,0x4fa0001b,0x4fa00023,0x4fa00018,0x4fa00020};
        int[] writes={0x1ea00035,0x23200014,0x2320001c,0x23200010,0x23200018};
        if(action<0||action>=5||level<0||level>(action==0?1:3)) return "Недопустимый уровень";
        try {
            client.connect();
            Integer before=readAutomationInt(1001,reads[action]);
            if(before==null||before<1||before>(action==0?2:4))
                return "Показание функции недоступно · команда не отправлена";
            int wanted=level+1;
            if(before==wanted)return null;
            Matcher reply=PARCEL_VALUE.matcher(client.shell("service call autoservice 6 i32 1001 i32 "+writes[action]+" i32 "+wanted));
            if(!reply.find()||(int)Long.parseLong(reply.group(1),16)<0)return "Автомобиль отклонил команду";
            for(int attempt=0;attempt<4;attempt++) {
                Thread.sleep(250);
                Integer actual=readAutomationInt(1001,reads[action]);
                if(actual!=null&&actual==wanted)return null;
            }
            return "Изменение не подтверждено автомобилем";
        } catch(Exception error) { return "Нет связи с автомобилем"; }
    }

    synchronized TechSnapshot readTechDetails() {
        BatteryDetailsSnapshot battery=readBatteryDetails();
        if(battery==null)return null;
        try {
            StringBuilder commands=new StringBuilder();
            for(Object[] f:TechSnapshot.FIELDS) {
                if(commands.length()>0)commands.append("; ");
                commands.append("service call autoservice ").append(f[3]).append(" i32 ").append(f[1]).append(" i32 ").append(f[2]);
            }
            Matcher m=PARCEL_VALUE.matcher(client.shell(commands.toString()));
            int[] raw=new int[TechSnapshot.FIELDS.length];int n=0;
            while(m.find()&&n<raw.length)raw[n++]=(int)Long.parseLong(m.group(1),16);
            return n==raw.length?new TechSnapshot(battery,raw):null;
        } catch(Exception error) { Log.w(TAG,"Tech panel unavailable",error);return null; }
    }

    synchronized BatteryDetailsSnapshot readBatteryDetails() {
        VehicleTelemetrySnapshot vehicle = readVehicleTelemetry();
        if (vehicle == null) return null;
        try {
            String output = client.shell(
                    "service call autoservice 5 i32 1014 i32 1147142160; "
                    + "service call autoservice 5 i32 1014 i32 1147142192; "
                    + "service call autoservice 7 i32 1014 i32 1032871984; "
                    + "service call autoservice 7 i32 1009 i32 666894360; "
                    + "service call autoservice 5 i32 1009 i32 876609560");
            Matcher matcher = PARCEL_VALUE.matcher(output);
            int[] raw = new int[5];
            int count = 0;
            while (matcher.find() && count < raw.length)
                raw[count++] = (int) Long.parseLong(matcher.group(1), 16);
            return count == raw.length ? new BatteryDetailsSnapshot(vehicle, raw) : null;
        } catch (Exception error) {
            Log.w(TAG, "Cannot read battery details", error);
            return null;
        }
    }

    synchronized VehicleTelemetrySnapshot readVehicleTelemetry() {
        try {
            client.connect();
            String output = client.shell(
                    "service call autoservice 7 i32 1013 i32 -1807745016; "
                            + "service call autoservice 5 i32 1016 i32 -1728052956; "
                            + "service call autoservice 5 i32 1016 i32 -1728052952; "
                            + "service call autoservice 5 i32 1016 i32 -1728052948; "
                            + "service call autoservice 5 i32 1016 i32 -1728052944; "
                            + "service call autoservice 5 i32 1007 i32 1246797848; "
                            + "service call autoservice 5 i32 1007 i32 1246797860; "
                            + "service call autoservice 5 i32 1007 i32 1246797872; "
                            + "service call autoservice 5 i32 1007 i32 1246797884; "
                            + "service call autoservice 5 i32 1006 i32 555745294; "
                            + "service call autoservice 5 i32 1006 i32 874512420; "
                            + "service call autoservice 7 i32 1014 i32 1246777400; "
                            + "service call autoservice 5 i32 1012 i32 339738656; "
                            + "service call autoservice 5 i32 1009 i32 876609586; "
                            + "service call autoservice 5 i32 1009 i32 876609592; "
                            + "service call autoservice 5 i32 1014 i32 1246785600; "
                            + "service call autoservice 5 i32 1014 i32 1145045032; "
                            + "service call autoservice 5 i32 1014 i32 1148190736; "
                            + "service call autoservice 5 i32 1014 i32 1148190752; "
                            + "service call autoservice 5 i32 1012 i32 282066952; "
                            + "service call autoservice 5 i32 1012 i32 1141899272; "
                            + "service call autoservice 5 i32 1014 i32 1033199672; "
                            + "service call autoservice 7 i32 1001 i32 1128267816; "
                            + "service call autoservice 5 i32 1000 i32 1077936144; "
                            + "service call autoservice 5 i32 1000 i32 1077936156; "
                            + "service call autoservice 5 i32 1000 i32 1077936152; "
                            + "service call autoservice 5 i32 1006 i32 873463832; "
                            + "service call autoservice 5 i32 1006 i32 603979827");
            int[] raw = new int[28];
            Matcher matcher = PARCEL_VALUE.matcher(output);
            int count = 0;
            while (matcher.find() && count < raw.length) {
                raw[count++] = (int) Long.parseLong(matcher.group(1), 16);
            }
            if (count != raw.length) {
                return null;
            }
            Float speed = decodeFloat(raw[0]);
            Integer speedKmh = speed != null && speed >= 0f && speed <= 300f
                    ? Math.round(speed) : null;
            return new VehicleTelemetrySnapshot(
                    speedKmh,
                    decodePressure(raw[1]), decodePressure(raw[2]),
                    decodePressure(raw[3]), decodePressure(raw[4]),
                    decodeTemperature(raw[5]), decodeTemperature(raw[6]),
                    decodeTemperature(raw[7]), decodeTemperature(raw[8]),
                    effectiveDriveMode(raw[9], raw[27]), decodeEnum(raw[10]), null,
                    decodePercent(raw[11]), decodePower(raw[12]),
                    decodeRpm(raw[19]), null, null,
                    decodeEnum(raw[13]), decodeEnum(raw[14]),
                    decodeIntPercent(raw[15]), decodeIntPercent(raw[16]),
                    decodeBatteryTemperature(raw[17]), decodeBatteryTemperature(raw[18]),
                    decodeRpm(raw[20]), decodeCoolantTemperature(raw[21]), decodeAuxiliaryVoltage(raw[22]),
                    ClimateIconState.decode(raw[23], raw[24], raw[25]), decodeEnergyState(raw[26]));
        } catch (Exception error) {
            client.close();
            Log.w(TAG, "Cannot read vehicle telemetry", error);
            return null;
        }
    }

    private static Integer decodeEnergyState(int raw) {
        return raw >= 0 && raw <= 33 ? raw : null;
    }

    /** Snowfield is a road-surface overlay; operation mode can still report SPORT. */
    private static Integer effectiveDriveMode(int operationMode, int roadSurfaceMode) {
        if (roadSurfaceMode == 2) {
            return 4;
        }
        return decodeEnum(operationMode);
    }

    private static Integer decodeCoolantTemperature(int raw) {
        return raw >= -40 && raw <= 150 ? raw : null;
    }

    private static Float decodeAuxiliaryVoltage(int raw) {
        Float value = decodeFloat(raw);
        return value != null && value >= 5f && value <= 20f ? value : null;
    }

    private static Integer decodeIntPercent(int raw) {
        return raw >= 0 && raw <= 100 ? raw : null;
    }

    private static Integer decodeBatteryTemperature(int raw) {
        return raw >= 0 && raw <= 165 ? raw - 40 : null;
    }

    private static Integer decodeRpm(int raw) {
        return raw >= 0 && raw <= 25000 ? raw : null;
    }

    private static Float decodeFloat(int raw) {
        if (isSentinel(raw)) {
            return null;
        }
        float value = Float.intBitsToFloat(raw);
        return Float.isFinite(value) && value != -1f ? value : null;
    }

    private static Integer decodePressure(int raw) {
        return !isSentinel(raw) && raw >= 100 && raw <= 500 ? raw : null;
    }

    private static Integer decodeTemperature(int raw) {
        return !isSentinel(raw) && raw >= -50 && raw <= 120 ? raw : null;
    }

    private static Integer decodeEnum(int raw) {
        return !isSentinel(raw) && raw >= 0 && raw <= 255 ? raw : null;
    }

    private static Float decodePercent(int raw) {
        Float value = decodeFloat(raw);
        return value != null && value >= 0f && value <= 100f ? value : null;
    }

    private static Integer decodePower(int raw) {
        return !isSentinel(raw) && raw >= -300 && raw <= 500 ? raw : null;
    }

    private static boolean isSentinel(int raw) {
        return raw == 0x0000FFFF || raw == 0x000FFFFF
                || raw == -10013 || raw == -10011;
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
