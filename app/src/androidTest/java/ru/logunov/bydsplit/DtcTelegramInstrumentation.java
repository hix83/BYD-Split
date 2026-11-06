package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Emulator only; fake Telegram transport, no real bot or vehicle commands. */
public final class DtcTelegramInstrumentation extends Instrumentation {
    private static final String TOKEN="123456:abcdefghijklmnopqrstuvwxyz012345";
    private TelegramBotStore store;
    private TelegramBotController controller;
    private final Object owner=new Object();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        check(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"emulator required");
        testReports();testApi();testController();
        result.putString("result","PASS: full reports/freshness/locks, pairing filters, message/document delivery, API errors, encrypted storage, explicit chat confirmation, cancellation of late responses; no network or car requests");
        finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}finally{if(controller!=null)runOnMainSync(()->controller.cancel(owner));if(store!=null)store.clear();}}
    private static JSONObject report(long at)throws Exception{return new JSONObject().put("at",at).put("results",new JSONArray()
        .put(new JSONObject().put("address",0x782).put("outcome","ok").put("count","5901FF010001").put("list","5902FFD1668709").put("descriptions",new JSONObject().put("U1166-87",new JSONObject().put("text","Communication fault description").put("exact",true))))
        .put(new JSONObject().put("address",0x7e2).put("outcome","no_answer")));}
    private void testReports()throws Exception{
        long now=System.currentTimeMillis();JSONObject r=report(now);String text=DtcReport.text(r);
        check(text.contains("U1166-87")&&text.contains("Communication fault description")&&text.contains("no_answer"),"complete code/description/unread report");
        check(DtcReport.eligible(r,now).length==1,"fresh targets");check(DtcReport.eligible(r,now+600001).length==0,"expired report");check(DtcReport.eligible(r,now-1).length==0,"clock rollback");
        r.put("eraseLocked",new JSONObject().put(Integer.toString(0x782),true));check(DtcReport.eligible(r,now).length==0,"unknown clear locks repeat");
        r.getJSONArray("results").getJSONObject(0).put("staleAfterErase",true);check(DtcReport.text(r).contains("Данные до стирания"),"preclear data labelled");
    }
    private static JSONObject update(int id,long chat,String type,String text)throws Exception{return new JSONObject().put("update_id",id).put("message",new JSONObject().put("text",text).put("chat",new JSONObject().put("id",chat).put("type",type).put("first_name","Owner")).put("from",new JSONObject().put("is_bot",false)));}
    private void testApi()throws Exception{
        JSONArray updates=new JSONArray().put(update(1,10,"private","12345678")).put(update(2,20,"private","/start 12345678")).put(update(3,20,"private","12345678"));
        updates.put(update(4,30,"channel","12345678"));JSONObject forwarded=update(5,40,"private","12345678");forwarded.getJSONObject("message").put("forward_origin",new JSONObject());updates.put(forwarded);
        JSONObject bot=update(6,50,"group","12345678");bot.getJSONObject("message").getJSONObject("from").put("is_bot",true);updates.put(bot);
        check(TelegramBotApi.linkChats(updates,"12345678",1).size()==1,"pairing baseline/dedup/forward/channel/bot filters");
        updates.put(update(7,-70,"supergroup","/start@mybot 12345678"));check(TelegramBotApi.linkChats(updates,"12345678",1).size()==2,"ambiguous chat detection");
        List<String> methods=new ArrayList<>(),documents=new ArrayList<>();TelegramBotApi api=new TelegramBotApi(TOKEN,(method,fields,document)->{methods.add(method);documents.add(document);check(fields.getLong("chat_id")==20,"destination");return new JSONObject().put("ok",true).put("result",new JSONObject());});
        api.report(20,"short report");String large="Расшифровка U1166-87\n".repeat(400);api.report(20,large);
        check(methods.equals(Arrays.asList("sendMessage","sendDocument"))&&large.equals(documents.get(1)),"long report never truncated");api.close();
        try{api.report(20,"cancel");throw new AssertionError("cancel accepted");}catch(TelegramBotApi.Failure expected){check(expected.code.equals("cancelled"),"cancelled API");}
        TelegramBotApi denied=new TelegramBotApi(TOKEN,(m,f,d)->new JSONObject().put("ok",false).put("error_code",403));try{denied.report(20,"failed");throw new AssertionError("API failure accepted");}catch(TelegramBotApi.Failure expected){check(expected.code.equals("chat")&&!expected.userText().contains(TOKEN),"safe error");}
    }
    private void awaitIdle()throws Exception{for(int i=0;i<200;i++){boolean[] busy={true};runOnMainSync(()->busy[0]=controller.busy);if(!busy[0])return;Thread.sleep(25);}throw new AssertionError("controller timeout");}
    private void testController()throws Exception{
        Context c=getTargetContext();store=new TelegramBotStore(c,"telegram_instrumentation");store.clear();
        final String[] code={""};final int[] sends={0};final boolean[] delay={false};CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        runOnMainSync(()->controller=new TelegramBotController(c,store,token->new TelegramBotApi(token,(method,fields,doc)->{
            if(delay[0]){entered.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}}
            Object value;if(method.equals("getMe"))value=new JSONObject().put("username","test_bot");else if(method.equals("getUpdates"))value=code[0].isEmpty()?new JSONArray():new JSONArray().put(update(12,20,"private",code[0]));else {sends[0]++;value=new JSONObject();}
            return new JSONObject().put("ok",true).put("result",value);
        })));
        runOnMainSync(()->controller.begin(owner,TOKEN));awaitIdle();runOnMainSync(()->{check(controller.stage.equals("code"),"code stage");code[0]=controller.code;check(!store.connected(),"not bound before code");controller.check(owner);});awaitIdle();
        runOnMainSync(()->{check(controller.stage.equals("confirm")&&!store.connected()&&sends[0]==0,"explicit destination confirmation");controller.confirm(owner);});awaitIdle();
        check(store.connected()&&store.get().token.equals(TOKEN),"encrypted token roundtrip");check(!c.getSharedPreferences("telegram_instrumentation",0).getAll().toString().contains(TOKEN),"token stored in plaintext");check(!store.automatic(),"auto reports opt in");
        runOnMainSync(()->controller.disconnect());check(!store.connected(),"disconnect clears binding");
        delay[0]=true;runOnMainSync(()->controller.begin(owner,TOKEN));check(entered.await(2,TimeUnit.SECONDS),"late request started");runOnMainSync(()->controller.cancel(owner));release.countDown();Thread.sleep(150);waitForIdleSync();
        runOnMainSync(()->check(!controller.busy&&controller.stage.equals("token")&&!store.connected(),"cancelled response resurrected setup"));
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
