package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;

/** Electric-only dashboard. The hatchback illustration is a schematic. */
final class VehicleDashboardView extends View {
    static final int ACTION_AUTO=0, ACTION_CLIMATE=1, ACTION_CAR_SETTINGS=2,
            ACTION_HOME=3, ACTION_BATTERY_DETAILS=4, ACTION_TECH_DETAILS=5;
    interface ActionListener { void onVehicleAction(int action); }
    private static final int TEXT=0xffedf4f5, MUTED=0xff9cabb5, GREEN=0xff64e4b5;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final ActionListener listener;
    private final boolean demo;
    private final RectF batteryHit=new RectF(), scenarioHit=new RectF();
    private final RectF[] buttons={new RectF(),new RectF(),new RectF(),new RectF(),new RectF()};
    private VehicleTelemetrySnapshot telemetry=VehicleTelemetrySnapshot.EMPTY;
    private int scenario;
    private ClimateIconRenderer climateIcon;
    private static final String[] SCENARIOS={"Электротяга", "Рекуперация", "Зарядка", "Стоянка"};

    VehicleDashboardView(Context context,ActionListener listener) {
        super(context);this.listener=listener;demo=AppPreferences.isDemoModeEnabled(context);
        p.setTypeface(context.getResources().getFont(R.font.ui7_regular));
        setContentDescription("BYD Seagull: заряд батареи, скорость, электропривод и давление шин");
    }
    void setTelemetry(VehicleTelemetrySnapshot value){telemetry=value==null?VehicleTelemetrySnapshot.EMPTY:value;invalidate();}
    private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align){
        p.setColor(color);p.setTextSize(size);p.setTextAlign(align);p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);
    }
    private void box(Canvas c,float l,float t,float r,float b,float radius,int color){
        p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(l,t,r,b,radius,radius,p);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(getWidth()==0||getHeight()==0)return;
        c.save();c.scale(getWidth()/760f,getHeight()/960f);
        p.setShader(new LinearGradient(0,0,760,960,new int[]{0xff172e32,0xff142025,0xff10191e},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(0,0,760,960,28,28,p);p.setShader(null);
        text(c,"BYD SEAGULL",30,46,25,TEXT,Paint.Align.LEFT);
        box(c,574,20,730,59,19,0xff25413e);
        text(c,demo?"ДЕМО · EV":"ЭЛЕКТРОМОБИЛЬ",652,46,demo?18:14,GREEN,Paint.Align.CENTER);
        Float soc=demo?Float.valueOf(68):telemetry.batterySocPercent;
        text(c,soc==null?"—":String.valueOf(Math.round(soc)),30,160,92,TEXT,Paint.Align.LEFT);
        text(c,"%",210,151,32,GREEN,Paint.Align.LEFT);
        text(c,"ЗАРЯД БАТАРЕИ",32,195,18,MUTED,Paint.Align.LEFT);
        batteryHit.set(20,70,300,225);
        Integer speed=demo?Integer.valueOf(scenario==0?52:scenario==1?38:0):telemetry.speedKmh;
        text(c,speed==null?"—":speed.toString(),730,146,65,TEXT,Paint.Align.RIGHT);
        text(c,"км/ч",730,184,21,MUTED,Paint.Align.RIGHT);
        box(c,30,217,730,227,5,0xff2c3d43);
        if(soc!=null)box(c,30,217,30+700*Math.max(0,Math.min(100,soc))/100f,227,5,GREEN);
        text(c,(demo?SCENARIOS[scenario]:evState())+(demo?"  ›":""),30,269,23,GREEN,Paint.Align.LEFT);
        text(c,demo?"NORMAL":driveMode(),730,269,18,MUTED,Paint.Align.RIGHT);
        scenarioHit.set(20,240,500,290);
        drawCar(c);
        Integer power=demo?Integer.valueOf(scenario==0?18:scenario==1?-22:scenario==2?-30:0):telemetry.batteryPowerKw;
        String powerLabel=power==null?"Мощность батареи":power<0?(charging()?"Зарядка":"Рекуперация"):"Потребление";
        metric(c,30,684,370,764,powerLabel,power==null?"— кВт":Math.abs(power)+" кВт");
        String temp=demo?"24–26 °C":telemetry.batteryMinTempC==null||telemetry.batteryMaxTempC==null?"— °C":telemetry.batteryMinTempC+"–"+telemetry.batteryMaxTempC+" °C";
        metric(c,390,684,730,764,"Температура батареи",temp);
        metric(c,30,778,370,850,"Здоровье · SOH",demo?"98 %":telemetry.batterySohPercent==null?"— %":telemetry.batterySohPercent+" %");
        metric(c,390,778,730,850,"Бортовая сеть",demo?"13,8 В":telemetry.auxiliaryVoltage==null?"— В":String.format(Locale.forLanguageTag("ru"),"%.1f В",telemetry.auxiliaryVoltage));
        drawActions(c);c.restore();
    }
    private boolean charging(){return demo?scenario==2:telemetry.chargeGunState!=null&&telemetry.chargeGunState>=2&&telemetry.chargeGunState<=4;}
    private String evState(){
        if(charging())return "Кабель подключён";
        if(telemetry.batteryPowerKw!=null&&telemetry.batteryPowerKw<0)return "Рекуперация";
        if(telemetry.speedKmh==null)return "Нет данных автомобиля";
        return telemetry.speedKmh>0?"Электротяга":"Стоянка";
    }
    private String driveMode(){
        if(telemetry.driveMode==null)return "РЕЖИМ —";
        switch(telemetry.driveMode){case 1:return "ECO";case 2:return "SPORT";case 0:case 3:return "NORMAL";default:return "РЕЖИМ —";}
    }
    private void metric(Canvas c,float l,float t,float r,float b,String label,String value){
        box(c,l,t,r,b,16,0xff203037);
        text(c,label,l+18,t+26,18,MUTED,Paint.Align.LEFT);
        text(c,value,l+18,b-14,27,TEXT,Paint.Align.LEFT);
    }
    private void drawCar(Canvas c){
        box(c,254,312,506,655,100,0xff0c171b);
        for(int x:new int[]{259,477})for(int y:new int[]{369,560})box(c,x,y,x+24,y+63,9,0xff62757d);
        path.reset();path.moveTo(300,642);path.cubicTo(273,617,281,385,297,341);
        path.cubicTo(316,302,444,302,463,341);path.cubicTo(479,385,487,617,460,642);
        path.cubicTo(428,660,332,660,300,642);path.close();
        p.setColor(0xff354b53);p.setStyle(Paint.Style.FILL);c.drawPath(path,p);
        p.setColor(0xff72969e);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        box(c,304,361,456,410,18,0xff0e2029);
        box(c,308,583,452,621,12,0xff10232b);
        box(c,310,431,450,563,18,0xff234e47);
        Float soc=demo?Float.valueOf(68):telemetry.batterySocPercent;
        for(int i=0;i<5;i++){
            float x=321+i*24;
            box(c,x,444,x+18,540,5,0xff3e6c61);
            if(soc!=null){
                float fraction=Math.max(0,Math.min(1,(soc-i*20)/20f));
                if(fraction>0)box(c,x,540-96*fraction,x+18,540,5,GREEN);
            }
        }
        text(c,"БАТАРЕЯ",380,579,14,MUTED,Paint.Align.CENTER);
        box(c,344,329,416,353,8,0xff5da7c0);
        text(c,"EV",380,348,16,0xff10232b,Paint.Align.CENTER);
        tyre(c,36,369,"ПЕРЕДНЕЕ ЛЕВОЕ",telemetry.tirePressFlKpa,telemetry.tireTempFlC);
        tyre(c,540,369,"ПЕРЕДНЕЕ ПРАВОЕ",telemetry.tirePressFrKpa,telemetry.tireTempFrC);
        tyre(c,36,562,"ЗАДНЕЕ ЛЕВОЕ",telemetry.tirePressRlKpa,telemetry.tireTempRlC);
        tyre(c,540,562,"ЗАДНЕЕ ПРАВОЕ",telemetry.tirePressRrKpa,telemetry.tireTempRrC);
        text(c,"Схема электромобиля",380,672,15,MUTED,Paint.Align.CENTER);
    }
    private void tyre(Canvas c,float x,float y,String title,Integer pressure,Integer temp){
        if(demo){pressure=240;temp=26;}
        text(c,title,x,y,14,MUTED,Paint.Align.LEFT);
        text(c,pressure==null?"— бар":String.format(Locale.forLanguageTag("ru"),"%.1f бар",pressure/100f),x,y+34,28,pressure==null?MUTED:pressure>=220&&pressure<=260?GREEN:0xffffba73,Paint.Align.LEFT);
        text(c,temp==null?"— °C":temp+" °C",x,y+61,19,MUTED,Paint.Align.LEFT);
    }
    private void drawActions(Canvas c){
        int[] icons={R.drawable.ui7_oem_car,R.drawable.ui7_climate,R.drawable.ui7_car_settings,R.drawable.ui7_home,R.drawable.ui7_tech};
        String[] labels={"Авто","Климат","Параметры","Домой","Техника"};
        for(int i=0;i<5;i++){
            float x=30+i*142;buttons[i].set(x,868,x+132,944);box(c,x,868,x+132,944,16,0xff273940);
            if(i==1){if(climateIcon==null)climateIcon=new ClimateIconRenderer(getContext());climateIcon.draw(c,(int)x+66,894,22,demo?ClimateIconState.decode(1,4,2):telemetry.climate);}
            else {android.graphics.drawable.Drawable icon=getContext().getDrawable(icons[i]).mutate();icon.setTint(TEXT);icon.setBounds((int)x+46,876,(int)x+86,914);icon.draw(c);}
            text(c,labels[i],x+66,932,16,TEXT,Paint.Align.CENTER);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_UP){
            float x=event.getX()*760/getWidth(),y=event.getY()*960/getHeight();performClick();
            if(batteryHit.contains(x,y)){if(listener!=null)listener.onVehicleAction(ACTION_BATTERY_DETAILS);}
            else if(demo&&scenarioHit.contains(x,y)){scenario=(scenario+1)%SCENARIOS.length;invalidate();}
            else for(int i=0;i<5;i++)if(buttons[i].contains(x,y)){if(listener!=null)listener.onVehicleAction(i==4?ACTION_TECH_DETAILS:i);break;}
        }return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
