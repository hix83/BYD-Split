package ru.logunov.bydsplit;

import android.content.Context;
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
    private static final int PORT = 37527;
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

    ShellBridgeClient(Context context) {
        localAdb = LocalAdbManager.get(context);
    }

    static final class Health {
        final boolean bridge;
        final boolean input;
        final boolean steering;

        Health(boolean bridge, boolean input, boolean steering) {
            this.bridge = bridge;
            this.input = input;
            this.steering = steering;
        }

        boolean isReady() {
            return bridge && input && steering;
        }
    }

    void checkHealth(Consumer<Health> callback) {
        executor.execute(() -> callback.accept(new Health(
                localAdb.isConnected()
                        || sendCommand("BYD_PING_V1 " + TOKEN, PORT),
                sendCommand("BYD_FAST_PING_V1 " + TOKEN, FAST_INPUT_PORT),
                sendCommand("BYD_STEERING_PING_V1 " + TOKEN, 37530))));
    }

    void bootstrap(boolean includeSteering, Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(
                localAdb.startHelpers(includeSteering)));
    }

    void restartHelpers(Consumer<Boolean> callback) {
        executor.execute(() -> {
            boolean success = localAdb.startHelpers(true);
            if (!success) {
                success = sendCommand(
                        "BYD_CONTROL_V1 " + TOKEN + " RESTART_HELPERS", PORT);
            }
            callback.accept(success);
        });
    }

    void restartInput(Consumer<Boolean> callback) {
        executor.execute(() -> {
            boolean success = localAdb.startHelpers(false);
            if (!success) {
                success = sendCommand(
                        "BYD_CONTROL_V1 " + TOKEN + " RESTART_INPUT", PORT);
            }
            callback.accept(success);
        });
    }

    void captureNextSteeringKey(Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(sendCommand(
                "BYD_STEERING_CAPTURE_V1 " + TOKEN, 37530)));
    }

    void cancelSteeringKeyCapture(Consumer<Boolean> callback) {
        executor.execute(() -> callback.accept(sendCommand(
                "BYD_STEERING_CAPTURE_CANCEL_V1 " + TOKEN, 37530)));
    }

    void enterPair(String primaryPackage, String secondaryPackage,
                   Consumer<Boolean> callback) {
        if (!PACKAGE_NAME.matcher(primaryPackage).matches()
                || !PACKAGE_NAME.matcher(secondaryPackage).matches()) {
            callback.accept(false);
            return;
        }
        executor.execute(() -> {
            boolean success = send(primaryPackage, secondaryPackage);
            callback.accept(success);
        });
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
                success = sendCommand("BYD_EMBED_V1 " + TOKEN + " "
                        + displayId + " " + componentName);
            }
            callback.accept(success);
        });
    }

    void removeFromDisplay(String packageName, int displayId,
                           Consumer<Boolean> callback) {
        if (!PACKAGE_NAME.matcher(packageName).matches()
                || displayId < 1 || displayId > 999) {
            callback.accept(false);
            return;
        }
        executor.execute(() -> callback.accept(
                localAdb.removeFromDisplay(packageName, displayId)));
    }

    void injectGesture(int displayId, int startX, int startY,
                       int endX, int endY, long durationMs) {
        if (displayId < 1 || displayId > 999) {
            return;
        }
        int safeDuration = (int) Math.max(1, Math.min(durationMs, 5000));
        executor.execute(() -> sendCommand(
                "BYD_INPUT_V1 " + TOKEN + " " + displayId
                        + " " + Math.max(0, startX)
                        + " " + Math.max(0, startY)
                        + " " + Math.max(0, endX)
                        + " " + Math.max(0, endY)
                        + " " + safeDuration));
    }

    void injectBack(int displayId) {
        if (displayId < 1 || displayId > 999) {
            return;
        }
        executor.execute(() -> {
            if (!localAdb.injectBack(displayId)) {
                sendCommand("BYD_KEY_V1 " + TOKEN + " "
                        + displayId + " BACK");
            }
        });
    }

    void injectMotion(int displayId, String action, int x, int y) {
        if (displayId < 1 || displayId > 999
                || !("DOWN".equals(action) || "MOVE".equals(action)
                || "UP".equals(action) || "CANCEL".equals(action))) {
            return;
        }
        String command = "BYD_FAST_MOTION_V1 " + TOKEN + " " + displayId
                + " " + action + " " + Math.max(0, x) + " " + Math.max(0, y);
        if (!"MOVE".equals(action)) {
            executor.execute(() -> sendFastMotion(command));
            return;
        }
        latestMove.set(command);
        if (moveDrainScheduled.compareAndSet(false, true)) {
            executor.execute(this::drainLatestMoves);
        }
    }

    void injectSingleFingerMotion(int displayId, int action, int x, int y) {
        if (displayId < 1 || displayId > 999
                || (action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_MOVE
                && action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL)) {
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
                if (command.startsWith("BYD_FAST_MULTI_V1 ")) {
                    sendCommand(command, FAST_INPUT_PORT);
                } else {
                    sendFastMotion(command);
                }
            }
        } finally {
            moveDrainScheduled.set(false);
            if (latestMove.get() != null
                    && moveDrainScheduled.compareAndSet(false, true)) {
                executor.execute(this::drainLatestMoves);
            }
        }
    }

    private boolean sendFastMotion(String command) {
        if (sendCommand(command, FAST_INPUT_PORT)) {
            return true;
        }
        return sendCommand(command.replace(
                "BYD_FAST_MOTION_V1", "BYD_MOTION_V1"));
    }

    private boolean send(String primaryPackage, String secondaryPackage) {
        return sendCommand("BYD_SPLIT_V1 " + TOKEN + " "
                + primaryPackage + " " + secondaryPackage);
    }

    private boolean sendCommand(String command) {
        return sendCommand(command, PORT);
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
