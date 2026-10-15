package ru.logunov.bydsplit;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.*;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import java.util.HashMap;
import java.util.Map;

/** API 28: shell owns both the display and its InputForwarder (INJECT_EVENTS). */
public final class ShellDisplayDaemon {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        Context system = (Context) type.getMethod("getSystemContext").invoke(thread);
        Context context = system.createPackageContext("com.android.shell", 0);
        int launcherUid = context.getPackageManager().getApplicationInfo("ru.logunov.bydsplit", 0).uid;
        Host host = new Host(context, launcherUid);
        Bundle argsBundle = new Bundle(); argsBundle.putBinder("host", host);
        // app_process is not an ActivityManager application thread; use the same
        // external-provider API as Android 9's shell 'content' command.
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class<?> amType = Class.forName("android.app.IActivityManager");
        Binder token = new Binder();
        Object holder = amType.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class)
                .invoke(am, "ru.logunov.bydsplit.display", 0, token);
        if (holder == null) throw new IllegalStateException("Display provider unavailable");
        Object provider = holder.getClass().getField("provider").get(holder);
        Bundle reply;
        try {
            reply = (Bundle) Class.forName("android.content.IContentProvider")
                    .getMethod("call", String.class, String.class, String.class, Bundle.class)
                    .invoke(provider, "com.android.shell", "register", null, argsBundle);
        } finally {
            amType.getMethod("removeContentProviderExternal", String.class, IBinder.class)
                    .invoke(am, "ru.logunov.bydsplit.display", token);
        }
        if (reply == null || reply.getBinder("lifetime") == null) throw new IllegalStateException("No launcher lifetime");
        reply.getBinder("lifetime").linkToDeath(() -> {
            host.close(); System.exit(0);
        }, 0);
        System.out.println("BYD_DISPLAY_READY");
        Looper.loop();
    }

    private static final class Host extends Binder {
        final DisplayManager manager;
        final int launcherUid;
        final ShellEventInjector injector = new ShellEventInjector();
        final Map<Integer, VirtualDisplay> displays = new HashMap<>();
        final Map<Integer, Surface> surfaces = new HashMap<>();
        final Map<Integer, Long> downTimes = new HashMap<>();
        Host(Context context, int launcherUid) throws Exception {
            this.launcherUid = launcherUid;
            manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            attachInterface(null, ShellDisplayBridge.DESCRIPTOR);
        }
        @android.annotation.SuppressLint("WrongConstant") // SUPPORTS_TOUCH is hidden in the public SDK.
        @Override protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(ShellDisplayBridge.DESCRIPTOR); return true; }
            if (Binder.getCallingUid() != launcherUid) throw new SecurityException("Launcher UID required");
            data.enforceInterface(ShellDisplayBridge.DESCRIPTOR);
            long identity = Binder.clearCallingIdentity();
            try {
                int result;
                if (code == ShellDisplayBridge.CREATE) {
                    if (displays.size() >= 4) throw new IllegalStateException("Too many embedded displays");
                    String name = data.readString(); int w = data.readInt(), h = data.readInt(), dpi = data.readInt();
                    dimensions(w, h, dpi);
                    Surface surface = Surface.CREATOR.createFromParcel(data);
                    VirtualDisplay display;
                    try {
                        display = manager.createVirtualDisplay("BYD-Split-" + name, w, h, dpi, surface,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | (1 << 6));
                        if (display == null) throw new IllegalStateException("Display creation failed");
                    } catch (RuntimeException e) { surface.release(); throw e; }
                    result = display.getDisplay().getDisplayId();
                    displays.put(result, display); surfaces.put(result, surface);
                } else {
                    int id = data.readInt(); VirtualDisplay display = displays.get(id);
                    if (display == null) throw new IllegalArgumentException("Unknown owned display");
                    result = 1;
                    switch (code) {
                        case ShellDisplayBridge.RESIZE:
                            int w = data.readInt(), h = data.readInt(), dpi = data.readInt(); dimensions(w, h, dpi);
                            display.resize(w, h, dpi); break;
                        case ShellDisplayBridge.SURFACE:
                            Surface surface = data.readInt() == 0 ? null : Surface.CREATOR.createFromParcel(data);
                            display.setSurface(surface);
                            Surface old = surfaces.put(id, surface); if (old != null) old.release(); break;
                        case ShellDisplayBridge.RELEASE:
                            displays.remove(id); display.release(); injector.release(id); downTimes.remove(id);
                            Surface released = surfaces.remove(id); if (released != null) released.release(); break;
                        case ShellDisplayBridge.MOTION: case ShellDisplayBridge.KEY:
                            InputEvent event = code == ShellDisplayBridge.MOTION
                                    ? MotionEvent.CREATOR.createFromParcel(data) : KeyEvent.CREATOR.createFromParcel(data);
                            if (event instanceof MotionEvent) {
                                MotionEvent original = (MotionEvent) event;
                                // Buffered long-press handling may send a synthetic DOWN
                                // followed by the original UP. Give the whole gesture one
                                // downTime, and monotonic delivery timestamps, as the old
                                // shell input protocol did, preserving every pointer ID.
                                int count = original.getPointerCount();
                                MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
                                MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
                                for (int i = 0; i < count; i++) {
                                    properties[i] = new MotionEvent.PointerProperties(); original.getPointerProperties(i, properties[i]);
                                    coords[i] = new MotionEvent.PointerCoords(); original.getPointerCoords(i, coords[i]);
                                }
                                long now = SystemClock.uptimeMillis();
                                if (original.getActionMasked() == MotionEvent.ACTION_DOWN) downTimes.put(id, now);
                                long down = downTimes.containsKey(id) ? downTimes.get(id) : now;
                                event = MotionEvent.obtain(down, now, original.getAction(), count, properties, coords,
                                        original.getMetaState(), original.getButtonState(), original.getXPrecision(),
                                        original.getYPrecision(), original.getDeviceId(), original.getEdgeFlags(),
                                        original.getSource(), original.getFlags());
                                if (original.getActionMasked() == MotionEvent.ACTION_UP || original.getActionMasked() == MotionEvent.ACTION_CANCEL)
                                    downTimes.remove(id);
                                original.recycle();
                            }
                            try { result = injector.inject(event, id, 0) ? 1 : 0; }
                            finally { if (event instanceof MotionEvent) ((MotionEvent) event).recycle(); }
                            break;
                        default: throw new IllegalArgumentException("Unknown display command");
                    }
                }
                reply.writeNoException(); reply.writeInt(result); return true;
            } catch (Exception e) { reply.writeException(e); return true; }
            finally { Binder.restoreCallingIdentity(identity); }
        }
        private void dimensions(int w, int h, int dpi) {
            if (w < 1 || h < 1 || w > 8192 || h > 8192 || dpi < 72 || dpi > 1000)
                throw new IllegalArgumentException("Invalid display dimensions");
        }
        synchronized void close() {
            for (VirtualDisplay d : displays.values()) d.release(); displays.clear();
            for (Surface s : surfaces.values()) if (s != null) s.release(); surfaces.clear();
        }
    }
}
