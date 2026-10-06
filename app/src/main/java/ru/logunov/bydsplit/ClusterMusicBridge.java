package ru.logunov.bydsplit;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Mirrors Yandex metadata to the OEM cluster. Adapted from BYDMate; see THIRD_PARTY_NOTICES.md. */
final class ClusterMusicBridge {
    static final String KEY="cluster_music_enabled";
    private final Context context;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();
    private boolean dirty;
    private volatile boolean closing;
    private int failures;
    private String confirmed;
    private long lastWrite;
    private int clearAttempts;
    ClusterMusicBridge(Context context){this.context=context.getApplicationContext();}
    void start(){executor.scheduleWithFixedDelay(this::tick,0,3,TimeUnit.SECONDS);}
    private boolean source(String pkg){return "ru.yandex.music".equals(pkg)||"ru.yandex.yandexnavi".equals(pkg);}
    private void tick(){
        try {
            boolean enabled=!closing&&AppPreferences.get(context).getBoolean(KEY,false)&&!AppPreferences.isDemoModeEnabled(context);
            if(!enabled&&!dirty)return;
            MediaSessionManager manager=(MediaSessionManager)context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> sessions=manager.getActiveSessions(new ComponentName(context,MaxCallNotificationService.class));
            MediaController owner=null;
            for(MediaController c:sessions){PlaybackState s=c.getPlaybackState();if(s!=null&&(s.getState()==PlaybackState.STATE_PLAYING||s.getState()==PlaybackState.STATE_BUFFERING)){owner=c;break;}}
            if(owner!=null&&!source(owner.getPackageName())){dirty=false;confirmed=null;clearAttempts=0;return;}
            if(owner==null)for(MediaController c:sessions){PlaybackState s=c.getPlaybackState();if(s!=null&&s.getState()==PlaybackState.STATE_PAUSED){owner=c;break;}}
            MediaMetadata md=owner==null?null:owner.getMetadata();
            String title=md==null?null:md.getString(MediaMetadata.METADATA_KEY_TITLE);
            boolean show=enabled&&owner!=null&&source(owner.getPackageName())&&title!=null&&!title.trim().isEmpty();
            if(!show&&!dirty)return;
            if(show&&failures>=3)return;
            int state=3,progress=0;String artist="";
            if(show){
                PlaybackState pb=owner.getPlaybackState();state=pb.getState()==PlaybackState.STATE_PAUSED?2:1;
                artist=md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if(artist==null)artist=md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                long duration=md.getLong(MediaMetadata.METADATA_KEY_DURATION),position=pb.getPosition();
                if(pb.getState()==PlaybackState.STATE_PLAYING&&position>=0)position+=(long)((SystemClock.elapsedRealtime()-pb.getLastPositionUpdateTime())*pb.getPlaybackSpeed());
                if(duration>0&&position>=0)progress=(int)Math.round(Math.max(0,Math.min(duration,position))*100.0/duration);
            }
            String args=state+" "+Base64.encodeToString(ClusterTrackText.encode(show?title.trim():""),Base64.NO_WRAP)+" "+Base64.encodeToString(ClusterTrackText.encode(show?artist:""),Base64.NO_WRAP)+" "+progress;
            long now=SystemClock.elapsedRealtime();
            if(show&&args.equals(confirmed)&&now-lastWrite<10000)return;
            if(!show&&clearAttempts++>=3){dirty=false;return;}
            dirty=true;
            lastWrite=now;
            String result=LocalAdbManager.get(context).writeClusterMusic(args);
            if(result.contains("OK")){dirty=show;failures=0;confirmed=show?args:null;clearAttempts=0;}else if(result.contains("UNSUPPORTED")||result.contains("ERROR")){failures++;Log.w("BYD_CLUSTER_MUSIC",result.trim());}
        }catch(SecurityException e){Log.w("BYD_CLUSTER_MUSIC","Notification access required");}
        catch(Exception e){Log.w("BYD_CLUSTER_MUSIC","Cluster sync unavailable",e);}
    }
    void close(){closing=true;executor.execute(this::tick);executor.shutdown();}
}
