package ru.logunov.bydsplit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Poll only while the effect is visible and following the car; never write vehicle settings. */
final class AmbientLightController {
    private final LocalAdbManager adb;
    private final Consumer<AmbientLightState> listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ScheduledExecutorService worker;
    private int generation;
    AmbientLightController(Context context,Consumer<AmbientLightState> listener){
        adb=LocalAdbManager.get(context.getApplicationContext());this.listener=listener;
    }
    void setActive(boolean active){
        if(active&&worker==null){
            final int token=++generation;
            worker=Executors.newSingleThreadScheduledExecutor();
            worker.scheduleWithFixedDelay(()->{
                AmbientLightState color=adb.readAmbientLightColor();
                main.post(()->{if(generation==token&&worker!=null)listener.accept(color);});
            },0,2,TimeUnit.SECONDS);
        }else if(!active&&worker!=null){
            generation++;worker.shutdownNow();worker=null;main.removeCallbacksAndMessages(null);
        }
    }
}
