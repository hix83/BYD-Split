package ru.logunov.bydsplit;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.view.Surface;

/** API 28 displays must be owned by the same privileged process that forwards input. */
final class EmbeddedDisplay {
    private final VirtualDisplay local;
    private final int id;
    private EmbeddedDisplay(VirtualDisplay local, int id) { this.local = local; this.id = id; }
    @android.annotation.SuppressLint("WrongConstant") // SUPPORTS_TOUCH is hidden in the public SDK.
    static EmbeddedDisplay create(Context context, String name, int w, int h, int dpi, Surface surface) throws Exception {
        if (Build.VERSION.SDK_INT == 28) return new EmbeddedDisplay(null, ShellDisplayBridge.create(name, w, h, dpi, surface));
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        VirtualDisplay display = manager.createVirtualDisplay("BYD-Split-" + name, w, h, dpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION | (1 << 6));
        return display == null ? null : new EmbeddedDisplay(display, display.getDisplay().getDisplayId());
    }
    int getDisplayId() { return id; }
    void resize(int w, int h, int dpi) {
        if (local != null) local.resize(w, h, dpi);
        else invoke(ShellDisplayBridge.RESIZE, p -> { p.writeInt(id); p.writeInt(w); p.writeInt(h); p.writeInt(dpi); });
    }
    void setSurface(Surface surface) {
        if (local != null) local.setSurface(surface);
        else invoke(ShellDisplayBridge.SURFACE, p -> {
            p.writeInt(id); p.writeInt(surface == null ? 0 : 1); if (surface != null) surface.writeToParcel(p, 0);
        });
    }
    void release() {
        if (local != null) local.release(); else invoke(ShellDisplayBridge.RELEASE, p -> p.writeInt(id));
    }
    private void invoke(int code, ShellDisplayBridge.Request request) {
        try { ShellDisplayBridge.call(code, request); }
        catch (Exception e) { android.util.Log.w("BYD_EMBEDDED", "Display helper command failed", e); }
    }
}
