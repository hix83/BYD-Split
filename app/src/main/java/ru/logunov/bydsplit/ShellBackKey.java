package ru.logunov.bydsplit;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

/** Android 9's input CLI has no -d option; inject Back into the requested display. */
public final class ShellBackKey {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected display ID");
        int display = Integer.parseInt(args[0]);
        if (display < 0 || display > 999) throw new IllegalArgumentException("Invalid display ID");
        ShellEventInjector injector = new ShellEventInjector();
        long now = SystemClock.uptimeMillis();
        boolean success = true;
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(now, SystemClock.uptimeMillis(), action,
                    KeyEvent.KEYCODE_BACK, 0, 0, -1, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY, InputDevice.SOURCE_KEYBOARD);
            success &= injector.inject(event, display, 2);
        }
        System.out.println(success ? "BYD_SPLIT_BACK_OK" : "BYD_SPLIT_BACK_FAILED");
    }
}
