package ru.logunov.bydsplit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared command queue survives page switches; polling exists only while a page is visible. */
final class ComfortController {
    private static ComfortController instance;
    static synchronized ComfortController get(Context context){
        if(instance==null)instance=new ComfortController(context.getApplicationContext());return instance;
    }
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ComfortRequests requests=new ComfortRequests();
    private final Set<Runnable> listeners=new LinkedHashSet<>();
    private long receivedAt,lastErrorAt;
    private String lastError="";
    private boolean reading,writing;
    private final Runnable tick=new Runnable(){public void run(){if(listeners.isEmpty())return;publish();poll();main.postDelayed(this,1500);}};
    private ComfortController(Context context){this.context=context;}
    void addListener(Runnable listener){boolean first=listeners.isEmpty();listeners.add(listener);listener.run();if(first)main.post(tick);}
    void removeListener(Runnable listener){listeners.remove(listener);if(listeners.isEmpty())main.removeCallbacks(tick);}
    boolean available(){return !AppPreferences.isDemoModeEnabled(context)&&receivedAt>0&&SystemClock.elapsedRealtime()-receivedAt<5000;}
    boolean pending(){return requests.hasPending();}
    Integer current(ComfortState.Field field){return requests.current(field);}
    int nextUiValue(ComfortState.Field field){return requests.display().nextUiValue(field);}
    void request(ComfortState.Field field,int value){
        if(!available())return;
        requests.request(field,value);publish();drain();
    }
    private void accept(ComfortState sample){requests.accept(sample);receivedAt=sample.any()?SystemClock.elapsedRealtime():0;}
    private void publish(){for(Runnable listener:new LinkedHashSet<>(listeners))listener.run();}
    private void poll(){
        if(reading||writing||requests.hasPending()||AppPreferences.isDemoModeEnabled(context))return;
        reading=true;worker.execute(()->{ComfortState sample=LocalAdbManager.get(context).readComfortPanel();main.post(()->{
            reading=false;accept(sample);publish();drain();
        });});
    }
    private void drain(){
        if(reading||writing)return;
        if(AppPreferences.isDemoModeEnabled(context)){requests.clear();publish();return;}
        ComfortRequests.Request command=requests.next();if(command==null)return;
        writing=true;
        worker.execute(()->{
            LocalAdbManager adb=LocalAdbManager.get(context);
            String error=adb.writeComfortPanel(command.field,command.value);
            ComfortState sample=adb.readComfortPanel();
            main.post(()->{
                writing=false;accept(sample);boolean latest=requests.complete(command);publish();
                if(latest&&error!=null){long now=SystemClock.elapsedRealtime();
                    if(!error.equals(lastError)||now-lastErrorAt>3000){Toast.makeText(context,error,Toast.LENGTH_SHORT).show();lastError=error;lastErrorAt=now;}}
                drain();
            });
        });
    }
}
