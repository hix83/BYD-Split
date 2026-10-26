package ru.logunov.bydsplit;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import java.util.*;
import java.io.StringWriter;

/** Runs on an explicitly selected emulator; uses its own disposable SQLite file. */
public final class StatisticsInstrumentation extends Instrumentation {
    private boolean live;
    private Bundle options;
    @Override public void onCreate(Bundle args){super.onCreate(args);options=args;live=args!=null&&Boolean.parseBoolean(args.getString("live","false"));start();}
    @Override public void onStart() {
        if(live){probeLive();return;}
        Bundle result=new Bundle();String name="song_statistics_instrumentation.db";
        getTargetContext().deleteDatabase(name);
        try(StatisticsStore store=new StatisticsStore(getTargetContext(),name)) {
            long id=store.begin("trip",1000000,6,60);
            TripAccumulator m=new TripAccumulator();
            for(int i=0;i<=120;i++)m.add(1000000+i*30000,60.,10000.,null,false);
            store.checkpoint(id,m,true,"");
            check(store.list(0).get(0).active,"active row not persisted");
            store.recoverInterrupted();
            StatisticsStore.Entry recovered=store.list(0).get(0);
            check(!recovered.active&&recovered.missing>0&&recovered.cost()==null,"interrupted recovery incomplete");
            store.correct(id,0.,10.);
            near(store.list(0).get(0).cost(),60);
            store.addPurchase("fuel",5000000,40,60,1000.,true);
            store.addPurchase("fuel",6000000,10,61,1250.,false);
            store.addPurchase("fuel",7000000,25,62,1500.,true);
            near(StatisticsStore.fullTankConsumption(store.list(0)),7);
            long latest=store.list(0).get(0).id;
            store.updatePurchase(latest,30,63,1500.,true);
            near(StatisticsStore.fullTankConsumption(store.list(0)),8);
            near(store.list(0).get(0).cost(),1890);
            store.addPurchase("charge",8000000,10,8,null,false);
            near(store.list(0).get(0).cost(),80);
            StringWriter csv=new StringWriter();store.exportCsv(csv);
            check(csv.toString().contains("Стоимость ₽")&&csv.toString().contains("fuel;"),"CSV missing journal rows");
            long chargeId=store.list(0).get(0).id;store.delete(chargeId);
            check(store.list(0).size()==4,"delete failed");
            store.close();
            try(StatisticsStore reopened=new StatisticsStore(getTargetContext(),name)) {
                near(reopened.list(0).get(3).km,60);
                near(reopened.list(0).get(3).electricPrice,6);
            }
            long autoId=store.begin("trip",9000000,6,60);
            TripAccumulator hybrid=new TripAccumulator();
            hybrid.add(9000000,60.,10000.,null,true);hybrid.fuel.sample(302.4);hybrid.electricity.sample(1000.);
            hybrid.add(9030000,60.,10000.,null,true);hybrid.fuel.sample(305.2);hybrid.electricity.sample(1002.);
            store.checkpoint(autoId,hybrid,false,"");
            StatisticsStore.Entry automatic=store.list(0).get(0);
            near(automatic.fuelLitres(),2.8);near(automatic.fuelCost(),168);
            near(automatic.cost(),180);near(automatic.electricityCost(),12);
            check(automatic.electricitySource().startsWith("счётчик BMS"),"BMS source lost");
            store.correct(autoId,null,2.);
            near(store.list(0).get(0).cost(),180);
            store.correct(autoId,4.,2.);
            near(store.list(0).get(0).fuelLitres(),4);
            check(store.list(0).get(0).fuelSource().equals("вручную"),"manual provenance lost");
            store.correct(autoId,null,2.);
            near(store.list(0).get(0).fuelLitres(),2.8);
            StringWriter autoCsv=new StringWriter();store.exportCsv(autoCsv);
            check(autoCsv.toString().contains("счётчик автомобиля")&&autoCsv.toString().contains("302.4;305.2"),"fuel provenance absent from CSV");
            store.checkpoint(autoId,hybrid,true,"");store.recoverInterrupted();
            check(store.list(0).get(0).fuelLitres()==null,"crash reused checkpoint as closing edge");
            check(store.list(0).get(0).energyEnd==null,"crash retained energy closing edge");
            near(store.list(0).get(0).fuelStart,302.4);
            long charge=store.begin("charge",10000000,6,60,"настроенный тариф",50.,10);
            TripAccumulator intake=new TripAccumulator();intake.start=10000000;intake.end=11000000;intake.recoveredKwh=10;intake.observedMs=1000000;
            store.checkpoint(charge,intake,false,"");
            near(store.list(0).get(0).cost(),10/0.9*6);
            near(store.tripPrice(12000000,20,7,true).rate,6/0.9);
            near(store.tripPrice(12000000,20,7,false).rate,7);
            long priced=store.begin("trip",12000000,store.tripPrice(12000000,20,7,true).rate,60,"последняя зарядка",null,0);
            TripAccumulator pricedMath=new TripAccumulator();pricedMath.start=12000000;pricedMath.end=12030000;
            pricedMath.electricity.sample(2000.);pricedMath.electricity.sample(2002.);
            pricedMath.fuel.sample(500.);pricedMath.fuel.sample(501.);
            store.checkpoint(priced,pricedMath,false,"");
            near(store.list(0).get(0).cost(),73.33);
            store.addPurchase("charge",13000000,10,1000,null,false);
            near(store.list(0).get(1).cost(),73.33); // A future purchase cannot reprice an existing trip.
            testMigration();
            testNativeHistory();
            testNativeSnapshot();
            testResume();
            result.putString("stream","SQLite journal, automatic costs, v1/v2/v3→v5 migrations, native history import and deduplication, tariff/correction retention, deletion tombstones, WAL snapshot and live counter resume: passed\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);
        } finally {getTargetContext().deleteDatabase(name);}
    }
    /** Actual car readings, separate disposable DB. Never inserts a trip into the owner's journal. */
    private void probeLive() {
        Bundle result=new Bundle();String database="song_parked_live_probe.db";
        getTargetContext().deleteDatabase(database);
        try {
            android.content.SharedPreferences prefs=AppPreferences.get(getTargetContext());
            android.content.SharedPreferences.Editor configured=prefs.edit();
            for(String key:new String[]{"electricity_price","fuel_price","dc_electricity_price"}) {
                if(options!=null&&options.containsKey(key)) {
                    float price=Float.parseFloat(options.getString(key));
                    check(Float.isFinite(price)&&price>=0&&price<=100000,"invalid requested tariff");
                    configured.putFloat(key,price);
                }
            }
            check(configured.commit(),"tariffs not saved");
            double electricityPrice=prefs.getFloat("electricity_price",6),fuelPrice=prefs.getFloat("fuel_price",60);
            TripAccumulator observed=new TripAccumulator();long started=android.os.SystemClock.elapsedRealtime();int samples=0;
            while(android.os.SystemClock.elapsedRealtime()-started<90000) {
                StatisticsSample sample=LocalAdbManager.get(getTargetContext()).readStatistics();
                check(sample!=null&&sample.speed!=null,"vehicle read unavailable");
                check(sample.speed==0,"parked probe stopped: car is moving");
                observed.fuel.sample(sample.lifetimeFuelLitres);observed.electricity.sample(sample.lifetimeElectricityKwh);
                observed.add(System.currentTimeMillis(),sample.speed,sample.watts,sample.odometer,sample.engine);
                samples++;
                Bundle progress=new Bundle();progress.putString("stream","Live parked sample "+samples+": fuel="+sample.lifetimeFuelLitres+" L, BMS="+sample.lifetimeElectricityKwh+" kWh, HV watts="+sample.watts+"\n");sendStatus(0,progress);
                if(observed.automaticElectricityKwh()!=null&&observed.automaticElectricityKwh()>0&&samples>=3)break;
                android.os.SystemClock.sleep(10000);
            }
            check(observed.fuel.startLitres!=null&&observed.fuel.endLitres!=null,"fuel boundaries unavailable");
            check(observed.electricity.startKwh!=null&&observed.electricity.endKwh!=null,"BMS boundaries unavailable");
            try(StatisticsStore store=new StatisticsStore(getTargetContext(),database)) {
                long id=store.begin("trip",observed.start,electricityPrice,fuelPrice);
                store.checkpoint(id,observed,false,"Проверка на стоянке — не поездка");
                StatisticsStore.Entry entry=store.list(0).get(0);
                org.json.JSONObject report=new org.json.JSONObject();
                report.put("real_trip_recorded",false);report.put("samples",samples);
                report.put("fuel_start_l",entry.fuelStart);report.put("fuel_end_l",entry.fuelEnd);report.put("fuel_used_l",entry.fuelLitres());
                report.put("energy_start_kwh",entry.energyStart);report.put("energy_end_kwh",entry.energyEnd);report.put("energy_used_kwh",entry.electricityKwh());
                report.put("energy_source",entry.electricitySource());report.put("electricity_price",electricityPrice);report.put("fuel_price",fuelPrice);
                report.put("electricity_cost",entry.electricityCost());report.put("fuel_cost",entry.fuelCost());report.put("total_cost",entry.cost());
                result.putString("stream","PARKED_LIVE_REPORT="+report.toString()+"\n");
            }
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {result.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
        finally {getTargetContext().deleteDatabase(database);}
    }
    private void testResume(){
        String name="song_statistics_resume_test.db";getTargetContext().deleteDatabase(name);
        try(StatisticsStore db=new StatisticsStore(getTargetContext(),name)){
            TripAccumulator t=new TripAccumulator();t.add(1000000,0.,null,null,false);t.fuel.sample(400.);t.electricity.sample(1200.);
            t.add(1030000,0.,null,null,false);t.fuel.sample(400.);t.electricity.sample(1200.1);
            long id=db.begin("trip",1000000,21.5,70);db.checkpoint(id,t,true,"");
            StatisticsStore.Entry original=db.list(0).get(0);check(original.missing==0&&original.powerMissing==30000,"missing power confused with missing speed");
            TripAccumulator resumed=original.resume();db.recoverInterruptedExcept(id);
            resumed.add(1040000,0.,null,null,false);resumed.fuel.sample(400.);resumed.electricity.sample(1200.2);db.checkpoint(id,resumed,true,"");
            StatisticsStore.Entry e=db.list(0).get(0);near(e.energyStart,1200);near(e.electricityKwh(),.2);near(e.cost(),4.3);check(e.active&&e.missing==10000,"resume did not preserve gap");
        }finally{getTargetContext().deleteDatabase(name);}
    }
    private void testNativeSnapshot() throws Exception {
        java.io.File source=new java.io.File(getTargetContext().getCacheDir(),"byd-native-wal-fixture.db");
        android.database.sqlite.SQLiteDatabase.deleteDatabase(source);
        try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(source,null)){
            db.enableWriteAheadLogging();try(android.database.Cursor pragma=db.rawQuery("PRAGMA wal_autocheckpoint=0",null)){pragma.moveToFirst();}
            db.execSQL("CREATE TABLE EnergyConsumption(_id INTEGER,start_timestamp INTEGER,end_timestamp INTEGER,duration INTEGER,trip REAL,electricity REAL,fuel REAL,is_deleted INTEGER)");
            db.execSQL("INSERT INTO EnergyConsumption VALUES(1,1792630000,1792630600,600,10,2,1.5,0)");
            org.json.JSONArray rows=NativeEnergyHistoryBridge.readRows(source,getTargetContext().getCacheDir());
            check(rows.length()==1,"WAL-only native row lost");near(rows.getJSONArray(0).getDouble(5),2);
            try(android.database.Cursor c=db.rawQuery("SELECT count(*) FROM EnergyConsumption",null)){check(c.moveToFirst()&&c.getInt(0)==1,"native source modified");}
        }finally{android.database.sqlite.SQLiteDatabase.deleteDatabase(source);}
    }
    private void testNativeHistory() throws Exception {
        String name="song_statistics_native_test.db";getTargetContext().deleteDatabase(name);
        try(StatisticsStore db=new StatisticsStore(getTargetContext(),name)){
            long base=1792630000000L;
            long local=db.begin("trip",base+10000,21.5,70);TripAccumulator observation=new TripAccumulator();observation.start=base+10000;observation.end=base+590000;observation.km=10;db.checkpoint(local,observation,false,"original observation");
            org.json.JSONArray rows=new org.json.JSONArray("[[1,1792630000,1792630600,600,10,2,1.5,0],[2,1792631000,1792631600,600,0,-1.2,0.4,0],[3,1792632000,1792632600,600,2,null,null,0]]");
            check(db.importNative(rows,21.5,70)==3,"native import count");
            check(db.list(0).size()==3&&db.archivedObservations()==1,"local/native double counting");
            StatisticsStore.Entry one=db.list(0).get(2);near(one.cost(),148);near(one.electricityKwh(),2);near(one.fuelLitres(),1.5);check(one.nativeRecord(),"native provenance");
            StatisticsStore.Entry generated=db.list(0).get(1);near(generated.cost(),28);near(generated.electricityKwh(),0);near(generated.nativeEnergy,-1.2);
            check(db.list(0).get(0).cost()==null,"null native columns became zero");
            db.importNative(rows,999,999);near(db.list(0).get(2).cost(),148);check(db.list(0).size()==3,"duplicate reimport");
            db.correct(one.id,2.,3.);db.importNative(rows,999,999);near(db.list(0).get(2).cost(),204.5);
            db.delete(one.id);db.importNative(rows,999,999);check(db.list(0).size()==2,"deleted native row resurrected");
            try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT note FROM journal WHERE id=?",new String[]{""+local})){check(c.moveToFirst()&&"original observation".equals(c.getString(0)),"observation archive lost");}
            rows.getJSONArray(1).put(7,1);db.importNative(rows,21.5,70);check(db.list(0).size()==1,"BYD deletion ignored");
        }finally{getTargetContext().deleteDatabase(name);}
        String migrated="song_statistics_v3_migration.db";getTargetContext().deleteDatabase(migrated);
        try(android.database.sqlite.SQLiteDatabase db=getTargetContext().openOrCreateDatabase(migrated,0,null)){
            db.execSQL("CREATE TABLE journal(id INTEGER PRIMARY KEY,type TEXT,start INTEGER,end INTEGER,active INTEGER,km REAL,used REAL,recovered REAL,moving INTEGER,observed INTEGER,missing INTEGER,max REAL,engine INTEGER,electric_price REAL,fuel_price REAL,litres REAL,grid REAL,odometer REAL,full INTEGER,note TEXT,fuel_start REAL,fuel_end REAL,fuel_invalid INTEGER,energy_start REAL,energy_end REAL,energy_invalid INTEGER,soc_start REAL,price_source TEXT,charge_loss REAL)");db.setVersion(3);
            db.execSQL("INSERT INTO journal(id,type,start,end,active,litres,grid,electric_price,fuel_price,note,price_source) VALUES(1,'trip',1,2,0,1,2,21.5,70,'kept','kept')");
        }
        try(StatisticsStore db=new StatisticsStore(getTargetContext(),migrated)){near(db.list(0).get(0).cost(),113);check(db.getReadableDatabase().getVersion()==5,"v3 migration");}finally{getTargetContext().deleteDatabase(migrated);}
    }
    private void testMigration() {
        for(int version:new int[]{1,2}) {
        String name="song_statistics_v"+version+"_migration.db";
        getTargetContext().deleteDatabase(name);
        try {
            try(android.database.sqlite.SQLiteDatabase db=getTargetContext().openOrCreateDatabase(name,0,null)) {
                db.execSQL("CREATE TABLE journal (id INTEGER PRIMARY KEY, type TEXT NOT NULL, start INTEGER, end INTEGER, active INTEGER DEFAULT 0, km REAL DEFAULT 0, used REAL DEFAULT 0, recovered REAL DEFAULT 0, moving INTEGER DEFAULT 0, observed INTEGER DEFAULT 0, missing INTEGER DEFAULT 0, max REAL DEFAULT 0, engine INTEGER DEFAULT 0, electric_price REAL, fuel_price REAL, litres REAL, grid REAL, odometer REAL, full INTEGER DEFAULT 0, note TEXT DEFAULT '')");
                db.execSQL("INSERT INTO journal(type,start,end,litres,fuel_price,grid,electric_price) VALUES ('trip',1,2,1.5,60,2,6)");
                if(version==2) {
                    db.execSQL("ALTER TABLE journal ADD COLUMN fuel_start REAL");
                    db.execSQL("ALTER TABLE journal ADD COLUMN fuel_end REAL");
                    db.execSQL("ALTER TABLE journal ADD COLUMN fuel_invalid INTEGER DEFAULT 0");
                    db.execSQL("UPDATE journal SET fuel_start=302.4,fuel_end=305.2");
                }
                db.setVersion(version);
            }
            try(StatisticsStore migrated=new StatisticsStore(getTargetContext(),name)) {
                check(migrated.getReadableDatabase().getVersion()==5,"schema version not upgraded");
                StatisticsStore.Entry old=migrated.list(0).get(0);
                near(old.fuelLitres(),1.5);near(old.cost(),102);
                if(version==1)check(old.fuelStart==null&&old.fuelEnd==null,"migration invented old counters");
                else {near(old.fuelStart,302.4);near(old.fuelEnd,305.2);}
                check(old.fuelSource().equals("вручную"),"old manual fuel lost");
            }
        } finally {getTargetContext().deleteDatabase(name);}
        }
    }
    private static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
    private static void near(Double a,double b){check(a!=null&&Math.abs(a-b)<1e-7,a+" != "+b);}
}
