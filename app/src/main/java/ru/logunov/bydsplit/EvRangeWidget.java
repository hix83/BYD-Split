package ru.logunov.bydsplit;

import android.app.Activity;
import android.graphics.*;
import android.view.View;

/** A three-slot UI7 card; touch/drag/delete remain owned by the dock cell. */
final class EvRangeWidget extends View {
    private final EvRangeController data;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable update=()->{invalidate();setContentDescription(dataDescription());};
    private boolean active;
    EvRangeWidget(Activity activity){super(activity);data=EvRangeController.get(activity);paint.setTypeface(getResources().getFont(R.font.ui7_regular));}
    void setActive(boolean enabled){if(active==enabled)return;active=enabled;if(enabled)data.add(update);else data.remove(update);}
    @Override protected void onDetachedFromWindow(){setActive(false);super.onDetachedFromWindow();}
    private String dataDescription(){return data.calculatedText()+", "+data.socText()+", "+data.tripText()+", "+data.tankText()+", "+data.climateText();}
    private void text(Canvas c,String value,float x,float y,float size,int color){paint.setColor(color);paint.setTextSize(size);c.drawText(value,x,y,paint);}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight(),s=Math.min(w/270f,h/76f);float left=(w-250*s)/2;
        paint.setColor(ThemePalette.color(getContext(),0xff26333e,0xffe6eef6));c.drawRoundRect(left-5*s,2*s,w-left+5*s,h-2*s,12*s,12*s,paint);
        int primary=ThemePalette.text(getContext()),muted=getContext().getColor(R.color.text_secondary),accent=getContext().getColor(R.color.accent);
        text(c,data.tripText().replace("Поездка ",""),left,13*s,11*s,muted);
        paint.setTextAlign(Paint.Align.RIGHT);text(c,data.tankText(),left+250*s,13*s,11*s,muted);paint.setTextAlign(Paint.Align.LEFT);
        float cx=left+28*s,cy=39*s,r=21*s;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*s);paint.setColor(ThemePalette.color(getContext(),0xff455361,0xffc8d4df));c.drawCircle(cx,cy,r,paint);
        Float soc=data.soc();if(soc!=null){paint.setColor(accent);paint.setStrokeCap(Paint.Cap.ROUND);c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,soc*3.6f,false,paint);paint.setStrokeCap(Paint.Cap.BUTT);}
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
        text(c,soc==null?"—%":Math.round(soc)+"%",cx,44*s,15*s,primary);
        Double calculatedKm=data.forecast();
        text(c,calculatedKm==null?"—":String.valueOf(Math.round(calculatedKm)),left+122*s,43*s,26*s,primary);
        text(c,"EV · км",left+122*s,56*s,10*s,accent);
        text(c,data.fuel()==null?"—":String.valueOf(data.fuel()),left+215*s,42*s,22*s,primary);
        text(c,"Бензин · км",left+215*s,56*s,10*s,muted);
        text(c,data.shortClimateText(),left+125*s,71*s,10*s,muted);paint.setTextAlign(Paint.Align.LEFT);
        setContentDescription(dataDescription());
    }
}
