package ru.logunov.bydsplit;

import android.content.Context;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.mqtt.AbsBYDAutoMqttListener;
import android.os.*;
import org.json.*;
import java.net.Socket;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Manual, owner-bound diagnostic reader. Adapted from BYDMate; PolyForm Noncommercial, see NOTICE. */
public final class DtcBridge {
    private static Socket owner;
    private static IBinder auto;
    private static synchronized void emit(JSONObject message)throws Exception {
        owner.getOutputStream().write((message+"\n").getBytes(StandardCharsets.UTF_8));owner.getOutputStream().flush();
    }
    private static int read(int device,int fid){try{return ComfortCommandWriter.transact(auto,5,device,fid,0);}catch(Exception error){return -1;}}
    private static String property(String name){try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,name);}catch(Exception error){return "";}}
    static JSONObject result(DtcScanner.Result r)throws Exception {
        return new JSONObject().put("address",r.address).put("outcome",r.outcome).put("count",DtcProtocol.hex(r.count))
                .put("list",DtcProtocol.hex(r.list)).put("identity",DtcProtocol.hex(r.identity));
    }
    public static void main(String[] args){
        try {
            owner=HelperLifetime.attach(args);
            HelperLifetime.limit(95000);
            RandomAccessFile lockFile=new RandomAccessFile("/data/local/tmp/byd-split-dtc.lock","rw");
            FileLock lock=lockFile.getChannel().tryLock();
            if(lock==null){emit(new JSONObject().put("kind","finished").put("state","UNAVAILABLE").put("reason","diag_busy"));HelperLifetime.exit();return;}
            Runtime.getRuntime().addShutdownHook(new Thread(()->{try{lock.release();lockFile.close();}catch(Exception ignored){}}));
            if(Looper.myLooper()==null)Looper.prepareMainLooper();
            Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
            auto=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"autoservice");
            // Construct the actual OEM listener on the prepared main thread, not a dynamic Proxy.
            NativeDevice device=new NativeDevice(context);
            Runtime.getRuntime().addShutdownHook(new Thread(device::close));
            new Thread(()->{
                try {
                    int model=read(1001,1087373328);
                    if(model<=0||model==255||model==65535)try{model=Integer.parseInt(property("persist.sys.AutoType"));}catch(Exception ignored){model=0;}
                    final int modelCode=model;
                    DtcScanner scanner=new DtcScanner(device,new DtcScanner.Clock(){public long now(){return SystemClock.elapsedRealtime();}public void sleep(long ms)throws Exception{Thread.sleep(ms);}},
                            ()->property("sys.diagmgrsrv.task_bk"),()->read(1011,555745336),()->read(1023,315621408)==2,legacyFirmware());
                    final int[] done={0};
                    DtcScanner.Scan scan=scanner.run(r->{try{emit(new JSONObject().put("kind","progress").put("done",++done[0]).put("result",result(r)));}catch(Exception error){throw new IllegalStateException("owner_lost");}});
                    JSONArray rows=new JSONArray();for(DtcScanner.Result r:scan.results)rows.put(result(r));
                    emit(new JSONObject().put("kind","finished").put("state",scan.state).put("reason",scan.reason).put("model",modelCode).put("results",rows).put("at",System.currentTimeMillis()));
                }catch(Throwable error){android.util.Log.e("BYD_DTC","Scan failed",error);try{emit(new JSONObject().put("kind","finished").put("state","UNAVAILABLE").put("reason",error.getClass().getSimpleName()));}catch(Exception ignored){}}
                finally {HelperLifetime.exit();}
            },"byd-dtc-scan").start();
            Looper.loop();
        }catch(Throwable error){android.util.Log.e("BYD_DTC","Startup failed",error);try{if(owner!=null)emit(new JSONObject().put("kind","finished").put("state","UNAVAILABLE").put("reason",error.getClass().getSimpleName()));}catch(Exception ignored){}}
        HelperLifetime.exit();
    }
    /** Verified Song L DM-i legacy gateway; never enable guardless scans on arbitrary models. */
    private static boolean legacyFirmware(){
        return "161".equals(property("persist.sys.AutoType"))&&"DiLink5.0".equals(property("ro.product.device"));
    }
    private static final class NativeDevice implements DtcScanner.Device {
        private static final int RX=0x9900010a,TX=0xaa00010a;
        private final LinkedBlockingQueue<byte[]> frames=new LinkedBlockingQueue<>(128);
        private final Object device;
        private boolean registered;
        private final AbsBYDAutoMqttListener listener=new AbsBYDAutoMqttListener(){
            @Override public void onDataEventChanged(int type,BYDAutoEventValue value){
                if(type==RX&&value!=null&&value.bufferDataValue!=null&&value.bufferDataValue.length<=1024)frames.offer(value.bufferDataValue.clone());
            }
            @Override public void onError(int code,String message){android.util.Log.w("BYD_DTC","Listener error "+code);}
        };
        NativeDevice(Context context)throws Exception {
            device=Class.forName("android.hardware.bydauto.mqtt.BYDAutoMqttDevice").getMethod("getInstance",Context.class).invoke(null,context);
        }
        private Method method(String name,int count)throws Exception {
            for(Method m:device.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterCount()==count&&m.getParameterTypes()[0].isInstance(listener))return m;
            throw new NoSuchMethodException(name);
        }
        @Override public synchronized void register()throws Exception{registered=true;method("registerListener",2).invoke(device,listener,new int[]{RX});}
        @Override public int send(byte[] payload)throws Exception {
            BYDAutoEventValue value=new BYDAutoEventValue();value.bufferDataValue=payload;
            return (Integer)device.getClass().getMethod("set",int[].class,BYDAutoEventValue.class).invoke(device,new int[]{TX},value);
        }
        @Override public byte[] poll(long ms)throws Exception{return frames.poll(Math.max(1,ms),TimeUnit.MILLISECONDS);}
        @Override public void drain(){frames.clear();}
        @Override public synchronized void close(){if(registered){registered=false;try{method("unregisterListener",1).invoke(device,listener);}catch(Exception ignored){}}frames.clear();}
    }
}
