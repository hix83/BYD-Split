package ru.logunov.bydsplit;
import android.os.IBinder;
import android.os.Parcel;
/** Comfort addresses come from this firmware's SDK, never another vehicle's constants. */
public final class ComfortCommandWriter {
    private static final String[] NAMES={"STEERING_WHEEL_HEAT","DRIVER_SEAT_HEATING","PASSENGER_SEAT_HEATING","DRIVER_SEAT_VENTILATING","PASSENGER_SEAT_VENTILATING"};
    static int transact(IBinder service,int tx,int device,int fid,int value)throws Exception{
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{data.writeInterfaceToken(service.getInterfaceDescriptor());data.writeInt(device);data.writeInt(fid);if(tx==6)data.writeInt(value);
            if(!service.transact(tx,data,reply,0))throw new IllegalStateException("transact");int status=reply.readInt();if(status<0)return status;return tx==6?status:reply.readInt();
        }finally{data.recycle();reply.recycle();}
    }
    public static void main(String[] args){
        HelperLifetime.limit(5000);
        try{
            int action=Integer.parseInt(args[0]),level=Integer.parseInt(args[1]);
            if(action<0||action>4||level<0||level>(action==0?1:3))throw new IllegalArgumentException();
            Class<?> setting=Class.forName("android.hardware.bydauto.BYDAutoFeatureIds$Setting");
            int read=setting.getField("SET_"+NAMES[action]+"_STATE").getInt(null);
            int write=setting.getField("SET_"+NAMES[action]+"_STATE_SET").getInt(null);
            int device=Class.forName("android.hardware.bydauto.BYDAutoConstants").getField("BYDAUTO_DEVICE_SETTING").getInt(null);
            IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"autoservice");
            int before=transact(service,5,device,read,0),wanted=level+1;
            if(before<1||before>(action==0?2:4)){System.out.println("UNAVAILABLE");return;}
            if(before==wanted){System.out.println("OK:"+wanted);return;}
            int result=transact(service,6,device,write,wanted);
            if(result<0){System.out.println("REFUSED:"+result);return;}
            for(int i=0;i<8;i++){Thread.sleep(250);if(transact(service,5,device,read,0)==wanted){System.out.println("OK:"+wanted);return;}}
            System.out.println("UNCONFIRMED");
        }catch(Exception e){System.out.println("ERROR:"+e.getClass().getSimpleName());}
        finally{HelperLifetime.exit();}
    }
}
