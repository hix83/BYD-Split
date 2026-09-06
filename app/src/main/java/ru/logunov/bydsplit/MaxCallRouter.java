package ru.logunov.bydsplit;

import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing MAX tasks are moved, never recreated, so the incoming call intent survives. */
final class MaxCallRouter {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static volatile boolean notificationActive;
    private static volatile long activityUntil;
    private static Context context;
    private static boolean scheduled;
    private static long fastUntil;
    static boolean active(){return notificationActive||SystemClock.uptimeMillis()<activityUntil;}
    static void setNotificationActive(Context c,boolean on) {
        main.post(()->{context=c.getApplicationContext();boolean was=notificationActive;notificationActive=on;
            if(on&&!was)fastUntil=SystemClock.uptimeMillis()+5000;
            if(!on&&was)activityUntil=SystemClock.uptimeMillis()+2000;
            schedule();});
    }
    static void onCallActivity(Context c) {
        main.post(()->{context=c.getApplicationContext();activityUntil=SystemClock.uptimeMillis()+15000;fastUntil=SystemClock.uptimeMillis()+5000;schedule();});
    }
    private static void schedule(){if(!scheduled&&active()){scheduled=true;main.post(tick);}}
    private static final Runnable tick=new Runnable(){public void run(){
        scheduled=false;
        if(!active()||context==null)return;
        int display=MainActivity.prepareMaxCallPane();
        if(display>0&&busy.compareAndSet(false,true)) {
            Context c=context;
            worker.execute(()->{try {if(active())LocalAdbManager.forCallRouting(c).moveMaxTasksToDisplay(display);}finally{busy.set(false);}});
        }
        scheduled=true;main.postDelayed(this,SystemClock.uptimeMillis()<fastUntil?150:750);
    }};
}
