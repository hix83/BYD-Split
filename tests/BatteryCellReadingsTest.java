package ru.logunov.bydsplit;

public final class BatteryCellReadingsTest {
    public static void main(String[] args) {
        BatteryCellReadings.Decoder d=new BatteryCellReadings.Decoder();
        for(int i=3;i<36;i++)d.accept(cells(i,3255),100+i*100);
        check(d.snapshot().millivolts==null,"partial sweep is never a complete battery");
        for(int i=0;i<36;i++)d.accept(cells(i,3255),1000+i*100);
        BatteryCellReadings s=d.snapshot();
        check(s.millivolts.length==106&&s.millivolts[105]==3255,"106 cells, ignore final two padding words");
        byte[] summary=cells(253,3300);d.accept(summary,4700);
        check(d.snapshot().millivolts[0]==3255,"summary frame cannot overwrite numbered cells");
        d.accept(hex("0000043c0002026d8c2b00353637373535bc"),4800);
        d.accept(hex("0000043c0002026d8c8f01353638383635b8"),4900);
        s=d.snapshot();
        check(s.temperatures.length==12&&s.temperatures[0]==13&&s.temperatures[8]==16&&s.temperatures[11]==13,"live temperature frames, 6 sensors each, offset 40");
        check(s.cellsFresh(5000)&&!s.cellsFresh(15001)&&!s.cellsFresh(1),"expired/future sample rejected");
        BatteryCellReadings parsed=BatteryCellReadings.parse("noise\nCELLS="+BatteryCellReadings.csv(s.millivolts)+"\nCELLS_AT="+s.cellsAt+"\nTEMPS="+BatteryCellReadings.csv(s.temperatures)+"\nTEMPS_AT="+s.temperaturesAt);
        check(parsed.cellsFresh(5000)&&parsed.temperaturesFresh(5000),"bridge round trip");
        check(BatteryCellReadings.parse("CELLS=1,2,3\nCELLS_AT=1").millivolts==null,"truncated response rejected");
        BatteryCellReadings.Decoder lost=new BatteryCellReadings.Decoder();
        for(int i=0;i<36;i++)if(i!=8)lost.accept(cells(i,3255),1000+i*100);
        check(lost.snapshot().millivolts==null,"missing frame cannot become full data");
        BatteryCellReadings.Decoder invalid=new BatteryCellReadings.Decoder();
        for(int i=0;i<36;i++)invalid.accept(cells(i,i==8?65535:3255),1000+i*100);
        check(invalid.snapshot().millivolts==null,"invalid cell word invalidates sweep");
        for(int i=0;i<36;i++)invalid.accept(cells(i,3255),1000+i*200);
        check(invalid.snapshot().millivolts==null,"overlong sweep rejected");
        byte[] corrupt=hex("0000043c0002026d8c2b00353637373535bd");
        invalid.accept(corrupt,10000);invalid.accept(hex("0000043c0002026d8c8f01353638383635b8"),10100);
        check(invalid.snapshot().temperatures==null,"bad temperature checksum rejected");
        System.out.println("11 BMS measurement checks passed");
    }
    private static byte[] cells(int index,int value) {
        byte[] b=new byte[18];b[2]=4;b[3]=0x3d;b[5]=2;b[10]=(byte)index;b[17]=(byte)0xe6;
        for(int k=0;k<3;k++){int v=index==35&&k>0?65535:value;b[11+k*2]=(byte)v;b[12+k*2]=(byte)(v>>8);}return b;
    }
    static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
