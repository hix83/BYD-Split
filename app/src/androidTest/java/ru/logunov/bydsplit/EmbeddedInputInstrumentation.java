package ru.logunov.bydsplit;

import android.app.Instrumentation;
import android.content.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.net.Uri;
import android.view.*;

/** No external test libraries. Exercises cross-UID input through the actual API 28 window dispatcher. */
public final class EmbeddedInputInstrumentation extends Instrumentation {
    private final Uri uri = Uri.parse("content://ru.logunov.bydsplit.test.input");
    private ShellBridgeClient bridge;
    private int display;
    private long down;
    private String paneName = "far";
    @Override public void onCreate(Bundle args) { super.onCreate(args);
        if (args != null) paneName = args.getString("pane", "far");
        if (!paneName.equals("far") && !paneName.equals("driver")) throw new IllegalArgumentException("pane must be far or driver");
        start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (Build.VERSION.SDK_INT != 28) throw new IllegalStateException("Run on API 28");
            Context context = getTargetContext();
            context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("demo_mode", true));
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (SystemClock.uptimeMillis() < deadline && display == 0) {
                for (Display d : ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).getDisplays())
                    if (d.getName().equals("BYD-Split-" + paneName)) display = d.getDisplayId();
                SystemClock.sleep(50);
            }
            check(display > 0 && ShellDisplayBridge.ready(), "shell-owned display ready");
            check(LocalAdbManager.get(context).launchOnDisplay(
                    "ru.logunov.bydsplit.test/ru.logunov.bydsplit.InputProbeActivity", "ru.logunov.bydsplit.test", display),
                    "test Activity launched on the secondary display");
            getTargetContext().getContentResolver().call(uri, "reset", null, null);
            bridge = new ShellBridgeClient(context);
            motion(0, 1, 100, 200); motion(1, 1, 100, 200);
            await("action1", 1);
            check(stats().getFloat("x") == 100 && stats().getFloat("y") == 200, "local coordinates preserved");
            // Buffered DOWN + original UP reproduces the launcher's long-press interception.
            bridge.injectSingleFingerMotion(display, 0, 120, 220);
            MotionEvent bufferedUp = MotionEvent.obtain(SystemClock.uptimeMillis() - 1000,
                    SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 120, 220, 0);
            bufferedUp.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            bridge.injectMotionEvent(display, bufferedUp); bufferedUp.recycle(); await("action1", 2);
            motion(0, 1, 100, 200);
            motion(2, 1, 130, 250); motion(2, 1, 170, 350); motion(1, 1, 200, 400); await("action1", 3);
            Bundle swipe = stats();
            check(Math.abs(swipe.getFloat("x") - 200) < 1 && swipe.getInt("moves") >= 1,
                    "swipe delivered: x=" + swipe.getFloat("x") + ", moves=" + swipe.getInt("moves"));
            motion(0, 1, 100, 200); motion(5 | (1 << 8), 2, 100, 200);
            motion(2, 2, 80, 180); motion(6 | (1 << 8), 2, 80, 180); motion(1, 1, 80, 180); await("action1", 4);
            Bundle multi = stats();
            check(multi.getInt("maxPointers") == 2 && multi.getInt("pointer0") == 3 && multi.getInt("pointer1") == 7,
                    "multitouch pointer IDs and action index preserved");
            motion(0, 1, 80, 180); motion(3, 1, 80, 180); await("action3", 1);
            bridge.injectBack(display); await("back", 1);
            check(stats().getInt("badDownTime") == 0 && stats().getInt("badEventTime") == 0, "consistent gesture timestamps");
            // The previous short-lived shell transport died at 15 seconds.
            SystemClock.sleep(17000);
            check(ShellDisplayBridge.ready(), "display helper survives the old ADB timeout");
            int[] location = new int[2];
            runOnMainSync(() -> {
                try {
                    java.lang.reflect.Field current = MainActivity.class.getDeclaredField("currentActivity"); current.setAccessible(true);
                    MainActivity activity = (MainActivity) ((java.lang.ref.WeakReference<?>) current.get(null)).get();
                    java.lang.reflect.Field pane = MainActivity.class.getDeclaredField(paneName.equals("driver") ? "driverEmbeddedPane" : "farEmbeddedPane"); pane.setAccessible(true);
                    ((View) pane.get(activity)).getLocationOnScreen(location);
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            long now = SystemClock.uptimeMillis();
            for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                MotionEvent primary = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, location[0] + 100, location[1] + 200, 0);
                primary.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                check(getUiAutomation().injectInputEvent(primary, true), "primary-screen input accepted"); primary.recycle();
            }
            await("action1", 5);
            check(Math.abs(stats().getFloat("x") - 100) < 1, "primary-screen touch mapped to panel coordinates: " + stats().getFloat("x"));
            result.putString("stream", "PASS: " + paneName + ": API 28 launch, persistent helper, primary-screen touch, buffered tap, swipe, multitouch, cancel, Back, coordinates and timestamps\n");
            String restorePackage = paneName.equals("driver") ? "ru.yandex.music" : "ru.yandex.yandexnavi";
            Intent restore = context.getPackageManager().getLaunchIntentForPackage(restorePackage);
            if (restore != null) LocalAdbManager.get(context).launchOnDisplay(
                    restore.getComponent().flattenToString(), restorePackage, display);
            finish(-1, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e)); finish(0, result);
        } finally { if (bridge != null) bridge.close(); }
    }
    private Bundle stats() { return getTargetContext().getContentResolver().call(uri, "snapshot", null, null); }
    private void await(String key, int count) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (stats().getInt(key) >= count) return;
            SystemClock.sleep(30);
        }
        throw new AssertionError("No input: " + key + "; " + stats());
    }
    private void motion(int action, int count, float x, float y) {
        long now = SystemClock.uptimeMillis(); if (action == 0) down = now;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i == 0 ? 3 : 7;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords(); coords[i].x = x + i * 100; coords[i].y = y + i * 100;
            coords[i].pressure = 1; coords[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, now, action, count, properties, coords, 0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        bridge.injectMotionEvent(display, event); event.recycle();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
