package ru.logunov.bydsplit;

import java.util.*;
import java.util.function.*;

public final class DtcEraseEngineTest {
    static final class Clock implements DtcScanner.Clock {long time=1000;public long now(){return time;}public void sleep(long ms){time+=ms;}}
    static final class Device implements DtcScanner.Device {
        final Clock clock;final Queue<byte[]> queue=new ArrayDeque<>();int sends,clears,closed,registered;
        boolean passive,foreign,silentClear,refused,back,duplicates,busy,cutList;String ack="54";
        Device(Clock c){clock=c;}
        public void register(){registered++;}
        public int send(byte[] p){
            sends++;int address=((p[1]&255)<<8)|(p[2]&255),service=p[4]&255;
            if(service==0x14){clears++;if(foreign)queue.add(frame(0x7e0,"5901FF010000"));if(silentClear)return 0;
                byte[] reply=frame(address,refused?"7F1433":busy?"14FFFFFF":ack);if(busy)reply[6]=5;queue.add(reply);if(duplicates)queue.add(reply.clone());
            }else if(service==0x19){boolean present=back||refused||busy||cutList;queue.add(frame(address,(p[5]&255)==1?(present?"5901FF010001":"5901FF010000"):(cutList?"5902FFD1":"5902FFD1668709")));}
            else throw new AssertionError("Unexpected write/session request");return 0;
        }
        public byte[] poll(long ms){if(passive&&sends==0)return frame(0x7e0,"5901FF010000");byte[] frame=queue.poll();if(frame==null)clock.time+=ms;return frame;}
        public void drain(){queue.clear();}public void close(){closed++;}
    }
    static DtcEraseEngine engine(Device d,Supplier<String> guard,Supplier<Integer> gear,BooleanSupplier ready,DoubleSupplier speed,boolean legacy){return new DtcEraseEngine(d,d.clock,guard,gear,ready,speed,legacy);}
    static DtcEraseEngine.Run run(Device d){return engine(d,()->DtcProtocol.GUARD,()->1,()->true,()->0,false).run(new int[]{0x782},r->{});}
    public static void main(String[] args){
        check(DtcProtocol.hex(DtcProtocol.clearRequest(0x782)).equals("0A07820414FFFFFF"),"clear packet");
        try{DtcProtocol.request(0x782,0x14,255,255);throw new AssertionError("scan builder can write");}catch(IllegalArgumentException expected){}
        for(String ack:new String[]{"54","54FFFFFF"}){Device d=new Device(new Clock());d.ack=ack;d.duplicates=true;DtcEraseEngine.Run r=run(d);
            check(r.state.equals("DONE")&&d.clears==1&&d.closed==1&&DtcEraseEngine.verified(r.results.get(0).reread),"clear/duplicate/reread");check(DtcEraseEngine.verdict(r.results.get(0)).startsWith("Стерто"),"confirmed empty verdict");}
        Device d=new Device(new Clock());d.silentClear=true;DtcEraseEngine.Run r=run(d);check(d.clears==1&&r.results.get(0).confirm.equals("unconfirmed"),"lost clear response resent/guessed");
        check(!DtcEraseEngine.verdict(r.results.get(0)).startsWith("Стерто"),"unconfirmed clear advertised as success");
        d=new Device(new Clock());d.refused=true;r=run(d);check(r.results.get(0).confirm.equals("refused")&&r.results.get(0).nrc==0x33&&d.clears==1,"NRC handling");
        d=new Device(new Clock());d.back=true;r=run(d);check(DtcEraseEngine.verdict(r.results.get(0)).contains("снова"),"returning fault hidden");
        d=new Device(new Clock());d.busy=true;r=run(d);check(d.clears==1&&!r.results.get(0).confirm.equals("confirmed"),"busy forwarded/resent");
        d=new Device(new Clock());d.cutList=true;r=run(d);check(!DtcEraseEngine.verified(r.results.get(0).reread),"truncated reread counted as erased");
        for(double speed:new double[]{Double.NaN,.1,1,-1}){d=new Device(new Clock());r=engine(d,()->DtcProtocol.GUARD,()->1,()->true,()->speed,false).run(new int[]{0x782},x->{});check(d.sends==0&&d.closed==1,"unknown/moving speed accepted");}
        for(String guard:new String[]{"","busy"}){d=new Device(new Clock());r=engine(d,()->guard,()->1,()->true,()->0,false).run(new int[]{0x782},x->{});check(d.sends==0,"guard ignored");}
        d=new Device(new Clock());r=engine(d,()->"",()->1,()->true,()->0,true).run(new int[]{0x782},x->{});check(r.state.equals("DONE"),"verified legacy Song gate blocked");
        d=new Device(new Clock());d.passive=true;r=run(d);check(d.sends==0&&r.reason.equals("passive_traffic"),"passive traffic ignored");
        d=new Device(new Clock());d.foreign=true;r=engine(d,()->DtcProtocol.GUARD,()->1,()->true,()->0,false).run(new int[]{0x782,0x783},x->{});
        check(d.clears==1&&r.reason.equals("foreign_traffic")&&r.results.get(1).confirm.equals("not_run")&&d.closed==1,"foreign traffic did not stop writes");
        d=new Device(new Clock());final Device changing=d;r=engine(d,()->DtcProtocol.GUARD,()->changing.clears==0?1:2,()->true,()->0,false).run(new int[]{0x782,0x783},x->{});check(d.clears==1&&r.reason.equals("not_park"),"continued after leaving P");
        d=new Device(new Clock());r=engine(d,()->DtcProtocol.GUARD,()->1,()->false,()->0,false).run(new int[]{0x782},x->{});check(d.sends==0,"OFF write");
        for(int[] targets:new int[][]{{},{0x123},{0x782,0x782}}){d=new Device(new Clock());r=engine(d,()->DtcProtocol.GUARD,()->1,()->true,()->0,false).run(targets,x->{});check(d.sends==0&&d.registered==0&&d.closed==1,"invalid targets used");}
        check(TelegramLink.code(" /start@test_bot 12345678 ").equals("12345678"),"group linking");
        check(TelegramLink.code("12345678").equals("12345678")&&!TelegramLink.code("/startx 12345678").equals("12345678"),"unrelated command linked");
        check(!TelegramLink.fresh(1000,999)&&!TelegramLink.fresh(1000,601000)&&TelegramLink.newCode().matches("[0-9]{8}"),"expiry or random code");
        System.out.println("PASS: one-shot clear, 54/group echo, NRC/lost reply/returning faults, strict reread, P/READY/exact speed/guard/traffic, target validation, cleanup, Telegram code expiry");
    }
    static byte[] frame(int address,String hex){byte[] uds=DtcProtocol.unhex(hex);int id=address+8;byte[] raw=new byte[7+uds.length+2];raw[0]=10;raw[1]=(byte)(id>>8);raw[2]=(byte)id;raw[3]=(byte)uds.length;raw[4]=1;raw[5]=1;raw[6]=1;System.arraycopy(uds,0,raw,7,uds.length);return raw;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
