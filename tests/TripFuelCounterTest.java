package ru.logunov.bydsplit;

public final class TripFuelCounterTest {
    public static void main(String[] args) {
        near(TripFuelCounter.decode(Float.floatToIntBits(302.4f)),302.4,.0001);
        for(int raw:new int[]{0,65535,1048575,-10011,-10013,Integer.MAX_VALUE,
                Float.floatToIntBits(-1f),Float.floatToIntBits(Float.NaN),Float.floatToIntBits(Float.POSITIVE_INFINITY)}) {
            check(TripFuelCounter.decode(raw)==null,"invalid word "+raw);
        }
        near(TripFuelCounter.readReply("Result: Parcel(00000000 43973333 '....')"),302.4,.0001);
        near(TripFuelCounter.readReply("Result: Parcel(\n  0x00000000: 00000000 43973333 00000000)"),302.4,.0001);
        check(TripFuelCounter.readReply("Result: Parcel(ffffd8e5 43973333)")==null,"nonzero status");
        check(TripFuelCounter.readReply("Service autoservice does not exist")==null,"absent service");
        TripFuelCounter normal=new TripFuelCounter();normal.sample(302.4);normal.sample(303.);normal.sample(305.2);
        near(normal.litres(),2.8,1e-9);
        TripFuelCounter ev=new TripFuelCounter();ev.sample(302.4);ev.sample(302.4);near(ev.litres(),0,0);
        TripFuelCounter missingStart=new TripFuelCounter();missingStart.sample(null);missingStart.sample(305.2);
        check(missingStart.litres()==null,"late sample stole opening edge");
        normal.sample(null);check(normal.litres()==null,"stale ending used");
        normal.sample(305.3);near(normal.litres(),2.9,1e-9);
        normal.loseClosingEdge();check(normal.litres()==null,"lost connection ending");
        TripFuelCounter reset=new TripFuelCounter();reset.sample(302.4);reset.sample(1.);reset.sample(303.);
        check(reset.litres()==null,"reset hidden by recovery");
        TripFuelCounter jump=new TripFuelCounter();jump.sample(302.4);jump.sample(400.);jump.sample(303.);
        check(jump.litres()==null,"jump hidden by recovery");
        near(TripFuelCounter.difference((double)302.4f,(double)305.2f),2.8,1e-9);
        check(TripFuelCounter.difference(300.,299.)==null,"negative litres");
        check(TripFuelCounter.difference(300.,361.)==null,"unbounded trip fuel");
        System.out.println("Fuel counter decoding, endpoints, missing data and resets passed");
    }
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void near(Double actual,double expected,double epsilon){check(actual!=null&&Math.abs(actual-expected)<=epsilon,actual+" != "+expected);}
}
