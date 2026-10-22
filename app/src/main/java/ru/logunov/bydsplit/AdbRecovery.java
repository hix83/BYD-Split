package ru.logunov.bydsplit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.*;
import android.net.nsd.*;
import android.os.Build;
import android.provider.Settings;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Opt-in local ADB recovery using Android wireless debugging. BYDMate provenance: NOTICE. */
final class AdbRecovery {
    static final String ENABLED="adb_restore_enabled";
    private static final String WIFI="adb_wifi_enabled", OWNED="adb_restore_owns_wifi";
    private static AdbRecovery instance;
    static synchronized AdbRecovery get(Context context){if(instance==null)instance=new AdbRecovery(context.getApplicationContext());return instance;}
    private final Context context;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final AtomicBoolean queued=new AtomicBoolean();
    private final Object settingLock=new Object();
    private volatile String status="Выключено";
    private boolean started;
    private volatile long lastEnable;
    private AdbRecovery(Context context){this.context=context;}
    boolean enabled(){return AppPreferences.get(context).getBoolean(ENABLED,false);}
    static boolean supported(){return Build.VERSION.SDK_INT>=30;}
    String status(){return !supported()?"Нужен Android 11 или новее":AppPreferences.isDemoModeEnabled(context)?"Демо: восстановление ADB не выполняется":!enabled()?"Выключено":status;}
    synchronized void start(){
        if(started)return;started=true;
        worker.scheduleWithFixedDelay(()->attempt(false),0,20,TimeUnit.SECONDS);
    }
    void setEnabled(boolean value){
        synchronized(settingLock){
            generation.incrementAndGet();AppPreferences.get(context).edit().putBoolean(ENABLED,value).apply();
            if(!value){restoreOwnedSetting();status="Выключено";}
        }
        start();if(value)retry();
    }
    void retry(){
        start();if(!queued.compareAndSet(false,true))return;
        worker.execute(()->{try{attempt(true);}finally{queued.set(false);}});
    }
    private boolean current(int gen){return enabled()&&generation.get()==gen&&!AppPreferences.isDemoModeEnabled(context);}
    private boolean permission(){return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    private boolean wifi(){
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
        if(cm==null)return false;
        for(Network network:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(network);if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return true;}
        return false;
    }
    private LocalAdbClient client(int port,boolean tls){return new LocalAdbClient(new LocalAdbKeyStore(context),port,tls,3500);}
    private boolean classic(boolean prepare,int gen){
        try(LocalAdbClient client=client(5555,false)){
            if(!client.shell("echo BYD_SPLIT_ADB_OK").trim().equals("BYD_SPLIT_ADB_OK"))return false;
            if(prepare&&current(gen)&&!permission())try{client.shell("pm grant "+context.getPackageName()+" android.permission.WRITE_SECURE_SETTINGS");}catch(Exception ignored){/* A refused grant does not mean ADB itself is unavailable. */}
            return true;
        }catch(Exception error){return false;}
    }
    private void attempt(boolean force){
        int gen=generation.get();
        if(!supported()||!current(gen))return;
        try{
            status="Проверяем ADB…";
            if(classic(true,gen)){if(current(gen))status=permission()?"ADB доступен · восстановление подготовлено":"ADB доступен · не удалось получить системное разрешение";return;}
            if(!current(gen))return;
            if(!permission()){status="Нужна активация: один раз подключите обычный ADB и нажмите «Повторить»";return;}
            if(!wifi()){status="Ожидание подключения к Wi-Fi";return;}
            synchronized(settingLock){
                if(!current(gen))return;
                if(Settings.Global.getInt(context.getContentResolver(),WIFI,0)!=1){
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(!force&&lastEnable>0&&now-lastEnable<600000){status="Подтвердите беспроводную отладку на экране и нажмите «Повторить»";return;}
                    lastEnable=now;
                    // Remember ownership before the write, so a process restart can still undo it.
                    AppPreferences.get(context).edit().putBoolean(OWNED,true).commit();
                    boolean written;
                    try{written=Settings.Global.putInt(context.getContentResolver(),WIFI,1);}
                    catch(RuntimeException error){AppPreferences.get(context).edit().remove(OWNED).apply();throw error;}
                    if(!written){AppPreferences.get(context).edit().remove(OWNED).apply();status="Система не разрешила включить беспроводную отладку";return;}
                }
            }
            Thread.sleep(1500);
            if(!current(gen))return;
            if(Settings.Global.getInt(context.getContentResolver(),WIFI,0)!=1){status="Разрешите отладку по Wi-Fi в системном окне, затем нажмите «Повторить»";return;}
            status="Ищем локальный порт беспроводной отладки…";
            int port=propertyPort();
            if(port>0&&restorePort(port,gen))return;
            if(!current(gen))return;
            port=discover(gen);
            if(!current(gen))return;
            if(port>0&&restorePort(port,gen))return;
            if(!current(gen))return;
            if(classic(false,gen))restored(gen);
            else status="ADB не восстановлен: проверьте разрешение отладки и ранее одобренный ключ";
        }catch(InterruptedException error){Thread.currentThread().interrupt();if(current(gen))status="Попытка прервана";}
        catch(Exception error){if(current(gen))status="Ошибка восстановления: "+error.getClass().getSimpleName();}
    }
    private boolean restorePort(int port,int gen)throws InterruptedException{
        if(!current(gen))return false;
        status="Восстанавливаем порт ADB 5555…";
        try(LocalAdbClient tls=client(port,true)){
            tls.connect();
            if(!current(gen))return false;
            try{tls.restartTcpip();}catch(Exception ignored){/* adbd can restart before sending its reply */}
        }catch(Exception ignored){/* Only a successful classic shell proves recovery. */}
        for(int i=0;i<10;i++){
            if(!current(gen))return false;
            Thread.sleep(1000);
            if(classic(false,gen)){restored(gen);return true;}
        }
        return false;
    }
    private void restored(int gen){
        if(!current(gen))return;
        status="ADB восстановлен · запускаем помощники…";
        boolean helpers=LocalAdbManager.get(context).restartHelpersAfterRecovery();
        if(current(gen))status=helpers?"ADB восстановлен · помощники запущены":"ADB восстановлен · запуск помощников не подтверждён";
    }
    private int propertyPort(){
        try{Object value=Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"service.adb.tls.port");int port=Integer.parseInt(String.valueOf(value));return port>0&&port<=65535?port:-1;}catch(Exception ignored){return -1;}
    }
    private int discover(int gen)throws InterruptedException{
        NsdManager nsd=context.getSystemService(NsdManager.class);if(nsd==null)return -1;
        CountDownLatch done=new CountDownLatch(1);AtomicInteger port=new AtomicInteger(-1);AtomicBoolean resolving=new AtomicBoolean(),stopped=new AtomicBoolean();
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            private final ArrayDeque<NsdServiceInfo> pending=new ArrayDeque<>();
            public void onDiscoveryStarted(String type){}
            public void onDiscoveryStopped(String type){}
            public void onStartDiscoveryFailed(String type,int error){done.countDown();}
            public void onStopDiscoveryFailed(String type,int error){}
            public void onServiceLost(NsdServiceInfo info){}
            public void onServiceFound(NsdServiceInfo info){
                synchronized(pending){if(pending.size()<32)pending.add(info);}
                resolveNext();
            }
            private void resolveNext(){
                if(stopped.get()||!current(gen)||!resolving.compareAndSet(false,true))return;
                NsdServiceInfo next;synchronized(pending){next=pending.poll();}
                if(next==null){resolving.set(false);return;}
                try{nsd.resolveService(next,new NsdManager.ResolveListener(){
                    public void onResolveFailed(NsdServiceInfo service,int error){resolving.set(false);resolveNext();}
                    public void onServiceResolved(NsdServiceInfo service){
                        resolving.set(false);
                        if(!stopped.get()&&current(gen)&&localAddress(service.getHost())&&service.getPort()>0&&service.getPort()<=65535){port.set(service.getPort());done.countDown();}
                        else resolveNext();
                    }
                });}catch(RuntimeException error){resolving.set(false);resolveNext();}
            }
        };
        try{
            nsd.discoverServices("_adb-tls-connect._tcp",NsdManager.PROTOCOL_DNS_SD,listener);
            for(int i=0;i<40&&current(gen);i++)if(done.await(500,TimeUnit.MILLISECONDS))break;
            return port.get();
        }catch(RuntimeException error){return -1;}
        finally{stopped.set(true);try{nsd.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}}
    }
    static boolean localAddress(InetAddress address){
        if(address==null)return false;if(address.isLoopbackAddress())return true;
        try{Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){Enumeration<InetAddress> addresses=interfaces.nextElement().getInetAddresses();while(addresses.hasMoreElements())if(address.equals(addresses.nextElement()))return true;}}catch(SocketException ignored){}
        return false;
    }
    private void restoreOwnedSetting(){
        if(!AppPreferences.get(context).getBoolean(OWNED,false))return;
        if(permission())try{
            if(Settings.Global.getInt(context.getContentResolver(),WIFI,0)==1&&!Settings.Global.putInt(context.getContentResolver(),WIFI,0))return;
            AppPreferences.get(context).edit().remove(OWNED).apply();
        }catch(RuntimeException ignored){}
    }
}
