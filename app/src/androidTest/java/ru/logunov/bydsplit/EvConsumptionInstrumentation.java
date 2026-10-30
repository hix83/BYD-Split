package ru.logunov.bydsplit;

import android.app.*;
import android.os.*;
import android.database.sqlite.SQLiteDatabase;

/** Uses a disposable named database; never injects learned values into the real journal. */
public final class EvConsumptionInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();String name="ev-learning-test.db";
        try{
            if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
            getTargetContext().deleteDatabase(name);
            check(!new StatisticsSample(VehicleTelemetrySnapshot.EMPTY,1000.,0.).chargeConnectionKnown,"unknown cable treated as unplugged");
            check(new StatisticsSample(VehicleTelemetrySnapshot.demo(),1000.,0.).chargeConnectionKnown,"known cable lost");
            long now=System.currentTimeMillis(),first,second;
            try(StatisticsStore store=new StatisticsStore(getTargetContext(),name)){
                first=store.begin("trip",now-300000,6,60);
                SQLiteDatabase db=store.getWritableDatabase();db.execSQL("DROP TABLE ev_consumption");db.setVersion(5);
            }
            try(StatisticsStore store=new StatisticsStore(getTargetContext(),name)){
                check(store.getReadableDatabase().getVersion()==6,"migration");
                check(store.list(0).size()==1,"migration lost journal");
                check(store.evConsumption(now).per100()==null,"old trip invented EV samples");
                store.recordEvConsumption(first,now-200000,2,.32,120000);
                second=store.begin("trip",now-100000,6,60);
                store.getWritableDatabase().execSQL("UPDATE journal SET engine=1 WHERE id="+second);
                store.recordEvConsumption(second,now-50000,3,.48,180000);
                check(Math.abs(store.evConsumption(now).per100()-16)<.001,"EV parts of mixed trip not counted");
                store.getWritableDatabase().execSQL("UPDATE journal SET hidden=1 WHERE id="+first);
                check(Math.abs(store.evConsumption(now).per100()-16)<.001,"native archive erased EV history");
            }
            try(StatisticsStore store=new StatisticsStore(getTargetContext(),name)){
                check(Math.abs(store.evConsumption(now).per100()-16)<.001,"history did not survive reopen");
                store.getWritableDatabase().execSQL("UPDATE journal SET deleted=1 WHERE id="+first);
                check(store.evConsumption(now).per100()==null,"deleted trip used for learning");
                check(store.evConsumption(now+EvConsumption.MAX_AGE_MS+1).per100()==null,"old history not expired");
            }
            getTargetContext().deleteDatabase(name);
            try(StatisticsStore store=new StatisticsStore(getTargetContext(),name)){
                for(int kind=0;kind<3;kind++){
                    long id=store.begin("trip",now-700000,6,60);TripAccumulator m=new TripAccumulator();
                    m.start=now-700000;m.end=now-100000;m.km=8;m.usedKwh=1.28;m.observedMs=600000;
                    m.engineSeen=kind==1;m.powerMissingMs=kind==2?1000:0;
                    store.checkpoint(id,m,false,"");
                }
                SQLiteDatabase db=store.getWritableDatabase();db.execSQL("DROP TABLE ev_consumption");db.setVersion(5);
            }
            try(StatisticsStore store=new StatisticsStore(getTargetContext(),name)){
                EvConsumption.Average average=store.evConsumption(now);
                check(Math.abs(average.km-8)<.001&&Math.abs(average.per100()-16)<.001,"legacy EV trip seeding included mixed or incomplete trips");
            }
            result.putString("result","PASS: v5 migration preserves journal, no fabricated history, EV parts of mixed trips, persistence, archive, deletion, expiry");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{getTargetContext().deleteDatabase(name);}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
