package ru.logunov.bydsplit;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Independent encrypted connection profile, preserved when a bot is disconnected. */
final class TelegramRouteStore {
    private final SharedPreferences prefs;private final String alias;
    TelegramRouteStore(Context c){this(c,"telegram_route");}
    TelegramRouteStore(Context c,String name){prefs=c.getSharedPreferences(name,0);alias="byd_split_"+name+"_v1";}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
        return (SecretKey)store.getKey(alias,null);
    }
    TelegramRoute get()throws TelegramBotApi.Failure{
        if(!prefs.contains("cipher"))return TelegramRoute.direct();
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
            JSONObject value=new JSONObject(new String(cipher.doFinal(Base64.decode(prefs.getString("cipher",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
            TelegramRoute.Mode mode=TelegramRoute.Mode.valueOf(value.getString("mode"));
            if(mode==TelegramRoute.Mode.DIRECT)return TelegramRoute.direct();
            if(mode==TelegramRoute.Mode.RELAY)return TelegramRoute.relay(value.getString("url"),value.getString("key"));
            return TelegramRoute.proxy(mode,value.getString("host"),value.getInt("port"),value.getString("username"),value.getString("password"));
        }catch(Exception e){throw new TelegramBotApi.Failure("route");}
    }
    void save(TelegramRoute route)throws Exception{
        JSONObject value=new JSONObject().put("mode",route.mode.name()).put("url",route.url).put("key",route.relayKey).put("host",route.host).put("port",route.port).put("username",route.username).put("password",route.password);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        if(!prefs.edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())throw new IllegalStateException();
    }
    void clear(){prefs.edit().clear().commit();try{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(alias);}catch(Exception ignored){}}
}
