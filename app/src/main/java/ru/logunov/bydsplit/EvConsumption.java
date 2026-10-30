package ru.logunov.bydsplit;

import java.util.*;

/** Observed engine-off intervals only. Signed HV energy includes regenerative braking. */
final class EvConsumption {
    static final double MIN_KM=5, WINDOW_KM=100;
    static final int MAX_TRIPS=10;
    static final long MAX_AGE_MS=30L*24*60*60*1000;
    static final class Segment {
        final double km,kwh;final long ms;
        Segment(double km,double kwh,long ms){this.km=km;this.kwh=kwh;this.ms=ms;}
    }
    static final class Row {
        final long trip;final double km,kwh;
        Row(long trip,double km,double kwh){this.trip=trip;this.km=km;this.kwh=kwh;}
    }
    static final class Average {
        final double km,kwh;final int trips;
        Average(double km,double kwh,int trips){this.km=km;this.kwh=kwh;this.trips=trips;}
        Double per100(){double value=kwh*100/km;return km+1e-6>=MIN_KM&&Double.isFinite(value)&&value>=1&&value<=100?value:null;}
    }
    static Average average(List<Row> newestFirst){
        double km=0,kwh=0;Set<Long> trips=new HashSet<>();
        for(Row row:newestFirst){
            if(!Double.isFinite(row.km)||!Double.isFinite(row.kwh)||row.km<0)continue;
            if(!trips.contains(row.trip)&&trips.size()>=MAX_TRIPS)break;
            trips.add(row.trip);
            double fraction=row.km>0?Math.min(1,(WINDOW_KM-km)/row.km):1;
            km+=row.km*fraction;kwh+=row.kwh*fraction;
            if(km>=WINDOW_KM-1e-6)break;
        }
        return new Average(km,kwh,trips.size());
    }
    static final class Integrator {
        private long previous;
        private Double speed,watts,odometer;
        void reset(){previous=0;speed=null;watts=null;odometer=null;}
        Segment add(long now,Double nextSpeed,Double nextWatts,Double nextOdometer,
                    boolean engineKnown,boolean engine,boolean plugged,Integer powerState){
            if(!engineKnown||engine||plugged||!Integer.valueOf(2).equals(powerState)
                    ||!valid(nextSpeed,0,250)||!valid(nextWatts,-300000,300000)) {reset();return null;}
            Segment result=null;
            long dt=now-previous;
            if(previous>0&&dt>0&&dt<=10000){
                double hours=dt/3600000.;
                boolean odoOk=odometer==null||nextOdometer==null||!Double.isFinite(nextOdometer)||!Double.isFinite(odometer)
                    ||nextOdometer>=odometer&&nextOdometer-odometer<=Math.max(.3,250*hours+.2);
                if(odoOk)result=new Segment((speed+nextSpeed)*.5*hours,(watts+nextWatts)*.5*hours/1000,dt);
            }
            previous=now;speed=nextSpeed;watts=nextWatts;odometer=nextOdometer;
            return result;
        }
        private boolean valid(Double value,double min,double max){return value!=null&&Double.isFinite(value)&&value>=min&&value<=max;}
    }
}
