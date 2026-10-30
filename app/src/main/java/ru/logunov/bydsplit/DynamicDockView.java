package ru.logunov.bydsplit;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** The actual dock moves into the modal editing layer; the underlying pane bounds stay fixed. */
final class DynamicDockView extends FrameLayout {
    static final int HEIGHT_DP = 94;
    interface Host { void activate(DockConfiguration.Item item); }
    private final Activity activity;
    private final Host host;
    private final ComfortController controller;
    private final List<Runnable> bindings = new ArrayList<>();
    private final Runnable update = () -> { for (Runnable binding : bindings) binding.run(); };
    private final java.util.EnumSet<DockConfiguration.Item> navigation = java.util.EnumSet.noneOf(DockConfiguration.Item.class);
    private boolean listening, editorOpen;
    private int heightPercent;
    private Dialog editorDialog;
    private List<DockConfiguration.Item> draft;
    private int draftAlignment;
    private GridLayout library;
    private TextView count;
    private HorizontalScrollView dockScroll;
    private LinearLayout dockRow;
    private MusicGlowView musicGlow;
    private int scrollDirection;
    private DockConfiguration.Item draggedItem;
    private int insertionIndex = -1;
    private boolean dropAccepted;
    private static final long REORDER_DURATION = 190;
    private final Runnable finishReorder = () -> { if (editorOpen) refreshEditor(); };
    private final android.util.SparseArray<Bitmap> croppedIcons = new android.util.SparseArray<>();
    private final Runnable edgeScroll = new Runnable() {
        @Override public void run() {
            if (scrollDirection == 0 || !editorOpen) return;
            dockScroll.smoothScrollBy(dp(16) * scrollDirection, 0);
            postDelayed(this, 40);
        }
    };

    DynamicDockView(Activity activity, Host host) {
        super(activity); this.activity = activity; this.host = host;
        controller = ComfortController.get(activity);
        heightPercent = DockConfiguration.heightPercent(activity);
        setPadding(sd(6), sd(7), sd(6), sd(7));
        setClipToPadding(false);
        setClipToOutline(true);
        rebuild();
    }

    int heightDp() { return Math.round(HEIGHT_DP * heightPercent / 100f); }
    void refreshSize() {
        int value = DockConfiguration.heightPercent(activity);
        if (value == heightPercent || editorOpen) return;
        heightPercent = value;
        setPadding(sd(6), sd(7), sd(6), sd(7));
        rebuild();
    }

    void setNavigation(boolean song, boolean camera, boolean music, boolean messages, boolean settings, boolean phone, String page) {
        navigation.clear();
        if (song) navigation.add(DockConfiguration.Item.SONG);
        if (camera) navigation.add(DockConfiguration.Item.CAMERA);
        if (music) navigation.add(DockConfiguration.Item.MUSIC);
        if (messages) navigation.add(DockConfiguration.Item.MESSAGES);
        if (settings) navigation.add(DockConfiguration.Item.SETTINGS);
        if (phone) navigation.add(DockConfiguration.Item.PHONE);
        if ("climate".equals(page)) navigation.add(DockConfiguration.Item.CLIMATE);
        if ("seats".equals(page)) navigation.add(DockConfiguration.Item.SEATS);
        update.run();
    }

