package ru.logunov.bydsplit;
public final class VehicleSessionStateTest {
    public static void main(String[] args){
        VehicleSessionState state=new VehicleSessionState();
        check(!state.update(100000,0,0.,false,false),"off created session");
        check(state.update(101000,2,0.,false,false)&&state.evidence,"READY at zero speed not recorded");
        check(state.update(901000,2,0.,false,false),"long parking ended session");
        check(state.update(902000,0,0.,false,false)&&!state.evidence,"short OFF not debounced");
        check(!state.update(991000,0,0.,false,false),"OFF not closed");
        check(state.update(992000,null,30.,false,false),"speed fallback");
        check(!state.update(993000,2,0.,false,true),"charge counted as trip");
        check(state.update(994000,null,0.,true,false),"engine fallback");
        TripAccumulator parked=new TripAccumulator();parked.start=100000;parked.end=130000;parked.missingMs=30000;
        parked.electricity.sample(1191.3);parked.electricity.sample(1191.3);
        near(parked.automaticElectricityKwh(),0);
        parked.electricity.sample(1191.4);near(parked.estimatedCost(21.5,0.,70,null),2.15);
        parked.electricity.loseClosingEdge();check(parked.automaticElectricityKwh()==null,"missing closing edge priced");
        System.out.println("Powered parking, OFF debounce, charge exclusion, BMS quantization and automatic parked cost passed");
    }
    private static void near(Double n,double expected){check(n!=null&&Math.abs(n-expected)<.000001,"wrong cost "+n);}
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
}
