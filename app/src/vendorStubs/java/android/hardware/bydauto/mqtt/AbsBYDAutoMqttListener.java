package android.hardware.bydauto.mqtt;
import android.hardware.bydauto.BYDAutoEventValue;
/** Compile-only signature, excluded from the APK. */
public abstract class AbsBYDAutoMqttListener {
    public abstract void onDataEventChanged(int eventType, BYDAutoEventValue value);
    public void onError(int code,String message) { }
}
