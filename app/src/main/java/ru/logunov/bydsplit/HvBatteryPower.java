package ru.logunov.bydsplit;

/** The float -1 is autoservice's missing-value marker, not measured regeneration. */
final class HvBatteryPower {
    static Double current(int raw) {
        if(raw==65535||raw==1048575||raw==Integer.MAX_VALUE)return null;
        double value=Float.intBitsToFloat(raw);
        return Double.isFinite(value)&&value!=-1.&&value>=-2000&&value<=2000?value:null;
    }
}
