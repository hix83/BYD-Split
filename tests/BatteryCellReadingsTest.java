package ru.logunov.bydsplit;
import java.nio.file.*;import java.util.*;
public final class BatteryCellReadingsTest {
 static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  List<String> lines=Files.readAllLines(Paths.get("tests/fixtures/seagull-bms-94x7.txt"));
  BatteryCellReadings.Decoder d=new BatteryCellReadings.Decoder();
  for(int i=0;i<lines.size();i++)d.accept(hex(lines.get(i)),1000+i*100);
  BatteryCellReadings r=d.snapshot();check(r.cellsFresh(4500)&&r.temperaturesFresh(4500),"live captured sweep");
  check(r.millivolts.length==94&&r.temperatures.length==7,"profile");
  int lo=Arrays.stream(r.millivolts).min().getAsInt(),hi=Arrays.stream(r.millivolts).max().getAsInt();
  check(lo==3270&&hi==3275,"captured min/max");for(int t:r.temperatures)check(t==12,"Celsius offset");
  check(!r.cellsFresh(15000)&&!r.cellsFresh(1),"stale/future");
  String output="CELLS="+BatteryCellReadings.csv(r.millivolts)+"\nCELLS_AT="+r.cellsAt+"\nTEMPS="+BatteryCellReadings.csv(r.temperatures)+"\nTEMPS_AT="+r.temperaturesAt;
  check(BatteryCellReadings.parse(output).cellsFresh(4500),"shell transport");
  check(!BatteryCellReadings.parse(output.replaceFirst("327[0-5]","65535")).cellsFresh(4500),"sentinel rejected");
  check(!BatteryCellReadings.parse("CELLS=3270,3271\nCELLS_AT=1000").cellsFresh(1000),"partial rejected");
  for(int mode=0;mode<5;mode++) {
   d=new BatteryCellReadings.Decoder();
   for(int i=0;i<32;i++){
    if(mode==0&&i==12)continue;
    byte[] b=hex(lines.get(i));
    if(mode==1&&i==12)b[8]^=1; // bad checksum
    if(mode==2)b[5]=2; // Song bus / wrong envelope
    if(mode==3&&i==12){b[7]=(byte)255;b[8]=(byte)255;checksum(b);}
    if(mode==4&&i==31){b[9]=0;checksum(b);} // padding must be ff
    d.accept(b,1000+i*100);
   }
   check(d.snapshot().millivolts==null,"bad sweep "+mode);
  }
  d=new BatteryCellReadings.Decoder();for(int i=0;i<32;i++)d.accept(hex(lines.get(i)),1000+i*300);
  check(d.snapshot().millivolts==null,"slow sweep rejected");
  Map<String,Double> motors=Di2BatteryValues.parse("MOTOR_TEMP=Result: Parcel(00000000 00000013)\nINVERTER_TEMP=Result: Parcel(00000000 00000011)");
  TechSnapshot tech=new TechSnapshot(Di2Snapshot.battery(motors),motors);
  check(tech.values.get("frontTemp")==19&&tech.values.get("frontInv")==17,"native motor temperatures");
  check(Di2BatteryValues.parse("MOTOR_TEMP=Result: Parcel(00000000 ffffd8e5)\nINVERTER_TEMP=Result: Parcel(00000000 000000ff)").isEmpty(),"motor sentinels rejected");
  Map<String,Double> hv=Di2BatteryValues.parse("HV_VOLTAGE=Result: Parcel(00000000 00000134)\nHV_CURRENT=Result: Parcel(00000000 3ecccc00)\nINSULATION=Result: Parcel(00000000 00001b16)");
  tech=new TechSnapshot(Di2Snapshot.battery(hv),hv);
  check(Math.abs(tech.values.get("batteryWatts")-123.2)<.01,"HV derived watts");
  check(Math.abs(tech.values.get("insulation")-6.934)<.001,"insulation kohm to Mohm");
  check(Di2BatteryValues.parse("HV_CURRENT=Result: Parcel(00000000 bf800000)\nHV_VOLTAGE=Result: Parcel(00000000 0000ffff)\nINSULATION=Result: Parcel(00000000 ffffd8e5)").isEmpty(),"HV sentinels");
  System.out.println("BatteryCellReadings: captured94x7, checksum/order/padding/age, temperatures passed");
 }
 static void checksum(byte[] b){int sum=0;for(int i=6;i<13;i++)sum+=b[i]&255;b[13]=(byte)(255-(sum&255));}
}
