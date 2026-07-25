package ru.logunov.bydsplit;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.graphics.Rect;

import java.util.List;

public final class SteeringAccessibilityService extends AccessibilityService {
    private static final String TAG = "BYD_STEERING";
    private static final int BYD_MICROPHONE_KEY_CODE = 304;
    private long lastMaxTreeLogAt;
    private static volatile boolean maxChatOpen;

    static void setMaxChatOpen(boolean open) {
        maxChatOpen = open;
        Log.i(TAG, "MAX chat state=" + open);
    }

    static boolean isMaxChatOpen() {
        return maxChatOpen;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility key filter connected");
        new Handler(Looper.getMainLooper()).postDelayed(
                this::logVisibleWindows, 1000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence packageName = event.getPackageName();
        if (packageName != null
                && "ru.oneme.app".contentEquals(packageName)) {
            Log.d(TAG, "MAX event type=" + event.getEventType()
                    + " class=" + event.getClassName()
                    + " text=" + event.getText());
            if (android.os.SystemClock.uptimeMillis() - lastMaxTreeLogAt > 1000) {
                lastMaxTreeLogAt = android.os.SystemClock.uptimeMillis();
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    AccessibilityNodeInfo root = source;
                    AccessibilityNodeInfo parent;
                    while ((parent = root.getParent()) != null) {
                        root.recycle();
                        root = parent;
                    }
                    Log.i(TAG, "Inspecting MAX event root");
                    maxChatOpen = inspectMaxChat(root);
                    Log.i(TAG, "MAX chat open=" + maxChatOpen);
                    logInterestingNodes(root, 0);
                    root.recycle();
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.i(TAG, "key code=" + event.getKeyCode()
                + " scan=" + event.getScanCode()
                + " action=" + event.getAction());
        if (event.getKeyCode() != BYD_MICROPHONE_KEY_CODE) {
            return false;
        }
        if (!maxChatOpen) {
            Log.i(TAG, "Microphone key left to BYD Voice: no open MAX chat");
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() > 0) {
            return true;
        }
        boolean handled = MainActivity.handleSteeringMicrophone(
                event.getAction() == KeyEvent.ACTION_DOWN);
        Log.i(TAG, "Microphone routed to MAX, handled=" + handled);
        return handled;
    }

    private boolean inspectMaxChat(AccessibilityNodeInfo node) {
        CharSequence packageName = node.getPackageName();
        if (packageName == null
                || !"ru.oneme.app".contentEquals(packageName)) {
            return false;
        }
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String viewId = node.getViewIdResourceName();
        if (containsChatComposerMarker(text)
                || containsChatComposerMarker(description)
                || containsChatComposerMarker(viewId)) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            Log.i(TAG, "MAX composer marker text=" + text
                    + " desc=" + description + " id=" + viewId
                    + " bounds=" + bounds);
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                boolean found = inspectMaxChat(child);
                child.recycle();
                if (found) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsChatComposerMarker(CharSequence value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toString().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("message")
                || normalized.equals("сообщение")
                || normalized.startsWith("record voice")
                || normalized.startsWith("record audio")
                || normalized.contains("голосов");
    }

    private void logVisibleWindows() {
        List<AccessibilityWindowInfo> windows = getWindows();
        Log.i(TAG, "windows=" + windows.size());
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            Log.i(TAG, "window id=" + window.getId()
                    + " type=" + window.getType()
                    + " rootPackage=" + (root == null
                    ? null : root.getPackageName()));
            if (root != null) {
                logInterestingNodes(root, 0);
                root.recycle();
            }
        }
    }

    private void logInterestingNodes(AccessibilityNodeInfo node, int depth) {
        if (depth > 20) {
            return;
        }
        CharSequence packageName = node.getPackageName();
        if (packageName != null && "ru.oneme.app".contentEquals(packageName)) {
            CharSequence text = node.getText();
            CharSequence description = node.getContentDescription();
            String viewId = node.getViewIdResourceName();
            if (text != null || description != null || viewId != null) {
                Log.i(TAG, "MAX node text=" + text
                        + " desc=" + description
                        + " id=" + viewId
                        + " clickable=" + node.isClickable()
                        + " longClickable=" + node.isLongClickable());
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                logInterestingNodes(child, depth + 1);
                child.recycle();
            }
        }
    }
}
