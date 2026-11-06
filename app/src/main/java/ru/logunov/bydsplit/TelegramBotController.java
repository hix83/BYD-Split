package ru.logunov.bydsplit;

import android.content.Context;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Short, cancellable foreground operations. No service, alarms, polling or automatic car scans. */
final class TelegramBotController {
    private static TelegramBotController instance;
    static synchronized TelegramBotController get(Context context){if(instance==null)instance=new TelegramBotController(context.getApplicationContext());return instance;}
    interface ApiFactory {TelegramBotApi create(String token)throws TelegramBotApi.Failure;}
    private final ApiFactory apiFactory;
    final TelegramBotStore store;
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final VehicleSleepController sleep;private final Set<Runnable> listeners=new LinkedHashSet<>();
    private TelegramBotApi active;private Object owner;private Future<?> task;private int generation;
    private String pendingToken="";String bot="",code="",chatName="",stage="token",status="";private long issued,afterId,chat;
    boolean busy;
    private final Runnable timeout=()->{Object current=owner;cancel(current);status="Telegram не ответил вовремя. Повторите отправку вручную.";publish();};
    private TelegramBotController(Context c){this(c,new TelegramBotStore(c),TelegramBotApi::new);}
    TelegramBotController(Context c,TelegramBotStore store,ApiFactory factory){this.store=store;apiFactory=factory;sleep=VehicleSleepController.get(c);if(store.connected())stage="connected";}
    void addListener(Runnable r){listeners.add(r);r.run();}
    void removeListener(Runnable r){listeners.remove(r);}
    private void publish(){for(Runnable r:new ArrayList<>(listeners))r.run();}
    private interface Work {Runnable run(TelegramBotApi api)throws Exception;}
    private void execute(Object requestOwner,String token,Work work){
        if(busy){status="Дождитесь завершения текущей операции";publish();return;}
        if(sleep.mode()==VehicleSleepState.Mode.SLEEP){status="Машина спит. Отправьте отчёт после включения ГУ.";publish();return;}
        try{active=apiFactory.create(token);}catch(TelegramBotApi.Failure e){status=e.userText();publish();return;}
        busy=true;owner=requestOwner;status="Связь с Telegram…";int id=++generation;TelegramBotApi api=active;
        sleep.addListener(this,()->{if(busy&&sleep.mode()==VehicleSleepState.Mode.SLEEP)cancel(owner);});
        main.postDelayed(timeout,35000);publish();
        task=worker.submit(()->{
            try{Runnable apply=work.run(api);main.post(()->{if(id!=generation)return;finish();apply.run();publish();});}
            catch(Exception e){String message=e instanceof TelegramBotApi.Failure?((TelegramBotApi.Failure)e).userText():"Не удалось завершить операцию Telegram";
                main.post(()->{if(id!=generation)return;finish();status=message;publish();});}
            finally{api.close();}
        });
    }
    private void finish(){busy=false;active=null;owner=null;task=null;main.removeCallbacks(timeout);sleep.removeListener(this);}
    void cancel(Object requestOwner){
        if(!busy||owner!=requestOwner)return;generation++;if(active!=null)active.close();if(task!=null)task.cancel(true);finish();status="Операция Telegram отменена";publish();
    }
    void begin(Object requestOwner,String token){
        execute(requestOwner,token,api->{String name=api.botName();JSONArray updates=api.updates();long last=-1;for(int i=0;i<updates.length();i++){JSONObject u=updates.optJSONObject(i);if(u!=null)last=Math.max(last,u.optLong("update_id",-1));}final long offset=last;
            return ()->{pendingToken=token;bot=name;code=TelegramLink.newCode();afterId=offset;issued=SystemClock.elapsedRealtime();stage="code";status="Отправьте код своему боту, затем нажмите «Проверить код».";};});
    }
    void check(Object requestOwner){
        if(!TelegramLink.fresh(issued,SystemClock.elapsedRealtime())){back();status="Код истёк. Проверьте токен ещё раз, чтобы получить новый код.";publish();return;}
        final String expected=code;final long offset=afterId;
        execute(requestOwner,pendingToken,api->{List<JSONObject> chats=TelegramBotApi.linkChats(api.updates(),expected,offset);
            if(chats.isEmpty())return ()->status="Код пока не получен. Отправьте его боту и повторите проверку.";
            if(chats.size()!=1)return ()->status="Код получен из нескольких чатов. Нажмите «Назад» и получите новый код.";
            JSONObject destination=chats.get(0);long id=destination.getLong("id");String name=TelegramBotApi.chatName(destination);
            return ()->{chat=id;chatName=name;stage="confirm";status="Подключить этот чат для отчётов?";};});
    }
    void confirm(Object requestOwner){
        if(!stage.equals("confirm")||!TelegramLink.fresh(issued,SystemClock.elapsedRealtime())){back();return;}
        final String token=pendingToken,name=bot,destination=chatName;final long id=chat;
        execute(requestOwner,token,api->{api.message(id,"BYD Split подключён. Сюда будут приходить отчёты диагностики, отправленные из приложения.");return ()->{
            try{store.save(token,name,destination,id);pendingToken="";code="";stage="connected";status="Telegram подключён";}
            catch(Exception e){status="Не удалось сохранить подключение. Повторите подключение.";}
        };});
    }
    void back(){if(busy)return;pendingToken="";code="";chatName="";stage=store.connected()?"connected":"token";status="";publish();}
    void disconnect(){if(busy)cancel(owner);store.clear();stage="token";pendingToken="";code="";status="Бот отключён";publish();}
    void send(Object requestOwner,JSONObject report){
        if(report==null){status="Сначала выполните проверку ошибок";publish();return;}
        final TelegramBotStore.Config config;
        try{config=store.get();}catch(Exception e){status="Не удалось прочитать ключ бота. Подключите бот заново.";publish();return;}
        if(config==null){status="Подключите своего бота в настройках Telegram";publish();return;}
        final String text=DtcReport.text(report);
        execute(requestOwner,config.token,api->{api.report(config.chat,text);return ()->status="Отчёт отправлен: "+config.chatName;});
    }
}
