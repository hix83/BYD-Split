package ru.logunov.bydsplit;
import android.app.*;
import android.os.*;
import android.content.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Emulator-only transport tests. Fake proxy and invalid bot tokens; never sends a message. */
public final class TelegramRouteInstrumentation extends Instrumentation {
 private static final String TOKEN="123456:abcdefghijklmnopqrstuvwxyz012345";
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  check(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"),"emulator required");
  storage();httpProxy(false);httpProxy(true);cancel();
  TelegramRoute route=TelegramRoute.relay("https://tg-api.slk-soft.ru","x".repeat(40));
  try(TelegramBotApi api=new TelegramBotApi(TOKEN,null,route)){try{api.botName();throw new AssertionError("invalid relay key accepted");}catch(TelegramBotApi.Failure e){check(e.code.equals("relay_auth"),"HTTPS gateway reachable with verified certificate: "+e.code);}}
  ui();
  result.putString("result","PASS: encrypted route storage, fail-closed corruption, HTTP CONNECT without/with auth, rejected credentials, cancellation, public gateway HTTPS/auth");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
 private void ui()throws Exception{
  AppPreferences.get(getTargetContext()).edit().putBoolean(AppPreferences.KEY_DEMO_MODE,true).putInt("theme_mode",2).commit();
  Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));
  runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("openSettings",int.class);m.setAccessible(true);m.invoke(activity,8);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();
  TelegramRouteView view=find(activity.getWindow().getDecorView());check(view!=null,"route settings visible in Integrations");
  java.lang.reflect.Field f=TelegramRouteView.class.getDeclaredField("mode");f.setAccessible(true);android.widget.Spinner spinner=(android.widget.Spinner)f.get(view);
  java.lang.reflect.Field toggle=TelegramRouteView.class.getDeclaredField("toggle");toggle.setAccessible(true);runOnMainSync(()->((android.widget.Button)get(toggle,view)).performClick());
  for(int mode=0;mode<4;mode++){final int index=mode;runOnMainSync(()->spinner.setSelection(index));waitForIdleSync();SystemClock.sleep(250);android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();java.io.File dir=new java.io.File(getTargetContext().getExternalFilesDir(null),"telegram-route-shots");dir.mkdirs();try(OutputStream out=new FileOutputStream(new java.io.File(dir,"mode-"+mode+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
 }
 private static Object get(java.lang.reflect.Field f,Object target){try{return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
 private static TelegramRouteView find(android.view.View view){if(view instanceof TelegramRouteView)return (TelegramRouteView)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TelegramRouteView result=find(group.getChildAt(i));if(result!=null)return result;}}return null;}
 private void storage()throws Exception{
  Context c=getTargetContext();TelegramRouteStore store=new TelegramRouteStore(c,"telegram_route_test");store.clear();
  try{check(store.get().mode==TelegramRoute.Mode.DIRECT,"default direct");
   for(TelegramRoute route:Arrays.asList(TelegramRoute.relay("https://example.org","secret_"+"a".repeat(40)),TelegramRoute.proxy(TelegramRoute.Mode.HTTP,"proxy.example",8080,"owner","private-password"),TelegramRoute.proxy(TelegramRoute.Mode.SOCKS5,"127.0.0.1",1080,"",""))){
    store.save(route);TelegramRoute read=store.get();check(read.sameDestination(route)&&read.password.equals(route.password)&&read.relayKey.equals(route.relayKey),"encrypted round trip");String raw=c.getSharedPreferences("telegram_route_test",0).getAll().toString();check(!raw.contains("private-password")&&!raw.contains("proxy.example")&&!raw.contains("secret_"),"plaintext preferences");
   }
   c.getSharedPreferences("telegram_route_test",0).edit().putString("cipher","corrupted").commit();try{store.get();throw new AssertionError("corruption fell back to direct");}catch(TelegramBotApi.Failure e){check(e.code.equals("route"),"corruption surfaced");}
  }finally{store.clear();}
 }
 private void httpProxy(boolean auth)throws Exception{
  try(ServerSocket server=new ServerSocket(0)){
   ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> peer=pool.submit(()->{try(Socket socket=server.accept()){
    socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));String first=in.readLine();check(first.equals("CONNECT api.telegram.org:443 HTTP/1.1"),"HTTP CONNECT target");String line,authorization="";while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("proxy-authorization:"))authorization=line;
    check(auth==!authorization.isEmpty(),"optional per-client authorization");if(auth)check(authorization.endsWith("b3duZXI6c2VjcmV0"),"proxy basic credentials");
    socket.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"test\"\r\nContent-Length: 0\r\n\r\n".getBytes());socket.getOutputStream().flush();
   }catch(Exception e){throw new RuntimeException(e);}});
   try(TelegramBotApi api=new TelegramBotApi(TOKEN,null,TelegramRoute.proxy(TelegramRoute.Mode.HTTP,"127.0.0.1",server.getLocalPort(),auth?"owner":"",auth?"secret":""))){try{api.botName();throw new AssertionError("407 accepted");}catch(TelegramBotApi.Failure e){check(e.code.equals("proxy_auth"),"HTTP proxy auth error: "+e.code);}}
   finally{try{peer.get(6,TimeUnit.SECONDS);}finally{pool.shutdownNow();}}
  }
 }
 private void cancel()throws Exception{
  try(ServerSocket server=new ServerSocket(0)){
   TelegramBotApi api=new TelegramBotApi(TOKEN,null,TelegramRoute.proxy(TelegramRoute.Mode.HTTP,"127.0.0.1",server.getLocalPort(),"",""));ExecutorService pool=Executors.newSingleThreadExecutor();
   Future<String> call=pool.submit(()->{try{api.botName();return "unexpected";}catch(TelegramBotApi.Failure e){return e.code;}});
   try(Socket accepted=server.accept()){api.close();check(call.get(3,TimeUnit.SECONDS).equals("cancelled"),"request cancelled promptly");}finally{api.close();pool.shutdownNow();}
  }
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
