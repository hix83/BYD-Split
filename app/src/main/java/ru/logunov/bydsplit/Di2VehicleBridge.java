package ru.logunov.bydsplit;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import org.json.JSONObject;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/** Runs as the authorized ADB shell, using the vehicle's own framework SDK.
 * No bundled vendor jar, hidden Binder transactions or fabricated sensor fallback. */
public final class Di2VehicleBridge {
    private static volatile String lastSdkFailure;
    static final String PREFIX = "BYD_DI2_V1 ";

    public static void main(String[] args) {
        JSONObject output = new JSONObject();
        int exitCode = 0;
        try {
            output.put("protocol", 1);
            output.put("androidApi", Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT != 28) throw new IllegalStateException("Di2_requires_API_28");
            String operation = args.length == 0 ? "read" : args[0];
            // Reject malformed writes before initialization or SDK calls.
            int value = 0;
            if (!operation.equals("read") && !operation.equals("climate")) {
                if (args.length != 2) throw new IllegalArgumentException("Expected_command_and_integer");
                value = Integer.parseInt(args[1]);
                Di2VehicleSdk.command(operation, value);
            } else if (args.length > 1) throw new IllegalArgumentException("Unexpected_arguments");
            if (Looper.myLooper() == null) Looper.prepareMainLooper();
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Object thread = threadClass.getMethod("systemMain").invoke(null);
            Context context = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
            Di2VehicleSdk sdk = new Di2VehicleSdk(new FrameworkAccess(context));
            if (!operation.equals("read") && !operation.equals("climate")) {
                String result = sdk.set(operation, value, () -> Thread.sleep(250));
                output.put("result", result);
                output.put("ok", "confirmed".equals(result));
                if (!"confirmed".equals(result)) exitCode = 2;
            }
            Di2VehicleSdk.Reading reading = operation.equals("read") ? sdk.read() : sdk.climate();
            output.put("values", new JSONObject(reading.values));
            output.put("errors", new JSONObject(reading.errors));
            if (!output.has("ok")) output.put("ok", !reading.values.isEmpty());
            if (!output.optBoolean("ok")) exitCode = 2;
        } catch (Exception e) {
            try { output.put("ok", false); output.put("error", Di2VehicleSdk.errorName(e)); }
            catch (Exception ignored) { }
            exitCode = 2;
        }
        System.out.println(PREFIX + output);
        // BYD SDK constructors may start Binder/Handler threads. Finish the shell request.
        System.exit(exitCode);
    }

    /** OEM COMMON permissions belong to the APK UID, so use its real Context. */
    static JSONObject local(Context context,String operation,Integer value) throws Exception {
        Di2VehicleSdk sdk=new Di2VehicleSdk(new FrameworkAccess(context));
        JSONObject output=new JSONObject();output.put("protocol",1);output.put("androidApi",Build.VERSION.SDK_INT);
        if(!operation.equals("read")&&!operation.equals("climate")){
            if(value==null)throw new IllegalArgumentException("Missing value");
            String result=sdk.set(operation,value,()->Thread.sleep(250));
            output.put("result",result);output.put("ok","confirmed".equals(result));
        }
        Di2VehicleSdk.Reading reading=operation.equals("read")?sdk.read():sdk.climate();
        output.put("values",new JSONObject(reading.values));output.put("errors",new JSONObject(reading.errors));
        if(!output.has("ok"))output.put("ok",!reading.values.isEmpty());
        if(lastSdkFailure!=null)output.put("sdkFailure",lastSdkFailure);
        return output;
    }

    private static final class FrameworkAccess implements Di2VehicleSdk.Access {
        private final Context context;
        private final Map<String, Object> devices = new HashMap<>();
        FrameworkAccess(Context context) { this.context = context; }
        @Override public Number call(String device, String method, int... arguments) throws Exception {
            Object instance = devices.get(device);
            if (instance == null) {
                String className;
                switch (device) {
                    case "ac": className = "ac.BYDAutoAcDevice"; break;
                    case "speed": className = "speed.BYDAutoSpeedDevice"; break;
                    case "statistic": className = "statistic.BYDAutoStatisticDevice"; break;
                    case "tyre": className = "tyre.BYDAutoTyreDevice"; break;
                    default: throw new IllegalArgumentException("Unknown SDK device");
                }
                Class<?> type = Class.forName("android.hardware.bydauto." + className);
                try { instance = type.getMethod("getInstance", Context.class).invoke(null, context); }
                catch(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();lastSdkFailure=cause.toString();throw e;}
                if (instance == null) throw new IllegalStateException("SDK device unavailable");
                devices.put(device, instance);
            }
            Class<?>[] types = new Class<?>[arguments.length];
            Object[] values = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) { types[i] = int.class; values[i] = arguments[i]; }
            try {
                return (Number) instance.getClass().getMethod(method, types).invoke(instance, values);
            } catch (InvocationTargetException e) {
                lastSdkFailure=String.valueOf(e.getCause());
                if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
                throw e;
            }
        }
    }
}
