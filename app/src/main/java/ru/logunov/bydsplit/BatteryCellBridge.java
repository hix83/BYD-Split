package ru.logunov.bydsplit;

import android.content.Context;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Proxy;

/** Bounded, read-only GB subscription. Runs as shell on a separate ADB connection. */
public final class BatteryCellBridge {
    private static int read(IBinder service,int device,int fid)throws Exception {
        android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
        try {data.writeInterfaceToken(service.getInterfaceDescriptor());data.writeInt(device);data.writeInt(fid);
            if(!service.transact(5,data,reply,0))throw new IllegalStateException("Read unavailable");
            reply.readException();return reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    @android.annotation.SuppressLint("WrongConstant")
    public static void main(String[] args) {
        Object manager=null,listener=null;Class<?> listenerType=null;
        boolean subscribed=false;
        try {
            IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"autoservice");
            Looper.prepare();
            Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
            Class<?> fids=Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");
            int cells=read(service,1001,fids.getField("BODYWORK_BATTERY_SINGLE_NUM").getInt(null));
            int probes=read(service,1039,fids.getField("GB_BATTERY_PROBE_NUM").getInt(null));
            if(cells!=94||probes!=7)throw new IllegalStateException("Unsupported battery format");
            manager=context.getSystemService("auto");
            listenerType=Class.forName("android.hardware.BYDAutoManager$OnBYDAutoListener");
            BatteryCellReadings.Decoder decoder=new BatteryCellReadings.Decoder();
            Looper loop=Looper.myLooper();
            Handler handler=new Handler(loop);
            listener=Proxy.newProxyInstance(listenerType.getClassLoader(),new Class<?>[]{listenerType},(proxy,method,values)->{
                if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                if(method.getName().equals("equals"))return proxy==values[0];
                if(method.getName().equals("toString"))return "SeagullBatteryReadOnly";
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
        System.exit(0);
    }
}
