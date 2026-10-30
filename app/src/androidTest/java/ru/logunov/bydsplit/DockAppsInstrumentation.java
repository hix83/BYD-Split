package ru.logunov.bydsplit;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Tests app shortcuts only on an emulator, never changes the owner's vehicle panel. */
public final class DockAppsInstrumentation extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 private void main(Runnable r){runOnMainSync(r);waitForIdleSync();}
 private Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 private void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
 private View find(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View match=find(((ViewGroup)v).getChildAt(i),text);if(match!=null)return match;}return null;}
 private void pointer(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();}
 private void drag(View from,View to){int[] a=new int[2],b=new int[2];from.getLocationOnScreen(a);to.getLocationOnScreen(b);float x=a[0]+from.getWidth()/2f,y=a[1]+from.getHeight()/2f,tx=b[0]+to.getWidth()/2f;long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(900);for(int i=1;i<=16;i++){pointer(down,MotionEvent.ACTION_MOVE,x+(tx-x)*i/16,y);SystemClock.sleep(35);}SystemClock.sleep(230);pointer(down,MotionEvent.ACTION_UP,tx,y);}
 public void onStart(){Bundle result=new Bundle();
  try{
   if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
   List<DockConfiguration.Item> decoded=DockConfiguration.decode("SONG,APP:com.android.settings/.Settings,APP:com.android.settings/com.android.settings.Settings,INVALID,SETTINGS");
   check(decoded.size()==3&&decoded.get(0)==DockConfiguration.Item.SONG&&decoded.get(1).isApp(),"legacy IDs or component deduplication failed");
   check(DockConfiguration.decode("APP:bad/;sh,SONG").size()==2,"invalid component accepted");
   List<DockConfiguration.Item> many=new ArrayList<>();for(int n=0;n<30;n++)many.add(DockConfiguration.Item.valueOf("APP:org.example.app"+n+"/.Main"));many.add(DockConfiguration.Item.SONG);many.add(DockConfiguration.Item.SETTINGS);
   StringBuilder raw=new StringBuilder();for(DockConfiguration.Item i:many)raw.append(i.name()).append(',');
   check(DockConfiguration.decode(raw.toString()).size()==20,"app shortcut limit bypassed");
   DockConfiguration.save(getTargetContext(),Arrays.asList(DockConfiguration.Item.SONG,DockConfiguration.Item.CAMERA,DockConfiguration.Item.SETTINGS),1);
   AppPreferences.get(getTargetContext()).edit().putBoolean(DockConfiguration.MODE,true).commit();
   Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));SystemClock.sleep(1200);waitForIdleSync();
   DynamicDockView dock=(DynamicDockView)field(activity,"dynamicDock");
   AppEntry selected=null;for(AppEntry e:new AppRepository(activity).loadLaunchableApps())if(e.component.getPackageName().equals("com.android.settings")){selected=e;break;}
   check(selected!=null,"settings app unavailable");final AppEntry app=selected;
   main(dock::performLongClick);Dialog editor=(Dialog)field(dock,"editorDialog");final Dialog firstEditor=editor;
   main(()->find(firstEditor.getWindow().getDecorView(),"APP").performClick());
   View choice=find(editor.getWindow().getDecorView(),"Добавить приложение: "+app.label);check(choice!=null,"APP page missing installed applications");
   main(choice::performClick);check(find(dock,"Удалить: "+app.label)!=null,"app minus missing");
   main(()->dock.closeEditor(false));check(DockConfiguration.read(activity).size()==3,"cancel persisted application");
   main(dock::performLongClick);editor=(Dialog)field(dock,"editorDialog");final Dialog modal=editor;
   main(()->find(modal.getWindow().getDecorView(),"APP").performClick());main(()->find(modal.getWindow().getDecorView(),"Добавить приложение: "+app.label).performClick());
   main(()->dock.closeEditor(true));check(DockConfiguration.read(activity).contains(DockConfiguration.Item.app(app)),"app save/resolve lost shortcut");
   main(dock::performLongClick);
   drag(find(dock,"Переместить: "+app.label),find(dock,"Переместить: BYD SONG"));SystemClock.sleep(450);waitForIdleSync();
   main(()->dock.closeEditor(true));check(DockConfiguration.read(activity).get(0).equals(DockConfiguration.Item.app(app)),"app drag order did not persist");
   View cell=null;ViewGroup row=(ViewGroup)field(dock,"dockRow");for(int n=0;n<row.getChildCount();n++)if(DockConfiguration.Item.app(app).equals(row.getChildAt(n).getTag()))cell=row.getChildAt(n);
   check(cell!=null,"saved app button missing");View appCell=cell;main(appCell::performClick);SystemClock.sleep(700);
   check(((AppEntry)field(activity,"farApp")).component.equals(app.component),"app did not target large pane");
   check((Boolean)field(activity,"showingVehicleDashboard"),"app replaced vehicle pane");
   View far=(View)field(activity,"farSlot"),compact=(View)field(activity,"compactPaneContainer");check(far.getWidth()>compact.getWidth()*1.8,"app target is not the 2/3 pane");
   main(()->{AppPreferences.setCompactPaneOnLeft(activity,false);MainActivity.applyPanelLayoutFromSettings();});
   check(far.getWidth()>compact.getWidth()*1.8&&far.getLeft()<compact.getLeft(),"swapped layout lost large app target");
   main(dock::performLongClick);editor=(Dialog)field(dock,"editorDialog");main(()->find(dock,"Удалить: "+app.label).performClick());main(()->dock.closeEditor(true));
   check(!DockConfiguration.read(activity).contains(DockConfiguration.Item.app(app)),"app removal did not persist");
   result.putString("result","PASS: legacy order, app IDs/dedup/limit, APP catalog, plus/minus, cancel/save, real app drag and saved order, large pane launch and swapped layout, remove");
  }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
  finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
 }
}
