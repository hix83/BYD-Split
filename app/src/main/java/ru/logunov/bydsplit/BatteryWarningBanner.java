package ru.logunov.bydsplit;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** App-local banner above both panes, including embedded applications. No system overlay. */
final class BatteryWarningBanner extends LinearLayout {
    private final Activity activity;
    private final TextView message;
    private boolean active;
    private BatteryImbalanceMonitor.Level shownLevel;
    private final Runnable listener=this::refresh;
    BatteryWarningBanner(Activity activity,Runnable openBattery){
        super(activity);this.activity=activity;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(14),dp(10),dp(8),dp(10));setElevation(dp(16));setVisibility(GONE);setClickable(true);
        message=new TextView(activity);message.setTextSize(15);message.setTypeface(getResources().getFont(R.font.ui7_regular));message.setPadding(0,0,dp(10),0);
        message.setOnClickListener(v->openBattery.run());addView(message,new LayoutParams(0,-2,1));
        TextView dismiss=new TextView(activity);dismiss.setText("×");dismiss.setTextSize(26);dismiss.setTextColor(ThemePalette.color(activity,0xfff4e7dc,0xff6a351d));dismiss.setGravity(Gravity.CENTER);
        dismiss.setContentDescription("Скрыть это предупреждение; наблюдение за батареей продолжится");dismiss.setOnClickListener(v->BatteryWatchController.get().dismiss());addView(dismiss,new LayoutParams(dp(44),dp(48)));
    }
    void attach(){
        FrameLayout content=activity.findViewById(android.R.id.content);
        if(getParent()!=content){if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);
            int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(700));
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.END);lp.topMargin=dp(16);lp.rightMargin=dp(16);content.addView(this,lp);}
        bringToFront();refresh();
    }
    void start(){if(!active){active=true;BatteryWatchController.get().addListener(listener);}else refresh();}
    void stop(){active=false;BatteryWatchController.get().removeListener(listener);setVisibility(GONE);}
    private void refresh(){
        BatteryWatchState.Snapshot s=BatteryWatchController.get().snapshot();
        if(!active||!s.showBanner){setVisibility(GONE);return;}
        String text=s.message()+"\nОткрыть батарею ›";if(!text.contentEquals(message.getText()))message.setText(text);
        if(shownLevel!=s.level){shownLevel=s.level;boolean high=s.level==BatteryImbalanceMonitor.Level.HIGH||s.level==BatteryImbalanceMonitor.Level.DIAGNOSTIC;
            GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(14));bg.setColor(high?ThemePalette.color(activity,0xff593329,0xffffdfd1):ThemePalette.color(activity,0xff4c3d23,0xffffedc6));
            bg.setStroke(dp(1),high?0xffdc8263:0xffc8a35b);setBackground(bg);message.setTextColor(ThemePalette.color(activity,0xffffe9cd,0xff623215));}
        setVisibility(VISIBLE);bringToFront();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
