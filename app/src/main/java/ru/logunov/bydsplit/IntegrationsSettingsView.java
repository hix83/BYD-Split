package ru.logunov.bydsplit;

import android.app.Dialog;
import android.content.Context;
import android.view.*;
import android.widget.*;

/** Settings home for external services, messaging and future AI connections. */
final class IntegrationsSettingsView extends LinearLayout {
    IntegrationsSettingsView(Context context){
        super(context);setOrientation(VERTICAL);
        int padding=Math.round(18*getResources().getDisplayMetrics().density);
        TextView title=new TextView(context);title.setText("Интеграции");title.setTextSize(24);title.setTextColor(ThemePalette.text(context));title.setPadding(padding,padding,padding,0);addView(title);
        TextView description=new TextView(context);description.setText("Подключения внешних сервисов, обмен данными и ИИ.");description.setTextSize(14);description.setTextColor(ThemePalette.text(context));description.setPadding(padding,padding/2,padding,padding/2);addView(description);
        addView(new TelegramSettingsView(context),new LinearLayout.LayoutParams(-1,0,1));
    }
    static Dialog show(Context context){
        Dialog dialog=new Dialog(context,ThemePalette.dialogTheme(context));
        LinearLayout panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);int padding=Math.round(12*context.getResources().getDisplayMetrics().density);panel.setPadding(padding,padding,padding,padding);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(ThemePalette.color(context,0xff222b35,0xffe8eef5));background.setCornerRadius(padding*2);panel.setBackground(background);
        Button close=new Button(context);close.setText("Закрыть интеграции");close.setAllCaps(false);close.setTextColor(ThemePalette.color(context,0xffc9e4ff,0xff164775));close.setOnClickListener(v->dialog.dismiss());panel.addView(close,new LinearLayout.LayoutParams(-1,padding*4));
        panel.addView(new IntegrationsSettingsView(context),new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(panel);dialog.show();
        Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        window.setLayout(Math.min(Math.round(720*context.getResources().getDisplayMetrics().density),context.getResources().getDisplayMetrics().widthPixels-32),Math.min(Math.round(650*context.getResources().getDisplayMetrics().density),context.getResources().getDisplayMetrics().heightPixels-32));return dialog;
    }
}
