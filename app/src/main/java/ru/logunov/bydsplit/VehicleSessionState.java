package ru.logunov.bydsplit;

/** A powered vehicle consumes while parked too. Confirm OFF, not five minutes of zero speed. */
final class VehicleSessionState {
    static final long OFF_DELAY_MS=90000;
    private long lastEvidence;
    boolean running, evidence;
    void resume(long lastOn){running=true;lastEvidence=lastOn;}
    boolean update(long now,Integer powerState,Double speed,boolean engine,boolean charging){
        evidence=!charging&&(Integer.valueOf(2).equals(powerState)||engine||speed!=null&&speed>1);
        if(evidence){lastEvidence=now;running=true;}
        else if(charging||running&&(now<lastEvidence||now-lastEvidence>=OFF_DELAY_MS))running=false;
        return running;
    }
}
