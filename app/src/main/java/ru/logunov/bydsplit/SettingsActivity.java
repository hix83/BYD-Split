package ru.logunov.bydsplit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity
        implements SteeringEventServer.KeyCaptureListener {
    private Button oneTwoButton;
    private Button twoOneButton;
    private Button steeringAssignButton;
    private Button steeringResetButton;
    private TextView layoutHelp;
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
        applyFullscreenMode();
        getWindow().getDecorView().post(() -> {
            int width = Math.round(
                    getResources().getDisplayMetrics().widthPixels * 0.88f);
            int height = Math.round(
                    getResources().getDisplayMetrics().heightPixels * 0.86f);
            getWindow().setLayout(width, height);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullscreenMode();
        }
    }

    private void applyFullscreenMode() {
        if (!AppPreferences.isFullscreenEnabled(this)) {
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onDestroy() {
        SteeringEventServer.cancelKeyCapture(this);
        bridgeClient.cancelSteeringKeyCapture(
                ignored -> bridgeClient.close());
        super.onDestroy();
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(16));
        root.setBackground(rounded(getColor(R.color.background), 24));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout content = new FrameLayout(this);
        String[] labels = {"Основные", "Камера парковки", "Места"};
        for (int index = 0; index < labels.length; index++) {
            Button tab = actionButton(labels[index]);
            final int page = index;
            tab.setOnClickListener(view -> showSettingsPage(content, tabs, page));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(48), 1f);
            if (index > 0) params.setMarginStart(dp(8));
            tabs.addView(tab, params);
        }
        root.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentParams.topMargin = dp(10);
        root.addView(content, contentParams);
        showSettingsPage(content, tabs, 0);
        return root;
    }

    private void showSettingsPage(FrameLayout host, LinearLayout tabs, int page) {
        host.removeAllViews();
        for (int index = 0; index < tabs.getChildCount(); index++) {
            tabs.getChildAt(index).setBackground(rounded(getColor(
                    index == page ? R.color.accent : R.color.button), 14));
        }
        View content = page == 0 ? createGeneralPage()
                : page == 1 ? createCameraPage() : createPlacesPage();
        host.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View createGeneralPage() {
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
                "Настройте расположение панелей, полноэкранный режим, "
                        + "кнопку на руле, автозапуск и режим Android Emulator.",
                16, getColor(R.color.text_secondary));
        LinearLayout.LayoutParams introParams = fullWidthWrap();
        introParams.topMargin = dp(12);
        introParams.bottomMargin = dp(22);
        root.addView(intro, introParams);

        LinearLayout layoutCard = card();
        layoutCard.addView(sectionTitle("Расположение панелей"));
        layoutHelp = text(
                "Размеры зафиксированы: служебная область занимает ⅓, "
                        + "основное приложение — ⅔. Выберите сторону "
                        + "служебной области.",
                15, getColor(R.color.text_secondary));
        addWithTop(layoutCard, layoutHelp, 8);

        LinearLayout layoutActions = horizontalActions();
        oneTwoButton = actionButton("1 : 2");
        oneTwoButton.setContentDescription(
                "Служебная область слева, основная справа");
        oneTwoButton.setOnClickListener(view -> setCompactPaneOnLeft(true));
        layoutActions.addView(oneTwoButton, weightedButtonParams());
        twoOneButton = actionButton("2 : 1");
        twoOneButton.setContentDescription(
                "Основная область слева, служебная справа");
        twoOneButton.setOnClickListener(view -> setCompactPaneOnLeft(false));
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
        Switch fullscreen = settingsSwitch(
                "Полноэкранный режим",
                AppPreferences.isFullscreenEnabled(this));
        fullscreen.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_FULLSCREEN, checked)
                    .apply();
            MainActivity.applyFullscreenModeFromSettings();
        });
        addWithTop(behaviorCard, fullscreen, 10);
        TextView fullscreenHelp = text(
                "Скрывает верхнюю и нижнюю системные панели DiLink. "
                        + "В полноэкранном режиме их можно временно показать "
                        + "свайпом от края экрана.",
                14, getColor(R.color.text_secondary));
        addWithTop(behaviorCard, fullscreenHelp, 6);

        Switch autoStart = settingsSwitch(
                "Автозапуск после загрузки DiLink",
                AppPreferences.isAutoStartEnabled(this));
        autoStart.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(this).edit()
                        .putBoolean(AppPreferences.KEY_AUTO_START, checked)
                        .apply());
        addWithTop(behaviorCard, autoStart, 12);

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

    private View createCameraPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(pageHeader("Камера парковки",
                "Интернет-сервис парковочной камеры из BYD Mate DM."));

        LinearLayout card = card();
        EditText name = input("Название камеры",
                AppPreferences.getParkingCameraName(this), false);
        card.addView(name, fullWidthWrap());
        EditText url = input("Адрес камеры",
                AppPreferences.getParkingCameraUrl(this), false);
        addWithTop(card, url, 10);
        TextView help = text("По умолчанию: https://parking.napaster.ru. "
                + "Если протокол не указан, будет использован HTTPS.", 14,
                getColor(R.color.text_secondary));
        addWithTop(card, help, 8);

        LinearLayout actions = horizontalActions();
        Button save = actionButton("Сохранить");
        save.setOnClickListener(view -> {
            String cameraName = name.getText().toString().trim();
            String cameraUrl = url.getText().toString().trim();
            if (cameraUrl.isEmpty()) {
                Toast.makeText(this, "Укажите адрес камеры", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!cameraUrl.contains("://")) cameraUrl = "https://" + cameraUrl;
            AppPreferences.get(this).edit()
                    .putString(AppPreferences.KEY_PARKING_CAMERA_NAME,
                            cameraName.isEmpty() ? "Парковка" : cameraName)
                    .putString(AppPreferences.KEY_PARKING_CAMERA_URL, cameraUrl)
                    .apply();
            Toast.makeText(this, "Камера сохранена", Toast.LENGTH_SHORT).show();
        });
        actions.addView(save, weightedButtonParams());
        Button test = actionButton("Открыть для проверки");
        test.setOnClickListener(view -> {
            save.performClick();
            if (!ParkingCameraOverlay.show(this)) {
                Toast.makeText(this,
                        "Разрешение показа поверх приложений выдаётся при первом запуске камеры",
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams testParams = weightedButtonParams();
        testParams.setMarginStart(dp(10));
        actions.addView(test, testParams);
        addWithTop(card, actions, 16);
        root.addView(card, fullWidthWrap());
        return scroll;
    }

    private View createPlacesPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(pageHeader("Места",
                "При въезде в выбранную геозону интернет-камера откроется один раз. "
                        + "После выезда зона снова станет активной."));

        LinearLayout automationCard = card();
        Switch enabled = settingsSwitch("Автоматически открывать камеру в местах",
                AppPreferences.isParkingCameraAutoEnabled(this));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_PARKING_CAMERA_AUTO, checked).apply();
            if (checked && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 342);
            }
        });
        automationCard.addView(enabled, fullWidthWrap());
        root.addView(automationCard, fullWidthWrap());

        LinearLayout listCard = card();
        listCard.addView(sectionTitle("Геозоны"));
        List<CameraPlace> places = new ArrayList<>(AppPreferences.getCameraPlaces(this));
        if (places.isEmpty()) {
            addWithTop(listCard, text("Пока нет сохранённых мест.", 15,
                    getColor(R.color.text_secondary)), 10);
        }
        for (int index = 0; index < places.size(); index++) {
            CameraPlace place = places.get(index);
            LinearLayout row = horizontalActions();
            TextView label = text(place.name + "  ·  "
                    + Math.round(place.radiusMeters) + " м\n"
                    + String.format(java.util.Locale.US, "%.6f, %.6f",
                    place.latitude, place.longitude), 15, Color.WHITE);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(58), 1f));
            Button delete = actionButton("Удалить");
            delete.setOnClickListener(view -> {
                places.remove(place);
                AppPreferences.setCameraPlaces(this, places);
                row.setVisibility(View.GONE);
                Toast.makeText(this, "Место удалено", Toast.LENGTH_SHORT).show();
            });
            row.addView(delete, new LinearLayout.LayoutParams(dp(116), dp(48)));
            addWithTop(listCard, row, 10);
        }
        Button add = actionButton("+ Добавить место");
        add.setOnClickListener(view -> showPlaceDialog());
        addWithTop(listCard, add, 14);
        LinearLayout.LayoutParams listParams = fullWidthWrap();
        listParams.topMargin = dp(14);
        root.addView(listCard, listParams);
        return scroll;
    }

    private void showPlaceDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = input("Название", "Парковка", false);
        EditText latitude = input("Широта", "", true);
        EditText longitude = input("Долгота", "", true);
        EditText radius = input("Радиус, м", "50", true);
        form.addView(name); form.addView(latitude); form.addView(longitude); form.addView(radius);
        Button current = actionButton("Подставить текущее местоположение");
        current.setOnClickListener(view -> fillCurrentLocation(latitude, longitude));
        addWithTop(form, current, 10);
        new AlertDialog.Builder(this)
                .setTitle("Новое место")
                .setView(form)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    try {
                        List<CameraPlace> places = new ArrayList<>(
                                AppPreferences.getCameraPlaces(this));
                        places.add(new CameraPlace(name.getText().toString().trim(),
                                Double.parseDouble(latitude.getText().toString()),
                                Double.parseDouble(longitude.getText().toString()),
                                Float.parseFloat(radius.getText().toString())));
                        AppPreferences.setCameraPlaces(this, places);
                        Toast.makeText(this, "Место сохранено", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException error) {
                        Toast.makeText(this, "Проверьте координаты и радиус",
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void fillCurrentLocation(EditText latitude, EditText longitude) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 342);
            Toast.makeText(this, "Разрешите геопозицию и нажмите ещё раз",
                    Toast.LENGTH_LONG).show();
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (location == null) {
            Toast.makeText(this, "Текущая геопозиция пока недоступна",
                    Toast.LENGTH_LONG).show();
            return;
        }
        latitude.setText(String.valueOf(location.getLatitude()));
        longitude.setText(String.valueOf(location.getLongitude()));
    }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackground(rounded(getColor(R.color.background), 20));
        return scroll;
    }

    private LinearLayout pageRoot(ScrollView scroll) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View pageHeader(String titleValue, String description) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(text(titleValue, 27, Color.WHITE));
        addWithTop(header, text(description, 15, getColor(R.color.text_secondary)), 6);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(16);
        header.setLayoutParams(params);
        return header;
    }

    private EditText input(String hint, String value, boolean numeric) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(getColor(R.color.text_secondary));
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(getColor(R.color.button), 12));
        input.setMinHeight(dp(52));
        if (numeric) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        return input;
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

    private void setCompactPaneOnLeft(boolean onLeft) {
        AppPreferences.setCompactPaneOnLeft(this, onLeft);
        updateLayoutButtons();
        MainActivity.applyPanelLayoutFromSettings();
    }

    private void updateLayoutButtons() {
        boolean compactOnLeft = AppPreferences.isCompactPaneOnLeft(this);
        styleLayoutButton(oneTwoButton, compactOnLeft);
        styleLayoutButton(twoOneButton, !compactOnLeft);
        layoutHelp.setText(
                "Размеры зафиксированы: служебная область занимает ⅓, "
                        + "основное приложение — ⅔. Переключение меняет "
                        + "области местами без перезапуска приложений.");
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
