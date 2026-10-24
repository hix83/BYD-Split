package ru.logunov.bydsplit;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.Arrays;

/** Exercises real pane layout, scaled controls and editor restoration at both size limits. */
public final class DockHeightInstrumentation extends Instrumentation {
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish")) throw new IllegalStateException("Emulator required");
            AppPreferences.get(getTargetContext()).edit().putBoolean(DockConfiguration.MODE, true)
                    .putInt(DockConfiguration.HEIGHT_PERCENT, 100).putBoolean(MusicGlowView.ENABLED, false).commit();
            DockConfiguration.save(getTargetContext(), Arrays.asList(DockConfiguration.Item.SONG, DockConfiguration.Item.CAMERA,
                    DockConfiguration.Item.CLIMATE, DockConfiguration.Item.SEATS, DockConfiguration.Item.DRIVER_HEAT,
                    DockConfiguration.Item.DRIVER_VENT, DockConfiguration.Item.WHEEL, DockConfiguration.Item.SETTINGS), 1);
            Activity a = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("demo_mode", true).putExtra("open_climate", true)); waitForIdleSync();
            DynamicDockView dock = (DynamicDockView) field(a, "dynamicDock");
            View left = (View) field(a, "driverSlot"), right = (View) field(a, "farSlot");
            float density = a.getResources().getDisplayMetrics().density;
            for (int percent : new int[]{80, 150, 100}) {
                runOnMainSync(() -> { AppPreferences.get(a).edit().putInt(DockConfiguration.HEIGHT_PERCENT, percent).commit(); MainActivity.applyDockSizeFromSettings(); }); waitForIdleSync();
                check(Math.abs(dock.getHeight() - Math.round(94 * percent / 100f) * density) <= 1, "dock height " + percent);
                check(left.getHeight() == right.getHeight(), "unequal panes");
                check(field(a, "driverSlot") == left && field(a, "farSlot") == right, "pane replacement");
                ViewGroup row = (ViewGroup) field(dock, "dockRow");
                LinearLayout tile = (LinearLayout) ((ViewGroup) row.getChildAt(0)).getChildAt(0);
                check(Math.abs(tile.getChildAt(0).getHeight() - 40 * percent / 100f * density) <= 1, "icon scale");
                check(tile.getChildAt(1).getBottom() <= tile.getHeight(), "caption clipped");
                ViewGroup heat = (ViewGroup) ((ViewGroup) row.getChildAt(4)).getChildAt(0);
                check(heat.getChildAt(2).getBottom() <= heat.getHeight(), "comfort label clipped");
                int paneHeight = right.getHeight(); ViewParent parent = dock.getParent();
                shot("height-" + percent);
                runOnMainSync(dock::performLongClick); waitForIdleSync();
                Dialog editor = (Dialog) field(dock, "editorDialog");
                check(dock.getRootView() == editor.getWindow().getDecorView(), "editor parent");
                check(dock.getHeight() == Math.round(Math.round(94 * percent / 100f) * density), "editor height");
                check(right.getHeight() == paneHeight, "editor resized pane");
                shot("editor-" + percent);
                runOnMainSync(() -> dock.closeEditor(false)); waitForIdleSync();
                check(dock.getParent() == parent && right.getHeight() == paneHeight, "editor restore");
                runOnMainSync(() -> { AppPreferences.get(a).edit().putBoolean(DockConfiguration.MODE, false).commit(); MainActivity.applyPanelLayoutFromSettings(); }); waitForIdleSync();
                ViewGroup classic = (ViewGroup) field(a, "classicDock");
                check(Math.abs(classic.getHeight() - 86 * percent / 100f * density) <= 1, "classic height");
                ViewGroup classicRow = (ViewGroup) classic.getChildAt(1);
                ViewGroup classicCell = (ViewGroup) classicRow.getChildAt(0);
                check(Math.abs(classicCell.getChildAt(0).getHeight() - 40 * percent / 100f * density) <= 1, "classic icon");
                shot("classic-" + percent);
                runOnMainSync(() -> { AppPreferences.get(a).edit().putBoolean(DockConfiguration.MODE, true).commit(); MainActivity.applyPanelLayoutFromSettings(); }); waitForIdleSync();
            }
            runOnMainSync(() -> { try { Method m = a.getClass().getDeclaredMethod("openSettings", int.class); m.setAccessible(true); m.invoke(a, 0); } catch (Exception e) { throw new RuntimeException(e); } }); waitForIdleSync();
            SettingsView settings = (SettingsView) field(a, "settingsView");
            check(((View) field(settings, "settingsRoot")).getPaddingBottom() == 0, "settings bottom inset");
            check(((View) field(settings, "generalScroll")).getPaddingBottom() == 0, "scroll bottom inset");
            View slider = description(a.getWindow().getDecorView(), "Высота нижней панели и размер кнопок");
            check(slider != null, "settings slider missing");
            runOnMainSync(() -> slider.requestRectangleOnScreen(new android.graphics.Rect(0,0,slider.getWidth(),slider.getHeight()), true)); waitForIdleSync();
            shot("settings-before-touch");
            int[] xy = new int[2]; slider.getLocationOnScreen(xy);
            long now = SystemClock.uptimeMillis(); float x = xy[0] + slider.getWidth() - slider.getPaddingRight(), y = xy[1] + slider.getHeight() / 2f;
            MotionEvent down = MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0), up = MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getUiAutomation().injectInputEvent(down,true); getUiAutomation().injectInputEvent(up,true); down.recycle(); up.recycle(); SystemClock.sleep(250); waitForIdleSync();
            check(DockConfiguration.heightPercent(a) == 150, "touch slider did not persist maximum: " + DockConfiguration.heightPercent(a) + " at " + x + "," + y);
            shot("settings-150");
            TextView reset = text(a.getWindow().getDecorView(), "Стандартная высота · 100%"); check(reset != null, "reset missing");
            runOnMainSync(reset::performClick); waitForIdleSync(); check(DockConfiguration.heightPercent(a) == 100 && dock.heightDp() == 94, "reset");
            runOnMainSync(() -> AppPreferences.get(a).edit().putInt(DockConfiguration.HEIGHT_PERCENT, 999).commit()); check(DockConfiguration.heightPercent(a) == 150, "upper clamp");
            runOnMainSync(() -> AppPreferences.get(a).edit().putInt(DockConfiguration.HEIGHT_PERCENT, -1).commit()); check(DockConfiguration.heightPercent(a) == 80, "lower clamp");
            runOnMainSync(() -> { AppPreferences.get(a).edit().putInt(DockConfiguration.HEIGHT_PERCENT, 100).commit(); MainActivity.applyDockSizeFromSettings(); });
            result.putString("result", "PASS: 80/100/150% dynamic and classic geometry, scaled icons and labels, equal panes, stable pane instances, editor size and restoration, real settings touch, persistence, reset and bounds"); finish(Activity.RESULT_OK,result);
        } catch (Throwable error) { result.putString("error", android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED,result); }
    }
    private void shot(String name) throws Exception {
        waitForIdleSync(); SystemClock.sleep(150); Bitmap b = getUiAutomation().takeScreenshot();
        File dir = new File(getTargetContext().getExternalFilesDir(null), "dock-height"); dir.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(dir,name+".png"))) { b.compress(Bitmap.CompressFormat.PNG,100,out); } b.recycle();
    }
    private static Object field(Object o, String name) throws Exception { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static View description(View v, String s) { if(s.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v; if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=description(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null; }
    private static TextView text(View v, String s) { if(v instanceof TextView && s.contentEquals(((TextView)v).getText()))return (TextView)v; if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),s);if(found!=null)return found;}return null; }
    private static void check(boolean value, String s) { if(!value)throw new AssertionError(s); }
}
