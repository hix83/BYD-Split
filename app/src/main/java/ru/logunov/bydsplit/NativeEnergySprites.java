package ru.logunov.bydsplit;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;

/** Unmodified frame sequences and xhdpi anchors from the OTA Energy layout. */
final class NativeEnergySprites {
    private static final int[][] IDS = {
        {R.drawable.ui7_flow_engine_generator_0, R.drawable.ui7_flow_engine_generator_1, R.drawable.ui7_flow_engine_generator_2, R.drawable.ui7_flow_engine_generator_3, R.drawable.ui7_flow_engine_generator_4, R.drawable.ui7_flow_engine_generator_5, R.drawable.ui7_flow_engine_generator_6, R.drawable.ui7_flow_engine_generator_7, R.drawable.ui7_flow_engine_generator_8, R.drawable.ui7_flow_engine_generator_9, R.drawable.ui7_flow_engine_generator_10, R.drawable.ui7_flow_engine_generator_11, R.drawable.ui7_flow_engine_generator_12, R.drawable.ui7_flow_engine_generator_13, R.drawable.ui7_flow_engine_generator_14, R.drawable.ui7_flow_engine_generator_15, R.drawable.ui7_flow_engine_generator_16, R.drawable.ui7_flow_engine_generator_17, R.drawable.ui7_flow_engine_generator_18, R.drawable.ui7_flow_engine_generator_19},
        {R.drawable.ui7_flow_engine_battery_0, R.drawable.ui7_flow_engine_battery_1, R.drawable.ui7_flow_engine_battery_2, R.drawable.ui7_flow_engine_battery_3, R.drawable.ui7_flow_engine_battery_4, R.drawable.ui7_flow_engine_battery_5, R.drawable.ui7_flow_engine_battery_6, R.drawable.ui7_flow_engine_battery_7, R.drawable.ui7_flow_engine_battery_8, R.drawable.ui7_flow_engine_battery_9, R.drawable.ui7_flow_engine_battery_10, R.drawable.ui7_flow_engine_battery_11, R.drawable.ui7_flow_engine_battery_12, R.drawable.ui7_flow_engine_battery_13, R.drawable.ui7_flow_engine_battery_14, R.drawable.ui7_flow_engine_battery_15, R.drawable.ui7_flow_engine_battery_16, R.drawable.ui7_flow_engine_battery_17, R.drawable.ui7_flow_engine_battery_18, R.drawable.ui7_flow_engine_battery_19},
        {R.drawable.ui7_flow_engine_wheels_0, R.drawable.ui7_flow_engine_wheels_1, R.drawable.ui7_flow_engine_wheels_2, R.drawable.ui7_flow_engine_wheels_3, R.drawable.ui7_flow_engine_wheels_4, R.drawable.ui7_flow_engine_wheels_5, R.drawable.ui7_flow_engine_wheels_6, R.drawable.ui7_flow_engine_wheels_7, R.drawable.ui7_flow_engine_wheels_8, R.drawable.ui7_flow_engine_wheels_9, R.drawable.ui7_flow_engine_wheels_10, R.drawable.ui7_flow_engine_wheels_11, R.drawable.ui7_flow_engine_wheels_12, R.drawable.ui7_flow_engine_wheels_13, R.drawable.ui7_flow_engine_wheels_14, R.drawable.ui7_flow_engine_wheels_15, R.drawable.ui7_flow_engine_wheels_16, R.drawable.ui7_flow_engine_wheels_17, R.drawable.ui7_flow_engine_wheels_18, R.drawable.ui7_flow_engine_wheels_19},
        {R.drawable.ui7_flow_battery_drive_0, R.drawable.ui7_flow_battery_drive_1, R.drawable.ui7_flow_battery_drive_2, R.drawable.ui7_flow_battery_drive_3, R.drawable.ui7_flow_battery_drive_4, R.drawable.ui7_flow_battery_drive_5, R.drawable.ui7_flow_battery_drive_6, R.drawable.ui7_flow_battery_drive_7, R.drawable.ui7_flow_battery_drive_8, R.drawable.ui7_flow_battery_drive_9, R.drawable.ui7_flow_battery_drive_10, R.drawable.ui7_flow_battery_drive_11, R.drawable.ui7_flow_battery_drive_12, R.drawable.ui7_flow_battery_drive_13, R.drawable.ui7_flow_battery_drive_14, R.drawable.ui7_flow_battery_drive_15, R.drawable.ui7_flow_battery_drive_16, R.drawable.ui7_flow_battery_drive_17, R.drawable.ui7_flow_battery_drive_18, R.drawable.ui7_flow_battery_drive_19},
        {R.drawable.ui7_flow_drive_battery_0, R.drawable.ui7_flow_drive_battery_1, R.drawable.ui7_flow_drive_battery_2, R.drawable.ui7_flow_drive_battery_3, R.drawable.ui7_flow_drive_battery_4, R.drawable.ui7_flow_drive_battery_5, R.drawable.ui7_flow_drive_battery_6, R.drawable.ui7_flow_drive_battery_7, R.drawable.ui7_flow_drive_battery_8, R.drawable.ui7_flow_drive_battery_9, R.drawable.ui7_flow_drive_battery_10, R.drawable.ui7_flow_drive_battery_11, R.drawable.ui7_flow_drive_battery_12, R.drawable.ui7_flow_drive_battery_13, R.drawable.ui7_flow_drive_battery_14, R.drawable.ui7_flow_drive_battery_15, R.drawable.ui7_flow_drive_battery_16, R.drawable.ui7_flow_drive_battery_17, R.drawable.ui7_flow_drive_battery_18, R.drawable.ui7_flow_drive_battery_19},
        {R.drawable.ui7_flow_drive_left_0, R.drawable.ui7_flow_drive_left_1, R.drawable.ui7_flow_drive_left_2, R.drawable.ui7_flow_drive_left_3, R.drawable.ui7_flow_drive_left_4, R.drawable.ui7_flow_drive_left_5, R.drawable.ui7_flow_drive_left_6, R.drawable.ui7_flow_drive_left_7, R.drawable.ui7_flow_drive_left_8, R.drawable.ui7_flow_drive_left_9, R.drawable.ui7_flow_drive_left_10, R.drawable.ui7_flow_drive_left_11, R.drawable.ui7_flow_drive_left_12, R.drawable.ui7_flow_drive_left_13, R.drawable.ui7_flow_drive_left_14, R.drawable.ui7_flow_drive_left_15, R.drawable.ui7_flow_drive_left_16, R.drawable.ui7_flow_drive_left_17, R.drawable.ui7_flow_drive_left_18, R.drawable.ui7_flow_drive_left_19},
        {R.drawable.ui7_flow_drive_right_0, R.drawable.ui7_flow_drive_right_1, R.drawable.ui7_flow_drive_right_2, R.drawable.ui7_flow_drive_right_3, R.drawable.ui7_flow_drive_right_4, R.drawable.ui7_flow_drive_right_5, R.drawable.ui7_flow_drive_right_6, R.drawable.ui7_flow_drive_right_7, R.drawable.ui7_flow_drive_right_8, R.drawable.ui7_flow_drive_right_9, R.drawable.ui7_flow_drive_right_10, R.drawable.ui7_flow_drive_right_11, R.drawable.ui7_flow_drive_right_12, R.drawable.ui7_flow_drive_right_13, R.drawable.ui7_flow_drive_right_14, R.drawable.ui7_flow_drive_right_15, R.drawable.ui7_flow_drive_right_16, R.drawable.ui7_flow_drive_right_17, R.drawable.ui7_flow_drive_right_18, R.drawable.ui7_flow_drive_right_19},
        {R.drawable.ui7_flow_regen_left_0, R.drawable.ui7_flow_regen_left_1, R.drawable.ui7_flow_regen_left_2, R.drawable.ui7_flow_regen_left_3, R.drawable.ui7_flow_regen_left_4, R.drawable.ui7_flow_regen_left_5, R.drawable.ui7_flow_regen_left_6, R.drawable.ui7_flow_regen_left_7, R.drawable.ui7_flow_regen_left_8, R.drawable.ui7_flow_regen_left_9, R.drawable.ui7_flow_regen_left_10, R.drawable.ui7_flow_regen_left_11, R.drawable.ui7_flow_regen_left_12, R.drawable.ui7_flow_regen_left_13, R.drawable.ui7_flow_regen_left_14, R.drawable.ui7_flow_regen_left_15, R.drawable.ui7_flow_regen_left_16, R.drawable.ui7_flow_regen_left_17, R.drawable.ui7_flow_regen_left_18, R.drawable.ui7_flow_regen_left_19},
        {R.drawable.ui7_flow_regen_right_0, R.drawable.ui7_flow_regen_right_1, R.drawable.ui7_flow_regen_right_2, R.drawable.ui7_flow_regen_right_3, R.drawable.ui7_flow_regen_right_4, R.drawable.ui7_flow_regen_right_5, R.drawable.ui7_flow_regen_right_6, R.drawable.ui7_flow_regen_right_7, R.drawable.ui7_flow_regen_right_8, R.drawable.ui7_flow_regen_right_9, R.drawable.ui7_flow_regen_right_10, R.drawable.ui7_flow_regen_right_11, R.drawable.ui7_flow_regen_right_12, R.drawable.ui7_flow_regen_right_13, R.drawable.ui7_flow_regen_right_14, R.drawable.ui7_flow_regen_right_15, R.drawable.ui7_flow_regen_right_16, R.drawable.ui7_flow_regen_right_17, R.drawable.ui7_flow_regen_right_18, R.drawable.ui7_flow_regen_right_19}
    };
    private final Bitmap[][] frames = new Bitmap[IDS.length][];
    private final Resources resources;
    NativeEnergySprites(Resources resources) { this.resources = resources; }

