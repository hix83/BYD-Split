package ru.logunov.bydsplit;

import java.util.*;

public final class EvConsumptionTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    static void near(double actual,double expected){check(Math.abs(actual-expected)<1e-6);}
    static EvConsumption.Segment add(EvConsumption.Integrator i,long time,double speed,double watts,boolean engine){return i.add(time,speed,watts,null,true,engine,false,2);}
    public static void main(String[] args){
        EvConsumption.Integrator i=new EvConsumption.Integrator();
        check(add(i,1000,60,12000,false)==null);
        EvConsumption.Segment s=add(i,4000,60,12000,false);near(s.km,.05);near(s.kwh,.01);
        check(add(i,7000,60,50000,true)==null); // both transition edges are discarded
        check(add(i,10000,60,12000,false)==null);
        near(add(i,13000,60,12000,false).kwh,.01);
        check(i.add(16000,60.,12000.,null,false,false,false,2)==null);
        check(add(i,19000,60,12000,false)==null);
        check(i.add(22000,60.,12000.,null,true,false,true,2)==null);
        check(add(i,25000,60,12000,false)==null);
        check(i.add(28000,60.,12000.,null,true,false,false,null)==null);
        check(add(i,31000,60,12000,false)==null);
        check(add(i,45000,60,12000,false)==null); // long gap, then a new anchor
        near(add(i,48000,60,-6000,false).kwh,.0025); // signed energy across zero
        near(add(i,51000,60,-6000,false).kwh,-.005);
        i.reset();check(add(i,1000,0,1000,false)==null);
        s=add(i,4000,0,1000,false);near(s.km,0);near(s.kwh,1./1200); // electric idle loads
        check(i.add(7000,0.,null,null,true,false,false,2)==null);
        check(i.add(10000,Double.NaN,1000.,null,true,false,false,2)==null);
        check(i.add(13000,60.,Double.POSITIVE_INFINITY,null,true,false,false,2)==null);
        i.reset();i.add(1000,60.,12000.,100.,true,false,false,2);
        check(i.add(4000,60.,12000.,99.,true,false,false,2)==null); // odometer rollback
        check(i.add(4000,60.,12000.,99.,true,false,false,2)==null); // duplicate time
        check(i.add(2000,60.,12000.,99.,true,false,false,2)==null); // clock rollback
        EvConsumption.Average a=EvConsumption.average(Arrays.asList(new EvConsumption.Row(2,3,.6),new EvConsumption.Row(1,2,.2)));
        near(a.per100(),16);near(a.km,5);check(a.trips==2); // distance weighted, not mean of rates
        check(EvConsumption.average(Arrays.asList(new EvConsumption.Row(1,4,.8))).per100()==null);
        check(EvConsumption.average(Arrays.asList(new EvConsumption.Row(1,5,-.1))).per100()==null);
        a=EvConsumption.average(Arrays.asList(new EvConsumption.Row(2,60,12),new EvConsumption.Row(1,60,6)));
        near(a.km,100);near(a.per100(),16); // partial oldest chunk
        List<EvConsumption.Row> rows=new ArrayList<>();for(int n=0;n<12;n++)rows.add(new EvConsumption.Row(n+1,1,.2));
        a=EvConsumption.average(rows);check(a.trips==10);near(a.km,10);
        a=EvConsumption.average(Arrays.asList(new EvConsumption.Row(1,0,.1),new EvConsumption.Row(1,5,.8)));
        near(a.per100(),18);
        EvConsumption.Average baseline=new EvConsumption.Average(55.4,12.8,10);
        EvConsumption.Estimate estimate=EvConsumption.blend(new EvConsumption.Average(0,0,0),baseline);
        near(estimate.per100,12.8*100/55.4);near(estimate.ownShare,0);
        estimate=EvConsumption.blend(new EvConsumption.Average(4,.8,1),baseline);
        near(estimate.per100,baseline.per100());near(estimate.ownShare,0);
        estimate=EvConsumption.blend(new EvConsumption.Average(25,5,2),baseline);
        near(estimate.per100,20*.25+baseline.per100()*.75);near(estimate.ownShare,.25);
        estimate=EvConsumption.blend(new EvConsumption.Average(100,20,4),baseline);
        near(estimate.per100,20);near(estimate.ownShare,1);
        estimate=EvConsumption.blend(new EvConsumption.Average(5,1,1),new EvConsumption.Average(0,0,0));
        near(estimate.per100,20);near(estimate.ownShare,1);
        check(EvConsumption.blend(new EvConsumption.Average(0,0,0),new EvConsumption.Average(3,.6,1)).per100==null);
        check(EvConsumption.blend(new EvConsumption.Average(5,-1,1),new EvConsumption.Average(5,0,1)).per100==null);
        System.out.println("EV learning: engine transitions, unknown state, charging, gaps, regeneration, idle, counter rollback, weighted window and trip limit passed");
    }
}
