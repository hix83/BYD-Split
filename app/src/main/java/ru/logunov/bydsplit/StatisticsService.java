package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

/** Foreground telemetry journal, independent of which launcher pane is visible. */
public final class StatisticsService extends Service {
    static final String ENABLED = "statistics_enabled";
    private ScheduledExecutorService worker;
    private ScheduledFuture<?> polling;
    private VehicleSleepController sleep;
    private final Runnable sleepListener=this::syncPolling;
    private volatile long polls,historyReads;
    private StatisticsStore store;
    private TripAccumulator trip, charge;
    private long tripId, chargeId, lastGood, lastSave;
    private int chargeIdleSamples;
    private long lastHistory;
    private final VehicleSessionState session=new VehicleSessionState();
    static volatile StatisticsSample latest;
    static volatile long lastSampleAt;
    static volatile String historyStatus="Штатная история BYD: ожидание чтения";
    static volatile String status = "Запись остановлена";
    static void sync(Context context) {
        boolean enabled=AppPreferences.get(context).getBoolean(ENABLED,true)
                && !AppPreferences.isDemoModeEnabled(context);
        Intent intent=new Intent(context,StatisticsService.class);
        if(enabled) context.startForegroundService(intent); else context.stopService(intent);
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("trip_journal","Учёт поездок",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("open_statistics",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(41,new Notification.Builder(this,"trip_journal").setSmallIcon(R.drawable.ui7_tech)
                .setContentTitle("BYD Split · учёт поездок").setContentText("Поездки и зарядки сохраняются на устройстве")
                .setContentIntent(open).setOngoing(true).build());
        store=new StatisticsStore(this);
        long now=System.currentTimeMillis();
        for(StatisticsStore.Entry e:store.list(0))if(e.active&&e.type.equals("trip")&&now>=e.end&&now-e.end<=120000){trip=e.resume();tripId=e.id;lastGood=e.end;session.resume(e.end);break;}
        store.recoverInterruptedExcept(trip==null?-1:tripId);
        worker=Executors.newSingleThreadScheduledExecutor();
        sleep=VehicleSleepController.get(this);
        sleep.addListener(this,sleepListener);
    }
    private void syncPolling() {
        if(sleep.pollingAllowed()) {
            if(polling==null||polling.isCancelled())polling=worker.scheduleWithFixedDelay(this::poll,0,3,TimeUnit.SECONDS);
        } else {
            if(polling!=null)polling.cancel(false);polling=null;
            worker.execute(()->{finishAll("Машина выключена · наблюдение приостановлено",false);latest=null;lastSampleAt=0;status="Машина спит · ожидание включения или зарядки";});
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_STICKY;}
    private void poll() {
        try {
            if(!sleep.pollingAllowed())return;
            polls++;
            if(!AppPreferences.get(this).getBoolean(ENABLED,true)||AppPreferences.isDemoModeEnabled(this)){stopSelf();return;}
            long now=System.currentTimeMillis();
            LocalAdbManager adb=LocalAdbManager.forStatistics(this);
            if(now-lastHistory>=60000){
                lastHistory=now;
                historyReads++;
                try{org.json.JSONArray rows=adb.readNativeHistory();android.content.SharedPreferences p=AppPreferences.get(this);int added=store.importNative(rows,p.getFloat("electricity_price",6),p.getFloat("fuel_price",60));historyStatus="Штатная история BYD: "+rows.length()+" записей";android.util.Log.i("BYD_STATISTICS","BYD history read="+rows.length()+" added="+added);}
                catch(Exception e){historyStatus="Штатная история BYD временно недоступна";android.util.Log.w("BYD_STATISTICS",historyStatus,e);}
            }
            if(!sleep.pollingAllowed())return;
            StatisticsSample sample=adb.readStatistics();
            if(!sleep.pollingAllowed())return;
            now=System.currentTimeMillis();
            if(sample==null) {
                status="Нет телеметрии · ожидание ADB";
                if(lastGood>0&&now-lastGood>120000) finishAll("Потеря связи · поездка неполная",true);
                return;
            }
            if(lastGood>0&&(now-lastGood>120000||now<lastGood)) finishAll("Разрыв телеметрии",true);
            lastGood=now;latest=sample;lastSampleAt=now;
            boolean running=session.update(now,sample.powerState,sample.speed,sample.engine,sample.charging);
            if(sample.charging && trip!=null) {
                // The charging tick belongs to a new energy session. Close at the last drive sample.
                finishTrip("Перед зарядкой",false);
            }
            if(session.evidence && !sample.charging) {
                if(trip==null){trip=new TripAccumulator();tripId=begin("trip",now,sample);}
            }
            if(trip!=null && session.evidence) {
                trip.electricity.sample(sample.lifetimeElectricityKwh);
                trip.fuel.sample(sample.lifetimeFuelLitres);
                if (trip.fuel.litres() != null && trip.fuel.litres() > 0) trip.engineSeen = true;
                trip.add(now,sample.speed,sample.watts,sample.odometer,sample.engine);
                if (!sample.engineKnown) trip.engineSeen = true; // Unknown engine state also requires manual cost inputs.

            }
            if(trip!=null&&!running&&!sample.charging)finishTrip("Выключение машины подтверждено · конец по последнему рабочему показанию",false);
            if(sample.charging) {
                chargeIdleSamples=0;
                if(charge==null){charge=new TripAccumulator();chargeId=begin("charge",now,sample);}
                charge.add(now,0.,sample.watts,null,false);
            } else if(charge!=null) {
                charge.add(now,0.,sample.watts,null,false);
                if(++chargeIdleSamples>=3){store.checkpoint(chargeId,charge,false,"Зарядка завершена");charge=null;}
            }
            if(now-lastSave>=5000) {
                if(trip!=null)store.checkpoint(tripId,trip,true,"Сеанс наблюдения лаунчера · учёт с момента подключения, включая стоянку");
                if(charge!=null)store.checkpoint(chargeId,charge,true,"");
                lastSave=now;
            }
            status=sample.watts==null&&sample.lifetimeElectricityKwh==null?"Нет показаний мощности и BMS · энергия неполная":trip!=null?(sample.speed!=null&&sample.speed>1?"Записывается поездка · BMS":"Записывается расход на стоянке · BMS"):charge!=null?"Записывается зарядка":"Машина выключена · ожидание включения или зарядки";
            if (sample.watts == null && sample.lifetimeElectricityKwh != null) status += " · шаг 0,1 кВт·ч";
            if (trip != null) status += sample.lifetimeFuelLitres == null ? " · счётчик топлива недоступен" : trip.fuel.invalid ? " · сбой счётчика топлива" : " · топливо: авто";
        } catch(Exception error) {android.util.Log.w("BYD_STATISTICS","Journal read failed",error);status="Не удалось прочитать телеметрию";}
    }
    private long begin(String type,long now,StatisticsSample sample) {
        lastSave=0;
        android.content.SharedPreferences preferences=AppPreferences.get(this);
        double rate=preferences.getFloat("electricity_price",6);
        if(type.equals("charge")&&sample.chargeMode.equals("DC")) rate=preferences.getFloat("dc_electricity_price",(float)rate);
        BatteryPriceHistory.Price price=type.equals("trip")
                ?store.tripPrice(now,getSharedPreferences("tech",0).getFloat("capacity",0),rate,
                    preferences.getBoolean("trip_price_from_charges",true))
                :new BatteryPriceHistory.Price(rate,sample.chargeMode.equals("UNKNOWN")?"настроенный тариф":"тариф "+sample.chargeMode);
        return store.begin(type,now,price.rate,preferences.getFloat("fuel_price",60),price.source,sample.soc,
                type.equals("charge")?preferences.getFloat("charge_loss_percent",0):0);
    }
    private void finishTrip(String note,boolean incomplete) {
        if(trip==null)return;
        if(incomplete){trip.missingMs+=1000;trip.fuel.loseClosingEdge();trip.electricity.loseClosingEdge();}
        store.checkpoint(tripId,trip,false,note);trip=null;
    }
    private void finishAll(String note,boolean incomplete) {
        finishTrip(note,incomplete);
        if(charge!=null){if(incomplete)charge.missingMs+=1000;store.checkpoint(chargeId,charge,false,note);charge=null;}
    }
    @Override public void onDestroy() {
        if(sleep!=null)sleep.removeListener(this);
        if(polling!=null)polling.cancel(false);
        if(worker!=null) {
            worker.execute(()->{finishAll("Наблюдение остановлено · конец по последнему полученному показанию",false);store.close();});
            worker.shutdown();
        }
        latest=null;lastSampleAt=0;status="Запись остановлена";super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        writer.println("Statistics: polls="+polls+" historyReads="+historyReads+" status="+status);
        if(sleep!=null)writer.println("Vehicle sleep: "+sleep.describe());
    }
}
