package ru.logunov.bydsplit;

import android.content.Context;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** One shared read-only poll for the visible widget and status page. No BMS subscriptions. */
final class EvRangeController {
    private static EvRangeController instance;
    static synchronized EvRangeController get(Context c){if(instance==null)instance=new EvRangeController(c.getApplicationContext());return instance;}
    static final String CAPACITY="ev_range_capacity", RESERVE="ev_range_reserve";
    static final String SHOW_STATUS="ev_range_show_status";
    static boolean showStatus(Context c){return AppPreferences.get(c).getBoolean(SHOW_STATUS,true);}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Set<Runnable> listeners=new LinkedHashSet<>();
    private final VehicleSleepController sleep;
    private ScheduledFuture<?> task;
    private volatile int generation;
    private final Runnable clock=new Runnable(){public void run(){if(listeners.isEmpty())return;notifyChanged();main.postDelayed(this,1000);}};
    private long at;
    private Float soc;
    private Integer ev,fuel;
    private Integer fuelPercent,inside,batteryTemp;
    private Float voltage;
    private double[] trip;
    private EvConsumption.Average learned=new EvConsumption.Average(0,0,0);
    private EvConsumption.Average nativeHistory=new EvConsumption.Average(0,0,0);
    private EvRangeController(Context c){context=c;sleep=VehicleSleepController.get(c);}
    void add(Runnable listener){boolean first=listeners.isEmpty();listeners.add(listener);if(first)sleep.addListener(this,this::sync);listener.run();}
    void remove(Runnable listener){listeners.remove(listener);if(listeners.isEmpty()){sleep.removeListener(this);main.removeCallbacks(clock);cancel();}}
    private void cancel(){generation++;if(task!=null){task.cancel(true);task=null;}soc=null;ev=null;fuel=null;trip=null;fuelPercent=null;inside=null;batteryTemp=null;voltage=null;at=0;}
    private void sync(){
        main.removeCallbacks(clock);cancel();notifyChanged();
        if(listeners.isEmpty()||!sleep.pollingAllowed()&&!AppPreferences.isDemoModeEnabled(context))return;
        main.post(clock);
        int token=generation;
        task=worker.scheduleWithFixedDelay(()->{
            if(token!=generation)return;
            boolean demo=AppPreferences.isDemoModeEnabled(context);
            int[] raw=demo?new int[]{Float.floatToIntBits(68f),72,520,65,63,65,Float.floatToIntBits(12.6f),23}:LocalAdbManager.get(context).readEvRange(()->token==generation&&sleep.pollingAllowed());
            double[] localTrip=null;
            if(demo)localTrip=new double[]{18,12.4};
            else if(token==generation&&StatisticsService.latest!=null&&System.currentTimeMillis()-StatisticsService.lastSampleAt<10000)
                try(StatisticsStore store=new StatisticsStore(context)){localTrip=store.widgetTrip(System.currentTimeMillis());}catch(RuntimeException ignored){}
            final double[] tripValue=localTrip;
            EvConsumption.Average average=new EvConsumption.Average(0,0,0);
            EvConsumption.Average baseline=new EvConsumption.Average(0,0,0);
            if(demo)average=new EvConsumption.Average(20,3.26,2);
            else if(token==generation)try(StatisticsStore store=new StatisticsStore(context)){average=store.evConsumption(System.currentTimeMillis());baseline=store.nativeEvConsumption(System.currentTimeMillis());}catch(RuntimeException ignored){}
            final EvConsumption.Average learnedValue=average,baselineValue=baseline;
            main.post(()->{if(token!=generation)return;
                learned=learnedValue;nativeHistory=baselineValue;
                soc=raw==null?null:EvRangeMath.soc(raw[0]);
                ev=raw==null?null:EvRangeMath.nativeKm(raw[1]);fuel=raw==null?null:EvRangeMath.nativeKm(raw[2]);
                fuelPercent=raw!=null&&raw[3]>=0&&raw[3]<=100?raw[3]:null;
                batteryTemp=raw!=null&&raw[4]>=0&&raw[4]<=165&&raw[5]>=0&&raw[5]<=165?Math.round((raw[4]+raw[5])/2f)-40:null;
                float v=raw==null?Float.NaN:Float.intBitsToFloat(raw[6]);voltage=Float.isFinite(v)&&v>=5&&v<=20?v:null;
                inside=raw!=null&&raw[7]>=-50&&raw[7]<=80?raw[7]:null;trip=tripValue;
                at=SystemClock.elapsedRealtime();notifyChanged();});
        },0,3,TimeUnit.SECONDS);
    }
    boolean fresh(){return at!=0&&SystemClock.elapsedRealtime()-at<10000;}
    Float soc(){return fresh()?soc:null;}
    Integer ev(){return fresh()?ev:null;}
    Integer fuel(){return fresh()?fuel:null;}
    double setting(String key,float fallback){return AppPreferences.get(context).getFloat(key,fallback);}
    double capacity(){return setting(CAPACITY,AppPreferences.isDemoModeEnabled(context)?18.3f:0);}
    EvConsumption.Estimate estimate(){return EvConsumption.blend(learned,nativeHistory);}
    Double consumption(){return estimate().per100;}
    double reserve(){return setting(RESERVE,25);}
    Double forecast(){Double average=consumption();return average==null?null:EvRangeMath.forecast(soc(),capacity(),reserve(),average);}
    Double energy(){return EvRangeMath.remaining(soc(),capacity(),reserve());}
    void notifyChanged(){for(Runnable listener:new ArrayList<>(listeners))listener.run();}
    String calculatedText(){Double km=forecast();return km==null?"EV — км":String.format(java.util.Locale.ROOT,"EV ≈ %.0f км",km);}
    String socText(){return soc()==null?"SOC —%":String.format(java.util.Locale.ROOT,"SOC %.0f%%",soc());}
    String learningText(){
        EvConsumption.Estimate value=estimate();
        String source=value.ownShare>=1?"собственные замеры":value.ownShare<=0?"история BYD":String.format(java.util.Locale.ROOT,"свои %.0f%% · история BYD %.0f%%",value.ownShare*100,(1-value.ownShare)*100);
        return value.per100==null?String.format(java.util.Locale.ROOT,"Обучение EV: свои %.1f / 5 км · нет подходящей истории BYD",learned.km):String.format(java.util.Locale.ROOT,"Расход EV: %.1f кВт·ч/100 км · %s\nСобственные участки: %.1f км · история BYD: %.1f км",value.per100,source,learned.km,nativeHistory.km);
    }
    String tripText(){return fresh()&&trip!=null?String.format(java.util.Locale.ROOT,"%.0f мин · %.1f км",trip[0],trip[1]):"Поездка — мин · — км";}
    String tankText(){return "Бак "+(fresh()&&fuelPercent!=null?fuelPercent:"—")+"%";}
    String climateText(){return "Салон "+(fresh()&&inside!=null?inside:"—")+"° · Батарея "+(fresh()&&batteryTemp!=null?batteryTemp:"—")+"° · "+(fresh()&&voltage!=null?String.format(java.util.Locale.ROOT,"%.1f",voltage):"—")+" В";}
    String shortClimateText(){return climateText().replace("Батарея","АКБ");}
}
