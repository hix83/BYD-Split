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
    private static final int TEXT_PRIMARY = 0xFFDAE1EB;
    private static final int TEXT_SECONDARY = 0xFF99A4AF;
    private static final int BLUE = 0xFF2682DF;
    private static final int ENERGY_BLUE = 0xFF55B8FF;
    private static final int HEALTHY = 0xFF55C98A;
    private static final int AMBER = 0xFFF0A95B;
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
    private final Bitmap[] nativeBattery = new Bitmap[21];
    private final NativeEnergySprites nativeFlows;
    private final Bitmap engineBitmap;
    private final Bitmap engineActiveBitmap;
    private final Bitmap driveActiveBitmap;
    private final Bitmap motorBitmap;
    private final ActionListener actionListener;
    private final RectF[] actionBounds = {
            new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private ClimateIconRenderer climateIcon;
    private VehicleTelemetrySnapshot telemetry = VehicleTelemetrySnapshot.EMPTY;
    private int demoScenario;
    private int demoSoc = 68;
    private int demoFuel = 65;
    private final RectF batteryBounds = new RectF(), fuelBounds = new RectF();
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
        ui7Typeface = context.getResources().getFont(R.font.ui7_regular);
        nativeFlows = new NativeEnergySprites(getResources());
        this.actionListener = actionListener;
        demoMode = AppPreferences.isDemoModeEnabled(context);
        vehicleBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.ui7_dmi_body);
        int[] batteryIds = {R.drawable.ui7_dmi_battery_0,R.drawable.ui7_dmi_battery_1,R.drawable.ui7_dmi_battery_2,R.drawable.ui7_dmi_battery_3,R.drawable.ui7_dmi_battery_4,R.drawable.ui7_dmi_battery_5,R.drawable.ui7_dmi_battery_6,R.drawable.ui7_dmi_battery_7,R.drawable.ui7_dmi_battery_8,R.drawable.ui7_dmi_battery_9,R.drawable.ui7_dmi_battery_10,R.drawable.ui7_dmi_battery_11,R.drawable.ui7_dmi_battery_12,R.drawable.ui7_dmi_battery_13,R.drawable.ui7_dmi_battery_14,R.drawable.ui7_dmi_battery_15,R.drawable.ui7_dmi_battery_16,R.drawable.ui7_dmi_battery_17,R.drawable.ui7_dmi_battery_18,R.drawable.ui7_dmi_battery_19,R.drawable.ui7_dmi_battery_20};
        for (int i = 0; i < batteryIds.length; i++) nativeBattery[i] = loadBitmap(batteryIds[i]);
        engineBitmap = loadBitmap(R.drawable.ui7_dmi_engine);
        engineActiveBitmap = loadBitmap(R.drawable.ui7_dmi_engine_active);
        driveActiveBitmap = loadBitmap(R.drawable.ui7_dmi_drive_active);
        motorBitmap = loadBitmap(R.drawable.ui7_dmi_drive);
        setContentDescription(
                "Состояние BYD Song L DM-i: скорость, направление, режим и шины");
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
                new int[]{0xFF1D252E, 0xFF191F27, 0xFF15181F},
                new float[]{0f, 0.53f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, width, height),
                width * 0.035f, width * 0.035f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, width * 0.0015f));
        paint.setColor(0x24B0B0B0);
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
        drawText(canvas, "ОЖ ДВС  " + (demoMode ? "82 °C" : telemetry.coolantTempC == null ? "— °C" : telemetry.coolantTempC + " °C"),
                width * .92f, height * .265f, width * .028f, TEXT_SECONDARY, Paint.Align.RIGHT);
        String voltage = demoMode ? "13,8 В" : telemetry.auxiliaryVoltage == null ? "— В" :
                String.format(Locale.forLanguageTag("ru"), "%.1f В", telemetry.auxiliaryVoltage);
        drawText(canvas, "Бортсеть  " + voltage,
                width * .92f, height * .302f, width * .028f, TEXT_SECONDARY, Paint.Align.RIGHT);
    }

    private void drawModeStatus(Canvas canvas, float width, float height) {
        float centerX = width * 0.5f;
        String workMode = workModeLabel(demoMode ? scenarioSnapshot(demoScenario).workMode : telemetry.workMode);
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
        paint.setColor(0xFF222B34);
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * .003f);
        paint.setColor(0xFF46515C);
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
        paint.setColor(Color.WHITE);
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
        DmiEnergyFlow flow = new DmiEnergyFlow(shown,
                demoMode ? demoScenario == 3 || demoScenario == 5 : null);
        String label = demoMode ? "ДЕМО · " + SCENARIOS[demoScenario] + "  ›" : "Состояние автомобиля";
        drawText(canvas, label, width * .5f, height * .225f, width * .029f,
                TEXT_PRIMARY, Paint.Align.CENTER);
        // Native Energy screen uses this 1170 × 1034 isometric SUV asset.
        // Keep the body, battery and powertrain in the same projection.
        float scale = Math.min(width / 1170f, height * .61f / 1034f);
        float left = (width - 1170f * scale) / 2;
        float sceneBaseTop = height * .275f;
        float top = sceneBaseTop - 18f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        paint.setAlpha(155);
        canvas.drawBitmap(vehicleBitmap, null, new RectF(0, 0, 1170, 1034), paint);
        paint.setAlpha(255);

        // Component anchors follow the original carbody layout (xhdpi = 2 px/dp).
        float engineX = 281.5f, engineY = 709;
        float driveX = 396.5f, driveY = 650;
        float batteryX = 610, batteryY = 525;
        float farX = 276, farY = 574, nearX = 534, nearY = 775;

        float soc = demoMode ? demoSoc : shown.batterySocPercent == null ? 0 : Math.max(0, Math.min(100, shown.batterySocPercent));
        batteryBounds.set(left + 420 * scale, top + 405 * scale, left + 790 * scale, top + 640 * scale);
        fuelBounds.set(left + 658 * scale, top + 264 * scale,
                left + 926 * scale, top + 452 * scale);
        Bitmap battery = nativeBattery[Math.round(soc / 5)];
        drawNativeComponent(canvas, flow.engineToGenerator || flow.engineToWheels
                ? engineActiveBitmap : engineBitmap, engineX, engineY, 139, 142);
        drawNativeComponent(canvas, flow.motorToWheels || flow.wheelsToMotor || flow.engineToGenerator
                ? driveActiveBitmap : motorBitmap, driveX, driveY, 125, 112);
        // OEM layering: couplings above engine/motor, battery casing above cables.
        nativeFlows.draw(canvas, paint, flow);
        drawNativeComponent(canvas, battery, batteryX, batteryY, 385, 275);
        drawText(canvas, shown.batterySocPercent == null ? "—" : Math.round(soc) + "%",
                batteryX, batteryY + 10, 44, TEXT_PRIMARY, Paint.Align.CENTER);

        drawFuelTank(canvas);

        drawNativeTireLabel(canvas, shown.tirePressFlKpa, shown.tireTempFlC,
                664, 881, nearX, nearY, Paint.Align.CENTER);
        drawNativeTireLabel(canvas, shown.tirePressFrKpa, shown.tireTempFrC,
                126, 494, farX, farY, Paint.Align.CENTER);
        drawNativeTireLabel(canvas, shown.tirePressRlKpa, shown.tireTempRlC,
                1045, 572, 948, 478, Paint.Align.CENTER);
        drawNativeTireLabel(canvas, shown.tirePressRrKpa, shown.tireTempRrC,
                620, 150, 670, 345, Paint.Align.CENTER);
        canvas.restore();
        // A compact legend belongs to the same scene; no second energy card.
        float y = Math.min(height * .865f, sceneBaseTop + 1034 * scale + height * .018f);
        drawText(canvas, "ДВС", width * .16f, y - height * .026f,
                width * .024f, TEXT_SECONDARY, Paint.Align.CENTER);
        drawText(canvas, formatRpm(shown.engineRpm), width * .16f, y,
                width * .026f, TEXT_PRIMARY, Paint.Align.CENTER);
        drawText(canvas, "Электропривод", width * .50f, y - height * .026f,
                width * .024f, TEXT_SECONDARY, Paint.Align.CENTER);
        drawText(canvas, formatRpm(demoMode ? Integer.valueOf(2450) : shown.motorRpm), width * .50f, y,
                width * .026f, TEXT_PRIMARY, Paint.Align.CENTER);
        drawText(canvas, "Батарея", width * .84f, y - height * .026f,
                width * .024f, TEXT_SECONDARY, Paint.Align.CENTER);
        drawText(canvas, formatPower(shown.batteryPowerKw), width * .84f, y,
                width * .026f, TEXT_PRIMARY, Paint.Align.CENTER);
        scheduleFlowAnimation(flow.engineToGenerator || flow.engineToWheels
                || flow.motorToWheels || flow.wheelsToMotor || flow.driveToBattery);
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

    private static final String[] SCENARIOS = {"Электротяга", "Заряд от ДВС", "Последовательный",
            "Совместная тяга", "Рекуперация", "Привод от ДВС"};

    private static VehicleTelemetrySnapshot scenarioSnapshot(int scenario) {
        int[][] data = {{72, 28, 0, 0, 26}, {0, -18, 1600, 20, 0},
                {72, 0, 1850, 28, 26}, {90, 20, 2100, 0, 18},
                {55, -22, 0, 0, -24}, {90, 0, 1900, 0, 0}};
        int[] d = data[scenario];
        return new VehicleTelemetrySnapshot(d[0], 240, 240, 230, 230, 31, 32, 29, 30,
                3, scenario == 0 || scenario == 4 ? 1 : 3, 308f, 68f,
                d[1], d[2], d[3], d[4], 1, null);
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
            paint.setColor(0xFF262D33);
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
        icon.setTint(Color.WHITE);
        if (action == ACTION_AUTO) {
            // OEM default PNG peaks at alpha 152; white tint alone retains that dimness.
            icon.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                    0, 0, 0, 0, 255,
                    0, 0, 0, 0, 255,
                    0, 0, 0, 0, 255,
                    0, 0, 0, 255f / 152f, 0}));
        }
        float opticalScale = action == ACTION_CLIMATE ? 1.30f
                : action == ACTION_CAR_SETTINGS ? 1.32f : 1f;
        int radius = Math.round(size * 1.5f * opticalScale);
        icon.setBounds(Math.round(centerX)-radius, Math.round(centerY)-radius,
                Math.round(centerX)+radius, Math.round(centerY)+radius);
        icon.draw(canvas);
    }

    private void drawFuelTank(Canvas canvas) {
        canvas.save();
        // Rearward offset along the vehicle axis, with clearance from the battery.
        // Position selected by the user in the emulator; fixed for demo and live views.
        canvas.translate(-8.96381f, -61.030228f);
        // Shared projected vectors keep corresponding opposite edges parallel.
        // Transverse direction follows the battery's rear end face (147,83).
        float cx = 820, cy = 390;
        float ux = 227 * .30f, uy = -139 * .30f;
        // Broad transverse tank, short longitudinal footprint.
        float vx = 147 * 1.15f * .90f, vy = 83 * 1.15f * .90f;
        float lx = cx - (ux + vx) / 2, ly = cy - (uy + vy) / 2;
        float tx = lx + ux, ty = ly + uy;
        float fx = lx + vx, fy = ly + vy;
        float rx = tx + vx, ry = ty + vy, depth = 64;
        float[] roof = {lx,ly,tx,ty,rx,ry,fx,fy};
        float[] front = {lx,ly,fx,fy,fx,fy+depth,lx,ly+depth};
        float[] side = {fx,fy,rx,ry,rx,ry+depth,fx,fy+depth};
        paint.setPathEffect(new android.graphics.CornerPathEffect(5));
        tankFace(canvas, roof, 0xB83D3D3D);
        tankFace(canvas, front, 0xD04A4A4A);
        tankFace(canvas, side, 0xD0333333);
        Integer fuel = demoMode ? Integer.valueOf(demoFuel) : telemetry.fuelPercent;
        if (fuel != null && fuel > 0) {
            float level = depth * (1 - fuel / 100f);
            tankFace(canvas, new float[]{lx,ly+level,fx,fy+level,fx,fy+depth,lx,ly+depth}, 0xC080795B);
            tankFace(canvas, new float[]{fx,fy+level,rx,ry+level,rx,ry+depth,fx,fy+depth}, 0xC0625D49);
            tankFace(canvas, new float[]{lx,ly+level,tx,ty+level,rx,ry+level,fx,fy+level}, 0xCA9B9270);
        }
        tankFace(canvas, roof, 0x183E3E3E);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        tankFace(canvas, roof, 0x887F7F7F);
        tankFace(canvas, front, 0x806B6B6B);
        tankFace(canvas, side, 0x805E5E5E);
        paint.setStyle(Paint.Style.FILL);
        paint.setPathEffect(null);
        drawText(canvas, fuel == null ? "—" : fuel + "%", 820, 411, 34,
                TEXT_PRIMARY, Paint.Align.CENTER);
        canvas.restore();
    }

    private void tankFace(Canvas canvas, float[] points, int color) {
        path.reset();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
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
            if (demoMode && (batteryBounds.contains(event.getX(), event.getY()) || fuelBounds.contains(event.getX(), event.getY()))) {
                if (batteryBounds.contains(event.getX(), event.getY())) demoSoc = demoSoc >= 100 ? 0 : Math.min(100, (demoSoc / 25 + 1) * 25);
                else demoFuel = demoFuel >= 100 ? 0 : Math.min(100, (demoFuel / 25 + 1) * 25);
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

    private static String workModeLabel(Integer mode) {
        if (mode == null) return "—";
        switch (mode) {
            case 0: return "STOP";
            case 1: return "EV";
            case 2: return "EV MAX";
            case 3: return "HEV";
            case 4: return "ДВС";
            case 5: return "KEEP";
            default: return "MODE " + mode;
        }
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

    private static String powerFlowTitle(Integer workMode) {
        if (workMode == null) return "СИСТЕМА DM-i";
        if (workMode == 1 || workMode == 2) return "ЭЛЕКТРИЧЕСКОЕ ДВИЖЕНИЕ";
        if (workMode == 3) return "ГИБРИДНЫЙ РЕЖИМ";
        if (workMode == 4) return "ДВИЖЕНИЕ ОТ ДВС";
        return "СИСТЕМА DM-i";
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
        // Dynasty pad body-4 is 12sp; keep telemetry legible in the 1/3 pane.
        paint.setTextSize(Math.max(size, 12f * getResources().getDisplayMetrics().scaledDensity));
        paint.setColor(color);
        paint.setTypeface(ui7Typeface);
        canvas.drawText(value, x, y, paint);
    }
}
