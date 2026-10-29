package ru.logunov.bydsplit;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** An open launcher owns the helpers through sockets, including across abrupt process death. */
final class HelperSession implements Closeable {
    private static HelperSession current;
    private final ServerSocket server;
    private final Set<Socket> clients = new HashSet<>();
    private final String token = UUID.randomUUID().toString().replace("-", "");
    private volatile boolean closed;
    private final Consumer<String> messages;
    private final Runnable disconnected;

    private HelperSession(Consumer<String> messages, Runnable disconnected) throws Exception {
        this.messages = messages;
        this.disconnected = disconnected;
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        Thread listener = new Thread(this::accept, "byd-helper-owner");
        listener.setDaemon(true);
        listener.start();
    }

    static synchronized HelperSession open() throws Exception {
        HelperSession replacement = new HelperSession(null, null);
        if (current != null) current.close();
        current = replacement;
        return replacement;
    }

    static synchronized HelperSession current() { return current; }
    static HelperSession observe(Consumer<String> messages, Runnable disconnected) throws Exception {
        return new HelperSession(messages, disconnected);
    }

    String arguments() { return " --owner " + server.getLocalPort() + " " + token; }

    synchronized boolean isOpen() { return !closed; }

    private void accept() {
        try {
            while (!closed) {
                Socket socket = server.accept();
                // Track even an unauthenticated connection so close() cannot leave it alive.
                synchronized (this) {
                    if (closed) { socket.close(); break; }
                    clients.add(socket);
                }
                boolean authenticated = false;
                try {
                    socket.setSoTimeout(1000);
                    String request = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
                    if (token.equals(request)) {
                        socket.getOutputStream().write("OK\n".getBytes(StandardCharsets.UTF_8));
                        socket.getOutputStream().flush();
                        socket.setSoTimeout(0);
                        BufferedReader messagesReader = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.UTF_8));
                        Thread watcher = new Thread(() -> releaseWhenDisconnected(socket, messagesReader),
                                "byd-helper-connection");
                        watcher.setDaemon(true);
                        watcher.start();
                        authenticated = true;
                    }
                } catch (Exception ignored) { }
                if (!authenticated) {
                    synchronized (this) { clients.remove(socket); }
                    socket.close();
                }
            }
        } catch (Exception ignored) {
            // A broken ownership server must also stop its existing helpers.
        } finally { close(); }
    }

    private void releaseWhenDisconnected(Socket socket, BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) if (messages != null) messages.accept(line);
        }
        catch (Exception ignored) { }
        finally {
            synchronized (this) { clients.remove(socket); }
            try { socket.close(); } catch (Exception ignored) { }
            if (!closed && disconnected != null) disconnected.run();
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { server.close(); } catch (Exception ignored) { }
        for (Socket socket : clients) try { socket.close(); } catch (Exception ignored) { }
        clients.clear();
    }
}
