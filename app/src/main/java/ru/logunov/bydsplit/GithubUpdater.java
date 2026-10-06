package ru.logunov.bydsplit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

final class GithubUpdater {
    static final String AUTO_KEY="github_auto_update";
    private static final String API="https://api.github.com/repos/hix83/BYD-Split/releases/latest";
    private static final String ASSET_PREFIX="https://github.com/hix83/BYD-Split/releases/download/";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final long MAX_BYTES=100L*1024*1024;
    static void onResume(Activity activity){
        if(AppPreferences.get(activity).getBoolean("update_pending_install",false)) {
            if(activity.getPackageManager().canRequestPackageInstalls()) {
                AppPreferences.get(activity).edit().putBoolean("update_pending_install",false).apply();
                try{validate(activity,new File(activity.getCacheDir(),"update.apk"));install(activity);}catch(Exception e){message(activity,"Загрузите обновление повторно");}
            }
            return;
        }
        if(AppPreferences.get(activity).getBoolean(AUTO_KEY,true)&&System.currentTimeMillis()-AppPreferences.get(activity).getLong("update_last_check",0)>86400000L)check(activity,false);
    }
    static void check(Activity activity,boolean manual){
        if(!BUSY.compareAndSet(false,true)){if(manual)message(activity,"Проверка уже выполняется");return;}
        if(manual)message(activity,"Проверяем GitHub Releases…");
        IO.execute(()->{
            try {
                String json=new String(fetch(API,1024*1024),StandardCharsets.UTF_8);
                JSONObject release=new JSONObject(json);
                String tag=release.getString("tag_name");
                PackageInfo current=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
                AppPreferences.get(activity).edit().putLong("update_last_check",System.currentTimeMillis()).apply();
                if(release.optBoolean("draft")||release.optBoolean("prerelease")||!UpdateVersion.newer(tag,current.versionName)) {
                    if(manual)activity.runOnUiThread(()->message(activity,"Установлена последняя версия"));return;
                }
                JSONArray assets=release.getJSONArray("assets");JSONObject apk=null;
                for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if(a.getString("name").equals("BYD-Split-"+tag.replaceFirst("^v", "")+".apk")){apk=a;break;}}
                if(apk==null)throw new IOException("APK отсутствует в релизе");
                String url=apk.getString("browser_download_url"),digest=apk.optString("digest");
                if(!url.startsWith(ASSET_PREFIX)||apk.getLong("size")>MAX_BYTES)throw new IOException("Некорректный APK релиза");
                String body=release.optString("body");
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    new AlertDialog.Builder(activity).setTitle("Обновление "+tag)
                        .setMessage(body.isEmpty()?"Доступна новая версия BYD Split":body)
                        .setNegativeButton("Позже",null).setPositiveButton("Обновить",(d,w)->download(activity,url,digest)).show();
                });
            }catch(Exception e){if(manual)activity.runOnUiThread(()->message(activity,"Не удалось проверить обновление: "+e.getMessage()));}
            finally{BUSY.set(false);}
        });
    }
    private static void download(Activity activity,String url,String digest){
        if(!BUSY.compareAndSet(false,true))return;
        message(activity,"Загружаем обновление…");
        IO.execute(()->{
            File temp=new File(activity.getCacheDir(),"update.part");
            try{
                HttpURLConnection conn=open(url);
                try(InputStream input=conn.getInputStream();OutputStream output=new FileOutputStream(temp)){
                    byte[] buffer=new byte[16384];int n;long total=0;MessageDigest sha=MessageDigest.getInstance("SHA-256");
                    while((n=input.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("APK слишком большой");sha.update(buffer,0,n);output.write(buffer,0,n);}
                    if(digest.startsWith("sha256:")&&!digest.substring(7).equalsIgnoreCase(hex(sha.digest())))throw new IOException("Контрольная сумма не совпала");
                }finally{conn.disconnect();}
                validate(activity,temp);
                File ready=new File(activity.getCacheDir(),"update.apk");
                if(!temp.renameTo(ready))throw new IOException("Не удалось сохранить APK");
                activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())install(activity);});
            }catch(Exception e){temp.delete();activity.runOnUiThread(()->message(activity,"Обновление не установлено: "+e.getMessage()));}
            finally{BUSY.set(false);}
        });
    }
    private static void validate(Activity activity,File apk)throws Exception{
        PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo next=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),current=pm.getPackageInfo(activity.getPackageName(),flags);
        if(next==null||!activity.getPackageName().equals(next.packageName)||next.versionCode<=current.versionCode)throw new IOException("Пакет или версия APK не подходят");
        Signature[] a=Build.VERSION.SDK_INT>=28?next.signingInfo.getApkContentsSigners():next.signatures;
        Signature[] b=Build.VERSION.SDK_INT>=28?current.signingInfo.getApkContentsSigners():current.signatures;
        if(a==null||b==null||a.length!=b.length)throw new IOException("Подпись APK не совпала");
        for(Signature sig:a){boolean found=false;for(Signature own:b)if(Arrays.equals(sig.toByteArray(),own.toByteArray()))found=true;if(!found)throw new IOException("Подпись APK не совпала");}
    }
    private static void install(Activity activity){
        try{
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                AppPreferences.get(activity).edit().putBoolean("update_pending_install",true).apply();
                message(activity,"Разрешите установку обновлений для BYD Split");
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));return;
            }
            Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("update",uri));activity.startActivity(intent);
        }catch(Exception e){message(activity,"Не удалось открыть установщик: "+e.getMessage());}
    }
    private static HttpURLConnection open(String url)throws Exception{
        for(int i=0;i<6;i++){
            URL target=new URL(url);if(!"https".equals(target.getProtocol()))throw new IOException("Требуется HTTPS");
            HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","BYD-Split-Updater");
            int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");c.disconnect();url=new URL(target,location).toString();continue;}
            if(code!=200){c.disconnect();throw new IOException("GitHub HTTP "+code);}return c;
        }throw new IOException("Слишком много перенаправлений");
    }
    private static byte[] fetch(String url,int limit)throws Exception{
        HttpURLConnection c=open(url);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Ответ слишком большой");out.write(buf,0,n);}return out.toByteArray();
        }finally{c.disconnect();}
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
    private static void message(Activity activity,String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
}
