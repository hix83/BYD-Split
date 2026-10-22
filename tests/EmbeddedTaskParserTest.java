package ru.logunov.bydsplit;

public final class EmbeddedTaskParserTest {
    public static void main(String[] args) {
        String dump = "Stack id=4 bounds=[] displayId=30 userId=0\n"
                + " taskId=7: com.android.calculator2/.Calculator visible=true\n"
                + "Stack id=5 bounds=[] displayId=3 userId=0\n"
                + " taskId=8: com.android.gallery3d/.Gallery visible=false\n";
        check(EmbeddedTaskParser.root(dump, "com.android.calculator2", 3, true) == null, "3 must not match 30");
        check(EmbeddedTaskParser.root(dump, "com.android.calculator2", 30, true) == 4, "Android 9 visible task");
        check(EmbeddedTaskParser.root(dump, "com.android.gallery3d", 3, true) == null, "hidden task is not success");
        check(EmbeddedTaskParser.root(dump, "com.android.gallery3d", null, false) == 5, "find stack to move");
        check(EmbeddedTaskParser.root(dump, "com.android.calculator", null, false) == null, "package boundary");
        check(EmbeddedTaskParser.root(dump.replace("Stack", "RootTask"), "com.android.calculator2", 30, true) == 4,
                "later Android format");
        check(EmbeddedTaskParser.root(" taskId=7: com.android.calculator2/.Calculator visible=true", "com.android.calculator2", 30, true) == null,
                "no orphan task");
        check(EmbeddedTaskParser.task(dump, "com.android.calculator2", 30) == 7, "focus uses task id, not stack id");
        check(EmbeddedTaskParser.task(dump, "com.android.calculator2", 3) == null, "focus must stay on requested display");
        check(EmbeddedTaskParser.task(dump, "com.android.gallery3d", 3) == null, "hidden task must not receive focus");
        String shared = "Stack id=12 displayId=0\n taskId=100: ru.logunov.bydsplit/.MainActivity visible=true\n taskId=101: ru.yandex.music/.Music visible=true\nStack id=15 displayId=3\n";
        check(EmbeddedTaskParser.anyTask(shared, "ru.yandex.music", 0) == 101, "move only music task, never host in shared stack");
        String config = shared.replace("Stack id=12 displayId=0", "Stack id=12 displayId=0\n configuration={mWindowingMode=fullscreen}")
                .replace("Stack id=15 displayId=3", "Stack id=15 displayId=3\n configuration={mWindowingMode=fullscreen}");
        check(EmbeddedTaskParser.taskDisplay(shared, 101) == 0, "task source display");
        check(EmbeddedTaskParser.fullscreenRootOnDisplay(config, 0) == null, "never reparent root containing host");
        check(EmbeddedTaskParser.fullscreenRootOnDisplay(config, 3) == 15, "target standard fullscreen root");
        check(EmbeddedTaskParser.fullscreenRootOnDisplay(config.replace("mWindowingMode=fullscreen", "mWindowingMode=split-screen-secondary"), 3) == null, "reject split-screen secondary placeholder");
        String covered = "Stack id=12 displayId=0\n taskId=101: ru.yandex.music/.Music visible=true topActivity=ComponentInfo{com.android.settings/.Settings}\n";
        check(EmbeddedTaskParser.root(covered, "ru.yandex.music", 0, true) == null, "stack visible does not mean music is foreground");
        check(EmbeddedTaskParser.fullscreenRootOnDisplay(config.replace("Stack id=15 displayId=3\n configuration={mWindowingMode=fullscreen}", "Stack id=15 displayId=3\n configuration={mWindowingMode=fullscreen}\n taskId=105: ru.yandex.music/.Music visible=true topActivity=ComponentInfo{ru.logunov.bydsplit/.MainActivity}"), 3) == null,
                "host as top Activity also excludes a guest-named root");
        System.out.println("EmbeddedTaskParserTest passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
