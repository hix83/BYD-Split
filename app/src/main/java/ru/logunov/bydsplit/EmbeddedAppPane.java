package ru.logunov.bydsplit;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.HapticFeedbackConstants;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Function;
import java.util.function.IntConsumer;

final class EmbeddedAppPane extends FrameLayout implements SurfaceHolder.Callback {
    private static final String TAG = "BYD_EMBEDDED";
    private static final int PANE_CORNER_RADIUS_DP = 15;
    private static final long DOUBLE_CLICK_WINDOW_MS = 550;
    private static final long DELETE_HOLD_MS = 2000;
    private static final long DELETE_SHRINK_MS = 1500;
    // Hidden in the public SDK, but supported by Android 12's VirtualDisplay.
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;

    private AppEntry entry;
    private final String paneName;
    private final ShellBridgeClient shellBridgeClient;
    private final Runnable changeAction;
    private final Runnable settingsAction;
    private final IntConsumer carouselAction;
    private final Function<Integer, AppEntry> adjacentAppProvider;
    private final IntConsumer interactiveCommitAction;
    private final Runnable deleteAction;
    private final SurfaceView surfaceView;
    private final FrameLayout controlsOverlay;
    private final LinearLayout pageIndicator;
    private final TextView changeView;
    private final LruCache<String, Bitmap> frameCache =
            new LruCache<String, Bitmap>(16 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getAllocationByteCount() / 1024);
                }

                @Override
                protected void entryRemoved(
                        boolean evicted, String key,
                        Bitmap oldValue, Bitmap newValue) {
                    if (oldValue != newValue && !oldValue.isRecycled()) {
                        oldValue.recycle();
                    }
                }
            };
    private final Runnable hideControlsRunnable = this::hidePaneControls;
    private final Runnable deleteHoldRunnable = this::beginDeleteMode;
    private VirtualDisplay virtualDisplay;
    private ImageView transitionSnapshot;
    private Bitmap transitionBitmap;
    private ImageView transitionIncoming;
    private Bitmap transitionIncomingBitmap;
    private int appSwitchGeneration;
    private boolean interactiveGesture;
    private boolean interactiveReady;
    private boolean interactiveReleased;
    private boolean interactiveCommit;
    private int interactiveDirection;
    private float interactiveOffset;
    private String interactiveOriginalComponent;
    private String interactiveTargetComponent;
    private long lastMoveSentAt;
    private float touchStartX;
    private float touchStartY;
    private float revealStartY;
    private boolean deleteHoldCandidate;
    private boolean deleteMode;
    private boolean deleteCardReady;
    private float deleteTouchStartX;
    private float deleteTouchStartY;
    private View deleteBackdrop;
    private ImageView deleteCard;
    private Bitmap deleteBitmap;
    private boolean deletedTaskRemoved;
    private boolean deleteReplacementReady;
    private int indicatorPageIndex;
    private int indicatorPageCount;
    private int voiceGestureGeneration;
    private boolean voiceHoldReady;
    private volatile VoiceState voiceState = VoiceState.IDLE;

    private enum VoiceState {
        IDLE,
        RECORDING,
        RECORDING_LOCKED,
        PAUSING,
        AWAITING_SEND,
        CONFIRMING_SEND,
        DELETING,
        SENDING
    }

    EmbeddedAppPane(Context context, AppEntry entry, String paneName,
                    ShellBridgeClient shellBridgeClient,
                    Runnable changeAction, Runnable settingsAction,
                    IntConsumer carouselAction,
                    Function<Integer, AppEntry> adjacentAppProvider,
                    IntConsumer interactiveCommitAction,
                    Runnable deleteAction,
                    int pageIndex, int pageCount) {
        super(context);
        this.entry = entry;
        this.paneName = paneName;
        this.shellBridgeClient = shellBridgeClient;
        this.changeAction = changeAction;
        this.settingsAction = settingsAction;
        this.carouselAction = carouselAction;
        this.adjacentAppProvider = adjacentAppProvider;
        this.interactiveCommitAction = interactiveCommitAction;
        this.deleteAction = deleteAction;

        setBackground(rounded(Color.BLACK, PANE_CORNER_RADIUS_DP));
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        dp(PANE_CORNER_RADIUS_DP));
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

        addCarouselEdge(true);
        addCarouselEdge(false);

        pageIndicator = new LinearLayout(context);
        pageIndicator.setOrientation(LinearLayout.HORIZONTAL);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setPadding(dp(8), dp(5), dp(8), dp(5));
        pageIndicator.setBackground(rounded(0x8817212B, 12));
        addSwipeListener(pageIndicator, 0);
        LayoutParams indicatorParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(24),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        indicatorParams.bottomMargin = dp(8);
        addView(pageIndicator, indicatorParams);
        updatePageIndicator(pageIndex, pageCount);

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

        changeView = new TextView(context);
        changeView.setText("  " + entry.label + "  ·  Изменить  ");
        changeView.setTextColor(Color.WHITE);
        changeView.setTextSize(14);
        changeView.setGravity(Gravity.CENTER);
        changeView.setBackground(rounded(0xCC17212B, 14));
        changeView.setOnClickListener(view -> {
            hidePaneControls();
            changeAction.run();
        });
        LayoutParams changeParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44),
                Gravity.TOP | Gravity.END);
        changeParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        controlsOverlay.addView(changeView, changeParams);

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

    void switchApp(AppEntry nextEntry, int pageIndex, int pageCount,
                   int direction) {
        boolean wasMax = isMaxPane();
        String previousComponent = entry.component.flattenToString();
        voiceGestureGeneration++;
        voiceState = VoiceState.IDLE;
        if (wasMax) {
            SteeringAccessibilityService.setMaxChatOpen(false);
        }
        entry = nextEntry;
        changeView.setText("  " + entry.label + "  ·  Изменить  ");
        updatePageIndicator(pageIndex, pageCount);
        if (virtualDisplay == null) {
            createDisplayAndLaunch();
            return;
        }
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        if (direction == 0 || getWidth() <= 0) {
            launchSwitchedApp(
                    entry.component.flattenToString(),
                    displayId, 0, null, ++appSwitchGeneration);
            return;
        }
        captureCurrentFrameAndLaunch(
                displayId, direction, previousComponent);
    }

    void completeInteractiveSwitch(
            AppEntry nextEntry, int pageIndex, int pageCount) {
        boolean wasMax = isMaxPane();
        voiceGestureGeneration++;
        voiceState = VoiceState.IDLE;
        if (wasMax) {
            SteeringAccessibilityService.setMaxChatOpen(false);
        }
        entry = nextEntry;
        changeView.setText("  " + entry.label + "  ·  Изменить  ");
        updatePageIndicator(pageIndex, pageCount);
    }

    void removeAppAndSwitch(
            AppEntry nextEntry, int pageIndex, int pageCount,
            int direction, String removedPackage,
            Runnable emptyPaneAction) {
        if (virtualDisplay == null) {
            if (nextEntry != null) {
                switchApp(nextEntry, pageIndex, pageCount, direction);
            } else {
                emptyPaneAction.run();
            }
            return;
        }
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        deletedTaskRemoved = false;
        deleteReplacementReady = false;
        if (nextEntry != null) {
            switchApp(nextEntry, pageIndex, pageCount, direction);
            postDelayed(() -> shellBridgeClient.removeFromDisplay(
                    removedPackage, displayId, success -> post(() -> {
                        if (!success) {
                            Log.w(TAG, "Cannot remove " + removedPackage
                                    + " from display " + displayId);
                        }
                        deletedTaskRemoved = true;
                        maybeRevealDeleteReplacement();
                    })), 360);
            return;
        }
        shellBridgeClient.removeFromDisplay(
                removedPackage, displayId, success -> post(() -> {
                    if (!success) {
                        Log.w(TAG, "Cannot remove " + removedPackage
                                + " from display " + displayId);
                    }
                    deletedTaskRemoved = true;
                    emptyPaneAction.run();
                }));
    }

    private void captureCurrentFrameAndLaunch(
            int displayId, int direction, String previousComponent) {
        clearTransitionSnapshot();
        int generation = ++appSwitchGeneration;
        String component = entry.component.flattenToString();
        Bitmap snapshot = Bitmap.createBitmap(
                Math.max(1, surfaceView.getWidth()),
                Math.max(1, surfaceView.getHeight()),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(
                surfaceView,
                snapshot,
                result -> {
                    if (generation != appSwitchGeneration
                            || virtualDisplay == null
                            || virtualDisplay.getDisplay().getDisplayId()
                            != displayId) {
                        snapshot.recycle();
                        return;
                    }
                    ImageView overlay = null;
                    if (result == PixelCopy.SUCCESS) {
                        cacheFrame(previousComponent, snapshot);
                        overlay = new ImageView(getContext());
                        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
                        overlay.setImageBitmap(snapshot);
                        transitionSnapshot = overlay;
                        transitionBitmap = snapshot;
                        addView(overlay, 1, new LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                    } else {
                        snapshot.recycle();
                    }
                    if (overlay == null) {
                        launchSwitchedApp(
                                component, displayId, direction,
                                null, generation);
                    } else {
                        animateCachedTransitionAndLaunch(
                                component, displayId, direction,
                                overlay, generation);
                    }
                },
                new Handler(Looper.getMainLooper()));
    }

    private void animateCachedTransitionAndLaunch(
            String component, int displayId, int direction,
            ImageView snapshot, int generation) {
        Bitmap incomingBitmap = cachedFrameOrPlaceholder(entry);
        ImageView incoming = new ImageView(getContext());
        incoming.setScaleType(ImageView.ScaleType.FIT_XY);
        incoming.setImageBitmap(incomingBitmap);
        transitionIncoming = incoming;
        transitionIncomingBitmap = incomingBitmap;
        addView(incoming, indexOfChild(snapshot) + 1,
                new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        boolean[] animationFinished = {false};
        boolean[] launchFinished = {false};
        Runnable revealLiveApp = () -> {
            if (!animationFinished[0] || !launchFinished[0]
                    || generation != appSwitchGeneration) {
                return;
            }
            postDelayed(() -> {
                if (generation != appSwitchGeneration) {
                    return;
                }
                clearTransitionSnapshot();
                cacheDisplayedFrame(component);
                deleteReplacementReady = true;
                maybeRevealDeleteReplacement();
            }, 45);
        };

        float distance = getWidth();
        float exitX = direction > 0 ? -distance : distance;
        incoming.setTranslationX(-exitX);
        incoming.animate()
                .translationX(0f)
                .setDuration(230)
                .start();
        snapshot.animate()
                .translationX(exitX)
                .setDuration(230)
                .withEndAction(() -> {
                    animationFinished[0] = true;
                    revealLiveApp.run();
                })
                .start();

        shellBridgeClient.launchOnDisplay(
                component, displayId,
                success -> post(() -> {
                    if (generation != appSwitchGeneration) {
                        return;
                    }
                    if (!success) {
                        clearTransitionSnapshot();
                        showFailure(getResources().getString(
                                R.string.bridge_unavailable));
                        return;
                    }
                    launchFinished[0] = true;
                    revealLiveApp.run();
                }));
    }

    private void launchSwitchedApp(
            String component, int displayId, int direction,
            ImageView snapshot, int generation) {
        shellBridgeClient.launchOnDisplay(
                component, displayId,
                success -> post(() -> {
                    if (generation != appSwitchGeneration) {
                        return;
                    }
                    if (!success) {
                        clearTransitionSnapshot();
                        showFailure(getResources().getString(
                                R.string.bridge_unavailable));
                        return;
                    }
                    if (direction == 0 || snapshot == null) {
                        clearTransitionSnapshot();
                        surfaceView.setTranslationX(0f);
                        surfaceView.setAlpha(1f);
                        if (deleteBackdrop != null) {
                            postDelayed(() -> {
                                deleteReplacementReady = true;
                                maybeRevealDeleteReplacement();
                            }, 140);
                        }
                        return;
                    }
                    postDelayed(() -> captureIncomingFrameAndAnimate(
                            snapshot, direction, generation), 45);
                }));
    }

    private void captureIncomingFrameAndAnimate(
            ImageView snapshot, int direction, int generation) {
        if (generation != appSwitchGeneration
                || snapshot != transitionSnapshot) {
            return;
        }
        Bitmap incomingBitmap = Bitmap.createBitmap(
                Math.max(1, surfaceView.getWidth()),
                Math.max(1, surfaceView.getHeight()),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(
                surfaceView,
                incomingBitmap,
                result -> {
                    if (generation != appSwitchGeneration
                            || snapshot != transitionSnapshot) {
                        incomingBitmap.recycle();
                        return;
                    }
                    if (result != PixelCopy.SUCCESS) {
                        incomingBitmap.recycle();
                        clearTransitionSnapshot();
                        return;
                    }
                    cacheFrame(
                            entry.component.flattenToString(), incomingBitmap);
                    ImageView incoming = new ImageView(getContext());
                    incoming.setScaleType(ImageView.ScaleType.FIT_XY);
                    incoming.setImageBitmap(incomingBitmap);
                    transitionIncoming = incoming;
                    transitionIncomingBitmap = incomingBitmap;
                    addView(incoming, indexOfChild(snapshot) + 1,
                            new LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT));
                    animateCapturedTransition(
                            snapshot, incoming, direction, generation);
                    deleteReplacementReady = true;
                    maybeRevealDeleteReplacement();
                },
                new Handler(Looper.getMainLooper()));
    }

    private void animateCapturedTransition(
            ImageView snapshot, ImageView incoming,
            int direction, int generation) {
        float distance = getWidth();
        float exitX = direction > 0 ? -distance : distance;
        incoming.setTranslationX(-exitX);
        snapshot.animate().cancel();
        incoming.animate().cancel();
        incoming.animate()
                .translationX(0f)
                .setDuration(230)
                .start();
        snapshot.animate()
                .translationX(exitX)
                .setDuration(230)
                .withEndAction(() -> {
                    if (generation == appSwitchGeneration) {
                        clearTransitionSnapshot();
                    }
                })
                .start();
    }

    private void clearTransitionSnapshot() {
        if (transitionSnapshot != null) {
            transitionSnapshot.animate().cancel();
            removeView(transitionSnapshot);
            transitionSnapshot.setImageDrawable(null);
            transitionSnapshot = null;
        }
        if (transitionBitmap != null) {
            transitionBitmap.recycle();
            transitionBitmap = null;
        }
        if (transitionIncoming != null) {
            transitionIncoming.animate().cancel();
            removeView(transitionIncoming);
            transitionIncoming.setImageDrawable(null);
            transitionIncoming = null;
        }
        if (transitionIncomingBitmap != null) {
            transitionIncomingBitmap.recycle();
            transitionIncomingBitmap = null;
        }
        surfaceView.animate().cancel();
        surfaceView.setTranslationX(0f);
        surfaceView.setAlpha(1f);
    }

    private void cacheFrame(String component, Bitmap source) {
        if (component == null || source == null || source.isRecycled()) {
            return;
        }
        Bitmap cached = source.copy(Bitmap.Config.ARGB_8888, false);
        if (cached != null) {
            frameCache.put(component, cached);
        }
    }

    private Bitmap cachedFrameOrPlaceholder(AppEntry app) {
        Bitmap cached = frameCache.get(app.component.flattenToString());
        if (cached != null && !cached.isRecycled()) {
            Bitmap copy = cached.copy(Bitmap.Config.ARGB_8888, false);
            if (copy != null) {
                return copy;
            }
        }
        int width = Math.max(1, surfaceView.getWidth());
        int height = Math.max(1, surfaceView.getHeight());
        Bitmap placeholder = Bitmap.createBitmap(
                width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(placeholder);
        canvas.drawColor(0xFF111923);
        int iconSize = Math.min(dp(96), Math.min(width, height) / 4);
        int iconLeft = (width - iconSize) / 2;
        int iconTop = (height - iconSize) / 2 - dp(28);
        android.graphics.Rect oldBounds = app.icon.copyBounds();
        app.icon.setBounds(
                iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
        app.icon.draw(canvas);
        app.icon.setBounds(oldBounds);
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(20));
        canvas.drawText(
                app.label.toString(),
                width / 2f,
                iconTop + iconSize + dp(38),
                labelPaint);
        return placeholder;
    }

    private void cacheDisplayedFrame(String component) {
        if (virtualDisplay == null
                || !surfaceView.getHolder().getSurface().isValid()) {
            return;
        }
        Bitmap displayed = Bitmap.createBitmap(
                Math.max(1, surfaceView.getWidth()),
                Math.max(1, surfaceView.getHeight()),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(
                surfaceView,
                displayed,
                result -> {
                    if (result == PixelCopy.SUCCESS) {
                        cacheFrame(component, displayed);
                    }
                    displayed.recycle();
                },
                new Handler(Looper.getMainLooper()));
    }

    private void beginInteractiveEdgeDrag(int direction) {
        AppEntry target = adjacentAppProvider.apply(direction);
        if (target == null || virtualDisplay == null || getWidth() <= 0) {
            return;
        }
        clearTransitionSnapshot();
        interactiveGesture = true;
        interactiveReady = false;
        interactiveReleased = false;
        interactiveCommit = false;
        interactiveDirection = direction;
        interactiveOffset = 0f;
        interactiveOriginalComponent = entry.component.flattenToString();
        interactiveTargetComponent = target.component.flattenToString();
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        int generation = ++appSwitchGeneration;
        Bitmap currentBitmap = Bitmap.createBitmap(
                Math.max(1, surfaceView.getWidth()),
                Math.max(1, surfaceView.getHeight()),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(
                surfaceView,
                currentBitmap,
                result -> {
                    if (!isCurrentInteractiveGesture(
                            generation, displayId, direction)) {
                        currentBitmap.recycle();
                        return;
                    }
                    if (result != PixelCopy.SUCCESS) {
                        currentBitmap.recycle();
                        resetInteractiveState();
                        return;
                    }
                    cacheFrame(interactiveOriginalComponent, currentBitmap);
                    ImageView current = new ImageView(getContext());
                    current.setScaleType(ImageView.ScaleType.FIT_XY);
                    current.setImageBitmap(currentBitmap);
                    transitionSnapshot = current;
                    transitionBitmap = currentBitmap;
                    addView(current, 1, new LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                    Bitmap incomingBitmap = cachedFrameOrPlaceholder(target);
                    ImageView incoming = new ImageView(getContext());
                    incoming.setScaleType(ImageView.ScaleType.FIT_XY);
                    incoming.setImageBitmap(incomingBitmap);
                    transitionIncoming = incoming;
                    transitionIncomingBitmap = incomingBitmap;
                    addView(incoming, indexOfChild(current) + 1,
                            new LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT));
                    interactiveReady = true;
                    applyInteractiveOffset();
                    if (interactiveReleased) {
                        settleInteractiveEdgeDrag();
                    }
                },
                new Handler(Looper.getMainLooper()));
    }

    private boolean isCurrentInteractiveGesture(
            int generation, int displayId, int direction) {
        return generation == appSwitchGeneration
                && interactiveGesture
                && interactiveDirection == direction
                && virtualDisplay != null
                && virtualDisplay.getDisplay().getDisplayId() == displayId;
    }

    private void updateInteractiveOffset(float rawOffset) {
        float width = Math.max(1, getWidth());
        interactiveOffset = interactiveDirection > 0
                ? Math.max(-width, Math.min(0f, rawOffset))
                : Math.max(0f, Math.min(width, rawOffset));
        if (interactiveReady) {
            applyInteractiveOffset();
        }
    }

    private void applyInteractiveOffset() {
        if (transitionSnapshot == null || transitionIncoming == null) {
            return;
        }
        float width = getWidth();
        transitionSnapshot.setTranslationX(interactiveOffset);
        transitionIncoming.setTranslationX(
                interactiveOffset + interactiveDirection * width);
    }

    private void finishInteractiveEdgeDrag() {
        interactiveReleased = true;
        interactiveCommit = Math.abs(interactiveOffset)
                >= getWidth() * 0.45f;
        if (interactiveReady) {
            settleInteractiveEdgeDrag();
        }
    }

    private void settleInteractiveEdgeDrag() {
        if (!interactiveGesture || !interactiveReady
                || transitionSnapshot == null
                || transitionIncoming == null) {
            return;
        }
        int generation = appSwitchGeneration;
        if (interactiveCommit) {
            float targetOffset = interactiveDirection > 0
                    ? -getWidth() : getWidth();
            long duration = settleDuration(
                    targetOffset - interactiveOffset);
            transitionIncoming.animate()
                    .translationX(0f)
                    .setDuration(duration)
                    .start();
            transitionSnapshot.animate()
                    .translationX(targetOffset)
                    .setDuration(duration)
                    .withEndAction(() -> {
                        if (generation != appSwitchGeneration) {
                            return;
                        }
                        int committedDirection = interactiveDirection;
                        interactiveCommitAction.accept(committedDirection);
                        launchCommittedInteractiveTarget(generation);
                    })
                    .start();
            return;
        }

        long duration = settleDuration(-interactiveOffset);
        transitionIncoming.animate()
                .translationX(interactiveDirection * getWidth())
                .setDuration(duration)
                .start();
        transitionSnapshot.animate()
                .translationX(0f)
                .setDuration(duration)
                .withEndAction(() -> {
                    if (generation == appSwitchGeneration) {
                        clearTransitionSnapshot();
                        resetInteractiveState();
                    }
                })
                .start();
    }

    private long settleDuration(float remainingDistance) {
        float fraction = Math.min(
                1f, Math.abs(remainingDistance) / Math.max(1, getWidth()));
        return Math.max(90L, Math.round(230f * fraction));
    }

    private void launchCommittedInteractiveTarget(int generation) {
        if (generation != appSwitchGeneration || virtualDisplay == null) {
            return;
        }
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        String component = interactiveTargetComponent;
        shellBridgeClient.launchOnDisplay(
                component,
                displayId,
                success -> post(() -> {
                    if (generation != appSwitchGeneration) {
                        return;
                    }
                    if (!success) {
                        clearTransitionSnapshot();
                        resetInteractiveState();
                        showFailure(getResources().getString(
                                R.string.bridge_unavailable));
                        return;
                    }
                    postDelayed(() -> cacheCommittedFrameAndFinish(
                            component, displayId, generation), 45);
                }));
    }

    private void cacheCommittedFrameAndFinish(
            String component, int displayId, int generation) {
        if (generation != appSwitchGeneration || virtualDisplay == null
                || virtualDisplay.getDisplay().getDisplayId() != displayId) {
            return;
        }
        Bitmap displayed = Bitmap.createBitmap(
                Math.max(1, surfaceView.getWidth()),
                Math.max(1, surfaceView.getHeight()),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(
                surfaceView,
                displayed,
                result -> {
                    if (generation != appSwitchGeneration) {
                        displayed.recycle();
                        return;
                    }
                    if (result == PixelCopy.SUCCESS) {
                        cacheFrame(component, displayed);
                    }
                    displayed.recycle();
                    clearTransitionSnapshot();
                    resetInteractiveState();
                },
                new Handler(Looper.getMainLooper()));
    }

    private void resetInteractiveState() {
        interactiveGesture = false;
        interactiveReady = false;
        interactiveReleased = false;
        interactiveCommit = false;
        interactiveDirection = 0;
        interactiveOffset = 0f;
        interactiveOriginalComponent = null;
        interactiveTargetComponent = null;
    }

    private void scheduleDeleteHold(MotionEvent event) {
        deleteHoldCandidate = isDeleteHoldArea(event.getX(), event.getY());
        deleteTouchStartX = event.getX();
        deleteTouchStartY = event.getY();
        if (!deleteHoldCandidate) {
            return;
        }
        postDelayed(deleteHoldRunnable, DELETE_HOLD_MS);
    }

    private boolean isDeleteHoldArea(float x, float y) {
        return x >= getWidth() * 0.25f
                && x <= getWidth() * 0.75f
                && y >= getHeight() * 0.20f
                && y <= getHeight() * 0.72f;
    }

    private void cancelDeleteHold() {
        deleteHoldCandidate = false;
        removeCallbacks(deleteHoldRunnable);
    }

    private void beginDeleteMode() {
        if (!deleteHoldCandidate || deleteMode || virtualDisplay == null
                || interactiveGesture
                || !surfaceView.getHolder().getSurface().isValid()) {
            return;
        }
        deleteHoldCandidate = false;
        deleteMode = true;
        deleteCardReady = false;
        hidePaneControls();
        voiceGestureGeneration++;
        voiceState = VoiceState.IDLE;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        showDeleteIndicator();
        deleteBackdrop = new View(getContext());
        deleteBackdrop.setBackgroundColor(Color.BLACK);
        deleteBackdrop.setAlpha(0f);
        addView(deleteBackdrop, indexOfChild(surfaceView) + 1,
                new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        deleteBackdrop.animate()
                .alpha(1f)
                .setDuration(180)
                .start();

        Bitmap snapshot = Bitmap.createBitmap(
                Math.max(1, surfaceView.getWidth()),
                Math.max(1, surfaceView.getHeight()),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(
                surfaceView,
                snapshot,
                result -> {
                    if (!deleteMode || result != PixelCopy.SUCCESS) {
                        snapshot.recycle();
                        if (deleteMode) {
                            cancelDeleteMode(false);
                        }
                        return;
                    }
                    deleteBitmap = snapshot;
                    ImageView card = new ImageView(getContext());
                    card.setScaleType(ImageView.ScaleType.FIT_XY);
                    card.setImageBitmap(snapshot);
                    card.setBackground(rounded(Color.BLACK,
                            PANE_CORNER_RADIUS_DP));
                    card.setClipToOutline(true);
                    card.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setRoundRect(
                                    0, 0, view.getWidth(), view.getHeight(),
                                    dp(PANE_CORNER_RADIUS_DP));
                        }
                    });
                    deleteCard = card;
                    addView(card, new LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                    card.animate()
                            .scaleX(0.30f)
                            .scaleY(0.30f)
                            .alpha(0.96f)
                            .setDuration(DELETE_SHRINK_MS)
                            .withEndAction(() -> {
                                if (deleteMode && card == deleteCard) {
                                    deleteCardReady = true;
                                    pageIndicator.animate()
                                            .scaleX(1.12f)
                                            .scaleY(1.12f)
                                            .setDuration(120)
                                            .withEndAction(() ->
                                                    pageIndicator.animate()
                                                            .scaleX(1f)
                                                            .scaleY(1f)
                                                            .setDuration(120)
                                                            .start())
                                            .start();
                                }
                            })
                            .start();
                },
                new Handler(Looper.getMainLooper()));
    }

    private void updateDeleteCard(float x, float y) {
        if (!deleteMode || !deleteCardReady || deleteCard == null) {
            return;
        }
        deleteCard.animate().cancel();
        deleteCard.setTranslationX(x - deleteTouchStartX);
        deleteCard.setTranslationY(y - deleteTouchStartY);
        boolean overTrash = isOverDeleteIndicator(x, y);
        pageIndicator.setScaleX(overTrash ? 1.16f : 1f);
        pageIndicator.setScaleY(overTrash ? 1.16f : 1f);
    }

    private boolean isOverDeleteIndicator(float x, float y) {
        return x >= getWidth() * 0.34f
                && x <= getWidth() * 0.66f
                && y >= getHeight() - dp(105);
    }

    private void finishDeleteDrag(float x, float y) {
        if (!deleteMode) {
            return;
        }
        if (deleteCardReady && deleteCard != null
                && isOverDeleteIndicator(x, y)) {
            ImageView card = deleteCard;
            deleteCardReady = false;
            card.animate().cancel();
            card.animate()
                    .translationX(0f)
                    .translationY(getHeight() * 0.48f)
                    .scaleX(0.05f)
                    .scaleY(0.05f)
                    .alpha(0f)
                    .setDuration(260)
                    .withEndAction(() -> {
                        clearDeleteCard(false);
                        deleteMode = false;
                        restorePageIndicator();
                        deleteAction.run();
                    })
                    .start();
            return;
        }
        cancelDeleteMode(true);
    }

    private void cancelDeleteMode(boolean animateBack) {
        deleteHoldCandidate = false;
        removeCallbacks(deleteHoldRunnable);
        if (deleteCard != null && animateBack) {
            ImageView card = deleteCard;
            deleteCardReady = false;
            card.animate().cancel();
            card.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(240)
                    .withEndAction(() -> {
                        clearDeleteCard(true);
                        deleteMode = false;
                        restorePageIndicator();
                    })
                    .start();
            return;
        }
        clearDeleteCard(true);
        deleteMode = false;
        deleteCardReady = false;
        restorePageIndicator();
    }

    private void clearDeleteCard(boolean clearBackdrop) {
        if (deleteCard != null) {
            deleteCard.animate().cancel();
            removeView(deleteCard);
            deleteCard.setImageDrawable(null);
            deleteCard = null;
        }
        if (deleteBitmap != null && !deleteBitmap.isRecycled()) {
            deleteBitmap.recycle();
        }
        deleteBitmap = null;
        if (clearBackdrop && deleteBackdrop != null) {
            deleteBackdrop.animate().cancel();
            removeView(deleteBackdrop);
            deleteBackdrop = null;
        }
    }

    private void maybeRevealDeleteReplacement() {
        if (!deletedTaskRemoved || !deleteReplacementReady
                || deleteBackdrop == null) {
            return;
        }
        View backdrop = deleteBackdrop;
        backdrop.animate().cancel();
        backdrop.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> {
                    if (deleteBackdrop == backdrop) {
                        removeView(backdrop);
                        deleteBackdrop = null;
                    }
                    deletedTaskRemoved = false;
                    deleteReplacementReady = false;
                })
                .start();
    }

    private void showDeleteIndicator() {
        pageIndicator.removeAllViews();
        TextView trash = new TextView(getContext());
        trash.setText("🗑");
        trash.setTextSize(25);
        trash.setGravity(Gravity.CENTER);
        trash.setTextColor(Color.WHITE);
        pageIndicator.setBackground(rounded(0xDDC9364B, 18));
        pageIndicator.addView(trash,
                new LinearLayout.LayoutParams(dp(52), dp(42)));
        ViewGroup.LayoutParams rawParams = pageIndicator.getLayoutParams();
        rawParams.height = dp(48);
        pageIndicator.setLayoutParams(rawParams);
        pageIndicator.setContentDescription(
                "Перетащите приложение сюда, чтобы закрыть");
    }

    private void restorePageIndicator() {
        pageIndicator.animate().cancel();
        pageIndicator.setScaleX(1f);
        pageIndicator.setScaleY(1f);
        pageIndicator.setBackground(rounded(0x8817212B, 12));
        ViewGroup.LayoutParams rawParams = pageIndicator.getLayoutParams();
        rawParams.height = dp(24);
        pageIndicator.setLayoutParams(rawParams);
        updatePageIndicator(indicatorPageIndex, indicatorPageCount);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addCarouselEdge(boolean start) {
        View edge = new View(getContext());
        edge.setContentDescription(start
                ? "Предыдущее приложение" : "Следующее приложение");
        addEdgeDragListener(edge, start ? -1 : 1);
        LayoutParams params = new LayoutParams(
                dp(22), ViewGroup.LayoutParams.MATCH_PARENT,
                (start ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL);
        params.topMargin = dp(16);
        params.bottomMargin = dp(36);
        addView(edge, params);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addEdgeDragListener(View view, int direction) {
        final float[] start = new float[2];
        view.setOnTouchListener((target, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = event.getRawX();
                    start[1] = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - start[0];
                    float dy = event.getRawY() - start[1];
                    boolean expectedDirection = direction > 0
                            ? dx < 0 : dx > 0;
                    if (!interactiveGesture
                            && expectedDirection
                            && Math.abs(dx) >= dp(8)
                            && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        beginInteractiveEdgeDrag(direction);
                    }
                    if (interactiveGesture
                            && interactiveDirection == direction) {
                        updateInteractiveOffset(dx);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float finalDx = event.getRawX() - start[0];
                    float finalDy = event.getRawY() - start[1];
                    if (interactiveGesture
                            && interactiveDirection == direction) {
                        updateInteractiveOffset(finalDx);
                        finishInteractiveEdgeDrag();
                    } else if (Math.abs(finalDx) >= dp(36)
                            && Math.abs(finalDx)
                            > Math.abs(finalDy) * 1.25f) {
                        int delta = finalDx < 0 ? 1 : -1;
                        if (delta == direction) {
                            carouselAction.accept(delta);
                        }
                    }
                    target.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (interactiveGesture
                            && interactiveDirection == direction) {
                        interactiveCommit = false;
                        interactiveReleased = true;
                        settleInteractiveEdgeDrag();
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addSwipeListener(View view, int edgeDirection) {
        final float[] start = new float[2];
        view.setOnTouchListener((target, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = event.getRawX();
                    start[1] = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getRawX() - start[0];
                    float dy = event.getRawY() - start[1];
                    if (Math.abs(dx) >= dp(36)
                            && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        int delta = dx < 0 ? 1 : -1;
                        if (!interactiveGesture
                                && (edgeDirection == 0
                                || delta == edgeDirection)) {
                            carouselAction.accept(delta);
                        }
                    }
                    target.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
    }

    private void updatePageIndicator(int pageIndex, int pageCount) {
        indicatorPageIndex = pageIndex;
        indicatorPageCount = pageCount;
        pageIndicator.removeAllViews();
        int safeCount = Math.max(1, pageCount);
        int safeIndex = Math.max(0, Math.min(pageIndex, safeCount - 1));
        for (int index = 0; index < safeCount; index++) {
            View dot = new View(getContext());
            dot.setBackground(rounded(
                    index == safeIndex
                            ? getContext().getColor(R.color.accent)
                            : 0x99FFFFFF,
                    4));
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(7), dp(7));
            params.setMargins(dp(3), 0, dp(3), 0);
            pageIndicator.addView(dot, params);
        }
        pageIndicator.setContentDescription(
                "Приложение " + (safeIndex + 1) + " из " + safeCount);
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
        appSwitchGeneration++;
        voiceState = VoiceState.IDLE;
        clearTransitionSnapshot();
        cancelDeleteMode(false);
        resetInteractiveState();
        frameCache.evictAll();
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    boolean isMaxPane() {
        return "ru.oneme.app".equals(entry.component.getPackageName());
    }

    void setVoiceRecording(boolean pressed) {
        if (virtualDisplay == null || !isMaxPane()
                || interactiveGesture) {
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
        return !interactiveGesture && !deleteMode && (longPress
                ? voiceState == VoiceState.IDLE
                : voiceState == VoiceState.RECORDING_LOCKED
                || voiceState == VoiceState.AWAITING_SEND
                || voiceState == VoiceState.CONFIRMING_SEND);
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

        if (voiceState == VoiceState.RECORDING_LOCKED) {
            voiceState = VoiceState.PAUSING;
            tapVoiceAction(
                    displayId,
                    getWidth() / 2,
                    y,
                    VoiceState.PAUSING,
                    VoiceState.AWAITING_SEND);
            return;
        }
        if (voiceState == VoiceState.AWAITING_SEND) {
            voiceState = VoiceState.CONFIRMING_SEND;
            int generation = ++voiceGestureGeneration;
            postDelayed(() -> {
                if (generation != voiceGestureGeneration
                        || voiceState != VoiceState.CONFIRMING_SEND
                        || virtualDisplay == null
                        || virtualDisplay.getDisplay().getDisplayId()
                        != displayId) {
                    return;
                }
                voiceState = VoiceState.SENDING;
                tapVoiceAction(
                        displayId,
                        startX,
                        y,
                        VoiceState.SENDING,
                        VoiceState.IDLE);
            }, DOUBLE_CLICK_WINDOW_MS);
            return;
        }
        if (voiceState == VoiceState.CONFIRMING_SEND) {
            voiceGestureGeneration++;
            voiceState = VoiceState.DELETING;
            tapVoiceAction(
                    displayId,
                    dp(24),
                    y,
                    VoiceState.DELETING,
                    VoiceState.IDLE);
        }
    }

    private void tapVoiceAction(
            int displayId, int x, int y,
            VoiceState expectedState, VoiceState nextState) {
        shellBridgeClient.injectSingleFingerMotion(
                displayId, MotionEvent.ACTION_DOWN, x, y);
        postDelayed(() -> {
            if (voiceState == expectedState
                    && virtualDisplay != null
                    && virtualDisplay.getDisplay().getDisplayId() == displayId) {
                shellBridgeClient.injectSingleFingerMotion(
                        displayId, MotionEvent.ACTION_UP, x, y);
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
        String launchedComponent = entry.component.flattenToString();
        shellBridgeClient.launchOnDisplay(
                launchedComponent,
                displayId,
                success -> post(() -> {
                    if (success) {
                        Log.i(TAG, entry.component + " -> display " + displayId);
                        postDelayed(() ->
                                cacheDisplayedFrame(launchedComponent), 80);
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
                scheduleDeleteHold(event);
                if (!deleteHoldCandidate) {
                    sendMotion(event);
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                if (deleteHoldCandidate) {
                    sendBufferedDeleteCandidateDown();
                }
                cancelDeleteHold();
                if (deleteMode) {
                    cancelDeleteMode(true);
                    return true;
                }
                sendMotion(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (deleteMode) {
                    updateDeleteCard(event.getX(), event.getY());
                    return true;
                }
                if (deleteHoldCandidate
                        && Math.hypot(event.getX() - deleteTouchStartX,
                        event.getY() - deleteTouchStartY) > dp(12)) {
                    sendBufferedDeleteCandidateDown();
                    cancelDeleteHold();
                }
                if (deleteHoldCandidate) {
                    return true;
                }
                if (event.getEventTime() - lastMoveSentAt >= 16) {
                    lastMoveSentAt = event.getEventTime();
                    sendMotion(event);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (deleteMode) {
                    finishDeleteDrag(event.getX(), event.getY());
                    view.performClick();
                    return true;
                }
                if (deleteHoldCandidate) {
                    sendBufferedDeleteCandidateDown();
                }
                cancelDeleteHold();
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
                if (deleteMode) {
                    cancelDeleteMode(true);
                    return true;
                }
                if (deleteHoldCandidate) {
                    cancelDeleteHold();
                    return true;
                }
                cancelDeleteHold();
                sendMotion(event);
                return true;
            default:
                return true;
        }
    }

    private void sendBufferedDeleteCandidateDown() {
        if (virtualDisplay == null) {
            return;
        }
        shellBridgeClient.injectSingleFingerMotion(
                virtualDisplay.getDisplay().getDisplayId(),
                MotionEvent.ACTION_DOWN,
                Math.round(deleteTouchStartX),
                Math.round(deleteTouchStartY));
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
