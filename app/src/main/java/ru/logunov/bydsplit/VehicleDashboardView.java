package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

final class VehicleDashboardView extends View {
    private static final int TEXT_PRIMARY = 0xFFEEF3FA;
    private static final int TEXT_SECONDARY = 0xFF8E9AAF;
    private static final int BLUE = 0xFF4C8DFF;
    private static final int ENERGY_BLUE = 0xFF55B8FF;
    private static final int HEALTHY = 0xFF55C98A;
    private static final int AMBER = 0xFFF0A95B;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    VehicleDashboardView(Context context) {
        super(context);
        setContentDescription(
                "Состояние BYD Song L DM-i: скорость, шины и гибридная система");
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
        drawModes(canvas, width, height);
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
        drawText(canvas, "72", width * 0.075f, height * 0.13f,
                width * 0.13f, TEXT_PRIMARY, Paint.Align.LEFT);
        drawText(canvas, "км/ч", width * 0.08f, height * 0.18f,
                width * 0.032f, TEXT_SECONDARY, Paint.Align.LEFT);

        float compassX = width * 0.82f;
        float compassY = height * 0.12f;
        float compassRadius = width * 0.095f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * 0.002f);
        paint.setColor(0x506D7E99);
        canvas.drawCircle(compassX, compassY, compassRadius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFDCE8FA);
        path.reset();
        path.moveTo(compassX + compassRadius * 0.53f,
                compassY - compassRadius * 0.58f);
        path.lineTo(compassX + compassRadius * 0.06f,
                compassY + compassRadius * 0.07f);
        path.lineTo(compassX + compassRadius * 0.26f,
                compassY + compassRadius * 0.18f);
        path.close();
        canvas.drawPath(path, paint);
        drawText(canvas, "СВ", compassX + compassRadius * 0.55f,
                compassY + compassRadius * 0.72f,
                width * 0.023f, TEXT_SECONDARY, Paint.Align.CENTER);

        drawText(canvas, "HYBRID  ·  NORMAL", width * 0.5f,
                height * 0.052f, width * 0.025f,
                BLUE, Paint.Align.CENTER);
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

