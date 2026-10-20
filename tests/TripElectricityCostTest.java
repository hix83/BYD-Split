package ru.logunov.bydsplit;

import java.util.*;

public final class TripElectricityCostTest {
    public static void main(String[] args) {
        String word=String.format("%08x",Float.floatToIntBits(1200.5f));
        near(TripEnergyCounter.readReply("Parcel(00000000 "+word+")"),1200.5);
        for(String reply:new String[]{"Parcel(00000000 00000000)","Parcel(ffffd8e5 "+word+")","Parcel(00000000 000fffff)","Parcel(00000000 7fc00000)","missing"})
            check(TripEnergyCounter.readReply(reply)==null,"invalid energy reply");
        TripAccumulator t=new TripAccumulator();
        t.add(1000000,60.,10000.,null,true);t.add(1030000,60.,10000.,null,true);
        t.electricity.sample(1000.);t.electricity.sample(1002.);
        near(t.automaticElectricityKwh(),2);near(t.estimatedCost(6,1.,60,null),72);
        t.missingMs=3000;near(t.estimatedCost(6,1.,60,null),72); // Boundary counter covers missing power ticks.
        t.electricity.loseClosingEdge();check(t.estimatedCost(6,1.,60,null)==null,"missing energy treated as free");
        near(t.estimatedCost(6,1.,60,2.),72); // Historical manual corrections survive.
        t.missingMs=0;t.electricity.sample(1001.); // counter reset: power integral is still available
        near(t.electricityCost(6,null),.5);
        TripAccumulator flat=new TripAccumulator();flat.add(1000000,60.,10000.,null,true);flat.add(1030000,60.,10000.,null,true);
        flat.electricity.sample(1000.);flat.electricity.sample(1000.);near(flat.electricityCost(6,null),.5);
        TripAccumulator recovery=new TripAccumulator();recovery.observedMs=60000;recovery.usedKwh=2;recovery.recoveredKwh=5;
        near(recovery.estimatedCost(6,1.,60,null),60); // Generated energy must not create a negative bill.
        recovery.recoveredKwh=1;near(recovery.estimatedCost(6,1.,60,null),66);
        check(new TripAccumulator().estimatedCost(6,0.,60,null)==null,"empty trip priced");
        check(TripEnergyCounter.difference(1000.,1100.,false,3000)==null,"counter spike priced");
        TripEnergyCounter late=new TripEnergyCounter();late.sample(null);late.sample(1000.);
        check(late.startKwh==null,"late boundary filled in");
        List<BatteryPriceHistory.Charge> charges=Arrays.asList(
            new BatteryPriceHistory.Charge(1000,10,60,50.),new BatteryPriceHistory.Charge(2000,10,100,50.),
            new BatteryPriceHistory.Charge(4000,10,1000,50.));
        near(BatteryPriceHistory.at(charges,3000,20,7).rate,8);
        near(BatteryPriceHistory.at(charges,1500,20,7).rate,6);
        near(BatteryPriceHistory.at(charges,500,20,7).rate,7);
        near(BatteryPriceHistory.at(charges,3000,0,7).rate,10);
        near(BatteryPriceHistory.at(charges,4001+BatteryPriceHistory.MAX_AGE_MS,20,7).rate,7);
        near(BatteryPriceHistory.at(Collections.emptyList(),3000,20,7).rate,7);
        System.out.println("Automatic full trip cost, energy sources, hybrid recovery and charging tariffs passed");
    }
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static void near(Double a,double b){check(a!=null&&Math.abs(a-b)<1e-7,a+" != "+b);}
}
