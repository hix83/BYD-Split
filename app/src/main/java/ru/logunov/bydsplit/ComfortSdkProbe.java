package ru.logunov.bydsplit;
/** Read-only diagnostic of the running firmware's comfort constants. */
public final class ComfortSdkProbe {
    public static void main(String[] args) throws Exception {
        for(String name:new String[]{"android.hardware.bydauto.BYDAutoFeatureIds","android.hardware.bydauto.ac.BYDAutoAcDevice","android.hardware.bydauto.BYDAutoConstants"}) {
            try {
                Class<?> root=Class.forName(name);
                java.util.List<Class<?>> classes=new java.util.ArrayList<>();classes.add(root);java.util.Collections.addAll(classes,root.getDeclaredClasses());
                for(Class<?> c:classes)for(java.lang.reflect.Field f:c.getDeclaredFields()) {
                    String n=f.getName().toUpperCase(java.util.Locale.ROOT);
                    if(java.lang.reflect.Modifier.isStatic(f.getModifiers())&&f.getType()==int.class
                        &&(n.contains("HEAT")||n.contains("VENT")||n.contains("SEAT")||n.contains("STEER")||n.contains("POWER"))) {
                        try {f.setAccessible(true);System.out.println(c.getSimpleName()+"."+f.getName()+"="+f.getInt(null));}catch(Exception ignored){}
                    }
                }
                if(name.contains("AcDevice"))for(java.lang.reflect.Method m:root.getDeclaredMethods())if(m.getName().toLowerCase().matches(".*(heat|vent|seat|steer).*") )System.out.println(m.toString());
            }catch(ClassNotFoundException ignored){}
        }
    }
}
