package ru.logunov.bydsplit;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
public final class TelegramRouteTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static void invalid(Runnable r){try{r.run();throw new AssertionError("accepted invalid route");}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  String secret="a".repeat(40);check(TelegramRoute.relay("https://example.org/",secret).url.equals("https://example.org"));
  for(String address:new String[]{"http://example.org","https://user@example.org","https://example.org?key=x","https://example.org/#x","https://example.org:0","https://example.org/a//b"})invalid(()->TelegramRoute.relay(address,secret));
  invalid(()->TelegramRoute.relay("https://example.org","short"));
  invalid(()->TelegramRoute.proxy(TelegramRoute.Mode.HTTP,"http://example.org",80,"",""));
  invalid(()->TelegramRoute.proxy(TelegramRoute.Mode.HTTP,"example.org",0,"",""));
  invalid(()->TelegramRoute.proxy(TelegramRoute.Mode.SOCKS5,"example.org",1080,"","secret"));
  check(!TelegramRoute.proxy(TelegramRoute.Mode.HTTP,"proxy",80,"u","p").sameDestination(TelegramRoute.proxy(TelegramRoute.Mode.HTTP,"other",80,"u","p")));
  check(!TelegramRoute.relay("https://example.org",secret).toString().contains(secret));
  for(boolean auth:new boolean[]{false,true})socks(auth,false);
  socks(true,true);
  System.out.println("TelegramRouteTest PASS: validation, SOCKS5 remote DNS, auth, rejection and socket cleanup");
 }
 static void socks(boolean auth,boolean reject)throws Exception{
  try(ServerSocket server=new ServerSocket(0)){
   ExecutorService pool=Executors.newSingleThreadExecutor();
   Future<?> peer=pool.submit(()->{try(Socket socket=server.accept()){
    socket.setSoTimeout(2000);DataInputStream in=new DataInputStream(socket.getInputStream());OutputStream out=socket.getOutputStream();
    check(in.readUnsignedByte()==5&&in.readUnsignedByte()==1&&in.readUnsignedByte()==(auth?2:0));out.write(new byte[]{5,(byte)(auth?2:0)});out.flush();
    if(auth){check(in.readUnsignedByte()==1);check(new String(in.readNBytes(in.readUnsignedByte())).equals("owner"));check(new String(in.readNBytes(in.readUnsignedByte())).equals("secret"));out.write(new byte[]{1,(byte)(reject?1:0)});out.flush();if(reject){check(in.read()==-1);return;}}
    check(in.readUnsignedByte()==5&&in.readUnsignedByte()==1&&in.readUnsignedByte()==0&&in.readUnsignedByte()==3);check(new String(in.readNBytes(in.readUnsignedByte())).equals("api.telegram.org"));check(in.readUnsignedShort()==443);
    out.write(new byte[]{5,0,0,1,127,0,0,1,0,0});out.write(42);out.flush();check(in.read()==-1);
   }catch(Exception e){throw new RuntimeException(e);}});
   TelegramRoute route=TelegramRoute.proxy(TelegramRoute.Mode.SOCKS5,"127.0.0.1",server.getLocalPort(),auth?"owner":"",auth?"secret":"");
   try(Socket socket=new TelegramSocksSocketFactory(route).createSocket()){
    try{socket.connect(InetSocketAddress.createUnresolved("api.telegram.org",443),2000);check(!reject);check(socket.getInputStream().read()==42);}catch(TelegramSocksSocketFactory.ProxyRejected e){check(reject&&socket.isClosed());}
   }finally{try{peer.get(4,TimeUnit.SECONDS);}finally{pool.shutdownNow();}}
  }
 }
}
