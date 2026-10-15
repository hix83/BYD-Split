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
    private static Integer find(String dump, String pkg, Integer requestedDisplay, boolean visibleOnly, boolean taskId) {
        Integer root = null, display = null;
        for (String line : dump.split("\r?\n")) {
            Matcher header = ROOT.matcher(line);
            if (header.find()) { root = Integer.parseInt(header.group(1)); display = Integer.parseInt(header.group(2)); }
            Matcher task = TASK.matcher(line);
            if (root != null && task.find() && pkg.equals(task.group(2))
                    && (requestedDisplay == null || requestedDisplay.equals(display))
                    && (!visibleOnly || line.contains("visible=true"))) return taskId ? Integer.valueOf(task.group(1)) : root;
        }
        return null;
    }
}
