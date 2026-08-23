package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MainActivity extends Activity {
    private static final String KEY_DRIVER_APP = AppPreferences.KEY_DRIVER_APP;
    private static final String KEY_FAR_APP = AppPreferences.KEY_FAR_APP;
    private static final String PACKAGE_MAX = "ru.oneme.app";
    private static final String PACKAGE_YANDEX_MUSIC = "ru.yandex.music";
    private static final String PACKAGE_YANDEX_MUSIC_ALT = "com.yandex.music";

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
    private LinearLayout compactPaneContainer;
    private View dividerView;
    private FrameLayout driverSlot;
    private FrameLayout farSlot;
    private EmbeddedAppPane driverEmbeddedPane;
    private EmbeddedAppPane farEmbeddedPane;
    private boolean showingVehicleDashboard;
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
        driverApps = filterCompactApps(driverApps);
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
        splitRoot.setPadding(dp(8), dp(8), dp(8), dp(8));
        splitRoot.setBackgroundColor(getColor(R.color.background));

        driverSlot = new FrameLayout(this);
        farSlot = new FrameLayout(this);

        compactPaneContainer = new LinearLayout(this);
        compactPaneContainer.setOrientation(LinearLayout.VERTICAL);
        compactPaneContainer.setPadding(dp(3), dp(3), dp(3), dp(3));
        compactPaneContainer.setBackground(roundedBackground(
                Color.rgb(13, 22, 36), 18));
        compactPaneContainer.addView(driverSlot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        compactPaneContainer.addView(createCompactDock(),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(86)));

        dividerView = createDivider();
        attachPanelOrder();
        setContentView(splitRoot);
        restoreCompactContent();
        refreshPane(KEY_FAR_APP);
    }

    private void applyPanelLayout() {
        if (splitRoot == null || compactPaneContainer == null
                || farSlot == null || dividerView == null) {
            return;
        }
        attachPanelOrder();
    }

    private void attachPanelOrder() {
        splitRoot.removeAllViews();
        LinearLayout.LayoutParams compactParams =
                new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        LinearLayout.LayoutParams mainParams =
                new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 2f);
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        dp(10), ViewGroup.LayoutParams.MATCH_PARENT);
        if (AppPreferences.isCompactPaneOnLeft(this)) {
            splitRoot.addView(compactPaneContainer, compactParams);
            splitRoot.addView(dividerView, dividerParams);
            splitRoot.addView(farSlot, mainParams);
        } else {
            splitRoot.addView(farSlot, mainParams);
            splitRoot.addView(dividerView, dividerParams);
            splitRoot.addView(compactPaneContainer, compactParams);
        }
    }

    private View createDivider() {
        FrameLayout divider = new FrameLayout(this);
        divider.setContentDescription("Фиксированная граница областей");
        View handle = new View(this);
        handle.setBackground(roundedBackground(0x994C8DFF, 4));
        divider.addView(handle, new FrameLayout.LayoutParams(
                dp(2), dp(62), Gravity.CENTER));
        return divider;
    }

    private View createCompactDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(5), dp(7), dp(5), dp(5));
        dock.setBackground(roundedBackground(Color.rgb(8, 15, 25), 14));

        AppEntry yandex = findFirstInstalled(
                PACKAGE_YANDEX_MUSIC, PACKAGE_YANDEX_MUSIC_ALT);
        AppEntry max = findFirstInstalled(PACKAGE_MAX);
        addCompactButton(dock, "Я.Музыка",
                yandex == null
                        ? QuickIconDrawable.yandexMusic()
                        : yandex.icon,
                () -> activateCompactApp(
                        PACKAGE_YANDEX_MUSIC, PACKAGE_YANDEX_MUSIC_ALT));
        addCompactButton(dock, "MAX",
                max == null ? QuickIconDrawable.max() : max.icon,
                () -> activateCompactApp(PACKAGE_MAX));
        addCompactButton(dock, "Авто", QuickIconDrawable.song(),
                () -> showVehicleDashboard(true));
        addCompactButton(dock, "Камеры", QuickIconDrawable.camera(),
                () -> Toast.makeText(this,
                        "Окно камер будет добавлено отдельным этапом",
                        Toast.LENGTH_SHORT).show());
        return dock;
    }

    private void addCompactButton(LinearLayout dock, String label,
                                  Drawable icon, Runnable action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(3), dp(5), dp(3), dp(3));
        button.setBackground(roundedBackground(Color.rgb(23, 34, 53), 12));
        button.setContentDescription(label);
        button.setOnClickListener(view -> action.run());

        ImageView image = new ImageView(this);
        image.setImageDrawable(icon);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.addView(image, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(1);
        button.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMarginStart(dp(3));
        params.setMarginEnd(dp(3));
        dock.addView(button, params);
    }

    private void restoreCompactContent() {
        String target = AppPreferences.getCompactTarget(this);
        if (!AppPreferences.COMPACT_TARGET_VEHICLE.equals(target)
                && activateCompactAppInternal(false, false, target)) {
            return;
        }
        showVehicleDashboard(false);
    }

    private void showVehicleDashboard(boolean persist) {
        hidePicker();
        releasePane(true);
        driverSlot.removeAllViews();
        driverSlot.addView(new VehicleDashboardView(this),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        showingVehicleDashboard = true;
        if (persist) {
            AppPreferences.setCompactTarget(
                    this, AppPreferences.COMPACT_TARGET_VEHICLE);
        }
    }

    private void activateCompactApp(String... packageNames) {
        if (!activateCompactAppInternal(true, true, packageNames)) {
            Toast.makeText(this,
                    "Приложение не установлено",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean activateCompactAppInternal(
            boolean persist, boolean refreshDock, String... packageNames) {
        AppEntry selected = findFirstInstalled(packageNames);
        if (selected == null) {
            return false;
        }
        int existingIndex = indexOf(driverApps, selected.component);
        if (existingIndex < 0) {
            driverApps.add(selected);
            existingIndex = driverApps.size() - 1;
        }
        driverAppIndex = existingIndex;
        updateCurrentEntries();
        saveCarousel(true);
        showingVehicleDashboard = false;
        refreshPane(KEY_DRIVER_APP);
        if (persist) {
            AppPreferences.setCompactTarget(
                    this, selected.component.getPackageName());
        }
        if (refreshDock) {
            driverSlot.announceForAccessibility(
                    "Открыто " + selected.label);
        }
        return true;
    }

    private AppEntry findFirstInstalled(String... packageNames) {
        for (String packageName : packageNames) {
            for (AppEntry app : getAvailableApps()) {
                if (packageName.equals(app.component.getPackageName())) {
                    return app;
                }
            }
        }
        return null;
    }

    private List<AppEntry> filterCompactApps(List<AppEntry> source) {
        List<AppEntry> result = new ArrayList<>();
        for (AppEntry app : source) {
            String packageName = app.component.getPackageName();
            if (PACKAGE_MAX.equals(packageName)
                    || PACKAGE_YANDEX_MUSIC.equals(packageName)
                    || PACKAGE_YANDEX_MUSIC_ALT.equals(packageName)) {
                result.add(app);
            }
        }
        return result;
    }

    private List<AppEntry> getCompactAvailableApps() {
        return filterCompactApps(getAvailableApps());
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
                () -> beginPickerDrag(preferenceKey),
                distance -> updatePickerDrag(preferenceKey, distance),
                distance -> finishPickerDrag(
                        preferenceKey, distance, false),
                () -> finishPickerDrag(preferenceKey, 0, true),
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

    private void showPicker(String preferenceKey, int direction) {
        createPicker(preferenceKey, direction, false);
    }

    private void beginPickerDrag(String preferenceKey) {
        createPicker(preferenceKey, 0, true);
    }

    private void createPicker(
            String preferenceKey, int direction, boolean hiddenAbove) {
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
        if (hiddenAbove) {
            pickerOverlay.setTranslationY(-Math.max(1, slot.getHeight()));
        }
        slot.addView(pickerOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void updatePickerDrag(String preferenceKey, int distance) {
        if (pickerOverlay == null
                || !preferenceKey.equals(pickingKey)) {
            return;
        }
        ViewGroup parent = (ViewGroup) pickerOverlay.getParent();
        int height = parent == null ? 0 : parent.getHeight();
        if (height <= 0) {
            return;
        }
        pickerOverlay.animate().cancel();
        float visibleDistance = Math.min(height, Math.max(0, distance));
        pickerOverlay.setTranslationY(-height + visibleDistance);
    }

    private void finishPickerDrag(
            String preferenceKey, int distance, boolean cancelled) {
        if (pickerOverlay == null
                || !preferenceKey.equals(pickingKey)) {
            return;
        }
        View overlay = pickerOverlay;
        ViewGroup parent = (ViewGroup) overlay.getParent();
        int height = parent == null ? 0 : parent.getHeight();
        if (height <= 0) {
            if (cancelled) {
                hidePicker();
            }
            return;
        }
        boolean open = !cancelled && distance >= height / 3;
        float targetY = open ? 0f : -height;
        float remaining = Math.abs(targetY - overlay.getTranslationY());
        long duration = Math.max(120L,
                Math.round(260f * remaining / height));
        overlay.animate()
                .translationY(targetY)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (!open && overlay == pickerOverlay) {
                        hidePicker();
                    }
                })
                .start();
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

        List<AppEntry> apps = driverPane
                ? getCompactAvailableApps() : getAvailableApps();
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
            showingVehicleDashboard = false;
            AppPreferences.setCompactTarget(
                    this, selected.component.getPackageName());
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
        if (!AppPreferences.isDebuggable(this) || intent == null) {
            return false;
        }
        boolean changed = false;
        if (intent.hasExtra("demo_mode")) {
            boolean enabled = intent.getBooleanExtra("demo_mode", false);
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_DEMO_MODE, enabled)
                    .apply();
            intent.removeExtra("demo_mode");
            changed = true;
        }
        if (intent.hasExtra("compact_on_left")) {
            AppPreferences.setCompactPaneOnLeft(this,
                    intent.getBooleanExtra("compact_on_left", true));
            intent.removeExtra("compact_on_left");
            changed = true;
        }
        return changed;
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
