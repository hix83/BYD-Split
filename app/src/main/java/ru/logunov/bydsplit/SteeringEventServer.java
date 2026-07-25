package ru.logunov.bydsplit;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SteeringEventServer implements Closeable {
    private static final String TAG = "BYD_STEERING";
    private static final String TOKEN = "d5c7a1429b68460e";
    private static final int PORT = 37529;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ServerSocket server;

    void start() {
        executor.execute(() -> {
            try (ServerSocket listening = new ServerSocket(
                    PORT, 4, InetAddress.getByName("127.0.0.1"))) {
                server = listening;
                while (!listening.isClosed()) {
                    try (Socket socket = listening.accept()) {
                        handle(socket);
                    } catch (Exception error) {
                        if (!listening.isClosed()) {
                            Log.e(TAG, "Steering event connection failed", error);
                        }
                    }
                }
            } catch (Exception error) {
                Log.e(TAG, "Steering event server failed", error);
            }
        });
    }

    private void handle(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();
        String[] parts = line == null ? new String[0] : line.split(" ");
        boolean handled = parts.length == 3
                && "BYD_STEERING_V1".equals(parts[0])
                && TOKEN.equals(parts[1])
                && ("SHORT".equals(parts[2]) || "LONG".equals(parts[2]))
                && MainActivity.handleSteeringPulse("LONG".equals(parts[2]));
        Log.i(TAG, "Low-level steering event="
                + (parts.length == 3 ? parts[2] : "INVALID")
                + " handled=" + handled);
        writer.write(handled ? "HANDLED\n" : "PASS\n");
        writer.flush();
    }

    @Override
    public void close() {
        try {
            if (server != null) {
                server.close();
            }
        } catch (Exception ignored) {
            // The executor is stopped below.
        }
        executor.shutdownNow();
    }
}
