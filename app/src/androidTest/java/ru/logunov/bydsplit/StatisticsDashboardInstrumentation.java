package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** UI fixtures live in a disposable database, never in the owner's journal. */
public final class StatisticsDashboardInstrumentation extends Instrumentation {
    private Activity activity;
    private StatisticsView page;
    private static final String DB="statistics_dashboard_preview.db";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(!Build.HARDWARE.contains("ranchu")&&!Build.HARDWARE.contains("goldfish"))throw new IllegalStateException("Emulator required");
            totals();
            getTargetContext().deleteDatabase(DB);
            AppPreferences.get(getTargetContext()).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,true).commit();
            seed();
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));
            for(int mode:new int[]{ConfigurationMode.NIGHT,ConfigurationMode.DAY}){
                String theme=mode==ConfigurationMode.NIGHT?"night":"day";
                runOnMainSync(()->{
                    android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
                    config.uiMode=(config.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|mode;
                    activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());
                    page=new StatisticsView(activity,()->{},0,30,new StatisticsStore(activity,DB));activity.setContentView(page);
                });shot("overview-"+theme);
                click("Поездки");shot("trips-"+theme);
                runOnMainSync(()->findTrip(page).performClick());shot("details-"+theme);
                click("Зарядки");shot("charges-"+theme);
                click("Заправки");shot("fuel-"+theme);
            }
            runOnMainSync(()->activity.finish());
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));
            runOnMainSync(()->{page=new StatisticsView(activity,()->{},0,30,new StatisticsStore(activity,DB));try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("openInternalPage",View.class,String.class);method.setAccessible(true);method.invoke(activity,page,"statistics");}catch(Exception ex){throw new IllegalStateException(ex);}});shot("in-launcher");
            // Exercise narrow pane reflow without changing the car/emulator display settings.
            runOnMainSync(()->{FrameLayout host=new FrameLayout(activity);page=new StatisticsView(activity,()->{},0,30,new StatisticsStore(activity,DB));host.addView(page,new FrameLayout.LayoutParams(Math.round(640*activity.getResources().getDisplayMetrics().density),-1));activity.setContentView(host);});shot("overview-narrow");
            String empty="statistics_dashboard_empty.db";getTargetContext().deleteDatabase(empty);
            runOnMainSync(()->activity.setContentView(new StatisticsView(activity,()->{},0,30,new StatisticsStore(activity,empty))));shot("empty");
            result.putString("stream","Coverage, rates, zero/missing data and mixed tariffs passed; day/night overview, journal, details, purchases, narrow and empty screenshots captured.\n");finish(0,result);
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(1,result);}
    }
    private static final class ConfigurationMode {static final int NIGHT=android.content.res.Configuration.UI_MODE_NIGHT_YES,DAY=android.content.res.Configuration.UI_MODE_NIGHT_NO;}
    private void seed(){try(StatisticsStore store=new StatisticsStore(getTargetContext(),DB)){
        Calendar date=Calendar.getInstance();date.set(Calendar.HOUR_OF_DAY,9);date.set(Calendar.MINUTE,10);date.set(Calendar.SECOND,0);
        for(int i=0;i<7;i++){
            long start=date.getTimeInMillis();long id=store.begin("trip",start,21.5,70);
            TripAccumulator t=new TripAccumulator();t.start=start;t.end=start+2100000;t.km=22+i*3.4;t.movingMs=1800000;t.observedMs=2100000;t.maxSpeed=82;t.usedKwh=4+i*.3;t.recoveredKwh=.8;
            if(i!=2)t.fuel.sample(120+i*2.);if(i!=2)t.fuel.sample(120+i*2.+.5+i*.1);
            store.checkpoint(id,t,false,"");date.add(Calendar.DAY_OF_YEAR,-1);
        }
        // A synthetic OEM session demonstrates provenance and replacement of its local observation.
        Calendar nativeDay=Calendar.getInstance();nativeDay.set(Calendar.HOUR_OF_DAY,9);nativeDay.set(Calendar.MINUTE,10);nativeDay.set(Calendar.SECOND,0);
        try{org.json.JSONArray row=new org.json.JSONArray();row.put(1).put(nativeDay.getTimeInMillis()/1000).put(nativeDay.getTimeInMillis()/1000+2100).put(2100).put(22).put(3.2).put(.5).put(0);
            store.importNative(new org.json.JSONArray().put(row),21.5,70);
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}
        store.addPurchase("charge",System.currentTimeMillis()-6000000,12.4,21.5,null,false);
        store.addPurchase("fuel",System.currentTimeMillis()-9000000,30,70,14500.,true);
    }}
    private static StatisticsStore.Entry trip(double km,Double fuel,Double energy){StatisticsStore.Entry e=new StatisticsStore.Entry();e.type="trip";e.km=km;e.litres=fuel;e.grid=energy;e.electricPrice=21.5;e.fuelPrice=70;return e;}
    private void totals(){
        StatisticsStore.Entry a=trip(10,1.,2.),b=trip(90,null,3.),c=trip(50,2.,null),zero=trip(0,0.,0.);
        StatisticsSummary s=new StatisticsSummary(Arrays.asList(a,b,c,zero));
        near(s.cost,113);near(s.pricedFuel+s.pricedElectricity,s.cost);near(s.costPerKm(),11.3);
        near(s.fuelPer100(),5);near(s.electricityPer100(),5);near(s.km,150);
        check(s.priced==2&&s.trips==4&&s.fuelKnown==3&&s.electricityKnown==3,"coverage");
        b.electricPrice=1;near(new StatisticsSummary(Arrays.asList(a,b)).electricityCost,46);
        StatisticsSummary empty=new StatisticsSummary(Collections.emptyList());check(empty.costPerKm()==null&&empty.fuelPer100()==null&&empty.electricityPer100()==null,"empty rates");
        StatisticsSummary stopped=new StatisticsSummary(Arrays.asList(zero));check(stopped.priced==1&&stopped.cost==0&&stopped.costPerKm()==null,"real zero");
        StatisticsSummary unknown=new StatisticsSummary(Arrays.asList(trip(20,null,null)));check(unknown.priced==0&&unknown.fuelKnown==0&&unknown.electricityKnown==0,"unknown");
    }
    private void click(String title){runOnMainSync(()->{View found=find(page,title);if(found==null)throw new AssertionError("Missing "+title);found.performClick();});waitForIdleSync();}
    private View findTrip(View v){if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith("Поездка "))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=findTrip(g.getChildAt(i));if(f!=null)return f;}}return null;}
    private View find(View v,String text){if(v instanceof Button&&text.equals(((Button)v).getText().toString()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=find(g.getChildAt(i),text);if(f!=null)return f;}}return null;}
    private void shot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(500);Bitmap bitmap=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getExternalFilesDir(null),"statistics-preview");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private static void near(Double actual,double expected){if(actual==null||Math.abs(actual-expected)>.001)throw new AssertionError(actual+" != "+expected);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
