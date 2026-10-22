package ru.logunov.bydsplit;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

/** Lives only in the separate test APK and receives real window input on the secondary display. */
public final class InputProbeActivity extends Activity {
    static final Bundle stats = new Bundle();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new View(this) {
            @Override public boolean onTouchEvent(MotionEvent e) {
                synchronized (stats) {
                    int action = e.getActionMasked();
                    increment("action" + action);
                    if (action == MotionEvent.ACTION_MOVE) stats.putInt("moves", stats.getInt("moves") + 1 + e.getHistorySize());
                    stats.putInt("maxPointers", Math.max(stats.getInt("maxPointers"), e.getPointerCount()));
                    if (action == MotionEvent.ACTION_DOWN) {
                        stats.putLong("downTime", e.getDownTime());
                        stats.putFloat("startX", e.getX()); stats.putFloat("startY", e.getY());
                    } else if (stats.getLong("downTime") != e.getDownTime()) increment("badDownTime");
                    if (e.getEventTime() < e.getDownTime()) increment("badEventTime");
                    if (e.getPointerCount() == 2) {
                        stats.putInt("pointer0", e.getPointerId(0)); stats.putInt("pointer1", e.getPointerId(1));
                    }
                    stats.putFloat("x", e.getX()); stats.putFloat("y", e.getY());
                }
                return true;
            }
        });
    }
    @Override protected void onResume() {
        super.onResume();
        synchronized (stats) { stats.putBoolean("resumed", true); stats.putInt("display", getWindowManager().getDefaultDisplay().getDisplayId()); }
    }
    @Override protected void onPause() {
        synchronized (stats) { stats.putBoolean("resumed", false); }
        super.onPause();
    }
    private static void increment(String key) { stats.putInt(key, stats.getInt(key) + 1); }
    @Override public void onBackPressed() { synchronized (stats) { increment("back"); } }
}
