package ru.logunov.bydsplit;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import java.io.*;

/** Local journal. A live row is checkpointed and survives process termination. */
final class StatisticsStore extends SQLiteOpenHelper {
    static final class Entry {
        long id, start, end, moving, observed, missing;
        String type, note, priceSource;
        double km, used, recovered, max, electricPrice, fuelPrice;
        boolean active, engine, full, fuelInvalid, energyInvalid;
        Double litres, grid, odometer, fuelStart, fuelEnd, energyStart, energyEnd, socStart;
        double chargeLoss;
        TripAccumulator math() {
            TripAccumulator m = new TripAccumulator();
            m.start=start; m.end=end; m.km=km; m.usedKwh=used; m.recoveredKwh=recovered;
            m.movingMs=moving; m.observedMs=observed; m.missingMs=missing; m.maxSpeed=max; m.engineSeen=engine;
            m.electricity.startKwh=energyStart; m.electricity.endKwh=energyEnd; m.electricity.invalid=energyInvalid;
            return m;
        }
        Double automaticFuelLitres() { return fuelInvalid ? null : TripFuelCounter.difference(fuelStart, fuelEnd); }
        Double fuelLitres() { return litres != null ? litres : type.equals("trip") ? automaticFuelLitres() : null; }
        Double fuelCost() { Double amount = fuelLitres(); return amount == null ? null : amount * fuelPrice; }
        String fuelSource() { return litres != null ? "вручную" : automaticFuelLitres() != null ? "счётчик автомобиля" : "нет данных"; }
        Double electricityKwh() { return grid != null ? grid : math().automaticElectricityKwh(); }
        Double electricityCost() { return math().electricityCost(electricPrice, grid); }
        String electricitySource() {
            if(grid!=null)return "уточнено вручную";
            Double counter=TripEnergyCounter.difference(energyStart,energyEnd,energyInvalid,end-start);
            if(counter!=null&&counter>0)return "счётчик BMS";
            return math().automaticElectricityKwh()!=null?"интеграл мощности HV":"нет данных";
        }
        Double cost() {
            if (type.equals("fuel")) return litres == null ? null : litres * fuelPrice;
            if (type.equals("charge")) return missing > 0 && grid == null ? null : (grid == null ? recovered / (1 - chargeLoss / 100) : grid) * electricPrice;
            TripAccumulator trip = math();
            Double fuel = fuelLitres();
            if (fuel != null && fuel > 0) trip.engineSeen = true;
            return trip.estimatedCost(electricPrice, fuel, fuelPrice, grid);
        }
    }
    StatisticsStore(Context context) { this(context, "song_statistics.db"); }
    StatisticsStore(Context context, String name) { super(context.getApplicationContext(), name, null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE journal (id INTEGER PRIMARY KEY, type TEXT NOT NULL, start INTEGER, end INTEGER, active INTEGER DEFAULT 0, km REAL DEFAULT 0, used REAL DEFAULT 0, recovered REAL DEFAULT 0, moving INTEGER DEFAULT 0, observed INTEGER DEFAULT 0, missing INTEGER DEFAULT 0, max REAL DEFAULT 0, engine INTEGER DEFAULT 0, electric_price REAL, fuel_price REAL, litres REAL, grid REAL, odometer REAL, full INTEGER DEFAULT 0, note TEXT DEFAULT '', fuel_start REAL, fuel_end REAL, fuel_invalid INTEGER DEFAULT 0, energy_start REAL, energy_end REAL, energy_invalid INTEGER DEFAULT 0, soc_start REAL, price_source TEXT DEFAULT 'настроенный тариф', charge_loss REAL DEFAULT 0)");
        db.execSQL("CREATE INDEX journal_start ON journal(start)");
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
        v.put("moving",m.movingMs);v.put("observed",m.observedMs);v.put("missing",m.missingMs);v.put("max",m.maxSpeed);
        if(m.fuel.startLitres==null)v.putNull("fuel_start");else v.put("fuel_start",m.fuel.startLitres);
        if(m.fuel.endLitres==null)v.putNull("fuel_end");else v.put("fuel_end",m.fuel.endLitres);
        v.put("fuel_invalid",m.fuel.invalid?1:0);
        if(m.electricity.startKwh==null)v.putNull("energy_start");else v.put("energy_start",m.electricity.startKwh);
        if(m.electricity.endKwh==null)v.putNull("energy_end");else v.put("energy_end",m.electricity.endKwh);
        v.put("energy_invalid",m.electricity.invalid?1:0);
        v.put("engine",m.engineSeen?1:0);v.put("active",active?1:0);v.put("note",note);
        getWritableDatabase().update("journal",v,"id=?",new String[]{Long.toString(id)});
    }
    synchronized void recoverInterrupted() {
        ContentValues v=new ContentValues();v.put("active",0);v.put("note","Запись прервана · сохранено до последней контрольной точки");
        // A missing second marks accounting incomplete without inventing unseen energy.
        getWritableDatabase().execSQL("UPDATE journal SET active=0, missing=missing+1000, fuel_end=NULL, energy_end=NULL, note='Запись прервана · сохранено до последней контрольной точки' WHERE active=1");
    }
    synchronized void correct(long id, Double litres, Double grid) {
        ContentValues v=new ContentValues(); if(litres==null)v.putNull("litres");else v.put("litres",litres);
        if(grid==null)v.putNull("grid");else v.put("grid",grid);
        getWritableDatabase().update("journal",v,"id=? AND active=0",new String[]{Long.toString(id)});
    }
    synchronized void delete(long id) {
        getWritableDatabase().delete("journal", "id=? AND active=0", new String[]{Long.toString(id)});
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
    synchronized List<Entry> list(long since) {
        List<Entry> entries=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("journal",null,"start>=?",new String[]{Long.toString(since)},null,null,"start DESC, id DESC")) {
            while(c.moveToNext()) {
                Entry e=new Entry();e.id=l(c,"id");e.type=t(c,"type");e.start=l(c,"start");e.end=l(c,"end");e.active=l(c,"active")==1;
                e.km=d(c,"km");e.used=d(c,"used");e.recovered=d(c,"recovered");e.moving=l(c,"moving");e.observed=l(c,"observed");e.missing=l(c,"missing");
                e.max=d(c,"max");e.engine=l(c,"engine")==1;e.electricPrice=d(c,"electric_price");e.fuelPrice=d(c,"fuel_price");
                e.energyStart=n(c,"energy_start");e.energyEnd=n(c,"energy_end");e.energyInvalid=l(c,"energy_invalid")==1;
                e.socStart=n(c,"soc_start");e.priceSource=t(c,"price_source");e.chargeLoss=d(c,"charge_loss");
                e.fuelStart=n(c,"fuel_start");e.fuelEnd=n(c,"fuel_end");e.fuelInvalid=l(c,"fuel_invalid")==1;
                e.litres=n(c,"litres");e.grid=n(c,"grid");e.odometer=n(c,"odometer");e.full=l(c,"full")==1;e.note=t(c,"note");entries.add(e);
            }
        } return entries;
    }
    // Full-to-full consumption uses every intervening fill, not just the last one.
    static Double fullTankConsumption(List<Entry> newestFirst) {
        List<FuelLedger.Fill> fills=new ArrayList<>();
        for(Entry e:newestFirst) if(e.type.equals("fuel")) fills.add(new FuelLedger.Fill(e.litres,e.odometer,e.full));
        return FuelLedger.consumption(fills);
    }
    void exportCsv(Writer writer) throws IOException {
        writer.write("\ufeffТип;Начало Unix ms;Конец Unix ms;Км;Разряд кВт·ч;Возврат кВт·ч;Топливо л;Уточнённая энергия кВт·ч;Стоимость ₽;Пропуски мс;Комментарий;Счётчик топлива начало л;Счётчик топлива конец л;Источник топлива;Электричество поездки кВт·ч;Стоимость электричества ₽;Стоимость бензина ₽;Источник энергии;Тариф ₽ за кВт·ч;Источник тарифа;Счётчик BMS начало;Счётчик BMS конец\n");
        for(Entry e:list(0)) writer.write(e.type+";"+e.start+";"+e.end+";"+e.km+";"+e.used+";"+e.recovered+";"+csv(e.fuelLitres())+";"+csv(e.grid)+";"+csv(e.cost())+";"+e.missing+";\""+e.note.replace("\"","\"\"")+"\";"+csv(e.fuelStart)+";"+csv(e.fuelEnd)+";"+e.fuelSource()+";"+csv(e.type.equals("trip")?e.electricityKwh():null)+";"+csv(e.type.equals("trip")?e.electricityCost():null)+";"+csv(e.fuelCost())+";"+(e.type.equals("trip")?e.electricitySource():"")+";"+e.electricPrice+";"+e.priceSource+";"+csv(e.energyStart)+";"+csv(e.energyEnd)+"\n");
    }
    private static String csv(Double n){return n==null?"":n.toString();}
    private static int i(Cursor c,String key){return c.getColumnIndexOrThrow(key);}
    private static long l(Cursor c,String k){return c.getLong(i(c,k));}
    private static double d(Cursor c,String k){return c.getDouble(i(c,k));}
    private static Double n(Cursor c,String k){return c.isNull(i(c,k))?null:d(c,k);}
    private static String t(Cursor c,String k){return c.getString(i(c,k));}
}
