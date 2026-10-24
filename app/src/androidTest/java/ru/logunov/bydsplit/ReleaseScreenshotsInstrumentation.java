package ru.logunov.bydsplit;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Repeatable public screenshots on an emulator; never runs against a physical vehicle. */
public final class ReleaseScreenshotsInstrumentation extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(!android.os.Build.HARDWARE.contains("ranchu")&&!android.os.Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
            AppPreferences.get(getTargetContext()).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,true).putBoolean(DockConfiguration.MODE,true)
                    .putInt("theme_mode",2).putBoolean(MusicGlowView.ENABLED,false).commit();
            DockConfiguration.save(getTargetContext(),Arrays.asList(DockConfiguration.Item.SONG,DockConfiguration.Item.CAMERA,DockConfiguration.Item.MUSIC,
                    DockConfiguration.Item.CLIMATE,DockConfiguration.Item.SEATS,DockConfiguration.Item.DRIVER_HEAT,DockConfiguration.Item.WHEEL,
                    DockConfiguration.Item.MIRRORS,DockConfiguration.Item.SETTINGS),1);
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true).putExtra("open_climate",true));
            shot("climate-dock");
            runOnMainSync(()->call(activity,"openComfort",new Class<?>[]{boolean.class},true));shot("seats-dock");
            DynamicDockView dock=(DynamicDockView)field(activity,"dynamicDock");
            runOnMainSync(()->call(dock,"edit",new Class<?>[]{}));shot("dock-editor");
            runOnMainSync(()->dock.closeEditor(false));
            runOnMainSync(()->{AppPreferences.get(activity).edit().putBoolean(MusicGlowView.ENABLED,true).putInt(MusicGlowView.BRIGHTNESS,65).putInt(MusicGlowView.HEIGHT,80).commit();MusicGlowView.preview();});
            shot("music-waves-preview");
            final AlertDialog[] palette=new AlertDialog[1];runOnMainSync(()->palette[0]=WaveColorDialog.show(activity,()->{}));shot("wave-color");
            runOnMainSync(()->{palette[0].dismiss();AppPreferences.get(activity).edit().putBoolean(MusicGlowView.ENABLED,false).commit();call(activity,"openBatteryDetails",new Class<?>[]{});});waitForIdleSync();
            String recording;
            try(InputStream in=getContext().getAssets().open("song-bms-recording-20261020.txt")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);recording=new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
            BatteryCellReadings stored=BatteryCellReadings.parse(recording);
            if(stored.millivolts==null||stored.temperatures==null)throw new AssertionError("Recording incomplete");
            runOnMainSync(()->{
                BatteryDetailsView page=(BatteryDetailsView)field(activity,"batteryDetailsView");page.setActive(false);
                long now=SystemClock.elapsedRealtime();set(page,"cellReadings",new BatteryCellReadings(stored.millivolts,now,stored.temperatures,now));set(page,"cellsAttempted",true);
                VehicleTelemetrySnapshot v=new VehicleTelemetrySnapshot(0,null,null,null,null,null,null,null,null,3,1,null,44f,1,0,0,0,1,null,54,94,12,16,0,null,13.8f);
                call(page,"update",new Class<?>[]{BatteryDetailsSnapshot.class},new BatteryDetailsSnapshot(v,new int[]{3251,3257,Float.floatToIntBits(1065.3f),0,2,106,106,12}));
                BatteryWatchController.get().start();BatteryWatchController.get().accept(6,now);call(page,"updateImbalance",new Class<?>[]{});
                ((TextView)field(page,"status")).setText("Запись BMS с Song · 20.10.2026 · демонстрация интерфейса");
            });shot("battery-voltage-recording");
            runOnMainSync(()->{BatteryDetailsView p=(BatteryDetailsView)field(activity,"batteryDetailsView");((ScrollView)p.getChildAt(2)).scrollTo(0,Math.round(360*p.getResources().getDisplayMetrics().density));});shot("battery-temperature-recording");
            runOnMainSync(()->{BatteryWatchController.get().stop();call(activity,"openSettings",new Class<?>[]{int.class},7);});shot("adb-recovery-settings");
            result.putString("result","8 release screenshots captured; BMS charts use an explicitly labelled recording");finish(0,result);
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(1,result);}
    }
    private void shot(String name)throws Exception{
        waitForIdleSync();SystemClock.sleep(750);
        Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new IOException("Screenshot unavailable");
        File dir=new File(getTargetContext().getExternalFilesDir(null),"release-shots");dir.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new IllegalStateException(e);}}
}
