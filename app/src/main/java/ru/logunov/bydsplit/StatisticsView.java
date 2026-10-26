package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** Trips, charging, fuel purchases and period summaries in the main launcher pane. */
final class StatisticsView extends LinearLayout {
    private final Activity activity;
    private final StatisticsStore store;
    private final LinearLayout content;
    private final TextView status;
    private int tab, period=30;
    private long expanded=-1;
    private final ScrollView scroll;
    private boolean wide,roomy;
    private final List<Button> tabButtons=new ArrayList<>(), periodButtons=new ArrayList<>();
    private final Runnable refresh=new Runnable(){public void run(){status.setText(AppPreferences.isDemoModeEnabled(activity)?"Эмулятор · реальная запись отключена":StatisticsService.status);show();postDelayed(this,5000);}};
    StatisticsView(Activity activity,Runnable close) { this(activity,close,0,30); }
    int selectedTab(){return tab;}
    int selectedPeriod(){return period;}
    StatisticsView(Activity activity,Runnable close,int selectedTab,int selectedPeriod) {
        this(activity,close,selectedTab,selectedPeriod,new StatisticsStore(activity));
    }
    StatisticsView(Activity activity,Runnable close,int selectedTab,int selectedPeriod,StatisticsStore journal) {
        super(activity);this.activity=activity;store=journal;
        tab=selectedTab;period=selectedPeriod;
        scroll=new ScrollView(activity);scroll.setFillViewport(true);
        setOrientation(VERTICAL);setPadding(dp(16),dp(12),dp(16),dp(12));android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(activity.getColor(R.color.background));background.setCornerRadius(dp(14));setBackground(background);setClipToOutline(true);setClickable(true);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(StatisticsUi.text(activity,"Поездки и расходы",22),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(StatisticsUi.button(activity,"‹ Назад",close),new LinearLayout.LayoutParams(dp(105),dp(42)));addView(header);
        status=StatisticsUi.text(activity,StatisticsService.status,12);addView(status);
        LinearLayout tabs=new LinearLayout(activity);String[] labels={"Обзор","Поездки","Зарядки","Заправки"};
        for(int i=0;i<labels.length;i++){final int index=i;Button button=StatisticsUi.button(activity,labels[i],()->{tab=index;expanded=-1;show();scroll.post(()->scroll.scrollTo(0,0));});tabButtons.add(button);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMarginEnd(dp(4));tabs.addView(button,lp);}addView(tabs);
        LinearLayout filters=new LinearLayout(activity);
        for(int days:new int[]{1,7,30,0}) {
            Button button=StatisticsUi.button(activity,days==0?"Всё время":days==1?"Сегодня":days+" дней",()->{period=days;expanded=-1;show();scroll.post(()->scroll.scrollTo(0,0));});
            button.setTag(days);periodButtons.add(button);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMarginEnd(dp(4));lp.topMargin=dp(4);filters.addView(button,lp);
        }
        addView(filters);
        content=StatisticsUi.column(activity);scroll.addView(content);addView(scroll,new LinearLayout.LayoutParams(-1,0,1));show();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);boolean next=w>=dp(700),large=w>=dp(1000);if(next!=wide||large!=roomy){wide=next;roomy=large;post(this::show);}}
    private int dp(int n){return StatisticsUi.dp(activity,n);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(refresh);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(refresh);store.close();super.onDetachedFromWindow();}
    private long since(){Calendar c=Calendar.getInstance();if(period==0)return 0;c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.DAY_OF_YEAR,1-period);return c.getTimeInMillis();}
    private void line(String s,int size){StatisticsUi.add(content,StatisticsUi.text(activity,s,size));}
    private String n(Double x,String u){return StatisticsUi.number(x,u);}
    private void show(){int y=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));}
    private void render() {
        for(int i=0;i<tabButtons.size();i++) select(tabButtons.get(i),i==tab);
        for(Button button:periodButtons) select(button,(Integer)button.getTag()==period);
        content.removeAllViews();List<StatisticsStore.Entry> entries=store.list(since());
        if(tab==0) {
            liveCard(entries);
            dashboard(entries);
            return;
        }
        String type=tab==1?"trip":tab==2?"charge":"fuel";
        if(tab>=2)StatisticsUi.add(content,StatisticsUi.button(activity,tab==2?"+ Добавить зарядку":"+ Добавить заправку",()->purchase(type)));
        if(tab==1) {
            StatisticsSummary sum=new StatisticsSummary(entries);
            line(sum.trips+" сеансов · "+n(sum.km,"км")+" · "+duration(sum.elapsed)+" за сеансы",17);
        }
        String day="";int count=0;
        for(StatisticsStore.Entry e:entries)if(e.type.equals(type)) {
            String date=new SimpleDateFormat("dd MMMM yyyy · EEEE",new Locale("ru")).format(new Date(e.start));
            if(!date.equals(day)){line(date,16);day=date;}
            entryCard(e);count++;
        }
        if(count==0){if(tab==1)empty();else line("За выбранный период "+(tab==2?"зарядок":"заправок")+" нет. Новую запись можно добавить кнопкой выше.",17);}
    }
    private void liveCard(List<StatisticsStore.Entry> entries){
        if(AppPreferences.isDemoModeEnabled(activity))return;
        LinearLayout card=panel();
        StatisticsSample sample=StatisticsService.latest;
        long age=System.currentTimeMillis()-StatisticsService.lastSampleAt;
        label(card,"СЕЙЧАС · "+(sample==null||age>30000?"нет свежих показаний":sample.speed!=null&&sample.speed>1?"в движении":"на стоянке"),14,electric());
        label(card,StatisticsService.historyStatus,12,secondary());
        StatisticsStore.Entry active=null;for(StatisticsStore.Entry e:entries)if(e.active&&e.type.equals("trip")){active=e;break;}
        if(sample!=null&&age<=30000){
            label(card,"Показания "+date(StatisticsService.lastSampleAt,"HH:mm:ss")+" · мощность автомобиля "+(sample.watts!=null?n(sample.watts/1000,"кВт"):sample.batteryPowerKw==null?"—":sample.batteryPowerKw+" кВт (округлённо)"),15,primary());
            if(active!=null){
                label(card,"Учёт с "+date(active.start,"HH:mm")+" · "+duration(Math.max(0,System.currentTimeMillis()-active.start))+" · "+n(active.electricityKwh(),"кВт·ч")+" · "+n(active.cost(),"₽"),21,primary());
                label(card,"BMS: "+n(active.energyStart,"кВт·ч")+" → "+n(sample.lifetimeElectricityKwh,"кВт·ч")+" · бензин "+n(active.fuelLitres(),"л"),13,secondary());
                label(card,"Счётчик BMS меняется шагами по 0,1 кВт·ч. Пока шаг не изменился, оценка расхода может оставаться 0. Учёт начат при подключении, предыдущее время не восстанавливается.",12,secondary());
            }else label(card,StatisticsService.status,15,secondary());
        }else label(card,"Расход не оценивается без свежей телеметрии.",14,secondary());
        StatisticsUi.add(content,card);
    }
    private void dashboard(List<StatisticsStore.Entry> entries) {
        StatisticsSummary s=new StatisticsSummary(entries);
        LinearLayout hero=panel();
        boolean hasNative=false;for(StatisticsStore.Entry e:entries)if(e.nativeRecord()){hasNative=true;break;}
        if(hasNative)label(hero,"История BYD + наблюдения BMS · стоимость истории по тарифам при импорте",11,secondary());
        label(hero,s.priced==s.trips&&s.trips>0?"СТОИМОСТЬ ПОЕЗДОК":"РАССЧИТАННАЯ СТОИМОСТЬ",12,secondary());
        label(hero,n(s.priced>0?s.cost:null,"₽"),38,primary());
        label(hero,s.trips==0?"Поездки записываются автоматически":s.priced+" из "+s.trips+" сеансов · оценка по расходу",13,secondary());
        if(s.priced>0) {
            costBar(hero,s.pricedElectricity,s.pricedFuel);
            label(hero,"Электричество  "+n(s.pricedElectricity,"₽"),14,electric());label(hero,"Бензин  "+n(s.pricedFuel,"₽"),14,fuel());
        }
        if(s.priced<s.trips)label(hero,"Остальные поездки: недостаточно данных для полной стоимости.",12,activity.getColor(R.color.warning));
        if(s.incomplete>0)label(hero,"Неполные показания: "+s.incomplete+" сеансов",12,activity.getColor(R.color.warning));
        LinearLayout left=StatisticsUi.column(activity),right=StatisticsUi.column(activity);
        StatisticsUi.add(left,hero);
        LinearLayout metrics=new LinearLayout(activity);
        if(wide&&!roomy){
            metrics.setOrientation(VERTICAL);metricRow(metrics,"Пробег",n(s.km,"км"));metricRow(metrics,"Время сеансов",duration(s.elapsed));metricRow(metrics,"Цена километра",n(s.costPerKm(),"₽/км"));
        }else{
            tile(metrics,"Пробег",n(s.km,"км"),s.trips+" сеансов",primary());
            tile(metrics,"Время сеансов",duration(s.elapsed),"За выбранный период",primary());
            tile(metrics,"Цена километра",n(s.costPerKm(),"₽/км"),"По поездкам с полной стоимостью",primary());
        }
        StatisticsUi.add(left,metrics);
        if(wide&&!roomy)label(left,"Цена километра — по поездкам с полной стоимостью",11,secondary());
        LinearLayout resources=new LinearLayout(activity);
        resource(resources,"ЭЛЕКТРИЧЕСТВО",n(s.electricityKnown>0?s.electricity:null,"кВт·ч"),n(s.electricityPer100(),"кВт·ч/100 км"),n(s.electricityKnown>0?s.electricityCost:null,"₽"),s.electricityKnown,s.trips,electric());
        resource(resources,"БЕНЗИН",n(s.fuelKnown>0?s.litres:null,"л"),n(s.fuelPer100(),"л/100 км"),n(s.fuelKnown>0?s.fuelCost:null,"₽"),s.fuelKnown,s.trips,fuel());
        StatisticsUi.add(right,resources);
        if(s.trips>0) {
            LinearLayout chart=panel();label(chart,"Стоимость по дням · ₽",19,primary());
            label(chart,"7 последних дней с записями · энергия / бензин",12,secondary());
            chart.addView(new TripCostChart(activity,entries),new LinearLayout.LayoutParams(-1,dp(roomy?160:115)));
            label(chart,"Только рассчитанные поездки. * — неполный итог дня; прочерк — нет полной стоимости.",12,secondary());StatisticsUi.add(right,chart);
        }
        if(wide){LinearLayout columns=new LinearLayout(activity);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginEnd(dp(10));columns.addView(left,lp);columns.addView(right,new LinearLayout.LayoutParams(0,-2,1));StatisticsUi.add(content,columns);}else{StatisticsUi.add(content,left);StatisticsUi.add(content,right);}
        if(s.trips==0){empty();}else{
            line("Последние поездки",19);int shown=0;
            for(StatisticsStore.Entry e:entries)if(e.type.equals("trip")&&shown++<3)entryCard(e);
            StatisticsUi.add(content,StatisticsUi.button(activity,"Все поездки  ›",()->{tab=1;show();scroll.post(()->scroll.scrollTo(0,0));}));
        }
        int archived=store.archivedObservations();if(archived>0)line("Штатные записи BYD заменили "+archived+" пересекающихся наблюдений лаунчера. Исходные наблюдения сохранены в базе и не считаются повторно.",12);
        LinearLayout counters=new LinearLayout(activity);counter(counters,1);counter(counters,2);StatisticsUi.add(content,counters);
        LinearLayout ledger=panel();double charges=0,fills=0;int nc=0,nf=0;
        for(StatisticsStore.Entry e:entries)if(e.cost()!=null){if(e.type.equals("charge")){charges+=e.cost();nc++;}if(e.type.equals("fuel")){fills+=e.cost();nf++;}}
        label(ledger,"Покупки за период",18,primary());
        label(ledger,"Зарядки: "+n(charges,"₽")+" · "+nc+"     Заправки: "+n(fills,"₽")+" · "+nf,15,primary());
        label(ledger,"Расход «до полного»: "+n(StatisticsStore.fullTankConsumption(store.list(0)),"л/100 км"),14,secondary());
        label(ledger,"Покупки учитываются отдельно от стоимости сеансов и не прибавляются к ней.",12,secondary());StatisticsUi.add(content,ledger);
        StatisticsUi.add(content,StatisticsUi.button(activity,"Тарифы и запись",()->MainActivity.openTripSettings()));
        StatisticsUi.add(content,StatisticsUi.button(activity,"Экспорт журнала CSV",()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"BYD-Song-statistics.csv");
            activity.startActivityForResult(intent,482);
        }));
    }
    private void empty(){
        LinearLayout card=panel();label(card,"Здесь появятся ваши поездки",22,primary());
        label(card,"Начните движение — пробег, бензин, электричество и стоимость будут записываться автоматически при доступной телеметрии.",16,secondary());
        label(card,"За другой период уже могут быть записи. Тарифы можно задать в настройках статистики.",14,secondary());StatisticsUi.add(content,card);
    }
    private void entryCard(StatisticsStore.Entry e) {
        boolean trip=e.type.equals("trip"),charge=e.type.equals("charge"),opened=expanded==e.id;
        LinearLayout card=panel();
        if(trip) {
            label(card,(e.nativeRecord()?"BYD · штатный журнал":"BMS · наблюдение лаунчера")+(e.km<.1?" · стоянка":" · поездка"),11,secondary());
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
            compact(row,"Время",date(e.start,"HH:mm")+" – "+(e.active?"сейчас":date(e.end,"HH:mm")),secondary());
            compact(row,"Пробег",n(e.km,"км"),primary());
            if(wide)compact(row,"Длительность",duration(Math.max(0,e.end-e.start)),primary());
            compact(row,e.nativeRecord()?"Баланс батареи":"Энергия BMS",n(e.nativeRecord()&&e.grid==null?e.nativeEnergy:e.electricityKwh(),"кВт·ч"),electric());
            compact(row,"Бензин",n(e.fuelLitres(),"л"),fuel());
            compact(row,"Стоимость",n(e.cost(),"₽"),primary());
            card.addView(row);
            if(e.active)label(card,"● Идёт учёт · оценка BMS с шагом 0,1 кВт·ч",12,electric());
            if(e.cost()==null)label(card,"Стоимость неизвестна · не хватает данных расхода",12,activity.getColor(R.color.warning));
            // The whole summary opens the details; no tall action strip between each trip.
            card.setOnClickListener(v->{expanded=opened?-1:e.id;show();});
            card.setContentDescription("Поездка "+date(e.start,"dd.MM HH:mm")+", "+n(e.km,"км")+", "+n(e.cost(),"₽")+". Нажмите для подробностей");
            label(card,opened?"⌃ Свернуть":"› Подробнее",12,secondary());
        } else {
            label(card,date(e.start,"dd.MM · HH:mm")+(e.active?"   ● Запись идёт":""),15,secondary());
            label(card,(charge?n(e.grid==null?e.recovered:e.grid,"кВт·ч"):n(e.litres,"л"))+"   ·   "+n(e.cost(),"₽"),25,primary());
            label(card,charge?"Тариф "+n(e.electricPrice,"₽/кВт·ч")+(e.grid==null?" · оценка по батарее":" · из сети"):"Цена "+n(e.fuelPrice,"₽/л")+(e.full?" · до полного":""),14,secondary());
            StatisticsUi.add(card,StatisticsUi.button(activity,opened?"Свернуть подробности  ⌃":"Подробности  ›",()->{expanded=opened?-1:e.id;show();}));
        }
        if(opened) {
            if(trip) {
                label(card,n(e.km>=.1&&e.electricityKwh()!=null?e.electricityKwh()*100/e.km:null,"кВт·ч/100 км")+" · "+n(e.km>=.1&&e.fuelLitres()!=null?e.fuelLitres()*100/e.km:null,"л/100 км")+" · "+n(e.km>=.1&&e.cost()!=null?e.cost()/e.km:null,"₽/км"),18,primary());
                costBar(card,e.electricityCost()==null?0:e.electricityCost(),e.fuelCost()==null?0:e.fuelCost());
                label(card,"Электричество: "+n(e.electricityCost(),"₽")+" · "+n(e.electricPrice,"₽/кВт·ч"),16,electric());
                label(card,"Бензин: "+n(e.fuelCost(),"₽")+" · "+n(e.fuelPrice,"₽/л"),16,fuel());
                label(card,"Средняя за сеанс "+n(e.end>e.start?e.km*3600000/(e.end-e.start):null,"км/ч")+(e.nativeRecord()?"":" · максимум "+n(e.max,"км/ч")),14,primary());
                if(e.nativeRecord()){
                    label(card,"Баланс батареи по BYD "+n(e.nativeEnergy,"кВт·ч")+". Отрицательное значение означает прирост энергии в батарее; он не вычитается из стоимости бензина.",13,secondary());
                }else{
                    if(e.energyStart!=null)label(card,"Счётчик BMS: "+n(e.energyStart,"кВт·ч")+" → "+n(e.energyEnd,"кВт·ч")+" · шаг 0,1 кВт·ч",13,secondary());
                    if(e.observed>0&&e.missing==0&&e.powerMissing==0)label(card,"Интеграл HV: разряд "+n(e.used,"кВт·ч")+" · возврат "+n(e.recovered,"кВт·ч"),14,primary());
                }
                label(card,"Возврат включает рекуперацию и генерацию ДВС. Стоимость — оценка: энергия × тариф + бензин × цена литра.",12,secondary());
                label(card,"Источники: электричество — "+e.electricitySource()+"; бензин — "+e.fuelSource()+". Тариф: "+e.priceSource,12,secondary());
            } else if(!charge)label(card,"Одометр "+n(e.odometer,"км"),15,primary());
            if(e.missing>0)label(card,"Неполные данные · пропуски "+e.missing/1000+" с",13,activity.getColor(R.color.warning));
            if(e.note!=null&&!e.note.isEmpty())label(card,e.note,13,secondary());
            if(!e.active){
                StatisticsUi.add(card,StatisticsUi.button(activity,trip||charge?"Уточнить расход":"Изменить заправку",()->{if(trip||charge)correct(e);else editFuel(e);}));
                StatisticsUi.add(card,StatisticsUi.button(activity,"Удалить запись",()->new AlertDialog.Builder(activity).setTitle("Удалить запись?").setMessage("Запись исчезнет из журнала и итогов статистики.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{store.delete(e.id);expanded=-1;show();}).show()));
            }
        }
        StatisticsUi.add(content,card);
    }
    private void metricRow(LinearLayout parent,String title,String value){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(5),dp(12),dp(5));
        TextView name=StatisticsUi.text(activity,title,13);name.setTextColor(secondary());row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(StatisticsUi.text(activity,value,20));StatisticsUi.add(parent,row);
    }
    private void compact(LinearLayout row,String title,String value,int color){
        LinearLayout cell=StatisticsUi.column(activity);cell.setPadding(0,0,dp(6),0);label(cell,title,11,secondary());label(cell,value,wide?18:15,color);row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }
    private int primary(){return activity.getColor(R.color.text_primary);}
    private int secondary(){return activity.getColor(R.color.text_secondary);}
    private int electric(){return activity.getColor(R.color.success);}
    private int fuel(){return activity.getColor(R.color.warning);}
    private LinearLayout panel(){
        LinearLayout box=StatisticsUi.column(activity);box.setPadding(dp(16),dp(12),dp(16),dp(12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(activity.getColor(R.color.surface_light));bg.setCornerRadius(dp(16));box.setBackground(bg);return box;
    }
    private void label(LinearLayout box,String text,int size,int color){TextView v=StatisticsUi.text(activity,text,size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setPadding(0,dp(3),0,dp(3));box.addView(v);}
    private void tile(LinearLayout row,String title,String value,String hint,int color){
        LinearLayout box=panel();label(box,title,12,secondary());label(box,value,22,color);label(box,hint,12,secondary());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMarginEnd(dp(6));row.addView(box,lp);
    }
    private void resource(LinearLayout row,String title,String amount,String rate,String cost,int known,int total,int color){
        LinearLayout box=panel();label(box,title,12,color);label(box,amount,roomy?27:22,primary());label(box,rate,roomy?18:14,color);label(box,cost,roomy?19:17,primary());
        label(box,"Данные: "+known+" из "+total+" сеансов",12,secondary());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginEnd(dp(6));row.addView(box,lp);
    }
    private void costBar(LinearLayout box,double electricity,double fuelCost){
        LinearLayout bar=new LinearLayout(activity);double total=electricity+fuelCost;
        if(total<=0)return;
        if(electricity>0){View v=new View(activity);v.setBackgroundColor(electric());bar.addView(v,new LinearLayout.LayoutParams(0,dp(6),(float)(electricity/total)));}
        if(fuelCost>0){View v=new View(activity);v.setBackgroundColor(fuel());bar.addView(v,new LinearLayout.LayoutParams(0,dp(6),(float)(fuelCost/total)));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(6));lp.topMargin=dp(10);lp.bottomMargin=dp(8);box.addView(bar,lp);
    }
    private static String duration(long ms){long m=ms/60000;return m>=60?m/60+" ч "+m%60+" мин":m+" мин";}
    private static String date(long ms,String pattern){return new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(ms));}
    private void select(Button button,boolean selected) {
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(selected?R.color.accent:R.color.button)));
        button.setTextColor(selected?android.graphics.Color.WHITE:activity.getColor(R.color.text_primary));
    }
    private void counter(LinearLayout row,int index) {
        long reset=AppPreferences.get(activity).getLong("trip_counter_"+index,0);
        StatisticsSummary s=new StatisticsSummary(store.list(reset));
        LinearLayout card=panel();label(card,"TRIP "+index,13,secondary());label(card,n(s.km,"км"),24,primary());
        label(card,n(s.priced>0?s.cost:null,"₽")+" · "+s.priced+" из "+s.trips+" сеансов",13,secondary());
        label(card,n(s.electricityPer100(),"кВт·ч/100 км")+" · "+n(s.fuelPer100(),"л/100 км"),13,primary());
        label(card,reset==0?"С начала учёта":"С "+date(reset,"dd.MM HH:mm"),12,secondary());
        StatisticsUi.add(card,StatisticsUi.button(activity,"Сбросить",()->{
            for(StatisticsStore.Entry e:store.list(0))if(e.active&&e.type.equals("trip")){Toast.makeText(activity,"Счётчик можно сбросить после завершения текущей поездки",Toast.LENGTH_LONG).show();return;}
            new AlertDialog.Builder(activity).setTitle("Сбросить TRIP "+index+"?").setMessage("История сохранится. Счётчик начнёт учитывать следующие поездки.").setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(d,w)->{AppPreferences.get(activity).edit().putLong("trip_counter_"+index,System.currentTimeMillis()).apply();show();}).show();
        }));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginEnd(dp(6));row.addView(card,lp);
    }
    private void editFuel(StatisticsStore.Entry e) {
        LinearLayout fields=StatisticsUi.column(activity);fields.setPadding(dp(20),0,dp(20),0);
        EditText amount=StatisticsUi.input(activity,"Объём · л",e.litres);
        EditText price=StatisticsUi.input(activity,"Цена · ₽/л",e.fuelPrice);
        EditText odo=StatisticsUi.input(activity,"Одометр · км",e.odometer);
        Switch full=new Switch(activity);full.setText("До полного");full.setTextColor(activity.getColor(R.color.text_primary));full.setChecked(e.full);
        StatisticsUi.addInput(fields,"Объём · л",amount);StatisticsUi.addInput(fields,"Цена · ₽/л",price);StatisticsUi.addInput(fields,"Одометр · км",odo);StatisticsUi.add(fields,full);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Заправка").setView(StatisticsUi.scroll(activity,fields)).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try {
            double a=StatisticsUi.parse(amount,true,1000),p=StatisticsUi.parse(price,true,100000);
            Double o=StatisticsUi.parse(odo,full.isChecked(),10000000);
            if(a<=0){amount.setError("Введите объём больше нуля");return;}
            store.updatePurchase(e.id,a,p,o,full.isChecked());dialog.dismiss();show();
        }catch(NumberFormatException ignored){}}));dialog.show();
    }
    private void correct(StatisticsStore.Entry e) {
        LinearLayout fields=StatisticsUi.column(activity);fields.setPadding(dp(20),0,dp(20),0);
        EditText litres=StatisticsUi.input(activity,"Израсходовано бензина · л",e.litres);
        EditText grid=StatisticsUi.input(activity,e.type.equals("trip")?"Энергия поездки · кВт·ч (необязательно)":"Энергия по счётчику сети · кВт·ч",e.grid);
        if(e.type.equals("trip"))StatisticsUi.addInput(fields,"Израсходовано бензина · л",litres);StatisticsUi.addInput(fields,e.type.equals("trip")?"Энергия поездки · кВт·ч (необязательно)":"Энергия по счётчику сети · кВт·ч",grid);
        StatisticsUi.add(fields,StatisticsUi.text(activity,"Из источника записи: "+n(e.nativeRecord()?e.nativeFuel:e.automaticFuelLitres(),"л"),14));
        StatisticsUi.add(fields,StatisticsUi.text(activity,"Пустые поля включают автоматический расчёт. 0 — ручное подтверждение нулевого расхода. Тариф этой записи: "+n(e.electricPrice,"₽/кВт·ч")+", "+n(e.fuelPrice,"₽/л"),14));
        AlertDialog d=new AlertDialog.Builder(activity).setTitle("Фактический расход").setView(StatisticsUi.scroll(activity,fields)).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{store.correct(e.id,e.type.equals("trip")?StatisticsUi.parse(litres,false,1000):null,StatisticsUi.parse(grid,false,10000));d.dismiss();show();}catch(NumberFormatException ignored){}}));d.show();
    }
    private void purchase(String type) {
        boolean fuel=type.equals("fuel");LinearLayout fields=StatisticsUi.column(activity);fields.setPadding(dp(20),0,dp(20),0);
        EditText amount=StatisticsUi.input(activity,fuel?"Объём · л":"Энергия по счётчику · кВт·ч",null);
        EditText price=StatisticsUi.input(activity,fuel?"Цена · ₽/л":"Цена · ₽/кВт·ч",(double)AppPreferences.get(activity).getFloat(fuel?"fuel_price":"electricity_price",fuel?60:6));
        EditText odo=StatisticsUi.input(activity,"Одометр · км",null);Switch full=new Switch(activity);full.setText("Бак до полного");full.setTextColor(activity.getColor(R.color.text_primary));
        StatisticsUi.addInput(fields,fuel?"Объём · л":"Энергия по счётчику · кВт·ч",amount);StatisticsUi.addInput(fields,fuel?"Цена · ₽/л":"Цена · ₽/кВт·ч",price);if(fuel){StatisticsUi.addInput(fields,"Одометр · км",odo);StatisticsUi.add(fields,full);}
        StatisticsUi.add(fields,StatisticsUi.text(activity,fuel?"Запись добавится на текущее время. Для расчёта расхода внесите одометр и отметьте полный бак.":"Запись добавится на текущее время. Не дублируйте автоматически записанную зарядку: используйте «Уточнить расход» в её карточке.",13));
        AlertDialog d=new AlertDialog.Builder(activity).setTitle(fuel?"Заправка":"Зарядка").setView(StatisticsUi.scroll(activity,fields)).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{
            double a=StatisticsUi.parse(amount,true,fuel?1000:10000),p=StatisticsUi.parse(price,true,100000);
            Double o=fuel?StatisticsUi.parse(odo,full.isChecked(),10000000):null;
            if(a<=0){amount.setError("Введите значение больше нуля");return;}
            store.addPurchase(type,System.currentTimeMillis(),a,p,o,full.isChecked());d.dismiss();show();
        }catch(NumberFormatException ignored){}}));d.show();
    }
}
