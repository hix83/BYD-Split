package ru.logunov.bydsplit;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DtcMetadataTest {
    static final class Clock implements DtcScanner.Clock{long at=1000;public long now(){return at;}public void sleep(long ms){at+=ms;}}
    static final class Device implements DtcScanner.Device{
        final Clock clock;final Queue<byte[]> replies=new ArrayDeque<>();int snapshots,closed;boolean wrongCode;
        Device(Clock clock){this.clock=clock;}
        public void register(){}
        public int send(byte[] p){int address=((p[1]&255)<<8)|(p[2]&255);String response;
            if(p[4]==0x22)response=p[6]==(byte)0x97?"62F197424D5300":"62F19432330000000B";
            else if(p[5]==1)response=address==0x782?"5901FF010001":"5901FF010000";
            else if(p[5]==2)response="5902FFD1668709";
            else {check(p[4]==0x19&&p[5]==4,"unexpected metadata write");snapshots++;response=wrongCode?"5904D167870900":"5904D16687090100";}
            replies.add(frame(address,DtcProtocol.unhex(response)));return 0;
        }
        public byte[] poll(long ms){byte[] r=replies.poll();clock.at+=r==null?ms:10;return r;}
        public void drain(){replies.clear();}public void close(){closed++;}
    }
    public static void main(String[] args)throws Exception{
        byte[] request=DtcProtocol.snapshotRequest(0x782,"U1166-87");check(DtcProtocol.hex(request).equals("0A0782061904D16687FF"),"snapshot request wire format");
        for(String code:new String[]{"bad","U4166-87","U1166-87;","D16687","U1166-8X"})try{DtcProtocol.snapshotRequest(0x782,code);throw new AssertionError("invalid code");}catch(IllegalArgumentException expected){}
        try{DtcProtocol.snapshotRequest(0x777,"U1166-87");throw new AssertionError("unlisted address");}catch(IllegalArgumentException expected){}
        check(DtcProtocol.snapshotMatch(frame(0x782,DtcProtocol.unhex("5904D1678709")),0x782,"U1166-87").kind.equals("other"),"wrong DTC snapshot accepted");
        check(DtcProtocol.snapshotMatch(frame(0x782,DtcProtocol.unhex("7F1931")),0x782,"U1166-87").kind.equals("nrc"),"snapshot refusal lost");
        Clock clock=new Clock();Device device=new Device(clock);DtcScanner.Scan scan=new DtcScanner(device,clock,()->"",()->1,()->true,true).run(r->{});
        check(scan.state.equals("DONE")&&device.snapshots==1&&device.closed==1,"metadata scan/cleanup");
        DtcScanner.Result brakes=scan.results.stream().filter(r->r.address==0x782).findFirst().get();
        check(brakes.snapshots.size()==1&&brakes.snapshots.get(0).code.equals("U1166-87")&&brakes.snapshots.get(0).outcome.equals("ok"),"snapshot code association");
        check(DtcEcuNames.ownName(brakes.name).equals("BMS"),"self-reported name lost");
        clock=new Clock();device=new Device(clock);device.wrongCode=true;scan=new DtcScanner(device,clock,()->"",()->1,()->true,true).run(r->{});
        check(scan.state.equals("DONE")&&scan.extrasReason.equals("foreign_traffic")&&device.closed==1,"extras failure discarded completed scan");
        check(scan.results.stream().filter(r->r.address==0x782).findFirst().get().list.length>0,"codes lost on extras failure");
        DtcEcuNames.Name name=DtcEcuNames.resolve(0x7e7,null,DtcProtocol.unhex("62F197424D5300"));check(name.label.contains("тяговой батареей")&&name.source.contains("F197"),"BMS self-name not translated");
        name=DtcEcuNames.resolve(0x782,null,DtcProtocol.unhex("62F197495042"));check(name.label.contains("тормозов")&&name.source.contains("F197"),"IPB self-name not translated");
        name=DtcEcuNames.resolve(0x720,null,null);check(name.label.contains("левый")&&name.source.contains("не подтверждено"),"address hint claimed confirmed");
        name=DtcEcuNames.resolve(0x7e0,null,null);check(name.label.contains("неизвестным"),"engine inferred from generic address");
        check(DtcEcuNames.ownName(DtcProtocol.unhex("62F197424D530A5858")).isEmpty(),"control characters in ECU name");
        check(DtcEcuNames.ownName(DtcProtocol.unhex("62F197FFFE")).isEmpty(),"invalid encoding in ECU name");
        System.out.println("DtcMetadataTest PASS: strict read-only snapshot wire/echo, per-code association, optional extras failure preserves codes, owner cleanup, self-name translation and explicit address hints");
    }
    static byte[] frame(int address,byte[] uds){byte[] raw=new byte[7+uds.length];raw[0]=10;raw[1]=(byte)((address+8)>>8);raw[2]=(byte)(address+8);raw[3]=(byte)uds.length;raw[4]=1;raw[6]=1;System.arraycopy(uds,0,raw,7,uds.length);return raw;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
