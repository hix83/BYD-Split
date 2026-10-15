package ru.logunov.bydsplit;
import java.util.*;
/** Di+ parameter units follow the BYDMate DiLink 2 contract; no zero-fill fallback. */
final class DiPlusValues {
 static Map<String,Double> parse(String raw){
  Map<String,String> fields=new HashMap<>();for(String part:raw.split("\\|")){int i=part.indexOf(':');if(i>0)fields.put(part.substring(0,i),part.substring(i+1));}
  Map<String,Double> v=new HashMap<>();
  put(v,fields,"SOC","socPercent",0,100,1);put(v,fields,"Speed","speedKmh",0,300,1);
  put(v,fields,"Mileage","mileage",0,100000000,.1);
  for(String wheel:new String[]{"FL","FR","RL","RR"})put(v,fields,"TirePress"+wheel,"pressure_"+wheel.toLowerCase(java.util.Locale.ROOT),100,500,1);
  put(v,fields,"Power","batteryPowerKw",-500,500,1);
  put(v,fields,"MotorRpm","motorRpm",-20000,20000,1);
  put(v,fields,"ChargeGun","chargeGunState",1,5,1);
  put(v,fields,"DriveMode","driveMode",0,4,1);
  put(v,fields,"ExtTemp","outsideC",-50,80,1);put(v,fields,"InsideTemp","insideC",-50,80,1);
  put(v,fields,"Wind","wind",1,7,1);
  put(v,fields,"ACStatus","power",0,1,1);put(v,fields,"FanLevel","fan",0,7,1);
  put(v,fields,"ACTemp","temperature",17,33,1);put(v,fields,"ACCirc","recirculation",0,1,1);
  Double voltage=number(fields.get("Voltage12V"));if(voltage!=null){if(voltage>100)voltage/=1000;if(voltage>=9&&voltage<=18)v.put("auxiliaryVoltage",voltage);}
  Double min=number(fields.get("MinBatTemp")),max=number(fields.get("MaxBatTemp"));
  if(min!=null&&max!=null&&min>=-40&&max<=80&&min<=max&&!(min==0&&max==0)){v.put("batteryMinTempC",min);v.put("batteryMaxTempC",max);}
  put(v,fields,"MinCellV","minCellMv",1.5,4.5,1000);put(v,fields,"MaxCellV","maxCellMv",1.5,4.5,1000);
  return v;
 }
 private static void put(Map<String,Double> out,Map<String,String> in,String from,String to,double lo,double hi,double scale){Double n=number(in.get(from));if(n!=null&&n>=lo&&n<=hi)out.put(to,n*scale);}
 private static Double number(String s){try{double n=Double.parseDouble(s);return Double.isFinite(n)?n:null;}catch(Exception e){return null;}}
}
