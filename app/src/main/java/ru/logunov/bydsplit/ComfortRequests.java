package ru.logunov.bydsplit;

import java.util.LinkedHashMap;

/** Main-thread optimistic state with last-intent coalescing and versioned completion. */
final class ComfortRequests {
    static final class Request {
        final long id;
        final ComfortState.Field field;
        final int value,raw;
        Request(long id,ComfortState.Field field,int value,int raw){this.id=id;this.field=field;this.value=value;this.raw=raw;}
    }
    private ComfortState actual=ComfortState.parse("");
    private final LinkedHashMap<ComfortState.Field,Request> pending=new LinkedHashMap<>();
    private Request inFlight;
    private long serial;
    void accept(ComfortState sample){actual=sample;}
    ComfortState display(){
        ComfortState result=actual;
        for(Request request:pending.values())result=result.withRaw(request.field,request.raw);
        return result;
    }
    Integer current(ComfortState.Field field){return display().get(field);}
    void request(ComfortState.Field field,int value){
        int expected=display().command(field,value).expected;
        pending.put(field,new Request(++serial,field,value,expected));
    }
    Request next(){
        if(inFlight!=null||pending.isEmpty())return null;
        inFlight=pending.values().iterator().next();return inFlight;
    }
    /** An old acknowledgement/failure must never erase a newer user choice. */
    boolean complete(Request request){
        if(inFlight==null||inFlight.id!=request.id)return false;
        inFlight=null;Request latest=pending.get(request.field);
        if(latest!=null&&latest.id==request.id){pending.remove(request.field);return true;}
        return false;
    }
    boolean hasPending(){return !pending.isEmpty();}
    void clear(){pending.clear();inFlight=null;}
}
