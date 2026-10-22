package ru.logunov.bydsplit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Android 9 and later am stack list formats; display IDs must match exactly. */
final class EmbeddedTaskParser {
    private static final Pattern ROOT = Pattern.compile("^(?:RootTask|Stack) id=(\\d+).*\\bdisplayId=(\\d+)\\b");
    private static final Pattern TASK = Pattern.compile("^\\s*taskId=(\\d+): ([A-Za-z0-9._]+)/");
    static Integer root(String dump, String pkg, Integer requestedDisplay, boolean visibleOnly) {
        return find(dump, pkg, requestedDisplay, visibleOnly, false);
    }
    static Integer task(String dump, String pkg, int display) {
        return find(dump, pkg, display, true, true);
    }
    static Integer anyTask(String dump, String pkg, Integer display) {
        return find(dump, pkg, display, false, true);
    }
    static Integer fullscreenRootOnDisplay(String dump, int display) {
        for (String block : dump.split("(?m)(?=^(?:RootTask|Stack) id=)")) {
            Matcher header = ROOT.matcher(block.split("\n", 2)[0]);
            if (header.find() && Integer.parseInt(header.group(2)) == display
                    && block.contains("mWindowingMode=fullscreen")
                    && !block.contains("ru.logunov.bydsplit/")) return Integer.valueOf(header.group(1));
        }
        return null;
    }
    static Integer taskDisplay(String dump, int taskId) {
        Integer display = null;
        for (String line : dump.split("\r?\n")) {
            Matcher header = ROOT.matcher(line);
            if (header.find()) display = Integer.valueOf(header.group(2));
            Matcher task = TASK.matcher(line);
            if (task.find() && Integer.parseInt(task.group(1)) == taskId) return display;
        }
        return null;
    }
    private static Integer find(String dump, String pkg, Integer requestedDisplay, boolean visibleOnly, boolean taskId) {
        Integer root = null, display = null;
        for (String line : dump.split("\r?\n")) {
            Matcher header = ROOT.matcher(line);
            if (header.find()) { root = Integer.parseInt(header.group(1)); display = Integer.parseInt(header.group(2)); }
            Matcher task = TASK.matcher(line);
            if (root != null && task.find() && pkg.equals(task.group(2))
                    && (requestedDisplay == null || requestedDisplay.equals(display))
                    && (!visibleOnly || line.contains("visible=true")
                        && (!line.contains("topActivity=") || line.contains("topActivity=ComponentInfo{" + pkg + "/")))) return taskId ? Integer.valueOf(task.group(1)) : root;
        }
        return null;
    }
}
