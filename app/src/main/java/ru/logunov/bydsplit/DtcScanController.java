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
    private DtcDescriptions descriptions;private boolean refreshingDescriptions;
    private HelperSession owner;
    private int generation;
    boolean running;private boolean preparing;boolean erasing;int done,total=18;String message="";JSONObject report;private JSONObject demoReport;
    JSONObject displayedReport(){return AppPreferences.isDemoModeEnabled(context)?demoReport:report;}
    private final Runnable timeout=()->fail(generation,"Операция остановлена по времени. Последний отчёт сохранён.");
    private DtcScanController(Context c){context=c;sleep=VehicleSleepController.get(c);try{String saved=c.getSharedPreferences("dtc",0).getString("last","");if(!saved.isEmpty())report=new JSONObject(saved);}catch(Exception ignored){}}
    void addListener(Runnable listener){listeners.add(listener);listener.run();refreshSavedDescriptions();}
    void removeListener(Runnable listener){listeners.remove(listener);if(listeners.isEmpty())cancel();}
    private void publish(){for(Runnable r:new ArrayList<>(listeners))r.run();}
    void start(){
        if(running)return;
        if(AppPreferences.isDemoModeEnabled(context)){demoReport=demo();message="Пример отчёта · демонстрационные данные";publish();return;}
        if(sleep.mode()!=VehicleSleepState.Mode.RUNNING){message="Включите машину и установите селектор в P. Нужны свежие показания.";publish();return;}
        erasing=false;total=18;running=true;done=0;message="Проверка режима P и доступа к диагностике…";
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
            if("progress".equals(value.optString("kind"))){done=value.optInt("done");total=value.optInt("total",18);message=erasing?("reread".equals(value.optString("phase"))?"Повторное чтение":"Стирание")+" · "+done+" из "+total:"details".equals(value.optString("phase"))?"Коды прочитаны · читаю имена блоков и стоп-кадры…":"Проверено блоков: "+done+" из 18";publish();return;}
            if(!"finished".equals(value.optString("kind")))return;
            main.removeCallbacks(timeout);
            if(erasing){finishErase(token,value);return;}
            if(!"DONE".equals(value.optString("state"))){fail(token,reason(value.optString("reason")));return;}
            // The helper has finished and can exit while the offline descriptions load.
            preparing=true;if(owner!=null)owner.close();owner=null;
            message="Подготовка отчёта…";publish();
            worker.execute(()->{
                try{describe(value);}catch(Exception ignored){}
                main.post(()->{if(token!=generation)return;running=false;preparing=false;sleep.removeListener(this);report=value;done=18;message="Проверка завершена";
                    context.getSharedPreferences("dtc",0).edit().putString("last",value.toString()).apply();publish();autoSend();});
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
                if(desc!=null)texts.put(row.code,new JSONObject().put("text",desc.text).put("exact",desc.exact).put("source",desc.source).put("manual",desc.manual));
            }
            result.put("descriptions",texts);
        }
        scan.put("descriptionRevision",DtcDescriptions.REVISION);
    }
    // Offline enrichment only: no helper, CAN requests, fresh scan date or automatic send.
    private void refreshSavedDescriptions(){
        if(running||refreshingDescriptions||report==null||AppPreferences.isDemoModeEnabled(context)||DtcDescriptions.REVISION.equals(report.optString("descriptionRevision")))return;
        final JSONObject original=report;final int token=generation;final JSONObject updated;
        try{updated=new JSONObject(original.toString());}catch(Exception e){return;}
        refreshingDescriptions=true;
        worker.execute(()->{
            boolean ok=false;try{describe(updated);ok=true;}catch(Exception ignored){}final boolean success=ok;
            main.post(()->{refreshingDescriptions=false;if(!success||running||generation!=token||report!=original)return;report=updated;persist();publish();});
        });
    }
    private InputStreamReader open(String name)throws Exception{return new InputStreamReader(context.getAssets().open("dtc/"+name+".tsv"),java.nio.charset.StandardCharsets.UTF_8);}
    int[] eligible(){return DtcReport.eligible(displayedReport(),System.currentTimeMillis());}
    void erase(int[] targets){
        if(running)return;
        Set<Integer> eligible=new HashSet<>();for(int a:eligible())eligible.add(a);
        Set<Integer> unique=new HashSet<>();
        if(targets==null||targets.length==0){message="Нужна свежая проверка с прочитанными кодами.";publish();return;}
        for(int address:targets)if(!eligible.contains(address)||!unique.add(address)){message="Отчёт устарел или результат стирания неизвестен. Выполните новую проверку.";publish();return;}
        boolean demo=AppPreferences.isDemoModeEnabled(context);
        if(!demo&&sleep.mode()!=VehicleSleepState.Mode.RUNNING){message="Стирание доступно при включённой машине в P и скорости 0.";publish();return;}
        try{
            JSONObject current=displayedReport();
            if(!demo&&!context.getSharedPreferences("dtc",0).edit().putString("before_clear",current.toString()).putString("before_clear_text",DtcReport.text(current)).commit())throw new IllegalStateException("archive_failed");
            JSONObject locked=current.optJSONObject("eraseLocked");if(locked==null)locked=new JSONObject();JSONArray pending=new JSONArray();
            for(int address:targets){locked.put(Integer.toString(address),true);pending.put(new JSONObject().put("address",address).put("confirm","unconfirmed").put("reason","interrupted").put("verdict","Результат неизвестен · требуется повторное чтение"));}
            current.put("eraseLocked",locked).put("lastErase",new JSONObject().put("state","RUNNING").put("at",System.currentTimeMillis()).put("erase",pending));if(!demo&&!context.getSharedPreferences("dtc",0).edit().putString("last",current.toString()).commit())throw new IllegalStateException("archive_failed");
            if(demo){
                JSONArray demoResults=new JSONArray();for(int address:targets)demoResults.put(new JSONObject().put("address",address).put("confirm","confirmed").put("reread",new JSONObject().put("address",address).put("outcome","ok").put("count","5901FF010000").put("list","").put("identity","")));
                erasing=true;running=true;total=targets.length*2;finishErase(++generation,new JSONObject().put("mode","erase").put("state","DONE").put("at",System.currentTimeMillis()).put("erase",demoResults));return;
            }
            erasing=true;running=true;preparing=false;done=0;total=targets.length*2;int token=++generation;
            message="Проверка P, нулевой скорости и свободной диагностики…";
            owner=HelperSession.observe(line->main.post(()->accept(token,line)),()->main.post(()->{if(!preparing)fail(token,"Стирание прервано. Результат неизвестен; исходный отчёт сохранён.");}));
            String args=owner.arguments();int[] selected=targets.clone();
            sleep.addListener(this,()->{if(running&&!sleep.automationAllowed())cancel();});main.postDelayed(timeout,130000);publish();
            worker.execute(()->{try{LocalAdbManager.forDiagnostics(context).startDtcErase(selected,args);}catch(Exception e){main.post(()->fail(token,"Не удалось запустить стирание. Исходный отчёт сохранён."));}});
        }catch(Exception e){if(running)cancel();message="Не удалось подготовить стирание. Выполните новую проверку.";publish();}
    }
    private void persist(){if(!AppPreferences.isDemoModeEnabled(context)&&report!=null)context.getSharedPreferences("dtc",0).edit().putString("last",report.toString()).apply();}
    private void finishErase(int token,JSONObject value)throws Exception{
        main.removeCallbacks(timeout);preparing=true;if(owner!=null)owner.close();owner=null;
        final JSONObject updated=new JSONObject(displayedReport().toString());
        worker.execute(()->{
            try{
                JSONObject locked=updated.optJSONObject("eraseLocked");if(locked==null)locked=new JSONObject();
                JSONObject previous=updated.optJSONObject("lastErase");JSONArray all=previous==null?new JSONArray():previous.optJSONArray("erase");if(all==null)all=new JSONArray();
                JSONArray received=value.optJSONArray("erase");JSONArray blocks=updated.getJSONArray("results");
                if(received!=null)for(int n=0;n<received.length();n++){
                    JSONObject item=received.getJSONObject(n);int address=item.getInt("address");
                    if(!locked.optBoolean(Integer.toString(address)))continue;
                    DtcEraseEngine.Result result=new DtcEraseEngine.Result(address);result.confirm=item.optString("confirm","unconfirmed");result.reason=item.optString("reason");if(item.has("nrc"))result.nrc=item.getInt("nrc");
                    JSONObject after=item.optJSONObject("reread");if(after!=null)result.reread=DtcReport.decode(after);
                    boolean verified=DtcEraseEngine.verified(result.reread);item.put("verified",verified).put("verdict",DtcEraseEngine.verdict(result));
                    if(verified||result.confirm.equals("refused")||result.confirm.equals("not_run"))locked.remove(Integer.toString(address));
                    for(int i=0;i<blocks.length();i++){
                        JSONObject block=blocks.getJSONObject(i);if(block.optInt("address")!=address)continue;
                        if(verified){after.put("identity",block.optString("identity")).put("name",block.optString("name")).put("nameOutcome",block.optString("nameOutcome"));blocks.put(i,after);}
                        else if(!result.confirm.equals("not_run")&&!result.confirm.equals("refused"))block.put("staleAfterErase",true);
                    }
                    for(int i=0;i<all.length();i++)if(all.getJSONObject(i).optInt("address")==address){all.put(i,item);break;}
                }
                updated.put("eraseLocked",locked).put("lastErase",new JSONObject(value.toString()).put("erase",all));describe(updated);
                main.post(()->{if(token!=generation)return;running=false;erasing=false;preparing=false;sleep.removeListener(this);if(AppPreferences.isDemoModeEnabled(context))demoReport=updated;else report=updated;
                    message=value.optString("state").equals("DONE")?"Стирание и повторное чтение завершены. Проверьте результат каждого блока.":"Операция прервана: "+reason(value.optString("reason"));persist();publish();autoSend();});
            }catch(Exception e){main.post(()->fail(token,"Не удалось обработать результат стирания. Исходный отчёт сохранён."));}
        });
    }
    private void autoSend(){TelegramBotController telegram=TelegramBotController.get(context);if(!AppPreferences.isDemoModeEnabled(context)&&telegram.store.connected()&&telegram.store.automatic())telegram.send(this,report);}
    void cancel(){
        TelegramBotController.get(context).cancel(this);
        if(!running&&owner==null)return;
        if(erasing){try{JSONObject current=displayedReport();JSONObject last=current==null?null:current.optJSONObject("lastErase");if(last!=null)last.put("state","ABORTED").put("reason","interrupted");persist();}catch(Exception ignored){}}
        generation++;running=false;erasing=false;preparing=false;main.removeCallbacks(timeout);if(owner!=null)owner.close();owner=null;sleep.removeListener(this);message="Операция остановлена. Последний отчёт сохранён.";publish();
    }
    private void fail(int token,String reason){if(token!=generation||!running)return;cancel();message=reason;publish();}
    private static String reason(String reason){
        switch(reason){case "guard_absent":return "На этой прошивке отсутствует статус штатного менеджера диагностики. Нужна адаптация сканера; запросы к блокам не отправлены.";case "speed_unknown_or_moving":return "Стирание остановлено: скорость должна достоверно равняться 0.";case "invalid_targets":return "Недопустимый список блоков.";case "not_park":return "Сканирование доступно только в P.";case "not_ready":return "Машина выключена: проверка остановлена.";case "diag_busy":case "passive_traffic":case "foreign_traffic":return "Диагностика занята другой программой. Повторите позже.";case "budget":return "Достигнут предел времени проверки. Последний отчёт сохранён.";case "no_frames":return "Блоки не ответили. Это не означает отсутствие ошибок.";default:return "Диагностика недоступна или прервана на этой прошивке. Последний отчёт сохранён.";}
    }
    private static JSONObject demo(){try{return new JSONObject().put("demo",true).put("at",System.currentTimeMillis()).put("model",0).put("results",new JSONArray()
            .put(new JSONObject().put("address",0x782).put("outcome","ok").put("count","5901FF010001").put("list","5902FFD1668709").put("identity","62F19432330000000B"))
            .put(new JSONObject().put("address",0x7e0).put("outcome","ok").put("count","5901FF010000").put("list","").put("identity",""))
            .put(new JSONObject().put("address",0x720).put("outcome","ok").put("count","5901FF010001").put("list","5902FFA2500008").put("identity","").put("name","62F19742434D")
                .put("descriptions",new JSONObject().put("B2250-00",new JSONObject().put("text","Front left window not initialized").put("exact",true).put("manual","61402").put("source","Справочник Song L DM-i · EN · без подтверждения типа блока"))))
            .put(new JSONObject().put("address",0x7e2).put("outcome","no_answer").put("count","").put("list","").put("identity","")));
        }catch(Exception impossible){return new JSONObject();}}
}
