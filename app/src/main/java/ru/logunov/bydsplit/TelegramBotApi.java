package ru.logunov.bydsplit;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** BYDMate 4.2.0 bot pairing/send flow adapted to Java. PolyForm Noncommercial; see NOTICE. */
final class TelegramBotApi implements AutoCloseable {
    interface Transport {JSONObject call(String method,JSONObject fields,String document)throws Exception;}
    static final class Failure extends Exception {
        final String code;
        Failure(String code){super(code);this.code=code;}
        String userText(){switch(code){case "token":return "Неверный токен бота";case "network":return "Нет связи с Telegram. Отчёт сохранён, можно отправить позже.";
            case "webhook":return "У бота настроен webhook или другой получатель обновлений. Используйте отдельного бота.";
            case "chat":return "Чат не найден или бот заблокирован";case "cancelled":return "Отправка отменена";
            case "route":return "Проверьте и сохраните настройки соединения Telegram";
            case "relay_auth":return "Шлюз отклонил ключ доступа. Проверьте ключ в настройках соединения.";
            case "relay_network":return "Нет связи со шлюзом или Telegram через него. Отчёт сохранён.";
            case "relay_rate":return "Шлюз просит подождать перед повторной отправкой";
            case "proxy_auth":return "Прокси отклонил логин или пароль";
            case "proxy_network":return "Нет связи через выбранный прокси. Проверьте адрес, порт и доступ к Telegram.";
            case "tls":return "Не удалось установить защищённое HTTPS-соединение. Проверьте адрес и сертификат сервера.";
            case "large":return "Отчёт слишком большой";case "rate":return "Telegram просит подождать перед повторной отправкой";default:return "Telegram не подтвердил операцию. Повторите позже.";}}
    }
    private final String token;private final Transport transport;private final TelegramRoute route;
    private OkHttpClient client;private volatile Call active;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    TelegramBotApi(String token)throws Failure{this(token,null,TelegramRoute.direct());}
    TelegramBotApi(String token,Transport transport)throws Failure{this(token,transport,TelegramRoute.direct());}
    TelegramBotApi(String token,Transport transport,TelegramRoute route)throws Failure{
        if(token==null||!token.matches("[0-9]{5,20}:[A-Za-z0-9_-]{20,100}"))throw new Failure("token");this.token=token;this.transport=transport;this.route=route;
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
    private synchronized OkHttpClient client()throws Failure{
        if(cancelled.get())throw new Failure("cancelled");
        if(client!=null)return client;
        OkHttpClient.Builder builder=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).writeTimeout(15,TimeUnit.SECONDS).callTimeout(28,TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).proxy(Proxy.NO_PROXY);
        if(route.mode==TelegramRoute.Mode.HTTP){
            builder.proxy(new Proxy(Proxy.Type.HTTP,InetSocketAddress.createUnresolved(route.host,route.port)));
            builder.proxyAuthenticator((r,response)->{
                if(route.username.isEmpty()){if("OkHttp-Preemptive".equals(response.header("Proxy-Authenticate")))return null;throw new TelegramSocksSocketFactory.ProxyRejected();}
                if(response.request().header("Proxy-Authorization")!=null)throw new TelegramSocksSocketFactory.ProxyRejected();
                return response.request().newBuilder().header("Proxy-Authorization",Credentials.basic(route.username,route.password,StandardCharsets.UTF_8)).build();
            });
        }else if(route.mode==TelegramRoute.Mode.SOCKS5){
            builder.socketFactory(new TelegramSocksSocketFactory(route));
            // The SOCKS handshake sends the original host. Do not resolve Telegram locally.
            builder.dns(host->{if(!host.equals("api.telegram.org"))throw new UnknownHostException("Destination not allowed");return Collections.singletonList(InetAddress.getByAddress(host,new byte[]{0,0,0,1}));});
        }
        client=builder.build();return client;
    }
    private JSONObject network(String method,JSONObject fields,String document)throws Exception{
        try{
            Request.Builder request=new Request.Builder();byte[] data;String contentType;
            if(route.mode==TelegramRoute.Mode.RELAY){
                request.url(route.url+"/v1/telegram").header("X-Split-Relay-Key",route.relayKey);
                data=new JSONObject().put("method",method).put("token",token).put("fields",fields).put("document",document==null?JSONObject.NULL:document).toString().getBytes(StandardCharsets.UTF_8);
                if(data.length>2*1024*1024)throw new Failure("large");contentType="application/json; charset=utf-8";
            }else{
                request.url("https://api.telegram.org/bot"+token+"/"+method);
                if(document==null){contentType="application/json; charset=utf-8";data=fields.toString().getBytes(StandardCharsets.UTF_8);}
                else{
                    String boundary="bydsplit"+UUID.randomUUID().toString().replace("-","");contentType="multipart/form-data; boundary="+boundary;
                    StringBuilder body=new StringBuilder();Iterator<String> keys=fields.keys();while(keys.hasNext()){String key=keys.next();body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n").append(fields.get(key)).append("\r\n");}
                    body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"document\"; filename=\"BYD-Split-diagnostics.txt\"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n").append(document).append("\r\n--").append(boundary).append("--\r\n");data=body.toString().getBytes(StandardCharsets.UTF_8);
                }
            }
            Call call=client().newCall(request.post(RequestBody.create(data,MediaType.get(contentType))).build());active=call;
            if(cancelled.get()){call.cancel();throw new Failure("cancelled");}
            try(Response response=call.execute()){
                if(response.code()==407)throw new Failure("proxy_auth");
                if(response.body()==null)throw new Failure(route.mode==TelegramRoute.Mode.RELAY?"relay_network":"response");
                byte[] bytes;
                try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(cancelled.get())throw new Failure("cancelled");if(out.size()+n>1024*1024)throw new Failure("response");out.write(buffer,0,n);}bytes=out.toByteArray();
                }
                JSONObject result;try{result=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}catch(JSONException e){throw new Failure(route.mode==TelegramRoute.Mode.RELAY?"relay_network":"response");}
                if(route.mode==TelegramRoute.Mode.RELAY&&result.has("relay_error")){
                    String error=result.optString("relay_error");throw new Failure(error.equals("unauthorized")?"relay_auth":error.equals("rate_limited")?"relay_rate":error.equals("too_large")?"large":"relay_network");
                }
                if(!response.isSuccessful())throw route.mode==TelegramRoute.Mode.RELAY?new Failure("relay_network"):failure(response.code());
                return result;
            }
        }catch(Failure e){throw e;}catch(TelegramSocksSocketFactory.ProxyRejected e){throw new Failure("proxy_auth");}
        catch(javax.net.ssl.SSLException e){throw new Failure(cancelled.get()?"cancelled":"tls");}
        catch(IOException e){
            if(cancelled.get())throw new Failure("cancelled");
            // OkHttp can wrap a rejected SOCKS handshake as the cause of an IOException.
            for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof TelegramSocksSocketFactory.ProxyRejected)throw new Failure("proxy_auth");
            throw new Failure(route.mode==TelegramRoute.Mode.RELAY?"relay_network":route.mode==TelegramRoute.Mode.DIRECT?"network":"proxy_network");
        }finally{active=null;}
    }
    @Override public synchronized void close(){cancelled.set(true);Call call=active;if(call!=null)call.cancel();OkHttpClient c=client;if(c!=null){c.dispatcher().cancelAll();c.connectionPool().evictAll();c.dispatcher().executorService().shutdown();}}
}
