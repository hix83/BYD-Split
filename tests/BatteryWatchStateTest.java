package ru.logunov.bydsplit;

public final class BatteryWatchStateTest {
    public static void main(String[] args){
        BatteryWatchState state=new BatteryWatchState();
        state.accept(100,1000);check(!state.snapshot(1000).running,"stopped session ignores samples");
        state.start();
        for(long t=1000;t<=61000;t+=3000)state.accept(31,t);
        BatteryWatchState.Snapshot battery=state.snapshot(61000),settings=state.snapshot(61000),climate=state.snapshot(61000);
        check(battery.showBanner&&settings.showBanner&&climate.showBanner,"same confirmed warning on every screen");
        check(settings.level==BatteryImbalanceMonitor.Level.WARNING&&settings.seconds==60,"page reads do not reset timer");
        state.dismiss(61000);check(!state.snapshot(61000).showBanner,"dismiss hides current episode");
        state.accept(31,64000);check(!state.snapshot(64000).showBanner&&state.snapshot(64000).running,"dismiss does not stop monitoring or repeat banner");
        for(long t=67000;t<=127000;t+=3000)state.accept(101,t);
        check(state.snapshot(127000).showBanner&&state.snapshot(127000).level==BatteryImbalanceMonitor.Level.HIGH,"escalation reappears despite warning dismissal");
        state.dismiss(127000);check(!state.snapshot(127000).showBanner,"high severity can also be dismissed");
        state.accept(6,130000);check(!state.snapshot(130000).showBanner,"recovery clears episode");
        for(long t=133000;t<=193000;t+=3000)state.accept(31,t);
        check(state.snapshot(193000).showBanner,"new confirmed episode is not silenced forever");
        check(!state.snapshot(203001).showBanner&&state.snapshot(203001).delta==null,"stale alarm disappears, never presented as current");
        state.accept(null,204000);state.accept(100,207000);
        check(!state.snapshot(207000).showBanner,"loss of valid telemetry resets confirmation");
        state.stop();check(!state.snapshot(207000).running&&!state.snapshot(207000).showBanner,"service stop clears global state");
        state.start();state.accept(100,210000);check(!state.snapshot(210000).showBanner,"service restart cannot inherit old minute");
        state.start();for(long t=1000;t<=61000;t+=3000)state.accept(31,t);state.dismiss(61000);
        for(long t=64000;t<=124000;t+=3000)state.accept(50,t);
        check(state.snapshot(124000).showBanner&&state.snapshot(124000).level==BatteryImbalanceMonitor.Level.DIAGNOSTIC,"diagnostic escalation after a dismissed elevated warning");
        state.accept(101,125000);check(state.snapshot(125000).message().contains("Существенный дисбаланс"),"live scale updates immediately while stronger alarm is confirming");
        state.dismiss(125000);for(long t=127000;t<=187000;t+=3000)state.accept(100,t);
        check(!state.snapshot(187000).showBanner,"100 stays in same diagnostic band, no repeat");
        for(long t=190000;t<=250000;t+=3000)state.accept(101,t);
        check(state.snapshot(250000).showBanner,"over 100 is a fresh higher-severity banner");
        System.out.println("17 background battery watch checks passed");
    }
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
}
