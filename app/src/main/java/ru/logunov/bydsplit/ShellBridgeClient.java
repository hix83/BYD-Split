package ru.logunov.bydsplit;

import android.content.Context;
import android.os.SystemClock;
import android.os.Build;
import android.view.KeyEvent;
import android.view.InputEvent;
import android.view.InputDevice;
import android.util.Log;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class ShellBridgeClient {
    private static final String TAG = "BYD_SPLIT_BRIDGE";
    private static final int FAST_INPUT_PORT = 37528;
    private static final String TOKEN = "d5c7a1429b68460e";
    private static final Pattern PACKAGE_NAME =
            Pattern.compile("[A-Za-z0-9._]+");
    private static final Pattern COMPONENT_NAME =
            Pattern.compile("[A-Za-z0-9._$/]+");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<String> latestMove = new AtomicReference<>();
    private final AtomicBoolean moveDrainScheduled = new AtomicBoolean();
    private final LocalAdbManager localAdb;
    private final java.util.Map<Integer, Long> downTimes = new java.util.HashMap<>();

    ShellBridgeClient(Context context) {
        localAdb = LocalAdbManager.get(context);
    }

    void bootstrap(boolean includeSteering, Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(
                localAdb.startHelpers(includeSteering)));
    }

    void restartHelpers(Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(
                localAdb.startHelpers(true)));
    }

    void restartInput(Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(
                localAdb.startHelpers(false)));
    }

    void captureNextSteeringKey(Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(sendCommand(
                "BYD_STEERING_CAPTURE_V1 " + TOKEN, 37530)));
    }

    void cancelSteeringKeyCapture(Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(sendCommand(
                "BYD_STEERING_CAPTURE_CANCEL_V1 " + TOKEN, 37530)));
    }

    void close() {
        executor.shutdownNow();
    }

    void launchOnDisplay(String componentName, int displayId,
                         Consumer<Boolean> callback) {
        if (!COMPONENT_NAME.matcher(componentName).matches()
                || componentName.indexOf('/') <= 0
                || displayId < 1 || displayId > 999) {
            callback.accept(false);
            return;
        }
        executor.execute(() -> {
            int separator = componentName.indexOf('/');
            String packageName = componentName.substring(0, separator);
            boolean success = localAdb.launchOnDisplay(
                    componentName, packageName, displayId);
            if (!success) {
                SystemClock.sleep(250);
                success = localAdb.launchOnDisplay(
                        componentName, packageName, displayId);
            }
            callback.accept(success);
        });
    }

    void closeApp(String packageName, int displayId,
                  Consumer<Boolean> callback) {
        if (!PACKAGE_NAME.matcher(packageName).matches()
                || displayId < 1 || displayId > 999) {
            callback.accept(false);
            return;
        }
        executor.execute(() -> callback.accept(
                localAdb.closeApp(packageName, displayId)));
    }

    void injectBack(int displayId) {
        if (displayId < 1 || displayId > 999) {
            return;
        }
        if (Build.VERSION.SDK_INT == 28) {
            executor.execute(() -> {
                long now = SystemClock.uptimeMillis();
                for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                    sendOwned(displayId, new KeyEvent(now, SystemClock.uptimeMillis(), action,
                            KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, KeyEvent.FLAG_FROM_SYSTEM,
                            InputDevice.SOURCE_KEYBOARD));
                }
            });
        } else executor.execute(() -> localAdb.injectBack(displayId));
    }

    void injectSingleFingerMotion(int displayId, int action, int x, int y) {
        if (displayId < 1 || displayId > 999
                || (action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL)) {
            return;
        }
        if (Build.VERSION.SDK_INT == 28) {
            executor.execute(() -> {
                long now = SystemClock.uptimeMillis();
                if (action == MotionEvent.ACTION_DOWN) downTimes.put(displayId, now);
                long down = downTimes.containsKey(displayId) ? downTimes.get(displayId) : now;
                MotionEvent event = MotionEvent.obtain(down, now, action, x, y, 0);
                event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                sendOwned(displayId, event);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) downTimes.remove(displayId);
            });
            return;
        }
        String command = "BYD_FAST_MULTI_V1 " + TOKEN + " " + displayId
                + " " + action + " 0 1 0 "
                + Math.max(0, x) + " " + Math.max(0, y);
        executor.execute(() -> sendCommand(command, FAST_INPUT_PORT));
    }

    void injectMotionEvent(int displayId, MotionEvent event) {
        if (displayId < 1 || displayId > 999
                || event.getPointerCount() < 1 || event.getPointerCount() > 10) {
            return;
        }
        if (Build.VERSION.SDK_INT == 28) {
            MotionEvent copy = MotionEvent.obtain(event);
            executor.execute(() -> sendOwned(displayId, copy));
            return;
        }
        StringBuilder command = new StringBuilder("BYD_FAST_MULTI_V1 ")
                .append(TOKEN).append(' ')
                .append(displayId).append(' ')
                .append(event.getActionMasked()).append(' ')
                .append(event.getActionIndex()).append(' ')
                .append(event.getPointerCount());
        for (int index = 0; index < event.getPointerCount(); index++) {
            command.append(' ').append(event.getPointerId(index))
                    .append(' ').append(Math.max(0, Math.round(event.getX(index))))
                    .append(' ').append(Math.max(0, Math.round(event.getY(index))));
        }
        String encoded = command.toString();
        if (event.getActionMasked() != MotionEvent.ACTION_MOVE) {
            executor.execute(() -> sendCommand(encoded, FAST_INPUT_PORT));
            return;
        }
        latestMove.set(encoded);
        if (moveDrainScheduled.compareAndSet(false, true)) {
            executor.execute(this::drainLatestMoves);
        }
    }

    private void drainLatestMoves() {
        try {
            String command;
            while ((command = latestMove.getAndSet(null)) != null) {
                sendCommand(command, FAST_INPUT_PORT);
            }
        } finally {
            moveDrainScheduled.set(false);
            if (latestMove.get() != null
                    && moveDrainScheduled.compareAndSet(false, true)) {
                executor.execute(this::drainLatestMoves);
            }
        }
    }

    void releaseDisplay(int displayId) {
        executor.execute(() -> { downTimes.remove(displayId); });
    }

    private void sendOwned(int display, InputEvent event) {
        try {
            if (!ShellDisplayBridge.input(display, event)) Log.e(TAG, "Shell display rejected input for " + display);
        } catch (Exception e) { Log.e(TAG, "Shell display input failed for " + display, e); }
        finally { if (event instanceof MotionEvent) ((MotionEvent) event).recycle(); }
    }

    private boolean sendCommand(String command, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), port), 800);
            socket.setSoTimeout(3000);

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write(command);
            writer.newLine();
            writer.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            boolean success = "OK".equals(response);
            if (!success) {
                Log.e(TAG, "Bridge response: " + response);
            }
            return success;
        } catch (Exception error) {
            Log.e(TAG, "Bridge unavailable", error);
            return false;
        }
    }
}
