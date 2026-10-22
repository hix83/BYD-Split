package ru.logunov.bydsplit;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.*;

/** Runs only on a disposable emulator; exercises the real panel and modal library. */
public final class DockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            List<DockConfiguration.Item> required = DockConfiguration.decode("BOGUS,SONG,SONG");
            check(required.size() == 2 && required.contains(DockConfiguration.Item.SETTINGS), "required IDs / deduplication");
            StringBuilder all = new StringBuilder();
            for (DockConfiguration.Item item : DockConfiguration.Item.values()) all.append(item.name()).append(',');
            check(DockConfiguration.decode(all.toString()).size() == 20, "20 item cap");
            DockConfiguration.save(getTargetContext(), Arrays.asList(DockConfiguration.Item.SONG, DockConfiguration.Item.CAMERA, DockConfiguration.Item.MUSIC, DockConfiguration.Item.MESSAGES, DockConfiguration.Item.SETTINGS), 1);
            AppPreferences.get(getTargetContext()).edit().putBoolean(DockConfiguration.MODE, false).commit();
            Activity activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode", true)); waitForIdleSync();
            View left = (View) field(activity, "driverSlot"), right = (View) field(activity, "farSlot");
            View classic = (View) field(activity, "classicDock"); check(classic.getVisibility() == View.VISIBLE, "classic default");
            runOnMainSync(() -> { AppPreferences.get(activity).edit().putBoolean(DockConfiguration.MODE, true).commit(); MainActivity.applyPanelLayoutFromSettings(); }); waitForIdleSync();
            DynamicDockView dock = (DynamicDockView) field(activity, "dynamicDock");
            check(classic.getVisibility() == View.GONE && left.getHeight() == right.getHeight(), "dynamic pane heights");
            check(dock.getWidth() > left.getWidth() * 2, "dock width");
            ViewGroup parent = (ViewGroup) dock.getParent(); int paneHeight = right.getHeight();
            open(dock);
            Dialog editor = (Dialog) field(dock, "editorDialog"); View modal = editor.getWindow().getDecorView();
            check(dock.getRootView() == modal, "actual panel is not in modal editing layer");
            check(right.getHeight() == paneHeight, "underlying pane resized during editing");
            check(description(dock, "Переместить: BYD SONG") != null, "panel is not editable");
            check(description(dock, "Удалить: BYD SONG") == null && description(dock, "Удалить: Настройки") == null, "required remove badges");
            View remove = description(dock, "Удалить: Камеры"); check(remove != null, "missing remove badge");
            runOnMainSync(remove::performClick); waitForIdleSync();
            check(description(dock, "Переместить: Камеры") == null && description(modal, "Добавить: Камеры") != null, "removed item did not return to library");
            View restore = description(modal, "Добавить: Камеры"); runOnMainSync(restore::performClick); waitForIdleSync();
            check(description(dock, "Переместить: Камеры") != null && description(modal, "Добавить: Камеры") == null, "library item was not moved to panel");
            View heat = description(modal, "Добавить: Подогрев водителя"); runOnMainSync(heat::performClick); waitForIdleSync();
            check(description(dock, "Переместить: Подогрев водителя") != null, "comfort item missing from panel");
            runOnMainSync(() -> dock.closeEditor(false)); waitForIdleSync();
            check(dock.getParent() == parent && DockConfiguration.read(activity).size() == 5, "cancel / parent restoration");
            open(dock); SystemClock.sleep(600);
            View from = description(dock, "Переместить: BYD SONG"), to = description(dock, "Переместить: Музыка");
            drag(from, to); waitForIdleSync();
            runOnMainSync(() -> dock.closeEditor(true)); waitForIdleSync();
            check(DockConfiguration.read(activity).indexOf(DockConfiguration.Item.SONG) == 2, "on-panel touch drag persistence");
            check(field(activity, "driverSlot") == left && field(activity, "farSlot") == right, "pane objects replaced");
            runOnMainSync(() -> { AppPreferences.get(activity).edit().putBoolean(DockConfiguration.MODE, false).commit(); MainActivity.applyPanelLayoutFromSettings(); }); waitForIdleSync();
            check(classic.getVisibility() == View.VISIBLE && dock.getVisibility() == View.GONE, "classic restoration");
            runOnMainSync(() -> { AppPreferences.get(activity).edit().putBoolean(DockConfiguration.MODE, true).commit(); MainActivity.applyPanelLayoutFromSettings(); }); waitForIdleSync();
            result.putString("result", "PASS: equal pane heights, real dock in modal layer, protected badges, remove-to-library, add-to-panel, no duplicates, cancel, touch drag on panel, saved order, preserved panes, classic restoration");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) { result.putString("error", android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED, result); }
    }
    private void open(View dock) { runOnMainSync(dock::performLongClick); waitForIdleSync(); }
    private Object field(Object object, String name) throws Exception { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private View description(View root, String value) {
        if (value.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) { View found = description(((ViewGroup) root).getChildAt(i), value); if (found != null) return found; }
        return null;
    }
    private void drag(View from, View to) {
        int[] a = new int[2], b = new int[2]; from.getLocationOnScreen(a); to.getLocationOnScreen(b);
        float x = a[0] + from.getWidth() / 2f, y = a[1] + from.getHeight() / 2f;
        float tx = b[0] + to.getWidth() / 2f, ty = b[1] + to.getHeight() / 2f;
        long down = SystemClock.uptimeMillis(); pointer(down, MotionEvent.ACTION_DOWN, x, y); SystemClock.sleep(900);
        for (int step = 1; step <= 12; step++) { pointer(down, MotionEvent.ACTION_MOVE, x + (tx-x)*step/12, y + (ty-y)*step/12); SystemClock.sleep(30); }
        SystemClock.sleep(230);
        runOnMainSync(() -> {
            ViewGroup row = (ViewGroup) from.getParent(); boolean neighboursMoved = false;
            for (int i = 0; i < row.getChildCount(); i++) {
                View cell = row.getChildAt(i);
                if (cell != from && Math.abs(cell.getTranslationX()) > 1) neighboursMoved = true;
            }
            check(neighboursMoved, "neighbours did not slide before drop");
            check(from.getAlpha() < .5f, "drag source is not a placeholder");
        });
        pointer(down, MotionEvent.ACTION_UP, tx, ty);
    }
    private void pointer(long down, int action, float x, float y) { MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); event.setSource(InputDevice.SOURCE_TOUCHSCREEN); getUiAutomation().injectInputEvent(event, true); event.recycle(); }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
