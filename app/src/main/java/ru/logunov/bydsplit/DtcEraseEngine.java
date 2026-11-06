package ru.logunov.bydsplit;

import java.util.*;
import java.util.function.*;

/** BYDMate 4.2.0 clear/reread flow adapted to Song. PolyForm Noncommercial; see NOTICE. */
final class DtcEraseEngine {
    static final long BUDGET=120000;
    static final class Result {
        final int address;String confirm="not_run",reason="";Integer nrc;DtcScanner.Result reread;
        Result(int address){this.address=address;}
    }
    static final class Run {String state="DONE",reason="";final List<Result> results=new ArrayList<>();}
    private final DtcScanner.Device device;private final DtcScanner.Clock clock;
    private final Supplier<String> guard;private final Supplier<Integer> gear;private final BooleanSupplier ready;
    private final DoubleSupplier speed;private final boolean legacy;
    private final List<byte[]> ours=new ArrayList<>();private long start;private Result currentClear;
    DtcEraseEngine(DtcScanner.Device device,DtcScanner.Clock clock,Supplier<String> guard,Supplier<Integer> gear,
                   BooleanSupplier ready,DoubleSupplier speed,boolean legacy){
        this.device=device;this.clock=clock;this.guard=guard;this.gear=gear;this.ready=ready;this.speed=speed;this.legacy=legacy;
    }
    private void gate(){
        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("cancelled");
        String value=guard.get();boolean absent=value==null||value.trim().isEmpty();
        if(absent&&!legacy)throw new IllegalStateException("guard_absent");
        if(!absent&&!DtcProtocol.GUARD.equals(value))throw new IllegalStateException("diag_busy");
        if(!Integer.valueOf(1).equals(gear.get()))throw new IllegalStateException("not_park");
        if(!ready.getAsBoolean())throw new IllegalStateException("not_ready");
        double kmh=speed.getAsDouble();if(!Double.isFinite(kmh)||kmh!=0)throw new IllegalStateException("speed_unknown_or_moving");
        if(clock.now()-start<0||clock.now()-start>=BUDGET)throw new IllegalStateException("budget");
    }
    Run run(int[] addresses,Consumer<Result> progress){
        Run run=new Run();start=clock.now();ours.clear();
        try{
            Set<Integer> unique=new HashSet<>();
            if(addresses==null||addresses.length==0||addresses.length>DtcProtocol.ADDRESSES.length)throw new IllegalStateException("invalid_targets");
            for(int address:addresses){if(!DtcProtocol.addressAllowed(address)||!unique.add(address))throw new IllegalStateException("invalid_targets");run.results.add(new Result(address));}
            gate();device.register();if(device.poll(2000)!=null)throw new IllegalStateException("passive_traffic");
            for(int i=0;i<run.results.size();i++){
                Result result=run.results.get(i);if(i>0)clock.sleep(1000);
                gate();quiet();gate(); // No write was attempted if a gate or passive traffic stops us.
                currentClear=result;
                DtcProtocol.Match reply=exchange(result.address,0x14,-1,0,true);
                result.reason=reply.kind;
                if("ok".equals(reply.kind)){result.confirm="confirmed";result.reason="";}
                else if("nrc".equals(reply.kind)){result.confirm="refused";result.nrc=reply.uds[2]&255;}
                progress.accept(result);
            }
            clock.sleep(3000);
            for(Result result:run.results){gate();result.reread=readCodes(result.address);progress.accept(result);}
        }catch(Exception error){
            run.state="ABORTED";run.reason=error instanceof IllegalStateException?error.getMessage():error.getClass().getSimpleName();
            for(Result r:run.results)if(r.reason.isEmpty()&&!"confirmed".equals(r.confirm)&&!"refused".equals(r.confirm))r.reason=run.reason;
        }finally{device.close();}
        return run;
    }
    private DtcScanner.Result readCodes(int address)throws Exception{
        DtcScanner.Result result=new DtcScanner.Result(address);
        DtcProtocol.Match count=exchange(address,0x19,1,255,false);result.outcome=count.kind;result.count=count.uds;
        if("ok".equals(count.kind)&&DtcProtocol.count(count.uds)>0){
            int mask=(count.uds[2]&4)!=0?13:9;
            DtcProtocol.Match list=exchange(address,0x19,2,mask,false);
            if("nrc".equals(list.kind))list=exchange(address,0x19,2,255,false);
            result.outcome=list.kind;result.list=list.uds;
        }
        return result;
    }
    private DtcProtocol.Match exchange(int address,int service,int sub,int parameter,boolean clear)throws Exception{
        gate();quiet();gate();
        byte[] request=clear?DtcProtocol.clearRequest(address):DtcProtocol.request(address,service,sub,parameter);
        if(clear){currentClear.confirm="unconfirmed";currentClear.reason="no_answer";}
        if(device.send(request)!=0)throw new IllegalStateException("transport_rejected");
        long deadline=Math.min(start+BUDGET,clock.now()+(clear?6000:2500));
        while(clock.now()<deadline){
            gate();byte[] raw=device.poll(Math.min(250,deadline-clock.now()));if(raw==null)continue;
            DtcProtocol.Match reply=DtcProtocol.match(raw,address,service,sub);
            if("other".equals(reply.kind)){if(!duplicate(raw))throw new IllegalStateException("foreign_traffic");continue;}
            ours.add(raw.clone());if("pending".equals(reply.kind))continue;
            clock.sleep(400);gate();quiet();return reply;
        }
        return new DtcProtocol.Match("no_answer",null);
    }
    private boolean duplicate(byte[] frame){
        for(byte[] previous:ours){int length=previous.length>=7?7+(previous[3]&255):previous.length;
            if(previous.length>=length&&frame.length>=length&&Arrays.equals(Arrays.copyOf(previous,length),Arrays.copyOf(frame,length)))return true;}
        return false;
    }
    private void quiet()throws Exception{
        for(byte[] raw;(raw=device.poll(1))!=null;){gate();if(!duplicate(raw))throw new IllegalStateException("foreign_traffic");}
    }
    static boolean verified(DtcScanner.Result result){
        if(result==null||!"ok".equals(result.outcome))return false;
        int count=DtcProtocol.count(result.count);if(count==0)return true;
        return count>0&&result.list!=null&&result.list.length>=7&&(result.list.length-3)%4==0&&!DtcProtocol.rows(result.list).isEmpty();
    }
    static String verdict(Result r){
        if("not_run".equals(r.confirm))return "Не отправлено";
        if("refused".equals(r.confirm))return "Блок отказал"+(r.nrc==null?"":String.format(Locale.ROOT," · NRC 0x%02X",r.nrc));
        if(!verified(r.reread))return "confirmed".equals(r.confirm)?"Стирание подтверждено, повторное чтение не подтверждено":"Результат неизвестен · выполните новую проверку";
        boolean empty=DtcProtocol.count(r.reread.count)==0;
        if("confirmed".equals(r.confirm))return empty?"Стерто · повторное чтение: кодов нет":"Стирание подтверждено, коды появились снова";
        return empty?"Повторное чтение: кодов нет; стирание не подтверждено":"Коды остались; стирание не подтверждено";
    }
}
