package ru.logunov.bydsplit;

import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Shell permission is needed on DiLink. Only subscribes to power and cable state; never polls. */
public final class VehiclePowerBridge {
    private static final int POWER_DEVICE = 1023, POWER = 315621408;
    private static final int CHARGE_DEVICE = 1009, CABLE = 876609586;
    private static int power, cable;
    private static String previous;
    private static Socket owner;

    private static synchronized void emit() throws Exception {
        String line = "STATE " + power + " " + cable;
        if (line.equals(previous)) return;
        owner.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
        owner.getOutputStream().flush();
        previous = line;
    }

    private static Integer value(Object raw) {
        if (raw instanceof Integer) return (Integer) raw;
        if (raw instanceof int[] && ((int[])raw).length > 0) return ((int[])raw)[0];
        if (raw instanceof byte[] && ((byte[])raw).length == 1) return ((byte[])raw)[0] & 255;
        return null;
    }

    @android.annotation.SuppressLint("WrongConstant")
    public static void main(String[] args) {
        try {
            owner = HelperLifetime.attach(args);
            Looper.prepare();
            Object thread = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            Context context = (Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
            Object manager = context.getSystemService("auto");
            Class<?> type = Class.forName("android.hardware.BYDAutoManager$OnBYDAutoListener");
            Object listener = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, values) -> {
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == values[0];
                if (method.getName().equals("toString")) return "BYDSplitPowerObserver";
                if (method.getName().equals("onChanged") && values.length == 4) {
                    Integer raw = value(values[2]);
                    synchronized (VehiclePowerBridge.class) {
                        if (Integer.valueOf(POWER_DEVICE).equals(values[0]) && Integer.valueOf(POWER).equals(values[1])) power = raw == null ? -1 : raw;
                        else if (Integer.valueOf(CHARGE_DEVICE).equals(values[0]) && Integer.valueOf(CABLE).equals(values[1])) cable = raw == null ? -1 : raw;
                        else return null;
                        emit();
                    }
                }
                return null;
            });
            manager.getClass().getMethod("registerListener", type).invoke(manager, listener);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { manager.getClass().getMethod("disableDevice", int.class).invoke(manager, POWER_DEVICE); } catch (Exception ignored) { }
                try { manager.getClass().getMethod("disableDevice", int.class).invoke(manager, CHARGE_DEVICE); } catch (Exception ignored) { }
                try { manager.getClass().getMethod("unregisterListener", type).invoke(manager, listener); } catch (Exception ignored) { }
            }));
            for (int[] pair : new int[][]{{POWER_DEVICE, POWER}, {CHARGE_DEVICE, CABLE}}) {
                int result = (Integer)manager.getClass().getMethod("enableDevice", int.class, int[].class)
                        .invoke(manager, pair[0], new int[]{pair[1]});
                if (result != 0) throw new IllegalStateException("Power observer refused " + result);
            }
            IBinder service = (IBinder)Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "autoservice");
            service.linkToDeath(() -> System.exit(0), 0);
            synchronized (VehiclePowerBridge.class) {
                power = ComfortCommandWriter.transact(service, 5, POWER_DEVICE, POWER, 0);
                cable = ComfortCommandWriter.transact(service, 5, CHARGE_DEVICE, CABLE, 0);
                emit();
            }
            Looper.loop();
        } catch (Throwable error) { System.err.println("Power observer stopped: " + error); }
        System.exit(0);
    }
}
