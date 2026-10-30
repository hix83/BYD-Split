package ru.logunov.bydsplit;

import android.content.Context;
import android.os.IBinder;
import android.os.Looper;

/** Runs with shell identity; reads current firmware symbols, writes through its own AC SDK. */
public final class ComfortPanelBridge {
    private final IBinder service;
    ComfortPanelBridge() throws Exception {
        service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"autoservice");
        if(service==null)throw new IllegalStateException("autoservice unavailable");
    }
    private Integer read(ComfortState.Field f) {
        try {
            Class<?> ids=Class.forName("android.hardware.bydauto.BYDAutoFeatureIds$"+(f.seat()?"Setting":"Ac"));
            int device=Class.forName("android.hardware.bydauto.BYDAutoConstants")
                    .getField(f.seat()?"BYDAUTO_DEVICE_SETTING":"BYDAUTO_DEVICE_AC").getInt(null);
            return ComfortCommandWriter.transact(service,5,device,ids.getField(f.symbol).getInt(null),0);
        } catch(Exception ignored) { return null; }
    }
    private ComfortState snapshot() {
        StringBuilder result=new StringBuilder();
        for(ComfortState.Field field:ComfortState.Field.values()) {
            Integer value=read(field);
            if(value!=null)result.append(field.name()).append('=').append(value).append('\n');
        }
        return ComfortState.parse(result.toString());
    }
    @android.annotation.SuppressLint("WrongConstant") // DiLink extends Context with the OEM "auto" service.
    private static Object acManager() throws Exception {
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        Object manager=context.getSystemService("auto");
        if(manager==null)throw new IllegalStateException("auto manager unavailable");
        return manager;
    }
    private static int[] featureIds(ComfortState state,ComfortState.Field field) throws Exception {
        String[] names=state.acFeatures(field);int[] ids=new int[names.length];
        Class<?> ac=Class.forName("android.hardware.bydauto.BYDAutoFeatureIds$Ac");
        for(int i=0;i<ids.length;i++)ids[i]=ac.getField(names[i]).getInt(null);
        return ids;
    }
    private int writeAc(ComfortState state,ComfortState.Field field,int value) throws Exception {
        if(field==ComfortState.Field.MAX){
            int device=Class.forName("android.hardware.bydauto.BYDAutoConstants").getField("BYDAUTO_DEVICE_AC").getInt(null);
            return ComfortCommandWriter.transact(service,6,device,featureIds(state,field)[0],value);
        }
        Object manager=acManager();
        int device=Class.forName("android.hardware.bydauto.BYDAutoConstants").getField("BYDAUTO_DEVICE_AC").getInt(null);
        return (Integer)manager.getClass().getMethod("setIntArray",int.class,int[].class,int[].class)
                .invoke(manager,device,featureIds(state,field),state.acValues(field,value));
    }
    public static void main(String[] args) {
        HelperLifetime.limit(5000);
        try {
            ComfortPanelBridge bridge=new ComfortPanelBridge();
            if(args.length==1 && args[0].equals("read")) {
                ComfortState state=bridge.snapshot();
                for(ComfortState.Field f:ComfortState.Field.values())if(state.get(f)!=null)System.out.println(f.name()+"="+state.get(f));
            } else if(args.length==1 && args[0].equals("check")) {
                Object manager=acManager();
                manager.getClass().getMethod("setIntArray",int.class,int[].class,int[].class);
                ComfortState state=bridge.snapshot();
                for(ComfortState.Field field:ComfortState.Field.values())if(!field.seat()&&field!=ComfortState.Field.UNIT)featureIds(state,field);
                System.out.println("SDK_READY:setIntArray; all AC fields resolved; no writes sent");
            } else if(args.length==3 && args[0].equals("write")) {
                ComfortState.Field field=ComfortState.Field.valueOf(args[1]);
                int value=Integer.parseInt(args[2]);
                ComfortState state=bridge.snapshot();
                ComfortState.Command command=state.command(field,value);
                if(field.seat()) { ComfortCommandWriter.main(new String[]{""+command.args[0],""+command.args[1]}); }
                else if(bridge.read(field)==null)System.out.println("UNAVAILABLE");
                else {
                    int result=bridge.writeAc(state,field,value);
                    if(result<0)System.out.println("REFUSED:"+result);
                    else {
                        boolean confirmed=false;
                        for(int i=0;i<10;i++) { Thread.sleep(200);Integer current=bridge.read(field);if(current!=null && current==command.expected){confirmed=true;break;} }
                        System.out.println(confirmed?"OK:"+command.expected:"UNCONFIRMED");
                    }
                }
            } else throw new IllegalArgumentException("command");
        } catch(Exception error) {
            System.out.println("ERROR:"+error.getClass().getSimpleName());
            if(args.length==1 && args[0].equals("check"))error.printStackTrace(System.out);
        }
        // Some DiLink SDK versions create a Binder/Looper thread while initializing.
        HelperLifetime.exit();
    }
}
