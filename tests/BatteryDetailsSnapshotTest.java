package ru.logunov.bydsplit;
public final class BatteryDetailsSnapshotTest {
    public static void main(String[] args) {
        BatteryDetailsSnapshot s = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY,
                new int[]{3275,3279,Float.floatToIntBits(1234.5f),0,13});
        check(s.deltaMv()==4 && s.minCellMv==3275,"cell millivolts");
        check(s.lifetimeKwh==1234.5f && s.sessionKwh==0f,"energy float and genuine zero");
        s = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY,new int[]{0xffffd8e5,3279,0xffffd8e5,Float.floatToIntBits(-1),65535});
        check(s.deltaMv()==null && s.minCellMv==null && s.lifetimeKwh==null && s.sessionKwh==null && s.bmsState==null,"invalid values");
        s = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY,new int[]{3300,3200,0,0,1});
        check(s.deltaMv()==null,"inverted voltage bounds");
        check(s.cellCount==null && s.temperatureSensorCount==null,"old snapshot has no invented counts");
        s = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY,new int[]{3275,3279,0,0,1,106,106,12});
        check(s.cellCount==106 && s.temperatureSensorCount==12,"live Song cell and probe counts");
        s = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY,new int[]{3275,3279,0,0,1,106,107,12});
        check(s.cellCount==null && s.temperatureSensorCount==12,"conflicting counts are unavailable");
        s = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY,new int[]{3275,3279,0,0,1,-1,106,0});
        check(s.cellCount==106 && s.temperatureSensorCount==null,"valid count fallback and missing sensors");
        s = new BatteryDetailsSnapshot(VehicleTelemetrySnapshot.EMPTY,new int[]{3275,3279,0,0,1,1023,65535,-1});
        check(s.cellCount==null && s.temperatureSensorCount==null,"sentinels are never counts");
        System.out.println("9 battery detail checks passed");
    }
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
