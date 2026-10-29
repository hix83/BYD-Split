package ru.logunov.bydsplit;

import java.io.StringReader;
import java.util.*;

public final class DtcScannerTest {
    private static final class Clock implements DtcScanner.Clock {
        long at=1000;public long now(){return at;}public void sleep(long ms){at+=ms;}
    }
    private static final class Device implements DtcScanner.Device {
        final Clock clock;int sent,closed;boolean silent,traffic,reject;final Queue<byte[]> queue=new ArrayDeque<>();
        Device(Clock clock){this.clock=clock;}
        public void register(){if(traffic)queue.add(new byte[]{1});}
        public int send(byte[] request){sent++;if(reject)return -1;if(!silent){int address=((request[1]&255)<<8)|(request[2]&255);queue.add(frame(address,DtcProtocol.unhex("5901FF010000")));}return 0;}
        public byte[] poll(long ms){byte[] frame=queue.poll();clock.at+=frame==null?ms:10;return frame;}
        public void drain(){queue.clear();}public void close(){closed++;}
    }
    public static void main(String[] args)throws Exception {
        for(int address:DtcProtocol.ADDRESSES){byte[] request=DtcProtocol.request(address,0x19,1,255);check(request.length==7&&request[4]==0x19,"unexpected TX service");}
        refused(0x7e0,0x14,0,0);refused(0x7e0,0x10,1,0);refused(0x777,0x19,1,255);refused(0x7e0,0x19,2,0x80);
        byte[] list=DtcProtocol.unhex("5902FFD1668709D1678708D1688704D1698720");
        List<DtcProtocol.Row> rows=DtcProtocol.rows(list);check(rows.size()==3,"inactive status shown");
        check(rows.get(0).code.equals("U1166-87")&&rows.get(0).kind.equals("NOW"),"active code decoding");
        check(rows.get(1).kind.equals("OLD")&&rows.get(2).kind.equals("PENDING"),"historic/pending status decoding");
        check(DtcProtocol.rows(DtcProtocol.unhex("5902FFD1")).isEmpty(),"truncated list became a code");
        check(DtcProtocol.match(frame(0x782,list),0x782,0x19,2).kind.equals("ok"),"real frame layout rejected");
        check(DtcProtocol.match(frame(0x782,list),0x783,0x19,2).kind.equals("other"),"foreign response accepted");
        check(DtcProtocol.match(frame(0x782,DtcProtocol.unhex("7F1978")),0x782,0x19,1).kind.equals("pending"),"pending response");
        check(DtcProtocol.match(frame(0x782,DtcProtocol.unhex("7F1931")),0x782,0x19,1).kind.equals("nrc"),"negative response");
        byte[] multi=frame(0x782,list);multi[4]=2;check(DtcProtocol.match(multi,0x782,0x19,2).kind.equals("multiframe"),"multiframe guessed");
        check(DtcProtocol.blockClass(0x782,null)==null,"address guessed as ECU identity");
        check("BRAKES".equals(DtcProtocol.blockClass(0x782,DtcProtocol.unhex("62F19432330000000B"))),"ECU identity decoding");
        Clock clock=new Clock();Device device=new Device(clock);
        DtcScanner.Scan scan=new DtcScanner(device,clock,()->DtcProtocol.GUARD,()->1,()->true).run(r->{});
        check(scan.state.equals("DONE")&&device.sent==18&&device.closed==1,"complete count scan");
        clock=new Clock();device=new Device(clock);device.silent=true;
        scan=new DtcScanner(device,clock,()->DtcProtocol.GUARD,()->1,()->true).run(r->{});
        check(scan.state.equals("UNAVAILABLE")&&scan.reason.equals("no_frames"),"no answers reported as clean car");
        clock=new Clock();device=new Device(clock);device.traffic=true;
        scan=new DtcScanner(device,clock,()->DtcProtocol.GUARD,()->1,()->true).run(r->{});
        check(device.sent==0&&device.closed==1&&scan.reason.equals("passive_traffic"),"busy transport used");
        clock=new Clock();device=new Device(clock);final Device changing=device;
        scan=new DtcScanner(device,clock,()->DtcProtocol.GUARD,()->changing.sent==0?1:2,()->true).run(r->{});
        check(device.sent==1&&scan.reason.equals("not_park")&&device.closed==1,"scan continued out of P");
        for(String guard:new String[]{"","busy"}){clock=new Clock();device=new Device(clock);scan=new DtcScanner(device,clock,()->guard,()->1,()->true).run(r->{});check(device.sent==0&&device.closed==1,"guard ignored");}
        clock=new Clock();device=new Device(clock);scan=new DtcScanner(device,clock,()->DtcProtocol.GUARD,()->null,()->true).run(r->{});check(device.sent==0,"unknown gear allowed");
        clock=new Clock();device=new Device(clock);scan=new DtcScanner(device,clock,()->DtcProtocol.GUARD,()->1,()->false).run(r->{});check(device.sent==0,"OFF scan allowed");
        DtcDescriptions descriptions=new DtcDescriptions(new StringReader("1\tzh\tBrake sensor\n2\tzh\tDifferent text\n"),new StringReader("m\tDM\nn\tEV\n"),new StringReader("m\tBRAKES\tU116687\t1\nn\tBRAKES\tU116687\t2\n"),new StringReader("304\tm\n"));
        check(descriptions.lookup(304,"BRAKES","U1166-87").exact,"model-specific description lost");
        check(descriptions.lookup(999,"BRAKES","U1166-87")==null,"ambiguous similar description guessed");
        check(descriptions.lookup(999,null,"U1166-87")==null,"unknown ECU got generic description");
        System.out.println("DtcScannerTest PASS: read whitelist, decoding, status bits, bounds, foreign/NRC/pending, identity, coverage, P/READY/guard gates, cleanup and descriptions");
    }
    private static byte[] frame(int address,byte[] uds){int rid=address+8;byte[] raw=new byte[7+uds.length+4];raw[0]=10;raw[1]=(byte)(rid>>8);raw[2]=(byte)rid;raw[3]=(byte)uds.length;raw[4]=1;raw[5]=1;raw[6]=1;System.arraycopy(uds,0,raw,7,uds.length);return raw;}
    private static void refused(int a,int s,int sub,int p){try{DtcProtocol.request(a,s,sub,p);throw new AssertionError("write or unlisted request accepted");}catch(IllegalArgumentException expected){}}
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
