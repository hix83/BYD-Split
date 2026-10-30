package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Emulator-only cold startup regression: the real Music internal entry screen must stay embedded. */
public final class ColdMusicLaunchInstrumentation extends Instrumentation {
    public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
             InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] data=new byte[8192]; int n; while((n=input.read(data))>=0)out.write(data,0,n); return out.toString("UTF-8");
        }
    }
    private Object field(Object obj,String name) throws Exception {
        Field f=obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj);
    }
    public void onStart() {
        Bundle result=new Bundle();
        try {
            if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish")) throw new IllegalStateException("Emulator required");
            String pkg="ru.yandex.music";
            AppEntry app=null;
            for(AppEntry entry:new AppRepository(getTargetContext()).loadLaunchableApps())
                if(entry.component.getPackageName().equals(pkg)) { app=entry; break; }
            if(app==null) throw new AssertionError("Yandex Music not installed");
            shell("am force-stop "+pkg);
            AppPreferences.get(getTargetContext()).edit()
                .remove(AppPreferences.KEY_DRIVER_APP).remove(AppPreferences.KEY_DRIVER_APPS)
                .putString(AppPreferences.KEY_COMPACT_TARGET,AppPreferences.COMPACT_TARGET_VEHICLE)
                .remove(AppPreferences.KEY_FAR_APP)
                .remove(AppPreferences.KEY_FAR_APPS)
                .putInt(AppPreferences.KEY_FAR_APP_INDEX,0).commit();
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));
            SystemClock.sleep(1200);
            AtomicInteger stops=new AtomicInteger();
            Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks() {
                public void onActivityStopped(Activity a){if(a==activity)stops.incrementAndGet();}
                public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
                public void onActivityResumed(Activity a){} public void onActivityPaused(Activity a){}
                public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
            };
            activity.getApplication().registerActivityLifecycleCallbacks(observer);
            final AppEntry selected=app;
            runOnMainSync(()->{try{Method m=MainActivity.class.getDeclaredMethod("activateQuickApp",AppEntry.class);m.setAccessible(true);m.invoke(activity,selected);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(4000);
            EmbeddedAppPane pane=(EmbeddedAppPane)field(activity,"driverEmbeddedPane");
            int display=pane.embeddedDisplayId();
            if(display<1)throw new AssertionError("No compact display");
            assertOnDisplay(pkg,display);
            if(stops.get()!=0)throw new AssertionError("Cold music covered Split full-screen");
            String pid=shell("pidof "+pkg).trim();
            shell("screencap -p /sdcard/song-cold-music.png");
            for(int i=0;i<2;i++) {
                if(!LocalAdbManager.get(activity).launchOnDisplay(app.component.flattenToString(),pkg,display))throw new AssertionError("Repeated launch failed");
                SystemClock.sleep(500);
                assertOnDisplay(pkg,display);
            }
            if(stops.get()!=0)throw new AssertionError("Repeated launch covered Split full-screen");
            if(!pid.equals(shell("pidof "+pkg).trim()))throw new AssertionError("Playback process restarted");
            activity.getApplication().unregisterActivityLifecycleCallbacks(observer);
            result.putString("result","PASS: stopped Yandex Music cold launch and internal entry screen stay embedded, no full-screen Split stop, repeated launches retain process");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private void assertOnDisplay(String pkg,int display)throws Exception{
        boolean target=false,found=false;
        for(String line:shell("am stack list").split("\n")){
            if(line.startsWith("RootTask id="))target=line.contains("displayId="+display+" ");
            if(target&&line.trim().startsWith("taskId=")&&line.contains(pkg+"/"))found=true;
        }
        if(!found)throw new AssertionError("Music not on display "+display);
    }
}
