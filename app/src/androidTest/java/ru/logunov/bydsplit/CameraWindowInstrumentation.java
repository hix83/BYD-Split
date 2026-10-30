package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;

/** Emulator regression: embedded Settings hides system overlays, but not the owned camera dialog. */
public final class CameraWindowInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private String shell(String command)throws Exception{
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
            InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;
            while((n=input.read(bytes))>=0)out.write(bytes,0,n);return out.toString("UTF-8");
        }
    }
    private Object field(String name)throws Exception{
        Field f=ParkingCameraOverlay.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }
    private void main(Runnable action){runOnMainSync(action);waitForIdleSync();}
    public void onStart(){
        Bundle result=new Bundle();String previous=null;boolean changed=false;
        try{
            if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
            previous=AppPreferences.get(getTargetContext()).getString(AppPreferences.KEY_PARKING_CAMERA_URL,null);
            AppPreferences.get(getTargetContext()).edit().putString(AppPreferences.KEY_PARKING_CAMERA_URL,"file:///android_asset/camera-test.html").commit();changed=true;
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));
            AppEntry settings=null;
            for(AppEntry app:new AppRepository(activity).loadLaunchableApps())if(app.component.getPackageName().equals("com.android.settings")){settings=app;break;}
            if(settings==null)throw new AssertionError("Settings missing");final AppEntry selected=settings;
            main(()->{try{Method m=MainActivity.class.getDeclaredMethod("activateLargeApp",AppEntry.class);m.setAccessible(true);m.invoke(activity,selected);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(2000);
            if(!shell("dumpsys window windows").contains("HIDE_NON_SYSTEM_OVERLAY_WINDOWS"))throw new AssertionError("Regression precondition missing");
            for(int attempt=0;attempt<2;attempt++){
                final Context context=attempt==0?activity:activity.getApplicationContext();
                main(()->{if(!ParkingCameraOverlay.show(context))throw new AssertionError("Camera show failed");});
                SystemClock.sleep(500);
                Dialog dialog=(Dialog)field("currentDialog");
                if(dialog==null||!dialog.isShowing()||!ParkingCameraOverlay.isShowing())throw new AssertionError("Owned dialog not shown");
                if(dialog.getWindow().getAttributes().type>=2000)throw new AssertionError("Camera still uses system overlay inside Split");
                String windows=shell("dumpsys window windows");
                int start=windows.indexOf("BYD Split · Камеры");
                if(start<0)throw new AssertionError("Camera window missing");
                String block=windows.substring(start);int next=block.indexOf("\n  Window #");if(next>=0)block=block.substring(0,next);
                if(!block.contains("isReadyForDisplay()=true")||!block.contains("isVisible=true"))throw new AssertionError("Camera hidden by Settings: "+block);
                if(MainActivity.parkingCameraWindowHost()!=activity)throw new AssertionError("Camera stopped Split and triggered task handoff");
                if(attempt==0)shell("screencap -p /sdcard/song-camera-window.png");
                if(attempt==0)main(()->ParkingCameraOverlay.close(activity));else main(dialog::cancel);
                if(ParkingCameraOverlay.isShowing()||field("currentWebView")!=null||field("currentDialog")!=null)throw new AssertionError("Camera window/WebView retained after close");
            }
            result.putString("result","PASS: camera visible above embedded Settings; Activity and application-context opens use owned window; close/back release window and WebView; Split stays resumed");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            main(()->ParkingCameraOverlay.close(getTargetContext()));
            if(changed){android.content.SharedPreferences.Editor editor=AppPreferences.get(getTargetContext()).edit();if(previous==null)editor.remove(AppPreferences.KEY_PARKING_CAMERA_URL);else editor.putString(AppPreferences.KEY_PARKING_CAMERA_URL,previous);editor.commit();}
        }
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
