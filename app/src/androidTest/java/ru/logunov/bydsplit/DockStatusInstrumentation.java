package ru.logunov.bydsplit;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.*;
import static ru.logunov.bydsplit.ComfortState.Field.*;
public final class DockStatusInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();try{
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));waitForIdleSync();
        runOnMainSync(()->{try{
            ComfortController controller=ComfortController.get(activity);
            ComfortRequests requests=(ComfortRequests)field(controller,"requests").get(controller);
            long received=field(controller,"receivedAt").getLong(controller);
            String saved=AppPreferences.get(activity).getString(DockConfiguration.ITEMS,null);
            try{
                AppPreferences.get(activity).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,false).putString(DockConfiguration.ITEMS,"SONG,DRIVER_HEAT,DRIVER_VENT,WHEEL,SETTINGS").commit();
                requests.accept(ComfortState.parse("DRIVER_HEAT=3\nDRIVER_VENT=2\nWHEEL=1"));
                field(controller,"receivedAt").setLong(controller,SystemClock.elapsedRealtime());
                DynamicDockView dock=new DynamicDockView(activity,item->{});
                segments(dock,DockConfiguration.Item.DRIVER_HEAT,2,2);
                segments(dock,DockConfiguration.Item.DRIVER_VENT,1,2);
                segments(dock,DockConfiguration.Item.WHEEL,0,1);
                requests.request(DRIVER_HEAT,1);
                dock.setNavigation(false,false,false,false,false,false,"");
                segments(dock,DockConfiguration.Item.DRIVER_HEAT,1,2);
                check(controller.pending(DRIVER_HEAT)&&!controller.pending(DRIVER_VENT),"per-field pending missing");
                field(controller,"receivedAt").setLong(controller,SystemClock.elapsedRealtime()-6000);
                requests.clear();dock.setNavigation(false,false,false,false,false,false,"");
                segments(dock,DockConfiguration.Item.DRIVER_HEAT,-1,2);
                segments(dock,DockConfiguration.Item.DRIVER_VENT,-1,2);
                check(!text(dock,"НЕТ ДАННЫХ"),"unexpected no-data caption");
                AppPreferences.get(activity).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,true).commit();
                DynamicDockView demoDock=new DynamicDockView(activity,id->{});
                demoDock.setActive(true);
                try{
                    for(DockConfiguration.Item id:new DockConfiguration.Item[]{DockConfiguration.Item.DRIVER_HEAT,DockConfiguration.Item.DRIVER_VENT}){
                        for(int expected:new int[]{2,1,0,2}){
                            item(demoDock,id).performClick();segments(demoDock,id,expected,2);
                            check(!controller.pending(),"demo command entered live queue");
                        }
                    }
                    segments(demoDock,DockConfiguration.Item.DRIVER_HEAT,0,2);
                    item(demoDock,DockConfiguration.Item.WHEEL).performClick();segments(demoDock,DockConfiguration.Item.WHEEL,1,1);
                    item(demoDock,DockConfiguration.Item.WHEEL).performClick();segments(demoDock,DockConfiguration.Item.WHEEL,0,1);
                    check(!requests.hasPending(),"demo modified live requests");
                    AppPreferences.get(activity).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,false).commit();
                    check(!controller.available(),"demo sample leaked into live availability");
                }finally{demoDock.setActive(false);}
            }finally{requests.clear();requests.accept(ComfortState.parse(""));field(controller,"receivedAt").setLong(controller,received);AppPreferences.get(activity).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,true).putString(DockConfiguration.ITEMS,saved).commit();}
        }catch(Exception e){throw new RuntimeException(e);}});
        result.putString("result","PASS: indicators, stale state, demo button clicks 2/1/off/2, heat/vent exclusion, wheel on/off, no live requests or demo leakage");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
    private Field field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    private View item(View root,DockConfiguration.Item id){if(root.getTag()==id)return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=item(((ViewGroup)root).getChildAt(i),id);if(found!=null)return found;}return null;}
    private void segments(View root,DockConfiguration.Item id,int level,int max)throws Exception{
        ViewGroup cell=(ViewGroup)item(root,id),tile=(ViewGroup)cell.getChildAt(0);
        View indicator=tile.getChildAt(1);
        check(tile.getChildAt(0) instanceof android.widget.ImageView&&tile.getChildAt(2) instanceof TextView,"icon-indicators-caption order");
        check(field(indicator,"level").getInt(indicator)==level&&field(indicator,"maximum").getInt(indicator)==max,"incorrect active segments");
    }
    private boolean text(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(text(((ViewGroup)view).getChildAt(i),value))return true;return false;}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
