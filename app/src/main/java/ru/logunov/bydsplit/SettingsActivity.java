package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 3000);
        }
    };

    private ShellBridgeClient bridgeClient;
    private TextView adbStatus;
    private TextView bridgeStatus;
    private TextView inputStatus;
    private TextView steeringStatus;
    private TextView accessibilityStatus;
    private TextView summary;
    private Button restartButton;
    private Button authorizeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setDimAmount(0.68f);
        setFinishOnTouchOutside(true);
        bridgeClient = new ShellBridgeClient(this);
        setContentView(createContent());
        getWindow().getDecorView().post(() -> {
            int width = Math.round(
                    getResources().getDisplayMetrics().widthPixels * 0.88f);
            int height = Math.round(
                    getResources().getDisplayMetrics().heightPixels * 0.86f);
            getWindow().setLayout(width, height);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(periodicRefresh);
        periodicRefresh.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(periodicRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        bridgeClient.close();
        super.onDestroy();
    }

    private View createContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(true);
        scroll.setPadding(0, dp(14), 0, dp(14));
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackground(rounded(getColor(R.color.background), 24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(42), dp(20), dp(42), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = actionButton("‹  К окнам");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        TextView title = text("Настройки BYD Split", 28, Color.WHITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dp(24));
        header.addView(title, titleParams);
        root.addView(header);

        TextView intro = text(
                "Здесь собраны разрешения, автозапуск и состояние компонентов, "
                        + "которые обеспечивают два интерактивных окна.",
                16, getColor(R.color.text_secondary));
        LinearLayout.LayoutParams introParams = fullWidthWrap();
        introParams.topMargin = dp(12);
        introParams.bottomMargin = dp(22);
        root.addView(intro, introParams);

        LinearLayout statusCard = card();
        statusCard.addView(sectionTitle("Готовность системы"));
        summary = text("Проверяем…", 16, getColor(R.color.text_secondary));
        addWithTop(statusCard, summary, 8);
        adbStatus = addStatus(statusCard, "ADB на головном устройстве");
        bridgeStatus = addStatus(statusCard, "Встроенный ADB-клиент");
        inputStatus = addStatus(statusCard, "Касания и мультитач");
        steeringStatus = addStatus(statusCard, "Кнопка микрофона на руле");
        accessibilityStatus = addStatus(statusCard, "Доступ к MAX");

        LinearLayout statusActions = horizontalActions();
        Button refresh = actionButton("Проверить");
        refresh.setOnClickListener(view -> refreshStatus());
        statusActions.addView(refresh, weightedButtonParams());
        restartButton = actionButton("Перезапустить помощники");
        restartButton.setOnClickListener(view -> restartHelpers());
        LinearLayout.LayoutParams restartParams = weightedButtonParams();
        restartParams.setMarginStart(dp(10));
        statusActions.addView(restartButton, restartParams);
        addWithTop(statusCard, statusActions, 18);

        authorizeButton = actionButton("Разрешить ADB и запустить помощники");
        authorizeButton.setOnClickListener(view -> authorizeAndStart());
        addWithTop(statusCard, authorizeButton, 10);
        root.addView(statusCard, fullWidthWrap());

        LinearLayout permissionCard = card();
        permissionCard.addView(sectionTitle("Разрешения и первый запуск"));
        TextView permissionHelp = text(
                "Специальные возможности нужны только для определения открытого "
                        + "чата MAX. При первом запуске подтвердите штатный запрос "
                        + "ADB и выберите «Всегда разрешать». После этого приложение "
                        + "само запускает все помощники.",
                15, getColor(R.color.text_secondary));
        addWithTop(permissionCard, permissionHelp, 8);

        LinearLayout permissionActions = horizontalActions();
        Button accessibility = actionButton("Специальные возможности");
        accessibility.setOnClickListener(view ->
                openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        permissionActions.addView(accessibility, weightedButtonParams());
        Button developer = actionButton("Настройки разработчика");
        developer.setOnClickListener(view ->
                openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        LinearLayout.LayoutParams developerParams = weightedButtonParams();
        developerParams.setMarginStart(dp(10));
        permissionActions.addView(developer, developerParams);
        addWithTop(permissionCard, permissionActions, 16);

        TextView fallbackHelp = text(
                "Компьютер и скрипты из tools нужны только как резервный способ "
                        + "восстановления, если прошивка DiLink не разрешает "
                        + "локальное ADB-подключение.",
                14, getColor(R.color.text_secondary));
        addWithTop(permissionCard, fallbackHelp, 12);
        LinearLayout.LayoutParams permissionParams = fullWidthWrap();
        permissionParams.topMargin = dp(16);
        root.addView(permissionCard, permissionParams);

        LinearLayout behaviorCard = card();
        behaviorCard.addView(sectionTitle("Поведение"));
        Switch autoStart = settingsSwitch(
                "Автозапуск после загрузки DiLink",
                AppPreferences.isAutoStartEnabled(this));
        autoStart.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(this).edit()
                        .putBoolean(AppPreferences.KEY_AUTO_START, checked)
                        .apply());
        addWithTop(behaviorCard, autoStart, 10);

        Switch demoMode = settingsSwitch(
                "Режим Android Emulator",
                AppPreferences.isDemoModeEnabled(this));
        demoMode.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_DEMO_MODE, checked)
                    .apply();
            Toast.makeText(this,
                    checked
                            ? "BYD-функции отключены, реальные приложения сохранены"
                            : "Режим DiLink включён",
                    Toast.LENGTH_SHORT).show();
            refreshStatus();
        });
        addWithTop(behaviorCard, demoMode, 8);
        TextView demoHelp = text(
                "На эмуляторе используются те же два виртуальных дисплея, "
                        + "реальные установленные приложения, касания и мультитач. "
                        + "Отключаются только кнопка руля и другие функции BYD.",
                14, getColor(R.color.text_secondary));
        addWithTop(behaviorCard, demoHelp, 6);
        LinearLayout.LayoutParams behaviorParams = fullWidthWrap();
        behaviorParams.topMargin = dp(16);
        root.addView(behaviorCard, behaviorParams);
        return scroll;
    }

    private void refreshStatus() {
        boolean emulatorMode = AppPreferences.isDemoModeEnabled(this);
        setStatus(adbStatus, isAdbEnabled(), null);
        boolean accessibilityEnabled = isAccessibilityEnabled();
        setStatus(accessibilityStatus,
                emulatorMode || accessibilityEnabled,
                emulatorMode ? "не требуется в эмуляторе" : null);
        summary.setText("Проверяем ADB-помощники…");
        bridgeClient.checkHealth(health -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setStatus(bridgeStatus, health.bridge,
                    health.bridge ? null : "нажмите «Разрешить ADB»");
            setStatus(inputStatus, health.input,
                    health.input ? null
                            : health.bridge ? "можно перезапустить"
                            : "мост недоступен");
            setStatus(steeringStatus,
                    emulatorMode || health.steering,
                    emulatorMode ? "не требуется в эмуляторе"
                            : health.bridge ? "можно перезапустить"
                            : "мост недоступен");
            restartButton.setEnabled(true);
            authorizeButton.setEnabled(!health.bridge);
            boolean ready = health.bridge && health.input
                    && (emulatorMode
                    || health.steering && accessibilityEnabled);
            summary.setText(ready
                    ? "Система готова к работе"
                    : "Требуется настройка — проверьте пункты ниже");
            summary.setTextColor(ready
                    ? getColor(R.color.success)
                    : getColor(R.color.warning));
        }));
    }

    private void restartHelpers() {
        restartButton.setEnabled(false);
        restartButton.setText("Запускаем…");
        java.util.function.Consumer<Boolean> callback =
                success -> runOnUiThread(() -> {
            restartButton.setText("Перезапустить помощники");
            Toast.makeText(this,
                    success ? "Помощники перезапущены"
                            : "Не удалось: сначала выполните ADB-bootstrap",
                    Toast.LENGTH_LONG).show();
            handler.postDelayed(this::refreshStatus, 800);
        });
        if (AppPreferences.isDemoModeEnabled(this)) {
            bridgeClient.restartInput(callback);
        } else {
            bridgeClient.restartHelpers(callback);
        }
    }

    private void authorizeAndStart() {
        authorizeButton.setEnabled(false);
        authorizeButton.setText("Ожидаем подтверждение на экране…");
        boolean includeSteering = !AppPreferences.isDemoModeEnabled(this);
        bridgeClient.bootstrap(includeSteering, success -> runOnUiThread(() -> {
            authorizeButton.setText("Разрешить ADB и запустить помощники");
            authorizeButton.setEnabled(!success);
            Toast.makeText(this,
                    success
                            ? "ADB разрешён, помощники запущены"
                            : "Не удалось подключиться к локальному ADB",
                    Toast.LENGTH_LONG).show();
            handler.postDelayed(this::refreshStatus, 700);
        }));
    }

    private boolean isAdbEnabled() {
        try {
            return Settings.Global.getInt(
                    getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName expected = new ComponentName(
                this, SteeringAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String value : splitter) {
            ComponentName component = ComponentName.unflattenFromString(value);
            if (expected.equals(component)) {
                return true;
            }
        }
        return false;
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (RuntimeException error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private TextView addStatus(LinearLayout parent, String label) {
        TextView view = text("●  " + label + " — проверяем", 16,
                getColor(R.color.text_secondary));
        addWithTop(parent, view, 10);
        return view;
    }

    private void setStatus(TextView view, boolean ready, String detail) {
        String raw = view.getText().toString();
        int separator = raw.indexOf(" — ");
        String label = separator >= 0 ? raw.substring(3, separator)
                : raw.substring(Math.min(3, raw.length()));
        view.setText("●  " + label + " — "
                + (detail != null ? detail : ready ? "готово" : "не включено"));
        view.setTextColor(ready
                ? getColor(R.color.success)
                : getColor(R.color.warning));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(rounded(getColor(R.color.surface_light), 20));
        return card;
    }

    private TextView sectionTitle(String value) {
        return text(value, 21, Color.WHITE);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.1f);
        return view;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(rounded(getColor(R.color.button), 14));
        return button;
    }

    private Switch settingsSwitch(String label, boolean checked) {
        Switch view = new Switch(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setChecked(checked);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private LinearLayout horizontalActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        return actions;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        return new LinearLayout.LayoutParams(0, dp(52), 1f);
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void addWithTop(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = fullWidthWrap();
        params.topMargin = dp(topDp);
        parent.addView(child, params);
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
