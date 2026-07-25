package ru.logunov.bydsplit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Adapter for the split-screen extensions present in BYD DiLink 5 firmware.
 *
 * These methods are OEM additions to android.app.StatusBarManager and are not
 * part of the public Android SDK, so reflection keeps the APK installable on
 * regular Android devices as well.
 */
final class BydSplitController {
    private static final String TAG = "BYD_SPLIT_NATIVE";

    private final Object statusBarManager;

    @SuppressLint("WrongConstant")
    BydSplitController(Context context) {
        statusBarManager = context.getSystemService("statusbar");
    }

    boolean isAvailable() {
        return findMethod("enterSplitScreenModeByLaucher",
                String.class, Intent.class) != null;
    }

    int isAppSupported(String packageName) {
        Object result = invoke(
                "isAppSuportSplit",
                new Class<?>[]{String.class},
                packageName
        );
        return result instanceof Integer ? (Integer) result : -1;
    }

    boolean enter(String primaryPackage, Intent secondaryIntent) {
        return invoke(
                "enterSplitScreenModeByLaucher",
                new Class<?>[]{String.class, Intent.class},
                primaryPackage,
                secondaryIntent
        ) != InvocationFailed.INSTANCE;
    }

    boolean enter(String primaryPackage, String secondaryPackage) {
        return invoke(
                "enterSplitScreenModeByLaucher",
                new Class<?>[]{String.class, String.class},
                primaryPackage,
                secondaryPackage
        ) != InvocationFailed.INSTANCE;
    }

    boolean swap() {
        return invoke(
                "swapSplitScreenWindow",
                new Class<?>[0]
        ) != InvocationFailed.INSTANCE;
    }

    int getStatus() {
        Object result = invoke("getSplitScreenStatus", new Class<?>[0]);
        return result instanceof Integer ? (Integer) result : -1;
    }

    void logDiagnostics(String... packageNames) {
        Log.i(TAG, "OEM API available=" + isAvailable() + ", status=" + getStatus());
        for (String packageName : packageNames) {
            Log.i(TAG, "split support " + packageName + "="
                    + isAppSupported(packageName));
        }
    }

    private Method findMethod(String name, Class<?>... parameterTypes) {
        if (statusBarManager == null) {
            return null;
        }
        try {
            return statusBarManager.getClass().getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException | SecurityException error) {
            Log.e(TAG, "Method unavailable: " + name, error);
            return null;
        }
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) {
        Method method = findMethod(name, parameterTypes);
        if (method == null) {
            return InvocationFailed.INSTANCE;
        }
        try {
            return method.invoke(statusBarManager, args);
        } catch (IllegalAccessException | InvocationTargetException
                 | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException
                    && ((InvocationTargetException) error).getCause() != null
                    ? ((InvocationTargetException) error).getCause()
                    : error;
            Log.e(TAG, "OEM call failed: " + name, cause);
            return InvocationFailed.INSTANCE;
        }
    }

    private enum InvocationFailed {
        INSTANCE
    }
}
