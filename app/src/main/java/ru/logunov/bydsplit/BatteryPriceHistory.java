package ru.logunov.bydsplit;

import java.util.*;

/** Pack-energy price inferred only from completed charges preceding a trip. */
final class BatteryPriceHistory {
    static final long MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000;
    static final class Charge {
        final long end;
        final double packKwh, cost;
        final Double socStart;
        Charge(long end, double packKwh, double cost, Double socStart) {
            this.end=end; this.packKwh=packKwh; this.cost=cost; this.socStart=socStart;
        }
    }
    static final class Price {
        final double rate;
        final String source;
        Price(double rate,String source){this.rate=rate;this.source=source;}
    }
    static Price at(List<Charge> charges,long tripStart,double capacity,double fallback) {
        List<Charge> sorted=new ArrayList<>(charges);sorted.sort(Comparator.comparingLong(c->c.end));
        Double price=null;long last=0;boolean weighted=false;
        for(Charge c:sorted) {
            if(c.end>tripStart||c.end<=0||!Double.isFinite(c.packKwh)||c.packKwh<=0||!Double.isFinite(c.cost)||c.cost<0)continue;
            double remain=capacity>0&&Double.isFinite(capacity)&&c.socStart!=null&&Double.isFinite(c.socStart)
                    &&c.socStart>=0&&c.socStart<=100 ? capacity*c.socStart/100 : 0;
            weighted=price!=null&&remain>0&&c.end-last<=MAX_AGE_MS;
            price=weighted?(remain*price+c.cost)/(remain+c.packKwh):c.cost/c.packKwh;
            last=c.end;
        }
        if(price==null||tripStart-last>MAX_AGE_MS)return new Price(fallback,"настроенный тариф");
        return new Price(price,weighted?"смесь предыдущих зарядок":"последняя зарядка");
    }
}
