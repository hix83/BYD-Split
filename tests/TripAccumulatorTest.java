package ru.logunov.bydsplit;

public final class TripAccumulatorTest {
    public static void main(String[] args) {
        TripAccumulator t=new TripAccumulator();long start=1000000;
        for(int i=0;i<=120;i++)t.add(start+i*30000,60.,10000.,null,false);
        near(t.km,60);near(t.usedKwh,10);near(t.consumption(),100./6);near(t.estimatedCost(6,0.,60,null),60);
        TripAccumulator crossing=new TripAccumulator();crossing.add(start,60.,10000.,null,false);crossing.add(start+30000,60.,-10000.,null,false);
        near(crossing.usedKwh,10000*.25/120/1000);near(crossing.recoveredKwh,crossing.usedKwh);near(crossing.netKwh(),0);
        TripAccumulator gap=new TripAccumulator();gap.add(start,60.,10000.,null,false);gap.add(start+120000,60.,10000.,null,false);
        near(gap.km,0);near(gap.usedKwh,0);if(gap.missingMs!=120000||gap.estimatedCost(6,null,60,null)!=null)throw new AssertionError("gap fabricated data");
        TripAccumulator hybrid=new TripAccumulator();hybrid.add(start,60.,10000.,null,true);hybrid.add(start+30000,60.,10000.,null,true);
        if(hybrid.estimatedCost(6,null,60,null)!=null)throw new AssertionError("missing fuel cost invented");
        near(hybrid.estimatedCost(6,1.,60,null),60.5);
        near(hybrid.estimatedCost(6,1.,60,2.),72);
        TripAccumulator unknown=new TripAccumulator();unknown.add(start,60.,null,null,false);unknown.add(start+30000,60.,null,null,false);
        if(unknown.missingMs!=30000||unknown.estimatedCost(6,null,60,null)!=null)throw new AssertionError("missing power treated as free");
        if(new TripAccumulator().consumption()!=null)throw new AssertionError("zero distance");
        TripAccumulator regen=new TripAccumulator();regen.add(start,30.,-2000.,null,false);regen.add(start+30000,30.,-2000.,null,false);
        near(regen.estimatedCost(6,0.,60,null),0);
        System.out.println("Trip integration, gaps and hybrid costs passed");
    }
    private static void near(Double a,double b){if(a==null||Math.abs(a-b)>1e-7)throw new AssertionError(a+" != "+b);}
}