    void setActive(boolean active) {
        active = active && getVisibility() == VISIBLE && !editorOpen
                && (AppPreferences.isDemoModeEnabled(getContext())||VehicleSleepController.get(getContext()).pollingAllowed());
        if (musicGlow != null) musicGlow.setActive(active);
        for(EvRangeWidget widget:evWidgets)widget.setActive(active);
        if (active == listening) return;
        listening = active;
        if (active) controller.addListener(update); else controller.removeListener(update);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); setActive(true); }
    @Override protected void onDetachedFromWindow() { stopEdgeScroll(); setActive(false); super.onDetachedFromWindow(); }
    private int gravity(int alignment) { return alignment == 0 ? Gravity.LEFT : alignment == 2 ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL; }

    private final java.util.List<EvRangeWidget> evWidgets=new java.util.ArrayList<>();
    private void rebuild() {
        for(EvRangeWidget widget:evWidgets)widget.setActive(false);evWidgets.clear();
        removeCallbacks(finishReorder);
        draggedItem = null; insertionIndex = -1;
        int position = dockScroll == null ? 0 : dockScroll.getScrollX();
        stopEdgeScroll(); removeAllViews(); bindings.clear();
        setBackground(shape(ThemePalette.color(activity, 0xff1d252e, 0xfff8f9fb), editorOpen ? activity.getColor(R.color.accent) : 0));
        dockScroll = new HorizontalScrollView(activity);
        dockScroll.setFillViewport(true); dockScroll.setHorizontalScrollBarEnabled(editorOpen);
        dockRow = new LinearLayout(activity);
        dockRow.setGravity(Gravity.CENTER_VERTICAL | gravity(editorOpen ? draftAlignment : AppPreferences.get(activity).getInt(DockConfiguration.ALIGN, 1)));
        dockRow.setOnLongClickListener(v -> { edit(); return true; });
        dockScroll.setOnLongClickListener(v -> { edit(); return true; });
        setOnLongClickListener(v -> { edit(); return true; });
        List<DockConfiguration.Item> items = editorOpen ? draft : DockConfiguration.read(activity);
        for (DockConfiguration.Item item : items) {
            FrameLayout cell = new FrameLayout(activity);
            cell.setTag(item);
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
            tile.setPadding(sd(3), 0, sd(3), 0);
            ImageView icon = icon(item);
            if (item.ordinal() > DockConfiguration.Item.SETTINGS.ordinal())
                icon.setPadding(sd(4), sd(4), sd(4), sd(4));
            tile.addView(icon, new LinearLayout.LayoutParams(sd(40), sd(40)));
            TextView caption = text(item.caption(), 10); caption.setTextSize(10 * heightPercent / 100f); caption.setGravity(Gravity.CENTER); caption.setSingleLine(true);
            DockLevelIndicator levels = new DockLevelIndicator();
            if (item.field != null && !editorOpen) {
                tile.addView(levels, new LinearLayout.LayoutParams(sd(52), sd(8)));
            }
            tile.addView(caption, new LinearLayout.LayoutParams(-1, sd(item.field == null || editorOpen ? 29 : 21)));
            cell.addView(tile, new FrameLayout.LayoutParams(-1, -1));
            if(item==DockConfiguration.Item.EV_RANGE){
                tile.removeAllViews();EvRangeWidget widget=new EvRangeWidget(activity);
                tile.addView(widget,new LinearLayout.LayoutParams(-1,-1));evWidgets.add(widget);widget.setActive(listening&&!editorOpen);
            }
            cell.setOnLongClickListener(v -> {
                if (!editorOpen) edit();
                else v.startDragAndDrop(ClipData.newPlainText("dock", item.name()), new View.DragShadowBuilder(v), item, 0);
                return true;
            });
            cell.setOnClickListener(v -> { if (!editorOpen) { if (item.field == null) host.activate(item); else command(item); } });
            if (editorOpen) {
                cell.setContentDescription("Переместить: " + item.title);
                cell.setBackground(shape(ThemePalette.color(activity, 0xff2b3743, 0xffe8eef5), 0));
                icon.setColorFilter(ThemePalette.text(activity));
                if (!item.required()) {
                    ImageButton remove = new ImageButton(activity);
                    remove.setImageResource(R.drawable.song_oem_dock_delete);
                    remove.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    remove.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    remove.setPadding(0, 0, 0, 0);
                    GradientDrawable badgeMask = shape(Color.WHITE, 0); badgeMask.setCornerRadius(dp(20));
                    remove.setBackgroundTintList(null);
                    remove.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff), null, badgeMask));
                    remove.setContentDescription("Удалить: " + item.title);
                    FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(sd(40), sd(40), Gravity.TOP | Gravity.RIGHT);
                    cell.addView(remove, badge);
                    remove.setOnClickListener(v -> { draft.remove(item); refreshEditor(); if(item==DockConfiguration.Item.EV_RANGE)library.post(()->((ScrollView)library.getParent()).smoothScrollTo(0,0)); });
                }
                cell.setOnDragListener((v, event) -> handleDrag(v, event, item));
            } else {
                bindings.add(() -> {
                    Integer raw = item.field == null || !controller.available() ? null : controller.current(item.field);
                    boolean available = item.field == null || controller.available() && raw != null;
                    boolean on = raw != null && (item.field.seat() ? raw > 1 : item.field == ComfortState.Field.AUTO ? raw == 0 : raw == 1);
                    if (item.field == null) on = navigation.contains(item);
                    if (item.field == ComfortState.Field.FAN) on = raw != null && raw > 0;
                    boolean pending = item.field != null && controller.pending(item.field);
                    boolean heating = item.field == ComfortState.Field.DRIVER_HEAT || item.field == ComfortState.Field.PASSENGER_HEAT
                            || item.field == ComfortState.Field.WHEEL || item.field == ComfortState.Field.REAR || item.field == ComfortState.Field.FRONT;
                    int activeColor = pending ? ThemePalette.color(activity, 0xffffc25b, 0xff916000)
                            : heating ? ThemePalette.color(activity, 0xffffa06b, 0xffb7460e) : activity.getColor(R.color.accent);
                    if(item==DockConfiguration.Item.CYCLE)setIcon(icon,raw!=null&&raw==1?R.drawable.song_oem_internal_cycle_mode_had:R.drawable.song_oem_out_cycle_mode_had,true);
                    icon.setColorFilter(on || pending ? activeColor : ThemePalette.text(activity));
                    cell.setAlpha(available ? 1 : .6f); cell.setSelected(on);
                    String state = raw == null ? "нет данных" : item.field.seat() ? "уровень " + (raw - 1) : item.field == ComfortState.Field.FAN ? "скорость " + raw : on ? "включено" : "выключено";
                    if(item==DockConfiguration.Item.CYCLE&&raw!=null)state=raw==1?"рециркуляция в салоне":"забор воздуха снаружи";
                    caption.setText(item.caption());
                    if (item.field != null) {
                        int max = item.field == ComfortState.Field.FAN ? 7 : item.field.seat() && item.field != ComfortState.Field.WHEEL ? 2 : 1;
                        int level = raw == null ? -1 : item.field.seat() ? raw - 1 : item.field == ComfortState.Field.FAN ? raw : on ? 1 : 0;
                        levels.update(level, max, activeColor);
                    }
                    cell.setContentDescription(item.title + (item.field == null ? "" : ", " + (pending ? "ожидаем подтверждения, " : "") + state));
                    int background = item.field == null && on
                            ? ThemePalette.color(activity, 0xff203c50, 0xffdeeffb) : Color.TRANSPARENT;
                    cell.setBackground(shape(background, 0));
                });
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(sd(90) * item.slots, -1);
            if (editorOpen) p.setMargins(dp(2), 0, dp(2), 0);
            dockRow.addView(cell, p);
        }
        if (editorOpen) dockRow.setOnDragListener((v, event) -> handleDrag(v, event, null));
        dockScroll.addView(dockRow, new HorizontalScrollView.LayoutParams(-2, -1));
        musicGlow = new MusicGlowView(activity);
        addView(musicGlow, new FrameLayout.LayoutParams(-1, -1));
        musicGlow.setActive(listening && !editorOpen);
        addView(dockScroll, new FrameLayout.LayoutParams(-1, -1));
        dockScroll.post(() -> dockScroll.scrollTo(position, 0)); update.run();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (musicGlow != null) musicGlow.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (musicGlow != null) musicGlow.layout(0, 0, getWidth(), getHeight());
    }

    private boolean handleDrag(View target, DragEvent event, DockConfiguration.Item targetItem) {
        if (!editorOpen || !(event.getLocalState() instanceof DockConfiguration.Item)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (draggedItem == null) {
                    removeCallbacks(finishReorder);
                    draggedItem = (DockConfiguration.Item) event.getLocalState();
                    insertionIndex = draft.indexOf(draggedItem); dropAccepted = false;
                    for (int i = 0; i < dockRow.getChildCount(); i++) {
                        View cell = dockRow.getChildAt(i); cell.animate().cancel();
                        if (cell.getTag() == draggedItem) cell.setAlpha(.2f);
                        cell.setBackground(shape(ThemePalette.color(activity, 0xff243b50, 0xffe1effc), activity.getColor(R.color.accent)));
                    }
                }
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                previewInsertion(target, event);
                int[] at = new int[2], bounds = new int[2]; target.getLocationOnScreen(at); dockScroll.getLocationOnScreen(bounds);
                float x = at[0] + event.getX() - bounds[0];
                int direction = x < dp(42) ? -1 : x > dockScroll.getWidth() - dp(42) ? 1 : 0;
                if (direction != scrollDirection) { stopEdgeScroll(); scrollDirection = direction; if (direction != 0) post(edgeScroll); }
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                stopEdgeScroll(); return true;
            case DragEvent.ACTION_DROP:
                previewInsertion(target, event);
                if (draggedItem == null) return false;
                draft.remove(draggedItem); draft.add(Math.max(0, Math.min(insertionIndex, draft.size())), draggedItem);
                dropAccepted = true; return true;
            case DragEvent.ACTION_DRAG_ENDED:
                stopEdgeScroll();
                if (draggedItem != null) {
                    for (int i = 0; i < dockRow.getChildCount(); i++) {
                        View cell = dockRow.getChildAt(i);
                        if (!dropAccepted) cell.animate().translationX(0);
                        cell.animate().alpha(1).setDuration(REORDER_DURATION)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator());
                    }
                    draggedItem = null;
                    postDelayed(finishReorder, REORDER_DURATION);
                }
                return true;
            default: return true;
        }
    }
    /** Keep hit-test slots fixed while neighbours slide, so animations cannot oscillate the target. */
    private void previewInsertion(View target, DragEvent event) {
        if (draggedItem == null || dockRow.getChildCount() == 0) return;
        int[] at = new int[2], rowAt = new int[2];
        target.getLocationOnScreen(at); dockRow.getLocationOnScreen(rowAt);
        float x = at[0] + event.getX() - rowAt[0];
        int index = 0;
        for (int i = 0; i < dockRow.getChildCount() - 1; i++) {
            View cell = dockRow.getChildAt(i), next = dockRow.getChildAt(i + 1);
            float boundary = (cell.getLeft() + cell.getWidth() / 2f + next.getLeft() + next.getWidth() / 2f) / 2;
            if (x >= boundary) index = i + 1; else break;
        }
        if (index == insertionIndex) return;
        insertionIndex = index;
        List<DockConfiguration.Item> order = new ArrayList<>(draft);
        order.remove(draggedItem); order.add(insertionIndex, draggedItem);
        java.util.Map<DockConfiguration.Item,Float> positions=new java.util.EnumMap<>(DockConfiguration.Item.class);
        float cursor=dockRow.getChildAt(0).getLeft();
        for(DockConfiguration.Item item:order){
            View original=dockRow.getChildAt(draft.indexOf(item));
            LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)original.getLayoutParams();
            positions.put(item,cursor);cursor+=original.getWidth()+params.leftMargin+params.rightMargin;
        }
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View cell = dockRow.getChildAt(i);
            float translation = positions.get((DockConfiguration.Item)cell.getTag()) - cell.getLeft();
            cell.animate().translationX(translation).setDuration(REORDER_DURATION)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator());
        }
    }
    private void stopEdgeScroll() { scrollDirection = 0; removeCallbacks(edgeScroll); }

    private final class DockLevelIndicator extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(3);
        private int level = -1, maximum = 1, color;
        DockLevelIndicator() { super(activity); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void update(int level, int maximum, int color) { this.level = level; this.maximum = maximum; this.color = color; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float gap = sd(3), width = Math.min(sd(18), (getWidth()-gap*(maximum-1))/maximum);
            float start = (getWidth()-width*maximum-gap*(maximum-1))/2;
            for (int i=0; i<maximum; i++) {
                paint.setColor(i < level ? color : activity.getColor(R.color.text_secondary)); paint.setAlpha(i < level ? 255 : 65);
                float x = start+i*(width+gap); canvas.drawRoundRect(x, sd(2), x+width, sd(6), sd(2), sd(2), paint);
            }
        }
    }

    private void command(DockConfiguration.Item item) {
        Integer raw = controller.current(item.field);
        if (!controller.available() || raw == null) { Toast.makeText(activity, "Нет данных автомобиля для этой команды", Toast.LENGTH_SHORT).show(); return; }
        int value;
        if (item == DockConfiguration.Item.FAN_UP || item == DockConfiguration.Item.FAN_DOWN) {
            value = raw + (item == DockConfiguration.Item.FAN_UP ? 1 : -1); if (value < 0 || value > 7) return;
        } else value = controller.nextUiValue(item.field);
        controller.request(item.field, value);
    }

    private void refreshEditor() { if (editorOpen) { rebuild(); refreshLibrary(); } }
    private void refreshLibrary() {
        library.removeAllViews(); count.setText("На панели: " + draft.size() + " / 20");
        int index = 0;
        List<DockConfiguration.Item> catalog=new ArrayList<>(java.util.Arrays.asList(DockConfiguration.Item.values()));
        catalog.remove(DockConfiguration.Item.EV_RANGE);catalog.add(0,DockConfiguration.Item.EV_RANGE);
        for (DockConfiguration.Item item : catalog) {
            if (draft.contains(item)) continue;
            LinearLayout choice = new LinearLayout(activity); choice.setOrientation(LinearLayout.VERTICAL);
            choice.setGravity(Gravity.CENTER); choice.setPadding(dp(5), dp(5), dp(5), dp(5));
            choice.setBackground(shape(ThemePalette.color(activity, 0xff2b3743, 0xffe8eef5), 0));
            choice.setContentDescription("Добавить: " + item.title);
            ImageView image = icon(item); image.setColorFilter(ThemePalette.text(activity));
            choice.addView(image, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView label = text(item.caption(), 12); label.setGravity(Gravity.CENTER); label.setSingleLine(true);
            choice.addView(label, new LinearLayout.LayoutParams(-1, -2));
            choice.setEnabled(draft.size() < DockConfiguration.MAX); choice.setAlpha(choice.isEnabled() ? 1 : .4f);
            choice.setOnClickListener(v -> { if (draft.size() < DockConfiguration.MAX) { draft.add(item); refreshEditor(); dockScroll.post(() -> dockScroll.fullScroll(View.FOCUS_RIGHT)); } });
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(index / 6), GridLayout.spec(index % 6, 1f));
            p.width = 0; p.height = dp(78); p.setMargins(dp(3), dp(3), dp(3), dp(3)); library.addView(choice, p); index++;
        }
    }

    private void edit() {
        if (editorOpen) return;
        ViewGroup original = (ViewGroup) getParent(); if (original == null) return;
        editorOpen = true; setActive(false);
        draft = new ArrayList<>(DockConfiguration.read(activity));
        draftAlignment = Math.max(0, Math.min(2, AppPreferences.get(activity).getInt(DockConfiguration.ALIGN, 1)));
        int originalIndex = original.indexOfChild(this);
        ViewGroup.LayoutParams originalParams = getLayoutParams();
        View placeholder = new View(activity);
        original.removeView(this); original.addView(placeholder, originalIndex, originalParams);
        Dialog dialog = new Dialog(activity); editorDialog = dialog; dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout layer = new FrameLayout(activity); layer.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, dp(heightDp()), Gravity.BOTTOM);
        panelParams.setMargins(dp(8), 0, dp(8), dp(8)); layer.addView(this, panelParams);
        LinearLayout card = new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(shape(ThemePalette.color(activity, 0xff1d252e, 0xfff8f9fb), 0)); card.setElevation(dp(12));
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Библиотека кнопок и виджетов", 20), new LinearLayout.LayoutParams(0, -2, 1));
        Button cancel = action("Отмена", false, () -> closeEditor(false)); Button save = action("Готово", true, () -> closeEditor(true));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(100), dp(42));
        cancelParams.setMarginStart(dp(14)); header.addView(cancel, cancelParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(100), dp(42));
        saveParams.setMarginStart(dp(8)); header.addView(save, saveParams); card.addView(header);
        card.addView(text("Нажмите элемент библиотеки, чтобы добавить. Перетаскивайте кнопки на панели. Корзина возвращает кнопку в библиотеку.", 13));
        LinearLayout options = new LinearLayout(activity); options.setGravity(Gravity.CENTER_VERTICAL);
        count = text("", 14); options.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        Spinner alignment = new Spinner(activity);
        alignment.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, new String[]{"Кнопки слева", "Кнопки по центру", "Кнопки справа"}));
        alignment.setSelection(draftAlignment); alignment.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if (editorOpen && position != draftAlignment) { draftAlignment = position; rebuild(); } }
        }); options.addView(alignment); card.addView(options);
        ScrollView scroll = new ScrollView(activity); library = new GridLayout(activity); library.setColumnCount(6); scroll.addView(library);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(32), dp(1050));
        int height = Math.min(dp(330), Math.max(dp(190), activity.getResources().getDisplayMetrics().heightPixels - dp(heightDp() + 40)));
        FrameLayout.LayoutParams libraryParams = new FrameLayout.LayoutParams(width, height, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        libraryParams.bottomMargin = dp(heightDp() + 20); layer.addView(card, libraryParams);
        dialog.setContentView(layer);
        dialog.setOnDismissListener(d -> {
            stopEdgeScroll(); editorOpen = false; editorDialog = null;
            layer.removeView(this); original.removeView(placeholder);
            original.addView(this, Math.min(originalIndex, original.getChildCount()), originalParams);
            draft = null; library = null; count = null; rebuild(); setActive(true);
        });
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        refreshEditor(); dialog.show(); window.setLayout(-1, -1);
    }

    void closeEditor(boolean save) {
        if (editorDialog == null) return;
        if (save) DockConfiguration.save(activity, draft, draftAlignment);
        editorDialog.dismiss();
    }
    /** OEM resources have large transparent margins. Fit their visible art to the historic dock scale. */
    private ImageView icon(DockConfiguration.Item item) {
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        setIcon(image,item.icon,item.ordinal()>DockConfiguration.Item.SETTINGS.ordinal());
        return image;
    }
    private void setIcon(ImageView image,int resource,boolean crop){
        if(Integer.valueOf(resource).equals(image.getTag()))return;
        image.setTag(resource);
        if (!crop) {
            image.setImageResource(resource);
        } else {
            Bitmap cropped = croppedIcons.get(resource);
            if (cropped == null) {
                Drawable drawable = activity.getDrawable(resource);
                Bitmap source = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
                int width = Math.max(1, drawable.getIntrinsicWidth()), height = Math.max(1, drawable.getIntrinsicHeight());
                float scale = 192f / Math.max(width, height);
                drawable.setBounds(0, 0, Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
                drawable.draw(new Canvas(source));
                int left = 192, top = 192, right = -1, bottom = -1;
                int[] pixels = new int[192 * 192]; source.getPixels(pixels, 0, 192, 0, 0, 192, 192);
                for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) {
                    if ((pixels[y * 192 + x] >>> 24) > 16) {
                        left = Math.min(left, x); right = Math.max(right, x);
                        top = Math.min(top, y); bottom = Math.max(bottom, y);
                    }
                }
                cropped = right < left ? source : Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1);
                croppedIcons.put(resource, cropped);
            }
            image.setImageBitmap(cropped);
            image.setPadding(dp(4), dp(4), dp(4), dp(4));
        }
    }
    private Button action(String title, boolean primary, Runnable action) {
        Button button = new Button(activity); button.setText(title); button.setAllCaps(false);
        button.setTextSize(14); button.setTypeface(getResources().getFont(R.font.ui7_regular));
        button.setTextColor(primary ? Color.WHITE : ThemePalette.text(activity));
        button.setGravity(Gravity.CENTER); button.setPadding(dp(14), 0, dp(14), 0);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setBackgroundTintList(null); button.setStateListAnimator(null); button.setElevation(0);
        GradientDrawable background = shape(activity.getColor(primary ? R.color.accent : R.color.button), 0);
        background.setCornerRadius(dp(14));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(primary ? 0x33ffffff : 0x332682df), background, null));
        button.setOnClickListener(v -> action.run()); return button;
    }
    private TextView text(String value, int size) { TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(ThemePalette.text(activity)); return t; }
    private int sd(int value) { return Math.round(value * heightPercent / 100f * getResources().getDisplayMetrics().density); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(10)); if (stroke != 0) d.setStroke(dp(2), stroke); return d; }
}
