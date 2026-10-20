package ru.logunov.bydsplit;

/** Optional HV voltage/current and odometer words; each missing field stays independent. */
final class StatisticsSensorDecoder {
    static Double decode(int field,int raw) {
        if(raw==65535||raw==1048575||raw==Integer.MAX_VALUE||raw<=-10000&&raw>=-10100)return null;
        if(field==1)return HvBatteryPower.current(raw);
        if(field!=0&&field!=2)throw new IllegalArgumentException("sensor index");
        double value=field==2?raw/10.:(double)raw;
        return value>=0&&value<=(field==0?1000:10000000)?Double.valueOf(value):null;
    }
}
