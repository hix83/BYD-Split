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
    private static final int SCAN_VOICE_SHORT = 290;
    private static final int SCAN_VOICE_LONG = 312;
    private static final long GESTURE_QUIET_PERIOD_MS = 750;
    private static long lastVoiceSignalAt;

    private SteeringInputDaemon() {
    }

    public static void main(String[] args) {
        Thread healthThread = new Thread(
                SteeringInputDaemon::serveHealth, "byd-steering-health");
        healthThread.setDaemon(true);
        healthThread.start();
        while (true) {
            try {
                monitor(findSimulatedKeyDevice());
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
                    writer.write(("BYD_STEERING_PING_V1 " + TOKEN)
                            .equals(request) ? "OK\n" : "ERR\n");
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
        // DiLink 5 exposes its "simulate-keys" driver at event6. The sysfs
        // name lookup below is retained as a fallback for other revisions.
        File diLink5Device = new File("/dev/input/event6");
        if (diLink5Device.exists()) {
            return diLink5Device;
        }
        File inputClass = new File("/sys/class/input");
        File[] entries = inputClass.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (!entry.getName().startsWith("event")) {
                    continue;
                }
                File nameFile = new File(entry, "device/name");
                if (!nameFile.isFile()) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(
                        new FileReader(nameFile))) {
                    if ("simulate-keys".equals(reader.readLine())) {
                        return new File("/dev/input/" + entry.getName());
                    }
                }
            }
        }
        throw new IllegalStateException("simulate-keys input device not found");
    }

    private static void monitor(File device) throws Exception {
        int recordSize = Process.is64Bit() ? 24 : 16;
        int typeOffset = Process.is64Bit() ? 16 : 8;
        byte[] record = new byte[recordSize];
        try (BufferedInputStream input = new BufferedInputStream(
                new FileInputStream(device))) {
            while (true) {
                readFully(input, record);
                ByteBuffer buffer = ByteBuffer.wrap(record)
                        .order(ByteOrder.nativeOrder());
                int type = Short.toUnsignedInt(buffer.getShort(typeOffset));
                int code = Short.toUnsignedInt(buffer.getShort(typeOffset + 2));
                int value = buffer.getInt(typeOffset + 4);
                if (type == EV_KEY && value == KEY_DOWN
                        && (code == SCAN_VOICE_SHORT
                        || code == SCAN_VOICE_LONG)) {
                    long now = SystemClock.uptimeMillis();
                    long quietFor = now - lastVoiceSignalAt;
                    lastVoiceSignalAt = now;
                    if (quietFor < GESTURE_QUIET_PERIOD_MS) {
                        System.out.println("Steering scan=" + code
                                + " ignored-repeat");
                        continue;
                    }
                    boolean handled = notifyApp(
                            code == SCAN_VOICE_LONG ? "LONG" : "SHORT");
                    System.out.println("Steering time=" + now + " scan=" + code
                            + " handled=" + handled);
                    if (handled) {
                        stopBydVoice();
                    }
                }
            }
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
