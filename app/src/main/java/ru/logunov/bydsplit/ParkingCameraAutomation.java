package ru.logunov.bydsplit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.*;
import java.util.concurrent.*;

/** Live rules run independently of dashboard visibility and GPS update frequency. */
final class ParkingCameraAutomation implements LocationListener {
    private final Context context;
    private final LocationManager locationManager;
    private final TemperatureRules engine = new TemperatureRules();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService worker;
    private volatile Location location;
    private boolean gps;
    private static final String[] ACTIONS={"Руль","Подогрев водителя","Подогрев пассажира","Вентиляция водителя","Вентиляция пассажира","Камера"};
    ParkingCameraAutomation(Context context) {
        this.context=context.getApplicationContext();
        locationManager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        android.content.SharedPreferences saved=this.context.getSharedPreferences("automation_pending_off",0);
        for(int action=0;action<5;action++) {
            long deadline=saved.getLong("off_"+action,0);
            if(deadline>0)engine.restoreOff(action,SystemClock.elapsedRealtime()+Math.max(0,deadline-System.currentTimeMillis()));
        }
    }
    void start() {
        if(AppPreferences.isDemoModeEnabled(context)) { stop(); return; }
        if(worker==null) {
            worker=Executors.newSingleThreadScheduledExecutor();
            worker.scheduleWithFixedDelay(this::tick,0,3,TimeUnit.SECONDS);
        }
        if(!gps && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,3000L,5f,this);gps=true; }
            catch(RuntimeException ignored) { }
        }
    }
    void stop() {
        if(gps)locationManager.removeUpdates(this);
        gps=false;
        if(worker!=null)worker.shutdownNow();
        worker=null;
    }
    private void tick() {
        try {
            if(AppPreferences.isDemoModeEnabled(context))return;
            List<TemperatureRules.Rule> rules=AutomationStore.load(context);
            Float[] temperatures=LocalAdbManager.get(context).readAutomationTemperatures();
            Map<String,Boolean> zones=new HashMap<>();
            Location fix=location;
            if(fix!=null && SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos()<30_000_000_000L) {
                for(CameraPlace place:AppPreferences.getCameraPlaces(context)) {
                    float[] distance=new float[1];
                    Location.distanceBetween(fix.getLatitude(),fix.getLongitude(),place.latitude,place.longitude,distance);
                    // Uncertain fixes near the boundary cannot manufacture an exit/re-entry.
                    float accuracy=fix.hasAccuracy()?fix.getAccuracy():0;
                    if(distance[0]+accuracy<=place.radiusMeters)zones.put(AutomationStore.key(place),true);
                    else if(distance[0]-accuracy>place.radiusMeters+10)zones.put(AutomationStore.key(place),false);
                }
            }
            for(String rule:engine.evaluate(rules,temperatures[0],temperatures[1],zones,SystemClock.elapsedRealtime(),(action,level)->{
                if(action==5) { main.post(()->ParkingCameraOverlay.show(context));AutomationJournal.add("Камера · открытие запрошено");return; }
                String error=LocalAdbManager.get(context).applyComfortRule(action,level);
                AutomationJournal.add(ACTIONS[action]+" · "+(error==null?"подтверждено, уровень "+level:error));
                if(error!=null)throw new IllegalStateException(error);
            })) AutomationJournal.add("Проверено правило · "+rule);
        } catch(Exception error) { android.util.Log.w("BYD_RULES","Rule evaluation failed",error); }
        finally {
            android.content.SharedPreferences.Editor saved=context.getSharedPreferences("automation_pending_off",0).edit().clear();
            for(Map.Entry<Integer,Long> off:engine.pendingOffs().entrySet())
                saved.putLong("off_"+off.getKey(),System.currentTimeMillis()+Math.max(0,off.getValue()-SystemClock.elapsedRealtime()));
            saved.commit();
        }
    }
    @Override public void onLocationChanged(Location value){location=new Location(value);}
    @Override public void onStatusChanged(String provider,int status,Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider){location=null;}
}
