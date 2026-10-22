package ru.logunov.bydsplit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.*;
import android.view.View;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;


/** Touch palette and HSV editor. Changes preview live; every dismissal except Apply restores the old color. */
final class WaveColorDialog {
    static final String CUSTOM_COLOR="dock_music_glow_custom_color";
    static AlertDialog show(Activity activity,Runnable changed){return new Editor(activity,changed).show();}
    /** Hue runs left to right; saturation runs top to bottom. */
    static final class Palette extends View {
        final float[] hsv;
        final Runnable changed;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF bounds=new RectF();
        Shader shader;
        Palette(Activity activity,float[] hsv,Runnable changed){
            super(activity);this.hsv=hsv;this.changed=changed;
            setContentDescription("Палитра цвета волн: оттенок слева направо, насыщенность сверху вниз");
            setFocusable(true);
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            bounds.set(0,0,w,h);
            shader=new ComposeShader(new LinearGradient(0,0,w,0,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null,Shader.TileMode.CLAMP),
                new LinearGradient(0,0,0,h,Color.WHITE,0x00ffffff,Shader.TileMode.CLAMP),PorterDuff.Mode.SRC_OVER);
        }
        @Override protected void onDraw(Canvas canvas){
            float d=getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.FILL);paint.setShader(shader);canvas.drawRoundRect(bounds,12*d,12*d,paint);paint.setShader(null);
            float r=7*d;
            float x=Math.max(r+2*d,Math.min(getWidth()-r-2*d,hsv[0]/360f*getWidth()));
            float y=Math.max(r+2*d,Math.min(getHeight()-r-2*d,hsv[1]*getHeight()));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4*d);paint.setColor(0x99000000);canvas.drawCircle(x,y,r,paint);
            paint.setStrokeWidth(2*d);paint.setColor(Color.WHITE);canvas.drawCircle(x,y,r,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
                getParent().requestDisallowInterceptTouchEvent(true);
                hsv[0]=Math.min(359f,Math.max(0,event.getX()/Math.max(1,getWidth())*360f));
                hsv[1]=Math.min(1,Math.max(0,event.getY()/Math.max(1,getHeight())));
                if(hsv[2]==0)hsv[2]=1;
                changed.run();invalidate();
                if(action==MotionEvent.ACTION_UP){performClick();getParent().requestDisallowInterceptTouchEvent(false);}
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);return true;}
            return super.onTouchEvent(event);
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private static final class Editor {
        final Activity activity;
        final Runnable changed;
        final int original;
        final float[] hsv=new float[3];
        final SeekBar[] sliders=new SeekBar[3];
        final TextView[] labels=new TextView[3];
        final LinearLayout root;
        final TextView sample;
        final Palette palette;
        boolean updating,applied;
        int color;
        Editor(Activity activity,Runnable changed){
            this.activity=activity;this.changed=changed;
            original=AppPreferences.get(activity).getInt(MusicGlowView.COLOR,0);
            color=original==0?AppPreferences.get(activity).getInt(CUSTOM_COLOR,0xff2682df):original;
            Color.colorToHSV(color,hsv);
            root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(8),dp(24),dp(8));
            sample=text("Образец цвета",14);sample.setGravity(Gravity.CENTER);
            root.addView(sample,new LinearLayout.LayoutParams(-1,dp(44)));
            palette=new Palette(activity,hsv,()->{color=Color.HSVToColor(hsv);refresh(true);preview();});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(150));params.topMargin=dp(12);params.bottomMargin=dp(12);root.addView(palette,params);
            for(int i=0;i<3;i++){
                final int channel=i;labels[i]=text("",14);root.addView(labels[i]);
                sliders[i]=new SeekBar(activity);sliders[i].setMax(i==0?359:100);
                android.content.res.ColorStateList accent=android.content.res.ColorStateList.valueOf(activity.getColor(R.color.accent));
                sliders[i].setThumbTintList(accent);sliders[i].setProgressTintList(accent);
                sliders[i].setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemePalette.color(activity,0xff455361,0xffcdd6df)));
                sliders[i].setContentDescription(new String[]{"Оттенок волн","Насыщенность цвета волн","Светлота цвета волн"}[i]);
                sliders[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onStartTrackingTouch(SeekBar bar){} public void onStopTrackingTouch(SeekBar bar){}
                    public void onProgressChanged(SeekBar bar,int progress,boolean user){
                        if(!user||updating)return;
                        hsv[channel]=channel==0?progress:progress/100f;color=Color.HSVToColor(hsv);refresh(true);preview();
                    }
                });root.addView(sliders[i],new LinearLayout.LayoutParams(-1,dp(36)));
            }
            refresh(true);
        }
        AlertDialog show(){
            ScrollView scroll=new ScrollView(activity);scroll.addView(root);
            TextView title=text("Свой цвет волн",22);title.setPadding(dp(24),dp(20),dp(24),dp(10));
            AlertDialog dialog=new AlertDialog.Builder(activity,ThemePalette.dialogTheme(activity)).setCustomTitle(title).setView(scroll)
                    .setNegativeButton("Отмена",null).setPositiveButton("Применить",null).create();
            dialog.setOnDismissListener(d->{if(!applied){AppPreferences.get(activity).edit().putInt(MusicGlowView.COLOR,original).apply();changed.run();}});
            dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setBackgroundDrawable(shape(activity.getColor(R.color.surface_light),20));
            dialog.getWindow().getDecorView().setClipToOutline(true);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(.4f);
            dialog.getWindow().setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(40),dp(600)),-2);
            styleAction(dialog.getButton(AlertDialog.BUTTON_NEGATIVE),false);
            styleAction(dialog.getButton(AlertDialog.BUTTON_POSITIVE),true);
            Button cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE),apply=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            android.view.ViewGroup oldActions=(android.view.ViewGroup)cancel.getParent();
            oldActions.removeView(cancel);((android.view.ViewGroup)apply.getParent()).removeView(apply);oldActions.setVisibility(android.view.View.GONE);
            LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(12),0,dp(8));
            actions.addView(cancel,new LinearLayout.LayoutParams(dp(120),dp(44)));
            LinearLayout.LayoutParams applyParams=new LinearLayout.LayoutParams(dp(120),dp(44));applyParams.setMarginStart(dp(10));actions.addView(apply,applyParams);
            root.addView(actions,new LinearLayout.LayoutParams(-1,-2));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                applied=true;AppPreferences.get(activity).edit().putInt(MusicGlowView.COLOR,color).putInt(CUSTOM_COLOR,color).apply();changed.run();dialog.dismiss();
            });
            preview();return dialog;
        }
        void preview(){AppPreferences.get(activity).edit().putInt(MusicGlowView.COLOR,color).apply();changed.run();}
        void refresh(boolean updateHex){
            updating=true;
            palette.invalidate();
            sliders[0].setProgress(Math.round(hsv[0]));sliders[1].setProgress(Math.round(hsv[1]*100));sliders[2].setProgress(Math.round(hsv[2]*100));
            labels[0].setText("Оттенок: "+Math.round(hsv[0])+"°");labels[1].setText("Насыщенность: "+Math.round(hsv[1]*100)+"%");labels[2].setText("Светлота: "+Math.round(hsv[2]*100)+"%");
            GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(dp(12));sample.setBackground(background);
            sample.setTextColor(Color.luminance(color)>.4?Color.BLACK:Color.WHITE);updating=false;
        }
        void styleAction(Button button,boolean primary){
            button.setAllCaps(false);button.setTextSize(14);button.setTypeface(activity.getResources().getFont(R.font.ui7_regular));
            button.setTextColor(primary?Color.WHITE:ThemePalette.text(activity));
            button.setMinHeight(0);button.setMinimumHeight(0);button.setMinWidth(0);button.setMinimumWidth(0);
            button.setPadding(dp(16),0,dp(16),0);button.setBackgroundTintList(null);button.setStateListAnimator(null);button.setElevation(0);
            button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),shape(activity.getColor(primary?R.color.accent:R.color.button),14),null));
            android.view.ViewGroup.LayoutParams existing=button.getLayoutParams();existing.width=dp(120);existing.height=dp(44);
            if(existing instanceof LinearLayout.LayoutParams){((LinearLayout.LayoutParams)existing).weight=0;((LinearLayout.LayoutParams)existing).setMargins(dp(5),dp(6),dp(5),dp(10));}button.setLayoutParams(existing);
        }
        GradientDrawable shape(int color,int radius){GradientDrawable background=new GradientDrawable();background.setColor(color);background.setCornerRadius(dp(radius));return background;}
        TextView text(String value,int size){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(ThemePalette.text(activity));text.setTypeface(activity.getResources().getFont(R.font.ui7_regular));return text;}
        int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    }
}
