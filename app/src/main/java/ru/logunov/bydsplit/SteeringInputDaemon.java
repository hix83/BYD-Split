package ru.logunov.bydsplit;

import android.os.Process;
import android.os.SystemClock;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Runs under the connected ADB shell UID and reads BYD's simulated steering
 * key device. DiLink consumes these keys before AccessibilityService sees them.
 */
public final class SteeringInputDaemon {
    private static final String TOKEN = "d5c7a1429b68460e";
    private static final int APP_PORT = 37529;
    private static final int HEALTH_PORT = 37530;
    private static final int EV_KEY = 1;
    private static final int KEY_DOWN = 1;
    private static final int KEY_UP = 0;
    private static final int DEFAULT_SCAN_VOICE_SHORT = 290;
    private static final int DEFAULT_SCAN_VOICE_LONG = 312;
    private static final long GESTURE_QUIET_PERIOD_MS = 80;
    private static final long LONG_HOLD_MS = 650;
    private static long lastVoiceSignalAt;
    private static volatile boolean captureNextKey;

    private SteeringInputDaemon() {
    }

    public static void main(String[] args) {
        int shortScan = parseScanCode(
                args, 0, DEFAULT_SCAN_VOICE_SHORT);
        int longScan = parseScanCode(
                args, 1, DEFAULT_SCAN_VOICE_LONG);
        System.out.println("Steering configuration short=" + shortScan
                + " long=" + longScan);
        Thread healthThread = new Thread(
                SteeringInputDaemon::serveHealth, "byd-steering-health");
        healthThread.setDaemon(true);
        healthThread.start();
        while (true) {
            try {
                monitor(findSimulatedKeyDevice(), shortScan, longScan);
            } catch (Exception error) {
                error.printStackTrace();
                SystemClock.sleep(1000);
            }
        }
    }

    private static void serveHealth() {
        try (ServerSocket server = new ServerSocket(
                HEALTH_PORT, 4, InetAddress.getByName("127.0.0.1"))) {
            while (true) {
                try (Socket socket = server.accept()) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(),
                                    StandardCharsets.UTF_8));
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(),
                                    StandardCharsets.UTF_8));
                    String request = reader.readLine();
                    if (("BYD_STEERING_CAPTURE_V1 " + TOKEN)
                            .equals(request)) {
                        captureNextKey = true;
                        writer.write("OK\n");
                    } else if (("BYD_STEERING_CAPTURE_CANCEL_V1 " + TOKEN)
                            .equals(request)) {
                        captureNextKey = false;
                        writer.write("OK\n");
                    } else {
                        writer.write(("BYD_STEERING_PING_V1 " + TOKEN)
                                .equals(request) ? "OK\n" : "ERR\n");
                    }
                    writer.flush();
                } catch (Exception ignored) {
                    // Health check failures must not stop steering monitoring.
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private static File findSimulatedKeyDevice() throws Exception {
        // Input event numbers are assigned dynamically and can change after
        // a reboot. Always resolve the BYD driver by name.
        File inputClass = new File("/sys/class/input");
        File[] entries = inputClass.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (!entry.getName().startsWith("event")) {
                    continue;
                }
                String canonicalPath = entry.getCanonicalPath();
                if (canonicalPath.contains("simulate_keys")
                        || canonicalPath.contains("simulate-keys")) {
                    File device = new File(
                            "/dev/input/" + entry.getName());
                    System.out.println("Steering device=" + device
                            + " source=" + canonicalPath);
                    return device;
                }
                File nameFile = new File(entry, "device/name");
                if (!nameFile.isFile()) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(
                        new FileReader(nameFile))) {
                    if ("simulate-keys".equals(reader.readLine())) {
                        File device = new File(
                                "/dev/input/" + entry.getName());
                        System.out.println("Steering device=" + device);
                        return device;
                    }
                }
            }
        }
        throw new IllegalStateException("simulate-keys input device not found");
    }

    private static void monitor(
            File device, int shortScan, int longScan) throws Exception {
        int recordSize = Process.is64Bit() ? 24 : 16;
        int typeOffset = Process.is64Bit() ? 16 : 8;
        byte[] record = new byte[recordSize];
        long sharedCodeDownAt = 0;
        try (BufferedInputStream input = new BufferedInputStream(
                new FileInputStream(device))) {
            while (true) {
                readFully(input, record);
                ByteBuffer buffer = ByteBuffer.wrap(record)
                        .order(ByteOrder.nativeOrder());
                int type = Short.toUnsignedInt(buffer.getShort(typeOffset));
                int code = Short.toUnsignedInt(buffer.getShort(typeOffset + 2));
                int value = buffer.getInt(typeOffset + 4);
                if (type == EV_KEY && value == KEY_DOWN && captureNextKey) {
                    captureNextKey = false;
                    boolean handled = notifyApp("CAPTURE " + code);
                    System.out.println("Steering captured scan=" + code
                            + " handled=" + handled);
                    if (handled) {
                        stopBydVoice();
                    }
                    continue;
                }
                if (type != EV_KEY
                        || (code != shortScan && code != longScan)) {
                    continue;
                }
                if (shortScan == longScan) {
                    if (value == KEY_DOWN) {
                        sharedCodeDownAt = SystemClock.uptimeMillis();
                    } else if (value == KEY_UP && sharedCodeDownAt > 0) {
                        long duration = SystemClock.uptimeMillis()
                                - sharedCodeDownAt;
                        sharedCodeDownAt = 0;
                        dispatchVoiceSignal(
                                code,
                                duration >= LONG_HOLD_MS
                                        ? "LONG" : "SHORT");
                    }
                } else if (value == KEY_DOWN) {
                    dispatchVoiceSignal(
                            code, code == longScan ? "LONG" : "SHORT");
                }
            }
        }
    }

    private static void dispatchVoiceSignal(int code, String kind) {
        long now = SystemClock.uptimeMillis();
        long quietFor = now - lastVoiceSignalAt;
        lastVoiceSignalAt = now;
        if (quietFor < GESTURE_QUIET_PERIOD_MS) {
            System.out.println("Steering scan=" + code
                    + " ignored-repeat");
            return;
        }
        boolean handled = notifyApp(kind);
        System.out.println("Steering time=" + now + " scan=" + code
                + " kind=" + kind + " handled=" + handled);
        if (handled) {
            stopBydVoice();
        }
    }

    private static int parseScanCode(
            String[] args, int index, int fallback) {
        if (index >= args.length) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(args[index]);
            return value > 0 && value <= 0xffff ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void readFully(BufferedInputStream input, byte[] target)
            throws Exception {
        int offset = 0;
        while (offset < target.length) {
            int count = input.read(target, offset, target.length - offset);
            if (count < 0) {
                throw new IllegalStateException("Input device closed");
            }
            offset += count;
        }
    }

    private static boolean notifyApp(String kind) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"), APP_PORT), 300);
            socket.setSoTimeout(500);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write("BYD_STEERING_V1 " + TOKEN + " " + kind);
            writer.newLine();
            writer.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            return "HANDLED".equals(reader.readLine());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void stopBydVoice() {
        try {
            new ProcessBuilder(
                    "/system/bin/am", "force-stop", "com.byd.autovoice")
                    .start()
                    .waitFor();
        } catch (Exception error) {
            error.printStackTrace();
        }
    }
}
