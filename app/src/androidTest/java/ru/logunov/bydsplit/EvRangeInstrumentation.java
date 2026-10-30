package ru.logunov.bydsplit;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator-only UI check, deliberately leaves the demo widget visible for review. */
public final class EvRangeInstrumentation extends Instrumentation {
    private int theme=1;
    public void onCreate(Bundle args){super.onCreate(args);theme=args.getInt("theme",1);start();}
    private AlertDialog dialog;
    public void onStart(){
        Bundle result=new Bundle();
        try{
            check(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"Emulator required");
            AppPreferences.get(getTargetContext()).edit().putInt("theme_mode",theme).putBoolean(DockConfiguration.MODE,true).putInt(DockConfiguration.HEIGHT_PERCENT,100)
                .putBoolean(EvRangeController.SHOW_STATUS,true)
                .putFloat(EvRangeController.CAPACITY,18.3f).putFloat("ev_range_consumption",99f).putFloat(EvRangeController.RESERVE,25f).commit();
            DockConfiguration.save(getTargetContext(),Arrays.asList(DockConfiguration.Item.SONG,DockConfiguration.Item.EV_RANGE,DockConfiguration.Item.DRIVER_HEAT,DockConfiguration.Item.SETTINGS),1);
            Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));waitForIdleSync();
            main(()->{try{Method show=MainActivity.class.getDeclaredMethod("showVehicleDashboard",boolean.class);show.setAccessible(true);show.invoke(activity,false);}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(1200);waitForIdleSync();
            DynamicDockView dock=(DynamicDockView)field(activity,"dynamicDock");
            check(find(activity.getWindow().getDecorView(),EvRangeWidget.class)!=null,"widget missing");
            main(()->{EvRangeController c=EvRangeController.get(activity);check(c.ev()!=null&&c.ev()==72,"native demo range");check(Math.abs(c.forecast()-48.276)<.02,"forecast");check(c.calculatedText().equals("EV ≈ 48 км"),"EV must show our calculation, not native range");});
            View grip=description(activity.getWindow().getDecorView(),"Потяните вниз, чтобы показать управление панелью");
            float density=activity.getResources().getDisplayMetrics().density;
            check(grip!=null&&Math.abs(grip.getHeight()-48*density)<=1&&Math.abs(grip.getWidth()-180*density)<=1,"grip touch area");
            int[] xy=new int[2];grip.getLocationOnScreen(xy);long down=SystemClock.uptimeMillis();float x=xy[0]+grip.getWidth()/2f,y=xy[1]+44*density;
            pointer(down,MotionEvent.ACTION_DOWN,x,y);
            for(int i=1;i<=15;i++){pointer(down,MotionEvent.ACTION_MOVE,x,y+i*30);SystemClock.sleep(20);}
            pointer(down,MotionEvent.ACTION_UP,x,y+450);SystemClock.sleep(350);waitForIdleSync();
            View picker=(View)field(activity,"pickerOverlay");check(picker!=null&&Math.abs(picker.getTranslationY())<1,"lower grip swipe failed");shot("ev-range-picker");
            main(()->{try{Method hide=MainActivity.class.getDeclaredMethod("hidePicker");hide.setAccessible(true);hide.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
            for(int percent:new int[]{80,100,150}){
                main(()->{AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT,percent).commit();MainActivity.applyDockSizeFromSettings();});
                ViewGroup row=(ViewGroup)field(dock,"dockRow");check(row.getChildAt(1).getWidth()==row.getChildAt(0).getWidth()*3,"widget width "+percent);shot("ev-range-"+percent);
            }
            main(()->{AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT,100).commit();MainActivity.applyDockSizeFromSettings();dock.performLongClick();});
            Dialog editor=(Dialog)field(dock,"editorDialog");
            main(()->description(editor.getWindow().getDecorView(),"Удалить: "+DockConfiguration.Item.EV_RANGE.title).performClick());
            SystemClock.sleep(300);waitForIdleSync();
            View choice=description(editor.getWindow().getDecorView(),"Добавить: "+DockConfiguration.Item.EV_RANGE.title);
            check(choice!=null&&choice.getGlobalVisibleRect(new android.graphics.Rect()),"removed widget is not visible in library");shot("ev-range-library");
            main(choice::performClick);
            View from=description(editor.getWindow().getDecorView(),"Переместить: "+DockConfiguration.Item.EV_RANGE.title),to=description(editor.getWindow().getDecorView(),"Переместить: "+DockConfiguration.Item.SONG.title);
            drag(from,to);SystemClock.sleep(500);waitForIdleSync();
            main(()->dock.closeEditor(true));
            check(DockConfiguration.read(activity).get(0)==DockConfiguration.Item.EV_RANGE,"widget drag order was not saved");
            Object dashboard=field(activity,"vehicleDashboardView"),rangeListener=field(dashboard,"rangeChanged");
            main(()->{AppPreferences.get(activity).edit().putBoolean(EvRangeController.SHOW_STATUS,false).commit();MainActivity.applyEvStatusFromSettings();});
            check(!((Set<?>)field(EvRangeController.get(activity),"listeners")).contains(rangeListener),"hidden status still observes EV");shot("ev-range-status-off");
            main(()->{AppPreferences.get(activity).edit().putBoolean(EvRangeController.SHOW_STATUS,true).commit();MainActivity.applyEvStatusFromSettings();});
            check(((Set<?>)field(EvRangeController.get(activity),"listeners")).contains(rangeListener),"status observer not restored");
            main(()->{AppPreferences.get(activity).edit().putInt(DockConfiguration.HEIGHT_PERCENT,100).commit();MainActivity.applyDockSizeFromSettings();dialog=EvRangeSettings.show(activity);});
            shot("ev-range-settings");
            List<EditText> inputs=new ArrayList<>();collect(dialog.getWindow().getDecorView(),inputs);
            check(inputs.size()==2,"manual consumption field still present");
            main(()->{inputs.get(0).setText("0");button(dialog.getWindow().getDecorView(),"Сохранить").performClick();});
            check(dialog.isShowing()&&inputs.get(0).getError()!=null,"invalid capacity accepted");
            main(()->{inputs.get(0).setText("26,6");button(dialog.getWindow().getDecorView(),"Сохранить").performClick();});
            check(!dialog.isShowing(),"save did not close");
            check(Math.abs(AppPreferences.get(activity).getFloat(EvRangeController.CAPACITY,0)-26.6f)<.001,"settings not saved");
            main(()->{AppPreferences.get(activity).edit().putFloat(EvRangeController.CAPACITY,18.3f).commit();EvRangeController.get(activity).notifyChanged();});shot("ev-range-final");
            result.putString("result","PASS: lower grip swipe, EV/forecast, 80/100/150%, visible library after removal, add and real widget drag with saved order, status toggle, validation and settings save");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{if(dialog!=null)main(()->dialog.dismiss());}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private View find(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type);if(found!=null)return found;}return null;}
    private void collect(View v,List<EditText> list){if(v instanceof EditText)list.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),list);}
    private Button button(View v,String title){if(v instanceof Button&&title.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button found=button(((ViewGroup)v).getChildAt(i),title);if(found!=null)return found;}return null;}
    private void shot(String name)throws Exception{SystemClock.sleep(250);android.graphics.Bitmap b=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private View description(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=description(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private void pointer(long down,int action,float x,float y){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();}
    private void drag(View from,View to){int[] a=new int[2],b=new int[2];from.getLocationOnScreen(a);to.getLocationOnScreen(b);float x=a[0]+from.getWidth()/2f,y=a[1]+from.getHeight()/2f,tx=b[0]+to.getWidth()/2f;long down=SystemClock.uptimeMillis();pointer(down,MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(900);for(int i=1;i<=16;i++){pointer(down,MotionEvent.ACTION_MOVE,x+(tx-x)*i/16,y);SystemClock.sleep(35);}SystemClock.sleep(230);pointer(down,MotionEvent.ACTION_UP,tx,y);}
}
