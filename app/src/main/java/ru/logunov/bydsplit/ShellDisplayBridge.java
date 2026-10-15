package ru.logunov.bydsplit;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

/** Private Binder protocol between the launcher and its authorized shell helper. */
final class ShellDisplayBridge {
    static final String DESCRIPTOR = "ru.logunov.bydsplit.ShellDisplay.v1";
    static final int CREATE = 1, RESIZE = 2, SURFACE = 3, RELEASE = 4, MOTION = 5, KEY = 6;
    static final Binder LIFETIME = new Binder();
    private static volatile IBinder remote;

    static void register(IBinder binder) throws Exception {
        if (binder == null || !DESCRIPTOR.equals(binder.getInterfaceDescriptor()))
            throw new IllegalArgumentException("Wrong display helper");
        binder.linkToDeath(() -> { if (remote == binder) remote = null; }, 0);
        remote = binder;
    }
    static boolean ready() { IBinder b = remote; return b != null && b.isBinderAlive(); }
    interface Request { void write(Parcel parcel); }
    static int call(int command, Request request) throws Exception {
        IBinder b = remote;
        if (b == null) throw new IllegalStateException("Display helper unavailable");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR); request.write(data);
            if (!b.transact(command, data, reply, 0)) throw new IllegalStateException("Display command rejected");
            reply.readException(); return reply.readInt();
        } finally { data.recycle(); reply.recycle(); }
    }
    static boolean input(int display, InputEvent event) throws Exception {
        return call(event instanceof MotionEvent ? MOTION : KEY, p -> {
            p.writeInt(display);
            if (event instanceof MotionEvent) ((MotionEvent) event).writeToParcel(p, 0);
            else ((KeyEvent) event).writeToParcel(p, 0);
        }) == 1;
    }
    static int create(String name, int width, int height, int density, Surface surface) throws Exception {
        return call(CREATE, p -> {
            p.writeString(name); p.writeInt(width); p.writeInt(height); p.writeInt(density);
            surface.writeToParcel(p, 0);
        });
    }
}
