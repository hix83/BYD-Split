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

final class SettingsView extends LinearLayout
        implements SteeringEventServer.KeyCaptureListener {
    private Button oneTwoButton;
    private Button twoOneButton;
    private Button steeringAssignButton;
    private Button steeringResetButton;
    private TextView layoutHelp;
    private TextView steeringStatus;
    private TextView adbRestoreStatus;
    private TextView musicGlowStatus;
    private Switch musicGlowSwitch;
    private LinearLayout settingsRoot;
    private ScrollView generalScroll;
    void refreshDockInsets() {
        boolean dynamic = DockConfiguration.dynamic(activity);
        if (settingsRoot != null) settingsRoot.setPadding(dp(18), dp(14), dp(18), dynamic ? 0 : dp(16));
        if (generalScroll != null) generalScroll.setPadding(0, dp(14), 0, dynamic ? 0 : dp(14));
    }
    void refreshMusicGlowSetting() {
        if (musicGlowSwitch != null) musicGlowSwitch.setChecked(MusicGlowView.enabled(activity));
    }
    private final Runnable refreshAdbRestore = new Runnable() {
        @Override public void run() {
            if (adbRestoreStatus != null) adbRestoreStatus.setText(AdbRecovery.get(getContext()).status());
            if (musicGlowStatus != null) musicGlowStatus.setText(MusicGlowView.status(getContext()));
            if (isAttachedToWindow()) postDelayed(this, 1000);
        }
    };
    private ShellBridgeClient bridgeClient;
    private int steeringCaptureStage;
    private int capturedShortScan;

    private final Activity activity;
    private final Runnable close;
    private int selectedPage;
    SettingsView(Activity activity, Runnable close, int page) {
        super(activity);
        this.activity = activity;
        this.close = close;
        selectedPage = page;
        bridgeClient = new ShellBridgeClient(activity);
        setOrientation(VERTICAL);
        setBackground(rounded(getColor(R.color.background), 14));
        setClipToOutline(true);
        setClickable(true);
        replaceContent(createContent());
    }
    int selectedPage() { return selectedPage; }
    private int getColor(int id) { return activity.getColor(id); }
    private void finish() { close.run(); }
    private void startActivity(android.content.Intent intent) { activity.startActivity(intent); }
    private void runOnUiThread(Runnable action) { activity.runOnUiThread(action); }
    private android.view.Window getWindow() { return activity.getWindow(); }
    private void replaceContent(View content) { removeAllViews(); addView(content, new LinearLayout.LayoutParams(-1,-1)); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (bridgeClient == null) bridgeClient = new ShellBridgeClient(activity);
        post(refreshAdbRestore);
    }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(refreshAdbRestore);
        SteeringEventServer.cancelKeyCapture(this);
        steeringCaptureStage = 0;
        ShellBridgeClient closingBridge = bridgeClient;
        bridgeClient = null;
        if (closingBridge != null) closingBridge.cancelSteeringKeyCapture(ignored -> closingBridge.close());
        super.onDetachedFromWindow();
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(activity);
        settingsRoot = root;
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(16));
        root.setBackground(rounded(getColor(R.color.background), 24));

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.VERTICAL);
        tabs.setGravity(Gravity.TOP);
        tabs.setPadding(0, dp(20), dp(12), 0);
        FrameLayout content = new FrameLayout(activity);
        String[] labels = {"Экран и тема", "Кнопка руля", "Музыка и MAX", "Камера", "Места", "Автоматизации", "Поездки и тарифы", "Система"};
        for (int index = 0; index < labels.length; index++) {
            Button tab = actionButton(labels[index]);
            tab.setTag(index);
            final int page = index;
            tab.setOnClickListener(view -> showSettingsPage(content, tabs, page));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(45));
            if (index > 0) params.topMargin = dp(4);
            tabs.addView(tab, params);
        }
        View space = new View(activity);
        space.setMinimumHeight(dp(20));
        tabs.addView(space, new LinearLayout.LayoutParams(-1, 0, 1f));
        View separator = new View(activity);
        separator.setBackgroundColor(getColor(R.color.text_secondary));
        separator.setAlpha(0.25f);
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(-1, dp(1));
        separatorParams.topMargin = dp(12);
        separatorParams.bottomMargin = dp(16);
        tabs.addView(separator, separatorParams);
        Button closeButton = actionButton("×  Закрыть настройки");
        closeButton.setContentDescription("Закрыть настройки и вернуться к приложению");
        closeButton.setTextColor(getColor(R.color.accent));
        closeButton.setMaxLines(2);
        GradientDrawable closeBackground = rounded(getColor(R.color.surface_light), 14);
        closeBackground.setStroke(dp(1), getColor(R.color.accent));
        closeButton.setBackground(closeBackground);
        closeButton.setOnClickListener(v -> finish());
        tabs.addView(closeButton, new LinearLayout.LayoutParams(-1, dp(56)));
        root.addView(tabs, new LinearLayout.LayoutParams(
                dp(160), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        contentParams.setMarginStart(dp(10));
        root.addView(content, contentParams);
        showSettingsPage(content, tabs, selectedPage);
        return root;
    }

    private void showSettingsPage(FrameLayout host, LinearLayout tabs, int page) {
        if (steeringCaptureStage != 0) {
            SteeringEventServer.cancelKeyCapture(this);
            bridgeClient.cancelSteeringKeyCapture(ignored -> {});
            steeringCaptureStage = 0;
        }
        selectedPage = page;
        host.removeAllViews();
        generalScroll = null;
        for (int index = 0; index < tabs.getChildCount(); index++) {
            if (!(tabs.getChildAt(index).getTag() instanceof Integer)) continue;
            ((TextView) tabs.getChildAt(index)).setTextColor(index == page ? Color.WHITE : ThemePalette.text(activity));
            tabs.getChildAt(index).setBackground(rounded(getColor(
                    index == page ? R.color.accent : R.color.button), 14));
        }
        View content = page == 3 ? createCameraPage() : page == 4 ? createPlacesPage()
                : page == 5 ? new TemperatureAutomationView(activity)
                : page == 6 ? new StatisticsSettingsView(activity)
                : createGeneralPage(page);
        host.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshDockInsets();
    }

    private View createGeneralPage(int page) {
        ScrollView scroll = new ScrollView(activity);
        generalScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(true);
        scroll.setPadding(0, dp(14), 0, dp(14));
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackground(rounded(getColor(R.color.background), 24));

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(6), dp(12), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(new String[]{"Экран и тема", "Кнопка руля", "Музыка и MAX", "", "", "", "", "Система"}[page], 22, ThemePalette.text(activity));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(0);
        header.addView(title, titleParams);
        root.addView(header);

        if (page == 0) {
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

        LinearLayout dockCard = card();
        dockCard.addView(sectionTitle("Нижняя панель"));
        Switch evStatus=settingsSwitch("Запас хода EV в статусе автомобиля",EvRangeController.showStatus(activity));
        evStatus.setOnCheckedChangeListener((button,enabled)->{AppPreferences.get(activity).edit().putBoolean(EvRangeController.SHOW_STATUS,enabled).apply();MainActivity.applyEvStatusFromSettings();});
        addWithTop(dockCard,evStatus,8);
        Button evRangeSettings=actionButton("Прогноз запаса хода EV");
        evRangeSettings.setOnClickListener(v->EvRangeSettings.show(activity));
        addWithTop(dockCard,evRangeSettings,8);
        Switch dynamicDock = settingsSwitch("Динамическая панель", DockConfiguration.dynamic(activity));
        dynamicDock.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(activity).edit().putBoolean(DockConfiguration.MODE, checked).apply();
            MainActivity.applyPanelLayoutFromSettings();
        });
        addWithTop(dockCard, dynamicDock, 8);
        addWithTop(dockCard, text("Выключено — классическая панель из пяти кнопок под служебной областью. Включено — панель на всю ширину и две области одинаковой высоты. Удерживайте кнопку или свободное место: панель перейдёт в режим редактирования, а над ней откроется библиотека. Добавляйте элементы из библиотеки, двигайте кнопки на панели; корзина возвращает элемент в библиотеку. От 2 до 20 кнопок; длинная панель прокручивается горизонтально.", 14, getColor(R.color.text_secondary)), 8);
        TextView dockHeightLabel = text("Высота панели: " + DockConfiguration.heightPercent(activity) + "%", 14, getColor(R.color.text_secondary));
        addWithTop(dockCard, dockHeightLabel, 12);
        android.widget.SeekBar dockHeight = new android.widget.SeekBar(activity);
        dockHeight.setMax((DockConfiguration.MAX_HEIGHT_PERCENT - DockConfiguration.MIN_HEIGHT_PERCENT) / 5);
        dockHeight.setProgress((DockConfiguration.heightPercent(activity) - DockConfiguration.MIN_HEIGHT_PERCENT) / 5);
        dockHeight.setContentDescription("Высота нижней панели и размер кнопок");
        dockHeight.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            public void onStopTrackingTouch(android.widget.SeekBar bar) {}
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean user) {
                if (!user) return;
                int percent = DockConfiguration.MIN_HEIGHT_PERCENT + value * 5;
                AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT, percent).apply();
                dockHeightLabel.setText("Высота панели: " + percent + "%");
                MainActivity.applyDockSizeFromSettings();
            }
        });
        addWithTop(dockCard, dockHeight, 4);
        addWithTop(dockCard, text("80–150%. Иконки, подписи и индикаторы масштабируются вместе с панелью. Действует для обоих видов панели; размер виден сразу.", 14, getColor(R.color.text_secondary)), 4);
        Button resetDockHeight = actionButton("Стандартная высота · 100%");
        resetDockHeight.setOnClickListener(view -> {
            AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT, 100).apply();
            dockHeight.setProgress((100 - DockConfiguration.MIN_HEIGHT_PERCENT) / 5);
            dockHeightLabel.setText("Высота панели: 100%");
            MainActivity.applyDockSizeFromSettings();
        });
        addWithTop(dockCard, resetDockHeight, 8);
        addWithTop(root, dockCard, 12);

        LinearLayout glowCard = card();
        glowCard.addView(sectionTitle("Светомузыка панели"));
        musicGlowSwitch = settingsSwitch("Эффекты под музыку", MusicGlowView.enabled(activity));
        musicGlowSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                musicGlowSwitch.setChecked(false);
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MusicGlowView.PERMISSION_REQUEST);
            } else AppPreferences.get(activity).edit().putBoolean(MusicGlowView.ENABLED, checked).apply();
        });
        addWithTop(glowCard, musicGlowSwitch, 8);
        addWithTop(glowCard, text("Свечение от нижнего края реагирует на аудиовыход Android. Иконки остаются неподвижными. Система запрашивает разрешение «Микрофон» для Visualizer; запись с микрофона не ведётся. Доступность звука зависит от прошивки и плеера.",14,getColor(R.color.text_secondary)),8);
        musicGlowStatus = text(MusicGlowView.status(activity),14,getColor(R.color.text_secondary));
        addWithTop(glowCard, musicGlowStatus, 8);
        addWithTop(glowCard,text("Эффект",14,getColor(R.color.text_secondary)),8);
        android.widget.GridLayout effectGrid=new android.widget.GridLayout(activity);effectGrid.setColumnCount(2);
        List<Button> effectButtons=new ArrayList<>();
        Runnable refreshEffects=()->{
            int selected=Math.max(0,Math.min(MusicGlowView.EFFECT_NAMES.length-1,AppPreferences.get(activity).getInt(MusicGlowView.EFFECT,0)));
            for(int i=0;i<effectButtons.size();i++){
                Button button=effectButtons.get(i);boolean chosen=i==selected;
                button.setText((chosen?"✓  ":"")+MusicGlowView.EFFECT_NAMES[i]);button.setSelected(chosen);
                button.setContentDescription("Эффект: "+MusicGlowView.EFFECT_NAMES[i]+(chosen?", выбран":""));
                GradientDrawable background=rounded(getColor(R.color.button),12);
                if(chosen)background.setStroke(dp(2),getColor(R.color.accent));button.setBackground(background);
            }
        };
        for(int i=0;i<MusicGlowView.EFFECT_NAMES.length;i++){
            final int selected=i;Button choice=actionButton("");choice.setTextSize(13);effectButtons.add(choice);
            choice.setOnClickListener(v->{AppPreferences.get(activity).edit().putInt(MusicGlowView.EFFECT,selected).apply();refreshEffects.run();});
            android.widget.GridLayout.LayoutParams p=new android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(i/2),android.widget.GridLayout.spec(i%2,1f));
            p.width=0;p.height=dp(44);p.setMargins(dp(3),dp(3),dp(3),dp(3));effectGrid.addView(choice,p);
        }
        refreshEffects.run();addWithTop(glowCard,effectGrid,4);
        TextView glowBrightness = text("Яркость",14,getColor(R.color.text_secondary));addWithTop(glowCard,glowBrightness,8);
        android.widget.SeekBar brightness = new android.widget.SeekBar(activity);
        brightness.setMax(100);brightness.setProgress(AppPreferences.get(activity).getInt(MusicGlowView.BRIGHTNESS,40));
        brightness.setContentDescription("Яркость светомузыки");
        brightness.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(android.widget.SeekBar bar){} public void onStopTrackingTouch(android.widget.SeekBar bar){}
            public void onProgressChanged(android.widget.SeekBar bar,int value,boolean user){if(user)AppPreferences.get(activity).edit().putInt(MusicGlowView.BRIGHTNESS,value).apply();}
        });addWithTop(glowCard,brightness,4);
        int initialHeight = Math.max(10,Math.min(80,AppPreferences.get(activity).getInt(MusicGlowView.HEIGHT,52)));
        TextView glowHeight = text("Высота эффекта: " + initialHeight + "%",14,getColor(R.color.text_secondary));
        addWithTop(glowCard,glowHeight,8);
        android.widget.SeekBar height = new android.widget.SeekBar(activity);
        height.setMax(70);height.setProgress(initialHeight-10);
        height.setContentDescription("Высота эффекта светомузыки");
        height.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(android.widget.SeekBar bar){} public void onStopTrackingTouch(android.widget.SeekBar bar){}
            public void onProgressChanged(android.widget.SeekBar bar,int value,boolean user){
                if(user){int percent=value+10;glowHeight.setText("Высота эффекта: "+percent+"%");
                    AppPreferences.get(activity).edit().putInt(MusicGlowView.HEIGHT,percent).apply();}
            }
        });addWithTop(glowCard,height,4);
        addWithTop(glowCard,text("Цвет эффекта",14,getColor(R.color.text_secondary)),8);
        android.widget.Switch ambientColor=settingsSwitch("Как атмосферная подсветка",AppPreferences.get(activity).getBoolean(MusicGlowView.AMBIENT,false));
        ambientColor.setOnCheckedChangeListener((button,checked)->AppPreferences.get(activity).edit().putBoolean(MusicGlowView.AMBIENT,checked).apply());
        addWithTop(glowCard,ambientColor,4);
        addWithTop(glowCard,text("Повторяет выбранный цвет передней зоны салона. При недоступности данных используется цвет ниже. Переливы и краткие реакции штатной подсветки требуют отдельной поддержки текущего цвета. Настройки подсветки автомобиля не изменяются.",14,getColor(R.color.text_secondary)),4);
        android.widget.GridLayout colorGrid = new android.widget.GridLayout(activity);colorGrid.setColumnCount(4);
        List<Button> colorButtons = new ArrayList<>();
        Button customColor = actionButton("");
        Runnable refreshColors = () -> {
            int selected=AppPreferences.get(activity).getInt(MusicGlowView.COLOR,0);
            boolean custom=true;for(int preset:MusicGlowView.COLORS)if(selected==preset)custom=false;
            int customValue=custom?selected:AppPreferences.get(activity).getInt(WaveColorDialog.CUSTOM_COLOR,0xff2682df);
            customColor.setText((custom?"✓  ":"")+"Свой цвет  ●");
            android.text.SpannableString customLabel=new android.text.SpannableString(customColor.getText());
            customLabel.setSpan(new android.text.style.ForegroundColorSpan(customValue),customLabel.length()-1,customLabel.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            customColor.setText(customLabel);
            customColor.setSelected(custom);
            GradientDrawable customBackground=rounded(getColor(R.color.button),14);
            if(custom)customBackground.setStroke(dp(2),selected);customColor.setBackground(customBackground);
            for(int i=0;i<colorButtons.size();i++){
                Button button=colorButtons.get(i);int color=MusicGlowView.COLORS[i];boolean chosen=color==selected;
                android.text.SpannableString label=new android.text.SpannableString((chosen?"✓ ":"● ")+MusicGlowView.COLOR_NAMES[i]);
                label.setSpan(new android.text.style.ForegroundColorSpan(color==0?getColor(R.color.accent):color),0,1,0);
                button.setText(label);button.setSelected(chosen);
                GradientDrawable background=rounded(getColor(R.color.button),12);
                if(chosen)background.setStroke(dp(2),color==0?getColor(R.color.accent):color);
                button.setBackground(background);button.setContentDescription("Цвет эффекта: "+MusicGlowView.COLOR_NAMES[i]+(chosen?", выбран":""));
            }
        };
        for(int i=0;i<MusicGlowView.COLORS.length;i++){
            final int color=MusicGlowView.COLORS[i];Button choice=actionButton("");choice.setTextSize(12);choice.setPadding(dp(4),0,dp(4),0);
            choice.setMinWidth(0);choice.setMinimumWidth(0);colorButtons.add(choice);
            choice.setOnClickListener(v->{AppPreferences.get(activity).edit().putInt(MusicGlowView.COLOR,color).apply();refreshColors.run();});
            android.widget.GridLayout.LayoutParams p=new android.widget.GridLayout.LayoutParams(android.widget.GridLayout.spec(i/4),android.widget.GridLayout.spec(i%4,1f));
            p.width=0;p.height=dp(42);p.setMargins(dp(3),dp(3),dp(3),dp(3));colorGrid.addView(choice,p);
        }
        refreshColors.run();addWithTop(glowCard,colorGrid,4);
        customColor.setOnClickListener(v->WaveColorDialog.show(activity,refreshColors));
        addWithTop(glowCard,customColor,8);
        Button previewGlow = actionButton("Предпросмотр · 6 секунд");
        previewGlow.setOnClickListener(v->{
            if(!MusicGlowView.enabled(activity))Toast.makeText(activity,"Сначала включите светомузыку",Toast.LENGTH_SHORT).show();
            else MusicGlowView.preview();
        });addWithTop(glowCard,previewGlow,8);
        addWithTop(root,glowCard,12);

        LinearLayout behaviorCard = card();
        behaviorCard.addView(sectionTitle("Оформление"));
        String[] modes = {"Как в DiLink", "Дневная", "Ночная"};
        int mode = AppPreferences.get(activity).getInt("theme_mode", 0);
        for (int i = 0; i < modes.length; i++) {
            final int choice = i;
            Button theme = actionButton((mode == i ? "✓  " : "") + modes[i]);
            theme.setOnClickListener(v -> {
                if (mode != choice) {
                    AppPreferences.get(activity).edit().putInt("theme_mode", choice).apply();
                    activity.recreate();
                }
            });
            addWithTop(behaviorCard, theme, 6);
        }
        Switch fullscreen = settingsSwitch(
                "Полноэкранный режим",
                AppPreferences.isFullscreenEnabled(activity));
        fullscreen.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(activity).edit()
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

        addWithTop(root, behaviorCard, 12);
        } else if (page == 1) {
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

        } else if (page == 2) {
        LinearLayout behaviorCard = card();
        behaviorCard.addView(sectionTitle("Музыка на приборной панели"));
        Switch clusterMusic = settingsSwitch("Трек Яндекса на приборной панели",
                AppPreferences.get(activity).getBoolean(ClusterMusicBridge.KEY, false));
        clusterMusic.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(activity).edit().putBoolean(ClusterMusicBridge.KEY, checked).apply());
        addWithTop(behaviorCard, clusterMusic, 12);
        addWithTop(behaviorCard, text("Название, исполнитель и прогресс Яндекс Музыки в штатной карточке приборной панели. Требуется доступ к уведомлениям и подключение ADB.",
                14, getColor(R.color.text_secondary)), 6);

        android.widget.Button mediaAccess = actionButton("Доступ к уведомлениям");
        mediaAccess.setOnClickListener(view -> startActivity(new android.content.Intent(
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        addWithTop(behaviorCard, mediaAccess, 8);

        addWithTop(behaviorCard, text("Приложения музыки и сообщений выбираются долгим нажатием соответствующей кнопки нижнего меню. Кнопка руля управляет голосовыми сообщениями MAX в открытом чате.", 15, getColor(R.color.text_secondary)), 14);
        root.addView(behaviorCard, fullWidthWrap());
        } else {
        LinearLayout behaviorCard = card();
        behaviorCard.addView(sectionTitle("Запуск и диагностика"));
        Switch autoStart = settingsSwitch(
                "Автозапуск после загрузки DiLink",
                AppPreferences.isAutoStartEnabled(activity));
        autoStart.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(activity).edit()
                        .putBoolean(AppPreferences.KEY_AUTO_START, checked)
                        .apply());
        addWithTop(behaviorCard, autoStart, 12);

        Switch demoMode = settingsSwitch(
                "Режим Android Emulator",
                AppPreferences.isDemoModeEnabled(activity));
        demoMode.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(activity).edit()
                    .putBoolean(AppPreferences.KEY_DEMO_MODE, checked)
                    .apply();
            Toast.makeText(activity,
                    checked
                            ? "BYD-функции отключены, реальные приложения сохранены"
                            : "Режим DiLink включён",
                    Toast.LENGTH_SHORT).show();
            updateSteeringControlsEnabled();
            StatisticsService.sync(activity);
            BatteryWatchService.sync(activity);
            activity.recreate();
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
        LinearLayout adbCard = card();
        adbCard.addView(sectionTitle("Восстановление ADB после перезагрузки"));
        AdbRecovery recovery = AdbRecovery.get(activity);
        Switch restore = settingsSwitch("Автоматически восстанавливать ADB", recovery.enabled());
        restore.setEnabled(AdbRecovery.supported());
        restore.setOnCheckedChangeListener((button, checked) -> recovery.setEnabled(checked));
        addWithTop(adbCard, restore, 10);
        adbRestoreStatus = text(recovery.status(), 14, getColor(R.color.text_secondary));
        addWithTop(adbCard, adbRestoreStatus, 8);
        addWithTop(adbCard, text("Для новых прошивок DiLink, где после загрузки закрывается ADB (в BYDMate — 2606+). Требуются Android 11+ и подключение к Wi-Fi. Пока обычный ADB доступен, включите функцию: приложение подготовит системное разрешение. На новой сети подтвердите системное окно отладки по Wi-Fi. В деморежиме функция не выполняется.", 14, getColor(R.color.text_secondary)), 8);
        Button retryRestore = actionButton("Повторить проверку / активацию");
        retryRestore.setEnabled(AdbRecovery.supported());
        retryRestore.setOnClickListener(v -> {
            if (!recovery.enabled()) Toast.makeText(activity, "Сначала включите восстановление ADB", Toast.LENGTH_SHORT).show();
            else recovery.retry();
        });
        addWithTop(adbCard, retryRestore, 10);
        addWithTop(root, adbCard, 16);
        LinearLayout updateCard = card();
        updateCard.addView(sectionTitle("Обновления"));
        Switch autoUpdate = settingsSwitch("Проверять обновления автоматически",
                AppPreferences.get(activity).getBoolean(GithubUpdater.AUTO_KEY, true));
        autoUpdate.setOnCheckedChangeListener((button, checked) ->
                AppPreferences.get(activity).edit().putBoolean(GithubUpdater.AUTO_KEY, checked).apply());
        addWithTop(updateCard, autoUpdate, 10);
        Button checkUpdate = actionButton("Проверить обновления");
        checkUpdate.setOnClickListener(view -> GithubUpdater.check(activity, true));
        addWithTop(updateCard, checkUpdate, 10);
        addWithTop(updateCard, text("GitHub Releases · проверка раз в сутки. Установка с сохранением настроек после подтверждения.",
                14, getColor(R.color.text_secondary)), 6);
        LinearLayout.LayoutParams updateParams = fullWidthWrap();
        updateParams.topMargin = dp(16);
        root.addView(updateCard, updateParams);
        }
        return scroll;
    }

    private View createCameraPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(pageHeader("Камера парковки",
                "Интернет-сервис парковочной камеры из BYD Mate DM."));

        LinearLayout card = card();
        EditText name = input("Название камеры",
                AppPreferences.getParkingCameraName(activity), false);
        card.addView(text("Название камеры", 14, getColor(R.color.text_secondary)));
        card.addView(name, fullWidthWrap());
        EditText url = input("Адрес камеры",
                AppPreferences.getParkingCameraUrl(activity), false);
        addWithTop(card, text("Адрес камеры", 14, getColor(R.color.text_secondary)), 10);
        addWithTop(card, url, 4);
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
                Toast.makeText(activity, "Укажите адрес камеры", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!cameraUrl.contains("://")) cameraUrl = "https://" + cameraUrl;
            AppPreferences.get(activity).edit()
                    .putString(AppPreferences.KEY_PARKING_CAMERA_NAME,
                            cameraName.isEmpty() ? "Парковка" : cameraName)
                    .putString(AppPreferences.KEY_PARKING_CAMERA_URL, cameraUrl)
                    .apply();
            Toast.makeText(activity, "Камера сохранена", Toast.LENGTH_SHORT).show();
        });
        actions.addView(save, weightedButtonParams());
        Button test = actionButton("Открыть для проверки");
        test.setOnClickListener(view -> {
            save.performClick();
            if (!ParkingCameraOverlay.show(activity)) {
                Toast.makeText(activity,
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
        List<CameraPlace> places = new ArrayList<>(AppPreferences.getCameraPlaces(activity));
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
                    place.latitude, place.longitude), 15, ThemePalette.text(activity));
            row.addView(label, new LinearLayout.LayoutParams(0, dp(58), 1f));
            Button edit = actionButton("На карте");
            edit.setOnClickListener(view -> PlaceMapDialog.show(activity, place,
                    () -> replaceContent(createContentForPlaces())));
            row.addView(edit, new LinearLayout.LayoutParams(dp(116), dp(48)));
            Button delete = actionButton("Удалить");
            delete.setOnClickListener(view -> {
                places.remove(place);
                AppPreferences.setCameraPlaces(activity, places);
                row.setVisibility(View.GONE);
                Toast.makeText(activity, "Место удалено", Toast.LENGTH_SHORT).show();
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
        PlaceMapDialog.show(activity, null, () -> replaceContent(createContentForPlaces()));
    }

    private View createContentForPlaces() {
        View root = createContent();
        LinearLayout layout = (LinearLayout) root;
        showSettingsPage((FrameLayout) layout.getChildAt(1), (LinearLayout) layout.getChildAt(0), 4);
        return root;
    }

    private void fillCurrentLocation(EditText latitude, EditText longitude) {
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 342);
            Toast.makeText(activity, "Разрешите геопозицию и нажмите ещё раз",
                    Toast.LENGTH_LONG).show();
            return;
        }
        LocationManager manager = (LocationManager) activity.getSystemService(android.content.Context.LOCATION_SERVICE);
        Location location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (location == null) {
            Toast.makeText(activity, "Текущая геопозиция пока недоступна",
                    Toast.LENGTH_LONG).show();
            return;
        }
        latitude.setText(String.valueOf(location.getLatitude()));
        longitude.setText(String.valueOf(location.getLongitude()));
    }

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackground(rounded(getColor(R.color.background), 20));
        return scroll;
    }

    private LinearLayout pageRoot(ScrollView scroll) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private View pageHeader(String titleValue, String description) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(text(titleValue, 27, ThemePalette.text(activity)));
        addWithTop(header, text(description, 15, getColor(R.color.text_secondary)), 6);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(16);
        header.setLayoutParams(params);
        return header;
    }

    private EditText input(String hint, String value, boolean numeric) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setText(value);
        input.setTextColor(ThemePalette.text(activity));
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
                activity, capturedShortScan, scanCode);
        updateSteeringStatus();
        steeringAssignButton.setEnabled(false);
        steeringResetButton.setEnabled(false);
        getWindow().getDecorView().postDelayed(() -> {
            if (!isAttachedToWindow() || bridgeClient == null) return;
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
                Toast.makeText(activity,
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
                activity,
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
            Toast.makeText(activity,
                    success ? successMessage
                            : "Коды сохранены, но помощник не перезапустился",
                    Toast.LENGTH_LONG).show();
        }));
    }

    private void updateSteeringStatus() {
        steeringStatus.setText(
                "Сейчас: короткое — scan "
                        + AppPreferences.getSteeringShortScan(activity)
                        + ", долгое — scan "
                        + AppPreferences.getSteeringLongScan(activity));
        steeringStatus.setTextColor(getColor(R.color.text_secondary));
        steeringAssignButton.setText("Назначить кнопку");
        updateSteeringControlsEnabled();
    }

    private void updateSteeringControlsEnabled() {
        boolean enabled = !AppPreferences.isDemoModeEnabled(activity)
                && steeringCaptureStage == 0;
        if (steeringAssignButton == null) return;
        steeringAssignButton.setEnabled(enabled);
        steeringResetButton.setEnabled(enabled);
    }

    private void setCompactPaneOnLeft(boolean onLeft) {
        AppPreferences.setCompactPaneOnLeft(activity, onLeft);
        updateLayoutButtons();
        MainActivity.applyPanelLayoutFromSettings();
    }

    private void updateLayoutButtons() {
        boolean compactOnLeft = AppPreferences.isCompactPaneOnLeft(activity);
        styleLayoutButton(oneTwoButton, compactOnLeft);
        styleLayoutButton(twoOneButton, !compactOnLeft);
        layoutHelp.setText(
                "Размеры зафиксированы: служебная область занимает ⅓, "
                        + "основное приложение — ⅔. Переключение меняет "
                        + "области местами без перезапуска приложений.");
    }

    private void styleLayoutButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : getColor(R.color.text_primary));
        button.setBackground(rounded(
                getColor(selected ? R.color.accent : R.color.button), 14));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(rounded(getColor(R.color.surface_light), 20));
        return card;
    }

    private TextView sectionTitle(String value) {
        return text(value, 21, ThemePalette.text(activity));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.1f);
        return view;
    }

    private Button actionButton(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(ThemePalette.text(activity));
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(rounded(getColor(R.color.button), 14));
        return button;
    }

    private Switch settingsSwitch(String label, boolean checked) {
        Switch view = new Switch(activity);
        view.setText(label);
        view.setTextColor(ThemePalette.text(activity));
        view.setTextSize(16);
        view.setChecked(checked);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }

    private LinearLayout horizontalActions() {
        LinearLayout actions = new LinearLayout(activity);
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
