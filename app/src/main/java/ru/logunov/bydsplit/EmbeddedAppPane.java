package ru.logunov.bydsplit;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

final class EmbeddedAppPane extends FrameLayout implements SurfaceHolder.Callback {
    private static final String TAG = "BYD_EMBEDDED";
    // Hidden in the public SDK, but supported by Android 12's VirtualDisplay.
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;

    private final AppEntry entry;
    private final String paneName;
    private final ShellBridgeClient shellBridgeClient;
    private final Runnable changeAction;
    private final Runnable settingsAction;
    private final SurfaceView surfaceView;
    private final FrameLayout controlsOverlay;
    private final Runnable hideControlsRunnable = this::hidePaneControls;
    private VirtualDisplay virtualDisplay;
    private long lastMoveSentAt;
    private float touchStartX;
    private float touchStartY;
    private float revealStartY;
    private int voiceGestureGeneration;
    private boolean voiceHoldReady;
    private volatile VoiceState voiceState = VoiceState.IDLE;

    private enum VoiceState {
        IDLE,
        RECORDING,
        RECORDING_LOCKED,
        PAUSING,
        AWAITING_SEND,
        SENDING
    }

    EmbeddedAppPane(Context context, AppEntry entry, String paneName,
                    ShellBridgeClient shellBridgeClient,
                    Runnable changeAction, Runnable settingsAction) {
        super(context);
        this.entry = entry;
        this.paneName = paneName;
        this.shellBridgeClient = shellBridgeClient;
        this.changeAction = changeAction;
        this.settingsAction = settingsAction;

        setBackground(rounded(Color.BLACK, 22));
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(22));
            }
        });

        surfaceView = new SurfaceView(context);
        surfaceView.setZOrderMediaOverlay(false);
        surfaceView.setClickable(true);
        surfaceView.setOnTouchListener(this::forwardTouch);
        surfaceView.getHolder().addCallback(this);
        addView(surfaceView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View revealEdge = new View(context);
        revealEdge.setContentDescription("Показать управление панелью");
        revealEdge.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    revealStartY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getY() - revealStartY >= dp(18)) {
                        showPaneControls();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (event.getY() - revealStartY >= dp(18)) {
                        showPaneControls();
                    }
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
        addView(revealEdge, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14), Gravity.TOP));

        controlsOverlay = new FrameLayout(context);
        controlsOverlay.setAlpha(0f);
        controlsOverlay.setVisibility(GONE);

        TextView back = new TextView(context);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(36);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Назад");
        back.setBackground(rounded(0xCC17212B, 14));
        back.setOnClickListener(view -> {
            if (virtualDisplay != null) {
                if (isMaxPane()) {
                    SteeringAccessibilityService.setMaxChatOpen(false);
                    voiceState = VoiceState.IDLE;
                    voiceGestureGeneration++;
                }
                shellBridgeClient.injectBack(
                        virtualDisplay.getDisplay().getDisplayId());
                hidePaneControls();
            }
        });
        LayoutParams backParams = new LayoutParams(
                dp(50), dp(44), Gravity.TOP | Gravity.START);
        backParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        controlsOverlay.addView(back, backParams);

        TextView change = new TextView(context);
        change.setText("  " + entry.label + "  ·  Изменить  ");
        change.setTextColor(Color.WHITE);
        change.setTextSize(14);
        change.setGravity(Gravity.CENTER);
        change.setBackground(rounded(0xCC17212B, 14));
        change.setOnClickListener(view -> {
            hidePaneControls();
            changeAction.run();
        });
        LayoutParams changeParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44),
                Gravity.TOP | Gravity.END);
        changeParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        controlsOverlay.addView(change, changeParams);

        TextView settings = new TextView(context);
        settings.setText("⚙");
        settings.setTextColor(Color.WHITE);
        settings.setTextSize(22);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Настройки BYD Split");
        settings.setBackground(rounded(0xCC17212B, 14));
        settings.setOnClickListener(view -> {
            hidePaneControls();
            settingsAction.run();
        });
        LayoutParams settingsParams = new LayoutParams(
                dp(50), dp(44), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        settingsParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        controlsOverlay.addView(settings, settingsParams);
        addView(controlsOverlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.TOP));
        if (isMaxPane()) {
            SteeringAccessibilityService.setMaxChatOpen(false);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        post(() -> attachSurfaceOrCreateDisplay(holder));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (width > 0 && height > 0) {
            post(() -> attachSurfaceOrCreateDisplay(holder));
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (virtualDisplay != null) {
            // Activity can temporarily lose its SurfaceView when a camera,
            // settings or another full-screen app covers BYD Split. Keep the
            // VirtualDisplay and its task alive; only detach video output.
            virtualDisplay.setSurface(null);
            Log.i(TAG, paneName + " surface detached from display "
                    + virtualDisplay.getDisplay().getDisplayId());
        }
    }

    void release() {
        voiceGestureGeneration++;
        voiceState = VoiceState.IDLE;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    boolean isMaxPane() {
        return "ru.oneme.app".equals(entry.component.getPackageName());
    }

    void setVoiceRecording(boolean pressed) {
        if (virtualDisplay == null || !isMaxPane()) {
            return;
        }
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        int startX = Math.max(0, getWidth() - dp(24));
        int y = Math.max(0, getHeight() - dp(24));
        int holdX = Math.max(0, startX - dp(16));

        if (pressed && voiceState == VoiceState.IDLE) {
            voiceState = VoiceState.RECORDING;
            voiceHoldReady = false;
            int generation = ++voiceGestureGeneration;
            shellBridgeClient.injectSingleFingerMotion(
                    displayId, MotionEvent.ACTION_DOWN, startX, y);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX - dp(5), y, false), 40);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX - dp(10), y, false), 80);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    holdX, y, true), 120);
            return;
        }

        if (!pressed && voiceState == VoiceState.RECORDING) {
            voiceGestureGeneration++;
            shellBridgeClient.injectSingleFingerMotion(
                    displayId, MotionEvent.ACTION_CANCEL, holdX, y);
            voiceState = voiceHoldReady
                    ? VoiceState.RECORDING_LOCKED : VoiceState.IDLE;
            return;
        }

        if (pressed && voiceState == VoiceState.RECORDING_LOCKED) {
            voiceState = VoiceState.PAUSING;
            shellBridgeClient.injectSingleFingerMotion(
                    displayId, MotionEvent.ACTION_DOWN, getWidth() / 2, y);
            return;
        }

        if (!pressed && voiceState == VoiceState.PAUSING) {
            shellBridgeClient.injectSingleFingerMotion(
                    displayId, MotionEvent.ACTION_UP, getWidth() / 2, y);
            voiceState = VoiceState.AWAITING_SEND;
            return;
        }

        if (pressed && voiceState == VoiceState.AWAITING_SEND) {
            voiceState = VoiceState.SENDING;
            shellBridgeClient.injectSingleFingerMotion(
                    displayId, MotionEvent.ACTION_DOWN, startX, y);
            return;
        }

        if (!pressed && voiceState == VoiceState.SENDING) {
            shellBridgeClient.injectSingleFingerMotion(
                    displayId, MotionEvent.ACTION_UP, startX, y);
            voiceState = VoiceState.IDLE;
        }
    }

    boolean canHandleSteeringPulse(boolean longPress) {
        return longPress
                ? voiceState == VoiceState.IDLE
                : voiceState == VoiceState.RECORDING_LOCKED
                || voiceState == VoiceState.AWAITING_SEND;
    }

    void handleSteeringPulse(boolean longPress) {
        if (virtualDisplay == null || !isMaxPane()
                || !canHandleSteeringPulse(longPress)) {
            return;
        }
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        int startX = Math.max(0, getWidth() - dp(24));
        int y = Math.max(0, getHeight() - dp(24));

        if (longPress) {
            voiceState = VoiceState.RECORDING;
            int generation = ++voiceGestureGeneration;
            int lockY = Math.max(0, y - dp(60));
            shellBridgeClient.injectSingleFingerMotion(
                    displayId, MotionEvent.ACTION_DOWN, startX, y);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX, y, false), 250);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX, y, false), 500);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX, y - dp(15), false), 800);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX, y - dp(30), false), 1100);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX, y - dp(45), false), 1400);
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    startX, lockY, false), 1700);
            postDelayed(() -> finishPulseLock(
                    generation, displayId, startX, lockY), 1950);
            return;
        }

        VoiceState nextState;
        int actionX;
        if (voiceState == VoiceState.RECORDING_LOCKED) {
            voiceState = VoiceState.PAUSING;
            nextState = VoiceState.AWAITING_SEND;
            actionX = getWidth() / 2;
        } else {
            voiceState = VoiceState.SENDING;
            nextState = VoiceState.IDLE;
            actionX = startX;
        }
        shellBridgeClient.injectSingleFingerMotion(
                displayId, MotionEvent.ACTION_DOWN, actionX, y);
        postDelayed(() -> {
            if ((voiceState == VoiceState.PAUSING
                    || voiceState == VoiceState.SENDING)
                    && virtualDisplay != null
                    && virtualDisplay.getDisplay().getDisplayId() == displayId) {
                shellBridgeClient.injectSingleFingerMotion(
                        displayId, MotionEvent.ACTION_UP, actionX, y);
                voiceState = nextState;
            }
        }, 60);
    }

    private void finishPulseLock(int generation, int displayId,
                                 int x, int y) {
        if (generation != voiceGestureGeneration || virtualDisplay == null
                || virtualDisplay.getDisplay().getDisplayId() != displayId
                || voiceState != VoiceState.RECORDING) {
            return;
        }
        shellBridgeClient.injectSingleFingerMotion(
                displayId, MotionEvent.ACTION_UP, x, y);
        voiceState = VoiceState.RECORDING_LOCKED;
    }

    private void sendVoiceGestureStep(int generation, int displayId,
                                      int action, int x, int y,
                                      boolean beginHeartbeat) {
        if (generation != voiceGestureGeneration || virtualDisplay == null
                || virtualDisplay.getDisplay().getDisplayId() != displayId
                || voiceState != VoiceState.RECORDING) {
            return;
        }
        shellBridgeClient.injectSingleFingerMotion(displayId, action, x, y);
        if (beginHeartbeat) {
            voiceHoldReady = true;
            postDelayed(() -> sendVoiceGestureStep(
                    generation, displayId, MotionEvent.ACTION_MOVE,
                    x, y, true), 100);
        }
    }

    private void showPaneControls() {
        removeCallbacks(hideControlsRunnable);
        controlsOverlay.animate().cancel();
        controlsOverlay.setVisibility(VISIBLE);
        controlsOverlay.animate().alpha(1f).setDuration(140).start();
        postDelayed(hideControlsRunnable, 4000);
    }

    private void hidePaneControls() {
        removeCallbacks(hideControlsRunnable);
        controlsOverlay.animate().cancel();
        if (controlsOverlay.getVisibility() != VISIBLE) {
            return;
        }
        controlsOverlay.animate()
                .alpha(0f)
                .setDuration(120)
                .withEndAction(() -> controlsOverlay.setVisibility(GONE))
                .start();
    }

    @SuppressLint("WrongConstant")
    private void createDisplayAndLaunch() {
        if (virtualDisplay != null || getWidth() <= 0 || getHeight() <= 0
                || !surfaceView.getHolder().getSurface().isValid()) {
            return;
        }
        DisplayManager manager = (DisplayManager)
                getContext().getSystemService(Context.DISPLAY_SERVICE);
        int density = getResources().getDisplayMetrics().densityDpi;
        Surface surface = surfaceView.getHolder().getSurface();
        virtualDisplay = manager.createVirtualDisplay(
                "BYD-Split-" + paneName,
                getWidth(),
                getHeight(),
                density,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                        | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH);
        if (virtualDisplay == null) {
            showFailure("Не удалось создать встроенный экран");
            return;
        }

        int displayId = virtualDisplay.getDisplay().getDisplayId();
        shellBridgeClient.launchOnDisplay(
                entry.component.flattenToString(),
                displayId,
                success -> post(() -> {
                    if (success) {
                        Log.i(TAG, entry.component + " -> display " + displayId);
                    } else {
                        showFailure(getResources().getString(
                                R.string.bridge_unavailable));
                    }
                }));
    }

    private void attachSurfaceOrCreateDisplay(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        if (!surface.isValid() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (virtualDisplay == null) {
            createDisplayAndLaunch();
            return;
        }
        virtualDisplay.resize(
                getWidth(),
                getHeight(),
                getResources().getDisplayMetrics().densityDpi);
        virtualDisplay.setSurface(surface);
        Log.i(TAG, paneName + " surface attached to existing display "
                + virtualDisplay.getDisplay().getDisplayId());
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean forwardTouch(View view, MotionEvent event) {
        if (virtualDisplay == null) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hidePaneControls();
                touchStartX = event.getX();
                touchStartY = event.getY();
                lastMoveSentAt = event.getEventTime();
                sendMotion(event);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                sendMotion(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getEventTime() - lastMoveSentAt >= 16) {
                    lastMoveSentAt = event.getEventTime();
                    sendMotion(event);
                }
                return true;
            case MotionEvent.ACTION_UP:
                sendMotion(event);
                if (isMaxPane() && event.getY() >= getHeight() - dp(90)) {
                    voiceGestureGeneration++;
                    voiceState = VoiceState.IDLE;
                }
                if (isMaxPane()
                        && Math.hypot(event.getX() - touchStartX,
                        event.getY() - touchStartY) < dp(12)
                        && event.getY() > dp(100)
                        && event.getY() < getHeight() - dp(90)) {
                    SteeringAccessibilityService.setMaxChatOpen(true);
                }
                view.performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                sendMotion(event);
                return true;
            default:
                return true;
        }
    }

    private void sendMotion(MotionEvent event) {
        shellBridgeClient.injectMotionEvent(
                virtualDisplay.getDisplay().getDisplayId(),
                event);
    }

    private void showFailure(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
