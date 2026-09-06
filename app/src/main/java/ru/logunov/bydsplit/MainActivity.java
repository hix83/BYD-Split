package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
    private static final class CompactButtonVisual {
        final ImageView tile;
        final View indicator;

        CompactButtonVisual(ImageView tile, View indicator) {
            this.tile = tile;
            this.indicator = indicator;
        }
    }

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
    private BatteryDetailsView batteryDetailsView;
    private View batteryDisplacedApp;
    private boolean batteryClosing;
    private EmbeddedAppPane driverEmbeddedPane;
    private EmbeddedAppPane farEmbeddedPane;
    private boolean showingVehicleDashboard;
    private boolean settingsVisible;
    private VehicleDashboardView vehicleDashboardView;
    private VehicleTelemetryController vehicleTelemetryController;
    private AppEntry quickMusicApp;
    private AppEntry quickMaxApp;
    private View compactAutoButton;
    private View compactCameraButton;
    private View compactMusicButton;
    private View compactMaxButton;
    private View compactSettingsButton;
    private volatile boolean resumed;
    private ParkingCameraAutomation cameraAutomation;

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
        cameraAutomation = new ParkingCameraAutomation(this);
        vehicleTelemetryController = new VehicleTelemetryController(
                this, snapshot -> {
                    if (showingVehicleDashboard
                            && vehicleDashboardView != null) {
                        vehicleDashboardView.setTelemetry(snapshot);
                    }
                });
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
        if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
                && intent.getBooleanExtra("debug_max_call_route",false)) {
            intent.removeExtra("debug_max_call_route");
            MaxCallRouter.onCallActivity(this);
        }
        if (applyDebugLaunchOptions(intent)) {
            pickingKey = null;
            render();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (batteryDetailsView != null) batteryDetailsView.setActive(true);
        settingsVisible = false;
        applySystemBarsMode();
        updateCompactDockSelection();
        if (showingVehicleDashboard) {
            vehicleTelemetryController.start(
                    AppPreferences.isDemoModeEnabled(this));
        }
        cameraAutomation.start();
        boolean locationNeeded = showingVehicleDashboard
                || AutomationStore.hasCameraRules(this);
        if (locationNeeded && checkSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 341);
        } else {
            cameraAutomation.start();
            vehicleTelemetryController.startLocationIfPermitted();
        }
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
        if (batteryDetailsView != null) batteryDetailsView.setActive(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cameraAutomation.stop();
        ParkingCameraOverlay.close(this);
        releasePanes();
        steeringEventServer.close();
        shellBridgeClient.close();
        vehicleTelemetryController.close();
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

    static int prepareMaxCallPane() {
        MainActivity activity=currentActivity.get();
        if(activity==null||activity.isFinishing()||activity.isDestroyed())return -1;
        if(activity.showingVehicleDashboard||activity.driverEmbeddedPane==null||!activity.driverEmbeddedPane.isMaxPane()) {
            activity.hidePicker();
            activity.activateCompactAppInternal(false,true,PACKAGE_MAX);
        }
        return activity.driverEmbeddedPane==null?-1:activity.driverEmbeddedPane.embeddedDisplayId();
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

    static void onParkingCameraVisibilityChanged(boolean visible) {
        MainActivity activity = currentActivity.get();
        if (activity != null && !activity.isFinishing()
                && !activity.isDestroyed()) {
            activity.runOnUiThread(activity::updateCompactDockSelection);
        }
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
        compactPaneContainer.setPadding(0, 0, 0, 0);
        compactPaneContainer.setBackgroundColor(Color.TRANSPARENT);
        compactPaneContainer.addView(driverSlot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        dockParams.topMargin = dp(8);
        compactPaneContainer.addView(createCompactDock(), dockParams);

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
        handle.setBackground(roundedBackground(0x99DAE1EB, 4));
        divider.addView(handle, new FrameLayout.LayoutParams(
                dp(3), dp(72), Gravity.CENTER));
        return divider;
    }

    private View createCompactDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(0, dp(5), 0, dp(4));
        dock.setBackground(roundedBackground(0xFF1D252E, 12));

        quickMusicApp = resolveQuickApp(AppPreferences.KEY_QUICK_MUSIC_APP,
                PACKAGE_YANDEX_MUSIC, PACKAGE_YANDEX_MUSIC_ALT);
        quickMaxApp = resolveQuickApp(AppPreferences.KEY_QUICK_MAX_APP, PACKAGE_MAX);
        compactAutoButton = addCompactButton(dock, "Авто",
                getDrawable(R.drawable.dock_ui7_song),
                () -> showVehicleDashboard(true), null);
        compactCameraButton = addCompactButton(dock, "Камеры",
                getDrawable(R.drawable.dock_ui7_camera),
                this::showParkingCamera, null);
        compactMusicButton = addCompactButton(dock, "Музыка",
                getDrawable(R.drawable.dock_ui7_music),
                () -> activateQuickApp(quickMusicApp),
                () -> chooseQuickApp(AppPreferences.KEY_QUICK_MUSIC_APP));
        compactMaxButton = addCompactButton(dock, "Сообщения",
                getDrawable(R.drawable.dock_ui7_message),
                () -> activateQuickApp(quickMaxApp),
                () -> chooseQuickApp(AppPreferences.KEY_QUICK_MAX_APP));
        compactSettingsButton = addCompactButton(dock, "Настройки",
                getDrawable(R.drawable.dock_ui7_settings),
                this::openSettings, null);
        updateCompactDockSelection();
        return dock;
    }

    private View addCompactButton(LinearLayout dock, String label,
                                  Drawable icon, Runnable action,
                                  Runnable longAction) {
        FrameLayout button = new FrameLayout(this);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(label);
        button.setOnClickListener(view -> action.run());
        if (longAction != null) {
            button.setOnLongClickListener(view -> {
                longAction.run();
                return true;
            });
        }

        ImageView image = new ImageView(this);
        image.setImageDrawable(icon);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(40), dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        iconParams.topMargin = dp(7);
        button.addView(image, iconParams);
        TextView caption = new TextView(this);
        caption.setText("Авто".equals(label) ? "BYD SONG" : label.toUpperCase(java.util.Locale.ROOT));
        caption.setTextColor(getColor(R.color.text_primary));
        caption.setTextSize(11);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        captionParams.bottomMargin = dp(9);
        button.addView(caption, captionParams);

        View indicator = new View(this);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                dp(24), dp(2), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        indicatorParams.bottomMargin = dp(4);
        button.addView(indicator, indicatorParams);
        button.setTag(new CompactButtonVisual(image, indicator));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (dock.getChildCount() > 0) {
            params.setMarginStart(dp(6));
        }
        dock.addView(button, params);
        return button;
    }

    private void updateCompactDockSelection() {
        boolean cameraVisible = ParkingCameraOverlay.isShowing();
        boolean contentVisible = !cameraVisible && !settingsVisible;
        styleCompactButton(compactAutoButton,
                contentVisible && showingVehicleDashboard);
        styleCompactButton(compactCameraButton, cameraVisible);
        styleCompactButton(compactMusicButton,
                contentVisible && !showingVehicleDashboard
                        && sameComponent(driverApp, quickMusicApp));
        styleCompactButton(compactMaxButton,
                contentVisible && !showingVehicleDashboard
                        && sameComponent(driverApp, quickMaxApp));
        styleCompactButton(compactSettingsButton, settingsVisible);
    }

    private void styleCompactButton(View button, boolean active) {
        if (button != null) {
            Object tag = button.getTag();
            if (tag instanceof CompactButtonVisual) {
                CompactButtonVisual visual = (CompactButtonVisual) tag;
                visual.tile.setAlpha(1f);
                visual.indicator.animate().cancel();
                visual.indicator.setBackground(roundedBackground(
                        active ? 0xFF2682DF : Color.TRANSPARENT, 3));
                visual.indicator.setAlpha(active ? 1f : 0.22f);
                visual.indicator.setElevation(0f);
            }
        }
    }

    private boolean sameComponent(AppEntry first, AppEntry second) {
        return first != null && second != null
                && first.component.equals(second.component);
    }

    private AppEntry resolveQuickApp(String key, String... defaultPackages) {
        String flattened = preferences.getString(key, null);
        AppEntry selected = repository.resolve(flattened == null
                ? null : ComponentName.unflattenFromString(flattened));
        return selected == null ? findFirstInstalled(defaultPackages) : selected;
    }

    private void chooseQuickApp(String preferenceKey) {
        AppPickerDialog.show(this, getAvailableApps(), selected -> {
            preferences.edit().putString(preferenceKey,
                    selected.component.flattenToString()).apply();
            render();
            Toast.makeText(this, "Назначено: " + selected.label,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void activateQuickApp(AppEntry entry) {
        if (entry == null) {
            Toast.makeText(this, "Приложение не установлено",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        activateCompactEntry(entry, true);
    }

    private void activateCompactEntry(AppEntry selected, boolean persist) {
        int existingIndex = indexOf(driverApps, selected.component);
        if (existingIndex < 0) {
            driverApps.add(selected);
            existingIndex = driverApps.size() - 1;
        }
        driverAppIndex = existingIndex;
        updateCurrentEntries();
        saveCarousel(true);
        showingVehicleDashboard = false;
        vehicleDashboardView = null;
        vehicleTelemetryController.stop();
        showCompactEmbeddedApp(selected, existingIndex);
        updateCompactDockSelection();
        if (persist) {
            AppPreferences.setCompactTarget(this,
                    selected.component.getPackageName());
        }
    }

    private void showParkingCamera() {
        if (ParkingCameraOverlay.show(this)) {
            return;
        }
        Toast.makeText(this,
                "Разрешите BYD Split показывать окно камеры поверх приложений",
                Toast.LENGTH_LONG).show();
        Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(permission);
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
        driverSlot.removeAllViews();
        vehicleDashboardView = new VehicleDashboardView(
                this, this::handleVehicleAction);
        driverSlot.addView(vehicleDashboardView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        showingVehicleDashboard = true;
        vehicleTelemetryController.start(
                AppPreferences.isDemoModeEnabled(this));
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, 341);
        }
        updateCompactDockSelection();
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

    private void openBatteryDetails() { openVehicleDetails(false); }

    private void openVehicleDetails(boolean technical) {
        if (farSlot == null || batteryDetailsView != null) return;
        hidePicker();
        batteryDisplacedApp = farSlot.getChildCount() > 0 ? farSlot.getChildAt(0) : null;
        BatteryDetailsView page = new BatteryDetailsView(this, () -> closeBatteryDetails(true), technical);
        batteryDetailsView = page;
        farSlot.addView(page, new FrameLayout.LayoutParams(-1, -1));
        float width = Math.max(1, farSlot.getWidth());
        page.setTranslationX(-width);
        if (batteryDisplacedApp != null)
            batteryDisplacedApp.animate().translationX(width).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator()).start();
        page.animate().translationX(0).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();
        page.setActive(resumed);
    }

    private void closeBatteryDetails(boolean animate) {
        if (batteryDetailsView == null) return;
        if (batteryClosing && animate) return;
        batteryClosing = true;
        BatteryDetailsView page = batteryDetailsView;
        View app = batteryDisplacedApp;
        page.setActive(false);
        page.animate().cancel();
        if (app != null) app.animate().cancel();
        Runnable finished = () -> {
            if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
            if (app != null) app.setTranslationX(0);
            if (batteryDetailsView == page) {
                batteryDetailsView = null; batteryDisplacedApp = null; batteryClosing = false;
            }
        };
        if (!animate) { finished.run(); return; }
        if (app != null) app.animate().translationX(0).setDuration(300).start();
        page.animate().translationX(-Math.max(1,farSlot.getWidth())).setDuration(300)
                .withEndAction(finished).start();
    }

    @Override public void onBackPressed() {
        // Embedded apps own the editor, but this window is their IME control target.
        // Hide through window insets: this Activity has no served EditText/token.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            View decor = getWindow().getDecorView();
            WindowInsets insets = decor.getRootWindowInsets();
            if (insets != null && insets.isVisible(WindowInsets.Type.ime())) {
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) controller.hide(WindowInsets.Type.ime());
                return;
            }
        }
        if (batteryDetailsView != null) { closeBatteryDetails(true); return; }
        super.onBackPressed();
    }

    private void handleVehicleAction(int action) {
        if (action == VehicleDashboardView.ACTION_BATTERY_DETAILS || action == VehicleDashboardView.ACTION_TECH_DETAILS) {
            boolean technical=action==VehicleDashboardView.ACTION_TECH_DETAILS;
            if (batteryDetailsView == null) openVehicleDetails(technical);
            else if(batteryDetailsView.technical!=technical) { closeBatteryDetails(false);openVehicleDetails(technical); }
            else closeBatteryDetails(true);
            return;
        }
        Intent intent;
        if (action == VehicleDashboardView.ACTION_AUTO) {
            intent = Intent.makeMainActivity(new ComponentName(
                    "com.byd.mycar", "com.byd.mycar.StartActivity"));
        } else if (action == VehicleDashboardView.ACTION_CLIMATE) {
            // Match the OEM navigation bar's climate launch contract.
            intent = new Intent("OPEN_AIR_CONDITIONING_FUNCTION");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("OPEN_AIR_CONDITIONING_FRAGMENT_ID", 1000);
            intent.putExtra("from", "Navbar");
            intent.setComponent(new ComponentName(
                    "com.byd.airconditioning",
                    "com.byd.airconditioning.mainactivity.FullScreenMainActivity"));
        } else if (action == VehicleDashboardView.ACTION_CAR_SETTINGS) {
            intent = Intent.makeMainActivity(new ComponentName(
                    "com.byd.carsettings", "com.byd.carsettings.MainActivity"));
        } else {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
        }
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            android.util.Log.w("BYD_SPLIT", "Vehicle shortcut failed", error);
            Toast.makeText(this, "Системный экран недоступен",
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
        vehicleDashboardView = null;
        vehicleTelemetryController.stop();
        showCompactEmbeddedApp(selected, existingIndex);
        updateCompactDockSelection();
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
            String component = app.component.flattenToString();
            if (component.equals(preferences.getString(
                    AppPreferences.KEY_QUICK_MUSIC_APP, ""))
                    || component.equals(preferences.getString(
                    AppPreferences.KEY_QUICK_MAX_APP, ""))
                    || PACKAGE_MAX.equals(packageName)
                    || PACKAGE_YANDEX_MUSIC.equals(packageName)
                    || PACKAGE_YANDEX_MUSIC_ALT.equals(packageName)) {
                result.add(app);
            }
        }
        return result;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 341 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraAutomation.start();
            vehicleTelemetryController.startLocationIfPermitted();
        }
    }

    private void showCompactEmbeddedApp(AppEntry selected, int pageIndex) {
        if (driverEmbeddedPane == null) {
            refreshPane(KEY_DRIVER_APP);
            return;
        }
        if (driverEmbeddedPane.getParent() != driverSlot) {
            driverSlot.removeAllViews();
            if (driverEmbeddedPane.getParent() instanceof ViewGroup) {
                ((ViewGroup) driverEmbeddedPane.getParent())
                        .removeView(driverEmbeddedPane);
            }
            driverSlot.addView(driverEmbeddedPane, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        driverEmbeddedPane.switchApp(
                selected, pageIndex, driverApps.size(), 0);
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
                !driverPane,
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
            vehicleDashboardView = null;
            vehicleTelemetryController.stop();
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
        List<AppEntry> apps = driverPane ? driverApps : farApps;
        if (apps == null || pickerOverlay != null) {
            return;
        }
        if (driverPane) {
            moveCompactCarouselWithVehicle(apps);
            return;
        }
        if (apps.size() < 2) {
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

    private void moveCompactCarouselWithVehicle(List<AppEntry> apps) {
        int itemCount = apps.size() + 1;
        if (itemCount < 2) {
            return;
        }
        int currentPosition = showingVehicleDashboard
                ? 0 : Math.max(0, Math.min(driverAppIndex, apps.size() - 1)) + 1;
        int nextPosition = (currentPosition - 1 + itemCount) % itemCount;
        if (nextPosition == 0) {
            showVehicleDashboard(true);
            return;
        }
        int nextIndex = nextPosition - 1;
        driverAppIndex = nextIndex;
        updateCurrentEntries();
        saveCarousel(true);
        showingVehicleDashboard = false;
        vehicleDashboardView = null;
        vehicleTelemetryController.stop();
        AppEntry selected = apps.get(nextIndex);
        AppPreferences.setCompactTarget(
                this, selected.component.getPackageName());
        showCompactEmbeddedApp(selected, nextIndex);
        updateCompactDockSelection();
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
        if (KEY_FAR_APP.equals(preferenceKey)) closeBatteryDetails(false);
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
        closeBatteryDetails(false);
        for (EmbeddedAppPane pane : activePanes) {
            pane.release();
        }
        activePanes.clear();
        driverEmbeddedPane = null;
        farEmbeddedPane = null;
    }

    private void openSettings() {
        settingsVisible = true;
        updateCompactDockSelection();
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
