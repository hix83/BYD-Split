package ru.logunov.bydsplit;
import java.util.*;

final class TechSnapshot {
    final BatteryDetailsSnapshot battery;
    final Map<String,Double> values=new HashMap<>();
    // key, device, FID, transaction, scale, minimum, maximum
    static final Object[][] FIELDS={
        {"hv",1009,1145045000,5,1.,0.,1000.},
        {"current",1009,1145045016,7,1.,-2000.,2000.},
        {"insulation",1039,1134559256,5,.001,0.,10000.},
        {"chargeLimit",1014,877658136,5,.1,0.,1000.},
        {"dischargeLimit",1014,1145045048,5,1.,0.,2000.},
        {"frontTemp",1039,1154482192,5,1.,-50.,250.},
        {"rearTemp",1039,1155530768,5,1.,-50.,250.},
        {"frontInv",1039,1154482184,5,1.,-50.,250.},
        {"rearInv",1039,1155530760,5,1.,-50.,250.},
        {"frontRpm",1012,1141899272,5,1.,-25000.,25000.},
        {"rearRpm",1012,621805576,5,1.,-25000.,25000.},
        {"accelerator",1013,874512392,5,1.,0.,100.},
        {"brake",1013,874512400,5,1.,0.,100.},
        {"compressor",1000,1031798840,5,1.,0.,30000.},
        {"ac",1000,1077936144,5,1.,0.,1.},
        {"inside",1000,1031798832,5,1.,-60.,80.},
        {"outside",1000,1077936184,5,1.,-60.,80.},
        {"mileage",1014,1246765072,5,.1,0.,10000000.}
    };
    TechSnapshot(BatteryDetailsSnapshot battery,int[] raw) {
        this.battery=battery;
        for(int i=0;i<raw.length&&i<FIELDS.length;i++) {
            Object[] f=FIELDS[i];
            // Leopard temperature addresses answer implausible values on this Song L
            // (-40/-36 C at +22 ambient). Keep their slots, but do not present them as sensors.
            String key=(String)f[0];
            if(key.equals("frontTemp")||key.equals("rearTemp")||key.equals("frontInv")||key.equals("rearInv")||key.equals("rearRpm"))continue;
            if(raw[i]==0xffff||raw[i]==0x7fffffff||raw[i]<=-10000&&raw[i]>=-10100)continue;
            double value=((int)f[3]==7?Float.intBitsToFloat(raw[i]):raw[i])*(double)f[4];
            if(Double.isFinite(value)&&value>=(double)f[5]&&value<=(double)f[6])values.put((String)f[0],value);
        }
        Double volts=values.get("hv"),amps=values.get("current");
        if(volts!=null&&amps!=null)values.put("batteryWatts",volts*amps);
    }
}
