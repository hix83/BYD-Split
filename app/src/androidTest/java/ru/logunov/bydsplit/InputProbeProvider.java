package ru.logunov.bydsplit;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

public final class InputProbeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        try {
            int launcher = getContext().getPackageManager().getApplicationInfo("ru.logunov.bydsplit", 0).uid;
            if (Binder.getCallingUid() != launcher) throw new SecurityException("Test target only");
        } catch (android.content.pm.PackageManager.NameNotFoundException e) { throw new IllegalStateException(e); }
        synchronized (InputProbeActivity.stats) {
            if ("reset".equals(method)) InputProbeActivity.stats.clear();
            return new Bundle(InputProbeActivity.stats);
        }
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
