package ru.logunov.bydsplit;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.LinkedHashSet;
import java.util.Set;

/** Service is the only writer; all visible pages observe the same main-thread state. */
final class BatteryWatchController {
    private static final BatteryWatchController INSTANCE=new BatteryWatchController();
    static BatteryWatchController get(){return INSTANCE;}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BatteryWatchState state=new BatteryWatchState();
    private final Set<Runnable> listeners=new LinkedHashSet<>();
    private volatile BatteryWatchState.Snapshot current=state.snapshot(SystemClock.elapsedRealtime());
    private final Runnable tick=new Runnable(){public void run(){refresh();if(current.running)main.postDelayed(this,1000);}};
    BatteryWatchState.Snapshot snapshot(){return current;}
    void start(){main.removeCallbacks(tick);state.start();main.post(tick);}
    void stop(){main.removeCallbacks(tick);state.stop();refresh();}
    void accept(Integer delta,long at){state.accept(delta,at);refresh();}
    void dismiss(){state.dismiss(SystemClock.elapsedRealtime());refresh();}
    void addListener(Runnable listener){listeners.add(listener);listener.run();}
    void removeListener(Runnable listener){listeners.remove(listener);}
    private void refresh(){current=state.snapshot(SystemClock.elapsedRealtime());for(Runnable listener:new LinkedHashSet<>(listeners))listener.run();}
}
