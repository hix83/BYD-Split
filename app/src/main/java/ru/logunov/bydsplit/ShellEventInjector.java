package ru.logunov.bydsplit;

import android.view.InputEvent;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Shell-only input compatibility. Di2 ActivityView uses InputForwarder on API 28. */
final class ShellEventInjector {
    private final Object manager;
    private final Method inject, setDisplay, createForwarder, forward;
    private final Map<Integer, Object> forwarders = new HashMap<>();

    @android.annotation.SuppressLint("BlockedPrivateApi")
    ShellEventInjector() throws Exception {
        Class<?> type = Class.forName("android.hardware.input.InputManager");
        manager = type.getDeclaredMethod("getInstance").invoke(null);
        inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
        Method display = null;
        try { display = InputEvent.class.getDeclaredMethod("setDisplayId", int.class); }
        catch (NoSuchMethodException expectedOnAndroid9) { }
        setDisplay = display;
        if (display == null) {
            createForwarder = type.getMethod("createInputForwarder", int.class);
            forward = Class.forName("android.app.IInputForwarder").getMethod("forwardEvent", InputEvent.class);
        } else { createForwarder = null; forward = null; }
    }
    boolean inject(InputEvent event, int display, int mode) throws Exception {
        if (display < 0 || display > 999) return false;
        if (setDisplay != null) {
            setDisplay.invoke(event, display);
            return Boolean.TRUE.equals(inject.invoke(manager, event, mode));
        }
        if (display == 0) return Boolean.TRUE.equals(inject.invoke(manager, event, mode));
        Object target = forwarders.get(display);
        if (target == null) {
            target = createForwarder.invoke(manager, display);
            if (target == null) return false;
            forwarders.put(display, target);
        }
        try { return Boolean.TRUE.equals(forward.invoke(target, event)); }
        catch (Exception e) { forwarders.remove(display); throw e; }
    }
    void release(int display) { forwarders.remove(display); }
}
