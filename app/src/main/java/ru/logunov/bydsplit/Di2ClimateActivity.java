package ru.logunov.bydsplit;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** A climate sheet opened from the existing dashboard climate shortcut. */
public final class Di2ClimateActivity extends ThemeActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, Double> values = new HashMap<>();
    private final List<Button> controls = new ArrayList<>();
    private TextView status, temperature, fan;
    private Button power, recirculation, compressor, wind, tempDown, tempUp, fanDown, fanUp, refresh;
    private boolean busy;
    private static final String[] WIND = {"В лицо", "В лицо и ноги", "В ноги", "В ноги и на стекло",
            "На стекло", "В лицо, ноги и на стекло", "В лицо и на стекло"};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        root.setBackgroundColor(getColor(R.color.background));
        TextView title = label("Климат · DiLink 2", 26);
        root.addView(title);
        status = label("Чтение состояния…", 16); root.addView(status);
        LinearLayout switches = row(root);
        power = control(switches, "Питание —", () -> toggle("power"));
        compressor = control(switches, "A/C —", () -> toggle("compressor"));
        recirculation = control(switches, "Рециркуляция —", () -> toggle("recirculation"));
        LinearLayout temperatures = row(root);
        tempDown = control(temperatures, "−", () -> step("temperature", -1));
        temperature = label("Температура —", 24); temperatures.addView(temperature, weighted());
        tempUp = control(temperatures, "+", () -> step("temperature", 1));
        LinearLayout fans = row(root);
        fanDown = control(fans, "−", () -> step("fan", -1));
        fan = label("Вентилятор —", 24); fans.addView(fan, weighted());
        fanUp = control(fans, "+", () -> step("fan", 1));
        wind = control(row(root), "Направление —", () -> new AlertDialog.Builder(this)
                .setTitle("Направление воздуха").setItems(WIND, (dialog, which) -> request("wind", which + 1)).show());
        LinearLayout footer = row(root);
        refresh = button(footer, "Обновить", () -> request("climate", null));
        button(footer, "Штатный климат", this::openOem);
        button(footer, "Закрыть", this::finish);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        getWindow().setDimAmount(0.68f);
        getWindow().getDecorView().post(() -> getWindow().setLayout(
                Math.min(dp(920), (int) (getResources().getDisplayMetrics().widthPixels * .9f)),
                (int) (getResources().getDisplayMetrics().heightPixels * .82f)));
        render();
        if (AppPreferences.isDemoModeEnabled(this)) {
            status.setText("Демо-режим · команды автомобилю отключены"); refresh.setEnabled(false);
        } else request("climate", null);
    }

    private void toggle(String key) {
        Integer v = Di2Snapshot.integer(values, key);
        if (v != null) request(key, v == 1 ? 0 : 1);
    }
    private void step(String key, int increment) {
        Integer v = Di2Snapshot.integer(values, key);
        if (v != null) request(key, v + increment);
    }
    private void request(String operation, Integer value) {
        if (busy || AppPreferences.isDemoModeEnabled(this)) return;
        busy = true; render(); status.setText(value == null ? "Чтение состояния…" : "Ожидание подтверждения автомобиля…");
        worker.execute(() -> {
            String message = null;
            Map<String, Double> fresh = new HashMap<>();
            try {
                LocalAdbManager adb = LocalAdbManager.get(this);
                if (value != null) message = adb.setDi2Climate(operation, value);
                JSONObject reply = adb.readDi2("climate", null);
                fresh.putAll(LocalAdbManager.di2Values(reply));
                if (fresh.isEmpty() && message == null) {
                    String errors = reply.optString("errors", reply.optString("error"));
                    message = errors.contains("SecurityException") ? "У ADB shell нет разрешения BYD SDK"
                            : errors.contains("ClassNotFoundException") ? "В системе отсутствует SDK DiLink 2"
                            : "Автомобиль не передаёт состояние климата";
                }
                if (message == null) message = value == null
                        ? reply.optJSONObject("errors") != null && reply.optJSONObject("errors").length() > 0
                            ? "Часть данных климата недоступна" : "Состояние прочитано"
                        : "Подтверждено автомобилем";
            } catch (Exception e) { message = "Нет подключения ADB"; }
            String result = message;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                values.clear(); values.putAll(fresh); busy = false; render(); status.setText(result);
            });
        });
    }
    private void render() {
        for (Button b : controls) b.setEnabled(false);
        power.setText(onOff("Питание", "power")); compressor.setText(onOff("A/C", "compressor"));
        recirculation.setText(onOff("Рециркуляция", "recirculation"));
        Integer t = Di2Snapshot.integer(values, "temperature"), f = Di2Snapshot.integer(values, "fan");
        Integer unit = Di2Snapshot.integer(values, "temperatureUnit"), w = Di2Snapshot.integer(values, "wind");
        temperature.setText(t == null || unit == null ? "Температура —" : t + (unit == 1 ? " °C" : " °F"));
        fan.setText(f == null ? "Вентилятор —" : "Вентилятор · " + f + "/7");
        wind.setText(w == null ? "Направление —" : WIND[w - 1]);
        refresh.setEnabled(!busy);
        if (busy || AppPreferences.isDemoModeEnabled(this)) return;
        power.setEnabled(values.containsKey("power")); compressor.setEnabled(values.containsKey("compressor"));
        recirculation.setEnabled(values.containsKey("recirculation")); wind.setEnabled(w != null);
        boolean celsius = unit != null && unit == 1 && t != null;
        tempDown.setEnabled(celsius && t > 18 && t <= 33); tempUp.setEnabled(celsius && t < 32 && t >= 17);
        fanDown.setEnabled(f != null && f > 0); fanUp.setEnabled(f != null && f < 7);
    }
    private String onOff(String label, String key) {
        Integer v = Di2Snapshot.integer(values, key);
        return label + (v == null ? " —" : v == 1 ? " · ВКЛ" : " · ВЫКЛ");
    }
    private void openOem() {
        Intent intent = new Intent("OPEN_AIR_CONDITIONING_FUNCTION");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra("OPEN_AIR_CONDITIONING_FRAGMENT_ID", 1000); intent.putExtra("from", "Navbar");
        intent.setComponent(new ComponentName("com.byd.airconditioning",
                "com.byd.airconditioning.mainactivity.FullScreenMainActivity"));
        try { startActivity(intent); }
        catch (RuntimeException e) { status.setText("Штатное приложение климата недоступно"); }
    }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size);
        v.setTextColor(getColor(R.color.text_primary)); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(4), dp(8), dp(4), dp(8)); return v;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2)); return r;
    }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private Button control(LinearLayout row, String text, Runnable action) {
        Button b = button(row, text, action); controls.add(b); return b;
    }
    private Button button(LinearLayout row, String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextSize(16); b.setAllCaps(false);
        b.setTextColor(getColor(R.color.text_primary)); b.setMinHeight(dp(56));
        row.addView(b, weighted()); b.setOnClickListener(v -> action.run()); return b;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
