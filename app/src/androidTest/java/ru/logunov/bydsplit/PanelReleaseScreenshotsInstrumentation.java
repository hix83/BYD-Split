package ru.logunov.bydsplit;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.Arrays;

/** Public demo captures for Song 0.9.4; refuses physical hardware. */
public final class PanelReleaseScreenshotsInstrumentation extends Instrumentation {
    private Activity activity;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
        AppPreferences.get(getTargetContext()).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,true)
                .putBoolean(DockConfiguration.MODE,true).putInt(DockConfiguration.HEIGHT_PERCENT,100)
                .putInt("theme_mode",2).putBoolean(MusicGlowView.ENABLED,false)
                .putBoolean(MusicGlowView.AMBIENT,false).putInt(MusicGlowView.COLOR,0).commit();
        DockConfiguration.save(getTargetContext(),Arrays.asList(DockConfiguration.Item.SONG,DockConfiguration.Item.MUSIC,
                DockConfiguration.Item.CLIMATE,DockConfiguration.Item.SEATS,DockConfiguration.Item.DRIVER_HEAT,
                DockConfiguration.Item.WHEEL,DockConfiguration.Item.MIRRORS,DockConfiguration.Item.SETTINGS),1);
        activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));
        SystemClock.sleep(2500);
        for(int percent:new int[]{80,150}){
            runOnMainSync(()->{AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT,percent).commit();MainActivity.applyDockSizeFromSettings();});
            shot("dock-height-"+percent);
        }
        runOnMainSync(()->{
            AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT,110)
                    .putBoolean(MusicGlowView.ENABLED,true).putInt(MusicGlowView.BRIGHTNESS,85).putInt(MusicGlowView.HEIGHT,80).commit();
            MainActivity.applyDockSizeFromSettings();
        });
        String[] effects={"waves","pixels","equalizer","oscilloscope","spectrum","glow"};
        for(int i=0;i<effects.length;i++){
            final int selected=i;
            runOnMainSync(()->{AppPreferences.get(activity).edit().putInt(MusicGlowView.EFFECT,selected).commit();MusicGlowView.preview();});
            shot("effect-"+effects[i]);
        }
        runOnMainSync(()->{
            AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT,100).putBoolean(MusicGlowView.ENABLED,false).commit();
            MainActivity.applyDockSizeFromSettings();call(activity,"openSettings",new Class<?>[]{int.class},0);
        });waitForIdleSync();
        SettingsView settings=(SettingsView)field(activity,"settingsView");
        ScrollView scroll=(ScrollView)field(settings,"generalScroll");
        reveal(scroll,"Нижняя панель");shot("panel-size-settings");
        reveal(scroll,"Светомузыка панели");shot("effects-settings");
        reveal(scroll,"Цвет эффекта");shot("ambient-color-settings");
        result.putString("result","PASS: 11 demo screenshots, six synthetic previews; no real location or vehicle readings");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private void reveal(ScrollView scroll,String label){runOnMainSync(()->{
        TextView target=text(scroll,label);if(target==null)throw new IllegalStateException(label);
        int top=0;View current=target;
        while(current!=scroll.getChildAt(0)){top+=current.getTop();current=(View)current.getParent();}
        scroll.scrollTo(0,Math.max(0,top-12));
    });}
    private void shot(String name)throws Exception{
        waitForIdleSync();SystemClock.sleep(750);Bitmap b=getUiAutomation().takeScreenshot();
        File dir=new File(getTargetContext().getExternalFilesDir(null),"release-094");dir.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    private static TextView text(View view,String label){
        if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;
    }
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void call(Object object,String name,Class<?>[] types,Object... args){try{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(object,args);}catch(Exception e){throw new IllegalStateException(e);}}
}
