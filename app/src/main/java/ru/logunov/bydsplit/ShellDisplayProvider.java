package ru.logunov.bydsplit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Bootstrap only. Arbitrary apps cannot register a display helper or obtain its Binder. */
public final class ShellDisplayProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != 2000 && uid != 0) throw new SecurityException("ADB shell required");
        if (!"register".equals(method) || extras == null) throw new IllegalArgumentException("Invalid bootstrap");
        try {
            ShellDisplayBridge.register(extras.getBinder("host"));
            Bundle result = new Bundle(); result.putBinder("lifetime", ShellDisplayBridge.LIFETIME); return result;
        } catch (Exception e) { throw new IllegalStateException("Cannot register display helper", e); }
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
