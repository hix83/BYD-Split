package ru.logunov.bydsplit;

import java.lang.reflect.*;
import java.util.*;

/** One bounded handoff of existing tasks. Never kills, relaunches or resets an application. */
public final class TaskDisplayBridge {
    public static void main(String[] args){
        HelperLifetime.limit(5000);
        try{
            HelperLifetime.attach(args);
            boolean restore="restore".equals(args[0]);
            int source=Integer.parseInt(args[restore?1:0]);
            String pkg=restore?args[2]:null;
            if(restore&&(pkg==null||!pkg.matches("[A-Za-z0-9_.]+")))throw new IllegalArgumentException("Package required");
            if(source<1||source>999)throw new IllegalArgumentException("Source must be an embedded display");
            Class<?> api=Class.forName("android.app.IActivityTaskManager");
            Object service=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Method list=api.getMethod("getAllRootTaskInfos");
            Class<?> transaction=Class.forName("android.window.WindowContainerTransaction");
            Class<?> token=Class.forName("android.window.WindowContainerToken");
            Class<?> organizer=Class.forName("android.window.WindowOrganizer");
            Object changes=transaction.getConstructor().newInstance();
            List<Object> roots=new ArrayList<>();
            for(Object info:(List<?>)list.invoke(service)){
                int display=info.getClass().getField("displayId").getInt(info);
                if(!restore){if(display==source)roots.add(info);continue;}
                android.content.ComponentName top=(android.content.ComponentName)info.getClass().getField("topActivity").get(info);
                if(top!=null&&pkg.equals(top.getPackageName())&&(display==0||display==source))roots.add(info);
            }
            if(restore)Collections.reverse(roots);
            for(Object info:roots){
                int id=info.getClass().getField("taskId").getInt(info);
                int target=restore?source:0;
                if(info.getClass().getField("displayId").getInt(info)!=target)api.getMethod("moveRootTaskToDisplay",int.class,int.class).invoke(service,id,target);
                // Keep the launcher / application the user just opened in front on handoff.
                transaction.getMethod("reorder",token,boolean.class).invoke(changes,info.getClass().getField("token").get(info),restore);
            }
            if(!roots.isEmpty())organizer.getMethod("applyTransaction",transaction).invoke(organizer.getConstructor().newInstance(),changes);
            if(!restore)for(Object info:(List<?>)list.invoke(service))if(info.getClass().getField("displayId").getInt(info)==source)throw new IllegalStateException("Embedded tasks remain");
            System.out.println((restore?"RESTORED:":"HANDED_OFF:")+roots.size());
        }catch(Throwable error){android.util.Log.w("BYD_TASK_HANDOFF","Cannot hand off embedded tasks",error);System.out.println("HANDOFF_ERROR:"+error.getClass().getSimpleName());}
        finally{HelperLifetime.exit();}
    }
}
