package ru.logunov.bydsplit;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;
import android.view.View;

/** One small Canvas layer, 15 Hz audio envelopes and at most 30 fps. No microphone recording. */
final class MusicGlowView extends View implements android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    static final String ENABLED="dock_music_glow", BRIGHTNESS="dock_music_glow_brightness";
    static final String HEIGHT="dock_music_glow_height";
    static final String COLOR="dock_music_glow_color";
    static final String EFFECT="dock_music_glow_effect";
    static final String AMBIENT="dock_music_glow_ambient";
    static final String[] EFFECT_NAMES={"Волны","Пиксели","Эквалайзер","Осциллограмма","Спектр","Мягкое свечение"};
    static final String[] COLOR_NAMES={"Градиент","Синий","Бирюзовый","Фиолетовый","Зелёный","Янтарный","Розовый","Белый"};
    static final int[] COLORS={0,0xff2682df,0xff24c4cf,0xff8d6be8,0xff4abf8a,0xffffaf57,0xffec719f,0xffdfeaf5};
    static final int PERMISSION_REQUEST=342;
    private static volatile long previewUntil;
    private static volatile String lastStatus="Ожидание музыки";
    private static volatile String ambientStatus="Ожидание цвета подсветки";
    private final AmbientLightController ambientReader;
    private Integer ambientColor;
    private final AudioManager audio;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final Shader[] gradients=new Shader[3];
    private Shader effectGradient;
    private Visualizer capture;
    private boolean hostActive, framesRunning;
    private volatile float target;
    private float envelope;
    private volatile long lastSample;
    private long retryAfter;
    private int brightness=40;
    private int waveHeight=52;
    private int waveColor;
    private int frames;
    private float capturedPeak;
    private int captures;
    private int effect;
    private volatile float[] spectrum=new float[MusicSignal.BANDS], waveform=new float[MusicSignal.POINTS];
    private final float[] levels=new float[MusicSignal.BANDS];
    static boolean enabled(Context context){return AppPreferences.get(context).getBoolean(ENABLED,false);}
    static String status(Context context){
        if(!enabled(context))return "Выключено";
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return "Нужно разрешение на анализ аудио";
        return lastStatus+(AppPreferences.get(context).getBoolean(AMBIENT,false)?" · "+ambientStatus:"");
    }
    static void preview(){previewUntil=SystemClock.uptimeMillis()+6000;}
    MusicGlowView(Context context){
        super(context);audio=context.getSystemService(AudioManager.class);
        ambientReader=new AmbientLightController(context,color->{
            ambientColor=color==null?null:color.color;
            ambientStatus=color==null?"Цвет подсветки недоступен · выбранный цвет":color.status();
            updateColor();
        });
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);setFocusable(false);
    }
    void setActive(boolean active){hostActive=active;sync();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();AppPreferences.get(getContext()).registerOnSharedPreferenceChangeListener(this);sync();}
    @Override protected void onDetachedFromWindow(){AppPreferences.get(getContext()).unregisterOnSharedPreferenceChangeListener(this);stop();super.onDetachedFromWindow();}
    @Override public void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);sync();}
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){if(ENABLED.equals(key)||BRIGHTNESS.equals(key)||HEIGHT.equals(key)||COLOR.equals(key)||EFFECT.equals(key)||AMBIENT.equals(key))sync();}
    private boolean running(){return hostActive&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&enabled(getContext());}
    private void sync(){
        brightness=Math.max(0,Math.min(100,AppPreferences.get(getContext()).getInt(BRIGHTNESS,40)));
        waveHeight=Math.max(10,Math.min(80,AppPreferences.get(getContext()).getInt(HEIGHT,52)));
        effect=Math.max(0,Math.min(EFFECT_NAMES.length-1,AppPreferences.get(getContext()).getInt(EFFECT,0)));
        updateColor();
        boolean follow=AppPreferences.get(getContext()).getBoolean(AMBIENT,false);
        if(!follow)ambientColor=null;
        ambientReader.setActive(running()&&brightness>0&&follow);
        removeCallbacks(monitor);
        if(running()&&brightness>0)post(monitor);else stop();
    }
    private void updateColor(){
        boolean follow=AppPreferences.get(getContext()).getBoolean(AMBIENT,false);
        int color=follow&&ambientColor!=null?ambientColor:AppPreferences.get(getContext()).getInt(COLOR,0);
        if(color!=waveColor){waveColor=color;rebuildGradients();invalidate();}
    }
    private final Runnable monitor=new Runnable(){public void run(){
        if(!running()||brightness==0){stop();return;}
        long now=SystemClock.uptimeMillis();
        if(previewUntil>now){
            releaseCapture();lastStatus="Предпросмотр эффекта · 6 секунд";
            target=.25f+.65f*(float)Math.pow(Math.max(0,Math.sin(now/1000.0*Math.PI*3)),3);lastSample=now;startFrames();
            float[] demoBands=new float[MusicSignal.BANDS], demoWave=new float[MusicSignal.POINTS];
            for(int i=0;i<demoBands.length;i++)demoBands[i]=target*(.2f+.8f*(float)Math.abs(Math.sin(now/380.0+i*.43)));
            for(int i=0;i<demoWave.length;i++)demoWave[i]=target*(float)Math.sin(i*.3+now/120.0);
            spectrum=demoBands;waveform=demoWave;
        }else if(getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            releaseCapture();target=0;lastStatus="Нужно разрешение на анализ аудио";
        }else if(!musicPlaying()){
            releaseCapture();target=0;lastStatus="Ожидание музыки";
        }else if(capture==null&&now>=retryAfter)openCapture();
        postDelayed(this,previewUntil>now?66:500);
    }};
    private boolean musicPlaying(){
        if(audio==null)return false;
        try{
            for(android.media.AudioPlaybackConfiguration player:audio.getActivePlaybackConfigurations()){
                int usage=player.getAudioAttributes().getUsage();
                if(usage==android.media.AudioAttributes.USAGE_MEDIA||usage==android.media.AudioAttributes.USAGE_GAME)return true;
            }
            return false;
        }catch(RuntimeException ignored){return audio.isMusicActive();}
    }
    private void openCapture(){
        try{
            capture=new Visualizer(0);
            int[] range=Visualizer.getCaptureSizeRange();int size=Math.max(range[0],Math.min(256,range[1]));
            capture.setCaptureSize(size);capture.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            int result=capture.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                public void onWaveFormDataCapture(Visualizer source,byte[] waveform,int rate){
                    if(source!=capture||!running())return;
                    target=signalLevel(waveform);lastSample=SystemClock.uptimeMillis();
                    MusicGlowView.this.waveform=MusicSignal.waveform(waveform);
                    capturedPeak=Math.max(capturedPeak,target);captures++;
                    lastStatus=target>.008f?"Свечение реагирует на музыку":"Музыка играет · ждём аудиосигнал";
                    if(target>.004f)startFrames();
                }
                public void onFftDataCapture(Visualizer source,byte[] fft,int rate){if(source==capture&&running())spectrum=MusicSignal.spectrum(fft);}
            },Math.min(15000,Visualizer.getMaxCaptureRate()),true,true);
            if(result!=Visualizer.SUCCESS||capture.setEnabled(true)!=Visualizer.SUCCESS)throw new IllegalStateException("Visualizer unavailable");
            lastStatus="Музыка играет · ждём аудиосигнал";
        }catch(RuntimeException error){releaseCapture();target=0;retryAfter=SystemClock.uptimeMillis()+10000;lastStatus="Аудиовыход недоступен для визуализации на этой системе";android.util.Log.w("BYD_MUSIC_GLOW",lastStatus,error);}
    }
    /** AC energy: constant / zero-filled buffers must never look like a beat. */
    static float signalLevel(byte[] waveform){
        if(waveform==null||waveform.length==0)return 0;
        double mean=0,squares=0;
        for(byte sample:waveform){int value=sample&255;mean+=value;squares+=value*value;}
        mean/=waveform.length;double variance=Math.max(0,squares/waveform.length-mean*mean);
        float level=(float)Math.sqrt(variance)/64f;
        return Math.max(0,Math.min(1,(level-.008f)*2.2f));
    }
    private void startFrames(){if(!framesRunning){framesRunning=true;post(frame);}}
    private final Runnable frame=new Runnable(){public void run(){
        if(!running()){stop();return;}
        float wanted=SystemClock.uptimeMillis()-lastSample>600?0:target;
        envelope+=(wanted-envelope)*(wanted>envelope?.48f:.13f);
        float[] bands=spectrum;
        for(int i=0;i<levels.length;i++){
            float next=wanted>.003f?bands[i]:0;
            levels[i]+=(next-levels[i])*(next>levels[i]?.55f:.18f);
        }
        if(envelope<.003f&&wanted<.003f){envelope=0;framesRunning=false;invalidate();return;}
        frames++;invalidate();postDelayed(this,33);
    }};
    private void releaseCapture(){Visualizer old=capture;capture=null;if(old!=null)try{old.release();}catch(RuntimeException ignored){}}
    private void stop(){removeCallbacks(monitor);removeCallbacks(frame);framesRunning=false;releaseCapture();if(ambientReader!=null)ambientReader.setActive(false);ambientColor=null;target=0;envelope=0;java.util.Arrays.fill(levels,0);invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        rebuildGradients();
    }
    private void rebuildGradients(){
        int h=getHeight();if(h<=0)return;
        int[] colors={0xff2682df,0xff40cbd7,0xff8065df};
        if(waveColor!=0){
            float[] hsv=new float[3];Color.colorToHSV(waveColor,hsv);
            colors[0]=waveColor|0xff000000;
            colors[1]=Color.HSVToColor(new float[]{hsv[0],hsv[1]*.75f,Math.min(1,hsv[2]*1.08f)});
            colors[2]=Color.HSVToColor(new float[]{hsv[0],hsv[1]*.9f,hsv[2]*.85f});
        }
        for(int i=0;i<3;i++)gradients[i]=new LinearGradient(0,h*.42f,0,h,
                new int[]{colors[i]&0x00ffffff,(colors[i]&0x00ffffff)|0x66000000,colors[i]},
                new float[]{0,.4f,1},Shader.TileMode.CLAMP);
        effectGradient=new LinearGradient(0,0,Math.max(1,getWidth()),0,colors,null,Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(envelope<.003f||brightness==0)return;
        float width=getWidth(),height=getHeight(),phase=SystemClock.uptimeMillis()/1000f;
        if(effect!=0){drawEffect(canvas,width,height);return;}
        for(int layer=0;layer<3;layer++){
            path.reset();path.moveTo(0,height);
            for(int point=0;point<=40;point++){
                float x=width*point/40f;
                float wave=.65f+.23f*(float)Math.sin(point/40f*Math.PI*3+phase*(1.4f+layer*.25f)+layer*1.7f);
                float rise=height*envelope*(waveHeight/100f)*(1-layer*(.105f/.52f))*wave;
                path.lineTo(x,height-rise);
            }
            path.lineTo(width,height);path.close();paint.setShader(gradients[layer]);
            // Keep the background translucent even at maximum brightness so captions retain contrast.
            paint.setAlpha(Math.min(layer==0?128:80,Math.round((layer==0?200:120)*brightness/100f)));
            canvas.drawPath(path,paint);
        }
        paint.setShader(null);
    }
    private void drawEffect(Canvas canvas,float width,float height){
        float max=height*waveHeight/100f, bottom=height-2;
        int color=waveColor==0?0xff40cbd7:waveColor;
        paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(color);
        if(waveColor==0)paint.setShader(effectGradient);
        paint.setAlpha(Math.round(140*brightness/100f));
        if(effect==1||effect==2||effect==4){
            float cell=width/levels.length, gap=Math.max(2,cell*.18f);
            for(int i=0;i<levels.length;i++){
                float rise=max*levels[i];
                if(effect==1){
                    float step=Math.max(5,height/12f);
                    for(int row=0;row<12&&row*step<rise;row++)
                        canvas.drawRoundRect(i*cell+gap/2,bottom-(row+1)*step+2,(i+1)*cell-gap/2,bottom-row*step,2,2,paint);
                }else if(effect==2){
                    canvas.drawRoundRect(i*cell+gap/2,bottom-rise,(i+1)*cell-gap/2,bottom,3,3,paint);
                }else{
                    float center=height-max*.5f;
                    canvas.drawRoundRect(i*cell+gap/2,center-rise*.5f,(i+1)*cell-gap/2,center+rise*.5f,3,3,paint);
                }
            }
        }else if(effect==3){
            float[] points=waveform;path.reset();
            float center=height-max*.5f;
            for(int i=0;i<points.length;i++){
                float x=width*i/(points.length-1),y=center-points[i]*max*.48f;
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,getResources().getDisplayMetrics().density*1.5f));
            canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        }else{
            paint.setShader(gradients[0]);paint.setAlpha(Math.round(150*brightness/100f*envelope));
            canvas.save();canvas.translate(0,height-max);canvas.scale(1,max/height);
            canvas.drawRect(0,0,width,height,paint);canvas.restore();
        }
        paint.setShader(null);
    }
}
