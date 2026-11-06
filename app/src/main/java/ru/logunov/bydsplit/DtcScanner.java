package ru.logunov.bydsplit;

import java.util.*;
import java.util.function.*;

/** BYDMate a36fa7b6 scan flow, adapted to Java. PolyForm Noncommercial; see NOTICE. */
final class DtcScanner {
    interface Device { void register() throws Exception; int send(byte[] payload)throws Exception; byte[] poll(long ms)throws Exception; void drain(); void close(); }
    interface Clock { long now(); void sleep(long ms)throws Exception; }
    static final class Result {
        final int address; String outcome="skipped",nameOutcome="not_requested";byte[] count,list,identity,name;
        final List<Snapshot> snapshots=new ArrayList<>();
        Result(int address){this.address=address;}
    }
    static final class Snapshot {
        final String code,outcome;final byte[] uds;
        Snapshot(String code,String outcome,byte[] uds){this.code=code;this.outcome=outcome;this.uds=uds;}
    }
    static final class Scan {
        String state="DONE",reason="",extrasReason="";final List<Result> results=new ArrayList<>();
    }
    private final Device device; private final Clock clock; private final Supplier<String> guard;
    private final Supplier<Integer> gear;private final BooleanSupplier ready;
    private long start,extraDeadline=Long.MAX_VALUE;private int frames;
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
        Scan scan=new Scan();start=clock.now();frames=0;extraDeadline=Long.MAX_VALUE;Result current=null;
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
            else readExtras(scan);
        }catch(Exception error){scan.state="ABORTED";scan.reason=error instanceof IllegalStateException?error.getMessage():error.getClass().getSimpleName();}
        finally {device.close();}
        // Every unchecked block remains explicitly untested, including a scan stopped mid-address.
        Set<Integer> seen=new HashSet<>();for(Result result:scan.results)seen.add(result.address);
        for(int address:DtcProtocol.ADDRESSES)if(!seen.contains(address))scan.results.add(new Result(address));
        return scan;
    }
    /** Optional read-only names/snapshots: a failure must not discard a completed code scan. */
    private void readExtras(Scan scan){
        extraDeadline=Math.min(start+90000,clock.now()+35000);
        try{
            for(Result r:scan.results)if(!DtcProtocol.rows(r.list).isEmpty()){
                extraGate();DtcProtocol.Match name=exchange(r.address,0x22,0xf1,0x97);
                r.nameOutcome=name.kind;if("ok".equals(name.kind))r.name=name.uds;
            }
            // One code from every responding block first; then the remaining visible codes.
            int requests=0;
            for(int round=0;round<30;round++){
                boolean any=false;
                for(Result r:scan.results){List<DtcProtocol.Row> codes=DtcProtocol.rows(r.list);if(round>=codes.size())continue;
                    if(requests++>=30)return;any=true;extraGate();String code=codes.get(round).code;
                    DtcProtocol.Match snapshot=exchangePayload(r.address,0x19,4,DtcProtocol.snapshotRequest(r.address,code),code);
                    r.snapshots.add(new Snapshot(code,snapshot.kind,snapshot.uds));
                }
                if(!any)break;
            }
            for(Result r:scan.results)if("not_requested".equals(r.nameOutcome)&&!"skipped".equals(r.outcome)&&!"gw_timeout".equals(r.outcome)&&!"no_answer".equals(r.outcome)){
                extraGate();DtcProtocol.Match name=exchange(r.address,0x22,0xf1,0x97);r.nameOutcome=name.kind;if("ok".equals(name.kind))r.name=name.uds;
            }
        }catch(Exception e){scan.extrasReason=e instanceof IllegalStateException?e.getMessage():e.getClass().getSimpleName();}
        finally{extraDeadline=Long.MAX_VALUE;}
    }
    private void extraGate(){gate();if(clock.now()>=extraDeadline)throw new IllegalStateException("extras_budget");}
    private DtcProtocol.Match exchange(int address,int service,int sub,int parameter)throws Exception {
        return exchangePayload(address,service,service==0x22?(sub<<8)|parameter:sub,DtcProtocol.request(address,service,sub,parameter),null);
    }
    private DtcProtocol.Match exchangePayload(int address,int service,int sub,byte[] request,String snapshot)throws Exception {
        gate();if(extraDeadline!=Long.MAX_VALUE)extraGate();
        if(device.send(request)!=0)throw new IllegalStateException("transport_rejected");
        long deadline=Math.min(extraDeadline,Math.min(start+90000,clock.now()+2500));
        while(clock.now()<deadline){
            byte[] raw=device.poll(deadline-clock.now());
            if(raw==null)break;
            frames++;
            DtcProtocol.Match match=snapshot==null?DtcProtocol.match(raw,address,service,sub):DtcProtocol.snapshotMatch(raw,address,snapshot);
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
