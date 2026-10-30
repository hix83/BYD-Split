package ru.logunov.bydsplit;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.lang.reflect.*;

/** Explicit read-only, parked-car check. Never clears codes or substitutes telemetry. */
public final class DtcVehicleInstrumentation extends Instrumentation {
    private Bundle options;
    public void onCreate(Bundle args){super.onCreate(args);options=args;start();}
    private void main(Runnable action){runOnMainSync(action);waitForIdleSync();}
    public void onStart(){
        Bundle result=new Bundle();
        try{
            if(!"true".equals(options.getString("readOnlyOnVehicle")))throw new IllegalStateException("Explicit vehicle check required");
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.elapsedRealtime()+15000;
            while(VehicleSleepController.get(activity).mode()!=VehicleSleepState.Mode.RUNNING&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
            if(AppPreferences.isDemoModeEnabled(activity)||VehicleSleepController.get(activity).mode()!=VehicleSleepState.Mode.RUNNING)throw new IllegalStateException("Real READY vehicle required");
            main(()->{try{Method open=MainActivity.class.getDeclaredMethod("openVehicleDetails",boolean.class);open.setAccessible(true);open.invoke(activity,true);}catch(Exception e){throw new RuntimeException(e);}});
            DtcScanController controller=DtcScanController.get(activity);
            main(controller::start);
            if("cancel".equals(options.getString("mode"))){
                SystemClock.sleep(1500);
                main(()->{try{Method open=MainActivity.class.getDeclaredMethod("openSettings");open.setAccessible(true);open.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
                if(controller.running)throw new AssertionError("page close did not cancel scan");
                Bundle phase=new Bundle();phase.putString("phase","page_closed_owner_alive");sendStatus(0,phase);
                SystemClock.sleep(6000);
                result.putString("result","PASS: closing the diagnostic page cancelled scan");
            }else{
                until=SystemClock.elapsedRealtime()+100000;
                while(controller.running&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
                if(controller.running)throw new AssertionError("UI scan timeout did not finish");
                result.putString("message",controller.message);
                if(controller.report==null)throw new AssertionError("No completed report: "+controller.message);
                result.putString("report",controller.report.toString());
                SystemClock.sleep(600);
                result.putString("result","PASS: real manual read-only scan completed");
            }
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
