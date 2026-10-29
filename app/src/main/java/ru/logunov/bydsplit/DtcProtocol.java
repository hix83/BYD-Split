package ru.logunov.bydsplit;

import java.util.*;

/** Adapted from BYDMate a36fa7b6 DtcWire/DtcParser. PolyForm Noncommercial; see NOTICE. */
final class DtcProtocol {
    static final int[] ADDRESSES={0x712,0x720,0x722,0x726,0x747,0x762,0x782,0x783,
            0x7e0,0x7e1,0x7e2,0x7e3,0x7e4,0x7e5,0x7e6,0x7e7,0x7f1,0x7f2};
    static final String GUARD="r@-1_0&w@-1_0";
    static final class Match {
        final String kind; final byte[] uds;
        Match(String kind,byte[] uds){this.kind=kind;this.uds=uds;}
    }
    static final class Row {
        final String code,kind;final int status;
        Row(String code,String kind,int status){this.code=code;this.kind=kind;this.status=status;}
    }
    static byte[] request(int address,int service,int sub,int parameter) {
        boolean allowed=false;for(int candidate:ADDRESSES)if(candidate==address)allowed=true;
        if(!allowed||!(service==0x19&&(sub==1&&parameter==255||sub==2&&(parameter==13||parameter==9||parameter==255))
                ||service==0x22&&sub==0xf1&&(parameter==0x94||parameter==0x97)))throw new IllegalArgumentException("Read request not allowed");
        return new byte[]{10,(byte)(address>>8),(byte)address,3,(byte)service,(byte)sub,(byte)parameter};
    }
    static Match match(byte[] raw,int address,int service,int sub) {
        if(raw==null||raw.length<7||u(raw,0)!=10||((u(raw,1)<<8)|u(raw,2))!=address+8)return new Match("other",null);
        if(u(raw,4)>1)return new Match("multiframe",null);
        int len=u(raw,3);
        if(raw.length<7+len)return new Match("truncated",null);
        byte[] uds=Arrays.copyOfRange(raw,7,7+len);
        if(u(raw,6)==3&&echoes(uds,service,service,sub))return new Match("gw_timeout",null);
        if(uds.length>=3&&u(uds,0)==0x7f&&u(uds,1)==service)
            return new Match(u(uds,2)==0x78?"pending":"nrc",uds);
        return new Match(echoes(uds,service+0x40,service,sub)?"ok":"other",uds);
    }
    private static boolean echoes(byte[] b,int head,int service,int sub){
        return b.length>=(service==0x22?3:2)&&u(b,0)==head
                &&(service==0x22?((u(b,1)<<8)|u(b,2))==sub:u(b,1)==sub);
    }
    static int count(byte[] uds){return uds!=null&&uds.length>=6&&u(uds,0)==0x59&&u(uds,1)==1?(u(uds,4)<<8)|u(uds,5):-1;}
    static List<Row> rows(byte[] uds){
        List<Row> rows=new ArrayList<>();
        if(uds==null||uds.length<3||u(uds,0)!=0x59||u(uds,1)!=2||(uds.length-3)%4!=0)return rows;
        for(int i=3;i+3<uds.length;i+=4){
            int status=u(uds,i+3);String kind=(status&1)!=0?"NOW":(status&8)!=0?"OLD":(status&4)!=0?"PENDING":null;
            if(kind!=null)rows.add(new Row(code(uds,i),kind,status));
        }
        return rows;
    }
    static String code(byte[] b,int at){int first=u(b,at);return String.format(Locale.ROOT,"%c%d%X%02X-%02X","PCBU".charAt(first>>6),(first>>4)&3,first&15,u(b,at+1),u(b,at+2));}
    static String blockClass(int address,byte[] id){
        if(id!=null&&id.length>=9&&u(id,0)==0x62&&u(id,1)==0xf1&&u(id,2)==0x94){
            int supplier=(u(id,3)<<8)|u(id,4),type=u(id,8);
            int[] suppliers={0x4b51,0x3233,0x3053,0x4843,0x0031,0x4a52,0x4835,0x4b30};
            int[] types={0x83,0x0b,0x6f,0x11,0x70,0x43,0x0e,0xfd};
            String[] names={"ADAS","BRAKES","STEERING","AIRBAG","RADAR","BMS","CHARGER","URE"};
            for(int i=0;i<suppliers.length;i++)if(supplier==suppliers[i]&&type==types[i])return names[i];
            if(supplier==0x4845&&type>=0xf0&&type<=0xf2)return address==0x720?"BODY_LEFT":address==0x747?"BODY_RIGHT":address==0x726?"BODY_REAR":null;
        }
        // Address-only labels validated on other models are not assumed to identify a Song ECU.
        return null;
    }
    static String hex(byte[] bytes){if(bytes==null)return "";StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02X",b&255));return s.toString();}
    static byte[] unhex(String text){if(text==null||text.length()>2048||text.length()%2!=0||!text.matches("[0-9A-Fa-f]*"))throw new IllegalArgumentException();byte[] b=new byte[text.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return b;}
    static boolean gate(String guard,Integer gear,long elapsed){return GUARD.equals(guard)&&Integer.valueOf(1).equals(gear)&&elapsed>=0&&elapsed<90000;}
    private static int u(byte[] b,int i){return b[i]&255;}
}