    private void draw(Canvas canvas, Paint paint, int sequence, float x, float y) {
        if (frames[sequence] == null) {
            frames[sequence] = new Bitmap[20];
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            for (int i = 0; i < 20; i++) {
                frames[sequence][i] = BitmapFactory.decodeResource(resources, IDS[sequence][i], options);
            }
        }
        int frame = (int) ((SystemClock.uptimeMillis() / 80L) % 20);
        paint.setAlpha(255);
        paint.setShader(null);
        canvas.drawBitmap(frames[sequence][frame], x, y, paint);
    }

    void draw(Canvas canvas, Paint paint, DmiEnergyFlow state) {
        if (state.engineToGenerator && !state.generatorToBattery) draw(canvas, paint, 0, 328, 642);
        if (state.generatorToBattery) draw(canvas, paint, 1, 328, 580);
        if (state.engineToWheels) draw(canvas, paint, 2, 222, 548);
        if (state.batteryToDrive) draw(canvas, paint, 3, 264, 550);
        if (state.driveToBattery && !state.generatorToBattery) draw(canvas, paint, 4, 264, 550);
        if (state.motorToWheels) {
            draw(canvas, paint, 5, 264, 550);
            draw(canvas, paint, 6, 264, 550);
        }
        if (state.wheelsToMotor) {
            draw(canvas, paint, 7, 264, 550);
            draw(canvas, paint, 8, 264, 550);
        }
    }
}
