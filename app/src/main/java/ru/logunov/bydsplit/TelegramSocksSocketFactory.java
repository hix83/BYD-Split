package ru.logunov.bydsplit;

import javax.net.SocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Per-client SOCKS5 with remote DNS and optional RFC1929 auth. No global Authenticator. */
final class TelegramSocksSocketFactory extends SocketFactory {
    private final TelegramRoute route;
    TelegramSocksSocketFactory(TelegramRoute route){if(route.mode!=TelegramRoute.Mode.SOCKS5)throw new IllegalArgumentException();this.route=route;}
    @Override public Socket createSocket(){return new Tunnel();}
    private Socket connect(String host,int port,InetAddress local,int localPort)throws IOException{Socket socket=createSocket();try{if(local!=null)socket.bind(new InetSocketAddress(local,localPort));socket.connect(InetSocketAddress.createUnresolved(host,port),10000);return socket;}catch(IOException e){socket.close();throw e;}}
    @Override public Socket createSocket(String h,int p)throws IOException{return connect(h,p,null,0);}
    @Override public Socket createSocket(InetAddress h,int p)throws IOException{return connect(h.getHostName(),p,null,0);}
    @Override public Socket createSocket(String h,int p,InetAddress l,int lp)throws IOException{return connect(h,p,l,lp);}
    @Override public Socket createSocket(InetAddress h,int p,InetAddress l,int lp)throws IOException{return connect(h.getHostName(),p,l,lp);}
    static final class ProxyRejected extends IOException {ProxyRejected(){super("SOCKS5 authentication rejected");}}
    private final class Tunnel extends Socket {
        @Override public void connect(SocketAddress endpoint)throws IOException{connect(endpoint,10000);}
        @Override public void connect(SocketAddress endpoint,int timeout)throws IOException{
            if(!(endpoint instanceof InetSocketAddress))throw new IOException("Invalid destination");
            InetSocketAddress destination=(InetSocketAddress)endpoint;
            if(!"api.telegram.org".equalsIgnoreCase(destination.getHostString())||destination.getPort()!=443)throw new IOException("Destination not allowed");
            int previousTimeout=getSoTimeout();
            try{
                super.connect(new InetSocketAddress(route.host,route.port),timeout);
                setSoTimeout(timeout>0?timeout:10000);
                InputStream in=getInputStream();OutputStream out=getOutputStream();
                boolean auth=!route.username.isEmpty();out.write(new byte[]{5,1,(byte)(auth?2:0)});out.flush();byte[] hello=read(in,2);
                if(hello[0]!=5||hello[1]!=(auth?2:0))throw new ProxyRejected();
                if(auth){byte[] user=route.username.getBytes(StandardCharsets.UTF_8),pass=route.password.getBytes(StandardCharsets.UTF_8);out.write(1);out.write(user.length);out.write(user);out.write(pass.length);out.write(pass);out.flush();byte[] accepted=read(in,2);if(accepted[0]!=1||accepted[1]!=0)throw new ProxyRejected();}
                byte[] host="api.telegram.org".getBytes(StandardCharsets.US_ASCII);
                out.write(new byte[]{5,1,0,3,(byte)host.length});out.write(host);out.write(new byte[]{1,(byte)187});out.flush();
                byte[] reply=read(in,4);if(reply[0]!=5||reply[1]!=0||reply[2]!=0)throw new IOException("SOCKS5 connection rejected");
                int length=(reply[3]&255)==1?4:(reply[3]&255)==4?16:(reply[3]&255)==3?read(in,1)[0]&255:-1;
                if(length<1)throw new IOException("Invalid SOCKS5 response");read(in,length+2);setSoTimeout(previousTimeout);
            }catch(IOException e){close();throw e;}
        }
    }
    private static byte[] read(InputStream in,int n)throws IOException{byte[] value=new byte[n];int offset=0;while(offset<n){int count=in.read(value,offset,n-offset);if(count<0)throw new EOFException("Incomplete SOCKS5 response");offset+=count;}return value;}
}
