package ru.logunov.bydsplit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Real child JVMs must die when their owner closes, rather than just flipping a flag. */
public final class HelperSessionTest {
    public static void main(String[] args) throws Exception {
        Process first = null, second = null;
        HelperSession a = HelperSession.open(), b = null;
        try {
            first = child(a.arguments().trim().split(" "));
            check(!first.waitFor(150, TimeUnit.MILLISECONDS), "helper exited with live owner");
            b = HelperSession.open();
            check(first.waitFor(3, TimeUnit.SECONDS) && first.exitValue() == 0,
                    "old helper survived session replacement");
            second = child(b.arguments().trim().split(" "));
            a.close();
            check(!second.waitFor(150, TimeUnit.MILLISECONDS), "old close killed replacement helper");
            b.close();
            check(second.waitFor(3, TimeUnit.SECONDS) && second.exitValue() == 0,
                    "helper survived owner close");
            try { HelperLifetime.attach(new String[0]); throw new AssertionError("missing owner accepted"); }
            catch (IllegalArgumentException expected) { }
            String[] invalid = b.arguments().trim().split(" ");
            try { HelperLifetime.attach(invalid); throw new AssertionError("closed owner accepted"); }
            catch (java.io.IOException | IllegalArgumentException expected) { }
            java.util.concurrent.CountDownLatch state=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch disconnected=new java.util.concurrent.CountDownLatch(1);
            HelperSession monitor=HelperSession.observe(line->{if("STATE 2 1".equals(line))state.countDown();},disconnected::countDown);
            Process reporter=null;
            try {
                reporter=child(monitor.arguments().trim().split(" "));
                check(state.await(2,TimeUnit.SECONDS),"state message lost after owner handshake");
                reporter.destroyForcibly();
                check(disconnected.await(2,TimeUnit.SECONDS),"observer death not reported");
            } finally {monitor.close();if(reporter!=null)reporter.destroyForcibly();}
            HelperSession blocked=HelperSession.open();Process stuck=null;
            try {
                String[] owner=blocked.arguments().trim().split(" ");String[] withHook=new String[owner.length+1];withHook[0]="blocked-hook";System.arraycopy(owner,0,withHook,1,owner.length);
                stuck=child(withHook);blocked.close();
                check(stuck.waitFor(5,TimeUnit.SECONDS)&&stuck.exitValue()==0,"hung OEM cleanup left orphan helper");
            } finally {blocked.close();if(stuck!=null)stuck.destroyForcibly();}
            System.out.println("HelperSessionTest PASS: owner close, replacement, stale close, missing/dead owner");
        } finally {
            a.close(); if (b != null) b.close();
            if (first != null) first.destroyForcibly();
            if (second != null) second.destroyForcibly();
        }
    }

    private static Process child(String[] owner) throws Exception {
        String[] command = new String[owner.length + 4];
        command[0] = System.getProperty("java.home") + "/bin/java";
        command[1] = "-cp"; command[2] = System.getProperty("java.class.path");
        command[3] = Probe.class.getName();
        System.arraycopy(owner, 0, command, 4, owner.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            String ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return new BufferedReader(new InputStreamReader(process.getInputStream())).readLine(); }
                catch (Exception error) { throw new RuntimeException(error); }
            }).get(4, TimeUnit.SECONDS);
            check("READY".equals(ready), "helper failed to attach: " + ready);
            return process;
        } catch (Exception | AssertionError error) { process.destroyForcibly(); throw error; }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            if(args.length>0&&args[0].equals("blocked-hook"))Runtime.getRuntime().addShutdownHook(new Thread(()->{try{Thread.sleep(15000);}catch(Exception ignored){}}));
            java.net.Socket owner=HelperLifetime.attach(args);
            owner.getOutputStream().write("STATE 2 1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            owner.getOutputStream().flush();
            System.out.println("READY"); System.out.flush();
            Thread.sleep(20000);
            throw new AssertionError("orphan helper timed out");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
