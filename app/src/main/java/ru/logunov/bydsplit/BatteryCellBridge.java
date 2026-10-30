package ru.logunov.bydsplit;

import android.content.Context;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Proxy;

/** Bounded, read-only GB subscription. Runs as shell on a separate ADB connection. */
public final class BatteryCellBridge {
    @android.annotation.SuppressLint("WrongConstant")
    public static void main(String[] args) {
        Object manager=null,listener=null;Class<?> listenerType=null;
        boolean subscribed=false;
        try {
            HelperLifetime.limit(15000);
            HelperLifetime.attach(args);
            IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"autoservice");
            int cells=ComfortCommandWriter.transact(service,5,1001,1134559240,0);
            int actual=ComfortCommandWriter.transact(service,5,1009,1132462136,0);
            int probes=ComfortCommandWriter.transact(service,5,1039,1134559248,0);
            if(cells!=106||actual!=106||probes!=12)throw new IllegalStateException("Unsupported battery format");
            Looper.prepare();
            Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
            manager=context.getSystemService("auto");
            listenerType=Class.forName("android.hardware.BYDAutoManager$OnBYDAutoListener");
            BatteryCellReadings.Decoder decoder=new BatteryCellReadings.Decoder();
            Looper loop=Looper.myLooper();
            Handler handler=new Handler(loop);
            listener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},(proxy,method,values)->{
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==values[0];
                if(method.getName().equals("toString"))return "SongBatteryReadOnly";
                if(method.getName().equals("onChanged")&&values.length==4&&values[0].equals(1039)
                        &&values[1].equals(-1728053217)&&values[2] instanceof byte[]) {
                    long now=SystemClock.elapsedRealtime();decoder.accept((byte[])values[2],now);
                    BatteryCellReadings s=decoder.snapshot();
                    if(s.cellsFresh(now)&&s.temperaturesFresh(now))handler.post(loop::quitSafely);
                }
                return null;
            });
            manager.getClass().getMethod("registerListener",listenerType).invoke(manager,listener);
            int result=(Integer)manager.getClass().getMethod("enableDevice",int.class,int[].class)
                    .invoke(manager,1039,new int[]{-1728053217});
            if(result!=0)throw new IllegalStateException("GB subscription refused: "+result);
            subscribed=true;
            handler.postDelayed(loop::quitSafely,8500);
            Looper.loop();
            BatteryCellReadings s=decoder.snapshot();long now=SystemClock.elapsedRealtime();
            if(s.cellsFresh(now)){System.out.println("CELLS="+BatteryCellReadings.csv(s.millivolts));System.out.println("CELLS_AT="+s.cellsAt);}
            if(s.temperaturesFresh(now)){System.out.println("TEMPS="+BatteryCellReadings.csv(s.temperatures));System.out.println("TEMPS_AT="+s.temperaturesAt);}
        } catch(Exception error){System.out.println("BMS_UNAVAILABLE:"+error.getClass().getSimpleName());}
        finally {
            if(manager!=null) {
                try{if(subscribed)manager.getClass().getMethod("disableDevice",int.class).invoke(manager,1039);}catch(Exception ignored){}
                try{if(listener!=null)manager.getClass().getMethod("unregisterListener",listenerType).invoke(manager,listener);}catch(Exception ignored){}
            }
        }
        HelperLifetime.exit();
    }
}
