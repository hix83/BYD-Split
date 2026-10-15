package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

final class VehicleDashboardView extends View {
    static final int ACTION_AUTO = 0;
    static final int ACTION_CLIMATE = 1;
    static final int ACTION_BATTERY_DETAILS = 4;
    static final int ACTION_TECH_DETAILS = 5;
    static final int ACTION_CAR_SETTINGS = 2;
    static final int ACTION_HOME = 3;

    interface ActionListener {
        void onVehicleAction(int action);
    }
    private final int TEXT_PRIMARY;
    private final int TEXT_SECONDARY;
    private final int BLUE;
    private static final int ENERGY_BLUE = 0xFF55B8FF;
    private final int HEALTHY;
    private final int AMBER;
    private static final long FLOW_FRAME_INTERVAL_MS = 80L;

    private final android.graphics.Typeface ui7Typeface;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path straightFlowPath = new Path();
    private final Path directDrivePath = new Path();
    private final Path generatorBatteryPath = new Path();
    private final Path batteryMotorPath = new Path();
    private final Path chargerBatteryPath = new Path();
    private final PathMeasure flowMeasure = new PathMeasure();
    private final float[] flowPosition = new float[2];
    private final float[] flowTangent = new float[2];
    private final Bitmap vehicleBitmap;
    private final Bitmap vehicleShadow;
    private final Bitmap[] nativeBattery = new Bitmap[21];
    private final NativeEnergySprites nativeFlows;
    private final Bitmap driveActiveBitmap;
    private final Bitmap motorBitmap;
    private final ActionListener actionListener;
    private final RectF[] actionBounds = {
            new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private ClimateIconRenderer climateIcon;
    private VehicleTelemetrySnapshot telemetry = VehicleTelemetrySnapshot.EMPTY;
    private int demoScenario;
    private int demoSoc = 68;
    private final RectF batteryBounds = new RectF();
    private final boolean demoMode;
    private boolean animationFrameScheduled;
    private final Runnable animationTick = () -> {
        animationFrameScheduled = false;
        if (isAttachedToWindow()) {
            invalidate();
        }
    };

    VehicleDashboardView(Context context, ActionListener actionListener) {
        super(context);
        TEXT_PRIMARY = context.getColor(R.color.text_primary);
        TEXT_SECONDARY = context.getColor(R.color.text_secondary);
        BLUE = context.getColor(R.color.accent);
        HEALTHY = context.getColor(R.color.success);
        AMBER = context.getColor(R.color.warning);
        ui7Typeface = context.getResources().getFont(R.font.ui7_regular);
        nativeFlows = new NativeEnergySprites(getResources());
        this.actionListener = actionListener;
        demoMode = AppPreferences.isDemoModeEnabled(context);
        vehicleBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.ui7_dmi_body);
        vehicleShadow = BitmapFactory.decodeResource(getResources(), R.drawable.di5_car_shadow);
        int[] batteryIds = {R.drawable.ui7_dmi_battery_0,R.drawable.ui7_dmi_battery_1,R.drawable.ui7_dmi_battery_2,R.drawable.ui7_dmi_battery_3,R.drawable.ui7_dmi_battery_4,R.drawable.ui7_dmi_battery_5,R.drawable.ui7_dmi_battery_6,R.drawable.ui7_dmi_battery_7,R.drawable.ui7_dmi_battery_8,R.drawable.ui7_dmi_battery_9,R.drawable.ui7_dmi_battery_10,R.drawable.ui7_dmi_battery_11,R.drawable.ui7_dmi_battery_12,R.drawable.ui7_dmi_battery_13,R.drawable.ui7_dmi_battery_14,R.drawable.ui7_dmi_battery_15,R.drawable.ui7_dmi_battery_16,R.drawable.ui7_dmi_battery_17,R.drawable.ui7_dmi_battery_18,R.drawable.ui7_dmi_battery_19,R.drawable.ui7_dmi_battery_20};
        for (int i = 0; i < batteryIds.length; i++) nativeBattery[i] = loadBitmap(batteryIds[i]);
        driveActiveBitmap = loadBitmap(R.drawable.ui7_dmi_drive_active);
        motorBitmap = loadBitmap(R.drawable.ui7_dmi_drive);
        setContentDescription(
                "Состояние BYD Seagull: скорость, направление, батарея и шины");
    }

