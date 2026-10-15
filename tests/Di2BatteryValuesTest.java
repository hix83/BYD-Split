package ru.logunov.bydsplit;
import java.util.Map;
public final class Di2BatteryValuesTest {
 public static void main(String[] args) {
  Map<String, Double> v = Di2BatteryValues.parse("SOH=Result: Parcel(00000000 00000061 '....')\nMIN_TEMP=Result: Parcel(00000000 00000019)\nMAX_TEMP=Result: Parcel(00000000 0000001c)\nVOLTAGE=Result: Parcel(00000000 415ccccd)\nMIN_CELL=Result: Parcel(00000000 405353f8)\nMAX_CELL=Result: Parcel(00000000 4055f3b7)");
  VehicleTelemetrySnapshot s = Di2Snapshot.vehicle(v);
  if (s.batterySohPercent != 97 || s.batteryMinTempC != 25 || s.batteryMaxTempC != 28 || Math.abs(s.auxiliaryVoltage - 13.8f) > .001) throw new AssertionError(v);
  BatteryDetailsSnapshot b = Di2Snapshot.battery(v);
  if (b.minCellMv != 3302 || b.maxCellMv != 3343 || b.lifetimeKwh != null) throw new AssertionError(v);
  if (!Di2BatteryValues.parse("SOH=Result: Parcel(00000000 ffffffff)\nVOLTAGE=Result: Parcel(00000000 bf800000)\nMAX_TEMP=Result: Parcel(ffffffff 0000001c)\nMIN_TEMP=Result: Parcel(00000000 7fffffff)").isEmpty()) throw new AssertionError("invalid data accepted");
  v=Di2BatteryValues.parse("MIN_TEMP=Result: Parcel(00000000 00000000)\nMAX_TEMP=Result: Parcel(00000000 fffffffb)");
  if (v.get("batteryMinTempC") != 0 || v.get("batteryMaxTempC") != -5) throw new AssertionError("real zero or cold temperature rejected");
  System.out.println("Di2BatteryValues: live parcels, sentinel rejection, Celsius and float units passed");
 }
}
