package ru.logunov.bydsplit;

public final class VehicleSleepStateTest {
    public static void main(String[] args) {
        VehicleSleepState s=new VehicleSleepState();
        check(s.sample(0,2,1)==VehicleSleepState.Mode.RUNNING,"READY parked must run");
        check(s.sample(7200000,2,1)==VehicleSleepState.Mode.RUNNING,"two-hour READY parking slept");
        check(s.sample(7200001,0,1)==VehicleSleepState.Mode.OFF_PENDING,"OFF not debounced");
        check(s.confirm(7290000)==VehicleSleepState.Mode.OFF_PENDING,"OFF confirmed too early");
        check(s.confirm(7290001)==VehicleSleepState.Mode.SLEEP,"confirmed OFF did not sleep");
        check(s.invalidate(7290001)==VehicleSleepState.Mode.SLEEP,"observer loss woke sleeping car");
        check(s.sample(7290001,null,null)==VehicleSleepState.Mode.SLEEP,"missing snapshot woke sleeping car");
        check(s.sample(7290001,0,1)==VehicleSleepState.Mode.SLEEP,"fresh OFF restarted debounce and polls");
        check(s.sample(7290002,0,2)==VehicleSleepState.Mode.CHARGING,"AC connected while asleep not resumed");
        check(s.confirm(9000000)==VehicleSleepState.Mode.CHARGING,"paused/missing current AC session lost");
        check(s.sample(9000001,0,3)==VehicleSleepState.Mode.CHARGING,"DC charging slept");
        check(s.sample(9000002,0,4)==VehicleSleepState.Mode.CHARGING,"combined cable slept");
        check(s.sample(9000003,0,1)==VehicleSleepState.Mode.OFF_PENDING,"unplug did not restart confirmation");
        check(s.sample(9000004,2,1)==VehicleSleepState.Mode.RUNNING,"READY did not wake immediately");
        for(Integer power:new Integer[]{null,-10011,65535,3})
            check(s.sample(10000000,power,1)==VehicleSleepState.Mode.UNKNOWN,"unknown power interpreted as OFF");
        for(Integer cable:new Integer[]{null,0,-10011,65535,5})
            check(s.sample(10000001,0,cable)==VehicleSleepState.Mode.UNKNOWN,"unknown cable interpreted as unplugged");
        check(s.sample(10000002,1,1)==VehicleSleepState.Mode.UNKNOWN,"ACC interpreted as OFF");
        s.sample(11000000,0,1);
        check(s.invalidate(11001000)==VehicleSleepState.Mode.UNKNOWN,"lost observer kept OFF confirmation");
        check(s.confirm(12000000)==VehicleSleepState.Mode.UNKNOWN,"lost telemetry manufactured sleep");
        s.sample(13000000,0,1);
        check(s.confirm(12900000)==VehicleSleepState.Mode.OFF_PENDING,"backwards clock confirmed OFF");
        check(s.confirm(12989999)==VehicleSleepState.Mode.OFF_PENDING,"backwards clock kept old deadline");
        // Exercise the existing energy accumulator under the actual sleep policy, not a mock cost formula.
        TripAccumulator parked=new TripAccumulator(),charge=new TripAccumulator();
        for(long at=1000;at<=3601000;at+=3000){
            check(s.sample(at,2,1)==VehicleSleepState.Mode.RUNNING,"parked sample gated");
            parked.add(at,0.,600.,null,false);
            check(s.sample(at,0,2)==VehicleSleepState.Mode.CHARGING,"screen-off AC sample gated");
            charge.add(at,0.,-2000.,null,false);
        }
        near(parked.usedKwh,.6,"parked READY energy lost");
        near(parked.electricityCost(21.5,null),12.90,"parked cost changed");
        near(charge.recoveredKwh,2.,"charging energy lost");
        check(parked.km==0&&charge.km==0,"stationary energy manufactured mileage");
        System.out.println("VehicleSleepStateTest PASS: parked READY, OFF debounce, AC/DC/paused charging, unplug, wake, missing values and clock reset");
    }
    private static void near(double value,double expected,String why){check(Math.abs(value-expected)<.000001,why);}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
