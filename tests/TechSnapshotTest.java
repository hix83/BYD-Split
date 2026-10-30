package ru.logunov.bydsplit;
import java.util.*;
public class TechSnapshotTest {
 public static void main(String[] args) {
  int[] raw=new int[TechSnapshot.FIELDS.length];Arrays.fill(raw,-10011);
  raw[0]=400;raw[1]=Float.floatToIntBits(-2.5f);raw[2]=3500;raw[3]=231;raw[5]=65535;
  TechSnapshot s=new TechSnapshot(null,raw);
  check(s.values.get("batteryWatts")==-1000);check(s.values.get("insulation")==3.5);check(Math.abs(s.values.get("chargeLimit")-23.1)<.0001);check(!s.values.containsKey("frontTemp"));check(!s.values.containsKey("rearRpm"));
  raw[1]=Float.floatToIntBits(Float.NaN);s=new TechSnapshot(null,raw);check(!s.values.containsKey("batteryWatts"));
  raw[1]=Float.floatToIntBits(3000);s=new TechSnapshot(null,raw);check(!s.values.containsKey("current"));
  raw[1]=Float.floatToIntBits(-1f);s=new TechSnapshot(null,raw);check(!s.values.containsKey("current"));check(!s.values.containsKey("batteryWatts"));
  raw[5]=3;raw[6]=2;s=new TechSnapshot(null,raw);
  check(s.values.get("frontTemp")==3);check(s.values.get("frontInv")==2);
  for(Object[] field:TechSnapshot.FIELDS)check(!((String)field[0]).startsWith("rear"));
  raw[5]=-10011;raw[6]=65535;s=new TechSnapshot(null,raw);
  check(!s.values.containsKey("frontTemp")&&!s.values.containsKey("frontInv"));
  System.out.println("Tech decoding checks passed: DM-i front temperatures, missing values, no rear drive");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
