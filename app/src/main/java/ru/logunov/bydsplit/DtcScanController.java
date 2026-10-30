package ru.logunov.bydsplit;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;

/** One manual scan, lifetime-bound to its visible page. Last completed report survives failures. */
final class DtcScanController {
    private static DtcScanController instance;
    static synchronized DtcScanController get(Context c){if(instance==null)instance=new DtcScanController(c.getApplicationContext());return instance;}
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners=new LinkedHashSet<>();
    private final VehicleSleepController sleep;
    private DtcDescriptions descriptions;
    private HelperSession owner;
    private int generation;
    boolean running;private boolean preparing;int done;String message="";JSONObject report;private JSONObject demoReport;
    JSONObject displayedReport(){return AppPreferences.isDemoModeEnabled(context)?demoReport:report;}
    private final Runnable timeout=()->fail(generation,"Сканирование остановлено по времени. Последний отчёт сохранён.");
    private DtcScanController(Context c){context=c;sleep=VehicleSleepController.get(c);try{String saved=c.getSharedPreferences("dtc",0).getString("last","");if(!saved.isEmpty())report=new JSONObject(saved);}catch(Exception ignored){}}
    void addListener(Runnable listener){listeners.add(listener);listener.run();}
    void removeListener(Runnable listener){listeners.remove(listener);if(listeners.isEmpty())cancel();}
    private void publish(){for(Runnable r:new ArrayList<>(listeners))r.run();}
    void start(){
        if(running)return;
        if(AppPreferences.isDemoModeEnabled(context)){demoReport=demo();message="Пример отчёта · демонстрационные данные";publish();return;}
        if(sleep.mode()!=VehicleSleepState.Mode.RUNNING){message="Включите машину и установите селектор в P. Нужны свежие показания.";publish();return;}
        running=true;done=0;message="Проверка режима P и доступа к диагностике…";
        final int token=++generation;
        try {
            owner=HelperSession.observe(line->main.post(()->accept(token,line)),()->main.post(()->{if(!preparing)fail(token,"Связь со сканером потеряна. Последний отчёт сохранён.");}));
            String arguments=owner.arguments();
            sleep.addListener(this,()->{if(running&&!sleep.automationAllowed())cancel();});
            main.postDelayed(timeout,100000);publish();
            worker.execute(()->{try{LocalAdbManager.forDiagnostics(context).startDtcScan(arguments);}catch(Exception error){main.post(()->fail(token,"Сканер недоступен: проверьте локальное подключение ADB."));}});
        }catch(Exception error){fail(token,"Не удалось запустить сканер.");}
    }
    private void accept(int token,String line){
        if(token!=generation||!running||line.length()>65536)return;
        try {
            JSONObject value=new JSONObject(line);
            if("progress".equals(value.optString("kind"))){done=value.optInt("done");message="Проверено блоков: "+done+" из 18";publish();return;}
            if(!"finished".equals(value.optString("kind")))return;
            main.removeCallbacks(timeout);
            if(!"DONE".equals(value.optString("state"))){fail(token,reason(value.optString("reason")));return;}
            // The helper has finished and can exit while the offline descriptions load.
            preparing=true;if(owner!=null)owner.close();owner=null;
            message="Подготовка отчёта…";publish();
            worker.execute(()->{
                try{describe(value);}catch(Exception ignored){}
                main.post(()->{if(token!=generation)return;running=false;preparing=false;sleep.removeListener(this);report=value;done=18;message="Проверка завершена";
                    context.getSharedPreferences("dtc",0).edit().putString("last",value.toString()).apply();publish();});
            });
        }catch(Exception error){fail(token,"Не удалось прочитать ответ сканера. Последний отчёт сохранён.");}
    }
    private void describe(JSONObject scan)throws Exception {
        JSONArray results=scan.optJSONArray("results");if(results==null)return;
        boolean needed=false;for(int i=0;i<results.length();i++)if(!DtcProtocol.rows(DtcProtocol.unhex(results.getJSONObject(i).optString("list"))).isEmpty())needed=true;
        if(!needed)return;
        if(descriptions==null)descriptions=new DtcDescriptions(open("texts"),open("manuals"),open("rows"),open("models"));
        for(int i=0;i<results.length();i++){
            JSONObject result=results.getJSONObject(i);int address=result.getInt("address");
            String block=DtcProtocol.blockClass(address,DtcProtocol.unhex(result.optString("identity")));
            JSONObject texts=new JSONObject();
            for(DtcProtocol.Row row:DtcProtocol.rows(DtcProtocol.unhex(result.optString("list")))){
                DtcDescriptions.Description desc=descriptions.lookup(scan.optInt("model"),block,row.code);
                if(desc!=null)texts.put(row.code,new JSONObject().put("text",desc.text).put("exact",desc.exact));
            }
            result.put("descriptions",texts);
        }
    }
    private InputStreamReader open(String name)throws Exception{return new InputStreamReader(context.getAssets().open("dtc/"+name+".tsv"),java.nio.charset.StandardCharsets.UTF_8);}
    void cancel(){if(!running&&owner==null)return;generation++;running=false;preparing=false;main.removeCallbacks(timeout);if(owner!=null)owner.close();owner=null;sleep.removeListener(this);message="Сканирование остановлено. Последний отчёт сохранён.";publish();}
    private void fail(int token,String reason){if(token!=generation||!running)return;cancel();message=reason;publish();}
    private static String reason(String reason){
        switch(reason){case "guard_absent":return "На этой прошивке отсутствует статус штатного менеджера диагностики. Нужна адаптация сканера; запросы к блокам не отправлены.";case "not_park":return "Сканирование доступно только в P.";case "not_ready":return "Машина выключена: проверка остановлена.";case "diag_busy":case "passive_traffic":case "foreign_traffic":return "Диагностика занята другой программой. Повторите позже.";case "budget":return "Достигнут предел времени проверки. Последний отчёт сохранён.";case "no_frames":return "Блоки не ответили. Это не означает отсутствие ошибок.";default:return "Диагностика недоступна или прервана на этой прошивке. Последний отчёт сохранён.";}
    }
    private static JSONObject demo(){try{return new JSONObject().put("demo",true).put("at",System.currentTimeMillis()).put("model",0).put("results",new JSONArray()
            .put(new JSONObject().put("address",0x782).put("outcome","ok").put("count","5901FF010001").put("list","5902FFD1668709").put("identity","62F19432330000000B"))
            .put(new JSONObject().put("address",0x7e0).put("outcome","ok").put("count","5901FF010000").put("list","").put("identity",""))
            .put(new JSONObject().put("address",0x7e2).put("outcome","no_answer").put("count","").put("list","").put("identity","")));
        }catch(Exception impossible){return new JSONObject();}}
}
