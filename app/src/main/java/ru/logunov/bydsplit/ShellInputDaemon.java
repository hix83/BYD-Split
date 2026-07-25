package ru.logunov.bydsplit;

import android.annotation.SuppressLint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Started by app_process under the ADB shell UID. Keeping InputManager alive
 * avoids launching the Android "input" command for every MOVE event.
 */
public final class ShellInputDaemon {
    private static final int PORT = 37528;
    private static final String TOKEN = "d5c7a1429b68460e";
    private static final Map<Integer, Long> DOWN_TIMES = new HashMap<>();

    private ShellInputDaemon() {
    }

    @SuppressLint("BlockedPrivateApi")
    public static void main(String[] args) throws Exception {
        Class<?> inputManagerClass = Class.forName(
                "android.hardware.input.InputManager");
        Method getInstance = inputManagerClass.getDeclaredMethod("getInstance");
        Object inputManager = getInstance.invoke(null);
        Method inject = inputManagerClass.getMethod(
                "injectInputEvent", android.view.InputEvent.class, int.class);
        Method setDisplayId = android.view.InputEvent.class.getDeclaredMethod(
                "setDisplayId", int.class);

        try (ServerSocket server = new ServerSocket(
                PORT, 8, InetAddress.getByName("127.0.0.1"))) {
            while (true) {
                try (Socket socket = server.accept()) {
                    handle(socket, inputManager, inject, setDisplayId);
                } catch (Exception ignored) {
                    // A malformed event must not stop input for both panes.
                }
            }
        }
    }

    private static void handle(Socket socket, Object manager, Method inject,
                               Method setDisplayId) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();
        String[] parts = line == null ? new String[0] : line.split(" ");
        if (parts.length == 2
                && "BYD_FAST_PING_V1".equals(parts[0])
                && TOKEN.equals(parts[1])) {
            writer.write("OK\n");
            writer.flush();
            return;
        }
        if (parts.length < 6 || !TOKEN.equals(parts[1])) {
            writer.write("ERR\n");
            writer.flush();
            return;
        }

        if ("BYD_FAST_MULTI_V1".equals(parts[0])) {
            injectMulti(parts, manager, inject, setDisplayId, writer);
            return;
        }
        if (!"BYD_FAST_MOTION_V1".equals(parts[0]) || parts.length != 6) {
            writer.write("ERR\n");
            writer.flush();
            return;
        }

        int displayId = Integer.parseInt(parts[2]);
        int action = actionFrom(parts[3]);
        float x = Float.parseFloat(parts[4]);
        float y = Float.parseFloat(parts[5]);
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            DOWN_TIMES.put(displayId, now);
        }
        long downTime = DOWN_TIMES.containsKey(displayId)
                ? DOWN_TIMES.get(displayId) : now;

        MotionEvent event = MotionEvent.obtain(
                downTime, now, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        setDisplayId.invoke(event, displayId);
        boolean injected;
        try {
            injected = (Boolean) inject.invoke(manager, event, 0);
        } finally {
            event.recycle();
        }
        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            DOWN_TIMES.remove(displayId);
        }
        writer.write(injected ? "OK\n" : "ERR\n");
        writer.flush();
    }

    private static void injectMulti(String[] parts, Object manager, Method inject,
                                    Method setDisplayId, BufferedWriter writer)
            throws Exception {
        int displayId = Integer.parseInt(parts[2]);
        int actionMasked = Integer.parseInt(parts[3]);
        int actionIndex = Integer.parseInt(parts[4]);
        int pointerCount = Integer.parseInt(parts[5]);
        if (pointerCount < 1 || pointerCount > 10
                || parts.length != 6 + pointerCount * 3
                || actionIndex < 0 || actionIndex >= pointerCount) {
            writer.write("ERR\n");
            writer.flush();
            return;
        }

        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coordinates =
                new MotionEvent.PointerCoords[pointerCount];
        for (int index = 0; index < pointerCount; index++) {
            int offset = 6 + index * 3;
            MotionEvent.PointerProperties pointer =
                    new MotionEvent.PointerProperties();
            pointer.id = Integer.parseInt(parts[offset]);
            pointer.toolType = MotionEvent.TOOL_TYPE_FINGER;
            properties[index] = pointer;

            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.x = Float.parseFloat(parts[offset + 1]);
            coords.y = Float.parseFloat(parts[offset + 2]);
            coords.pressure = 1f;
            coords.size = 1f;
            coordinates[index] = coords;
        }

        long now = SystemClock.uptimeMillis();
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            DOWN_TIMES.put(displayId, now);
        }
        long downTime = DOWN_TIMES.containsKey(displayId)
                ? DOWN_TIMES.get(displayId) : now;
        int action = actionMasked;
        if (actionMasked == MotionEvent.ACTION_POINTER_DOWN
                || actionMasked == MotionEvent.ACTION_POINTER_UP) {
            action |= actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        MotionEvent event = MotionEvent.obtain(
                downTime,
                now,
                action,
                pointerCount,
                properties,
                coordinates,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
        setDisplayId.invoke(event, displayId);
        boolean injected;
        try {
            injected = (Boolean) inject.invoke(manager, event, 0);
        } finally {
            event.recycle();
        }
        if (actionMasked == MotionEvent.ACTION_UP
                || actionMasked == MotionEvent.ACTION_CANCEL) {
            DOWN_TIMES.remove(displayId);
        }
        writer.write(injected ? "OK\n" : "ERR\n");
        writer.flush();
    }

    private static int actionFrom(String action) {
        switch (action) {
            case "DOWN":
                return MotionEvent.ACTION_DOWN;
            case "MOVE":
                return MotionEvent.ACTION_MOVE;
            case "UP":
                return MotionEvent.ACTION_UP;
            case "CANCEL":
                return MotionEvent.ACTION_CANCEL;
            default:
                throw new IllegalArgumentException("Unsupported action");
        }
    }
}
