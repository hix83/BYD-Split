package ru.logunov.bydsplit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Local adbd STLS, adapted from BYDMate AdbTls / AdbCertificate (see NOTICE). */
final class AdbRecoveryTls {
    static Socket upgrade(Socket plain, KeyPair keys) throws Exception {
        if (!plain.getInetAddress().isLoopbackAddress()) throw new SecurityException("Local adbd only");
        X509Certificate certificate = certificate(keys);
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(new KeyManager[]{new X509ExtendedKeyManager() {
            public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) {
                if(types!=null)for(String type:types)if("RSA".equals(type))return "adb";return null;
            }
            public X509Certificate[] getCertificateChain(String alias){return "adb".equals(alias)?new X509Certificate[]{certificate}:null;}
            public PrivateKey getPrivateKey(String alias){return "adb".equals(alias)?keys.getPrivate():null;}
            public String[] getClientAliases(String type,Principal[] issuers){return "RSA".equals(type)?new String[]{"adb"}:null;}
            public String[] getServerAliases(String type,Principal[] issuers){return null;}
            public String chooseServerAlias(String type,Principal[] issuers,Socket socket){return null;}
        }}, new TrustManager[]{new LocalAdbdTrust()}, new SecureRandom());
        SSLSocket tls=(SSLSocket)context.getSocketFactory().createSocket(plain,"127.0.0.1",plain.getPort(),true);
        tls.setSoTimeout(8000); tls.startHandshake(); return tls;
    }
    // adbd authenticates our existing RSA key, not a CA chain. Only loopback can reach this trust manager.
    @android.annotation.SuppressLint({"CustomX509TrustManager", "TrustAllX509TrustManager"})
    private static final class LocalAdbdTrust implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain,String auth){}
        public void checkServerTrusted(X509Certificate[] chain,String auth){}
        public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
    }
    /** Small DER encoder avoids introducing a global crypto provider. Certificate carries the existing ADB key. */
    static X509Certificate certificate(KeyPair keys) throws Exception {
        byte[] algorithm=seq(new byte[]{6,9,42,(byte)134,72,(byte)134,(byte)247,13,1,1,11},new byte[]{5,0});
        byte[] name=seq(der(0x31,seq(new byte[]{6,3,85,4,3},der(12,"BYD Split ADB".getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
        byte[] validity=seq(der(23,"200101000000Z".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),der(24,"20991231235959Z".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        byte[] body=seq(der(2,BigInteger.ONE.toByteArray()),algorithm,name,validity,name,keys.getPublic().getEncoded());
        Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(keys.getPrivate());signer.update(body);
        byte[] signature=signer.sign(), bits=new byte[signature.length+1];System.arraycopy(signature,0,bits,1,signature.length);
        return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(seq(body,algorithm,der(3,bits))));
    }
    private static byte[] seq(byte[]... items){ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] item:items)out.write(item,0,item.length);return der(48,out.toByteArray());}
    private static byte[] der(int tag,byte[] value){ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);int n=value.length;
        if(n<128)out.write(n);else if(n<256){out.write(0x81);out.write(n);}else{out.write(0x82);out.write(n>>8);out.write(n);}out.write(value,0,n);return out.toByteArray();}
}
