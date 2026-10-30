package ru.logunov.bydsplit;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.regex.*;

/** Emulator-only regression: embedded app -> HOME -> normal app launch -> Split, retaining task. */
public final class DisplayHandoffInstrumentation extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 private void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=input.read(b))>=0)out.write(b,0,n);return out.toString("UTF-8");}}
 private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private int[] task(String pkg,int target)throws Exception{int root=-1,display=-1;int[] fallback={-1,-1};for(String line:shell("am stack list").split("\n")){Matcher m=Pattern.compile("^RootTask id=(\\d+).*displayId=(\\d+)").matcher(line);if(m.find()){root=Integer.parseInt(m.group(1));display=Integer.parseInt(m.group(2));}if(line.trim().startsWith("taskId=")&&line.contains(pkg+"/")){fallback=new int[]{root,display};if(display==target)return fallback;}}return fallback;}
 private void waitFocus(String pkg)throws Exception{long until=SystemClock.elapsedRealtime()+5000;do{if(Pattern.compile("topResumedActivity=.*\\b"+Pattern.quote(pkg)+"/").matcher(shell("dumpsys activity activities")).find())return;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<until);throw new AssertionError("Foreground was not "+pkg);}
 private int[] waitTask(String pkg,int display)throws Exception{long until=SystemClock.elapsedRealtime()+8000;int[] t;do{t=task(pkg,display);if(t[1]==display)return t;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<until);throw new AssertionError("Task not on display "+display+": "+java.util.Arrays.toString(t));}
 public void onStart(){Bundle result=new Bundle();try{
  if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
  shell("am force-stop com.android.settings");
  AppPreferences.get(getTargetContext()).edit().remove(AppPreferences.KEY_FAR_APP).remove(AppPreferences.KEY_FAR_APPS).putString(AppPreferences.KEY_COMPACT_TARGET,AppPreferences.COMPACT_TARGET_VEHICLE).commit();
  Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true).putExtra("compact_on_left",true));SystemClock.sleep(800);
  AppEntry selected=null;for(AppEntry app:new AppRepository(activity).loadLaunchableApps())if(app.component.getPackageName().equals("com.android.settings")){selected=app;break;}if(selected==null)throw new AssertionError("Test app missing");final AppEntry app=selected;
  main(()->{try{Method m=MainActivity.class.getDeclaredMethod("activateLargeApp",AppEntry.class);m.setAccessible(true);m.invoke(activity,app);}catch(Exception e){throw new RuntimeException(e);}});
  SystemClock.sleep(700);EmbeddedAppPane pane=(EmbeddedAppPane)field(activity,"farEmbeddedPane");int embedded=pane.embeddedDisplayId();if(embedded<1)throw new AssertionError("No embedded display");
  int original=waitTask(app.component.getPackageName(),embedded)[0];String pid=shell("pidof com.android.settings").trim();
  for(int cycle=0;cycle<2;cycle++){
   shell("input keyevent KEYCODE_HOME");waitTask(app.component.getPackageName(),0);
   waitFocus("com.android.launcher3");
   long until=SystemClock.elapsedRealtime()+3000;while(pane.embeddedDisplayId()>0&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
   if(pane.embeddedDisplayId()>0)throw new AssertionError("Background virtual display retained");
   shell("am start -n "+app.component.flattenToString());waitFocus(app.component.getPackageName());SystemClock.sleep(1500);waitFocus(app.component.getPackageName());int[] standalone=waitTask(app.component.getPackageName(),0);if(standalone[0]!=original)throw new AssertionError("Task was recreated on handoff");
   shell("am start --display 0 -n ru.logunov.bydsplit/.MainActivity");
   until=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<until){pane=(EmbeddedAppPane)field(activity,"farEmbeddedPane");if(pane!=null&&pane.embeddedDisplayId()>0)break;SystemClock.sleep(100);}embedded=pane==null?-1:pane.embeddedDisplayId();
   int[] restored=waitTask(app.component.getPackageName(),embedded);if(restored[0]!=original)throw new AssertionError("Task was recreated on restore");
  }
  if(!pid.equals(shell("pidof com.android.settings").trim()))throw new AssertionError("Application process restarted");
  result.putString("result","PASS: two HOME/standard-launch/Split cycles, task and process retained, Home focus retained, background displays released and recreated");
 }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
