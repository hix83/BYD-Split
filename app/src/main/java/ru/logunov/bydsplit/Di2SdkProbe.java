package ru.logunov.bydsplit;

import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.*;

/** Shell entry point: inspect SDK metadata, without invoking vehicle methods. */
public final class Di2SdkProbe {
    private static final String[] CLASSES = {
        "android.hardware.bydauto.BYDAutoFeatureIds",
        "android.hardware.bydauto.setting.BYDAutoSettingDevice",
        "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice",
        "android.hardware.bydauto.light.BYDAutoLightDevice",
        "android.hardware.bydauto.audio.BYDAutoAudioDevice",
        "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",
        "android.hardware.bydauto.time.BYDAutoTimeDevice",
        "android.hardware.bydauto.test.BYDAutoTestDevice",
        "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice",
        "android.hardware.bydauto.adas.BYDAutoADASDevice",
        "android.hardware.bydauto.radar.BYDAutoRadarDevice",
        "android.hardware.bydauto.charging.BYDAutoChargingDevice",
        "android.hardware.bydauto.statistic.BYDAutoStatisticDevice",
        "android.hardware.bydauto.speed.BYDAutoSpeedDevice",
        "android.hardware.bydauto.energy.BYDAutoEnergyDevice",
        "android.hardware.bydauto.tyre.BYDAutoTyreDevice",
        "android.hardware.bydauto.ac.BYDAutoAcDevice",
        "android.hardware.bydauto.safetybelt.BYDAutoSafetyBeltDevice",
        "android.hardware.bydauto.engine.BYDAutoEngineDevice"
    };
    public static void main(String[] args) throws Exception {
        JSONObject result=new JSONObject();result.put("androidApi",Build.VERSION.SDK_INT);
        JSONArray entries=new JSONArray();
        for(String name:CLASSES){
            JSONObject entry=new JSONObject();entry.put("class",name);
            try {
                Class<?> type=Class.forName(name,false,Di2SdkProbe.class.getClassLoader());
                ArrayList<String> methods=new ArrayList<>();
                for(Method method:type.getDeclaredMethods())methods.add(method.toGenericString());
                Collections.sort(methods);entry.put("methods",new JSONArray(methods));
                if(name.endsWith("BYDAutoFeatureIds")){
                    JSONObject constants=new JSONObject();
                    for(Field field:type.getFields())if(field.getType()==int.class&&Modifier.isStatic(field.getModifiers())&&Modifier.isFinal(field.getModifiers()))
                        constants.put(field.getName(),field.getInt(null));
                    entry.put("featureIds",constants);
                }
            } catch(Throwable error){entry.put("unavailable",error.getClass().getSimpleName());}
            entries.put(entry);
        }
        result.put("sdk",entries);System.out.println(result.toString(2));
    }
}
