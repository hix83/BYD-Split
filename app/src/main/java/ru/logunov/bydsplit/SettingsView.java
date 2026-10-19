package ru.logunov.bydsplit;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

final class SettingsView extends FrameLayout implements SteeringEventServer.KeyCaptureListener {
    private final Activity host;
    private final Runnable closeAction;
    private FrameLayout pageHost;
    private LinearLayout navigation;
    private int selectedPage;
    private boolean closed;
    private final boolean initialDemo;
    private Button oneTwoButton, twoOneButton, steeringAssignButton, steeringResetButton;
    private TextView layoutHelp, steeringStatus;
    private ShellBridgeClient bridgeClient;
    private int steeringCaptureStage, capturedShortScan;

    SettingsView(Activity host, Runnable closeAction) {
        super(host);
        this.host = host;
        this.closeAction = closeAction;
        initialDemo = AppPreferences.isDemoModeEnabled(host);
        bridgeClient = new ShellBridgeClient(host);
        addView(createContent(), new FrameLayout.LayoutParams(-1, -1));
    }

    private String versionName() {
        try { return host.getPackageManager().getPackageInfo(host.getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return "—"; }
    }

    private int getColor(int id) { return host.getColor(id); }

    boolean demoModeChanged() { return initialDemo != AppPreferences.isDemoModeEnabled(host); }

    private void cancelCapture() {
        if (steeringCaptureStage == 0) return;
        steeringCaptureStage = 0;
        SteeringEventServer.cancelKeyCapture(this);
        bridgeClient.cancelSteeringKeyCapture(ignored -> {});
    }

    void close() {
        if (closed) return;
        closed = true;
        steeringCaptureStage = 0;
        SteeringEventServer.cancelKeyCapture(this);
        bridgeClient.cancelSteeringKeyCapture(ignored -> bridgeClient.close());
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackground(rounded(getColor(R.color.background), 15));
        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Настройки", 24, getColor(R.color.text_primary));
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        Button back = actionButton("‹  Назад");
        back.setOnClickListener(v -> closeAction.run());
        header.addView(back, new LinearLayout.LayoutParams(dp(112), dp(44)));
        root.addView(header);
        LinearLayout body = horizontalActions();
        navigation = new LinearLayout(host);
        navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setPadding(0, dp(12), dp(12), 0);
        pageHost = new FrameLayout(host);
        String[] labels = {"Экран", "Автомобиль", "Запуск и ADB", "Камера", "Места", "Автоматизации", "Обновления"};
        for (int index = 0; index < labels.length; index++) {
            Button tab = actionButton(labels[index]);
            final int page = index;
            tab.setOnClickListener(v -> showSettingsPage(pageHost, navigation, page));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
            params.bottomMargin = dp(6);
            navigation.addView(tab, params);
        }
        ScrollView navScroll = new ScrollView(host);
        navScroll.addView(navigation);
        body.addView(navScroll, new LinearLayout.LayoutParams(dp(172), -1));
        body.addView(pageHost, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        showSettingsPage(pageHost, navigation, selectedPage);
        return root;
    }

    private void showSettingsPage(FrameLayout contentHost, LinearLayout tabs, int page) {
        cancelCapture();
        selectedPage = page;
        contentHost.removeAllViews();
        for (int i = 0; i < tabs.getChildCount(); i++) {
            Button tab = (Button) tabs.getChildAt(i);
            tab.setTextColor(getColor(i == page ? R.color.accent : R.color.text_primary));
            tab.setBackground(rounded(getColor(i == page ? R.color.button : R.color.background), 12));
            tab.setSelected(i == page);
        }
        View content = page == 3 ? createCameraPage() : page == 4 ? createPlacesPage()
                : page == 5 ? new TemperatureAutomationView(host) : createGeneralPage(page);
        contentHost.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private View createGeneralPage(int page) {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(true);
        scroll.setPadding(0, dp(14), 0, dp(14));
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackground(rounded(getColor(R.color.background), 24));

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(8), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        String[] titles = {"Экран и оформление", "Функции автомобиля", "Запуск и подключение"};
        String[] descriptions = {"Расположение панелей, тема и системные панели.",
                "Кнопка на руле и музыкальная карточка приборной панели.",
                "Автозапуск лаунчера, контроль Remote ADB и режим проверки."};
        root.addView(pageHeader(page == 6 ? "Обновления Seagull" : titles[page],
                page == 6 ? "Текущая версия: " + versionName() : descriptions[page]));

        LinearLayout layoutCard = card();
        layoutCard.addView(sectionTitle("Расположение панелей"));
        layoutHelp = text(
                "Размеры зафиксированы: служебная область занимает ⅓, "
                        + "основное приложение — ⅔. Выберите сторону "
                        + "служебной области.",
                15, getColor(R.color.text_secondary));
        addWithTop(layoutCard, layoutHelp, 8);

        LinearLayout layoutActions = horizontalActions();
        oneTwoButton = actionButton("Слева  ⅓ | ⅔");
        oneTwoButton.setContentDescription(
                "Служебная область слева, основная справа");
        oneTwoButton.setOnClickListener(view -> setCompactPaneOnLeft(true));
        layoutActions.addView(oneTwoButton, weightedButtonParams());
        twoOneButton = actionButton("Справа  ⅔ | ⅓");
        twoOneButton.setContentDescription(
                "Основная область слева, служебная справа");
        twoOneButton.setOnClickListener(view -> setCompactPaneOnLeft(false));
        LinearLayout.LayoutParams twoOneParams = weightedButtonParams();
        twoOneParams.setMarginStart(dp(10));
        layoutActions.addView(twoOneButton, twoOneParams);
        addWithTop(layoutCard, layoutActions, 16);
        updateLayoutButtons();
        if (page == 0) root.addView(layoutCard, fullWidthWrap());

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
        if (page == 1) root.addView(steeringCard, steeringParams);

        LinearLayout themeCard = card();
        themeCard.addView(sectionTitle("Оформление"));
        TextView themeHelp = text("Авто следует дневной/ночной теме автомобиля. Можно выбрать тему только для лаунчера.",
                15, getColor(R.color.text_secondary));
        addWithTop(themeCard, themeHelp, 8);
        LinearLayout themeActions = horizontalActions();
        String[] themeModes = {ThemePolicy.AUTO, ThemePolicy.DAY, ThemePolicy.NIGHT};
        String[] themeLabels = {"Как в автомобиле", "Дневная", "Ночная"};
        String selectedTheme = AppPreferences.get(host).getString(ThemeManager.KEY, ThemePolicy.AUTO);
        for (int i = 0; i < themeModes.length; i++) {
            final String mode = themeModes[i];
            Button button = actionButton(themeLabels[i]);
            button.setSelected(mode.equals(selectedTheme));
            if (mode.equals(selectedTheme)) button.setTextColor(getColor(R.color.accent));
            button.setOnClickListener(v -> {
                AppPreferences.get(host).edit().putString(ThemeManager.KEY, mode).apply();
                AppPreferences.get(host).edit().putBoolean("settings_reopen_after_theme", true).apply();
                host.recreate();
            });
            themeActions.addView(button, weightedButtonParams());
        }
        addWithTop(themeCard, themeActions, 12);
        LinearLayout.LayoutParams themeParams = fullWidthWrap(); themeParams.topMargin = dp(16);
        if (page == 0) root.addView(themeCard, themeParams);

        LinearLayout behaviorCard = card();
        behaviorCard.addView(sectionTitle("Системные панели"));
        Switch fullscreen = settingsSwitch(
                "Полноэкранный режим",
                AppPreferences.isFullscreenEnabled(host));
        fullscreen.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(host).edit()
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

        if (page == 0) addWithTop(root, behaviorCard, 16);
        behaviorCard = card();
        behaviorCard.addView(sectionTitle("Музыка на приборной панели"));
        Switch clusterMusic = settingsSwitch("Трек Яндекса на приборной панели",
                AppPreferences.get(host).getBoolean(ClusterMusicBridge.KEY, false));
        clusterMusic.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(host).edit().putBoolean(ClusterMusicBridge.KEY, checked).apply());
        addWithTop(behaviorCard, clusterMusic, 12);
        addWithTop(behaviorCard, text("Название, исполнитель и прогресс Яндекс Музыки в штатной карточке приборной панели. Требуется доступ к уведомлениям и подключение ADB.",
                14, getColor(R.color.text_secondary)), 6);

        android.widget.Button mediaAccess = actionButton("Доступ к уведомлениям");
        mediaAccess.setOnClickListener(view -> host.startActivity(new android.content.Intent(
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        addWithTop(behaviorCard, mediaAccess, 8);

        if (page == 1) addWithTop(root, behaviorCard, 16);
        behaviorCard = card();
        behaviorCard.addView(sectionTitle("Запуск лаунчера"));
        Switch autoStart = settingsSwitch(
                "Автозапуск после загрузки DiLink",
                AppPreferences.isAutoStartEnabled(host));
        autoStart.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(host).edit()
                        .putBoolean(AppPreferences.KEY_AUTO_START, checked)
                        .apply());
        addWithTop(behaviorCard, autoStart, 12);

        if (page == 2) addWithTop(root, behaviorCard, 16);
        behaviorCard = card();
        behaviorCard.addView(sectionTitle("Подключение ADB"));
        addWithTop(behaviorCard, text("Если ADB включается позже системы, лаунчер дождётся подключения и запустит помощники автоматически. Перезапускать приложение не нужно.",
                15, getColor(R.color.text_secondary)), 8);
        Switch adbRemote = settingsSwitch("Автозапуск DiLink Remote ADB",
                AppPreferences.get(host).getBoolean(AdbRemoteWatchdogService.KEY, false));
        adbRemote.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(host).edit().putBoolean(AdbRemoteWatchdogService.KEY, checked).apply();
            AdbRemoteWatchdogService.sync(host);
        });
        addWithTop(behaviorCard, adbRemote, 12);
        addWithTop(behaviorCard, text("Проверяет службу ADB Remote каждые 15 секунд и открывает приложение, если она остановилась. Повторная попытка — не чаще раза в две минуты. Работает на DiLink 2; отключение останавливает контроль.",
                14, getColor(R.color.text_secondary)), 6);

        if (page == 2) addWithTop(root, behaviorCard, 16);
        behaviorCard = card();
        behaviorCard.addView(sectionTitle("Режим проверки"));
        Switch demoMode = settingsSwitch(
                "Режим Android Emulator",
                AppPreferences.isDemoModeEnabled(host));
        demoMode.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(host).edit()
                    .putBoolean(AppPreferences.KEY_DEMO_MODE, checked)
                    .apply();
            Toast.makeText(host,
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
        if (page == 2) root.addView(behaviorCard, behaviorParams);
        LinearLayout updateCard = card();
        updateCard.addView(sectionTitle("Обновления"));
        Switch autoUpdate = settingsSwitch("Проверять обновления автоматически",
                AppPreferences.get(host).getBoolean(GithubUpdater.AUTO_KEY, true));
        autoUpdate.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(host).edit().putBoolean(GithubUpdater.AUTO_KEY, checked).apply());
        addWithTop(updateCard, autoUpdate, 10);
        Button checkUpdate = actionButton("Проверить обновления");
        checkUpdate.setOnClickListener(view -> GithubUpdater.check(host, true));
        addWithTop(updateCard, checkUpdate, 10);
        addWithTop(updateCard, text("Канал Seagull · ветка seagull · проверка раз в сутки. Установка с сохранением настроек после подтверждения.",
                14, getColor(R.color.text_secondary)), 6);
        LinearLayout.LayoutParams updateParams = fullWidthWrap();
        updateParams.topMargin = dp(16);
        if (page == 6) root.addView(updateCard, updateParams);
        return scroll;
    }

    private View createCameraPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(pageHeader("Камера парковки",
                "Название и адрес камеры, которая открывается кнопкой «Камеры»."));

        LinearLayout card = card();
        EditText name = input("Название камеры",
                AppPreferences.getParkingCameraName(host), false);
        card.addView(name, fullWidthWrap());
        EditText url = input("Адрес камеры",
                AppPreferences.getParkingCameraUrl(host), false);
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
                Toast.makeText(host, "Укажите адрес камеры", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!cameraUrl.contains("://")) cameraUrl = "https://" + cameraUrl;
            AppPreferences.get(host).edit()
                    .putString(AppPreferences.KEY_PARKING_CAMERA_NAME,
                            cameraName.isEmpty() ? "Парковка" : cameraName)
                    .putString(AppPreferences.KEY_PARKING_CAMERA_URL, cameraUrl)
                    .apply();
            Toast.makeText(host, "Камера сохранена", Toast.LENGTH_SHORT).show();
        });
        actions.addView(save, weightedButtonParams());
        Button test = actionButton("Открыть для проверки");
        test.setOnClickListener(view -> {
            save.performClick();
            if (!ParkingCameraOverlay.show(host)) {
                Toast.makeText(host,
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
                "Выберите места на карте и задайте радиус. Действия при въезде настраиваются в автоматизациях."));

        LinearLayout listCard = card();
        listCard.addView(sectionTitle("Геозоны"));
        List<CameraPlace> places = new ArrayList<>(AppPreferences.getCameraPlaces(host));
        if (places.isEmpty()) {
            addWithTop(listCard, text("Мест пока нет. Нажмите «Добавить место» и выберите точку на карте.", 15,
                    getColor(R.color.text_secondary)), 10);
        }
        for (int index = 0; index < places.size(); index++) {
            CameraPlace place = places.get(index);
            LinearLayout row = horizontalActions();
            TextView label = text(place.name + "  ·  "
                    + Math.round(place.radiusMeters) + " м\n"
                    + String.format(java.util.Locale.US, "%.6f, %.6f",
                    place.latitude, place.longitude), 15, getColor(R.color.text_primary));
            row.addView(label, new LinearLayout.LayoutParams(0, dp(58), 1f));
            Button edit = actionButton("На карте");
            edit.setOnClickListener(view -> PlaceMapDialog.show(host, place,
                    () -> refreshPlaces()));
            row.addView(edit, new LinearLayout.LayoutParams(dp(116), dp(48)));
            Button delete = actionButton("Удалить");
            delete.setOnClickListener(view -> {
                places.remove(place);
                AppPreferences.setCameraPlaces(host, places);
                row.setVisibility(View.GONE);
                Toast.makeText(host, "Место удалено", Toast.LENGTH_SHORT).show();
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
        PlaceMapDialog.show(host, null, () -> refreshPlaces());
    }

    private void refreshPlaces() { if (!closed) showSettingsPage(pageHost, navigation, 4); }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackground(rounded(getColor(R.color.background), 20));
        return scroll;
    }

    private LinearLayout pageRoot(ScrollView scroll) {
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(8), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View pageHeader(String titleValue, String description) {
        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(text(titleValue, 22, getColor(R.color.text_primary)));
        addWithTop(header, text(description, 15, getColor(R.color.text_secondary)), 6);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(16);
        header.setLayoutParams(params);
        return header;
    }

    private EditText input(String hint, String value, boolean numeric) {
        EditText input = new EditText(host);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(getColor(R.color.text_primary));
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
        host.runOnUiThread(() -> { if (!closed) handleCapturedSteeringKey(scanCode); });
    }

    private void handleCapturedSteeringKey(int scanCode) {
        if (steeringCaptureStage == 1) {
            capturedShortScan = scanCode;
            steeringCaptureStage = 2;
            steeringStatus.setText(
                    "Шаг 2 из 2: теперь нажмите и удерживайте "
                            + "эту же кнопку…");
            host.getWindow().getDecorView().postDelayed(() -> {
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
                host, capturedShortScan, scanCode);
        updateSteeringStatus();
        steeringAssignButton.setEnabled(false);
        steeringResetButton.setEnabled(false);
        host.getWindow().getDecorView().postDelayed(() -> {
            SteeringEventServer.cancelKeyCapture(this);
            restartSteeringHelper("Кнопка назначена");
        }, 1500);
    }

    private void requestNextSteeringKey() {
        if (closed) return;
        SteeringEventServer.beginKeyCapture(this);
        bridgeClient.captureNextSteeringKey(success -> {
            if (success) {
                return;
            }
            host.runOnUiThread(() -> {
                if (closed) return;
                SteeringEventServer.cancelKeyCapture(this);
                steeringCaptureStage = 0;
                updateSteeringStatus();
                Toast.makeText(host,
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
                host,
                AppPreferences.DEFAULT_STEERING_SHORT_SCAN,
                AppPreferences.DEFAULT_STEERING_LONG_SCAN);
        updateSteeringStatus();
        restartSteeringHelper("Назначение сброшено");
    }

    private void restartSteeringHelper(String successMessage) {
        if (closed) return;
        steeringAssignButton.setEnabled(false);
        steeringResetButton.setEnabled(false);
        bridgeClient.restartHelpers(success -> host.runOnUiThread(() -> {
            if (closed) return;
            updateSteeringControlsEnabled();
            Toast.makeText(host,
                    success ? successMessage
                            : "Коды сохранены, но помощник не перезапустился",
                    Toast.LENGTH_LONG).show();
        }));
    }

    private void updateSteeringStatus() {
        steeringStatus.setText(
                "Сейчас: короткое — scan "
                        + AppPreferences.getSteeringShortScan(host)
                        + ", долгое — scan "
                        + AppPreferences.getSteeringLongScan(host));
        steeringStatus.setTextColor(getColor(R.color.text_secondary));
        steeringAssignButton.setText("Назначить кнопку");
        updateSteeringControlsEnabled();
    }

    private void updateSteeringControlsEnabled() {
        boolean enabled = !AppPreferences.isDemoModeEnabled(host)
                && steeringCaptureStage == 0;
        steeringAssignButton.setEnabled(enabled);
        steeringResetButton.setEnabled(enabled);
    }

    private void setCompactPaneOnLeft(boolean onLeft) {
        AppPreferences.setCompactPaneOnLeft(host, onLeft);
        updateLayoutButtons();
        MainActivity.applyPanelLayoutFromSettings();
    }

    private void updateLayoutButtons() {
        boolean compactOnLeft = AppPreferences.isCompactPaneOnLeft(host);
        styleLayoutButton(oneTwoButton, compactOnLeft);
        styleLayoutButton(twoOneButton, !compactOnLeft);
        layoutHelp.setText(
                "Размеры зафиксированы: служебная область занимает ⅓, "
                        + "основное приложение — ⅔. Переключение меняет "
                        + "области местами без перезапуска приложений.");
    }

    private void styleLayoutButton(Button button, boolean selected) {
        button.setTextColor(getColor(R.color.text_primary));
        button.setBackground(rounded(
                getColor(selected ? R.color.accent : R.color.button), 14));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(getColor(R.color.card), 16));
        return card;
    }

    private TextView sectionTitle(String value) {
        return text(value, 19, getColor(R.color.text_primary));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        view.setTypeface(getResources().getFont(R.font.ui7_regular));
        view.setTextColor(color);
        view.setLineSpacing(0, 1.1f);
        return view;
    }

    private Button actionButton(String label) {
        Button button = new Button(host);
        button.setText(label);
        button.setTextColor(getColor(R.color.text_primary));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(rounded(getColor(R.color.button), 14));
        return button;
    }

    private Switch settingsSwitch(String label, boolean checked) {
        Switch view = new Switch(host);
        view.setText(label);
        view.setTextColor(getColor(R.color.text_primary));
        view.setTextSize(16);
        view.setChecked(checked);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private LinearLayout horizontalActions() {
        LinearLayout actions = new LinearLayout(host);
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
