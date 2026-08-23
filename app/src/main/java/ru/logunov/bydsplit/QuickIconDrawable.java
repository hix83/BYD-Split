package ru.logunov.bydsplit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

final class QuickIconDrawable extends Drawable {
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
        float radius = Math.min(bounds.width(), bounds.height()) * 0.23f;
        switch (kind) {
            case YANDEX_MUSIC:
                paint.setShader(null);
                paint.setColor(Color.rgb(5, 5, 5));
                canvas.drawRoundRect(bounds, radius, radius, paint);
                drawYandexStar(canvas, bounds);
                break;
            case MAX:
                paint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        new int[]{0xFF7442F5, 0xFF426AF0, 0xFF2FA5F1},
                        null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(bounds, radius, radius, paint);
                paint.setShader(null);
                drawMaxBubble(canvas, bounds);
                break;
            case SONG:
                paint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        0xFF355A91, 0xFF14243C, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(bounds, radius, radius, paint);
                paint.setShader(null);
                drawSong(canvas, bounds);
                break;
            case CAMERA:
                paint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        0xFF526782, 0xFF202D40, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(bounds, radius, radius, paint);
                paint.setShader(null);
                drawCamera(canvas, bounds);
                break;
            case SETTINGS:
                paint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        0xFF526782, 0xFF202D40, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(bounds, radius, radius, paint);
                paint.setShader(null);
                drawSettings(canvas, bounds);
                break;
        }
    }

    private void drawYandexStar(Canvas canvas, RectF bounds) {
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        float outer = bounds.width() * 0.34f;
        float inner = outer * 0.28f;
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
        paint.setColor(0xFFFFD926);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    private void drawMaxBubble(Canvas canvas, RectF bounds) {
        float width = bounds.width();
        RectF bubble = new RectF(
                bounds.left + width * 0.21f,
                bounds.top + width * 0.25f,
                bounds.right - width * 0.21f,
                bounds.bottom - width * 0.28f);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(bubble, width * 0.19f, width * 0.19f, paint);
        path.reset();
        path.moveTo(bounds.left + width * 0.25f,
                bounds.bottom - width * 0.37f);
        path.lineTo(bounds.left + width * 0.19f,
                bounds.bottom - width * 0.18f);
        path.lineTo(bounds.left + width * 0.42f,
                bounds.bottom - width * 0.31f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawSong(Canvas canvas, RectF bounds) {
        paint.setColor(0xFFE8EFFA);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        paint.setTextSize(bounds.width() * 0.55f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = bounds.centerY()
                - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText("宋", bounds.centerX(), baseline, paint);
    }

    private void drawCamera(Canvas canvas, RectF bounds) {
        float width = bounds.width();
        RectF body = new RectF(
                bounds.left + width * 0.18f,
                bounds.top + width * 0.30f,
                bounds.right - width * 0.18f,
                bounds.bottom - width * 0.22f);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width * 0.055f);
        paint.setStrokeCap(Paint.Cap.ROUND);
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
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(bounds.width() * 0.075f);
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
