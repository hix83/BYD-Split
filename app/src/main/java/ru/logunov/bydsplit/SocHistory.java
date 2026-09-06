package ru.logunov.bydsplit;
import android.content.*;

/** Average of periodic samples collected by this app, not a lifetime BMS counter. */
final class SocHistory {
    static synchronized void record(Context c,VehicleTelemetrySnapshot s) {
        if(s.batterySocPercent==null)return;
        SharedPreferences p=c.getSharedPreferences("soc_history",0);
        long now=System.currentTimeMillis();
        if(now-p.getLong("last",0)<60000)return;
        boolean charging=s.chargeGunState!=null&&s.chargeGunState>=2&&s.chargeGunState<=4;
        boolean disconnected=s.chargeGunState!=null&&s.chargeGunState==1;
        boolean reset=p.getBoolean("charging",false)&&disconnected;
        float sum=reset?0:p.getFloat("sessionSum",0);long n=reset?0:p.getLong("sessionN",0);
        SharedPreferences.Editor edit=p.edit().putLong("last",now)
            .putFloat("sum",p.getFloat("sum",0)+s.batterySocPercent).putLong("n",p.getLong("n",0)+1)
            .putFloat("sessionSum",sum+s.batterySocPercent).putLong("sessionN",n+1);
        if(charging||disconnected)edit.putBoolean("charging",charging);
        if(reset)edit.putBoolean("observedCharge",true);
        edit.apply();
    }
    static Float average(Context c,boolean session) {
        SharedPreferences p=c.getSharedPreferences("soc_history",0);
        if(session&&!p.getBoolean("observedCharge",false))return null;
        long n=p.getLong(session?"sessionN":"n",0);
        return n<2?null:p.getFloat(session?"sessionSum":"sum",0)/n;
    }
}
