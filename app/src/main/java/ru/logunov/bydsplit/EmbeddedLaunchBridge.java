package ru.logunov.bydsplit;

import android.content.ComponentName;
import android.os.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One owner-bound launch: intercept task changes only during startup, then unregister and exit. */
public final class EmbeddedLaunchBridge {
    public static void main(String[] args){
        HelperLifetime.limit(5000);
        Object service=null,listener=null;Class<?> api=null,listenerApi=null;
        ExecutorService moves=Executors.newSingleThreadExecutor();
        AtomicBoolean closing=new AtomicBoolean();
        AtomicReference<java.lang.Process> runningCommand=new AtomicReference<>();
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            closing.set(true);moves.shutdownNow();
            java.lang.Process command=runningCommand.getAndSet(null);
            if(command!=null)command.destroyForcibly();
        },"byd-launch-cleanup"));
        try{
            HelperLifetime.attach(args);
            ComponentName component=ComponentName.unflattenFromString(args[0]);
            int display=Integer.parseInt(args[1]);
            if(component==null||display<1||display>999)throw new IllegalArgumentException("Embedded target required");
            if(Looper.myLooper()==null)Looper.prepare();
            api=Class.forName("android.app.IActivityTaskManager");
            service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            final Object tasks=service;final Class<?> taskApi=api;
            AtomicLong lastChange=new AtomicLong(SystemClock.elapsedRealtime());
            AtomicBoolean found=new AtomicBoolean();
            AtomicBoolean queued=new AtomicBoolean();
            Runnable scan=()->{
                if(closing.get())return;
                try{
                    found.set(false);
                    for(Object info:(List<?>)taskApi.getMethod("getAllRootTaskInfos").invoke(tasks)){
                        ComponentName top=(ComponentName)info.getClass().getField("topActivity").get(info);
                        if(top==null||!component.getPackageName().equals(top.getPackageName()))continue;
                        int current=info.getClass().getField("displayId").getInt(info);
                        if(current!=0&&current!=display)continue;
                        if(current!=display){
                            int task=info.getClass().getField("taskId").getInt(info);
                            taskApi.getMethod("moveRootTaskToDisplay",int.class,int.class).invoke(tasks,task,display);
                            lastChange.set(SystemClock.elapsedRealtime());
                        }
                        found.set(true);
                    }
                }catch(Exception error){android.util.Log.w("BYD_EMBED_LAUNCH","Cannot route startup task",error);}
            };
            Binder binder=new Binder(){
                @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
                    if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString("android.app.ITaskStackListener");return true;}
                    data.enforceInterface("android.app.ITaskStackListener");
                    if(!closing.get()&&queued.compareAndSet(false,true))try{moves.execute(()->{queued.set(false);scan.run();});}catch(RejectedExecutionException ignored){}
                    return true;
                }
            };
            listenerApi=Class.forName("android.app.ITaskStackListener");
            listener=Class.forName(listenerApi.getName()+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
            api.getMethod("registerTaskStackListener",listenerApi).invoke(service,listener);
            java.lang.Process command=new ProcessBuilder("/system/bin/am","start","--display",Integer.toString(display),"-f","0x00010000","-n",component.flattenToString()).redirectErrorStream(true).start();
            runningCommand.set(command);
            try{if(!command.waitFor(1500,TimeUnit.MILLISECONDS))throw new IllegalStateException("Launch command timed out");}
            finally{runningCommand.set(null);command.destroyForcibly();}
            long until=SystemClock.elapsedRealtime()+2800;
            do{Thread.sleep(50);}while(SystemClock.elapsedRealtime()<until&&(!found.get()||SystemClock.elapsedRealtime()-lastChange.get()<1300));
            Future<?> finalScan=moves.submit(scan);finalScan.get(500,TimeUnit.MILLISECONDS);
            System.out.println(found.get()?"BYD_SPLIT_LAUNCH_OK":"LAUNCH_NOT_FOUND");
        }catch(Throwable error){android.util.Log.w("BYD_EMBED_LAUNCH","Cannot launch embedded app",error);System.out.println("LAUNCH_ERROR:"+error.getClass().getSimpleName());}
        finally{
            closing.set(true);
            if(service!=null&&listener!=null)try{api.getMethod("unregisterTaskStackListener",listenerApi).invoke(service,listener);}catch(Exception ignored){}
            moves.shutdownNow();
            HelperLifetime.exit();
        }
    }
}
