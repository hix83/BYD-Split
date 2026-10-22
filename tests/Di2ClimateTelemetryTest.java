package ru.logunov.bydsplit;
import java.util.*;
public final class Di2ClimateTelemetryTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  Map<String,Double> v=DiPlusValues.parse("SOC:43|ACStatus:1|FanLevel:7|ACTemp:27|Wind:4|ACCirc:0|ExtTemp:-128|InsideTemp:-2000");
  v.putAll(Di2BatteryValues.parse("AC_COMPRESSOR=Result: Parcel(00000000 00000000)"));
  TechSnapshot t=new TechSnapshot(Di2Snapshot.battery(v),v);
  check(t.values.get("ac")==1&&t.values.get("compressor")==0,"climate on is not A/C on");
  check(t.values.get("fan")==7&&t.values.get("temperature")==27&&t.values.get("wind")==4&&t.values.get("recirculation")==0,"live climate settings reach page");
  check(!t.values.containsKey("inside")&&!t.values.containsKey("outside"),"target is never cabin measurement");
  v.clear();t=new TechSnapshot(Di2Snapshot.battery(v),v);check(t.values.isEmpty(),"missing is not off");
  v.put("power",2.);v.put("compressor",.5);v.put("fan",65535.);v.put("wind",8.);v.put("recirculation",-1.);v.put("temperature",Double.NaN);
  t=new TechSnapshot(Di2Snapshot.battery(v),v);check(t.values.isEmpty(),"invalid enums rejected");
  check(Di2BatteryValues.parse("AC_COMPRESSOR=Result: Parcel(00000000 ffffd8e5)").isEmpty(),"Binder error not off");
  check(Di2BatteryValues.parse("AC_COMPRESSOR=Result: Parcel(00000000 00000001)").get("compressor")==1,"A/C on");
  System.out.println("Di2 climate telemetry: live snapshot, independent climate/A-C states, unavailable sensors passed");
 }
}
