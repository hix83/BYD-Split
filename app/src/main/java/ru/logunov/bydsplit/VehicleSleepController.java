package ru.logunov.bydsplit;

import android.content.*;
import android.os.*;
import android.util.Log;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared by the launcher and recording services. Sleep has no ADB polling or wakeup alarm. */
final class VehicleSleepController {
    private static VehicleSleepController instance;
    static synchronized VehicleSleepController get(Context context) {
        if (instance == null) instance = new VehicleSleepController(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<Object,Runnable> listeners = new LinkedHashMap<>();
    private final VehicleSleepState policy = new VehicleSleepState();
    private volatile VehicleSleepState.Mode mode = VehicleSleepState.Mode.UNKNOWN;
    private HelperSession observer;
    private int generation;
    private boolean registered;
    private volatile String lastState="нет показаний";
    private volatile boolean observerLive;
    private final Runnable startupTimeout=()->{if(!observerLive)lost(generation);};
    private final Runnable confirm = () -> publish(policy.confirm(SystemClock.elapsedRealtime()));
    private final Runnable retry = this::startObserver;
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            // A screen/power event only requests a fresh state; it cannot declare READY.
            publish(policy.invalidate(SystemClock.elapsedRealtime()));
            if (observer == null) startObserver();
            else requestSnapshot();
        }
    };
    private VehicleSleepController(Context context) { this.context = context; }
    VehicleSleepState.Mode mode() { return mode; }
    String describe(){return "mode="+mode+" observer="+observerLive+" state="+lastState;}
    boolean pollingAllowed() { return mode != VehicleSleepState.Mode.SLEEP; }
    boolean automationAllowed() { return mode == VehicleSleepState.Mode.RUNNING; }
    boolean gpsAllowed() { return mode == VehicleSleepState.Mode.RUNNING || mode == VehicleSleepState.Mode.UNKNOWN; }
    void addListener(Object owner, Runnable listener) {
        boolean first = listeners.isEmpty();
        listeners.put(owner, listener);
        if (first && !AppPreferences.isDemoModeEnabled(context)) {
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_POWER_CONNECTED); f.addAction(Intent.ACTION_POWER_DISCONNECTED);
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(wake, f, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(wake, f);
            registered = true;
            startObserver();
        }
        listener.run();
    }
    void removeListener(Object owner) {
        listeners.remove(owner);
        if (!listeners.isEmpty()) return;
        generation++;
        main.removeCallbacks(confirm); main.removeCallbacks(retry);
        main.removeCallbacks(startupTimeout);observerLive=false;
        if (observer != null) observer.close(); observer = null;
        if (registered) context.unregisterReceiver(wake); registered = false;
        mode = policy.invalidate(SystemClock.elapsedRealtime());
    }
    private void startObserver() {
        if (listeners.isEmpty() || observer != null || AppPreferences.isDemoModeEnabled(context)) return;
        final int token = ++generation;
        observerLive=false;
        try {
            observer = HelperSession.observe(line -> main.post(() -> accept(token,line)),
                    () -> main.post(() -> lost(token)));
            String arguments = observer.arguments();
            main.removeCallbacks(startupTimeout);main.postDelayed(startupTimeout,10000);
            worker.execute(() -> {
                try { LocalAdbManager.forPowerObserver(context).startPowerObserver(arguments); }
                catch (Exception error) { Log.w("BYD_SLEEP", "Observer unavailable", error); main.post(() -> lost(token)); }
            });
        } catch (Exception error) { lost(token); }
    }
    private void lost(int token) {
        if (token != generation || listeners.isEmpty()) return;
        generation++;
        observerLive=false;main.removeCallbacks(startupTimeout);
        if (observer != null) observer.close(); observer = null;
        main.removeCallbacks(confirm);
        publish(policy.invalidate(SystemClock.elapsedRealtime()));
        main.removeCallbacks(retry); main.postDelayed(retry, 30000);
    }
    private void accept(int token, String line) {
        if (token != generation || listeners.isEmpty()) return;
        String[] parts = line.split(" ");
        if (parts.length != 3 || !parts[0].equals("STATE")) return;
        try {int power=Integer.parseInt(parts[1]),cable=Integer.parseInt(parts[2]);
            observerLive=true;lastState=line;main.removeCallbacks(startupTimeout);
            publish(policy.sample(SystemClock.elapsedRealtime(),power,cable));}
        catch (NumberFormatException ignored) { }
    }
    private void requestSnapshot() {
        int token = generation;
        worker.execute(() -> {
            try {
                String state = LocalAdbManager.forPowerObserver(context).readPowerState();
                main.post(() -> accept(token,state));
            } catch (Exception error) { main.post(() -> lost(token)); }
        });
    }
    private void publish(VehicleSleepState.Mode next) {
        main.removeCallbacks(confirm);
        long at = policy.confirmationAt();
        if (next == VehicleSleepState.Mode.OFF_PENDING && at >= 0)
            main.postDelayed(confirm, Math.max(1, at - SystemClock.elapsedRealtime()));
        if (next == mode) return;
        mode = next;
        Log.i("BYD_SLEEP", "mode=" + mode);
        for (Runnable listener : new java.util.ArrayList<>(listeners.values())) listener.run();
        if(next==VehicleSleepState.Mode.SLEEP)worker.execute(()->LocalAdbManager.closeIdleConnections(context));
    }
}
