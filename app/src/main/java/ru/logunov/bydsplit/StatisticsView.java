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
    private final List<Button> tabButtons=new ArrayList<>(), periodButtons=new ArrayList<>();
    private final Runnable refresh=new Runnable(){public void run(){status.setText(AppPreferences.isDemoModeEnabled(activity)?"Эмулятор · реальная запись отключена":StatisticsService.status);show();postDelayed(this,15000);}};
    StatisticsView(Activity activity,Runnable close) { this(activity,close,0,30); }
    int selectedTab(){return tab;}
    int selectedPeriod(){return period;}
    StatisticsView(Activity activity,Runnable close,int selectedTab,int selectedPeriod) {
        super(activity);this.activity=activity;store=new StatisticsStore(activity);
        tab=selectedTab;period=selectedPeriod;
        setOrientation(VERTICAL);setPadding(dp(16),dp(12),dp(16),dp(12));android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(activity.getColor(R.color.background));background.setCornerRadius(dp(14));setBackground(background);setClipToOutline(true);setClickable(true);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(StatisticsUi.text(activity,"Статистика · Song DM-i",22),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(StatisticsUi.button(activity,"‹ Назад",close),new LinearLayout.LayoutParams(dp(105),dp(42)));addView(header);
        status=StatisticsUi.text(activity,StatisticsService.status,12);addView(status);
        LinearLayout tabs=new LinearLayout(activity);String[] labels={"Обзор","Поездки","Зарядки","Заправки"};
        for(int i=0;i<labels.length;i++){final int index=i;Button button=StatisticsUi.button(activity,labels[i],()->{tab=index;show();});tabButtons.add(button);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMarginEnd(dp(4));tabs.addView(button,lp);}addView(tabs);
        LinearLayout filters=new LinearLayout(activity);
        for(int days:new int[]{1,7,30,0}) {
            Button button=StatisticsUi.button(activity,days==0?"Всё время":days==1?"Сегодня":days+" дней",()->{period=days;show();});
            button.setTag(days);periodButtons.add(button);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMarginEnd(dp(4));lp.topMargin=dp(4);filters.addView(button,lp);
        }
        addView(filters);
        ScrollView scroll=new ScrollView(activity);content=StatisticsUi.column(activity);scroll.addView(content);addView(scroll,new LinearLayout.LayoutParams(-1,0,1));show();
    }
    private int dp(int n){return StatisticsUi.dp(activity,n);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(refresh);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(refresh);store.close();super.onDetachedFromWindow();}
    private long since(){Calendar c=Calendar.getInstance();if(period==0)return 0;c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.DAY_OF_YEAR,1-period);return c.getTimeInMillis();}
    private void line(String s,int size){StatisticsUi.add(content,StatisticsUi.text(activity,s,size));}
    private String n(Double x,String u){return StatisticsUi.number(x,u);}
    private void show() {
        for(int i=0;i<tabButtons.size();i++) select(tabButtons.get(i),i==tab);
        for(Button button:periodButtons) select(button,(Integer)button.getTag()==period);
        content.removeAllViews();List<StatisticsStore.Entry> entries=store.list(since());
        if(tab==0) {
            double km=0,used=0,recovered=0,cost=0,purchases=0;int count=0,priced=0,incomplete=0;long moving=0;
            for(StatisticsStore.Entry e:entries) {
                if(e.type.equals("trip")){km+=e.km;used+=e.used;recovered+=e.recovered;moving+=e.moving;count++;if(e.missing>0)incomplete++;if(e.cost()!=null){cost+=e.cost();priced++;}}
                else if(e.cost()!=null)purchases+=e.cost();
            }
            line("Пробег  "+n(km,"км")+"     Поездок  "+count,22);
            line("Энергия батареи: разряд "+n(used,"кВт·ч")+" · возврат "+n(recovered,"кВт·ч"),17);
            line("Баланс на 100 км  "+n(km>=.1?(used-recovered)*100/km:null,"кВт·ч/100 км"),20);
            line("В движении  "+(moving/60000)+" мин · с пропусками данных: "+incomplete,15);
            line("Оценка стоимости поездок  "+n(priced>0?cost:null,"₽")+" · рассчитано "+priced+" из "+count,18);
            electricitySummary(entries);
            fuelSummary(entries);
            line("Покупки топлива и зарядки  "+n(purchases,"₽"),18);
            line("Расход бензина «до полного»  "+n(StatisticsStore.fullTankConsumption(store.list(0)),"л/100 км"),18);
            line("Стоимость рассчитывается автоматически: энергия × тариф + литры × цена. Это оценка по телеметрии. Возврат включает рекуперацию и генерацию ДВС. Покупки и стоимость поездок не складываются.",13);
            counter(1); counter(2);
            StatisticsUi.add(content,StatisticsUi.button(activity,"Тарифы и запись",()->MainActivity.openTripSettings()));
            StatisticsUi.add(content,StatisticsUi.button(activity,"Экспорт журнала CSV",()->{
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"BYD-Song-statistics.csv");
                activity.startActivityForResult(intent,482);
            }));
            if(entries.isEmpty())line("История пока пуста. При доступной телеметрии первая поездка появится после начала движения. Можно внести зарядку или заправку вручную.",16);
            return;
        }
        String type=tab==1?"trip":tab==2?"charge":"fuel";
        if(tab>=2)StatisticsUi.add(content,StatisticsUi.button(activity,tab==2?"+ Добавить зарядку":"+ Добавить заправку",()->purchase(type)));
        int count=0;
        for(StatisticsStore.Entry e:entries)if(e.type.equals(type)) {
            count++;LinearLayout card=StatisticsUi.column(activity);card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackgroundColor(activity.getColor(R.color.surface_light));
            String date=new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(e.start));
            StatisticsUi.add(card,StatisticsUi.text(activity,date+(e.active?" · запись идёт":""),18));
            String summary=type.equals("trip")?n(e.km,"км")+" · "+n(e.km>=.1&&e.electricityKwh()!=null?e.electricityKwh()*100/e.km:null,"кВт·ч/100 км")
                    :type.equals("charge")?n(e.grid==null?e.recovered:e.grid,"кВт·ч")+" · "+(e.grid==null?"по батарее":"из сети, вручную")
                    :n(e.litres,"л")+(e.full?" · до полного":"")+" · одометр "+n(e.odometer,"км");
            StatisticsUi.add(card,StatisticsUi.text(activity,summary,17));
            StatisticsUi.add(card,StatisticsUi.text(activity,(type.equals("trip")?"Полная стоимость ":"Стоимость ")+n(e.cost(),"₽")+(e.type.equals("trip") || e.type.equals("charge") && e.grid == null?" · оценка":""),17));
            if(type.equals("charge"))StatisticsUi.add(card,StatisticsUi.text(activity,"Тариф "+n(e.electricPrice,"₽/кВт·ч")+" · "+e.priceSource+" · потери "+n(e.chargeLoss,"%"),13));
            if(e.missing>0)StatisticsUi.add(card,StatisticsUi.text(activity,"Неполные данные · пропуски "+e.missing/1000+" с",13));
            if(!e.note.isEmpty())StatisticsUi.add(card,StatisticsUi.text(activity,e.note,13));
            if(type.equals("trip")) {
                StatisticsUi.add(card,StatisticsUi.text(activity,"Стоимость на 100 км "+n(e.cost()!=null&&e.km>=.1?e.cost()*100/e.km:null,"₽/100 км"),15));
                StatisticsUi.add(card,StatisticsUi.text(activity,"Разряд "+n(e.used,"кВт·ч")+" · возврат "+n(e.recovered,"кВт·ч")+"\nВ движении "+e.moving/60000+" мин · максимум "+n(e.max,"км/ч")+(e.engine?" · ДВС / нет сигнала ДВС":""),14));
                Double litres=e.fuelLitres();
                StatisticsUi.add(card,StatisticsUi.text(activity,"Бензин "+n(litres,"л")+" · "+n(litres!=null&&e.km>=.1?litres*100/e.km:null,"л/100 км")+" · "+e.fuelSource(),17));
                StatisticsUi.add(card,StatisticsUi.text(activity,"Стоимость топлива "+n(e.fuelCost(),"₽"),16));
                if(e.fuelStart!=null||e.fuelEnd!=null)StatisticsUi.add(card,StatisticsUi.text(activity,"Счётчик: "+n(e.fuelStart,"л")+" → "+n(e.fuelEnd,"л")+(e.fuelInvalid?" · сброс / скачок":" · шаг 0,1 л"),13));
                if(litres==null)StatisticsUi.add(card,StatisticsUi.text(activity,"Нет надёжной пары показаний топлива на границах поездки. Можно уточнить литры вручную.",13));
                StatisticsUi.add(card,StatisticsUi.text(activity,"Электричество "+n(e.electricityKwh(),"кВт·ч")+" · "+n(e.electricityCost(),"₽")+" · "+e.electricitySource(),17));
                StatisticsUi.add(card,StatisticsUi.text(activity,"Тариф "+n(e.electricPrice,"₽/кВт·ч")+" · "+e.priceSource,13));
                if(e.cost()==null)StatisticsUi.add(card,StatisticsUi.text(activity,"Полная стоимость недоступна: нет достоверной энергии или литров. Известные части показаны отдельно.",13));
            }
            if(!e.active&&(type.equals("trip")||type.equals("charge")))StatisticsUi.add(card,StatisticsUi.button(activity,"Уточнить расход",()->correct(e)));
            if(!e.active) {
                if(type.equals("fuel")) StatisticsUi.add(card,StatisticsUi.button(activity,"Изменить заправку",()->editFuel(e)));
                StatisticsUi.add(card,StatisticsUi.button(activity,"Удалить запись",()->new AlertDialog.Builder(activity)
                        .setTitle("Удалить запись?").setMessage("Запись исчезнет из журнала и итогов статистики.")
                        .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{store.delete(e.id);show();}).show()));
            }
            StatisticsUi.add(content,card);
        }
        if(count==0)line("За выбранный период записей нет.",17);
    }
    private void electricitySummary(List<StatisticsStore.Entry> records) {
        double kwh=0,cost=0;int known=0,total=0;
        for(StatisticsStore.Entry e:records) if(e.type.equals("trip")) {
            total++;
            if(e.electricityKwh()!=null){kwh+=e.electricityKwh();cost+=e.electricityCost();known++;}
        }
        line("Электрическая часть  "+n(known>0?kwh:null,"кВт·ч")+" · "+n(known>0?cost:null,"₽")+" · данные "+known+" из "+total,17);
    }
    private void fuelSummary(List<StatisticsStore.Entry> records) {
        double litres=0,km=0,cost=0;int known=0,total=0;
        for(StatisticsStore.Entry e:records) if(e.type.equals("trip")) {
            total++;
            Double amount=e.fuelLitres();
            if(amount!=null){litres+=amount;km+=e.km;cost+=amount*e.fuelPrice;known++;}
        }
        line("Бензин  "+n(known>0?litres:null,"л")+" · "+n(km>=.1?litres*100/km:null,"л/100 км"),18);
        line("Топливо: данные для "+known+" из "+total+" поездок ("+n(km,"км")+") · стоимость "+n(known>0?cost:null,"₽"),13);
    }
    private void select(Button button,boolean selected) {
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(selected?R.color.accent:R.color.button)));
        button.setTextColor(selected?android.graphics.Color.WHITE:activity.getColor(R.color.text_primary));
    }
    private void counter(int index) {
        long reset=AppPreferences.get(activity).getLong("trip_counter_"+index,0);
        List<StatisticsStore.Entry> records=store.list(reset);
        double km=0,net=0,cost=0;int count=0,priced=0;
        for(StatisticsStore.Entry e:records) if(e.type.equals("trip")) {
            km+=e.km;net+=e.used-e.recovered;count++;
            if(e.cost()!=null){cost+=e.cost();priced++;}
        }
        line("TRIP "+index+" · "+n(km,"км")+" · "+n(km>=.1?net*100/km:null,"кВт·ч/100 км"),18);
        electricitySummary(records);
        fuelSummary(records);
        line("Оценка "+n(priced>0?cost:null,"₽")+" · рассчитано "+priced+" из "+count+" поездок · "+(reset==0?"с начала учёта":new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(reset))),13);
        StatisticsUi.add(content,StatisticsUi.button(activity,"Сбросить TRIP "+index,()->{
            for(StatisticsStore.Entry e:store.list(0)) if(e.active&&e.type.equals("trip")) {
                Toast.makeText(activity,"Счётчик можно сбросить после завершения текущей поездки",Toast.LENGTH_LONG).show();return;
            }
            new AlertDialog.Builder(activity).setTitle("Сбросить TRIP "+index+"?")
                    .setMessage("История сохранится. Счётчик начнёт учитывать следующие поездки.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(d,w)->{
                        AppPreferences.get(activity).edit().putLong("trip_counter_"+index,System.currentTimeMillis()).apply();show();
                    }).show();
        }));
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
        StatisticsUi.add(fields,StatisticsUi.text(activity,"Автоматически по счётчику: "+n(e.automaticFuelLitres(),"л"),14));
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
