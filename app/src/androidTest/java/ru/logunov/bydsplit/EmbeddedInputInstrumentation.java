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
    private boolean dashboardOnly;
    private boolean settingsOnly;
    private boolean displayLifecycleOnly;
    private boolean guestFocusOnly;
    private String paneName = "far";
    @Override public void onCreate(Bundle args) { super.onCreate(args);
        settingsOnly = args != null && "true".equals(args.getString("settings_only"));
        guestFocusOnly = args != null && "true".equals(args.getString("guest_focus_only"));
        displayLifecycleOnly = args != null && "true".equals(args.getString("display_lifecycle_only"));
        dashboardOnly = args != null && "true".equals(args.getString("dashboard_only"));
        if (args != null) paneName = args.getString("pane", "far");
        if (!paneName.equals("far") && !paneName.equals("driver")) throw new IllegalArgumentException("pane must be far or driver");
        start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (Build.VERSION.SDK_INT != 28) throw new IllegalStateException("Run on API 28");
            Context context = getTargetContext();
            if (settingsOnly) {
                testSettingsPanel(context);
                result.putString("stream", "PASS: settings panel sections, layout swap, retained guest view and Back restoration\n");
                finish(-1, result); return;
            }
            if (dashboardOnly) {
                boolean previousDemo = AppPreferences.isDemoModeEnabled(context);
                AppPreferences.get(context).edit().putBoolean(AppPreferences.KEY_DEMO_MODE, false).commit();
                try {
                runOnMainSync(() -> {
                    VehicleDashboardView dashboard = new VehicleDashboardView(context, action -> {});
                    for (int width : new int[]{416, 640}) {
                        dashboard.layout(0, 0, width, 610);
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, 610, android.graphics.Bitmap.Config.ARGB_8888);
                        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                        dashboard.setTelemetry(VehicleTelemetrySnapshot.EMPTY);
                        dashboard.draw(canvas);
                        dashboard.setTelemetry(Di2Snapshot.vehicle(DiPlusValues.parse("ACTemp:28|SOC:98|Wind:5|FanLevel:4|ACStatus:1")));
                        dashboard.draw(canvas);
                        dashboard.setTelemetry(null);
                        dashboard.draw(canvas);
                        bitmap.recycle();
                    }
                    try {
                        BatteryDetailsSnapshot data = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY);
                        for (boolean technical : new boolean[]{false, true}) {
                            BatteryDetailsView details = new BatteryDetailsView(context, () -> {}, technical);
                            java.lang.reflect.Method update = BatteryDetailsView.class.getDeclaredMethod(
                                    technical ? "updateTechnical" : "update", technical ? TechSnapshot.class : BatteryDetailsSnapshot.class);
                            update.setAccessible(true);
                            update.invoke(details, technical ? new TechSnapshot(data, new java.util.HashMap<String, Double>()) : data);
                            details.measure(View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
                            details.layout(0, 0, 840, 700);
                            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(840, 700, android.graphics.Bitmap.Config.ARGB_8888);
                            details.draw(new android.graphics.Canvas(bitmap));
                            bitmap.recycle();
                        }
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                } finally {
                    AppPreferences.get(context).edit().putBoolean(AppPreferences.KEY_DEMO_MODE, previousDemo).commit();
                }
                result.putString("stream", "PASS: dashboard empty/live/cleared telemetry at both widths, battery and technical details updates\n");
                finish(-1, result);
                return;
            }
            context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("demo_mode", true));
            if (displayLifecycleOnly || guestFocusOnly) LocalAdbManager.get(context).startHelpers(false);
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (SystemClock.uptimeMillis() < deadline && ((displayLifecycleOnly || guestFocusOnly) ? !ShellDisplayBridge.ready() : display == 0)) {
                for (Display d : ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).getDisplays())
                    if (d.getName().equals("BYD-Split-" + paneName)) display = d.getDisplayId();
                SystemClock.sleep(50);
            }
            check((displayLifecycleOnly || guestFocusOnly || display > 0) && ShellDisplayBridge.ready(), "shell-owned display ready");
            if (guestFocusOnly) {
                // Keep a real host Activity behind a different-UID guest. Returning
                // the host to front used to pause GL renderers after the launch.
                SystemClock.sleep(500);
                android.media.ImageReader output = android.media.ImageReader.newInstance(800, 600, android.graphics.PixelFormat.RGBA_8888, 2);
                LocalAdbManager manager = LocalAdbManager.get(context);
                int id = ShellDisplayBridge.create("focus-test", 800, 600, 160, output.getSurface());
                String originalPolicy;
                try (LocalAdbClient reader = new LocalAdbClient(new LocalAdbKeyStore(context))) {
                    reader.connect(); originalPolicy = reader.shell("settings get global policy_control").trim();
                }
                try {
                    check(manager.launchOnDisplay("ru.logunov.bydsplit.test/ru.logunov.bydsplit.InputProbeActivity",
                            "ru.logunov.bydsplit.test", id), "guest launched");
                    SystemClock.sleep(1500);
                    check(stats().getBoolean("resumed"), "guest stays resumed after startup settles");
                    manager.startMainActivity();
                    SystemClock.sleep(400);
                    check(!stats().getBoolean("resumed"), "opening host page pauses guest on API28");
                    manager.focusEmbeddedTask("ru.logunov.bydsplit.test", id);
                    SystemClock.sleep(400);
                    check(stats().getBoolean("resumed"), "returning from host page resumes guest");
                    try (LocalAdbClient reader = new LocalAdbClient(new LocalAdbKeyStore(context))) {
                        reader.connect();
                        check(reader.shell("settings get global policy_control").contains("ru.logunov.bydsplit.test"),
                                "immersive policy includes the guest");
                    }
                    result.putString("stream", "PASS: guest stays RESUMED; scoped immersive policy applied and restored\n");
                } finally {
                    manager.releaseEmbeddedImmersive(id);
                    try (LocalAdbClient reader = new LocalAdbClient(new LocalAdbKeyStore(context))) {
                        reader.connect();
                        check(originalPolicy.equals(reader.shell("settings get global policy_control").trim()),
                                "original immersive setting restored after release");
                    }
                    ShellDisplayBridge.call(ShellDisplayBridge.RELEASE, parcel -> parcel.writeInt(id));
                    output.close();
                }
                finish(-1, result); return;
            }
            if (displayLifecycleOnly) {
                android.media.ImageReader output = android.media.ImageReader.newInstance(320, 240, android.graphics.PixelFormat.RGBA_8888, 2);
                int originalId = -1;
                try {
                    for (int iteration = 0; iteration < 5; iteration++) {
                        int id = ShellDisplayBridge.create("lifecycle-test", 320 + iteration * 16, 240, 160, output.getSurface());
                        if (originalId == -1) originalId = id;
                        check(id == originalId, "same display id after pane rebuild");
                        ShellDisplayBridge.call(ShellDisplayBridge.RELEASE, parcel -> parcel.writeInt(id));
                        check(((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(id) != null,
                                "parked display stays valid for guest ViewRootImpl");
                    }
                } finally { output.close(); }
                result.putString("stream", "PASS: five pane release/create cycles preserve guest display identity\n");
                finish(-1, result); return;
            }
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
    private static Object field(Object owner, Class<?> type, String name) {
        try { java.lang.reflect.Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void testSettingsPanel(Context context) {
        context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("open_settings", true));
        MainActivity[] activity = {null}; SettingsView[] page = {null};
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (page[0] == null && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> {
                activity[0] = (MainActivity) ((java.lang.ref.WeakReference<?>) field(null, MainActivity.class, "currentActivity")).get();
                if (activity[0] != null) page[0] = (SettingsView) field(activity[0], MainActivity.class, "settingsView");
            });
            if (page[0] == null) SystemClock.sleep(50);
        }
        check(page[0] != null, "settings opened inside launcher");
        View[] guest = {null};
        runOnMainSync(() -> {
            MainActivity host = activity[0]; SettingsView settings = page[0];
            guest[0] = (View) field(host, MainActivity.class, "farEmbeddedPane");
            check(settings.getParent() == field(host, MainActivity.class, "farSlot"), "settings belongs to app panel");
            check(settings.getContext() == host, "settings uses the launcher Activity");
            android.widget.LinearLayout navigation = (android.widget.LinearLayout) field(settings, SettingsView.class, "navigation");
            check(navigation.getChildCount() == 7, "all settings sections accessible");
            for (int i = 0; i < 7; i++) {
                navigation.getChildAt(i).performClick();
                check(((android.widget.FrameLayout) field(settings, SettingsView.class, "pageHost")).getChildCount() == 1,
                        "section content exists: " + i);
            }
            boolean original = AppPreferences.isCompactPaneOnLeft(host);
            try {
                java.lang.reflect.Method swap = SettingsView.class.getDeclaredMethod("setCompactPaneOnLeft", boolean.class);
                swap.setAccessible(true);
                try {
                    swap.invoke(settings, !original);
                    check(settings.getParent() == field(host, MainActivity.class, "farSlot"), "settings survives layout swap");
                    check(guest[0] == field(host, MainActivity.class, "farEmbeddedPane"), "layout swap retains guest view");
                } finally { swap.invoke(settings, original); }
            } catch (Exception e) { throw new IllegalStateException(e); }
            host.onBackPressed();
        });
        SystemClock.sleep(450);
        runOnMainSync(() -> {
            MainActivity host = activity[0];
            check(field(host, MainActivity.class, "settingsView") == null, "Back closes settings");
            check(!host.isFinishing(), "Back does not finish launcher");
            if (guest[0] != null) {
                check(guest[0].getParent() == field(host, MainActivity.class, "farSlot"), "original guest remains in panel");
                check(guest[0].getTranslationX() == 0, "original guest translation restored");
            }
        });
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
