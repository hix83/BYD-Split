package ru.logunov.bydsplit;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials stay in this installation: AES-GCM key in Android Keystore, no export or logs. */
final class TelegramBotStore {
    private static final String ALIAS="byd_split_telegram_bot_v1";
    private final SharedPreferences prefs;private final String alias;
    static final class Config {
        final String token,bot,chatName;final long chat;
        Config(String token,String bot,String chatName,long chat){this.token=token;this.bot=bot;this.chatName=chatName;this.chat=chat;}
    }
    TelegramBotStore(Context context){this(context,"telegram_bot");}
    TelegramBotStore(Context context,String name){prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE);alias=ALIAS+(name.equals("telegram_bot")?"":"_"+name);}
    private SecretKey key()throws Exception{
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        if(!keys.containsAlias(alias)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}
        return (SecretKey)keys.getKey(alias,null);
    }
    Config get()throws Exception{
        String encrypted=prefs.getString("cipher","");long chat=prefs.getLong("chat",0);if(encrypted.isEmpty()||chat==0)return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        String token=new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        return new Config(token,prefs.getString("bot",""),prefs.getString("chat_name",""),chat);
    }
    void save(String token,String bot,String chatName,long chat)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!prefs.edit().putString("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("bot",bot).putString("chat_name",chatName).putLong("chat",chat).commit())throw new IllegalStateException();
    }
    String destination(){return prefs.getString("bot","").isEmpty()?"Бот не подключён":"@"+prefs.getString("bot","")+" → "+prefs.getString("chat_name","");}
    boolean connected(){return prefs.getLong("chat",0)!=0&&!prefs.getString("cipher","").isEmpty();}
    boolean automatic(){return prefs.getBoolean("auto_dtc",false);}
    void automatic(boolean enabled){prefs.edit().putBoolean("auto_dtc",enabled).apply();}
    void clear(){prefs.edit().clear().commit();try{KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);keys.deleteEntry(alias);}catch(Exception ignored){}}
}
