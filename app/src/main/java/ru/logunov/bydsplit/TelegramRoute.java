package ru.logunov.bydsplit;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Telegram-only transport settings. Secrets are never included in toString or errors. */
final class TelegramRoute {
    enum Mode { DIRECT, RELAY, HTTP, SOCKS5 }
    final Mode mode;
    final String url,relayKey,host,username,password;
    final int port;
    private TelegramRoute(Mode mode,String url,String key,String host,int port,String username,String password){this.mode=mode;this.url=url;relayKey=key;this.host=host;this.port=port;this.username=username;this.password=password;}
    static TelegramRoute direct(){return new TelegramRoute(Mode.DIRECT,"","","",0,"","");}
    static TelegramRoute relay(String url,String key){
        try{
            URI uri=new URI(url.trim());String path=uri.getRawPath();
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getPort()==0||uri.getPort()>65535||uri.getPort() < -1)throw new IllegalArgumentException();
            if(path!=null&&!path.isEmpty()&&(!path.matches("/[A-Za-z0-9/_-]*")||path.contains("//")))throw new IllegalArgumentException();
            if(key==null||!key.matches("[A-Za-z0-9_-]{32,128}"))throw new IllegalArgumentException();
            String normalized=uri.toASCIIString();while(normalized.endsWith("/"))normalized=normalized.substring(0,normalized.length()-1);
            return new TelegramRoute(Mode.RELAY,normalized,key,"",0,"","");
        }catch(Exception e){throw new IllegalArgumentException("Invalid relay settings");}
    }
    static TelegramRoute proxy(Mode mode,String host,int port,String user,String password){
        if(mode!=Mode.HTTP&&mode!=Mode.SOCKS5)throw new IllegalArgumentException("Invalid proxy mode");
        try{
            host=host.trim();if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
            if(host.contains(":")){if(!host.matches("[0-9a-fA-F:.]+")||new URI("http://["+host+"]/" ).getHost()==null)throw new IllegalArgumentException();}
            else{host=IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);if(host.isEmpty()||host.length()>253)throw new IllegalArgumentException();}
            if(port<1||port>65535||user==null||password==null||user.contains(":")||(!password.isEmpty()&&user.isEmpty()))throw new IllegalArgumentException();
            for(String value:new String[]{user,password}){if(value.getBytes(StandardCharsets.UTF_8).length>255)throw new IllegalArgumentException();for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw new IllegalArgumentException();}
            return new TelegramRoute(mode,"","",host,port,user,password);
        }catch(Exception e){throw new IllegalArgumentException("Invalid proxy settings");}
    }
    boolean sameDestination(TelegramRoute other){return other!=null&&mode==other.mode&&url.equals(other.url)&&host.equals(other.host)&&port==other.port&&username.equals(other.username);}
    String label(){switch(mode){case RELAY:return "Через HTTPS-шлюз";case HTTP:return "HTTP-прокси";case SOCKS5:return "SOCKS5-прокси";default:return "Напрямую / VPN на ГУ";}}
    @Override public String toString(){return "TelegramRoute("+mode+")";}
}
