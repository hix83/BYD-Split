package ru.logunov.bydsplit;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import java.io.*;

/** Local journal. A live row is checkpointed and survives process termination. */
final class StatisticsStore extends SQLiteOpenHelper {
    static final class Entry {
        long id, start, end, moving, observed, missing, powerMissing;
        String type, note, priceSource;
        double km, used, recovered, max, electricPrice, fuelPrice;
        boolean active, engine, full, fuelInvalid, energyInvalid;
        Double litres, grid, odometer, fuelStart, fuelEnd, energyStart, energyEnd, socStart;
        double chargeLoss;
        String source="launcher";
        Double nativeEnergy,nativeFuel;
        boolean nativeRecord(){return "byd".equals(source);}
        TripAccumulator math() {
            TripAccumulator m = new TripAccumulator();
            m.start=start; m.end=end; m.km=km; m.usedKwh=used; m.recoveredKwh=recovered;
            m.movingMs=moving; m.observedMs=observed; m.missingMs=missing; m.powerMissingMs=powerMissing; m.maxSpeed=max; m.engineSeen=engine;
            m.electricity.startKwh=energyStart; m.electricity.endKwh=energyEnd; m.electricity.invalid=energyInvalid;
            return m;
        }
        TripAccumulator resume(){
            TripAccumulator m=math();m.fuel.restore(fuelStart,fuelEnd,fuelInvalid);m.electricity.restore(energyStart,energyEnd,energyInvalid);
            m.lastTime=end;return m;
        }
        Double automaticFuelLitres() { return fuelInvalid ? null : TripFuelCounter.difference(fuelStart, fuelEnd); }
        Double fuelLitres() { return litres != null ? litres : nativeRecord()?nativeFuel:type.equals("trip") ? automaticFuelLitres() : null; }
        Double fuelCost() { Double amount = fuelLitres(); return amount == null ? null : amount * fuelPrice; }
        String fuelSource() { return litres != null ? "вручную" : nativeRecord()?"штатный журнал BYD":automaticFuelLitres() != null ? "счётчик автомобиля" : "нет данных"; }
        Double electricityKwh() { return grid != null ? grid : nativeRecord()?(nativeEnergy==null?null:Math.max(0,nativeEnergy)):math().automaticElectricityKwh(); }
        Double electricityCost() { Double kwh=electricityKwh();return kwh==null?null:Math.round(kwh*electricPrice*100)/100.; }
        String electricitySource() {
            if(grid!=null)return "уточнено вручную";
            if(nativeRecord())return "штатный журнал BYD · баланс батареи";
            Double counter=TripEnergyCounter.difference(energyStart,energyEnd,energyInvalid,end-start);
            if(counter!=null&&(counter>0||(missing>0||powerMissing>0||observed==0)&&end>start))return "счётчик BMS · шаг 0,1 кВт·ч";
            return math().automaticElectricityKwh()!=null?"интеграл мощности HV":"нет данных";
        }
        Double cost() {
            if (type.equals("fuel")) return litres == null ? null : litres * fuelPrice;
            if (type.equals("charge")) return missing > 0 && grid == null ? null : (grid == null ? recovered / (1 - chargeLoss / 100) : grid) * electricPrice;
            if(nativeRecord()){Double energy=electricityCost(),fuel=fuelCost();return energy==null||fuel==null?null:Math.round((energy+fuel)*100)/100.;}
            TripAccumulator trip = math();
            Double fuel = fuelLitres();
            if (fuel != null && fuel > 0) trip.engineSeen = true;
            return trip.estimatedCost(electricPrice, fuel, fuelPrice, grid);
        }
    }
    StatisticsStore(Context context) { this(context, "song_statistics.db"); }
    StatisticsStore(Context context, String name) { super(context.getApplicationContext(), name, null, 6); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE journal (id INTEGER PRIMARY KEY, type TEXT NOT NULL, start INTEGER, end INTEGER, active INTEGER DEFAULT 0, km REAL DEFAULT 0, used REAL DEFAULT 0, recovered REAL DEFAULT 0, moving INTEGER DEFAULT 0, observed INTEGER DEFAULT 0, missing INTEGER DEFAULT 0, max REAL DEFAULT 0, engine INTEGER DEFAULT 0, electric_price REAL, fuel_price REAL, litres REAL, grid REAL, odometer REAL, full INTEGER DEFAULT 0, note TEXT DEFAULT '', fuel_start REAL, fuel_end REAL, fuel_invalid INTEGER DEFAULT 0, energy_start REAL, energy_end REAL, energy_invalid INTEGER DEFAULT 0, soc_start REAL, price_source TEXT DEFAULT 'настроенный тариф', charge_loss REAL DEFAULT 0, source TEXT DEFAULT 'launcher', source_id INTEGER, native_energy REAL, native_fuel REAL, hidden INTEGER DEFAULT 0, deleted INTEGER DEFAULT 0, power_missing INTEGER DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX journal_byd_id ON journal(source,source_id)");
        db.execSQL("CREATE INDEX journal_start ON journal(start)");
        createEvConsumption(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int old, int version) {
        if (old < 2) {
            db.execSQL("ALTER TABLE journal ADD COLUMN fuel_start REAL");
            db.execSQL("ALTER TABLE journal ADD COLUMN fuel_end REAL");
            db.execSQL("ALTER TABLE journal ADD COLUMN fuel_invalid INTEGER DEFAULT 0");
        }
        if (old < 3) {
            db.execSQL("ALTER TABLE journal ADD COLUMN energy_start REAL");
            db.execSQL("ALTER TABLE journal ADD COLUMN energy_end REAL");
            db.execSQL("ALTER TABLE journal ADD COLUMN energy_invalid INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE journal ADD COLUMN soc_start REAL");
            db.execSQL("ALTER TABLE journal ADD COLUMN price_source TEXT DEFAULT 'настроенный тариф'");
            db.execSQL("ALTER TABLE journal ADD COLUMN charge_loss REAL DEFAULT 0");
        }
        if(old<4)upgradeHistory(db);
        if(old<5)db.execSQL("ALTER TABLE journal ADD COLUMN power_missing INTEGER DEFAULT 0");
        if(old<6){
            createEvConsumption(db);
            // v5 records flag every observed engine/unknown-engine sample and missing HV power.
            // Only complete, closed, wholly electric launcher trips can seed the new history.
            if(old>=5)db.execSQL("INSERT INTO ev_consumption(trip_id,at,km,kwh,ms) SELECT id,end,km,used-recovered,observed FROM journal WHERE source='launcher' AND type='trip' AND active=0 AND deleted=0 AND engine=0 AND missing=0 AND power_missing=0 AND observed>0 AND km>=0.1 AND used>=0 AND recovered>=0 AND end>=? AND end<=?",
                    new Object[]{System.currentTimeMillis()-EvConsumption.MAX_AGE_MS,System.currentTimeMillis()});
        }
    }
    private void createEvConsumption(SQLiteDatabase db){
        db.execSQL("CREATE TABLE ev_consumption (id INTEGER PRIMARY KEY, trip_id INTEGER NOT NULL, at INTEGER NOT NULL, km REAL NOT NULL, kwh REAL NOT NULL, ms INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX ev_consumption_at ON ev_consumption(at,id)");
    }
    synchronized void recordEvConsumption(long trip,long at,double km,double kwh,long ms){
        if(trip<=0||ms<=0||!Double.isFinite(km)||!Double.isFinite(kwh)||km<0)return;
        SQLiteDatabase db=getWritableDatabase();ContentValues value=new ContentValues();
        value.put("trip_id",trip);value.put("at",at);value.put("km",km);value.put("kwh",kwh);value.put("ms",ms);db.insertOrThrow("ev_consumption",null,value);
        db.delete("ev_consumption","at<?",new String[]{Long.toString(at-EvConsumption.MAX_AGE_MS)});
        db.execSQL("DELETE FROM ev_consumption WHERE id < (SELECT max(id)-10000 FROM ev_consumption)");
    }
    synchronized EvConsumption.Average evConsumption(long now){
        List<EvConsumption.Row> rows=new ArrayList<>();
        // Archived launcher observations remain valid; native imports never create EV rows.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.trip_id,e.km,e.kwh FROM ev_consumption e JOIN journal j ON j.id=e.trip_id WHERE j.deleted=0 AND e.at>=? AND e.at<=? ORDER BY e.at DESC,e.id DESC LIMIT 10000",new String[]{Long.toString(now-EvConsumption.MAX_AGE_MS),Long.toString(now)})){
            while(c.moveToNext())rows.add(new EvConsumption.Row(c.getLong(0),c.getDouble(1),c.getDouble(2)));
        }
        return EvConsumption.average(rows);
    }
    /** A baseline from actual native trip energy, never the dashboard's remaining range. */
    synchronized EvConsumption.Average nativeEvConsumption(long now){
        List<EvConsumption.Row> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,km,native_energy FROM journal WHERE source='byd' AND type='trip' AND active=0 AND deleted=0 AND hidden=0 AND native_fuel=0 AND native_energy>=0 AND km>0 AND end>=? AND end<=? ORDER BY end DESC,id DESC LIMIT 10",new String[]{Long.toString(now-EvConsumption.MAX_AGE_MS),Long.toString(now)})){
            while(c.moveToNext())rows.add(new EvConsumption.Row(c.getLong(0),c.getDouble(1),c.getDouble(2)));
        }
        return EvConsumption.average(rows);
    }
    private void upgradeHistory(SQLiteDatabase db){
        db.execSQL("ALTER TABLE journal ADD COLUMN source TEXT DEFAULT 'launcher'");
        db.execSQL("ALTER TABLE journal ADD COLUMN source_id INTEGER");
        db.execSQL("ALTER TABLE journal ADD COLUMN native_energy REAL");
        db.execSQL("ALTER TABLE journal ADD COLUMN native_fuel REAL");
        db.execSQL("ALTER TABLE journal ADD COLUMN hidden INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE journal ADD COLUMN deleted INTEGER DEFAULT 0");
        db.execSQL("CREATE UNIQUE INDEX journal_byd_id ON journal(source,source_id)");
    }
    synchronized long begin(String type, long now, double electric, double fuel) {
        return begin(type,now,electric,fuel,"настроенный тариф",null,0);
    }
    synchronized long begin(String type,long now,double electric,double fuel,String priceSource,Double socStart,double chargeLoss) {
        ContentValues v=new ContentValues();v.put("type",type);v.put("start",now);v.put("end",now);
        v.put("active",1);v.put("electric_price",electric);v.put("fuel_price",fuel);
        v.put("price_source",priceSource);if(socStart!=null)v.put("soc_start",socStart);v.put("charge_loss",chargeLoss);
        return getWritableDatabase().insertOrThrow("journal",null,v);
    }
    BatteryPriceHistory.Price tripPrice(long tripStart,double capacity,double fallback,boolean useHistory) {
        if(!useHistory)return new BatteryPriceHistory.Price(fallback,"настроенный тариф");
        List<BatteryPriceHistory.Charge> charges=new ArrayList<>();
        for(Entry e:list(0)) if(e.type.equals("charge")&&!e.active&&e.missing==0&&e.end<=tripStart&&e.cost()!=null&&e.recovered>0) {
            charges.add(new BatteryPriceHistory.Charge(e.end,e.recovered,e.cost(),e.socStart));
        }
        return BatteryPriceHistory.at(charges,tripStart,capacity,fallback);
    }
    synchronized void checkpoint(long id, TripAccumulator m, boolean active, String note) {
        ContentValues v=new ContentValues();v.put("end",m.end);v.put("km",m.km);v.put("used",m.usedKwh);v.put("recovered",m.recoveredKwh);
        v.put("moving",m.movingMs);v.put("observed",m.observedMs);v.put("missing",m.missingMs);v.put("power_missing",m.powerMissingMs);v.put("max",m.maxSpeed);
        if(m.fuel.startLitres==null)v.putNull("fuel_start");else v.put("fuel_start",m.fuel.startLitres);
        if(m.fuel.endLitres==null)v.putNull("fuel_end");else v.put("fuel_end",m.fuel.endLitres);
        v.put("fuel_invalid",m.fuel.invalid?1:0);
        if(m.electricity.startKwh==null)v.putNull("energy_start");else v.put("energy_start",m.electricity.startKwh);
        if(m.electricity.endKwh==null)v.putNull("energy_end");else v.put("energy_end",m.electricity.endKwh);
        v.put("energy_invalid",m.electricity.invalid?1:0);
        v.put("engine",m.engineSeen?1:0);v.put("active",active?1:0);v.put("note",note);
        getWritableDatabase().update("journal",v,"id=?",new String[]{Long.toString(id)});
    }
    synchronized void recoverInterrupted(){recoverInterruptedExcept(-1);}
    synchronized void recoverInterruptedExcept(long retained) {
        ContentValues v=new ContentValues();v.put("active",0);v.put("note","Запись прервана · сохранено до последней контрольной точки");
        // A missing second marks accounting incomplete without inventing unseen energy.
        getWritableDatabase().execSQL("UPDATE journal SET active=0, missing=missing+1000, fuel_end=NULL, energy_end=NULL, note='Запись прервана · сохранено до последней контрольной точки' WHERE active=1 AND id<>?",new Object[]{retained});
    }
    synchronized void correct(long id, Double litres, Double grid) {
        ContentValues v=new ContentValues(); if(litres==null)v.putNull("litres");else v.put("litres",litres);
        if(grid==null)v.putNull("grid");else v.put("grid",grid);
        getWritableDatabase().update("journal",v,"id=? AND active=0",new String[]{Long.toString(id)});
    }
    synchronized void delete(long id) {
        ContentValues v=new ContentValues();v.put("deleted",1);getWritableDatabase().update("journal",v,"id=? AND active=0",new String[]{Long.toString(id)});
    }
    synchronized void updatePurchase(long id, double amount, double price, Double odo, boolean full) {
        ContentValues v=new ContentValues();v.put("litres",amount);v.put("fuel_price",price);
        if(odo==null)v.putNull("odometer");else v.put("odometer",odo);v.put("full",full?1:0);
        getWritableDatabase().update("journal",v,"id=? AND active=0 AND type='fuel'",new String[]{Long.toString(id)});
    }
    synchronized void addPurchase(String type, long now, double amount, double price, Double odo, boolean full) {
        ContentValues v=new ContentValues();v.put("type",type);v.put("start",now);v.put("end",now);
        v.put("electric_price",type.equals("charge")?price:0);v.put("fuel_price",type.equals("fuel")?price:0);
        if(type.equals("fuel"))v.put("litres",amount);else v.put("grid",amount);
        if(odo!=null)v.put("odometer",odo);v.put("full",full?1:0);v.put("note","Внесено вручную");
        getWritableDatabase().insertOrThrow("journal",null,v);
    }
    /** Read-only compact snapshot for the visible EV widget; no history scan or imports. */
    synchronized double[] widgetTrip(long now) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT start,end,km FROM journal WHERE type='trip' AND active=1 AND hidden=0 AND deleted=0 AND source='launcher' ORDER BY start DESC LIMIT 1",null)){
            if(!c.moveToFirst()||now<c.getLong(1)||now-c.getLong(1)>120000)return null;
            return new double[]{Math.max(0,c.getLong(1)-c.getLong(0))/60000.,c.getDouble(2)};
        }
    }
    synchronized List<Entry> list(long since) {
        List<Entry> entries=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("journal",null,"start>=? AND hidden=0 AND deleted=0",new String[]{Long.toString(since)},null,null,"start DESC, id DESC")) {
            while(c.moveToNext()) {
                Entry e=new Entry();e.id=l(c,"id");e.type=t(c,"type");e.start=l(c,"start");e.end=l(c,"end");e.active=l(c,"active")==1;
                e.km=d(c,"km");e.used=d(c,"used");e.recovered=d(c,"recovered");e.moving=l(c,"moving");e.observed=l(c,"observed");e.missing=l(c,"missing");
                e.powerMissing=l(c,"power_missing");e.max=d(c,"max");e.engine=l(c,"engine")==1;e.electricPrice=d(c,"electric_price");e.fuelPrice=d(c,"fuel_price");
                e.energyStart=n(c,"energy_start");e.energyEnd=n(c,"energy_end");e.energyInvalid=l(c,"energy_invalid")==1;
                e.source=t(c,"source");e.nativeEnergy=n(c,"native_energy");e.nativeFuel=n(c,"native_fuel");
                e.socStart=n(c,"soc_start");e.priceSource=t(c,"price_source");e.chargeLoss=d(c,"charge_loss");
                e.fuelStart=n(c,"fuel_start");e.fuelEnd=n(c,"fuel_end");e.fuelInvalid=l(c,"fuel_invalid")==1;
                e.litres=n(c,"litres");e.grid=n(c,"grid");e.odometer=n(c,"odometer");e.full=l(c,"full")==1;e.note=t(c,"note");entries.add(e);
            }
        } return entries;
    }
    /** Idempotent authoritative history import. Local observations stay in SQLite as an archive. */
    synchronized int importNative(org.json.JSONArray rows,double electricPrice,double fuelPrice) throws org.json.JSONException {
        SQLiteDatabase db=getWritableDatabase();int changed=0;db.beginTransaction();
        try {
            for(int i=0;i<rows.length();i++){
                org.json.JSONArray r=rows.getJSONArray(i);
                long external=r.getLong(0),start=r.getLong(1)*1000,end=r.getLong(2)*1000;
                double km=r.getDouble(4);Double energy=r.isNull(5)?null:r.getDouble(5),fuel=r.isNull(6)?null:r.getDouble(6);
                if(external<0||start<946684800000L||end<start||end-start>7L*86400000||!Double.isFinite(km)||km<0||km>5000)continue;
                if(energy!=null&&(!Double.isFinite(energy)||Math.abs(energy)>1000))energy=null;
                if(fuel!=null&&(!Double.isFinite(fuel)||fuel<0||fuel>1000))fuel=null;
                Long existing=null;try(Cursor c=db.rawQuery("SELECT id FROM journal WHERE source='byd' AND source_id=?",new String[]{""+external})){if(c.moveToFirst())existing=c.getLong(0);}
                ContentValues v=new ContentValues();v.put("start",start);v.put("end",end);v.put("km",km);
                if(energy==null)v.putNull("native_energy");else v.put("native_energy",energy);
                if(fuel==null)v.putNull("native_fuel");else v.put("native_fuel",fuel);
                v.put("source","byd");v.put("source_id",external);v.put("hidden",r.getInt(7)!=0?1:0);
                v.put("type","trip");v.put("active",0);
                v.put("note","Штатный журнал BYD · № "+external+". Стоимость оценена по тарифам при импорте.");
                if(existing==null){v.put("electric_price",electricPrice);v.put("fuel_price",fuelPrice);v.put("price_source","тарифы при импорте истории BYD");db.insertOrThrow("journal",null,v);changed++;}
                else db.update("journal",v,"id=?",new String[]{""+existing});
                if(r.getInt(7)==0){
                    // Only replace observations predominantly covered by this real BYD session.
                    db.execSQL("UPDATE journal SET hidden=1 WHERE source='launcher' AND type='trip' AND active=0 AND end>start AND start<? AND end>? AND (min(end,?)-max(start,?))*1.0/(end-start)>=0.5",
                        new Object[]{end,start,end,start});
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return changed;
    }
    synchronized int archivedObservations(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM journal WHERE source='launcher' AND hidden=1",null)){return c.moveToFirst()?c.getInt(0):0;}}
    // Full-to-full consumption uses every intervening fill, not just the last one.
    static Double fullTankConsumption(List<Entry> newestFirst) {
        List<FuelLedger.Fill> fills=new ArrayList<>();
        for(Entry e:newestFirst) if(e.type.equals("fuel")) fills.add(new FuelLedger.Fill(e.litres,e.odometer,e.full));
        return FuelLedger.consumption(fills);
    }
    void exportCsv(Writer writer) throws IOException {
        writer.write("\ufeffТип;Начало Unix ms;Конец Unix ms;Км;Разряд кВт·ч;Возврат кВт·ч;Топливо л;Уточнённая энергия кВт·ч;Стоимость ₽;Пропуски мс;Комментарий;Счётчик топлива начало л;Счётчик топлива конец л;Источник топлива;Электричество поездки кВт·ч;Стоимость электричества ₽;Стоимость бензина ₽;Источник энергии;Тариф ₽ за кВт·ч;Источник тарифа;Счётчик BMS начало;Счётчик BMS конец;Источник записи;Баланс батареи BYD кВт·ч\n");
        for(Entry e:list(0)) writer.write(e.type+";"+e.start+";"+e.end+";"+e.km+";"+e.used+";"+e.recovered+";"+csv(e.fuelLitres())+";"+csv(e.grid)+";"+csv(e.cost())+";"+e.missing+";\""+e.note.replace("\"","\"\"")+"\";"+csv(e.fuelStart)+";"+csv(e.fuelEnd)+";"+e.fuelSource()+";"+csv(e.type.equals("trip")?e.electricityKwh():null)+";"+csv(e.type.equals("trip")?e.electricityCost():null)+";"+csv(e.fuelCost())+";"+(e.type.equals("trip")?e.electricitySource():"")+";"+e.electricPrice+";"+e.priceSource+";"+csv(e.energyStart)+";"+csv(e.energyEnd)+";"+e.source+";"+csv(e.nativeEnergy)+"\n");
    }
    private static String csv(Double n){return n==null?"":n.toString();}
    private static int i(Cursor c,String key){return c.getColumnIndexOrThrow(key);}
    private static long l(Cursor c,String k){return c.getLong(i(c,k));}
    private static double d(Cursor c,String k){return c.getDouble(i(c,k));}
    private static Double n(Cursor c,String k){return c.isNull(i(c,k))?null:d(c,k);}
    private static String t(Cursor c,String k){return c.getString(i(c,k));}
}
