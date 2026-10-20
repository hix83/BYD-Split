package ru.logunov.bydsplit;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
/** Short non-interactive overlay above both embedded application panes. */
final class AutomationBanner {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static TextView view;
    private static WindowManager manager;
    private static final Runnable HIDE=()->{if(view!=null){try{manager.removeView(view);}catch(RuntimeException ignored){}view=null;}};
    static void show(Context context,String rule){MAIN.post(()->{
        MAIN.removeCallbacks(HIDE);HIDE.run();
        Context c=context.getApplicationContext();String message="✓ Автоматизация выполнена · "+rule;
        if(!Settings.canDrawOverlays(c)){Toast.makeText(c,message,Toast.LENGTH_LONG).show();return;}
        manager=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
        TextView label=new TextView(c);label.setText(message);label.setTextSize(17);label.setTextColor(ThemePalette.text(c));
        label.setTypeface(c.getResources().getFont(R.font.ui7_regular));int pad=Math.round(16*c.getResources().getDisplayMetrics().density);
        label.setPadding(pad,pad/2,pad,pad/2);label.setMaxLines(2);
        GradientDrawable bg=new GradientDrawable();bg.setColor(c.getColor(R.color.surface_light));bg.setCornerRadius(pad);bg.setStroke(2,c.getColor(R.color.success));label.setBackground(bg);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=pad;p.setTitle("BYD Split · Автоматизация");
        try{manager.addView(label,p);view=label;MAIN.postDelayed(HIDE,4500);}catch(RuntimeException e){Toast.makeText(c,message,Toast.LENGTH_LONG).show();}
    });}
}
