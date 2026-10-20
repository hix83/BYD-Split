package ru.logunov.bydsplit;
import java.util.*;
public final class FuelLedgerTest {
    public static void main(String[] args) {
        List<FuelLedger.Fill> fills=Arrays.asList(new FuelLedger.Fill(25.,1500.,true),new FuelLedger.Fill(10.,1250.,false),new FuelLedger.Fill(40.,1000.,true));
        near(FuelLedger.consumption(fills),7);
        if(FuelLedger.consumption(fills.subList(0,2))!=null)throw new AssertionError("single full tank");
        if(FuelLedger.consumption(Arrays.asList(new FuelLedger.Fill(20.,1000.,true),new FuelLedger.Fill(20.,1000.,true)))!=null)throw new AssertionError("zero mileage");
        near(FuelLedger.consumption(Arrays.asList(new FuelLedger.Fill(2.,1600.,false),fills.get(0),fills.get(1),fills.get(2))),7);
        System.out.println("Full-tank fuel accounting passed");
    }
    private static void near(Double a,double b){if(a==null||Math.abs(a-b)>1e-7)throw new AssertionError(a+" != "+b);}
}