        float carWidth = width * 0.20f;
        float carHeight = height * 0.29f;
        float carLeft = width * 0.5f - carWidth / 2f;
        float carTop = height * 0.255f;
        RectF car = new RectF(carLeft, carTop,
                carLeft + carWidth, carTop + carHeight);
        paint.setShader(new LinearGradient(
                car.left, car.top, car.right, car.bottom,
                new int[]{0xFFB7C9CE, 0xFF789399, 0xFFB4C5C8},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(car, carWidth * 0.34f, carWidth * 0.26f, paint);
        paint.setShader(null);

        RectF roof = new RectF(
                car.left + carWidth * 0.16f,
                car.top + carHeight * 0.17f,
                car.right - carWidth * 0.16f,
                car.bottom - carHeight * 0.25f);
        paint.setColor(0xFF0A1523);
        canvas.drawRoundRect(roof, carWidth * 0.16f, carWidth * 0.12f, paint);
        paint.setColor(0xFF5B9FFF);
        canvas.drawRoundRect(new RectF(
                car.left + carWidth * 0.15f,
                car.bottom - carHeight * 0.09f,
                car.right - carWidth * 0.15f,
                car.bottom - carHeight * 0.065f),
                carWidth * 0.03f, carWidth * 0.03f, paint);

        float labelSize = width * 0.029f;
        drawTireStatus(canvas, "2.4 bar", width * 0.28f,
                height * 0.32f, car.left, labelSize, true);
        drawTireStatus(canvas, "2.4 bar", width * 0.72f,
                height * 0.32f, car.right, labelSize, false);
        drawTireStatus(canvas, "2.3 bar", width * 0.28f,
                height * 0.48f, car.left, labelSize, true);
        drawTireStatus(canvas, "2.3 bar", width * 0.72f,
                height * 0.48f, car.right, labelSize, false);
    }

    private void drawTireStatus(Canvas canvas, String value, float labelX,
                                float y, float carEdge, float size,
                                boolean left) {
        Paint.Align align = left ? Paint.Align.RIGHT : Paint.Align.LEFT;
        drawText(canvas, value, labelX, y, size, TEXT_PRIMARY, align);
        paint.setColor(0xA04C8DFF);
        paint.setStrokeWidth(Math.max(1f, size * 0.09f));
        float start = left ? labelX + size * 0.35f : carEdge;
        float end = left ? carEdge : labelX - size * 0.35f;
        canvas.drawLine(start, y - size * 0.25f,
                end, y - size * 0.25f, paint);
    }

    private void drawPowerFlow(Canvas canvas, float width, float height) {
        float titleY = height * 0.625f;
        drawText(canvas, "СЕРИЙНЫЙ HEV  ·  ЗАРЯД", width * 0.5f,
                titleY, width * 0.030f, TEXT_PRIMARY, Paint.Align.CENTER);

        float nodeY = height * 0.72f;
        float nodeSize = width * 0.12f;
        float engineX = width * 0.16f;
        float generatorX = width * 0.39f;
        float motorX = width * 0.64f;
        float wheelX = width * 0.86f;
        drawNode(canvas, engineX, nodeY, nodeSize, "ДВС");
        drawNode(canvas, generatorX, nodeY, nodeSize, "G");
        drawNode(canvas, motorX, nodeY, nodeSize, "M");
        drawNode(canvas, wheelX, nodeY, nodeSize, "◉");
        drawArrow(canvas, engineX + nodeSize * 0.55f, nodeY,
                generatorX - nodeSize * 0.55f, nodeY, AMBER);
        drawArrow(canvas, generatorX + nodeSize * 0.55f, nodeY,
                motorX - nodeSize * 0.55f, nodeY, ENERGY_BLUE);
        drawArrow(canvas, motorX + nodeSize * 0.55f, nodeY,
                wheelX - nodeSize * 0.55f, nodeY, ENERGY_BLUE);

        float batteryY = height * 0.82f;
        float batteryWidth = width * 0.24f;
        RectF battery = new RectF(
                width * 0.5f - batteryWidth / 2f,
                batteryY - nodeSize * 0.34f,
                width * 0.5f + batteryWidth / 2f,
                batteryY + nodeSize * 0.34f);
        paint.setColor(0xFF162A43);
        canvas.drawRoundRect(battery, width * 0.016f, width * 0.016f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * 0.003f);
        paint.setColor(ENERGY_BLUE);
        canvas.drawRoundRect(battery, width * 0.016f, width * 0.016f, paint);
        paint.setStyle(Paint.Style.FILL);
        drawText(canvas, "Батарея  68%", width * 0.5f,
                batteryY + width * 0.011f, width * 0.026f,
                TEXT_PRIMARY, Paint.Align.CENTER);
        drawArrow(canvas, generatorX, nodeY + nodeSize * 0.54f,
                width * 0.46f, battery.top, ENERGY_BLUE);
        drawArrow(canvas, width * 0.54f, battery.top,
                motorX, nodeY + nodeSize * 0.54f, ENERGY_BLUE);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * 0.002f);
        paint.setColor(0x506D7E99);
        path.reset();
        path.moveTo(engineX, nodeY + nodeSize * 0.65f);
        path.lineTo(engineX, height * 0.865f);
        path.lineTo(wheelX, height * 0.865f);
        path.lineTo(wheelX, nodeY + nodeSize * 0.65f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawModes(Canvas canvas, float width, float height) {
        String[] labels = {"EV", "СЕРИЯ", "ПАРАЛЛЕЛЬ", "РЕКУП."};
        float gap = width * 0.014f;
        float left = width * 0.055f;
        float totalWidth = width * 0.89f;
        float chipWidth = (totalWidth - gap * 3f) / 4f;
        float top = height * 0.91f;
        float bottom = height * 0.972f;
        for (int index = 0; index < labels.length; index++) {
            float chipLeft = left + index * (chipWidth + gap);
            RectF chip = new RectF(chipLeft, top,
                    chipLeft + chipWidth, bottom);
            paint.setColor(index == 1 ? 0xFF254A78 : 0x66172234);
            canvas.drawRoundRect(chip, width * 0.018f,
                    width * 0.018f, paint);
            drawText(canvas, labels[index], chip.centerX(),
                    chip.centerY() + width * 0.010f,
                    width * 0.021f,
                    index == 1 ? TEXT_PRIMARY : TEXT_SECONDARY,
                    Paint.Align.CENTER);
        }
    }

    private void drawNode(Canvas canvas, float centerX, float centerY,
                          float size, String label) {
        RectF node = new RectF(
                centerX - size / 2f, centerY - size / 2f,
                centerX + size / 2f, centerY + size / 2f);
        paint.setColor(0xFF152238);
        canvas.drawRoundRect(node, size * 0.22f, size * 0.22f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.025f);
        paint.setColor(0x884C8DFF);
        canvas.drawRoundRect(node, size * 0.22f, size * 0.22f, paint);
        paint.setStyle(Paint.Style.FILL);
        drawText(canvas, label, centerX, centerY + size * 0.12f,
                size * 0.28f, TEXT_PRIMARY, Paint.Align.CENTER);
    }

    private void drawArrow(Canvas canvas, float startX, float startY,
                           float endX, float endY, int color) {
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(2f, getWidth() * 0.006f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(startX, startY, endX, endY, paint);
        float angle = (float) Math.atan2(endY - startY, endX - startX);
        float arrow = getWidth() * 0.018f;
        path.reset();
        path.moveTo(endX, endY);
        path.lineTo(endX - arrow * (float) Math.cos(angle - 0.6f),
                endY - arrow * (float) Math.sin(angle - 0.6f));
        path.lineTo(endX - arrow * (float) Math.cos(angle + 0.6f),
                endY - arrow * (float) Math.sin(angle + 0.6f));
        path.close();
        canvas.drawPath(path, paint);
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
