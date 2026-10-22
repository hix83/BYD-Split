package ru.logunov.bydsplit;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;

public final class WaveColorInstrumentation extends Instrumentation {
    private AlertDialog dialog;
    private WaveColorDialog.Palette palette;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();int original=AppPreferences.get(getTargetContext()).getInt(MusicGlowView.COLOR,0);
        boolean hadCustom=AppPreferences.get(getTargetContext()).contains(WaveColorDialog.CUSTOM_COLOR);
        int custom=AppPreferences.get(getTargetContext()).getInt(WaveColorDialog.CUSTOM_COLOR,0);
        try{
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));waitForIdleSync();
            main(()->{AppPreferences.get(activity).edit().putInt(MusicGlowView.COLOR,0).commit();open(activity);});
            main(()->touch(.5f,1f));int preview=selected();check(preview!=0,"live palette preview missing");
            main(()->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            check(selected()==0,"cancel did not restore gradient");
            main(()->open(activity));
            main(()->touch(.25f,.8f));int applied=selected();check(applied!=preview,"palette positions produce same color");
            main(()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            check(selected()==applied,"apply failed");
            check(AppPreferences.get(activity).getInt(WaveColorDialog.CUSTOM_COLOR,0)==applied,"custom color not remembered");
            main(()->open(activity));check(android.graphics.Color.HSVToColor(palette.hsv)==applied,"saved color not reopened");
            main(()->touch(.8f,.6f));check(selected()!=applied,"drag preview missing");
            main(()->dialog.cancel());check(selected()==applied,"back cancellation failed");
            main(()->open(activity));android.os.SystemClock.sleep(300);
            android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
            try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"wave-color-style.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}screenshot.recycle();
            result.putString("result","PASS: touch palette preview, distinct colors, cancel restores gradient, apply and custom memory, reopening, back cancellation");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            try{main(()->{if(dialog!=null&&dialog.isShowing())dialog.dismiss();});}catch(Throwable ignored){}
            android.content.SharedPreferences.Editor restore=AppPreferences.get(getTargetContext()).edit().putInt(MusicGlowView.COLOR,original);
            if(hadCustom)restore.putInt(WaveColorDialog.CUSTOM_COLOR,custom);else restore.remove(WaveColorDialog.CUSTOM_COLOR);restore.commit();
        }
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private int selected(){return AppPreferences.get(getTargetContext()).getInt(MusicGlowView.COLOR,0);}
    private void open(Activity activity){dialog=WaveColorDialog.show(activity,()->{});palette=find(dialog.getWindow().getDecorView());}
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void touch(float x,float y){
        long now=android.os.SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){
            MotionEvent event=MotionEvent.obtain(now,now,action,palette.getWidth()*x,palette.getHeight()*y,0);
            palette.dispatchTouchEvent(event);event.recycle();
        }
    }
    private WaveColorDialog.Palette find(View view){if(view instanceof WaveColorDialog.Palette)return (WaveColorDialog.Palette)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WaveColorDialog.Palette found=find(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
