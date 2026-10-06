package ru.logunov.bydsplit;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Base64;
import java.lang.reflect.Field;

/** Shell-uid writer; protocol adapted from BYDMate (AndyShaman), PolyForm Noncommercial 1.0.0. */
public final class ClusterMusicWriter {
    private static Integer symbol(String group, String name) throws Exception {
        Class<?> root=Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");
        for(Class<?> c:root.getDeclaredClasses()) if(c.getSimpleName().equals(group)) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f.getInt(null); }
            catch(NoSuchFieldException e){ return null; }
        }
        return null;
    }
    private static int device(String name) throws Exception {
        return Class.forName("android.hardware.bydauto.BYDAutoConstants")
            .getField("BYDAUTO_DEVICE_"+name).getInt(null);
    }
    private static void write(IBinder svc,int dev,Integer fid,Integer value,byte[] bytes) throws Exception {
        if(fid==null)return;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken(svc.getInterfaceDescriptor()); data.writeInt(dev); data.writeInt(fid);
            if(bytes!=null)data.writeByteArray(bytes);else data.writeInt(value);
            if(!svc.transact(bytes!=null?14:6,data,reply,0)||reply.dataAvail()<4||reply.readInt()<0)
                throw new IllegalStateException("write refused");
        } finally {data.recycle();reply.recycle();}
    }
    public static void main(String[] args) {
        try {
            int dev=device("INSTRUMENT");
            Integer info=symbol("Instrument","INSTRUMENT_MUSIC_INFO_SET"),
                state=symbol("Instrument","INSTRUMENT_MUSIC_STATE_SET"),
                source=symbol("Instrument","INSTRUMENT_MUSIC_SOURCE_SET");
            if(info==null||state==null||source==null){System.out.println("UNSUPPORTED");return;}
            IBinder svc=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"autoservice");
            if(svc==null){System.out.println("UNSUPPORTED");return;}
            int status=Integer.parseInt(args[0]);
            if(status!=3)write(svc,dev,source,26,null);
            write(svc,dev,state,status,null);
            write(svc,dev,info,null,Base64.decode(args[1],Base64.DEFAULT));
            try {write(svc,device("AUDIO"),symbol("Audio","AUDIO_ARMREST_SCREEN_SINGER_NAME_SET"),null,Base64.decode(args[2],Base64.DEFAULT));}catch(Exception ignored){}
            try {write(svc,dev,symbol("Instrument","INSTRUMENT_MUSIC_PLAYBACK_PROGRESS_SET"),Integer.parseInt(args[3]),null);}catch(Exception ignored){}
            System.out.println("OK");
        }catch(ClassNotFoundException error){System.out.println("UNSUPPORTED");}
        catch(Exception error){System.out.println("ERROR:"+error.getClass().getSimpleName());}
    }
}
