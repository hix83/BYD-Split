package ru.logunov.bydsplit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class QuickIconDrawable extends Drawable {
    private static final int ICON_COLOR = 0xFFD9E2EE;
    private enum Kind { YANDEX_MUSIC, MAX, SONG, CAMERA, SETTINGS }

    private final Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private QuickIconDrawable(Kind kind) {
        this.kind = kind;
    }

    static Drawable yandexMusic() {
        return new QuickIconDrawable(Kind.YANDEX_MUSIC);
    }

    static Drawable max() {
        return new QuickIconDrawable(Kind.MAX);
    }

    static Drawable song() {
        return new QuickIconDrawable(Kind.SONG);
    }

    static Drawable camera() {
        return new QuickIconDrawable(Kind.CAMERA);
    }

    static Drawable settings() {
        return new QuickIconDrawable(Kind.SETTINGS);
    }

    @Override
    public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        switch (kind) {
            case YANDEX_MUSIC:
                drawYandexStar(canvas, bounds);
                break;
            case MAX:
                drawMaxBubble(canvas, bounds);
                break;
            case SONG:
                drawSong(canvas, bounds);
                break;
            case CAMERA:
                drawCamera(canvas, bounds);
                break;
            case SETTINGS:
                drawSettings(canvas, bounds);
                break;
        }
    }

    private void drawYandexStar(Canvas canvas, RectF bounds) {
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        float outer = bounds.width() * 0.39f;
        float inner = outer * 0.24f;
        path.reset();
        for (int index = 0; index < 16; index++) {
            double angle = -Math.PI / 2 + index * Math.PI / 8;
            float radius = index % 2 == 0 ? outer : inner;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        paint.setColor(ICON_COLOR);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    private void drawMaxBubble(Canvas canvas, RectF bounds) {
        float width = bounds.width();
        RectF bubble = new RectF(bounds.left + width * 0.16f,
                bounds.top + width * 0.20f, bounds.right - width * 0.16f,
                bounds.bottom - width * 0.24f);
        prepareStroke(width * 0.070f);
        canvas.drawRoundRect(bubble, width * 0.20f, width * 0.20f, paint);
        path.reset();
        path.moveTo(bounds.left + width * 0.25f, bounds.bottom - width * 0.30f);
        path.lineTo(bounds.left + width * 0.18f, bounds.bottom - width * 0.09f);
        path.lineTo(bounds.left + width * 0.43f, bounds.bottom - width * 0.25f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSong(Canvas canvas, RectF bounds) {
        float w = bounds.width();
        float l = bounds.left;
        float t = bounds.top;
        prepareStroke(w * 0.070f);

        // BYD Dynasty-style seal-script 宋: roof (宀) and a flowing 木.
        path.reset();
        path.moveTo(l + w * 0.50f, t + w * 0.10f);
        path.lineTo(l + w * 0.50f, t + w * 0.19f);
        path.moveTo(l + w * 0.24f, t + w * 0.30f);
        path.quadTo(l + w * 0.50f, t + w * 0.20f,
                l + w * 0.76f, t + w * 0.30f);
        path.lineTo(l + w * 0.72f, t + w * 0.40f);
        path.moveTo(l + w * 0.28f, t + w * 0.31f);
        path.lineTo(l + w * 0.28f, t + w * 0.41f);
        path.moveTo(l + w * 0.32f, t + w * 0.43f);
        path.lineTo(l + w * 0.68f, t + w * 0.43f);
        path.moveTo(l + w * 0.50f, t + w * 0.40f);
        path.lineTo(l + w * 0.50f, t + w * 0.84f);
        path.moveTo(l + w * 0.28f, t + w * 0.57f);
        path.lineTo(l + w * 0.72f, t + w * 0.57f);
        path.moveTo(l + w * 0.49f, t + w * 0.58f);
        path.cubicTo(l + w * 0.44f, t + w * 0.68f,
                l + w * 0.35f, t + w * 0.77f,
                l + w * 0.23f, t + w * 0.83f);
        path.moveTo(l + w * 0.51f, t + w * 0.58f);
        path.cubicTo(l + w * 0.56f, t + w * 0.68f,
                l + w * 0.65f, t + w * 0.77f,
                l + w * 0.77f, t + w * 0.83f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCamera(Canvas canvas, RectF bounds) {
        float width = bounds.width();
        RectF body = new RectF(
                bounds.left + width * 0.18f,
                bounds.top + width * 0.30f,
                bounds.right - width * 0.18f,
                bounds.bottom - width * 0.22f);
        prepareStroke(width * 0.065f);
        canvas.drawRoundRect(body, width * 0.10f, width * 0.10f, paint);
        canvas.drawCircle(bounds.centerX(), bounds.centerY() + width * 0.05f,
                width * 0.13f, paint);
        canvas.drawLine(bounds.left + width * 0.32f,
                bounds.top + width * 0.30f,
                bounds.left + width * 0.40f,
                bounds.top + width * 0.22f, paint);
        canvas.drawLine(bounds.left + width * 0.40f,
                bounds.top + width * 0.22f,
                bounds.left + width * 0.58f,
                bounds.top + width * 0.22f, paint);
        canvas.drawLine(bounds.left + width * 0.58f,
                bounds.top + width * 0.22f,
                bounds.left + width * 0.66f,
                bounds.top + width * 0.30f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSettings(Canvas canvas, RectF bounds) {
        float r = bounds.width() * 0.27f;
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        prepareStroke(bounds.width() * 0.065f);
        canvas.drawCircle(cx, cy, r, paint);
        canvas.drawCircle(cx, cy, r * 0.34f, paint);
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4;
            canvas.drawLine(cx + (float) Math.cos(angle) * r,
                    cy + (float) Math.sin(angle) * r,
                    cx + (float) Math.cos(angle) * r * 1.32f,
                    cy + (float) Math.sin(angle) * r * 1.32f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void prepareStroke(float width) {
        paint.setColor(ICON_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
