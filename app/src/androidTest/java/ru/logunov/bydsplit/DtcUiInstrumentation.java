package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Emulator-only UI check. Example reports never enter the owner's saved DTC results. */
public final class DtcUiInstrumentation extends Instrumentation {
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
        try{Class.forName("android.hardware.bydauto.BYDAutoEventValue");throw new AssertionError("Vendor stubs packaged in APK");}catch(ClassNotFoundException expected){}
        String before=getTargetContext().getSharedPreferences("dtc",0).getString("last","");
        for(int mode:new int[]{android.content.res.Configuration.UI_MODE_NIGHT_YES,android.content.res.Configuration.UI_MODE_NIGHT_NO}){
            if(activity!=null)runOnMainSync(activity::finish);
            AppPreferences.get(getTargetContext()).edit().putInt("theme_mode",mode==android.content.res.Configuration.UI_MODE_NIGHT_YES?2:1).commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));
            runOnMainSync(()->{
                try{java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("openVehicleDetails",boolean.class);open.setAccessible(true);open.invoke(activity,true);}catch(Exception error){throw new IllegalStateException(error);}
            });
            waitForIdleSync();Thread.sleep(700);
            View button=find(activity.getWindow().getDecorView(),"Показать пример");if(button==null)throw new AssertionError("scanner button missing");runOnMainSync(button::performClick);waitForIdleSync();Thread.sleep(250);
            check(find(activity.getWindow().getDecorView(),"U1166-87 · Есть сейчас")!=null,"example code missing");
            shot(mode==android.content.res.Configuration.UI_MODE_NIGHT_YES?"diagnostics-night":"diagnostics-day");
        }
        check(before.equals(getTargetContext().getSharedPreferences("dtc",0).getString("last","")),"demo overwrote real saved report");
        runOnMainSync(activity::finish);
        result.putString("result","PASS: tech scanner, example result, no vendor stubs, day/night screenshots, no demo persistence");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private static View find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void shot(String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getExternalFilesDir(null),"dtc-preview");if(!dir.exists()&&!dir.mkdirs())throw new IOException();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){if(image==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();}image.recycle();}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