    private Bitmap loadBitmap(int resource) {
        return BitmapFactory.decodeResource(getResources(), resource);
    }

    void setTelemetry(VehicleTelemetrySnapshot value) {
        telemetry = value == null ? VehicleTelemetrySnapshot.EMPTY : value;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(animationTick);
        animationFrameScheduled = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        drawBackground(canvas, width, height);
        drawHeader(canvas, width, height);
        drawVehicle(canvas, width, height);

        drawQuickActions(canvas, width, height);
    }

    private void drawBackground(Canvas canvas, float width, float height) {
        paint.setShader(new LinearGradient(
                0, 0, width, height,
                new int[]{getContext().getColor(R.color.surface_light), getContext().getColor(R.color.surface_light), getContext().getColor(R.color.background)},
                new float[]{0f, 0.53f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, width, height),
                width * 0.035f, width * 0.035f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, width * 0.0015f));
        paint.setColor(getContext().getColor(R.color.divider));
        canvas.drawRoundRect(new RectF(1, 1, width - 1, height - 1),
                width * 0.035f, width * 0.035f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHeader(Canvas canvas, float width, float height) {
        Integer shownSpeed = demoMode ? scenarioSnapshot(demoScenario).speedKmh : telemetry.speedKmh;
        String speed = shownSpeed == null ? "—" : String.valueOf(shownSpeed);
        drawText(canvas, speed, width * 0.075f, height * 0.13f,
                width * 0.13f, TEXT_PRIMARY, Paint.Align.LEFT);
        drawText(canvas, "км/ч", width * 0.08f, height * 0.18f,
                width * 0.032f, TEXT_SECONDARY, Paint.Align.LEFT);

        drawModeStatus(canvas, width, height);
        drawCompass(canvas, width, height);
        drawText(canvas, "SOH  " + (demoMode ? "98%" : telemetry.batterySohPercent == null ? "—%" : telemetry.batterySohPercent + "%"),
                width * .08f, height * .265f, width * .032f, HEALTHY, Paint.Align.LEFT);
        String batteryTemperature = demoMode ? "24–26 °C" :
                telemetry.batteryMinTempC == null || telemetry.batteryMaxTempC == null ? "— °C" :
                telemetry.batteryMinTempC + "–" + telemetry.batteryMaxTempC + " °C";
        drawText(canvas, "Батарея  " + batteryTemperature,
                width * .08f, height * .302f, width * .028f, TEXT_SECONDARY, Paint.Align.LEFT);
        String voltage = demoMode ? "13,8 В" : telemetry.auxiliaryVoltage == null ? "— В" :
                String.format(Locale.forLanguageTag("ru"), "%.1f В", telemetry.auxiliaryVoltage);
        drawText(canvas, "Бортсеть  " + voltage,
                width * .92f, height * .302f, width * .028f, TEXT_SECONDARY, Paint.Align.RIGHT);
    }

    private void drawModeStatus(Canvas canvas, float width, float height) {
        float centerX = width * 0.5f;
        String workMode = "EV";
        String driveMode = driveModeLabel(telemetry.driveMode);
        drawText(canvas, workMode, centerX, height * 0.095f,
                width * 0.038f, TEXT_PRIMARY, Paint.Align.CENTER);
        drawText(canvas, driveMode, centerX, height * 0.143f,
                width * 0.023f, BLUE, Paint.Align.CENTER);
        paint.setColor(0x804C8DFF);
        canvas.drawRoundRect(new RectF(
                        centerX - width * 0.04f, height * 0.158f,
                        centerX + width * 0.04f, height * 0.162f),
                width * 0.005f, width * 0.005f, paint);
    }

    private void drawCompass(Canvas canvas, float width, float height) {
        float centerX = width * 0.82f;
        float centerY = height * 0.108f;
        float radius = width * 0.092f;
        Float bearing = telemetry.bearingDegrees;
        float heading = bearing == null ? 0f : bearing;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(getContext().getColor(R.color.card));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * .003f);
        paint.setColor(getContext().getColor(R.color.divider));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(width * .005f);
        paint.setColor(BLUE);
        canvas.drawArc(new RectF(centerX-radius, centerY-radius, centerX+radius, centerY+radius),
                -103f-heading, 26f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        String[] points = {"С", "В", "Ю", "З"};
        for (int index = 0; index < points.length; index++) {
            double radians = Math.toRadians(index * 90f - heading - 90f);
            float x = centerX + (float) Math.cos(radians) * radius * .65f;
            float y = centerY + (float) Math.sin(radians) * radius * .65f + width * .011f;
            drawText(canvas, points[index], x, y, width * .027f,
                    index == 0 ? BLUE : TEXT_PRIMARY, Paint.Align.CENTER);
        }
        paint.setColor(TEXT_PRIMARY);
        path.reset();
        path.moveTo(centerX, centerY - radius * 1.16f);
        path.lineTo(centerX - radius * .16f, centerY - radius * .83f);
        path.lineTo(centerX + radius * .16f, centerY - radius * .83f);
        path.close();
        canvas.drawPath(path, paint);

        String degrees = bearing == null
                ? "—°" : Math.round(heading) + "° · " + directionLabel(heading);
        drawText(canvas, degrees, centerX, centerY + radius * 1.32f,
                width * 0.020f, TEXT_SECONDARY, Paint.Align.CENTER);
    }

    private void drawVehicle(Canvas canvas, float width, float height) {
        VehicleTelemetrySnapshot shown = demoMode ? scenarioSnapshot(demoScenario) : telemetry;
        boolean moving = shown.speedKmh != null && shown.speedKmh > 0;
        boolean regen = moving && shown.batteryPowerKw != null && shown.batteryPowerKw < -1;
        boolean traction = moving && shown.batteryPowerKw != null && shown.batteryPowerKw > 1;
        String label = demoMode ? "ДЕМО · " + SCENARIOS[demoScenario] + "  ›" : "Состояние автомобиля";
        drawText(canvas, label, width * .5f, height * .225f, width * .029f,
                TEXT_PRIMARY, Paint.Align.CENTER);
        // Owner-selected Di5 CROSS body, normalized to the existing scene bounds.
        // Preserve the isometric layout, battery and electric powertrain.
        float scale = Math.min(width / 1170f, height * .61f / 1034f);
        float left = (width - 1170f * scale) / 2;
        float sceneBaseTop = height * .275f;
        float top = sceneBaseTop - 18f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        paint.setAlpha(255);
        canvas.drawBitmap(vehicleShadow, null, new RectF(0, 0, 1170, 1034), paint);
        canvas.drawBitmap(vehicleBitmap, null, new RectF(0, 0, 1170, 1034), paint);
        paint.setAlpha(255);

        // Component anchors follow the original carbody layout (xhdpi = 2 px/dp).
        float driveX = 396.5f, driveY = 650;
        float batteryX = 610, batteryY = 525;
        float farX = 276, farY = 574, nearX = 534, nearY = 775;

        float soc = demoMode ? demoSoc : shown.batterySocPercent == null ? 0 : Math.max(0, Math.min(100, shown.batterySocPercent));
        batteryBounds.set(left + 420 * scale, top + 405 * scale, left + 790 * scale, top + 640 * scale);
        Bitmap battery = nativeBattery[Math.round(soc / 5)];
        drawNativeComponent(canvas, traction || regen
                ? driveActiveBitmap : motorBitmap, driveX, driveY, 125, 112);
        // OEM layering: couplings above engine/motor, battery casing above cables.
        nativeFlows.drawElectric(canvas, paint, traction, regen);
        drawNativeComponent(canvas, battery, batteryX, batteryY, 385, 275);
        drawText(canvas, shown.batterySocPercent == null ? "—" : Math.round(soc) + "%",
                batteryX, batteryY + 10, 44, TEXT_PRIMARY, Paint.Align.CENTER);


        drawNativeTireLabel(canvas, shown.tirePressFlKpa, shown.tireTempFlC,
                664, 881, nearX, nearY, Paint.Align.CENTER);
        drawNativeTireLabel(canvas, shown.tirePressFrKpa, shown.tireTempFrC,
                126, 494, farX, farY, Paint.Align.CENTER);
        drawNativeTireLabel(canvas, shown.tirePressRlKpa, shown.tireTempRlC,
                1045, 572, 948, 478, Paint.Align.CENTER);
        drawNativeTireLabel(canvas, shown.tirePressRrKpa, shown.tireTempRrC,
                620, 150, 700, 345, Paint.Align.CENTER);
        canvas.restore();
        // A compact legend belongs to the same scene; no second energy card.
        float y = Math.min(height * .865f, sceneBaseTop + 1034 * scale + height * .018f);
        drawText(canvas, "Электропривод", width * .30f, y - height * .026f,
                width * .024f, TEXT_SECONDARY, Paint.Align.CENTER);
        drawText(canvas, formatRpm(demoMode ? Integer.valueOf(moving ? 2450 : 0) : shown.motorRpm), width * .30f, y,
                width * .026f, TEXT_PRIMARY, Paint.Align.CENTER);
        drawText(canvas, "Батарея", width * .70f, y - height * .026f,
                width * .024f, TEXT_SECONDARY, Paint.Align.CENTER);
        drawText(canvas, formatPower(shown.batteryPowerKw), width * .70f, y,
                width * .026f, TEXT_PRIMARY, Paint.Align.CENTER);
        scheduleFlowAnimation(traction || regen);
    }

    private void drawNativeComponent(Canvas canvas, Bitmap bitmap, float x, float y,
                                     float width, float height) {
        float scale = Math.min(width / bitmap.getWidth(), height / bitmap.getHeight());
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, null, new RectF(x - bitmap.getWidth() * scale / 2,
                y - bitmap.getHeight() * scale / 2, x + bitmap.getWidth() * scale / 2,
                y + bitmap.getHeight() * scale / 2), paint);
    }

    private void drawNativeTireLabel(Canvas canvas, Integer pressure, Integer temperature,
                                     float x, float y, float wheelX, float wheelY, Paint.Align align) {
        int color = pressure == null ? TEXT_SECONDARY : pressure >= 220 && pressure <= 260 ? HEALTHY : AMBER;
        // Short orthogonal callouts: horizontal/vertical segments only.
        // Labels sit outside the silhouette, clear of the energy circuit.
        paint.setColor(0x906C8590);
        paint.setStrokeWidth(2);
        paint.setStrokeCap(Paint.Cap.BUTT);
        if (Math.abs(x - wheelX) < 95) {
            float startY = y < wheelY ? y + 58 : y - 28;
            canvas.drawLine(x, startY, x, wheelY, paint);
            canvas.drawLine(x, wheelY, wheelX, wheelY, paint);
        } else {
            float startX = x + (x < wheelX ? 86 : -86);
            float startY = y + 12;
            canvas.drawLine(startX, startY, wheelX, startY, paint);
            canvas.drawLine(wheelX, startY, wheelX, wheelY, paint);
        }
        paint.setColor(0xFF82949F);
        canvas.drawCircle(wheelX, wheelY, 3, paint);
        drawText(canvas, pressure == null ? "— bar" : String.format(Locale.US, "%.1f bar", pressure / 100f),
                x, y, 36, color, align);
        drawText(canvas, temperature == null ? "—°C" : temperature + "°C", x, y + 40,
                31, TEXT_SECONDARY, align);
    }

    private static final String[] SCENARIOS = {"Электротяга", "Рекуперация", "Зарядка", "Стоянка"};

    private static VehicleTelemetrySnapshot scenarioSnapshot(int scenario) {
        int[][] data = {{52, 18}, {38, -22}, {0, -30}, {0, 0}};
        int[] d = data[scenario];
        return new VehicleTelemetrySnapshot(d[0], 240, 240, 230, 230, 31, 32, 29, 30,
                3, 1, 308f, 68f,
                d[1], null, null, d[1], scenario == 2 ? 2 : 1, null);
    }

    private void drawTireStatus(Canvas canvas, Integer pressureKpa,
                                Integer temperatureC,
                                float labelX, float y, float carEdge,
                                float size, boolean left) {
        Paint.Align align = left ? Paint.Align.RIGHT : Paint.Align.LEFT;
        String value = pressureKpa == null ? "— bar"
                : String.format(Locale.US, "%.1f bar", pressureKpa / 100f);
        int statusColor = pressureKpa == null ? TEXT_SECONDARY
                : pressureKpa >= 220 && pressureKpa <= 260 ? HEALTHY : AMBER;
        drawText(canvas, value, labelX, y, size, statusColor, align);
        String temperature = temperatureC == null
                ? "—°C" : temperatureC + "°C";
        drawText(canvas, temperature, labelX, y + size * 1.18f,
                size, temperatureC == null ? TEXT_SECONDARY : TEXT_PRIMARY,
                align);
        paint.setColor((statusColor & 0x00FFFFFF) | 0xA0000000);
        paint.setStrokeWidth(Math.max(1f, size * 0.09f));
        float start = left ? labelX + size * 0.35f : carEdge;
        float end = left ? carEdge : labelX - size * 0.35f;
        canvas.drawLine(start, y - size * 0.25f,
                end, y - size * 0.25f, paint);
    }

    private void drawQuickActions(Canvas canvas, float width, float height) {
        float gap = width * 0.014f;
        float left = width * 0.055f;
        float totalWidth = width * 0.89f;
        float chipWidth = (totalWidth - gap * (actionBounds.length-1)) / actionBounds.length;
        float top = height * 0.895f;
        float bottom = height * 0.972f;
        for (int index = 0; index < actionBounds.length; index++) {
            float chipLeft = left + index * (chipWidth + gap);
            RectF chip = actionBounds[index];
            chip.set(chipLeft, top, chipLeft + chipWidth, bottom);
            paint.setColor(getContext().getColor(R.color.button));
            canvas.drawRoundRect(chip, width * 0.018f,
                    width * 0.018f, paint);
            drawActionIcon(canvas, index, chip.centerX(),
                    chip.centerY(), width * 0.034f);

        }
    }

    private void drawActionIcon(Canvas canvas, int action,
                                float centerX, float centerY, float size) {
        if (action == ACTION_CLIMATE) {
            if (climateIcon == null) climateIcon = new ClimateIconRenderer(getContext());
            int radius = Math.round(size * 1.5f * 1.30f);
            climateIcon.draw(canvas, Math.round(centerX), Math.round(centerY), radius,
                    demoMode ? ClimateIconState.decode(1, 4, 2) : telemetry.climate);
            return;
        }
        paint.setColor(TEXT_PRIMARY);
        int[] icons = {R.drawable.ui7_oem_car, R.drawable.ui7_climate, R.drawable.ui7_car_settings, R.drawable.ui7_home, R.drawable.ui7_tech};
        android.graphics.drawable.Drawable icon = getContext().getDrawable(icons[action]);
        icon = icon.mutate();
        icon.setTint(TEXT_PRIMARY);
        if (action == ACTION_AUTO) {
            // OEM default PNG peaks at alpha 152; white tint alone retains that dimness.
            icon.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                    0, 0, 0, 0, Color.red(TEXT_PRIMARY),
                    0, 0, 0, 0, Color.green(TEXT_PRIMARY),
                    0, 0, 0, 0, Color.blue(TEXT_PRIMARY),
                    0, 0, 0, 255f / 152f, 0}));
        }
        float opticalScale = action == ACTION_CLIMATE ? 1.30f
                : action == ACTION_CAR_SETTINGS ? 1.32f : 1f;
        int radius = Math.round(size * 1.5f * opticalScale);
        icon.setBounds(Math.round(centerX)-radius, Math.round(centerY)-radius,
                Math.round(centerX)+radius, Math.round(centerY)+radius);
        icon.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (event.getX() >= getWidth() * .05f && event.getX() <= getWidth() * .40f
                    && event.getY() >= getHeight() * .225f && event.getY() <= getHeight() * .28f) {
                performClick();
                if (actionListener != null) actionListener.onVehicleAction(ACTION_BATTERY_DETAILS);
                return true;
            }
            if (demoMode && batteryBounds.contains(event.getX(), event.getY())) {
                demoSoc = demoSoc >= 100 ? 0 : Math.min(100, (demoSoc / 25 + 1) * 25);
                performClick(); invalidate(); return true;
            }
            if (demoMode && event.getY() > getHeight() * .18f
                    && event.getY() < getHeight() * .87f) {
                demoScenario = (demoScenario + 1) % SCENARIOS.length;
                performClick();
                invalidate();
                return true;
            }
            for (int index = 0; index < actionBounds.length; index++) {
                if (actionBounds[index].contains(event.getX(), event.getY())) {
                    performClick();
                    if (actionListener != null) {
                        actionListener.onVehicleAction(index==4?ACTION_TECH_DETAILS:index);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        playSoundEffect(android.view.SoundEffectConstants.CLICK);
        return true;
    }

    private static String driveModeLabel(Integer mode) {
        if (mode == null) return "РЕЖИМ —";
        switch (mode) {
            case 1: return "ECO";
            case 2: return "SPORT";
            case 4: return "SNOWFIELD";
            case 0:
            case 3: return "NORMAL";
            default: return "РЕЖИМ " + mode;
        }
    }

    private static String directionLabel(float bearing) {
        String[] labels = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        return labels[Math.round(bearing / 45f) % labels.length];
    }

    private void drawFlowLine(Canvas canvas,
                              float startX, float startY,
                              float endX, float endY,
                              int color, boolean active) {
        Path flow = straightFlowPath;
        flow.reset();
        flow.moveTo(startX, startY);
        flow.lineTo(endX, endY);
        drawAnimatedFlow(canvas, flow, color, active);
    }

    private void drawAnimatedFlow(Canvas canvas, Path flow,
                                  int color, boolean active) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1f, getWidth() * 0.003f));
        paint.setColor(active ? withAlpha(color, 150) : 0x303F5068);
        canvas.drawPath(flow, paint);
        paint.setStyle(Paint.Style.FILL);
        if (!active) {
            return;
        }
        flowMeasure.setPath(flow, false);
        float length = flowMeasure.getLength();
        float spacing = getWidth() * 0.035f;
        float phase = (SystemClock.uptimeMillis() % 900L) / 900f;
        for (float distance = phase * spacing;
             distance < length; distance += spacing) {
            if (!flowMeasure.getPosTan(
                    distance, flowPosition, flowTangent)) {
                continue;
            }
            float angle = (float) Math.atan2(
                    flowTangent[1], flowTangent[0]);
            float size = getWidth() * 0.010f;
            path.reset();
            path.moveTo(flowPosition[0] + (float) Math.cos(angle) * size,
                    flowPosition[1] + (float) Math.sin(angle) * size);
            path.lineTo(flowPosition[0]
                            + (float) Math.cos(angle + 2.45f) * size,
                    flowPosition[1]
                            + (float) Math.sin(angle + 2.45f) * size);
            path.lineTo(flowPosition[0]
                            + (float) Math.cos(angle - 2.45f) * size,
                    flowPosition[1]
                            + (float) Math.sin(angle - 2.45f) * size);
            path.close();
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
    }

    private void scheduleFlowAnimation(boolean active) {
        if (!active) {
            if (animationFrameScheduled) {
                removeCallbacks(animationTick);
                animationFrameScheduled = false;
            }
            return;
        }
        if (!animationFrameScheduled && isAttachedToWindow()) {
            animationFrameScheduled = true;
            postDelayed(animationTick, FLOW_FRAME_INTERVAL_MS);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static String formatRpm(Integer rpm) {
        return rpm == null ? "— об/мин" : rpm + " об/мин";
    }

    private static String formatPower(Integer powerKw) {
        if (powerKw == null) {
            return "— кВт";
        }
        return (powerKw > 0 ? "+" : "") + powerKw + " кВт";
    }

    private static boolean isExternalCharging(Integer gunState,
                                               Integer chargingType) {
        if (gunState != null) {
            return gunState >= 2 && gunState <= 4;
        }
        return chargingType != null && chargingType >= 2
                && chargingType <= 5;
    }

    private static String chargerTypeLabel(Integer gunState,
                                           Integer chargingType) {
        if (gunState != null) {
            if (gunState == 2) return "GB/T AC";
            if (gunState == 3) return "GB/T DC";
            if (gunState == 4) return "GB/T AC/DC";
        }
        if (chargingType != null) {
            if (chargingType == 2) return "GB/T AC";
            if (chargingType == 4 || chargingType == 5) return "GB/T DC";
        }
        return "GB/T AC/DC";
    }

    private void drawText(Canvas canvas, String value, float x, float y,
                          float size, int color, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(align);
        // Modest increase for the 10.1-inch panel; keep the original scene anchors.
        paint.setTextSize(Math.max(size * 1.12f, 14f * getResources().getDisplayMetrics().scaledDensity));
        paint.setColor(color);
        paint.setTypeface(ui7Typeface);
        canvas.drawText(value, x, y, paint);
    }
}
