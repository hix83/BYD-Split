package ru.logunov.bydsplit;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.View;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses a generated PCM tone, not a music account or microphone. Exercises real global Visualizer. */
public final class MusicGlowInstrumentation extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();AudioTrack track=null;Thread writer=null;AtomicBoolean playing=new AtomicBoolean();try{
        byte[] silence=new byte[256];Arrays.fill(silence,(byte)128);
        check(MusicGlowView.signalLevel(silence)==0&&MusicGlowView.signalLevel(new byte[256])==0,"silence has energy");
        byte[] wave=new byte[256];for(int i=0;i<wave.length;i++)wave[i]=(byte)(128+80*Math.sin(i*.2));
        check(MusicGlowView.signalLevel(wave)>.5,"tone envelope missing");
        AppPreferences.get(getTargetContext()).edit().putBoolean(DockConfiguration.MODE,true).putBoolean(MusicGlowView.ENABLED,true).putInt(MusicGlowView.BRIGHTNESS,70).commit();
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo_mode",true));waitForIdleSync();
        DynamicDockView dock=(DynamicDockView)field(activity,"dynamicDock").get(activity);
        MusicGlowView glow=(MusicGlowView)field(dock,"musicGlow").get(dock);
        check(!dock.getClipToPadding(),"glow clipped at dock padding");
        check(glow.getLeft()==0&&glow.getTop()==0&&glow.getRight()==dock.getWidth()&&glow.getBottom()==dock.getHeight(),"glow does not reach dock edges");
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(16384).setTransferMode(AudioTrack.MODE_STREAM).build();
        track.setVolume(.6f);track.play();playing.set(true);AudioTrack output=track;
        writer=new Thread(()->{short[] pcm=new short[2048];long sample=0;while(playing.get()){
            for(int i=0;i<pcm.length;i++,sample++){double time=sample/44100.;double pulse=.25+.7*Math.pow(Math.max(0,Math.sin(time*Math.PI*3)),4);pcm[i]=(short)(22000*pulse*Math.sin(time*Math.PI*2*220));}
            if(output.write(pcm,0,pcm.length)<0)break;
        }},"glow-test-tone");writer.start();
        SystemClock.sleep(2500);
        check(field(glow,"capture").get(glow)!=null,"global Visualizer unavailable: "+MusicGlowView.status(activity));
        int before=field(glow,"frames").getInt(glow);SystemClock.sleep(2500);int rendered=field(glow,"frames").getInt(glow)-before;
        check(rendered>5&&rendered<=85,"frame cadence not bounded: "+rendered);
        check(field(glow,"lastSample").getLong(glow)>0,"no real audio callbacks");
        check(field(glow,"capturedPeak").getFloat(glow)>.005,"global capture delivered no tone energy");
        float peak=field(glow,"capturedPeak").getFloat(glow);
        Bitmap screenshot=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"music-glow.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,file);}screenshot.recycle();
        playing.set(false);track.pause();track.flush();if(writer!=null)writer.join(1500);SystemClock.sleep(3000);
        check(field(glow,"capture").get(glow)==null,"capture not released after pause; active="+activity.getSystemService(AudioManager.class).getActivePlaybackConfigurations()+"; status="+MusicGlowView.status(activity));
        check(!field(glow,"framesRunning").getBoolean(glow),"render loop kept running at silence");
        runOnMainSync(MusicGlowView::preview);SystemClock.sleep(1300);
        check(field(glow,"framesRunning").getBoolean(glow),"preview did not animate");
        Bitmap preview=getUiAutomation().takeScreenshot();try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"music-glow-preview.png"))){preview.compress(Bitmap.CompressFormat.PNG,100,file);}preview.recycle();
        runOnMainSync(()->{glow.setActive(false);});
        check(field(glow,"capture").get(glow)==null,"capture not released on background");
        AppPreferences.get(getTargetContext()).edit().putInt(MusicGlowView.BRIGHTNESS,40).commit();
        result.putString("result","PASS: waveform analysis, real global Visualizer with generated PCM peak="+peak+", animated frames="+rendered+"/2.5s, pause releases capture and frames, preview, lifecycle stop");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}finally{playing.set(false);if(track!=null){try{track.pause();}catch(Exception ignored){}if(writer!=null)try{writer.join(1000);}catch(InterruptedException ignored){}track.release();}}}
    private Field field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
