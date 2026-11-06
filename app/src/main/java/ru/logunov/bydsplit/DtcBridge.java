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
        JSONArray snapshots=new JSONArray();for(DtcScanner.Snapshot snap:r.snapshots)snapshots.put(new JSONObject().put("code",snap.code).put("outcome",snap.outcome).put("uds",DtcProtocol.hex(snap.uds)));
        return new JSONObject().put("name",DtcProtocol.hex(r.name)).put("nameOutcome",r.nameOutcome).put("snapshots",snapshots).put("address",r.address).put("outcome",r.outcome).put("count",DtcProtocol.hex(r.count))
                .put("list",DtcProtocol.hex(r.list)).put("identity",DtcProtocol.hex(r.identity));
    }
    static JSONObject eraseResult(DtcEraseEngine.Result r)throws Exception {
        JSONObject value=new JSONObject().put("address",r.address).put("confirm",r.confirm).put("reason",r.reason).put("verdict",DtcEraseEngine.verdict(r));
        if(r.nrc!=null)value.put("nrc",r.nrc);
        if(r.reread!=null)value.put("reread",result(r.reread)).put("verified",DtcEraseEngine.verified(r.reread));
        return value;
    }
    public static void main(String[] args){
        try {
            owner=HelperLifetime.attach(args);
            final boolean erase=args.length==5&&"--erase".equals(args[0]);
            if(!erase&&args.length!=3)throw new IllegalArgumentException("Invalid diagnostic operation");
            HelperLifetime.limit(erase?125000:95000);
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
                    if(erase){
                        String[] parts=args[1].split(",",-1);int[] addresses=new int[parts.length];
                        for(int i=0;i<parts.length;i++)addresses[i]=Integer.parseInt(parts[i],16);
                        DtcEraseEngine engine=new DtcEraseEngine(device,new DtcScanner.Clock(){public long now(){return SystemClock.elapsedRealtime();}public void sleep(long ms)throws Exception{Thread.sleep(ms);}},
                                ()->property("sys.diagmgrsrv.task_bk"),()->read(1011,555745336),()->read(1023,315621408)==2,
                                ()->{try{return Float.intBitsToFloat(ComfortCommandWriter.transact(auto,7,1013,-1807745016,0));}catch(Exception e){return Double.NaN;}},legacyFirmware());
                        final int[] progress={0};
                        DtcEraseEngine.Run run=engine.run(addresses,r->{try{emit(new JSONObject().put("kind","progress").put("mode","erase").put("done",++progress[0]).put("total",addresses.length*2).put("phase",r.reread==null?"erase":"reread"));}catch(Exception e){throw new IllegalStateException("owner_lost");}});
                        JSONArray cleared=new JSONArray();for(DtcEraseEngine.Result r:run.results)cleared.put(eraseResult(r));
                        emit(new JSONObject().put("kind","finished").put("mode","erase").put("state",run.state).put("reason",run.reason).put("model",modelCode).put("erase",cleared).put("at",System.currentTimeMillis()));
                        return;
                    }
                    DtcScanner scanner=new DtcScanner(device,new DtcScanner.Clock(){public long now(){return SystemClock.elapsedRealtime();}public void sleep(long ms)throws Exception{Thread.sleep(ms);}},
                            ()->property("sys.diagmgrsrv.task_bk"),()->read(1011,555745336),()->read(1023,315621408)==2,legacyFirmware());
                    final int[] done={0};
                    DtcScanner.Scan scan=scanner.run(r->{try{emit(new JSONObject().put("kind","progress").put("done",++done[0]).put("phase",done[0]==18?"details":"scan").put("result",result(r)));}catch(Exception error){throw new IllegalStateException("owner_lost");}});
                    JSONArray rows=new JSONArray();for(DtcScanner.Result r:scan.results)rows.put(result(r));
                    emit(new JSONObject().put("kind","finished").put("state",scan.state).put("reason",scan.reason).put("model",modelCode).put("results",rows).put("extrasReason",scan.extrasReason).put("at",System.currentTimeMillis()));
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
