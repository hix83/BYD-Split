package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator-only, reproducible public release screenshots. All telemetry is demo data. */
public final class ReleasePreviewInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private void invoke(Object o,String n,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private View text(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void shot(String name)throws Exception{SystemClock.sleep(350);android.graphics.Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"release-097-"+name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    public void onStart(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
        AppPreferences.get(getTargetContext()).edit().putInt("theme_mode",2).putBoolean(AppPreferences.KEY_DEMO_MODE,true).putBoolean(DockConfiguration.MODE,true)
            .putInt(DockConfiguration.HEIGHT_PERCENT,100).putString(AppPreferences.KEY_COMPACT_TARGET,AppPreferences.COMPACT_TARGET_VEHICLE)
            .remove(AppPreferences.KEY_FAR_APP).remove(AppPreferences.KEY_FAR_APPS).commit();
        List<DockConfiguration.Item> items=new ArrayList<>(Arrays.asList(DockConfiguration.Item.SONG,DockConfiguration.Item.EV_RANGE,DockConfiguration.Item.MUSIC,DockConfiguration.Item.MESSAGES,DockConfiguration.Item.CAMERA,DockConfiguration.Item.SETTINGS));
        for(AppEntry app:new AppRepository(getTargetContext()).loadLaunchableApps())if(app.component.getPackageName().equals("ru.yandex.yandexnavi")){items.add(items.size()-1,DockConfiguration.Item.app(app));break;}
        DockConfiguration.save(getTargetContext(),items,1);
        Activity a=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));SystemClock.sleep(1000);
        DynamicDockView dock=(DynamicDockView)field(a,"dynamicDock");
        shot("ev-night");
        main(dock::performLongClick);shot("dock-editor");
        main(()->{try{((Button)field(dock,"appsTab")).performClick();}catch(Exception e){throw new RuntimeException(e);}});shot("dock-apps");
        main(()->{invoke(dock,"chooseAssignment",new Class<?>[]{DockConfiguration.Item.class},DockConfiguration.Item.MUSIC);});shot("music-assignment");
        main(()->dock.closeEditor(false));
        main(()->invoke(a,"openVehicleDetails",new Class<?>[]{boolean.class},true));SystemClock.sleep(650);shot("tech");
        View page=(View)field(a,"internalPage");
        main(()->{View title=text(page,"Передний электропривод");if(title!=null)title.requestRectangleOnScreen(new android.graphics.Rect(0,0,title.getWidth(),500),true);});shot("front-drive");
        main(()->{View sample=text(page,"Показать пример");if(sample==null)throw new AssertionError("Demo diagnostic button missing");sample.performClick();sample.requestRectangleOnScreen(new android.graphics.Rect(0,0,sample.getWidth(),600),true);});SystemClock.sleep(400);shot("diagnostics");
        result.putString("result","PASS: demo-only editor, APP library, music assignment, technical page and diagnostic example screenshots");
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
