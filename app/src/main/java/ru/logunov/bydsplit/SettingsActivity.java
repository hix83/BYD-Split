package ru.logunov.bydsplit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

public final class SettingsActivity extends Activity
        implements SteeringEventServer.KeyCaptureListener {
    private Button oneTwoButton;
    private Button twoOneButton;
    private Button steeringAssignButton;
    private Button steeringResetButton;
    private TextView steeringStatus;
    private ShellBridgeClient bridgeClient;
    private int steeringCaptureStage;
    private int capturedShortScan;

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
    protected void onDestroy() {
        SteeringEventServer.cancelKeyCapture(this);
        bridgeClient.cancelSteeringKeyCapture(
                ignored -> bridgeClient.close());
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
                "Настройте расположение панелей, кнопку на руле, "
                        + "автозапуск и режим Android Emulator.",
                16, getColor(R.color.text_secondary));
        LinearLayout.LayoutParams introParams = fullWidthWrap();
        introParams.topMargin = dp(12);
        introParams.bottomMargin = dp(22);
        root.addView(intro, introParams);

        LinearLayout layoutCard = card();
        layoutCard.addView(sectionTitle("Расположение панелей"));
        TextView layoutHelp = text(
                "Выберите, какая панель будет шире. Размер меняется сразу, "
                        + "без перезапуска открытых приложений.",
                15, getColor(R.color.text_secondary));
        addWithTop(layoutCard, layoutHelp, 8);

        LinearLayout layoutActions = horizontalActions();
        oneTwoButton = actionButton("1 : 2");
        oneTwoButton.setContentDescription(
                "Маленькая панель у водителя, большая справа");
        oneTwoButton.setOnClickListener(view -> setDriverPaneLarge(false));
        layoutActions.addView(oneTwoButton, weightedButtonParams());
        twoOneButton = actionButton("2 : 1");
        twoOneButton.setContentDescription(
                "Большая панель у водителя, маленькая справа");
        twoOneButton.setOnClickListener(view -> setDriverPaneLarge(true));
        LinearLayout.LayoutParams twoOneParams = weightedButtonParams();
        twoOneParams.setMarginStart(dp(10));
        layoutActions.addView(twoOneButton, twoOneParams);
        addWithTop(layoutCard, layoutActions, 16);
        updateLayoutButtons();
        root.addView(layoutCard, fullWidthWrap());

        LinearLayout steeringCard = card();
        steeringCard.addView(sectionTitle("Кнопка микрофона на руле"));
        TextView steeringHelp = text(
                "Назначение выполняется в два шага: сначала коротко нажмите "
                        + "нужную кнопку, затем нажмите и удерживайте её. "
                        + "Это учитывает разные коды короткого и долгого "
                        + "нажатия в прошивке BYD.",
                15, getColor(R.color.text_secondary));
        addWithTop(steeringCard, steeringHelp, 8);
        steeringStatus = text("", 15, getColor(R.color.text_secondary));
        addWithTop(steeringCard, steeringStatus, 12);

        LinearLayout steeringActions = horizontalActions();
        steeringAssignButton = actionButton("Назначить кнопку");
        steeringAssignButton.setOnClickListener(
                view -> beginSteeringAssignment());
        steeringActions.addView(
                steeringAssignButton, weightedButtonParams());
        steeringResetButton = actionButton("Сбросить");
        steeringResetButton.setOnClickListener(
                view -> resetSteeringAssignment());
        LinearLayout.LayoutParams resetParams = weightedButtonParams();
        resetParams.setMarginStart(dp(10));
        steeringActions.addView(steeringResetButton, resetParams);
        addWithTop(steeringCard, steeringActions, 14);
        updateSteeringStatus();
        LinearLayout.LayoutParams steeringParams = fullWidthWrap();
        steeringParams.topMargin = dp(16);
        root.addView(steeringCard, steeringParams);

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
            updateSteeringControlsEnabled();
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

    private void beginSteeringAssignment() {
        steeringCaptureStage = 1;
        steeringStatus.setText(
                "Шаг 1 из 2: коротко нажмите нужную кнопку на руле…");
        steeringStatus.setTextColor(getColor(R.color.warning));
        steeringAssignButton.setText("Ожидаем нажатие…");
        steeringAssignButton.setEnabled(false);
        steeringResetButton.setEnabled(false);
        requestNextSteeringKey();
    }

    @Override
    public void onSteeringKeyCaptured(int scanCode) {
        runOnUiThread(() -> handleCapturedSteeringKey(scanCode));
    }

    private void handleCapturedSteeringKey(int scanCode) {
        if (steeringCaptureStage == 1) {
            capturedShortScan = scanCode;
            steeringCaptureStage = 2;
            steeringStatus.setText(
                    "Шаг 2 из 2: теперь нажмите и удерживайте "
                            + "эту же кнопку…");
            getWindow().getDecorView().postDelayed(() -> {
                if (steeringCaptureStage == 2) {
                    requestNextSteeringKey();
                }
            }, 250);
            return;
        }
        if (steeringCaptureStage != 2) {
            return;
        }
        steeringCaptureStage = 0;
        AppPreferences.setSteeringScans(
                this, capturedShortScan, scanCode);
        updateSteeringStatus();
        steeringAssignButton.setEnabled(false);
        steeringResetButton.setEnabled(false);
        getWindow().getDecorView().postDelayed(() -> {
            SteeringEventServer.cancelKeyCapture(this);
            restartSteeringHelper("Кнопка назначена");
        }, 1500);
    }

    private void requestNextSteeringKey() {
        SteeringEventServer.beginKeyCapture(this);
        bridgeClient.captureNextSteeringKey(success -> {
            if (success) {
                return;
            }
            runOnUiThread(() -> {
                SteeringEventServer.cancelKeyCapture(this);
                steeringCaptureStage = 0;
                updateSteeringStatus();
                Toast.makeText(this,
                        "Помощник кнопки руля недоступен",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void resetSteeringAssignment() {
        SteeringEventServer.cancelKeyCapture(this);
        bridgeClient.cancelSteeringKeyCapture(ignored -> {
            // The helper restart below also clears capture state.
        });
        steeringCaptureStage = 0;
        AppPreferences.setSteeringScans(
                this,
                AppPreferences.DEFAULT_STEERING_SHORT_SCAN,
                AppPreferences.DEFAULT_STEERING_LONG_SCAN);
        updateSteeringStatus();
        restartSteeringHelper("Назначение сброшено");
    }

    private void restartSteeringHelper(String successMessage) {
        steeringAssignButton.setEnabled(false);
        steeringResetButton.setEnabled(false);
        bridgeClient.restartHelpers(success -> runOnUiThread(() -> {
            updateSteeringControlsEnabled();
            Toast.makeText(this,
                    success ? successMessage
                            : "Коды сохранены, но помощник не перезапустился",
                    Toast.LENGTH_LONG).show();
        }));
    }

    private void updateSteeringStatus() {
        steeringStatus.setText(
                "Сейчас: короткое — scan "
                        + AppPreferences.getSteeringShortScan(this)
                        + ", долгое — scan "
                        + AppPreferences.getSteeringLongScan(this));
        steeringStatus.setTextColor(getColor(R.color.text_secondary));
        steeringAssignButton.setText("Назначить кнопку");
        updateSteeringControlsEnabled();
    }

    private void updateSteeringControlsEnabled() {
        boolean enabled = !AppPreferences.isDemoModeEnabled(this)
                && steeringCaptureStage == 0;
        steeringAssignButton.setEnabled(enabled);
        steeringResetButton.setEnabled(enabled);
    }

    private void setDriverPaneLarge(boolean driverPaneLarge) {
        AppPreferences.setDriverPaneLarge(this, driverPaneLarge);
        updateLayoutButtons();
        MainActivity.applyPanelLayoutFromSettings();
    }

    private void updateLayoutButtons() {
        boolean driverPaneLarge = AppPreferences.isDriverPaneLarge(this);
        styleLayoutButton(oneTwoButton, !driverPaneLarge);
        styleLayoutButton(twoOneButton, driverPaneLarge);
    }

    private void styleLayoutButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.BLACK : Color.WHITE);
        button.setBackground(rounded(
                getColor(selected ? R.color.accent : R.color.button), 14));
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
