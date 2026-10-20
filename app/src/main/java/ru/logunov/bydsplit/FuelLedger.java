package ru.logunov.bydsplit;

import java.util.List;

/** Full-tank method: exclude the opening fill, include all fills up to closing. */
final class FuelLedger {
    static final class Fill {
        final Double litres, odometer;
        final boolean full;
        Fill(Double litres,Double odometer,boolean full){this.litres=litres;this.odometer=odometer;this.full=full;}
    }
    static Double consumption(List<Fill> newestFirst) {
        Fill end=null;double litres=0;
        for(Fill f:newestFirst) {
            if(end==null){if(f.full&&f.odometer!=null){if(f.litres==null)return null;end=f;litres=f.litres;}}
            else {
                if(f.full&&f.odometer!=null) {
                    double distance=end.odometer-f.odometer;
                    return distance>0?litres*100/distance:null;
                }
                if(f.litres==null)return null;
                litres+=f.litres;
            }
        }return null;
    }
}
