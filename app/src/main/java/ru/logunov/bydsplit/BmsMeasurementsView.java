package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/** Individual, timestamped BMS measurements. The voltage axis is explicitly zoomed. */
final class BmsMeasurementsView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private BatteryCellReadings readings;
    private int selected=-1;
    private float downX,downY;
    BmsMeasurementsView(Context c) {
        super(c);GradientDrawable bg=new GradientDrawable();bg.setColor(c.getColor(R.color.card));bg.setCornerRadius(dp(14));setBackground(bg);
        setClickable(true);setContentDescription("Напряжения 94 ячеек BMS. Нажмите столбец для точного значения.");
    }
    void update(BatteryCellReadings value){readings=value;invalidate();}
    void refreshAge(){invalidate();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void text(Canvas c,String s,float x,float y,int size,int color){p.setColor(color);p.setTextSize(dp(size));c.drawText(s,x,y,p);}
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);p.setTypeface(getResources().getFont(R.font.ui7_regular));
        int fg=getContext().getColor(R.color.text_primary),muted=getContext().getColor(R.color.text_secondary),accent=getContext().getColor(R.color.accent);
        long now=SystemClock.elapsedRealtime();boolean live=readings!=null&&readings.cellsFresh(now);
        text(c,"Ячейки · напряжение, мВ",dp(14),dp(25),16,fg);
        if(!live){text(c,readings==null?"Ожидание полного набора BMS…":"Нет свежего полного набора BMS",dp(14),dp(68),14,muted);return;}
        int[] cells=readings.millivolts;int min=4500,max=0;for(int v:cells){min=Math.min(min,v);max=Math.max(max,v);}
        String detail=selected>=0?String.format(Locale.forLanguageTag("ru"),"Ячейка %d · %.3f В",selected+1,cells[selected]/1000.0):"Нажмите столбец · разброс "+(max-min)+" мВ";
        text(c,detail,dp(14),dp(49),14,fg);
        text(c,"Увеличенная шкала · "+((now-readings.cellsAt)/1000)+" с назад",dp(14),dp(70),12,muted);
        float low=(float)(Math.floor((min-5)/10.0)*10),high=(float)(Math.ceil((max+5)/10.0)*10);
        float left=dp(53),right=getWidth()-dp(15),top=dp(86),bottom=dp(205),step=(right-left)/cells.length;
        for(int i=0;i<=2;i++){
            float y=bottom-(bottom-top)*i/2;int v=Math.round(low+(high-low)*i/2);
            p.setColor(getContext().getColor(R.color.button));p.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,p);
            text(c,Integer.toString(v),dp(8),y+dp(4),11,muted);
        }
        for(int i=0;i<cells.length;i++) {
            float y=bottom-(cells[i]-low)/(high-low)*(bottom-top);
            p.setColor(i==selected?fg:accent);float x=left+i*step;
            c.drawRoundRect(x+step*.12f,y,x+step*.88f,bottom,dp(1),dp(1),p);
        }
        for(int n:new int[]{1,24,47,70,94})text(c,Integer.toString(n),left+(n-1)*step,bottom+dp(17),11,muted);
        text(c,"Номер измерения BMS",dp(14),dp(244),12,muted);
        text(c,"Температуры датчиков батареи · °C",dp(14),dp(274),16,fg);
        if(readings.temperaturesFresh(now)) {
            int[] temps=readings.temperatures;int minT=0,maxT=10;for(int v:temps){minT=Math.min(minT,v);maxT=Math.max(maxT,v);}
            float lowT=(float)Math.floor(minT/10.0)*10,highT=(float)Math.ceil((maxT+2)/10.0)*10;
            float tTop=dp(307),tBottom=dp(411),tStep=(right-left)/temps.length;
            for(int j=0;j<=2;j++){
                float y=tBottom-(tBottom-tTop)*j/2;int v=Math.round(lowT+(highT-lowT)*j/2);
                p.setColor(getContext().getColor(R.color.button));c.drawLine(left,y,right,y,p);
                text(c,Integer.toString(v),dp(18),y+dp(4),11,muted);
            }
            float zero=tBottom-(0-lowT)/(highT-lowT)*(tBottom-tTop);
            for(int i=0;i<temps.length;i++) {
                float x=left+i*tStep,y=tBottom-(temps[i]-lowT)/(highT-lowT)*(tBottom-tTop);
                p.setColor(accent);c.drawRoundRect(x+tStep*.2f,Math.min(y,zero),x+tStep*.8f,Math.max(y,zero)+dp(1),dp(3),dp(3),p);
                text(c,Integer.toString(temps[i])+"°",x+tStep*.3f,y-dp(5),13,fg);
                text(c,Integer.toString(i+1),x+tStep*.45f,tBottom+dp(19),12,muted);
            }
        }else text(c,"Нет свежего набора температур",dp(14),dp(336),14,muted);

    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            if(Math.abs(e.getX()-downX)<dp(10)&&Math.abs(e.getY()-downY)<dp(10)){
                float left=dp(53),right=getWidth()-dp(15);
                if(e.getX()>=left&&e.getX()<=right&&e.getY()>=dp(80)&&e.getY()<=dp(225))selected=Math.min(93,(int)((e.getX()-left)/(right-left)*94));
                performClick();invalidate();
            }return true;
        }return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
