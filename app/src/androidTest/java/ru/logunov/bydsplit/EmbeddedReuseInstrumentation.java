package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;

/** Emulator regression: real dock activation and ADB task reuse. */
public final class EmbeddedReuseInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!Build.HARDWARE.contains("ranchu") && !Build.HARDWARE.contains("goldfish"))
                throw new AssertionError("Emulator required");
            AppPreferences.get(getTargetContext()).edit().putBoolean("demo_mode", true).commit();
            MainActivity activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode", true));
            Method activate = MainActivity.class.getDeclaredMethod("activateLargeApp", AppEntry.class);
            activate.setAccessible(true);
            AppEntry settings = new AppEntry("Settings", new ComponentName("com.android.settings", "com.android.settings.Settings"), getTargetContext().getPackageManager().getApplicationIcon("com.android.settings"));
            LocalAdbClient shell = new LocalAdbClient(new LocalAdbKeyStore(getTargetContext()));
            Runnable tap = () -> runOnMainSync(() -> { try { activate.invoke(activity, settings); } catch (Exception e) { throw new RuntimeException(e); } });
            tap.run(); SystemClock.sleep(7000);
            int original = settingsTask(shell);
            tap.run(); tap.run(); tap.run(); SystemClock.sleep(7000);
            if (settingsTask(shell) != original) throw new AssertionError("Repeated tap replaced task");
            Intent contactsIntent = getTargetContext().getPackageManager().getLaunchIntentForPackage("com.android.contacts");
            if (contactsIntent == null) throw new AssertionError("Contacts fixture missing");
            AppEntry contacts = new AppEntry("Contacts", contactsIntent.getComponent(), getTargetContext().getPackageManager().getApplicationIcon("com.android.contacts"));
            runOnMainSync(() -> { try { activate.invoke(activity, contacts); } catch (Exception e) { throw new RuntimeException(e); } });
            SystemClock.sleep(7000);
            tap.run(); SystemClock.sleep(7000);
            if (settingsTask(shell) != original) throw new AssertionError("Switching back replaced task");
            shell.shell("am display move-stack " + original + " 0");
            tap.run(); SystemClock.sleep(7000);
            if (settingsTask(shell) != original) throw new AssertionError("Handoff replaced task");
            // Leave isConnected() true while breaking reads, as with a stale TCP connection.
            Field clientField=LocalAdbManager.class.getDeclaredField("client");clientField.setAccessible(true);
            Object client=clientField.get(LocalAdbManager.get(getTargetContext()));
            Field socketField=LocalAdbClient.class.getDeclaredField("socket");socketField.setAccessible(true);
            java.net.Socket staleSocket = (java.net.Socket)socketField.get(client);
            staleSocket.shutdownInput();
            tap.run(); SystemClock.sleep(7000);
            if (socketField.get(client) == staleSocket || socketField.get(client) == null)
                throw new AssertionError("ADB connection was not renewed");
            if (settingsTask(shell) != original) throw new AssertionError("ADB retry replaced task");
            shell.shell("am force-stop com.android.settings");
            tap.run(); SystemClock.sleep(7000);
            settingsTask(shell);
            result.putString("result", "Repeated taps, A-B-A switch, task return, broken ADB socket and killed app recovered; one embedded task");
            shell.close();
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("error", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private int settingsTask(LocalAdbClient shell) throws Exception {
        String tasks = shell.shell("am stack list");
        int display = -1, count = 0, id = -1;
        for (String line : tasks.split("\n")) {
            java.util.regex.Matcher root = java.util.regex.Pattern.compile("displayId=(\\d+)").matcher(line);
            if (line.startsWith("RootTask") && root.find()) display = Integer.parseInt(root.group(1));
            java.util.regex.Matcher task = java.util.regex.Pattern.compile("taskId=(\\d+): com.android.settings/").matcher(line);
            if (task.find()) {
                if (display <= 0) throw new AssertionError("Settings escaped embedded display");
                if (!line.contains("visible=true")) throw new AssertionError("Settings was not brought to front");
                id = Integer.parseInt(task.group(1)); count++;
            }
        }
        if (count != 1) throw new AssertionError("Expected one settings task, got " + count);
        return id;
    }
}
