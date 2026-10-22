package ru.logunov.bydsplit;
import java.util.Map;
public final class DiPlusValuesTest {
 public static void main(String[] args){
  Map<String,Double> v=DiPlusValues.parse("SOC:81|Speed:0|Mileage:472980|Voltage12V:0|TirePressFL:0|MinBatTemp:0|MaxBatTemp:0");
  if(v.get("socPercent")!=81||v.get("mileage")!=47298||v.containsKey("pressure_fl")||v.containsKey("auxiliaryVoltage")||v.containsKey("batteryMinTempC"))throw new AssertionError(v);
  v=DiPlusValues.parse("SOC:65535|Speed:NaN|Voltage12V:13800|TirePressFL:240|MinBatTemp:-5|MaxBatTemp:3|MinCellV:3.25");
  if(v.containsKey("socPercent")||v.containsKey("speedKmh")||v.get("auxiliaryVoltage")!=13.8||v.get("pressure_fl")!=240||v.get("minCellMv")!=3250)throw new AssertionError(v);
  if(!DiPlusValues.parse("SOC:{电量百分比}|Mileage:—").isEmpty())throw new AssertionError();
  VehicleTelemetrySnapshot s=Di2Snapshot.vehicle(v);if(s.auxiliaryVoltage!=13.8f||s.batteryMinTempC!=-5)throw new AssertionError();
  v=DiPlusValues.parse("MotorRpm:-2998|Wind:5|FanLevel:4|ACStatus:1");
  s=Di2Snapshot.vehicle(v);
  if(s.motorRpm!=-2998 || s.climate.fanLevel!=4 || !s.climate.glass || s.climate.face)throw new AssertionError("motor/airflow mapping");
  if(DiPlusValues.parse("MotorRpm:-2147483648|Wind:65535").containsKey("motorRpm"))throw new AssertionError("motor sentinel");
  v=DiPlusValues.parse("ACTemp:28.5|ACStatus:1|FanLevel:4|Wind:5");
  s=Di2Snapshot.vehicle(v);
  if(s.climate.targetTemperatureC!=28.5f || s.withBearing(10f).climate.targetTemperatureC!=28.5f || VehicleTelemetrySnapshot.EMPTY.withVehicleData(s).climate.targetTemperatureC!=28.5f)throw new AssertionError("climate target lost");
  if(Di2Snapshot.vehicle(DiPlusValues.parse("ACTemp:-2000")).climate.targetTemperatureC!=null)throw new AssertionError("invalid target");
  v=DiPlusValues.parse("SOC:92|Accelerator:37.5|Brake:12");
  TechSnapshot tech=new TechSnapshot(Di2Snapshot.battery(v),v);
  if(tech.values.get("accelerator")!=37.5 || tech.values.get("brake")!=12)throw new AssertionError("pedals lost before technical page");
  v=DiPlusValues.parse("Accelerator:0|Brake:100");
  if(v.get("accelerator")!=0 || v.get("brake")!=100)throw new AssertionError("pedal endpoints");
  for(String invalid:new String[]{"-1","101","65535","NaN","Infinity","{加速踏板深度}","—"}) {
   v=DiPlusValues.parse("Accelerator:"+invalid+"|Brake:"+invalid);
   if(v.containsKey("accelerator")||v.containsKey("brake"))throw new AssertionError("invalid pedal: "+invalid);
  }
  tech=new TechSnapshot(Di2Snapshot.battery(v),v);
  if(tech.values.containsKey("accelerator")||tech.values.containsKey("brake"))throw new AssertionError("missing pedals must stay unknown");
  System.out.println("DiPlusValues: live reply, units and missing-data checks passed");
 }
}
