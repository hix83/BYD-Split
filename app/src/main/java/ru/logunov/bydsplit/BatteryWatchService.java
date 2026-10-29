package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.*;

/** Independent foreground observer; never depends on trip journaling or a visible activity. */
public final class BatteryWatchService extends Service {
    private static final String CHANNEL="battery_watch";
    private static final int NOTIFICATION=42;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ScheduledExecutorService worker;
    private ScheduledFuture<?> polling;
    private VehicleSleepController sleep;
    private boolean stopped;
    private volatile long samples,lastSampleAt;
    private String notificationText="";
    private final Runnable notificationListener=this::updateNotification;
    static void sync(Context context){
        Intent intent=new Intent(context,BatteryWatchService.class);
        if(AppPreferences.isDemoModeEnabled(context))context.stopService(intent);
        else context.startForegroundService(intent);
    }
    @Override public void onCreate(){
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Наблюдение за батареей",NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION,notification("Ожидание показаний BMS"));
        BatteryWatchController controller=BatteryWatchController.get();controller.start();controller.addListener(notificationListener);
        worker=Executors.newSingleThreadScheduledExecutor();
        sleep=VehicleSleepController.get(this);
        sleep.addListener(this,this::syncPolling);
    }
    private void syncPolling(){
        if(!sleep.pollingAllowed()){
            if(polling!=null)polling.cancel(false);polling=null;
            BatteryWatchController.get().stop();
            getSystemService(NotificationManager.class).notify(NOTIFICATION,notification("Машина спит · наблюдение приостановлено"));
            return;
        }
        if(polling!=null&&!polling.isCancelled())return;
        BatteryWatchController.get().start();
        polling=worker.scheduleWithFixedDelay(()->{
            if(!sleep.pollingAllowed())return;
            if(AppPreferences.isDemoModeEnabled(this)){main.post(this::stopSelf);return;}
            Integer delta=LocalAdbManager.forBatteryWatch(this).readCellVoltageDelta();
            long at=SystemClock.elapsedRealtime();samples++;lastSampleAt=at;
            main.post(()->{if(!stopped&&sleep.pollingAllowed()&&!AppPreferences.isDemoModeEnabled(this))BatteryWatchController.get().accept(delta,at);});
        },0,3,TimeUnit.SECONDS);
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,42,new Intent(this,MainActivity.class).putExtra("open_battery",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ui7_tech).setContentTitle("BYD Split · наблюдение за батареей")
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void updateNotification(){String text=BatteryWatchController.get().snapshot().message();if(text.equals(notificationText))return;notificationText=text;getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(text));}
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_STICKY;}
    @Override public void onDestroy(){
        if(sleep!=null)sleep.removeListener(this);
        stopped=true;if(worker!=null)worker.shutdownNow();
        BatteryWatchController controller=BatteryWatchController.get();controller.removeListener(notificationListener);controller.stop();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override protected void dump(FileDescriptor fd,PrintWriter writer,String[] args){
        BatteryWatchState.Snapshot s=BatteryWatchController.get().snapshot();
        writer.println("Battery watch: running="+s.running+" samples="+samples+" level="+s.level+" deltaMv="+s.delta
                +" observedSeconds="+s.seconds+" lastReadAgeMs="+(lastSampleAt==0?-1:SystemClock.elapsedRealtime()-lastSampleAt));
        if(sleep!=null)writer.println("Vehicle sleep: "+sleep.describe());
    }
}
