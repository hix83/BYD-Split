package ru.logunov.bydsplit;

import java.util.*;
import java.util.function.*;

/** BYDMate a36fa7b6 scan flow, adapted to Java. PolyForm Noncommercial; see NOTICE. */
final class DtcScanner {
    interface Device { void register() throws Exception; int send(byte[] payload)throws Exception; byte[] poll(long ms)throws Exception; void drain(); void close(); }
    interface Clock { long now(); void sleep(long ms)throws Exception; }
    static final class Result {
        final int address; String outcome="skipped";byte[] count,list,identity;
        Result(int address){this.address=address;}
    }
    static final class Scan {
        String state="DONE",reason="";final List<Result> results=new ArrayList<>();
    }
    private final Device device; private final Clock clock; private final Supplier<String> guard;
    private final Supplier<Integer> gear;private final BooleanSupplier ready;
    private long start;private int frames;
    private final boolean legacy;
    DtcScanner(Device device,Clock clock,Supplier<String> guard,Supplier<Integer> gear,BooleanSupplier ready){this(device,clock,guard,gear,ready,false);}
    DtcScanner(Device device,Clock clock,Supplier<String> guard,Supplier<Integer> gear,BooleanSupplier ready,boolean legacy){this.device=device;this.clock=clock;this.guard=guard;this.gear=gear;this.ready=ready;this.legacy=legacy;}
    private void gate() {
        String diagnosticGuard=guard.get();
        boolean absent=diagnosticGuard==null||diagnosticGuard.trim().isEmpty();
        if(absent&&!legacy)throw new IllegalStateException("guard_absent");
        if(!absent&&!DtcProtocol.GUARD.equals(diagnosticGuard))throw new IllegalStateException("diag_busy");
        if(!Integer.valueOf(1).equals(gear.get()))throw new IllegalStateException("not_park");
        if(!ready.getAsBoolean())throw new IllegalStateException("not_ready");
        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("cancelled");
        if(clock.now()-start<0||clock.now()-start>=90000)throw new IllegalStateException("budget");
    }
    Scan run(Consumer<Result> progress) {
        Scan scan=new Scan();start=clock.now();frames=0;Result current=null;
        try {
            gate();device.register();
            if(device.poll(2000)!=null)throw new IllegalStateException("passive_traffic");
            for(int address:DtcProtocol.ADDRESSES){
                current=new Result(address);scan.results.add(current);
                DtcProtocol.Match count=exchange(address,0x19,1,255);
                current.outcome=count.kind;current.count=count.uds;
                if("ok".equals(count.kind)&&DtcProtocol.count(count.uds)>0){
                    int mask=(count.uds[2]&4)!=0?13:9;
                    DtcProtocol.Match list=exchange(address,0x19,2,mask);
                    if("nrc".equals(list.kind))list=exchange(address,0x19,2,255);
                    current.outcome=list.kind;current.list=list.uds;
                    if("ok".equals(list.kind)&&!DtcProtocol.rows(list.uds).isEmpty()){
                        DtcProtocol.Match identity=exchange(address,0x22,0xf1,0x94);
                        if("ok".equals(identity.kind))current.identity=identity.uds;
                    }
                }
                progress.accept(current);current=null;
            }
            if(frames==0){scan.state="UNAVAILABLE";scan.reason="no_frames";}
        }catch(Exception error){scan.state="ABORTED";scan.reason=error instanceof IllegalStateException?error.getMessage():error.getClass().getSimpleName();}
        finally {device.close();}
        // Every unchecked block remains explicitly untested, including a scan stopped mid-address.
        Set<Integer> seen=new HashSet<>();for(Result result:scan.results)seen.add(result.address);
        for(int address:DtcProtocol.ADDRESSES)if(!seen.contains(address))scan.results.add(new Result(address));
        return scan;
    }
    private DtcProtocol.Match exchange(int address,int service,int sub,int parameter)throws Exception {
        gate();
        if(device.send(DtcProtocol.request(address,service,sub,parameter))!=0)throw new IllegalStateException("transport_rejected");
        long deadline=Math.min(start+90000,clock.now()+2500);
        while(clock.now()<deadline){
            byte[] raw=device.poll(deadline-clock.now());
            if(raw==null)break;
            frames++;
            DtcProtocol.Match match=DtcProtocol.match(raw,address,service,service==0x22?(sub<<8)|parameter:sub);
            if("other".equals(match.kind)){
                if(legacy)throw new IllegalStateException("foreign_traffic");
                continue;
            }
            if("pending".equals(match.kind))continue;
            clock.sleep(400);device.drain();return match;
        }
        return new DtcProtocol.Match("no_answer",null);
    }
}
