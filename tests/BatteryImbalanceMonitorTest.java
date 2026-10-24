package ru.logunov.bydsplit;

public final class BatteryImbalanceMonitorTest {
    public static void main(String[] args) {
        BatteryImbalanceMonitor m=new BatteryImbalanceMonitor();
        check(m.level(0)==BatteryImbalanceMonitor.Level.UNAVAILABLE,"missing is not balanced");
        m.accept(6,1000);check(m.level(1000)==BatteryImbalanceMonitor.Level.BELOW_THRESHOLD,"small live spread");
        m.accept(30,4000);check(m.level(4000)==BatteryImbalanceMonitor.Level.BELOW_THRESHOLD,"below warning threshold");
        m.accept(100,7000);check(m.level(7000)==BatteryImbalanceMonitor.Level.OBSERVING,"spike does not notify");
        m.accept(6,10000);check(m.level(10000)==BatteryImbalanceMonitor.Level.BELOW_THRESHOLD,"spike clears");
        for(long t=13000;t<=70000;t+=3000)m.accept(31,t);
        check(m.level(73000)==BatteryImbalanceMonitor.Level.OBSERVING,"timer alone cannot confirm missing 60th second");
        m.accept(31,73000);check(m.level(73000)==BatteryImbalanceMonitor.Level.WARNING,"60 seconds confirmed by fresh samples");
        m.accept(101,76000);check(m.level(76000)==BatteryImbalanceMonitor.Level.WARNING,"high level needs its own duration");
        for(long t=79000;t<=136000;t+=3000)m.accept(101,t);
        check(m.level(136000)==BatteryImbalanceMonitor.Level.HIGH,"persistent high spread");
        check(m.level(146001)==BatteryImbalanceMonitor.Level.UNAVAILABLE,"stale warning hidden");
        m.accept(100,149000);check(m.level(149000)==BatteryImbalanceMonitor.Level.OBSERVING,"gap resets confirmation");
        m.accept(null,150000);check(m.delta(150000)==null,"missing sample resets assessment");
        m.accept(100,151000);m.accept(0,154000);check(m.level(154000)==BatteryImbalanceMonitor.Level.BELOW_THRESHOLD,"genuine zero spread accepted");
        m.clear();check(m.level(154000)==BatteryImbalanceMonitor.Level.UNAVAILABLE,"page close clears monitor");
        m.accept(-1,160000);check(m.level(160000)==BatteryImbalanceMonitor.Level.UNAVAILABLE,"sentinel rejected");
        int[] boundaries={0,10,11,20,21,30,31,49,50,100,101,3000};
        BatteryImbalanceMonitor.Rating[] expected={BatteryImbalanceMonitor.Rating.EXCELLENT,BatteryImbalanceMonitor.Rating.EXCELLENT,
            BatteryImbalanceMonitor.Rating.NORMAL,BatteryImbalanceMonitor.Rating.NORMAL,BatteryImbalanceMonitor.Rating.WATCH,BatteryImbalanceMonitor.Rating.WATCH,
            BatteryImbalanceMonitor.Rating.ELEVATED,BatteryImbalanceMonitor.Rating.ELEVATED,BatteryImbalanceMonitor.Rating.DIAGNOSTIC,BatteryImbalanceMonitor.Rating.DIAGNOSTIC,
            BatteryImbalanceMonitor.Rating.SIGNIFICANT,BatteryImbalanceMonitor.Rating.SIGNIFICANT};
        for(int i=0;i<boundaries.length;i++)check(BatteryImbalanceMonitor.rating(boundaries[i])==expected[i],"scale boundary "+boundaries[i]);
        m.clear();for(long t=1000;t<=61000;t+=3000)m.accept(100,t);
        check(m.level(61000)==BatteryImbalanceMonitor.Level.DIAGNOSTIC,"exact 100 is diagnostic, not over 100");
        System.out.println("User scale boundaries and persistent battery spread checks passed");
    }
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
