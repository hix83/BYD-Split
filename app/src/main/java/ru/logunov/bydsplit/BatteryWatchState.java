package ru.logunov.bydsplit;

/** Shared monitoring session; opening/closing pages never changes this state. */
final class BatteryWatchState {
    private final BatteryImbalanceMonitor monitor=new BatteryImbalanceMonitor();
    private boolean running;
    private BatteryImbalanceMonitor.Level dismissed=BatteryImbalanceMonitor.Level.UNAVAILABLE;
    void start(){running=true;monitor.clear();dismissed=BatteryImbalanceMonitor.Level.UNAVAILABLE;}
    void stop(){running=false;monitor.clear();dismissed=BatteryImbalanceMonitor.Level.UNAVAILABLE;}
    void accept(Integer delta,long at){if(running)monitor.accept(delta,at);}
    void dismiss(long now){dismissed=monitor.level(now);}
    Snapshot snapshot(long now) {
        BatteryImbalanceMonitor.Level level=running?monitor.level(now):BatteryImbalanceMonitor.Level.UNAVAILABLE;
        if(level.ordinal()<BatteryImbalanceMonitor.Level.WARNING.ordinal())dismissed=BatteryImbalanceMonitor.Level.UNAVAILABLE;
        boolean warning=level.ordinal()>=BatteryImbalanceMonitor.Level.WARNING.ordinal();
        return new Snapshot(running,level,monitor.delta(now),monitor.observedSeconds(now),warning&&level.ordinal()>dismissed.ordinal());
    }
    static final class Snapshot {
        final boolean running,showBanner;
        final BatteryImbalanceMonitor.Level level;
        final Integer delta;
        final long seconds;
        Snapshot(boolean running,BatteryImbalanceMonitor.Level level,Integer delta,long seconds,boolean showBanner){
            this.running=running;this.level=level;this.delta=delta;this.seconds=seconds;this.showBanner=showBanner;
        }
        String message() {
            if(!running)return "Наблюдение за батареей остановлено";
            BatteryImbalanceMonitor.Rating rating=BatteryImbalanceMonitor.rating(delta);
            String prefix="Разброс BMS · "+delta+" мВ · ";
            switch(level) {
                case BELOW_THRESHOLD:return prefix+rating.label;
                case OBSERVING:return prefix+rating.label+" · подтверждаем превышение ("+seconds+" / 60 с)";
                case WARNING:return prefix+rating.label+". Разброс более 30 мВ держится не менее минуты. Стоит проверить после снижения нагрузки.";
                case DIAGNOSTIC:return prefix+rating.label+". Разброс не менее 50 мВ держится не менее минуты.";
                case HIGH:return prefix+rating.label+". Разброс более 100 мВ держится не менее минуты.";
                default:return "Разброс BMS · нет свежих показаний";
            }
        }
    }
}
