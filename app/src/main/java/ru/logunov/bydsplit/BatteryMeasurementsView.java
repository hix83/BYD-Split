package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;
import java.util.Locale;

/** Numbered BMS plots: intentionally makes no claim about physical sensor positions. */
final class BatteryMeasurementsView extends View {
    private final boolean temperature;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int text,muted,grid;
    private int[] readings;
    private boolean attempted;
    private int selected=-1;
    private static final int[] CELL_TICKS={0,25,52,79,105};
    BatteryMeasurementsView(Context context,boolean temperature) {
        super(context);this.temperature=temperature;
        text=context.getColor(R.color.text_primary);muted=context.getColor(R.color.text_secondary);
        grid=ThemePalette.color(context,0xff3b4754,0xffbec8d2);
        GradientDrawable bg=new GradientDrawable();bg.setColor(ThemePalette.color(context,0xff202a35,0xffe8ecf0));bg.setCornerRadius(dp(14));setBackground(bg);
        paint.setTypeface(getResources().getFont(R.font.ui7_regular));setClickable(true);
    }
    void setReadings(int[] data,boolean attempted) {
        if(Arrays.equals(readings,data)&&this.attempted==attempted)return;
        readings=data==null?null:data.clone();this.attempted=attempted;
        setContentDescription((temperature?"Температуры датчиков":"Напряжения ячеек")
                +(data==null?" · показания недоступны":" · "+BatteryCellReadings.csv(data)+(temperature?" градусов Цельсия":" милливольт")));
        invalidate();
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private void label(Canvas c,String s,float x,float y,int size,int color){paint.setColor(color);paint.setTextSize(dp(size));paint.setStyle(Paint.Style.FILL);c.drawText(s,x,y,paint);}
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        label(c,temperature?(readings==null?"Температуры датчиков":"Температуры · "+readings.length+" датчиков"):(readings==null?"Напряжения ячеек":"Напряжения · "+readings.length+" ячеек"),dp(14),dp(25),16,text);
        label(c,temperature?"По номерам BMS · расположение не установлено":"Шкала мВ от минимума · нажмите на столбец",dp(14),dp(46),11,muted);
        if(readings==null){label(c,attempted?"Нет свежего полного набора показаний":"Ожидание полного цикла BMS…",dp(14),dp(105),13,muted);return;}
        int min=readings[0],max=min;for(int v:readings){min=Math.min(min,v);max=Math.max(max,v);}
        float lo=temperature?Math.min(0,(float)Math.floor(min/5f)*5):min-2;
        float hi=temperature?Math.max(lo+5,(float)Math.ceil(max/5f)*5):max+2;
        float left=dp(46),right=getWidth()-dp(18),top=dp(68),bottom=getHeight()-dp(47);
        if(right<=left)return;
        for(int i=0;i<3;i++) {
            float value=lo+(hi-lo)*i/2f,y=bottom-(bottom-top)*i/2f;
            paint.setColor(grid);paint.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,paint);
            label(c,String.format(Locale.ROOT,"%.0f",value),dp(8),y+dp(4),10,muted);
        }
        if(temperature) {
            float step=(right-left)/readings.length;
            for(int i=0;i<readings.length;i++) {
                float x=left+(i+.5f)*step,y=bottom-(readings[i]-lo)/(hi-lo)*(bottom-top);
                paint.setColor(0xff55bca8);c.drawRoundRect(x-step*.33f,y,x+step*.33f,bottom,dp(3),dp(3),paint);
                label(c,Integer.toString(readings[i])+"°",x-dp(8),y-dp(5),11,text);
                label(c,Integer.toString(i+1),x-dp(i>=9?6:3),bottom+dp(17),10,muted);
            }
        } else {
            float step=(right-left)/readings.length;
            for(int i=0;i<readings.length;i++) {
                float x=left+(i+.5f)*step,y=bottom-(readings[i]-lo)/(hi-lo)*(bottom-top);
                paint.setColor(i==selected?text:0xff649cf3);
                c.drawRect(x-step*.36f,y,x+step*.36f,bottom,paint);
            }
            for(int index:CELL_TICKS)label(c,Integer.toString(index+1),left+(index+.5f)*step-dp(5),bottom+dp(17),10,muted);
        }
        String footer=temperature?"Диапазон "+min+"–"+max+" °C":selected>=0&&selected<readings.length
                ?String.format(Locale.forLanguageTag("ru"),"Ячейка №%d · %.3f В",selected+1,readings[selected]/1000f)
                :"Диапазон "+min+"–"+max+" мВ · разброс "+(max-min)+" мВ";
        label(c,footer,dp(14),getHeight()-dp(11),12,text);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(temperature||readings==null)return super.onTouchEvent(event);
        if(event.getAction()==MotionEvent.ACTION_UP){float width=getWidth()-dp(64);if(width>0)selected=Math.max(0,Math.min(105,(int)Math.floor((event.getX()-dp(46))/width*106)));invalidate();performClick();return true;}
        return event.getAction()==MotionEvent.ACTION_DOWN;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
