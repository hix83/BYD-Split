package ru.logunov.bydsplit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Shell helpers fail closed without their launcher, without periodic polling or wake locks. */
final class HelperLifetime {
    static Socket attach(String[] args) throws Exception {
        int offset = args.length - 3;
        if (offset < 0 || !"--owner".equals(args[offset])) {
            throw new IllegalArgumentException("Launcher owner required");
        }
        int port = Integer.parseInt(args[offset + 1]);
        String token = args[offset + 2];
        if (port < 1 || port > 65535 || !token.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("Invalid launcher owner");
        }
        Socket owner = new Socket();
        try {
            owner.connect(new InetSocketAddress("127.0.0.1", port), 1000);
            owner.setSoTimeout(1500);
            owner.getOutputStream().write((token + "\n").getBytes(StandardCharsets.UTF_8));
            owner.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    owner.getInputStream(), StandardCharsets.UTF_8));
            if (!"OK".equals(reader.readLine())) throw new IllegalStateException("Owner refused");
            owner.setSoTimeout(0);
            Thread guard = new Thread(() -> {
                try { while (reader.read() != -1) { } } catch (Exception ignored) { }
                finally {
                    try { owner.close(); } catch (Exception ignored) { }
                    exit();
                }
            }, "byd-helper-lifetime");
            guard.setDaemon(true);
            guard.start();
            return owner;
        } catch (Exception error) {
            owner.close();
            throw error;
        }
    }

    /** Bound even native cleanup that blocks forever. */
    static void exit() {
        Thread deadline=new Thread(()->{try{Thread.sleep(2000);}catch(InterruptedException ignored){}Runtime.getRuntime().halt(0);},"byd-helper-exit-deadline");
        deadline.setDaemon(true);deadline.start();System.exit(0);
    }
    /** A manual scanner must expire even while its owner and UI remain alive. */
    static void limit(long millis) {
        if(millis<=0)throw new IllegalArgumentException("Invalid lifetime");
        Thread deadline=new Thread(()->{try{Thread.sleep(millis);}catch(InterruptedException ignored){}exit();},"byd-helper-max-lifetime");
        deadline.setDaemon(true);deadline.start();
    }

    private HelperLifetime() { }
}
