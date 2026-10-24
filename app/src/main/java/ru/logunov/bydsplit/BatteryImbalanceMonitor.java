package ru.logunov.bydsplit;

/** User-selected observation scale, NOT verified BYD fault limits or an SOC diagnosis. */
final class BatteryImbalanceMonitor {
    static final int WARNING_MV=31,DIAGNOSTIC_MV=50,HIGH_MV=101;
    static final long CONFIRM_MS=60000,MAX_GAP_MS=10000;
    enum Level { UNAVAILABLE, BELOW_THRESHOLD, OBSERVING, WARNING, DIAGNOSTIC, HIGH }
    enum Rating {
        EXCELLENT("Отлично"),NORMAL("Норма"),WATCH("Допустимо, стоит наблюдать"),
        ELEVATED("Повышенный дисбаланс"),DIAGNOSTIC("Требуется диагностика и балансировка"),
        SIGNIFICANT("Существенный дисбаланс, возможны проблемы с ячейками");
        final String label;Rating(String label){this.label=label;}
    }
    static Rating rating(Integer mv) {
        if(mv==null||mv<0||mv>3000)return null;
        if(mv<=10)return Rating.EXCELLENT;
        if(mv<=20)return Rating.NORMAL;
        if(mv<=30)return Rating.WATCH;
        if(mv<50)return Rating.ELEVATED;
        if(mv<=100)return Rating.DIAGNOSTIC;
        return Rating.SIGNIFICANT;
    }
    private long warningSince=-1,diagnosticSince=-1,highSince=-1,lastAt=-1;
    private Integer delta;
    void accept(Integer value,long now) {
        if(rating(value)==null){reset();return;}
        if(lastAt>=0&&(now<=lastAt||now-lastAt>MAX_GAP_MS))reset();
        lastAt=now;delta=value;
        if(value>=WARNING_MV){if(warningSince<0)warningSince=now;}else warningSince=-1;
        if(value>=DIAGNOSTIC_MV){if(diagnosticSince<0)diagnosticSince=now;}else diagnosticSince=-1;
        if(value>=HIGH_MV){if(highSince<0)highSince=now;}else highSince=-1;
    }
    Level level(long now) {
        if(lastAt<0||now<lastAt||now-lastAt>MAX_GAP_MS)return Level.UNAVAILABLE;
        if(highSince>=0&&lastAt-highSince>=CONFIRM_MS)return Level.HIGH;
        if(diagnosticSince>=0&&lastAt-diagnosticSince>=CONFIRM_MS)return Level.DIAGNOSTIC;
        if(warningSince>=0&&lastAt-warningSince>=CONFIRM_MS)return Level.WARNING;
        return warningSince>=0?Level.OBSERVING:Level.BELOW_THRESHOLD;
    }
    Integer delta(long now){return level(now)==Level.UNAVAILABLE?null:delta;}
    long observedSeconds(long now){return warningSince<0||level(now)==Level.UNAVAILABLE?0:Math.max(0,(lastAt-warningSince)/1000);}
    void clear(){reset();}
    private void reset(){warningSince=-1;diagnosticSince=-1;highSince=-1;lastAt=-1;delta=null;}
}
