package ru.logunov.bydsplit;
import android.os.*;
/** Uses the USB service's permission-checked API to trust only this app's public ADB key. */
public final class AdbAuthorizationHelper {
 public static void main(String[] args)throws Exception {
  Class<?> amType=Class.forName("android.app.IActivityManager");Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);Binder token=new Binder();
  Object holder=amType.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class).invoke(am,"ru.logunov.bydsplit.display",0,token);
  try {
   Object provider=holder.getClass().getField("provider").get(holder);
   Bundle reply=(Bundle)Class.forName("android.content.IContentProvider").getMethod("call",String.class,String.class,String.class,Bundle.class).invoke(provider,"com.android.shell","adbPublicKey",null,null);
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"usb");
   Object usb=Class.forName("android.hardware.usb.IUsbManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   Class.forName("android.hardware.usb.IUsbManager").getMethod("allowUsbDebugging",boolean.class,String.class).invoke(usb,true,reply.getString("key"));
   System.out.println("BYD_SPLIT_ADB_AUTHORIZED");
  }finally{amType.getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(am,"ru.logunov.bydsplit.display",token);}
 }
}
