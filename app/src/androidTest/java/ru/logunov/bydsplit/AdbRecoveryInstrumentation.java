package ru.logunov.bydsplit;

import android.app.*;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.Arrays;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** A fake loopback adbd exercises real STLS, mutual TLS and ADB framing without changing device ADB. */
public final class AdbRecoveryInstrumentation extends Instrumentation {
    private static final int CNXN=0x4e584e43,STLS=0x534c5453,OPEN=0x4e45504f,OKAY=0x59414b4f,WRTE=0x45545257,CLSE=0x45534c43;
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();ExecutorService server=Executors.newSingleThreadExecutor();try{
        KeyPair clientKeys=new LocalAdbKeyStore(getTargetContext()).loadOrCreate();
        X509Certificate cert=AdbRecoveryTls.certificate(clientKeys);cert.verify(clientKeys.getPublic());cert.checkValidity();
        check(Arrays.equals(cert.getPublicKey().getEncoded(),clientKeys.getPublic().getEncoded()),"certificate changed ADB key");
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);KeyPair serverKeys=generator.generateKeyPair();
        KeyStore store=KeyStore.getInstance("PKCS12");store.load(null,null);char[] password="test-local-only".toCharArray();
        store.setKeyEntry("adbd",serverKeys.getPrivate(),password,new java.security.cert.Certificate[]{AdbRecoveryTls.certificate(serverKeys)});
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,password);
        SSLContext ssl=SSLContext.getInstance("TLSv1.3");ssl.init(km.getKeyManagers(),new TrustManager[]{new X509TrustManager(){
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{if(chain.length==0||!Arrays.equals(chain[0].getPublicKey().getEncoded(),clientKeys.getPublic().getEncoded()))throw new CertificateException("wrong key");}
            public void checkServerTrusted(X509Certificate[] chain,String auth){}
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
        }},new SecureRandom());
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            Future<?> served=server.submit(()->{try(Socket plain=listener.accept()){
                plain.setSoTimeout(8000);check(read(plain.getInputStream()).command==CNXN,"missing initial CNXN");
                send(plain.getOutputStream(),STLS,0x01000000,0,new byte[0]);check(read(plain.getInputStream()).command==STLS,"missing STLS ack");
                try(SSLSocket tls=(SSLSocket)ssl.getSocketFactory().createSocket(plain,"127.0.0.1",plain.getPort(),true)){
                    tls.setUseClientMode(false);tls.setNeedClientAuth(true);tls.setSoTimeout(8000);tls.startHandshake();
                    send(tls.getOutputStream(),CNXN,0x01000000,4096,"device::\0".getBytes(StandardCharsets.US_ASCII));
                    Packet open=read(tls.getInputStream());check(open.command==OPEN&&new String(open.payload,StandardCharsets.UTF_8).equals("tcpip:5555\0"),"wrong ADB service");
                    send(tls.getOutputStream(),OKAY,7,open.arg0,new byte[0]);send(tls.getOutputStream(),WRTE,7,open.arg0,"restarting in TCP mode".getBytes(StandardCharsets.UTF_8));
                    check(read(tls.getInputStream()).command==OKAY,"missing stream ACK");send(tls.getOutputStream(),CLSE,7,open.arg0,new byte[0]);
                    check(read(tls.getInputStream()).command==CLSE,"missing stream close");
                }
            }catch(Exception error){throw new RuntimeException(error);}});
            try(LocalAdbClient client=new LocalAdbClient(new LocalAdbKeyStore(getTargetContext()),listener.getLocalPort(),true,8000)){client.connect();client.restartTcpip();}
            served.get(15,TimeUnit.SECONDS);
        }
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            Future<?> served=server.submit(()->{try(Socket plain=listener.accept()){read(plain.getInputStream());send(plain.getOutputStream(),CNXN,0x01000000,4096,new byte[0]);}catch(Exception error){throw new RuntimeException(error);}});
            try(LocalAdbClient client=new LocalAdbClient(new LocalAdbKeyStore(getTargetContext()),listener.getLocalPort(),true,3000)){
                try{client.connect();throw new AssertionError("TLS downgrade accepted");}catch(IllegalStateException expected){}
            }served.get(5,TimeUnit.SECONDS);
        }
        check(AdbRecovery.localAddress(InetAddress.getByName("127.0.0.1")),"loopback rejected");
        check(!AdbRecovery.localAddress(InetAddress.getByName("192.0.2.10")),"foreign mDNS host accepted");
        result.putString("result","PASS: existing RSA key in certificate, signature, real mutual TLS 1.3 over STLS, tcpip service framing, downgrade rejection, local-only discovery filter");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}finally{server.shutdownNow();}}
    private static class Packet{int command,arg0;byte[] payload;}
    private static Packet read(InputStream input)throws Exception{DataInputStream stream=new DataInputStream(input);byte[] raw=new byte[24];stream.readFully(raw);ByteBuffer h=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);Packet p=new Packet();p.command=h.getInt();p.arg0=h.getInt();h.getInt();int length=h.getInt();check(length>=0&&length<65536,"bad packet length");p.payload=new byte[length];stream.readFully(p.payload);return p;}
    private static void send(OutputStream output,int command,int a,int b,byte[] payload)throws Exception{int checksum=0;for(byte value:payload)checksum+=value&255;ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);h.putInt(command).putInt(a).putInt(b).putInt(payload.length).putInt(checksum).putInt(command^-1);output.write(h.array());output.write(payload);output.flush();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
