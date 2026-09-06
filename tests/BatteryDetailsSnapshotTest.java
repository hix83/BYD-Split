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
        System.out.println("4 battery detail checks passed");
    }
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
