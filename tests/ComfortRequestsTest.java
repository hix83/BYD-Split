package ru.logunov.bydsplit;
import static ru.logunov.bydsplit.ComfortState.Field.*;
public final class ComfortRequestsTest {
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        ComfortRequests r=new ComfortRequests();ComfortState actual=ComfortState.parse("UNIT=1\nDRIVER_TEMP=22\nFAN=3\nDRIVER_HEAT=1\nPOWER=1");r.accept(actual);
        r.request(DRIVER_HEAT,r.display().nextUiValue(DRIVER_HEAT));check(r.current(DRIVER_HEAT)==3);
        ComfortRequests.Request first=r.next();check(first.value==2&&r.next()==null);
        r.request(DRIVER_HEAT,r.display().nextUiValue(DRIVER_HEAT));check(r.current(DRIVER_HEAT)==2);
        r.request(DRIVER_HEAT,r.display().nextUiValue(DRIVER_HEAT));check(r.current(DRIVER_HEAT)==1);
        r.accept(actual.withRaw(DRIVER_HEAT,3));check(r.current(DRIVER_HEAT)==1); // Old poll cannot flicker the newest choice.
        check(!r.complete(first));check(r.hasPending());
        ComfortRequests.Request last=r.next();check(last.value==0); // Intermediate level was coalesced.
        r.accept(actual);check(r.complete(last));check(!r.hasPending()&&r.current(DRIVER_HEAT)==1);
        r.request(FAN,7);check(r.current(FAN)==7);ComfortRequests.Request failed=r.next();
        r.accept(actual);check(r.complete(failed));check(r.current(FAN)==3); // Rejection rolls back to the car.
        r.request(DRIVER_TEMP,23);r.request(FAN,5);r.request(DRIVER_TEMP,24);
        check(r.current(DRIVER_TEMP)==24&&r.current(FAN)==5);
        ComfortRequests.Request temperature=r.next();check(temperature.field==DRIVER_TEMP&&temperature.value==24);
        r.accept(actual.withRaw(DRIVER_TEMP,24));check(r.complete(temperature));check(r.current(FAN)==5);
        ComfortRequests.Request fan=r.next();check(fan.field==FAN&&fan.value==5);
        r.accept(actual.withRaw(FAN,5).withRaw(DRIVER_TEMP,24));check(r.complete(fan));check(!r.hasPending());
        check(!r.complete(first)); // Duplicate / stale callback has no effect.
        r.request(POWER,0);r.clear();check(!r.hasPending()&&r.next()==null&&r.current(POWER)==1);
        try{r.request(DRIVER_HEAT,3);throw new AssertionError();}catch(IllegalArgumentException expected){}
        try{r.request(UNIT,0);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("Optimistic comfort: instant display, descending levels, coalescing, late replies, rollback and multiple fields passed");
    }
}
