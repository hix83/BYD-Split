package ru.logunov.bydsplit;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator-only assignment check with installed media and messenger applications. */
public final class DockAssignmentInstrumentation extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private void main(Runnable action){runOnMainSync(action);waitForIdleSync();}
 private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private View description(View v,String value){if(value.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View x=description(((ViewGroup)v).getChildAt(n),value);if(x!=null)return x;}return null;}
 private ListView list(View v){if(v instanceof ListView)return (ListView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){ListView l=list(((ViewGroup)v).getChildAt(n));if(l!=null)return l;}return null;}
 private boolean contains(List<AppEntry> apps,String pkg){for(AppEntry app:apps)if(pkg.equals(app.component.getPackageName()))return true;return false;}
 private void choose(DynamicDockView dock,String pkg)throws Exception{
  AlertDialog dialog=(AlertDialog)field(dock,"assignmentDialog");check(dialog!=null&&dialog.isShowing(),"assignment picker missing");
  ListView entries=list(dialog.getWindow().getDecorView());int position=-1;
  for(int n=0;n<entries.getAdapter().getCount();n++)if(((AppEntry)entries.getAdapter().getItem(n)).component.getPackageName().equals(pkg)){position=n;break;}
  check(position>=0,"requested category app missing: "+pkg);final int at=position;
  main(()->entries.performItemClick(entries.getAdapter().getView(at,null,entries),at,at));
 }
 public void onStart(){Bundle result=new Bundle();try{
  if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
  List<AppEntry> music=new AppRepository(getTargetContext()).loadShortcutApps(true),messages=new AppRepository(getTargetContext()).loadShortcutApps(false);
  check(contains(music,"ru.yandex.music")&&!contains(music,"ru.oneme.app"),"music category filter");
  check(contains(messages,"ru.oneme.app")&&!contains(messages,"ru.yandex.music"),"messenger category filter");
  AppPreferences.get(getTargetContext()).edit().putBoolean(DockConfiguration.MODE,true).remove(AppPreferences.KEY_QUICK_MUSIC_APP).remove(AppPreferences.KEY_QUICK_MAX_APP).commit();
  DockConfiguration.save(getTargetContext(),Arrays.asList(DockConfiguration.Item.SONG,DockConfiguration.Item.MUSIC,DockConfiguration.Item.MESSAGES,DockConfiguration.Item.CAMERA,DockConfiguration.Item.SETTINGS),1);
  Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true).putExtra("compact_on_left",true));SystemClock.sleep(700);waitForIdleSync();
  DynamicDockView dock=(DynamicDockView)field(activity,"dynamicDock");main(dock::performLongClick);
  View pencil=description(dock,"Назначить приложение: Музыка"),minus=description(dock,"Удалить: Музыка");check(pencil!=null&&minus!=null,"pencil/minus missing");
  int[] p=new int[2],m=new int[2];pencil.getLocationOnScreen(p);minus.getLocationOnScreen(m);check(p[0]<m[0],"pencil must be left of minus");
  check(description(dock,"Назначить приложение: Камеры")==null,"pencil added to unrelated function");
  main(pencil::performClick);choose(dock,"ru.yandex.music");check(!AppPreferences.get(activity).contains(AppPreferences.KEY_QUICK_MUSIC_APP),"assignment saved before Done");
  main(()->dock.closeEditor(false));check(!AppPreferences.get(activity).contains(AppPreferences.KEY_QUICK_MUSIC_APP),"Cancel saved assignment");
  main(dock::performLongClick);main(()->description(dock,"Назначить приложение: Музыка").performClick());choose(dock,"ru.yandex.music");main(()->dock.closeEditor(true));
  String selected=AppPreferences.get(activity).getString(AppPreferences.KEY_QUICK_MUSIC_APP,"");check(ComponentName.unflattenFromString(selected).getPackageName().equals("ru.yandex.music"),"music assignment not saved");
  check(((AppEntry)field(activity,"quickMusicApp")).component.flattenToString().equals(selected),"music callback still uses old assignment");
  main(dock::performLongClick);main(()->description(dock,"Назначить приложение: Сообщения").performClick());choose(dock,"ru.oneme.app");main(()->dock.closeEditor(true));
  selected=AppPreferences.get(activity).getString(AppPreferences.KEY_QUICK_MAX_APP,"");check(ComponentName.unflattenFromString(selected).getPackageName().equals("ru.oneme.app"),"messenger assignment not saved");
  check(((AppEntry)field(activity,"quickMaxApp")).component.flattenToString().equals(selected),"messages callback still uses old assignment");
  result.putString("result","PASS: category lists, left pencil/right minus, only Music/Messages, staged assignment, Cancel/Done, live launch target refresh");
 }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
