package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** UI7 battery page. Its reader exists only while the page is in the foreground. */
final class BatteryDetailsView extends LinearLayout {
    private final Map<String, TextView> values = new HashMap<>();
    private final TextView status;
    private final TemperatureChart chart;
    private ScheduledExecutorService reader, cellReader;
    private BmsMeasurementsView measurements;
    private final Runnable ageRefresh=new Runnable(){@Override public void run(){
        if(!active||measurements==null)return;measurements.refreshAge();postDelayed(this,1000);
    }};
    private int generation;
    private boolean active;
    private final boolean demo;
    final boolean technical;
    private final int text = getContext().getColor(R.color.text_primary), muted = getContext().getColor(R.color.text_secondary);

    BatteryDetailsView(Context context, Runnable close) {
        this(context,close,false);
    }
    BatteryDetailsView(Context context,Runnable close,boolean technical) {
        super(context);
        this.technical=technical;
        demo = AppPreferences.isDemoModeEnabled(context);
        setOrientation(VERTICAL); setPadding(dp(20),dp(14),dp(20),dp(14));
        setBackground(bg(getContext().getColor(R.color.surface_light))); setClipToOutline(true);
        setClickable(true);
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(technical?"Техника · Seagull EV":"Состояние батареи",22,text);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView back = label("Назад",16,getContext().getColor(R.color.accent)); back.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable backIcon=getContext().getDrawable(R.drawable.di5_back);
        backIcon.setBounds(0,0,dp(20),dp(20));
        back.setCompoundDrawables(backIcon,null,null,null);
        back.setCompoundDrawablePadding(dp(4));
        back.setSingleLine(true);
        back.setContentDescription("Вернуться к приложению"); back.setBackground(bg(getContext().getColor(R.color.button)));
        back.setOnClickListener(v -> close.run()); header.addView(back,new LinearLayout.LayoutParams(dp(114),dp(42)));
        addView(header);
        status=label("Получение показаний…",13,muted); addView(status);
        ScrollView scroll=new ScrollView(context); scroll.setFillViewport(true);
        LinearLayout content=new LinearLayout(context); content.setOrientation(VERTICAL);
        scroll.addView(content); addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(technical) {
            chart=null; buildTechnical(content);
        } else {
        row(content,new String[]{"soh","soc","power"},new String[]{"SOH · здоровье","SOC · заряд","Мощность батареи"});
        TextView explanation=label("SOH рассчитывает BMS автомобиля. Это состояние батареи относительно новой, а SOC — её текущий заряд.",12,muted);
        explanation.setPadding(dp(4),dp(10),dp(4),dp(6)); content.addView(explanation);
        section(content,"Напряжения ячеек · 94");
        measurements=new BmsMeasurementsView(context);
        content.addView(measurements,new LinearLayout.LayoutParams(-1,dp(448)));
        TextView cellNote=label("Номера соответствуют порядку BMS, а не расположению ячеек в корпусе. Диаграмма обновляется только при открытом экране.",12,muted);
        cellNote.setPadding(dp(4),dp(6),dp(4),dp(8));content.addView(cellNote);
        section(content,"Температура батареи");
        row(content,new String[]{"minTemp","maxTemp","deltaTemp"},new String[]{"Минимальная","Максимальная","Разница датчиков"});
        chart=new TemperatureChart(context); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(105));cp.topMargin=dp(12);content.addView(chart,cp);
        section(content,"Ячейки и бортовая сеть");
        row(content,new String[]{"minCell","maxCell","deltaCell"},new String[]{"Ячейка · минимум","Ячейка · максимум","Разброс ячеек"});
        row(content,new String[]{"voltage","gun","charging"},new String[]{"Бортсеть","Подключение","Состояние зарядки"});
        section(content,"Счётчики энергии");
        row(content,new String[]{"lifetime","session"},new String[]{"Накопленная энергия BMS","Энергия зарядной сессии"});
        TextView note=label("График: минимум и максимум датчиков за время просмотра. Счётчики передаются BMS; энергия сессии может сохраняться после отключения кабеля. Недоступные данные обозначены прочерком.",13,muted);
        note.setPadding(dp(4),dp(14),dp(4),dp(10));content.addView(note);
        }
    }
    private void buildTechnical(LinearLayout content) {
        section(content,"Электропривод");
        row(content,new String[]{"drive","frontRpm"},new String[]{"Режим движения","Электромотор · об/мин"});
        section(content,"Батарея · сейчас");
        row(content,new String[]{"soh","soc","avgTemp"},new String[]{"SOH","SOC","Температура · средняя"});
        row(content,new String[]{"hv","power","batteryWatts"},new String[]{"Напряжение HV","Мощность батареи","Мощность HV · Вт"});
        row(content,new String[]{"voltage","insulation","rate"},new String[]{"Бортсеть","Изоляция HV","Расчёт SOC / час"});
        TextView capacity=label("Ёмкость батареи для расчёта %/ч · настроить",13,getContext().getColor(R.color.accent));
        capacity.setPadding(0,dp(10),0,dp(6));capacity.setOnClickListener(v->editCapacity());content.addView(capacity);
        section(content,"Измерения ячеек и датчиков BMS");
        measurements=new BmsMeasurementsView(getContext());
        content.addView(measurements,new LinearLayout.LayoutParams(-1,dp(448)));
        row(content,new String[]{"minCell","maxCell","deltaCell"},new String[]{"Ячейка · минимум","Ячейка · максимум","Разброс"});
        section(content,"Электромотор и инвертор");
        row(content,new String[]{"frontTemp","frontInv"},new String[]{"Температура мотора","Температура инвертора"});
        TextView motorsNote=label("Температуры переднего электромотора и инвертора · DiLink 2. Недоступные значения обозначены прочерком.",14,muted);
        motorsNote.setPadding(0,dp(8),0,dp(4));content.addView(motorsNote);
        row(content,new String[]{"accelerator","brake"},new String[]{"Педаль газа","Педаль тормоза"});
        section(content,"Климат");
        row(content,new String[]{"ac","compressor"},new String[]{"Климат","Кондиционер A/C"});
        row(content,new String[]{"target","fan"},new String[]{"Заданная температура","Вентилятор"});
        row(content,new String[]{"airflow","recirculation"},new String[]{"Направление обдува","Рециркуляция"});
        section(content,"Батарея · история");
        row(content,new String[]{"mileage","lifetime"},new String[]{"Пробег BMS","Энергия BMS"});
        row(content,new String[]{"sessionAvg","allAvg"},new String[]{"Средний SOC после зарядки","Средний SOC · наблюдения"});
        TextView note=label("—: нет достоверного показания. Средняя температура — середина минимума и максимума. Средний SOC собирается приложением раз в минуту при получении телеметрии. %/ч — оценка по мощности HV и заданной ёмкости: плюс — расход, минус — заряд.",14,muted);
        note.setPadding(0,dp(14),0,dp(12));content.addView(note);
    }
    private void editCapacity() {
        EditText input=new EditText(getContext());input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        float saved=getContext().getSharedPreferences("tech",0).getFloat("capacity",0);
        if(saved>0)input.setText(Float.toString(saved));input.setHint("Ёмкость вашей батареи, кВт·ч");
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(getContext()).setTitle("Ёмкость батареи, кВт·ч").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try {float n=Float.parseFloat(input.getText().toString().replace(',','.'));if(!Float.isFinite(n)||n<=0||n>200)throw new NumberFormatException();getContext().getSharedPreferences("tech",0).edit().putFloat("capacity",n).apply();dialog.dismiss();}catch(NumberFormatException error){input.setError("Укажите ёмкость от 0 до 200 кВт·ч");}}));dialog.show();
    }
    private void updateTechnical(TechSnapshot data) {
        if(data==null){status.setText("Нет связи · показания недоступны");for(TextView value:values.values())value.setText("—");return;}
        VehicleTelemetrySnapshot s=data.battery.vehicle;BatteryDetailsSnapshot b=data.battery;
        status.setText(demo?"Демонстрационный режим":"Показания автомобиля · "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date()));
        for(Map.Entry<String,TextView> item:values.entrySet())item.getValue().setText("—");
        for(Object[] f:TechSnapshot.FIELDS) {
            String key=(String)f[0];if(!values.containsKey(key))continue;
            String unit=key.contains("Temp")||key.endsWith("Inv")||key.equals("inside")||key.equals("outside")?"°C":key.endsWith("Rpm")?"об/мин":key.endsWith("Limit")?"кВт":key.equals("hv")?"В":key.equals("insulation")?"МОм":key.equals("compressor")?"":key.equals("mileage")?"км":key.equals("ac")?"":"%";
            put(key,number(data.values.get(key),unit,key.equals("insulation")?2:key.endsWith("Limit")?1:0));
        }
        Double ac=data.values.get("ac");put("ac",state(ac));
        put("compressor",state(data.values.get("compressor")));
        put("target",number(data.values.get("temperature"),"°C",0));
        Double fan=data.values.get("fan");put("fan",fan==null?"—":String.format(Locale.ROOT,"%.0f / 7",fan));
        put("recirculation",state(data.values.get("recirculation")));
        Double wind=data.values.get("wind");
        String[] directions={"—","Лицо","Лицо и ноги","Ноги","Стекло и ноги","Стекло","Лицо, ноги и стекло","Лицо и стекло"};
        put("airflow",wind==null?"—":directions[wind.intValue()]);
        put("drive",s.driveMode==null?"—":s.driveMode==1?"ECO":s.driveMode==2?"SPORT":s.driveMode==4?"SNOWFIELD":s.driveMode==0||s.driveMode==3?"NORMAL":"Код "+s.driveMode);
        put("frontRpm",number(s.motorRpm,"об/мин",0));
        put("soc",number(s.batterySocPercent,"%",0));put("soh",number(s.batterySohPercent,"%",0));
        put("avgTemp",number(s.batteryMinTempC!=null&&s.batteryMaxTempC!=null?(s.batteryMinTempC+s.batteryMaxTempC)/2f:null,"°C",1));
        put("power",number(s.batteryPowerKw,"кВт",0));put("voltage",number(s.auxiliaryVoltage,"В",1));
        Double watts=data.values.get("batteryWatts");put("batteryWatts",number(watts,"Вт",0));
        float capacity=getContext().getSharedPreferences("tech",0).getFloat("capacity",0);
        put("rate",number(watts!=null&&capacity>0?watts/(capacity*10):null,"%/ч",2));
        put("minCell",number(b.minCellMv==null?null:b.minCellMv/1000f,"В",3));put("maxCell",number(b.maxCellMv==null?null:b.maxCellMv/1000f,"В",3));put("deltaCell",number(b.deltaMv(),"мВ",0));
        put("lifetime",number(b.lifetimeKwh,"кВт·ч",1));put("sessionAvg",number(demo?null:SocHistory.average(getContext(),true),"%",1));put("allAvg",number(demo?null:SocHistory.average(getContext(),false),"%",1));
    }
    private String tyre(Integer pressure,Integer temp){return number(pressure==null?null:pressure/100f,"бар",2)+" · "+number(temp,"°C",0);}

    private void section(LinearLayout target,String name) {
        TextView v=label(name,16,text);v.setPadding(dp(3),dp(14),0,dp(4));target.addView(v);
    }
    private void row(LinearLayout target,String[] keys,String[] titles) {
        LinearLayout row=new LinearLayout(getContext());
        for(int i=0;i<keys.length;i++) {
            LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);
            card.setPadding(dp(12),dp(8),dp(10),dp(8));card.setBackground(bg(getContext().getColor(R.color.card)));
            card.addView(label(titles[i],12,muted));
            TextView value=label("—",20,text);value.setPadding(0,dp(4),0,0);card.addView(value);values.put(keys[i],value);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.topMargin=dp(6);if(i>0)lp.leftMargin=dp(10);row.addView(card,lp);
        }
        target.addView(row);
    }
    private TextView label(String value,int size,int color) {
        TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(getResources().getFont(R.font.ui7_regular));return v;
    }
    private GradientDrawable bg(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d; }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    void setActive(boolean enabled) {
        if(active==enabled)return;active=enabled;generation++;
        if(reader!=null){reader.shutdownNow();reader=null;}
        if(cellReader!=null){cellReader.shutdownNow();cellReader=null;}
        removeCallbacks(ageRefresh);
        if(measurements!=null)measurements.update(null);
        if(!enabled)return;
        final int token=generation;
        if(measurements!=null&&!demo) {
            post(ageRefresh);cellReader=Executors.newSingleThreadScheduledExecutor();
            cellReader.scheduleWithFixedDelay(()->{
                BatteryCellReadings cells=LocalAdbManager.readBatteryCells(getContext());
                post(()->{if(active&&token==generation)measurements.update(cells);});
            },0,3,TimeUnit.SECONDS);
        }
        reader=Executors.newSingleThreadScheduledExecutor();
        reader.scheduleWithFixedDelay(() -> {
            if(technical) {
                TechSnapshot tech=demo?new TechSnapshot(new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.demo(),new int[]{3275,3279,Float.floatToIntBits(1200),0,2}),new int[0]):LocalAdbManager.get(getContext()).readTechDetails();
                post(()->{if(active&&token==generation)updateTechnical(tech);});return;
            }
            BatteryDetailsSnapshot data=demo ? new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.demo(),new int[]{3275,3279,Float.floatToIntBits(1200),0,2})
                    : LocalAdbManager.get(getContext()).readBatteryDetails();
            post(() -> {if(active && token==generation)update(data);});
        },0,technical?1:3,TimeUnit.SECONDS);
    }
    @Override protected void onDetachedFromWindow(){setActive(false);super.onDetachedFromWindow();}
    private String state(Double n){return n==null?"—":n==1?"Включён":"Выключен";}
    private String number(Number n,String unit,int decimals){return n==null?"—":String.format(Locale.forLanguageTag("ru"),"%."+decimals+"f",n.doubleValue())+" "+unit;}
    private void put(String key,String value){TextView field=values.get(key);if(field!=null)field.setText(value);}
    private void update(BatteryDetailsSnapshot data) {
        if(data==null){status.setText("Нет связи · показания недоступны");for(TextView v:values.values())v.setText("—");return;}
        VehicleTelemetrySnapshot s=data.vehicle;
        status.setText(demo?"Демонстрационные данные":"Данные автомобиля · обновлено "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date()));
        put("soh",number(s.batterySohPercent,"%",0));put("soc",number(s.batterySocPercent,"%",0));put("power",number(s.batteryPowerKw,"кВт",0));
        put("minTemp",number(s.batteryMinTempC,"°C",0));put("maxTemp",number(s.batteryMaxTempC,"°C",0));
        Integer delta=s.batteryMinTempC!=null&&s.batteryMaxTempC!=null?s.batteryMaxTempC-s.batteryMinTempC:null;
        put("deltaTemp",number(delta,"°C",0));
        put("minCell",number(data.minCellMv==null?null:data.minCellMv/1000f,"В",3));put("maxCell",number(data.maxCellMv==null?null:data.maxCellMv/1000f,"В",3));put("deltaCell",number(data.deltaMv(),"мВ",0));
        put("voltage",number(s.auxiliaryVoltage,"В",1));
        String gun="—";if(s.chargeGunState!=null)switch(s.chargeGunState){case 1:gun="Нет кабеля";break;case 2:gun="AC";break;case 3:gun="DC";break;case 4:gun="AC / DC";break;case 5:gun="V2L";break;}
        put("gun",gun);
        String state="—";if(s.chargeGunState!=null&&s.chargeGunState==1)state="Не заряжается";
        else if(data.bmsState!=null)switch(data.bmsState){case 1:state="Заряжается";break;case 2:state="Завершена";break;case 13:state="Пауза";break;}
        put("charging",state);put("lifetime",number(data.lifetimeKwh,"кВт·ч",1));put("session",number(data.sessionKwh,"кВт·ч",1));
        chart.add(s.batteryMinTempC,s.batteryMaxTempC);
    }
    private final class TemperatureChart extends View {
        final ArrayList<float[]> samples=new ArrayList<>(); final Paint p=new Paint(3);
        TemperatureChart(Context c){super(c);setBackground(bg(getContext().getColor(R.color.card)));setContentDescription("График минимальной и максимальной температуры батареи за время просмотра");}
        void add(Integer min,Integer max){if(min==null||max==null)return;samples.add(new float[]{SystemClock.elapsedRealtime()/1000f,min,max});if(samples.size()>120)samples.remove(0);invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);p.setTypeface(getResources().getFont(R.font.ui7_regular));p.setTextSize(dp(12));p.setColor(muted);
            c.drawText("Минимум / максимум · текущий просмотр",dp(14),dp(22),p);
            if(samples.size()<2){c.drawText("Собираем показания…",dp(14),dp(68),p);return;}
            float lo=200,hi=-100;for(float[] a:samples){lo=Math.min(lo,a[1]);hi=Math.max(hi,a[2]);}lo-=1;hi+=1;
            float left=dp(55),right=getWidth()-dp(18),top=dp(38),bottom=getHeight()-dp(26);
            c.drawText(Math.round(hi)+"°",dp(14),top+dp(4),p);c.drawText(Math.round(lo)+"°",dp(14),bottom,p);
            p.setColor(getContext().getColor(R.color.divider));p.setStrokeWidth(dp(1));c.drawLine(left,bottom,right,bottom,p);
            float first=samples.get(0)[0],span=Math.max(1,samples.get(samples.size()-1)[0]-first);
            for(int k=1;k<=2;k++){p.setColor(k==1?0xff649cf3:0xff55cfb2);p.setStrokeWidth(dp(2));for(int i=1;i<samples.size();i++){float[] a=samples.get(i-1),b=samples.get(i);c.drawLine(left+(a[0]-first)/span*(right-left),bottom-(a[k]-lo)/(hi-lo)*(bottom-top),left+(b[0]-first)/span*(right-left),bottom-(b[k]-lo)/(hi-lo)*(bottom-top),p);}}
            p.setColor(muted);c.drawText(Math.round(span)+" с наблюдения",left,getHeight()-dp(7),p);
        }
    }
}
