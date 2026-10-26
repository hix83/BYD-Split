package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import static ru.logunov.bydsplit.ComfortState.Field.*;

/** OEM Song composition, scaled to the launcher pane. Display state comes from the shared optimistic controller. */
final class ClimatePanelView extends FrameLayout {
    interface Host {
        Integer current(ComfortState.Field field);
        boolean enabled();
        void command(ComfortState.Field field,int value);
    }
    private final Host host;
    private final List<Runnable> bindings=new ArrayList<>();
    private final List<Position> positions=new ArrayList<>();
    private final AirflowArt art;
    private final TextView fanStatus;
    private static final class Position {
        final View view;final int cx,cy,w,h;
        Position(View view,int cx,int cy,int w,int h){this.view=view;this.cx=cx;this.cy=cy;this.w=w;this.h=h;}
    }
    ClimatePanelView(Context context,Host host){
        super(context);this.host=host;setClipChildren(false);
        art=new AirflowArt();addView(art,new FrameLayout.LayoutParams(-1,-1));
        int[] icons={R.drawable.song_oem_ac_power_on,R.drawable.song_oem_control_mode,R.drawable.song_oem_ac_compressor,R.drawable.song_oem_ac_max,R.drawable.song_oem_ac_front_defrost,R.drawable.song_oem_mirrors_icon,R.drawable.song_air_cycle_state,R.drawable.song_oem_vent_only_icon};
        ComfortState.Field[] fields={POWER,AUTO,COMPRESSOR,MAX,FRONT,REAR,CYCLE,VENT};
        String[] labels={"Питание климата","Автоматический режим","Кондиционер A/C","Максимальное охлаждение","Обдув лобового стекла","Обогрев зеркал и заднего стекла","Рециркуляция","Вентиляция без охлаждения"};
        for(int i=0;i<fields.length;i++){
            final ComfortState.Field f=fields[i];final int on=f==AUTO?0:1;
            View control=iconControl(icons[i],f==VENT?"≋ ONLY":"",labels[i],()->{Integer v=host.current(f);if(v!=null)send(f,v==on?1-on:on);});
            place(control,48+i*100,40,94,52);
            bindings.add(()->{Integer v=host.current(f);style(control,v!=null&&v==on,host.enabled()&&v!=null,true);if(f==CYCLE)control.setContentDescription(v==null?"Рециркуляция: нет данных":v==1?"Рециркуляция в салоне":"Забор воздуха снаружи");});
        }
        place(new TemperatureRail(DRIVER_TEMP,"Водитель"),60,300,116,350);
        place(new TemperatureRail(PASSENGER_TEMP,"Пассажир"),740,300,116,350);
        direction("На стекло",4,R.drawable.song_oem_wind_mode_defrost_thumb_gray,210);
        direction("В лицо",1,R.drawable.song_oem_wind_mode_face_thumb_gray,285);
        direction("В ноги",2,R.drawable.song_oem_wind_mode_foot_thumb_gray,360);
        View dual=iconControl(0,"DUAL","Раздельная температура",()->{Integer v=host.current(DUAL);if(v!=null)send(DUAL,1-v);});place(dual,60,520,110,50);
        bindings.add(()->{Integer v=host.current(DUAL);style(dual,v!=null&&v==1,host.enabled()&&v!=null,true);});
        View less=iconControl(R.drawable.song_oem_ac_wind_level_min_normal,"","Уменьшить скорость вентилятора",()->stepFan(-1));
        View more=iconControl(R.drawable.song_oem_ac_wind_level_max_normal,"","Увеличить скорость вентилятора",()->stepFan(1));
        place(less,177,520,52,52);place(more,751,520,52,52);
        bindings.add(()->{Integer v=host.current(FAN);style(less,false,host.enabled()&&v!=null&&v>0,false);style(more,false,host.enabled()&&v!=null&&v<7,false);});
        LinearLayout bar=new LinearLayout(context);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(0,0,0,0);bar.setBackgroundColor(Color.TRANSPARENT);
        for(int i=1;i<=7;i++){
            final int speed=i;Button segment=new Button(context);segment.setAllCaps(false);segment.setText(""+i);segment.setTextSize(15);segment.setTypeface(getResources().getFont(R.font.ui7_regular));segment.setPadding(0,0,0,0);segment.setMinWidth(0);segment.setMinimumWidth(0);segment.setMinHeight(0);segment.setMinimumHeight(0);
            segment.setContentDescription("Скорость вентилятора: "+i);segment.setOnClickListener(v->send(FAN,speed));LinearLayout.LayoutParams sector=new LinearLayout.LayoutParams(0,-1,1);if(i>1)sector.leftMargin=dp(3);bar.addView(segment,sector);
            bindings.add(()->{Integer v=host.current(FAN);boolean fill=v!=null&&speed<=v,enabled=host.enabled()&&v!=null;segment.setEnabled(enabled);segment.setSelected(v!=null&&speed==v);segment.setAlpha(enabled?1:.35f);
                segment.setTextColor(fill?Color.WHITE:context.getColor(R.color.text_secondary));GradientDrawable sectorBackground=shape(fill?context.getColor(R.color.accent):ThemePalette.color(context,0xff2c3641,0xffe1e7ee),fill?0:ThemePalette.color(context,0xff536170,0xffb4c2cf));sectorBackground.setCornerRadius(dp(5));segment.setBackground(sectorBackground);});
        }
        place(bar,463,520,490,58);
        fanStatus=text("Скорость вентилятора · —",13,true);fanStatus.setGravity(Gravity.CENTER);place(fanStatus,463,476,490,30);
        bindings.add(()->{Integer v=host.current(FAN);fanStatus.setText(v==null?"Скорость вентилятора · нет данных":"Скорость вентилятора · "+v+" / 7");});
        refreshState();
    }
    private void direction(String title,int bit,int icon,int y){
        View control=iconControl(icon,"",title,()->{Integer mode=host.current(WIND);if(mode!=null){int next=ComfortState.windMode(ComfortState.windMask(mode)^bit);if(next>0)send(WIND,next);}});
        place(control,167,y,56,56);
        bindings.add(()->{Integer v=host.current(WIND);int mask=ComfortState.windMask(v==null?0:v);style(control,(mask&bit)!=0,host.enabled()&&v!=null&&ComfortState.windMode(mask^bit)>0,false);});
    }
    private void stepFan(int change){Integer v=host.current(FAN);if(v!=null&&v+change>=0&&v+change<=7)send(FAN,v+change);}
    private void send(ComfortState.Field f,int value){if(host.enabled()&&host.current(f)!=null)host.command(f,value);}
    private View iconControl(int icon,String caption,String description,Runnable action){
        View v;
        if(icon!=0){ImageButton b=new ImageButton(getContext());b.setImageResource(icon);b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setPadding(0,0,0,0);v=b;}
        else {Button b=new Button(getContext());b.setText(caption);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(getResources().getFont(R.font.ui7_regular));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);v=b;}
        v.setContentDescription(description);v.setOnClickListener(view->action.run());return v;
    }
    private void style(View view,boolean selected,boolean enabled,boolean background){
        int color=selected?getContext().getColor(R.color.accent):getContext().getColor(R.color.text_primary);
        view.setEnabled(enabled);view.setSelected(selected);view.setAlpha(enabled?1:.35f);
        if(view instanceof ImageView)((ImageView)view).setColorFilter(color);if(view instanceof TextView)((TextView)view).setTextColor(color);
        view.setBackground(shape(background?ThemePalette.color(getContext(),0x9c26313c,0xa6e5ebf1):Color.TRANSPARENT,selected&&background?color:0));
    }
    void refreshState(){for(Runnable update:bindings)update.run();art.invalidate();}
    private void place(View v,int cx,int cy,int w,int h){positions.add(new Position(v,cx,cy,w,h));addView(v);}
    private float scale(){return Math.min(getWidth()/800f,getHeight()/590f);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?dp(590):MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);
        art.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));float s=Math.min(width/800f,height/590f);
        for(Position p:positions)p.view.measure(MeasureSpec.makeMeasureSpec(Math.round(p.w*s),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(p.h*s),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        art.layout(0,0,getWidth(),getHeight());float s=scale(),x=(getWidth()-800*s)/2;
        for(Position p:positions){int left=Math.round(x+p.cx*s-p.view.getMeasuredWidth()/2f),top=Math.round(p.cy*s-p.view.getMeasuredHeight()/2f);p.view.layout(left,top,left+p.view.getMeasuredWidth(),top+p.view.getMeasuredHeight());}
    }
    private final class TemperatureRail extends LinearLayout {
        final ComfortState.Field field;final TextView previous,value,next;final ImageButton warmer,colder;
        TemperatureRail(ComfortState.Field field,String label){
            super(ClimatePanelView.this.getContext());this.field=field;setOrientation(VERTICAL);setGravity(Gravity.CENTER);
            TextView heading=text(label,13,true);heading.setGravity(Gravity.CENTER);addView(heading,new LinearLayout.LayoutParams(-1,dp(25)));
            warmer=arrow(R.drawable.song_oem_ac_temp_pick_plus,label+": увеличить температуру",1);addView(warmer,new LinearLayout.LayoutParams(-1,0,1));
            previous=text("—",27,true);previous.setGravity(Gravity.CENTER);previous.setAlpha(.4f);previous.setOnClickListener(v->step(1));addView(previous,new LinearLayout.LayoutParams(-1,0,1));
            value=text("—",40,false);value.setGravity(Gravity.CENTER);value.setSingleLine(true);addView(value,new LinearLayout.LayoutParams(-1,dp(76)));
            next=text("—",27,true);next.setGravity(Gravity.CENTER);next.setAlpha(.4f);next.setOnClickListener(v->step(-1));addView(next,new LinearLayout.LayoutParams(-1,0,1));
            colder=arrow(R.drawable.song_oem_ac_temp_pick_minus,label+": уменьшить температуру",-1);addView(colder,new LinearLayout.LayoutParams(-1,0,1));
            bindings.add(()->{Integer v=host.current(field),u=host.current(UNIT);int min=u!=null&&u==0?64:17,max=u!=null&&u==0?91:33;boolean enabled=host.enabled()&&v!=null;
                warmer.setEnabled(enabled&&v<max);colder.setEnabled(enabled&&v>min);previous.setEnabled(enabled&&v<max);next.setEnabled(enabled&&v>min);warmer.setAlpha(warmer.isEnabled()?1:.3f);colder.setAlpha(colder.isEnabled()?1:.3f);
                value.setText(v==null?"—":v+(u!=null&&u==0?"°F":"°C"));previous.setText(v==null||v>=max?"":String.valueOf(v+1));next.setText(v==null||v<=min?"":String.valueOf(v-1));value.setContentDescription(label+": "+value.getText());});
        }
        ImageButton arrow(int resource,String description,int change){ImageButton b=new ImageButton(getContext());b.setImageResource(resource);b.setScaleType(ImageView.ScaleType.FIT_CENTER);b.setPadding(dp(16),dp(6),dp(16),dp(6));b.setBackgroundColor(Color.TRANSPARENT);b.setContentDescription(description);b.setOnClickListener(v->step(change));return b;}
        void step(int amount){Integer v=host.current(field),unit=host.current(UNIT);if(v==null||unit==null)return;int n=v+amount;if(field.valid(n,unit))send(field,n);}
    }
    private final class AirflowArt extends View {
        private final Map<Integer,Bitmap> images=new HashMap<>();private final Paint paint=new Paint(3);
        private final int[] activeFrames={R.drawable.song_oem_wind_mode_defrost_animation_00,R.drawable.song_oem_wind_mode_face_animation_00,R.drawable.song_oem_wind_mode_foot_animation_00};
        private final int[] off={R.drawable.song_oem_wind_mode_defrost_off,R.drawable.song_oem_wind_mode_face_off,R.drawable.song_oem_wind_mode_foot_off};
        AirflowArt(){super(ClimatePanelView.this.getContext());setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void image(Canvas c,int id,float x,float y,float w,float h,int alpha){Bitmap b=images.get(id);if(b==null){BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;b=BitmapFactory.decodeResource(getResources(),id,options);images.put(id,b);}paint.setAlpha(alpha);c.drawBitmap(b,null,new RectF(x,y,x+w,y+h),paint);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float s=scale();c.save();c.translate((getWidth()-800*s)/2,0);c.scale(s,s);
            paint.setAlpha(255);paint.setShader(new RadialGradient(490,325,310,ThemePalette.color(getContext(),0x333ea7e9,0x224ca7df),Color.TRANSPARENT,Shader.TileMode.CLAMP));c.drawCircle(490,325,310,paint);paint.setShader(null);
            Integer mode=host.current(WIND),power=host.current(POWER),fan=host.current(FAN);int mask=ComfortState.windMask(mode==null?0:mode);
            boolean moving=power!=null&&power==1&&fan!=null&&fan>0;
            image(c,R.drawable.song_oem_wind_mode_background,440,148,238,246,mode==null?100:255);
            int[] bits={4,1,2};
            for(int zone=0;zone<3;zone++){
                int y=105+zone*124;boolean active=moving&&(mask&bits[zone])!=0;
                if(active)image(c,activeFrames[zone],150,y,450,124.5f,255);
                else image(c,off[zone],150,y,450,124.5f,mode==null?45:140);
            }
            c.restore();
        }
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean muted){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(getContext().getColor(muted?R.color.text_secondary:R.color.text_primary));t.setTypeface(getResources().getFont(R.font.ui7_regular));return t;}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(10));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
}
