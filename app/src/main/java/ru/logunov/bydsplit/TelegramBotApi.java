package ru.logunov.bydsplit;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** BYDMate 4.2.0 bot pairing/send flow adapted to Java. PolyForm Noncommercial; see NOTICE. */
final class TelegramBotApi implements AutoCloseable {
    interface Transport {JSONObject call(String method,JSONObject fields,String document)throws Exception;}
    static final class Failure extends Exception {
        final String code;
        Failure(String code){super(code);this.code=code;}
        String userText(){switch(code){case "token":return "Неверный токен бота";case "network":return "Нет связи с Telegram. Отчёт сохранён, можно отправить позже.";
            case "webhook":return "У бота настроен webhook или другой получатель обновлений. Используйте отдельного бота.";
            case "chat":return "Чат не найден или бот заблокирован";case "cancelled":return "Отправка отменена";
            case "large":return "Отчёт слишком большой";case "rate":return "Telegram просит подождать перед повторной отправкой";default:return "Telegram не подтвердил операцию. Повторите позже.";}}
    }
    private final String token;private final Transport transport;
    private final AtomicBoolean cancelled=new AtomicBoolean();private volatile HttpURLConnection active;
    TelegramBotApi(String token)throws Failure{this(token,null);}
    TelegramBotApi(String token,Transport transport)throws Failure{
        if(token==null||!token.matches("[0-9]{5,20}:[A-Za-z0-9_-]{20,100}"))throw new Failure("token");this.token=token;this.transport=transport;
    }
    JSONObject call(String method,JSONObject fields,String document)throws Exception{
        if(cancelled.get()||Thread.currentThread().isInterrupted())throw new Failure("cancelled");
        if(!Arrays.asList("getMe","getUpdates","sendMessage","sendDocument").contains(method))throw new Failure("response");
        JSONObject response;
        if(transport!=null)response=transport.call(method,fields,document);
        else response=network(method,fields,document);
        if(cancelled.get())throw new Failure("cancelled");
        if(response==null||!response.optBoolean("ok")||!response.has("result"))throw failure(response==null?0:response.optInt("error_code"));
        return response;
    }
    String botName()throws Exception{return call("getMe",new JSONObject(),null).getJSONObject("result").getString("username");}
    JSONArray updates()throws Exception{return call("getUpdates",new JSONObject().put("offset",-100).put("limit",100).put("timeout",0).put("allowed_updates",new JSONArray().put("message")),null).getJSONArray("result");}
    void message(long chat,String text)throws Exception{
        if(text.length()>4096)throw new Failure("large");
        call("sendMessage",new JSONObject().put("chat_id",chat).put("text",text).put("link_preview_options",new JSONObject().put("is_disabled",true)),null);
    }
    void report(long chat,String text)throws Exception{
        if(text.length()<=3800){message(chat,text);return;}
        if(text.getBytes(StandardCharsets.UTF_8).length>1024*1024)throw new Failure("large");
        call("sendDocument",new JSONObject().put("chat_id",chat).put("caption","BYD Split · полный отчёт диагностики с доступными расшифровками"),text);
    }
    static List<JSONObject> linkChats(JSONArray updates,String code,long afterId){
        Map<Long,JSONObject> found=new LinkedHashMap<>();
        for(int i=updates.length()-1;i>=0;i--){JSONObject update=updates.optJSONObject(i);if(update==null||update.optLong("update_id",-1)<=afterId)continue;
            JSONObject message=update.optJSONObject("message");if(message==null||message.has("forward_origin")||message.has("forward_date"))continue;
            JSONObject from=message.optJSONObject("from"),chat=message.optJSONObject("chat");
            if(chat==null||chat.optLong("id")==0||from==null||from.optBoolean("is_bot")||!Arrays.asList("private","group","supergroup").contains(chat.optString("type")))continue;
            if(TelegramLink.code(message.optString("text")).equals(code))found.put(chat.optLong("id"),chat);
        }
        return new ArrayList<>(found.values());
    }
    static String chatName(JSONObject chat){String name=chat.optString("title");if(!name.isEmpty())return name;name=chat.optString("first_name");String user=chat.optString("username");return (name+(user.isEmpty()?"":" @"+user)).trim();}
    private static Failure failure(int code){return new Failure(code==401||code==404?"token":code==409?"webhook":code==403||code==400?"chat":code==413?"large":code==429?"rate":"response");}
    private JSONObject network(String method,JSONObject fields,String document)throws Exception{
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL("https://api.telegram.org/bot"+token+"/"+method).openConnection();active=connection;
            if(cancelled.get())throw new Failure("cancelled");
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setRequestMethod("POST");connection.setDoOutput(true);
            byte[] data;
            if(document==null){connection.setRequestProperty("Content-Type","application/json; charset=utf-8");data=fields.toString().getBytes(StandardCharsets.UTF_8);}
            else{
                String boundary="bydsplit"+UUID.randomUUID().toString().replace("-","");
                connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
                StringBuilder body=new StringBuilder();Iterator<String> keys=fields.keys();while(keys.hasNext()){String key=keys.next();body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n").append(fields.get(key)).append("\r\n");}
                body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"document\"; filename=\"BYD-Split-diagnostics.txt\"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n").append(document).append("\r\n--").append(boundary).append("--\r\n");data=body.toString().getBytes(StandardCharsets.UTF_8);
            }
            connection.setFixedLengthStreamingMode(data.length);
            try(OutputStream out=connection.getOutputStream()){out.write(data);}
            int status=connection.getResponseCode();if(status<200||status>=300)throw failure(status);
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(cancelled.get())throw new Failure("cancelled");if(out.size()+n>1024*1024)throw new Failure("response");out.write(buffer,0,n);}
                try{return new JSONObject(out.toString("UTF-8"));}catch(JSONException e){throw new Failure("response");}
            }
        }catch(Failure e){throw e;}catch(IOException e){throw new Failure(cancelled.get()?"cancelled":"network");}
        finally{if(connection!=null)connection.disconnect();active=null;}
    }
    @Override public void close(){cancelled.set(true);HttpURLConnection connection=active;if(connection!=null)connection.disconnect();}
}
