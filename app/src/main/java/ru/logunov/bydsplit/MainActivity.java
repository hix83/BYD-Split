package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MainActivity extends Activity {
    private static final String KEY_DRIVER_APP = AppPreferences.KEY_DRIVER_APP;
    private static final String KEY_FAR_APP = AppPreferences.KEY_FAR_APP;

    private final List<EmbeddedAppPane> activePanes =
            new CopyOnWriteArrayList<>();
    private static WeakReference<MainActivity> currentActivity =
            new WeakReference<>(null);
    private SharedPreferences preferences;
    private AppRepository repository;
    private ShellBridgeClient shellBridgeClient;
    private SteeringEventServer steeringEventServer;
    private AppEntry driverApp;
    private AppEntry farApp;
    private List<AppEntry> driverApps;
    private List<AppEntry> farApps;
    private int driverAppIndex;
    private int farAppIndex;
    private List<AppEntry> availableApps;
    private String pickingKey;
    private int pickingDirection;
    private View pickerOverlay;
    private LinearLayout splitRoot;
    private FrameLayout driverSlot;
    private FrameLayout farSlot;
    private EmbeddedAppPane driverEmbeddedPane;
    private EmbeddedAppPane farEmbeddedPane;
    private volatile boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        currentActivity = new WeakReference<>(this);
        preferences = AppPreferences.get(this);
        applyDebugLaunchOptions(getIntent());
        repository = new AppRepository(this);
        shellBridgeClient = new ShellBridgeClient(this);
        steeringEventServer = new SteeringEventServer();
        steeringEventServer.start();
        driverApps = readCarousel(
                AppPreferences.KEY_DRIVER_APPS, KEY_DRIVER_APP);
        farApps = readCarousel(
                AppPreferences.KEY_FAR_APPS, KEY_FAR_APP);
        driverAppIndex = readIndex(
                AppPreferences.KEY_DRIVER_APP_INDEX, driverApps);
        farAppIndex = readIndex(
                AppPreferences.KEY_FAR_APP_INDEX, farApps);
        updateCurrentEntries();
        if (!AppPreferences.isDemoModeEnabled(this)) {
            shellBridgeClient.bootstrap(true, success -> {
                    if (!success) {
                        android.util.Log.w("BYD_SPLIT",
                            "Не удалось запустить ADB-помощники");
                    }
            });
        }
        render();
        applySystemBarsMode();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (applyDebugLaunchOptions(intent)) {
            pickingKey = null;
            render();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        applySystemBarsMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applySystemBarsMode();
        }
    }

    @SuppressWarnings("deprecation")
    private void applySystemBarsMode() {
        boolean fullscreen = AppPreferences.isFullscreenEnabled(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(!fullscreen);
            WindowInsetsController controller =
                    getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                int systemBars = WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars();
                if (fullscreen) {
                    controller.hide(systemBars);
                    controller.setSystemBarsBehavior(
                            WindowInsetsController
                                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(systemBars);
                }
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                fullscreen
                        ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    static void applyFullscreenModeFromSettings() {
        MainActivity activity = currentActivity.get();
        if (activity == null || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(activity::applySystemBarsMode);
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releasePanes();
        steeringEventServer.close();
        shellBridgeClient.close();
        if (currentActivity.get() == this) {
            currentActivity.clear();
        }
        super.onDestroy();
    }

    static boolean handleSteeringMicrophone(boolean pressed) {
        MainActivity activity = currentActivity.get();
        if (activity == null) {
            return false;
        }
        for (EmbeddedAppPane pane : activity.activePanes) {
            if (pane.isMaxPane()) {
                activity.runOnUiThread(() -> pane.setVoiceRecording(pressed));
                return true;
            }
        }
        return false;
    }

    static boolean isActive() {
        MainActivity activity = currentActivity.get();
        return activity != null && !activity.isFinishing()
                && !activity.isDestroyed();
    }

    static boolean handleSteeringCarousel(boolean leftPane) {
        MainActivity activity = currentActivity.get();
        if (activity == null || activity.isFinishing()
                || activity.isDestroyed() || !activity.resumed) {
            return false;
        }
        activity.runOnUiThread(() ->
                activity.moveCarouselCyclic(leftPane));
        return true;
    }

    static void applyPanelLayoutFromSettings() {
        MainActivity activity = currentActivity.get();
        if (activity != null && !activity.isFinishing()
                && !activity.isDestroyed()) {
            activity.runOnUiThread(activity::applyPanelLayout);
        }
    }

    static boolean handleSteeringPulse(boolean longPress) {
        MainActivity activity = currentActivity.get();
        if (SteeringEventServer.isKeyCaptureActive()) {
            return true;
        }
        boolean maxChatOpen = SteeringAccessibilityService.isMaxChatOpen();
        if (activity == null
                || (longPress && !maxChatOpen)) {
            return false;
        }
        for (EmbeddedAppPane pane : activity.activePanes) {
            if (pane.isMaxPane() && pane.canHandleSteeringPulse(longPress)) {
                activity.runOnUiThread(() ->
                        activity.getWindow().getDecorView().postDelayed(
                                () -> pane.handleSteeringPulse(longPress), 350));
                return true;
            }
        }
        // A short click has no action while MAX is idle, but it still belongs
        // to MAX while a chat is open and must not launch BYD Voice.
        return maxChatOpen && !longPress;
    }

    private void render() {
        releasePanes();

        splitRoot = new LinearLayout(this);
        splitRoot.setOrientation(LinearLayout.HORIZONTAL);
        splitRoot.setPadding(dp(10), dp(10), dp(10), dp(10));
        splitRoot.setBackgroundColor(getColor(R.color.background));

        driverSlot = new FrameLayout(this);
        farSlot = new FrameLayout(this);

        float ratio = AppPreferences.getPanelRatio(this);
        LinearLayout.LayoutParams driverParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, ratio);
        LinearLayout.LayoutParams farParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - ratio);
        splitRoot.addView(driverSlot, driverParams);
        splitRoot.addView(createDivider(), new LinearLayout.LayoutParams(
                dp(16), ViewGroup.LayoutParams.MATCH_PARENT));
        splitRoot.addView(farSlot, farParams);
        setContentView(splitRoot);
        refreshPane(KEY_DRIVER_APP);
        refreshPane(KEY_FAR_APP);
    }

    private void applyPanelLayout() {
        if (driverSlot == null || farSlot == null) {
            return;
        }
        float ratio = AppPreferences.getPanelRatio(this);
        LinearLayout.LayoutParams driverParams =
                (LinearLayout.LayoutParams) driverSlot.getLayoutParams();
        LinearLayout.LayoutParams farParams =
                (LinearLayout.LayoutParams) farSlot.getLayoutParams();
        driverParams.weight = ratio;
        farParams.weight = 1f - ratio;
        driverSlot.setLayoutParams(driverParams);
        farSlot.setLayoutParams(farParams);
    }

    @SuppressWarnings("ClickableViewAccessibility")
    private View createDivider() {
        FrameLayout divider = new FrameLayout(this);
        divider.setContentDescription("Изменить размер областей");
        View handle = new View(this);
        handle.setBackground(roundedBackground(0xCC9EABB8, 4));
        divider.addView(handle, new FrameLayout.LayoutParams(
                dp(4), dp(52), Gravity.CENTER));
        divider.setOnTouchListener((view, event) -> {
            if (splitRoot == null) {
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateRatioFromTouch(event.getRawX(), false);
                    return true;
                case MotionEvent.ACTION_UP:
                    updateRatioFromTouch(event.getRawX(), true);
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
        return divider;
    }

    private void updateRatioFromTouch(float rawX, boolean persist) {
        int[] location = new int[2];
        splitRoot.getLocationOnScreen(location);
        float usableWidth = splitRoot.getWidth()
                - splitRoot.getPaddingLeft() - splitRoot.getPaddingRight()
                - dp(16);
        if (usableWidth <= 0) {
            return;
        }
        float ratio = (rawX - location[0] - splitRoot.getPaddingLeft()
                - dp(8)) / usableWidth;
        ratio = Math.max(AppPreferences.MIN_PANEL_RATIO,
                Math.min(AppPreferences.MAX_PANEL_RATIO, ratio));
        LinearLayout.LayoutParams driverParams =
                (LinearLayout.LayoutParams) driverSlot.getLayoutParams();
        LinearLayout.LayoutParams farParams =
                (LinearLayout.LayoutParams) farSlot.getLayoutParams();
        driverParams.weight = ratio;
        farParams.weight = 1f - ratio;
        driverSlot.setLayoutParams(driverParams);
        farSlot.setLayoutParams(farParams);
        if (persist) {
            AppPreferences.setPanelRatio(this, ratio);
        }
    }

    private View createPane(String title, AppEntry entry, String preferenceKey,
                            boolean driverPane) {
        if (entry == null) {
            return createInlinePicker(title, null, preferenceKey, driverPane);
        }
        int index = driverPane ? driverAppIndex : farAppIndex;
        int count = (driverPane ? driverApps : farApps).size();

        EmbeddedAppPane pane = new EmbeddedAppPane(
                this,
                entry,
                driverPane ? "driver" : "far",
                shellBridgeClient,
                () -> chooseApp(preferenceKey),
                this::openSettings,
                delta -> moveCarousel(preferenceKey, delta),
                delta -> getAdjacentApp(preferenceKey, delta),
                delta -> commitInteractiveCarousel(preferenceKey, delta),
                () -> deleteCurrentApp(preferenceKey),
                index,
                count
        );
        activePanes.add(pane);
        if (driverPane) {
            driverEmbeddedPane = pane;
        } else {
            farEmbeddedPane = pane;
        }
        return pane;
    }

    private void chooseApp(String preferenceKey) {
        showPicker(preferenceKey, 0);
    }

    private void showPicker(String preferenceKey, int direction) {
        hidePicker();
        boolean driverPane = KEY_DRIVER_APP.equals(preferenceKey);
        FrameLayout slot = driverPane ? driverSlot : farSlot;
        if (slot == null) {
            return;
        }
        pickingKey = preferenceKey;
        pickingDirection = direction;
        AppEntry current = driverPane ? driverApp : farApp;
        pickerOverlay = createInlinePicker(
                getString(driverPane
                        ? R.string.driver_zone : R.string.passenger_zone),
                current, preferenceKey, driverPane);
        slot.addView(pickerOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hidePicker() {
        if (pickerOverlay != null && pickerOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) pickerOverlay.getParent()).removeView(pickerOverlay);
        }
        pickerOverlay = null;
        pickingKey = null;
        pickingDirection = 0;
    }

    private View createInlinePicker(String title, AppEntry currentEntry,
                                    String preferenceKey, boolean driverPane) {
        FrameLayout pane = new FrameLayout(this);
        pane.setBackground(roundedBackground(
                driverPane ? Color.rgb(24, 31, 40) : Color.rgb(20, 26, 34), 15));
        pane.setPadding(dp(12), dp(12), dp(12), dp(12));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = new TextView(this);
        titleView.setText(title + " · Выберите приложение");
        titleView.setTextColor(getColor(R.color.text_secondary));
        titleView.setTextSize(15);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, dp(44), 1f));

        TextView settings = new TextView(this);
        settings.setText("⚙");
        settings.setContentDescription("Настройки");
        settings.setTextColor(getColor(R.color.accent));
        settings.setTextSize(24);
        settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(view -> openSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(56), dp(44)));

        if (currentEntry != null) {
            TextView cancel = new TextView(this);
            cancel.setText("Отмена");
            cancel.setTextColor(getColor(R.color.accent));
            cancel.setTextSize(15);
            cancel.setGravity(Gravity.CENTER);
            cancel.setOnClickListener(view -> {
                hidePicker();
            });
            header.addView(cancel, new LinearLayout.LayoutParams(dp(82), dp(44)));
        }
        content.addView(header);

        List<AppEntry> apps = getAvailableApps();
        if (apps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_apps);
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            GridLayout grid = new GridLayout(this);
            int columns = driverPane ? 3 : 6;
            grid.setColumnCount(columns);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            grid.setUseDefaultMargins(false);
            for (AppEntry app : apps) {
                GridLayout.LayoutParams tileParams = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED),
                        GridLayout.spec(GridLayout.UNDEFINED, 1f));
                tileParams.width = 0;
                tileParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                grid.addView(createAppTile(app, preferenceKey), tileParams);
            }

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.addView(grid, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        pane.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return pane;
    }

    private View createAppTile(AppEntry app, String preferenceKey) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(5), dp(8), dp(5), dp(8));
        tile.setBackground(roundedBackground(Color.TRANSPARENT, 14));
        tile.setOnClickListener(view -> selectApp(preferenceKey, app));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView label = new TextView(this);
        label.setText(app.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        labelParams.topMargin = dp(5);
        tile.addView(label, labelParams);
        tile.setMinimumHeight(dp(112));
        return tile;
    }

    private void selectApp(String preferenceKey, AppEntry selected) {
        boolean driver = KEY_DRIVER_APP.equals(preferenceKey);
        List<AppEntry> apps = driver ? driverApps : farApps;
        int currentIndex = driver ? driverAppIndex : farAppIndex;
        int requestedDirection = pickingDirection;
        int existingIndex = indexOf(apps, selected.component);
        int nextIndex;
        if (existingIndex >= 0) {
            nextIndex = existingIndex;
        } else if (apps.isEmpty()) {
            apps.add(selected);
            nextIndex = 0;
        } else if (pickingDirection < 0) {
            apps.add(0, selected);
            nextIndex = 0;
        } else if (pickingDirection > 0) {
            apps.add(selected);
            nextIndex = apps.size() - 1;
        } else {
            apps.set(Math.max(0, Math.min(currentIndex, apps.size() - 1)),
                    selected);
            nextIndex = currentIndex;
        }
        if (driver) {
            driverAppIndex = nextIndex;
        } else {
            farAppIndex = nextIndex;
        }
        updateCurrentEntries();
        saveCarousel(driver);
        hidePicker();
        EmbeddedAppPane pane = driver ? driverEmbeddedPane : farEmbeddedPane;
        AppEntry current = driver ? driverApp : farApp;
        if (pane == null) {
            refreshPane(preferenceKey);
        } else {
            int transitionDirection = requestedDirection != 0
                    ? requestedDirection
                    : Integer.compare(nextIndex, currentIndex);
            pane.switchApp(
                    current, nextIndex, apps.size(), transitionDirection);
        }
    }

    private void moveCarousel(String preferenceKey, int delta) {
        boolean driver = KEY_DRIVER_APP.equals(preferenceKey);
        List<AppEntry> apps = driver ? driverApps : farApps;
        int currentIndex = driver ? driverAppIndex : farAppIndex;
        int nextIndex = currentIndex + delta;
        if (nextIndex < 0 || nextIndex >= apps.size()) {
            showPicker(preferenceKey, delta);
            return;
        }
        if (driver) {
            driverAppIndex = nextIndex;
        } else {
            farAppIndex = nextIndex;
        }
        updateCurrentEntries();
        saveCarousel(driver);
        EmbeddedAppPane pane = driver ? driverEmbeddedPane : farEmbeddedPane;
        if (pane != null) {
            pane.switchApp(
                    apps.get(nextIndex), nextIndex, apps.size(), delta);
        }
    }

    private void moveCarouselCyclic(boolean driverPane) {
        String preferenceKey = driverPane
                ? KEY_DRIVER_APP : KEY_FAR_APP;
        List<AppEntry> apps = driverPane ? driverApps : farApps;
        if (apps == null || apps.size() < 2 || pickerOverlay != null) {
            return;
        }
        int currentIndex = driverPane
                ? driverAppIndex : farAppIndex;
        int delta = driverPane ? -1 : 1;
        int animationDirection = driverPane ? 1 : -1;
        int nextIndex = (currentIndex + delta + apps.size()) % apps.size();
        if (driverPane) {
            driverAppIndex = nextIndex;
        } else {
            farAppIndex = nextIndex;
        }
        updateCurrentEntries();
        saveCarousel(driverPane);
        EmbeddedAppPane pane = driverPane
                ? driverEmbeddedPane : farEmbeddedPane;
        if (pane != null) {
            pane.switchApp(
                    apps.get(nextIndex), nextIndex, apps.size(),
                    animationDirection);
        }
    }

    private AppEntry getAdjacentApp(String preferenceKey, int delta) {
        boolean driver = KEY_DRIVER_APP.equals(preferenceKey);
        List<AppEntry> apps = driver ? driverApps : farApps;
        int currentIndex = driver ? driverAppIndex : farAppIndex;
        int targetIndex = currentIndex + delta;
        return targetIndex < 0 || targetIndex >= apps.size()
                ? null : apps.get(targetIndex);
    }

    private void commitInteractiveCarousel(
            String preferenceKey, int delta) {
        boolean driver = KEY_DRIVER_APP.equals(preferenceKey);
        List<AppEntry> apps = driver ? driverApps : farApps;
        int currentIndex = driver ? driverAppIndex : farAppIndex;
        int nextIndex = currentIndex + delta;
        if (nextIndex < 0 || nextIndex >= apps.size()) {
            return;
        }
        if (driver) {
            driverAppIndex = nextIndex;
        } else {
            farAppIndex = nextIndex;
        }
        updateCurrentEntries();
        saveCarousel(driver);
        EmbeddedAppPane pane = driver ? driverEmbeddedPane : farEmbeddedPane;
        if (pane != null) {
            pane.completeInteractiveSwitch(
                    apps.get(nextIndex), nextIndex, apps.size());
        }
    }

    private void deleteCurrentApp(String preferenceKey) {
        boolean driver = KEY_DRIVER_APP.equals(preferenceKey);
        List<AppEntry> apps = driver ? driverApps : farApps;
        int currentIndex = driver ? driverAppIndex : farAppIndex;
        if (apps.isEmpty() || currentIndex < 0
                || currentIndex >= apps.size()) {
            return;
        }
        AppEntry removed = apps.remove(currentIndex);
        int direction;
        int nextIndex;
        if (currentIndex > 0) {
            nextIndex = currentIndex - 1;
            direction = -1;
        } else if (!apps.isEmpty()) {
            nextIndex = 0;
            direction = 1;
        } else {
            nextIndex = 0;
            direction = 0;
        }
        if (driver) {
            driverAppIndex = nextIndex;
        } else {
            farAppIndex = nextIndex;
        }
        updateCurrentEntries();
        saveCarousel(driver);
        EmbeddedAppPane pane = driver
                ? driverEmbeddedPane : farEmbeddedPane;
        AppEntry next = driver ? driverApp : farApp;
        if (pane == null) {
            refreshPane(preferenceKey);
            return;
        }
        pane.removeAppAndSwitch(
                next, nextIndex, apps.size(), direction,
                removed.component.getPackageName(),
                () -> refreshPane(preferenceKey));
    }

    private void refreshPane(String preferenceKey) {
        boolean driverPane = KEY_DRIVER_APP.equals(preferenceKey);
        FrameLayout slot = driverPane ? driverSlot : farSlot;
        if (slot == null) {
            return;
        }
        releasePane(driverPane);
        slot.removeAllViews();
        View pane = createPane(
                getString(driverPane
                        ? R.string.driver_zone : R.string.passenger_zone),
                driverPane ? driverApp : farApp,
                preferenceKey,
                driverPane);
        slot.addView(pane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void releasePane(boolean driverPane) {
        EmbeddedAppPane pane =
                driverPane ? driverEmbeddedPane : farEmbeddedPane;
        if (pane == null) {
            return;
        }
        activePanes.remove(pane);
        pane.release();
        if (driverPane) {
            driverEmbeddedPane = null;
        } else {
            farEmbeddedPane = null;
        }
    }

    private List<AppEntry> getAvailableApps() {
        if (availableApps == null) {
            availableApps = repository.loadLaunchableApps();
        }
        return availableApps;
    }

    private AppEntry readEntry(String key) {
        String flattened = preferences.getString(key, null);
        ComponentName component = flattened == null
                ? null : ComponentName.unflattenFromString(flattened);
        AppEntry entry = repository.resolve(component);
        if (component != null && entry == null) {
            preferences.edit().remove(key).apply();
        }
        return entry;
    }

    private List<AppEntry> readCarousel(String listKey, String legacyKey) {
        List<AppEntry> result = new ArrayList<>();
        String serialized = preferences.getString(listKey, null);
        if (serialized != null) {
            for (String flattened : serialized.split("\\|")) {
                ComponentName component =
                        ComponentName.unflattenFromString(flattened);
                AppEntry entry = repository.resolve(component);
                if (entry != null && indexOf(result, entry.component) < 0) {
                    result.add(entry);
                }
            }
        }
        if (result.isEmpty()) {
            AppEntry legacy = readEntry(legacyKey);
            if (legacy != null) {
                result.add(legacy);
            }
        }
        return result;
    }

    private int readIndex(String key, List<AppEntry> apps) {
        if (apps.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(
                preferences.getInt(key, 0), apps.size() - 1));
    }

    private int indexOf(List<AppEntry> apps, ComponentName component) {
        for (int index = 0; index < apps.size(); index++) {
            if (apps.get(index).component.equals(component)) {
                return index;
            }
        }
        return -1;
    }

    private void updateCurrentEntries() {
        driverApp = driverApps.isEmpty()
                ? null : driverApps.get(driverAppIndex);
        farApp = farApps.isEmpty()
                ? null : farApps.get(farAppIndex);
    }

    private void saveCarousel(boolean driver) {
        List<AppEntry> apps = driver ? driverApps : farApps;
        int index = driver ? driverAppIndex : farAppIndex;
        StringBuilder serialized = new StringBuilder();
        for (AppEntry app : apps) {
            if (serialized.length() > 0) {
                serialized.append('|');
            }
            serialized.append(app.component.flattenToString());
        }
        String legacyKey = driver ? KEY_DRIVER_APP : KEY_FAR_APP;
        String listKey = driver
                ? AppPreferences.KEY_DRIVER_APPS
                : AppPreferences.KEY_FAR_APPS;
        String indexKey = driver
                ? AppPreferences.KEY_DRIVER_APP_INDEX
                : AppPreferences.KEY_FAR_APP_INDEX;
        SharedPreferences.Editor editor = preferences.edit()
                .putString(listKey, serialized.toString())
                .putInt(indexKey, index);
        if (!apps.isEmpty()) {
            editor.putString(legacyKey,
                    apps.get(index).component.flattenToString());
        } else {
            editor.remove(legacyKey);
        }
        editor.apply();
    }

    private void releasePanes() {
        for (EmbeddedAppPane pane : activePanes) {
            pane.release();
        }
        activePanes.clear();
        driverEmbeddedPane = null;
        farEmbeddedPane = null;
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private boolean applyDebugLaunchOptions(Intent intent) {
        if (!AppPreferences.isDebuggable(this)
                || intent == null
                || !intent.hasExtra("demo_mode")) {
            return false;
        }
        boolean enabled = intent.getBooleanExtra("demo_mode", false);
        AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.KEY_DEMO_MODE, enabled)
                .apply();
        intent.removeExtra("demo_mode");
        return true;
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private android.graphics.drawable.Drawable createPlusIcon() {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            {
                paint.setColor(getColor(R.color.accent));
                paint.setStrokeWidth(dp(5));
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            }
            @Override public void draw(android.graphics.Canvas canvas) {
                float centerX = getBounds().exactCenterX();
                float centerY = getBounds().exactCenterY();
                float half = Math.min(getBounds().width(), getBounds().height()) * 0.22f;
                canvas.drawLine(centerX - half, centerY, centerX + half, centerY, paint);
                canvas.drawLine(centerX, centerY - half, centerX, centerY + half, paint);
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) {
                paint.setColorFilter(filter);
            }
            @Override public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
