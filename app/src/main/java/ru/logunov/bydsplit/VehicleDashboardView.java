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
    static final int ACTION_CAR_SETTINGS = 2;
    static final int ACTION_HOME = 3;

    interface ActionListener {
        void onVehicleAction(int action);
    }
    private static final int TEXT_PRIMARY = 0xFFEEF3FA;
    private static final int TEXT_SECONDARY = 0xFF8E9AAF;
    private static final int BLUE = 0xFF4C8DFF;
    private static final int ENERGY_BLUE = 0xFF55B8FF;
    private static final int HEALTHY = 0xFF55C98A;
    private static final int AMBER = 0xFFF0A95B;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Bitmap vehicleBitmap;
    private final Bitmap engineBitmap;
    private final Bitmap generatorBitmap;
    private final Bitmap controllerBitmap;
    private final Bitmap motorBitmap;
    private final Bitmap wheelBitmap;
    private final Bitmap batteryShellBitmap;
    private final ActionListener actionListener;
    private final RectF[] actionBounds = {
            new RectF(), new RectF(), new RectF(), new RectF()};
    private VehicleTelemetrySnapshot telemetry = VehicleTelemetrySnapshot.EMPTY;

    VehicleDashboardView(Context context, ActionListener actionListener) {
        super(context);
        this.actionListener = actionListener;
        vehicleBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.song_l_dmi_top);
        engineBitmap = loadBitmap(R.drawable.dmi_engine_reference);
        generatorBitmap = loadBitmap(R.drawable.dmi_generator_reference);
        controllerBitmap = loadBitmap(R.drawable.dmi_controller_reference);
        motorBitmap = loadBitmap(R.drawable.dmi_motor_reference);
        wheelBitmap = loadBitmap(R.drawable.dmi_wheel_reference);
        batteryShellBitmap = loadBitmap(R.drawable.dmi_battery_shell_reference);
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
        drawPowerFlow(canvas, width, height);
        drawQuickActions(canvas, width, height);
    }

    private void drawBackground(Canvas canvas, float width, float height) {
        paint.setShader(new LinearGradient(
                0, 0, width, height,
                new int[]{0xFF111B2B, 0xFF0B1422, 0xFF070D16},
                new float[]{0f, 0.53f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, width, height),
                width * 0.035f, width * 0.035f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, width * 0.0015f));
        paint.setColor(0x304C8DFF);
        canvas.drawRoundRect(new RectF(1, 1, width - 1, height - 1),
                width * 0.035f, width * 0.035f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHeader(Canvas canvas, float width, float height) {
        String speed = telemetry.speedKmh == null
                ? "—" : String.valueOf(telemetry.speedKmh);
        drawText(canvas, speed, width * 0.075f, height * 0.13f,
                width * 0.13f, TEXT_PRIMARY, Paint.Align.LEFT);
        drawText(canvas, "км/ч", width * 0.08f, height * 0.18f,
                width * 0.032f, TEXT_SECONDARY, Paint.Align.LEFT);

        drawModeStatus(canvas, width, height);
        drawCompass(canvas, width, height);
    }

    private void drawModeStatus(Canvas canvas, float width, float height) {
        float centerX = width * 0.5f;
        String workMode = workModeLabel(telemetry.workMode);
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

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * 0.002f);
        paint.setColor(0x706D7E99);
        canvas.drawCircle(centerX, centerY, radius, paint);

        for (int angle = 0; angle < 360; angle += 15) {
            float relative = angle - heading;
            double radians = Math.toRadians(relative - 90f);
            float outerX = centerX + (float) Math.cos(radians) * radius * 0.91f;
            float outerY = centerY + (float) Math.sin(radians) * radius * 0.91f;
            float tickLength = angle % 45 == 0 ? radius * 0.13f : radius * 0.07f;
            float innerX = centerX + (float) Math.cos(radians)
                    * (radius * 0.91f - tickLength);
            float innerY = centerY + (float) Math.sin(radians)
                    * (radius * 0.91f - tickLength);
            paint.setStrokeWidth(angle % 45 == 0
                    ? width * 0.003f : width * 0.0014f);
            paint.setColor(angle == 0 ? BLUE : 0x708E9AAF);
            canvas.drawLine(innerX, innerY, outerX, outerY, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        String[] points = {"С", "В", "Ю", "З"};
        for (int index = 0; index < points.length; index++) {
            float relative = index * 90f - heading;
            double radians = Math.toRadians(relative - 90f);
            float x = centerX + (float) Math.cos(radians) * radius * 0.61f;
            float y = centerY + (float) Math.sin(radians) * radius * 0.61f
                    + width * 0.008f;
            drawText(canvas, points[index], x, y, width * 0.020f,
                    index == 0 ? BLUE : TEXT_SECONDARY, Paint.Align.CENTER);
        }

        paint.setColor(TEXT_PRIMARY);
        path.reset();
        path.moveTo(centerX, centerY - radius * 1.08f);
        path.lineTo(centerX - radius * 0.11f, centerY - radius * 0.84f);
        path.lineTo(centerX + radius * 0.11f, centerY - radius * 0.84f);
        path.close();
        canvas.drawPath(path, paint);

        String degrees = bearing == null
                ? "—°" : Math.round(heading) + "° · " + directionLabel(heading);
        drawText(canvas, degrees, centerX, centerY + radius * 1.32f,
                width * 0.020f, TEXT_SECONDARY, Paint.Align.CENTER);
    }

    private void drawVehicle(Canvas canvas, float width, float height) {
        float cardLeft = width * 0.055f;
        float cardTop = height * 0.22f;
        float cardRight = width * 0.945f;
        float cardBottom = height * 0.57f;
        paint.setColor(0x66162437);
        canvas.drawRoundRect(new RectF(cardLeft, cardTop, cardRight, cardBottom),
                width * 0.025f, width * 0.025f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, width * 0.0015f));
        paint.setColor(0x4A304967);
        canvas.drawRoundRect(new RectF(cardLeft, cardTop, cardRight, cardBottom),
                width * 0.025f, width * 0.025f, paint);
        paint.setStyle(Paint.Style.FILL);

        float carHeight = height * 0.30f;
        float carWidth = carHeight * vehicleBitmap.getWidth()
                / vehicleBitmap.getHeight();
        RectF car = new RectF(
                width * 0.5f - carWidth / 2f,
                height * 0.245f,
                width * 0.5f + carWidth / 2f,
                height * 0.245f + carHeight);
        paint.setAlpha(255);
        canvas.drawBitmap(vehicleBitmap, null, car, paint);

        float labelSize = width * 0.027f;
        drawTireStatus(canvas, telemetry.tirePressFlKpa, telemetry.tireTempFlC,
                width * 0.31f,
                height * 0.32f, car.left, labelSize, true);
        drawTireStatus(canvas, telemetry.tirePressFrKpa, telemetry.tireTempFrC,
                width * 0.69f,
                height * 0.32f, car.right, labelSize, false);
        drawTireStatus(canvas, telemetry.tirePressRlKpa, telemetry.tireTempRlC,
                width * 0.31f,
                height * 0.48f, car.left, labelSize, true);
        drawTireStatus(canvas, telemetry.tirePressRrKpa, telemetry.tireTempRrC,
                width * 0.69f,
                height * 0.48f, car.right, labelSize, false);
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

    private void drawPowerFlow(Canvas canvas, float width, float height) {
        float cardTop = height * 0.585f;
        float cardBottom = height * 0.885f;
        paint.setColor(0x52101D30);
        canvas.drawRoundRect(new RectF(
                        width * 0.035f, cardTop,
                        width * 0.965f, cardBottom),
                width * 0.025f, width * 0.025f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, width * 0.0015f));
        paint.setColor(0x55315F9E);
        canvas.drawRoundRect(new RectF(
                        width * 0.035f, cardTop,
                        width * 0.965f, cardBottom),
                width * 0.025f, width * 0.025f, paint);
        paint.setStyle(Paint.Style.FILL);

        float titleY = height * 0.617f;
        drawText(canvas, powerFlowTitle(telemetry.workMode), width * 0.5f,
                titleY, width * 0.025f, TEXT_PRIMARY, Paint.Align.CENTER);

        float nodeY = height * 0.695f;
        float imageWidth = width * 0.135f;
        float imageHeight = height * 0.083f;
        float engineX = width * 0.115f;
        float generatorX = width * 0.305f;
        float controllerX = width * 0.50f;
        float motorX = width * 0.695f;
        float wheelX = width * 0.885f;

        boolean engineActive = telemetry.workMode != null
                && telemetry.workMode >= 3 && telemetry.workMode <= 5;
        boolean tractionActive = telemetry.workMode != null
                && telemetry.workMode >= 1 && telemetry.workMode <= 5;
        boolean batteryDischarging = telemetry.batteryPowerKw != null
                ? telemetry.batteryPowerKw > 1
                : telemetry.workMode != null
                && (telemetry.workMode == 1 || telemetry.workMode == 2);
        boolean batteryCharging = telemetry.batteryPowerKw != null
                && telemetry.batteryPowerKw < -1;
        boolean wheelsMoving = telemetry.speedKmh != null
                && telemetry.speedKmh > 0;

        drawFlowLine(canvas, engineX + imageWidth * 0.42f, nodeY,
                generatorX - imageWidth * 0.42f, nodeY,
                AMBER, engineActive);
        drawFlowLine(canvas, generatorX + imageWidth * 0.42f, nodeY,
                controllerX - imageWidth * 0.42f, nodeY,
                ENERGY_BLUE, engineActive);
        drawFlowLine(canvas, controllerX + imageWidth * 0.42f, nodeY,
                motorX - imageWidth * 0.42f, nodeY,
                ENERGY_BLUE, tractionActive);
        drawFlowLine(canvas, motorX + imageWidth * 0.42f, nodeY,
                wheelX - imageWidth * 0.42f, nodeY,
                ENERGY_BLUE, tractionActive && wheelsMoving);

        float batteryY = height * 0.825f;
        float batteryWidth = width * 0.30f;
        float batteryHeight = batteryWidth
                * batteryShellBitmap.getHeight() / batteryShellBitmap.getWidth();
        RectF battery = new RectF(
                controllerX - batteryWidth / 2f,
                batteryY - batteryHeight / 2f,
                controllerX + batteryWidth / 2f,
                batteryY + batteryHeight / 2f);
        Path batteryToController = new Path();
        if (batteryCharging) {
            batteryToController.moveTo(controllerX, nodeY + imageHeight * 0.45f);
            batteryToController.lineTo(controllerX, battery.top + battery.height() * 0.2f);
        } else {
            batteryToController.moveTo(controllerX, battery.top + battery.height() * 0.2f);
            batteryToController.lineTo(controllerX, nodeY + imageHeight * 0.45f);
        }
        drawAnimatedFlow(canvas, batteryToController,
                batteryCharging ? AMBER : ENERGY_BLUE,
                batteryCharging || batteryDischarging);

        drawPowerNode(canvas, engineBitmap, engineX, nodeY,
                imageWidth, imageHeight, "ДВС",
                formatRpm(telemetry.engineRpm));
        drawPowerNode(canvas, generatorBitmap, generatorX, nodeY,
                imageWidth, imageHeight, "Генератор",
                formatPower(telemetry.generatorPowerKw));
        drawPowerNode(canvas, controllerBitmap, controllerX, nodeY,
                imageWidth, imageHeight, "Электроника",
                formatPower(telemetry.batteryPowerKw));
        drawPowerNode(canvas, motorBitmap, motorX, nodeY,
                imageWidth, imageHeight, "Эл. мотор",
                formatPower(telemetry.motorPowerKw));
        drawPowerNode(canvas, wheelBitmap, wheelX, nodeY,
                imageWidth, imageHeight, "Колёса",
                telemetry.speedKmh == null ? "— км/ч"
                        : telemetry.speedKmh + " км/ч");

        drawBattery(canvas, battery, telemetry.batterySocPercent,
                telemetry.batteryPowerKw);
        if (engineActive || tractionActive
                || batteryCharging || batteryDischarging) {
            postInvalidateOnAnimation();
        }
    }

    private void drawQuickActions(Canvas canvas, float width, float height) {
        String[] labels = {"АВТО", "КЛИМАТ", "НАСТР.", "ДОМОЙ"};
        float gap = width * 0.014f;
        float left = width * 0.055f;
        float totalWidth = width * 0.89f;
        float chipWidth = (totalWidth - gap * 3f) / 4f;
        float top = height * 0.895f;
        float bottom = height * 0.972f;
        for (int index = 0; index < labels.length; index++) {
            float chipLeft = left + index * (chipWidth + gap);
            RectF chip = actionBounds[index];
            chip.set(chipLeft, top, chipLeft + chipWidth, bottom);
            paint.setColor(0xA6172234);
            canvas.drawRoundRect(chip, width * 0.018f,
                    width * 0.018f, paint);
            drawActionIcon(canvas, index, chip.centerX(),
                    chip.top + chip.height() * 0.35f, width * 0.027f);
            drawText(canvas, labels[index], chip.centerX(),
                    chip.bottom - width * 0.010f, width * 0.017f,
                    TEXT_SECONDARY, Paint.Align.CENTER);
        }
    }

    private void drawActionIcon(Canvas canvas, int action,
                                float centerX, float centerY, float size) {
        paint.setColor(TEXT_PRIMARY);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, size * 0.11f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        if (action == ACTION_AUTO) {
            RectF body = new RectF(centerX - size * 0.75f,
                    centerY - size * 0.24f, centerX + size * 0.75f,
                    centerY + size * 0.42f);
            canvas.drawRoundRect(body, size * 0.22f, size * 0.22f, paint);
            path.moveTo(centerX - size * 0.48f, body.top);
            path.lineTo(centerX - size * 0.28f, centerY - size * 0.62f);
            path.lineTo(centerX + size * 0.28f, centerY - size * 0.62f);
            path.lineTo(centerX + size * 0.48f, body.top);
            canvas.drawPath(path, paint);
            canvas.drawCircle(centerX - size * 0.45f,
                    body.bottom, size * 0.14f, paint);
            canvas.drawCircle(centerX + size * 0.45f,
                    body.bottom, size * 0.14f, paint);
        } else if (action == ACTION_CLIMATE) {
            for (int blade = 0; blade < 4; blade++) {
                canvas.save();
                canvas.rotate(blade * 90f, centerX, centerY);
                canvas.drawArc(new RectF(centerX - size * 0.12f,
                                centerY - size * 0.68f,
                                centerX + size * 0.48f,
                                centerY + size * 0.02f),
                        190f, 130f, false, paint);
                canvas.restore();
            }
            canvas.drawCircle(centerX, centerY, size * 0.13f, paint);
        } else if (action == ACTION_CAR_SETTINGS) {
            canvas.drawCircle(centerX, centerY, size * 0.54f, paint);
            canvas.drawCircle(centerX, centerY, size * 0.20f, paint);
            for (int tooth = 0; tooth < 8; tooth++) {
                double angle = Math.toRadians(tooth * 45f);
                canvas.drawLine(
                        centerX + (float) Math.cos(angle) * size * 0.60f,
                        centerY + (float) Math.sin(angle) * size * 0.60f,
                        centerX + (float) Math.cos(angle) * size * 0.78f,
                        centerY + (float) Math.sin(angle) * size * 0.78f,
                        paint);
            }
        } else {
            path.moveTo(centerX - size * 0.72f, centerY - size * 0.02f);
            path.lineTo(centerX, centerY - size * 0.67f);
            path.lineTo(centerX + size * 0.72f, centerY - size * 0.02f);
            path.moveTo(centerX - size * 0.52f, centerY - size * 0.12f);
            path.lineTo(centerX - size * 0.52f, centerY + size * 0.62f);
            path.lineTo(centerX + size * 0.52f, centerY + size * 0.62f);
            path.lineTo(centerX + size * 0.52f, centerY - size * 0.12f);
            canvas.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            for (int index = 0; index < actionBounds.length; index++) {
                if (actionBounds[index].contains(event.getX(), event.getY())) {
                    performClick();
                    if (actionListener != null) {
                        actionListener.onVehicleAction(index);
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
            case 4: return "SNOW";
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

    private void drawPowerNode(Canvas canvas, Bitmap bitmap,
                               float centerX, float centerY,
                               float maxWidth, float maxHeight,
                               String label, String value) {
        float scale = Math.min(maxWidth / bitmap.getWidth(),
                maxHeight / bitmap.getHeight());
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        RectF target = new RectF(
                centerX - drawWidth / 2f, centerY - drawHeight / 2f,
                centerX + drawWidth / 2f, centerY + drawHeight / 2f);
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, null, target, paint);
        drawText(canvas, label, centerX,
                centerY + maxHeight * 0.67f,
                getWidth() * 0.018f, TEXT_PRIMARY, Paint.Align.CENTER);
        drawText(canvas, value, centerX,
                centerY + maxHeight * 0.90f,
                getWidth() * 0.016f, BLUE, Paint.Align.CENTER);
    }

    private void drawBattery(Canvas canvas, RectF target,
                             Float socPercent, Integer batteryPowerKw) {
        float soc = socPercent == null ? 0f
                : Math.max(0f, Math.min(100f, socPercent));
        float left = target.left + target.width() * 0.145f;
        float right = target.left + target.width() * 0.855f;
        float top = target.top + target.height() * 0.29f;
        float bottom = target.top + target.height() * 0.69f;
        RectF liveFrame = new RectF(
                left - target.width() * 0.035f,
                top - target.height() * 0.09f,
                right + target.width() * 0.035f,
                bottom + target.height() * 0.09f);
        paint.setColor(0xFF071629);
        canvas.drawRoundRect(liveFrame, target.height() * 0.12f,
                target.height() * 0.12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, target.height() * 0.025f));
        paint.setColor(0xC52F9DFF);
        canvas.drawRoundRect(liveFrame, target.height() * 0.12f,
                target.height() * 0.12f, paint);
        paint.setStyle(Paint.Style.FILL);
        float gap = target.width() * 0.010f;
        float cellWidth = (right - left - gap * 9f) / 10f;
        for (int index = 0; index < 10; index++) {
            float cellLeft = left + index * (cellWidth + gap);
            RectF cell = new RectF(cellLeft, top,
                    cellLeft + cellWidth, bottom);
            paint.setColor(0xFF102039);
            canvas.drawRoundRect(cell, cellWidth * 0.24f,
                    cellWidth * 0.24f, paint);
            float fill = Math.max(0f, Math.min(1f,
                    (soc - index * 10f) / 10f));
            if (fill > 0f) {
                int save = canvas.save();
                canvas.clipRect(cell.left, cell.top,
                        cell.left + cell.width() * fill, cell.bottom);
                paint.setShader(new LinearGradient(
                        cell.left, cell.top, cell.right, cell.bottom,
                        index < 6 ? 0xFF168BFF : 0xFF25E884,
                        index < 8 ? 0xFF22D8FF : 0xFFB4F52B,
                        Shader.TileMode.CLAMP));
                canvas.drawRoundRect(cell, cellWidth * 0.24f,
                        cellWidth * 0.24f, paint);
                paint.setShader(null);
                canvas.restoreToCount(save);
            }
        }
        paint.setAlpha(255);
        canvas.drawBitmap(batteryShellBitmap, null, target, paint);

        String socLabel = socPercent == null
                ? "SOC —%" : "SOC " + Math.round(soc) + "%";
        float pillWidth = target.width() * 0.42f;
        float pillHeight = target.height() * 0.26f;
        RectF pill = new RectF(
                target.centerX() - pillWidth / 2f,
                target.centerY() - pillHeight / 2f,
                target.centerX() + pillWidth / 2f,
                target.centerY() + pillHeight / 2f);
        paint.setColor(0xB0071224);
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, paint);
        drawText(canvas, socLabel, target.centerX(),
                target.centerY() + getWidth() * 0.009f,
                getWidth() * 0.021f, TEXT_PRIMARY, Paint.Align.CENTER);
        drawText(canvas, "АКБ " + formatPower(batteryPowerKw),
                target.centerX(), target.bottom + getWidth() * 0.016f,
                getWidth() * 0.016f,
                batteryPowerKw != null && batteryPowerKw < -1
                        ? AMBER : ENERGY_BLUE,
                Paint.Align.CENTER);
    }

    private void drawFlowLine(Canvas canvas,
                              float startX, float startY,
                              float endX, float endY,
                              int color, boolean active) {
        Path flow = new Path();
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
        PathMeasure measure = new PathMeasure(flow, false);
        float length = measure.getLength();
        float spacing = getWidth() * 0.035f;
        float phase = (SystemClock.uptimeMillis() % 900L) / 900f;
        float[] position = new float[2];
        float[] tangent = new float[2];
        for (float distance = phase * spacing;
             distance < length; distance += spacing) {
            if (!measure.getPosTan(distance, position, tangent)) {
                continue;
            }
            float angle = (float) Math.atan2(tangent[1], tangent[0]);
            float size = getWidth() * 0.010f;
            path.reset();
            path.moveTo(position[0] + (float) Math.cos(angle) * size,
                    position[1] + (float) Math.sin(angle) * size);
            path.lineTo(position[0] + (float) Math.cos(angle + 2.45f) * size,
                    position[1] + (float) Math.sin(angle + 2.45f) * size);
            path.lineTo(position[0] + (float) Math.cos(angle - 2.45f) * size,
                    position[1] + (float) Math.sin(angle - 2.45f) * size);
            path.close();
            paint.setColor(color);
            canvas.drawPath(path, paint);
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

    private void drawText(Canvas canvas, String value, float x, float y,
                          float size, int color, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(align);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(android.graphics.Typeface.create(
                "sans", android.graphics.Typeface.NORMAL));
        canvas.drawText(value, x, y, paint);
    }
}
