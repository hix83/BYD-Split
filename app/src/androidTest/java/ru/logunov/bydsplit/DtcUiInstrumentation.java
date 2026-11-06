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
            View description=find(activity.getWindow().getDecorView(),"Front left window not initialized\nСправочник Song L DM-i · EN · без подтверждения типа блока");
            check(description!=null,"reference description missing");
            runOnMainSync(()->description.requestRectangleOnScreen(new android.graphics.Rect(0,0,description.getWidth(),description.getHeight()),true));waitForIdleSync();Thread.sleep(200);
            shot(mode==android.content.res.Configuration.UI_MODE_NIGHT_YES?"descriptions-night":"descriptions-day");
            DtcScanController controller=DtcScanController.get(getTargetContext());
            int countBefore=controller.eligible().length;
            View erase=find(activity.getWindow().getDecorView(),"Стереть все…");check(erase!=null&&erase.isEnabled(),"clear unavailable for fresh demo");
            runOnMainSync(erase::performClick);waitForIdleSync();Thread.sleep(100);
            shot(mode==android.content.res.Configuration.UI_MODE_NIGHT_YES?"erase-confirm-night":"erase-confirm-day");
            runOnMainSync(()->{try{
                java.lang.reflect.Field popup=DtcScanView.class.getDeclaredField("popup");popup.setAccessible(true);
                AlertDialog dialog=(AlertDialog)popup.get(erase.getParent().getParent());
                check(dialog!=null&&dialog.isShowing()&&dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString().equals("Стереть и перепроверить"),"explicit erase confirmation missing");
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}});waitForIdleSync();
            check(controller.eligible().length==countBefore,"cancelled dialog changed codes");
            runOnMainSync(()->controller.erase(controller.eligible()));
            for(int i=0;i<120&&controller.running;i++)Thread.sleep(50);waitForIdleSync();
            check(!controller.running&&controller.eligible().length==0,"demo clear/reread did not finish");
            check(controller.displayedReport().getJSONObject("lastErase").getJSONArray("erase").getJSONObject(0).optBoolean("verified"),"demo reread not verified");
            check(before.equals(getTargetContext().getSharedPreferences("dtc",0).getString("last","")),"demo clear persisted real report");
            final Dialog[] setup={null};runOnMainSync(()->setup[0]=IntegrationsSettingsView.show(activity));waitForIdleSync();Thread.sleep(150);
            runOnMainSync(()->check(find(setup[0].getWindow().getDecorView(),"Проверить токен")!=null,"Telegram setup missing"));
            shot(mode==android.content.res.Configuration.UI_MODE_NIGHT_YES?"telegram-night":"telegram-day");runOnMainSync(()->setup[0].dismiss());
            runOnMainSync(()->{try{java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("openSettings",int.class);open.setAccessible(true);open.invoke(activity,8);}catch(Exception e){throw new IllegalStateException(e);}});
            waitForIdleSync();Thread.sleep(200);check(find(activity.getWindow().getDecorView(),"Проверить токен")!=null,"integrations settings page missing");
            shot(mode==android.content.res.Configuration.UI_MODE_NIGHT_YES?"integrations-night":"integrations-day");
        }
        check(before.equals(getTargetContext().getSharedPreferences("dtc",0).getString("last","")),"demo overwrote real saved report");
        runOnMainSync(activity::finish);
        result.putString("result","PASS: tech scanner, example result, no vendor stubs, day/night screenshots, confirmation/cancel/demo clear/reread, no demo persistence");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private static View find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void shot(String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getExternalFilesDir(null),"dtc-preview");if(!dir.exists()&&!dir.mkdirs())throw new IOException();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){if(image==null||!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();}image.recycle();}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
