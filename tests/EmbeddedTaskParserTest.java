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
        System.out.println("EmbeddedTaskParserTest: 10 checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
