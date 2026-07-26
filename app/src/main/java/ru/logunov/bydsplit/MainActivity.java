package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
    private List<AppEntry> availableApps;
    private String pickingKey;
    private FrameLayout driverSlot;
    private FrameLayout farSlot;
    private EmbeddedAppPane driverEmbeddedPane;
    private EmbeddedAppPane farEmbeddedPane;

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
        driverApp = readEntry(KEY_DRIVER_APP);
        farApp = readEntry(KEY_FAR_APP);
        if (!AppPreferences.isDemoModeEnabled(this)) {
            shellBridgeClient.bootstrap(true, success -> {
                if (!success) {
                    android.util.Log.w("BYD_SPLIT",
                            "Встроенный ADB не авторизован");
                }
            });
        }
        render();
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

    static void applyPanelLayoutFromSettings() {
        MainActivity activity = currentActivity.get();
        if (activity != null && !activity.isFinishing()
                && !activity.isDestroyed()) {
            activity.runOnUiThread(activity::applyPanelLayout);
        }
    }

    static boolean handleSteeringPulse(boolean longPress) {
        MainActivity activity = currentActivity.get();
        if (activity == null
                || (longPress
                && !SteeringAccessibilityService.isMaxChatOpen())) {
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
        return false;
    }

    private void render() {
        releasePanes();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackgroundColor(getColor(R.color.background));

        driverSlot = new FrameLayout(this);
        farSlot = new FrameLayout(this);

        boolean driverPaneLarge = AppPreferences.isDriverPaneLarge(this);
        LinearLayout.LayoutParams driverParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT,
                driverPaneLarge ? 2f : 1f);
        LinearLayout.LayoutParams farParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT,
                driverPaneLarge ? 1f : 2f);
        driverParams.setMarginEnd(dp(5));
        farParams.setMarginStart(dp(5));
        root.addView(driverSlot, driverParams);
        root.addView(farSlot, farParams);
        setContentView(root);
        refreshPane(KEY_DRIVER_APP);
        refreshPane(KEY_FAR_APP);
    }

    private void applyPanelLayout() {
        if (driverSlot == null || farSlot == null) {
            return;
        }
        boolean driverPaneLarge = AppPreferences.isDriverPaneLarge(this);
        LinearLayout.LayoutParams driverParams =
                (LinearLayout.LayoutParams) driverSlot.getLayoutParams();
        LinearLayout.LayoutParams farParams =
                (LinearLayout.LayoutParams) farSlot.getLayoutParams();
        driverParams.weight = driverPaneLarge ? 2f : 1f;
        farParams.weight = driverPaneLarge ? 1f : 2f;
        driverSlot.setLayoutParams(driverParams);
        farSlot.setLayoutParams(farParams);
    }

    private View createPane(String title, AppEntry entry, String preferenceKey,
                            boolean driverPane) {
        if (preferenceKey.equals(pickingKey)) {
            return createInlinePicker(title, entry, preferenceKey, driverPane);
        }
        if (entry == null) {
            return createInlinePicker(title, null, preferenceKey, driverPane);
        }

        EmbeddedAppPane pane = new EmbeddedAppPane(
                this,
                entry,
                driverPane ? "driver" : "far",
                shellBridgeClient,
                () -> chooseApp(preferenceKey),
                this::openSettings
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
        pickingKey = preferenceKey;
        refreshPane(preferenceKey);
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
                pickingKey = null;
                refreshPane(preferenceKey);
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
        preferences.edit()
                .putString(preferenceKey, selected.component.flattenToString())
                .apply();
        if (KEY_DRIVER_APP.equals(preferenceKey)) {
            driverApp = selected;
        } else {
            farApp = selected;
        }
        pickingKey = null;
        refreshPane(preferenceKey);
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
