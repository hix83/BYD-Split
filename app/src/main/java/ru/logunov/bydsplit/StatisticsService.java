package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

/** Foreground telemetry journal, independent of which launcher pane is visible. */
public final class StatisticsService extends Service {
    static final String ENABLED = "statistics_enabled";
    private ScheduledExecutorService worker;
    private StatisticsStore store;
    private TripAccumulator trip, charge;
    private long tripId, chargeId, lastMotion, lastGood, lastSave;
    private int chargeIdleSamples;
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
        store=new StatisticsStore(this);store.recoverInterrupted();
        worker=Executors.newSingleThreadScheduledExecutor();
        worker.scheduleWithFixedDelay(this::poll,0,3,TimeUnit.SECONDS);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_STICKY;}
    private void poll() {
        try {
            if(!AppPreferences.get(this).getBoolean(ENABLED,true)||AppPreferences.isDemoModeEnabled(this)){stopSelf();return;}
            StatisticsSample sample=LocalAdbManager.get(this).readStatistics();
            long now=System.currentTimeMillis();
            if(sample==null||sample.speed==null) {
                status="Нет телеметрии · ожидание ADB";
                if(lastGood>0&&now-lastGood>120000) finishAll("Потеря связи · поездка неполная",true);
                return;
            }
            if(lastGood>0&&(now-lastGood>120000||now<lastGood)) finishAll("Разрыв телеметрии",true);
            lastGood=now;
            if(sample.charging && trip!=null) {
                // The charging tick belongs to a new energy session. Close at the last drive sample.
                finishTrip("Перед зарядкой",false);
            }
            if(sample.speed>1 && !sample.charging) {
                lastMotion=now;
                if(trip==null){trip=new TripAccumulator();tripId=begin("trip",now,sample);}
            }
            if(trip!=null) {
                trip.electricity.sample(sample.lifetimeElectricityKwh);
                trip.fuel.sample(sample.lifetimeFuelLitres);
                if (trip.fuel.litres() != null && trip.fuel.litres() > 0) trip.engineSeen = true;
                trip.add(now,sample.speed,sample.watts,sample.odometer,sample.engine);
                if (!sample.engineKnown) trip.engineSeen = true; // Unknown engine state also requires manual cost inputs.
                if(now-lastMotion>=300000)finishTrip("Завершено после 5 минут без движения",false);
            }
            if(sample.charging) {
                chargeIdleSamples=0;
                if(charge==null){charge=new TripAccumulator();chargeId=begin("charge",now,sample);}
                charge.add(now,0.,sample.watts,null,false);
            } else if(charge!=null) {
                charge.add(now,0.,sample.watts,null,false);
                if(++chargeIdleSamples>=3){store.checkpoint(chargeId,charge,false,"Зарядка завершена");charge=null;}
            }
            if(now-lastSave>=15000) {
                if(trip!=null)store.checkpoint(tripId,trip,true,"");
                if(charge!=null)store.checkpoint(chargeId,charge,true,"");
                lastSave=now;
            }
            status=sample.watts==null&&sample.lifetimeElectricityKwh==null?"Нет показаний мощности и BMS · энергия неполная":trip!=null?"Записывается поездка":charge!=null?"Записывается зарядка":"Ожидание движения или зарядки";
            if (sample.watts == null && sample.lifetimeElectricityKwh != null) status += " · энергия BMS";
            if (trip != null) status += sample.lifetimeFuelLitres == null ? " · счётчик топлива недоступен" : trip.fuel.invalid ? " · сбой счётчика топлива" : " · топливо: авто";
        } catch(Exception error) {android.util.Log.w("BYD_STATISTICS","Journal read failed",error);status="Не удалось прочитать телеметрию";}
    }
    private long begin(String type,long now,StatisticsSample sample) {
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
        if(worker!=null) {
            worker.execute(()->{finishAll("Запись остановлена",true);store.close();});
            worker.shutdown();
        }
        status="Запись остановлена";super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
