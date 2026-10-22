package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import static ru.logunov.bydsplit.ComfortState.Field.*;

/** In-pane climate / seat controls. Only explicit taps submit writes. */
final class ComfortView extends LinearLayout {
    private final ComfortController controller;
    private final Runnable onStateChanged=this::refresh;
    private final List<Runnable> bindings=new ArrayList<>();
    private final TextView status;
    private final FittedPanel content;
    private final Button climateTab,seatsTab;
    private boolean foreground,subscribed;
    private boolean seats;
    private final Runnable pageChanged;
    private final Runnable closePage;


    ComfortView(Context context,boolean seats,Runnable close,Runnable pageChanged) {
        super(context);controller=ComfortController.get(context);this.seats=seats;this.pageChanged=pageChanged;this.closePage=close;
        setOrientation(VERTICAL);setPadding(dp(20),dp(14),dp(20),dp(14));setClickable(true);
        setBackground(shape(ThemePalette.color(context,0xff191f27,0xfff4f6f8),0));
        LinearLayout header=row();
        climateTab=button("Климат",()->switchTab(false)); seatsTab=button("Сиденья и руль",()->switchTab(true));
        header.addView(climateTab,new LayoutParams(0,dp(48),1));
        LayoutParams tabParams=new LayoutParams(0,dp(48),1);tabParams.leftMargin=dp(8);header.addView(seatsTab,tabParams);
        Button back=button("×  Закрыть",close);back.setTextColor(accent());back.setBackground(shape(getContext().getColor(R.color.surface_light),accent()));
        back.setContentDescription("Закрыть экран и вернуться к приложению");
        LayoutParams backParams=new LayoutParams(dp(140),dp(48));backParams.leftMargin=dp(28);header.addView(back,backParams);addView(header);
        status=text("Получение состояний автомобиля…",13,true);LayoutParams statusParams=new LayoutParams(-1,dp(35));addView(status,statusParams);
        content=new FittedPanel(context);addView(content,new LayoutParams(-1,0,1));
        build();
    }
    boolean seatsPage(){return seats;}
    private void switchTab(boolean value){if(seats==value){closePage.run();return;}seats=value;build();pageChanged.run();}
    private void build(){
        bindings.clear();content.removeAllViews();style(climateTab,!seats,true);style(seatsTab,seats,true);
        if(seats)buildSeats();else buildClimate();refresh();
    }
    private void buildClimate(){
        ClimatePanelView panel=new ClimatePanelView(getContext(),new ClimatePanelView.Host(){
            public Integer current(ComfortState.Field field){return ComfortView.this.current(field);}
            public boolean enabled(){return available();}
            public void command(ComfortState.Field field,int value){write(field,value);}
        });
        content.show(panel,800,590);bindings.add(panel::refreshState);
    }
    private void buildSeats(){
        SeatCabin cabin=new SeatCabin();
        content.show(cabin,790,632);
        bindings.add(cabin::refreshState);
    }
    /** Original 518×604 dp OEM cabin, with accessible controls anchored to the seats. */
    private final class SeatCabin extends android.widget.FrameLayout {
        private final CabinArt art=new CabinArt();
        private final java.util.Map<ComfortState.Field,CabinButton> controls=new java.util.EnumMap<>(ComfortState.Field.class);
        private final TextView driverLabel=text("Водитель",13,false),passengerLabel=text("Пассажир",13,false);
        private final TextView hint=text("Нажатие: 2 → 1 → выкл · удержание выключает",12,true);
        private final CabinPair driverPair,passengerPair;
        SeatCabin(){
            super(ComfortView.this.getContext());setClipChildren(false);addView(art,new FrameLayout.LayoutParams(-1,-1));
            put(WHEEL,"Руль",R.drawable.ui7_steering_heat);put(REAR,"Зеркала и стекло",R.drawable.song_oem_mirrors_icon);
            driverPair=new CabinPair(DRIVER_HEAT,DRIVER_VENT);passengerPair=new CabinPair(PASSENGER_HEAT,PASSENGER_VENT);
            addView(driverPair);addView(passengerPair);
            driverLabel.setGravity(Gravity.CENTER);passengerLabel.setGravity(Gravity.CENTER);hint.setGravity(Gravity.CENTER);
            addView(driverLabel);addView(passengerLabel);addView(hint);
        }
        private void put(ComfortState.Field field,String title,int icon){CabinButton b=new CabinButton(field,title,icon);controls.put(field,b);addView(b);}
        private float scale(){return Math.min(getWidth()/790f,Math.max(1,getHeight()-dp(28))/604f);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED?dp(590):MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width,height);art.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
            float scale=Math.min(width/790f,Math.max(1,height-dp(28))/604f);
            int controlWidth=Math.round(118*scale),controlHeight=Math.max(dp(48),Math.round(52*scale));
            for(ComfortState.Field f:new ComfortState.Field[]{WHEEL,REAR})controls.get(f).measure(MeasureSpec.makeMeasureSpec(controlWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(controlHeight,MeasureSpec.EXACTLY));
            for(CabinPair pair:new CabinPair[]{driverPair,passengerPair})pair.measure(MeasureSpec.makeMeasureSpec(controlWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(controlHeight*2+dp(1),MeasureSpec.EXACTLY));
            driverLabel.measure(MeasureSpec.makeMeasureSpec(Math.round(132*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(24),MeasureSpec.EXACTLY));
            passengerLabel.measure(MeasureSpec.makeMeasureSpec(Math.round(132*scale),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(24),MeasureSpec.EXACTLY));
            hint.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(28),MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
            art.layout(0,0,getWidth(),getHeight());float scale=scale(),x=(getWidth()-518*scale)/2;
            place(controls.get(WHEEL),x,scale,-62,112);place(controls.get(REAR),x,scale,580,112);
            place(driverPair,x,scale,-62,236);place(passengerPair,x,scale,580,236);
            place(driverLabel,x,scale,-62,170);place(passengerLabel,x,scale,580,170);
            hint.layout(0,getHeight()-dp(28),getWidth(),getHeight());
        }
        private void place(View v,float x,float scale,int cx,int cy){int l=Math.round(x+cx*scale-v.getMeasuredWidth()/2f),t=Math.round(cy*scale-v.getMeasuredHeight()/2f);v.layout(l,t,l+v.getMeasuredWidth(),t+v.getMeasuredHeight());}
        void refreshState(){for(CabinButton b:controls.values())b.update();art.invalidate();}
        private final class CabinPair extends LinearLayout {
            CabinPair(ComfortState.Field heat,ComfortState.Field vent){
                super(ComfortView.this.getContext());setOrientation(VERTICAL);
                setBackground(shape(ThemePalette.color(getContext(),0xa6222e39,0xa6f1f5f9),ThemePalette.color(getContext(),0xff52606e,0xffa3aeb9)));
                setClipToOutline(true);
                CabinButton upper=new CabinButton(heat,"Подогрев",R.drawable.song_oem_heat_icon),lower=new CabinButton(vent,"Вентиляция",R.drawable.song_oem_vent_icon);
                controls.put(heat,upper);controls.put(vent,lower);
                addView(upper,new LayoutParams(-1,0,1));
                View divider=new View(getContext());divider.setBackgroundColor(getContext().getColor(R.color.text_secondary));divider.setAlpha(.3f);
                LayoutParams line=new LayoutParams(-1,dp(1));line.leftMargin=dp(12);line.rightMargin=dp(12);addView(divider,line);
                addView(lower,new LayoutParams(-1,0,1));
            }
        }
        private final class CabinButton extends LinearLayout {
            private final ComfortState.Field field;
            private final LevelBars levels;
            private final ImageView icon;
            CabinButton(ComfortState.Field field,String label,int drawable){
                super(ComfortView.this.getContext());this.field=field;setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(dp(5),dp(2),dp(5),dp(2));
                icon=new ImageView(getContext());icon.setImageResource(drawable);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                addView(icon,new LayoutParams(dp(field==WHEEL?36:60),dp(36)));
                levels=new LevelBars(field==WHEEL||field==REAR?1:2);addView(levels,new LayoutParams(dp(56),dp(10)));
                setFocusable(true);setClickable(true);
                setOnClickListener(v->{Integer raw=current(field);if(raw!=null&&available())write(field,controller.nextUiValue(field));});
                setOnLongClickListener(v->{if(current(field)!=null&&available())write(field,0);return true;});
            }
            void update(){
                Integer raw=current(field);int level=raw==null?-1:field.seat()?raw-1:raw;boolean on=level>0,enabled=raw!=null&&available();
                int color=on?(field==DRIVER_VENT||field==PASSENGER_VENT?0xff66b9fc:0xffffa06b):getContext().getColor(R.color.text_primary);
                icon.setColorFilter(color);levels.level=level;levels.color=color;levels.invalidate();
                setEnabled(enabled);setAlpha(enabled?1:.42f);setSelected(on);
                if(field==WHEEL||field==REAR){
                    int bg=ThemePalette.color(getContext(),0xa6222e39,0xa6f1f5f9);setBackground(shape(bg,on?color:ThemePalette.color(getContext(),0xff52606e,0xffa3aeb9)));
                }else setBackgroundColor(on?(0x33000000|(color&0x00ffffff)):android.graphics.Color.TRANSPARENT);
                setContentDescription(fieldLabel(field)+": "+(level<0?"нет данных":level==0?"выключен":"уровень "+level)+". Нажатие меняет уровень, удержание выключает");
            }
        }
        private final class LevelBars extends View {
            int level=-1,color;private final int count;private final android.graphics.Paint paint=new android.graphics.Paint(3);
            LevelBars(int count){super(ComfortView.this.getContext());this.count=count;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
            @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float gap=dp(3),w=Math.min(dp(15),(getWidth()-gap*(count-1))/count),start=(getWidth()-count*w-gap*(count-1))/2;
                for(int i=0;i<count;i++){paint.setColor(color);paint.setAlpha(level>i?255:70);c.drawRoundRect(start+i*(w+gap),dp(4),start+i*(w+gap)+w,dp(7),dp(2),dp(2),paint);}}
        }
        private final class CabinArt extends View {
            private final java.util.Map<Integer,android.graphics.Bitmap> images=new java.util.HashMap<>();
            private final android.graphics.Paint paint=new android.graphics.Paint(3);
            CabinArt(){super(ComfortView.this.getContext());setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
            private void image(android.graphics.Canvas c,int id,int x,int y,int w,int h){android.graphics.Bitmap b=images.get(id);if(b==null){android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inScaled=false;b=android.graphics.BitmapFactory.decodeResource(getResources(),id,opts);images.put(id,b);}c.drawBitmap(b,null,new android.graphics.RectF(x,y,x+w,y+h),paint);}
            @Override protected void onDraw(android.graphics.Canvas c){
                super.onDraw(c);float scale=scale();c.save();c.translate((getWidth()-518*scale)/2,0);c.scale(scale/2,scale/2);
                paint.setAlpha(255);
                image(c,R.drawable.song_oem_seat_bg_default,0,0,1036,1208);
                image(c,R.drawable.song_oem_driver_seat_default,0,0,518,680);image(c,R.drawable.song_oem_passenger_seat_default,518,0,518,680);
                image(c,R.drawable.song_oem_rear_left_seat_default,0,680,518,528);image(c,R.drawable.song_oem_rear_right_seat_default,518,680,518,528);
                highlight(c,DRIVER_HEAT,DRIVER_VENT,false);highlight(c,PASSENGER_HEAT,PASSENGER_VENT,true);
                Integer wheel=current(WHEEL);image(c,wheel!=null&&wheel>1?R.drawable.song_oem_steer_wheel_heat:R.drawable.song_oem_steer_wheel,0,0,518,320);
                Integer rear=current(REAR);if(rear!=null&&rear==1)image(c,R.drawable.song_oem_rear_mirror_heat_state,0,0,1036,320);
                paint.setColor(getContext().getColor(R.color.text_secondary));paint.setAlpha(155);paint.setStrokeWidth(dp(1)*2/scale);
                connector(c,-3,112,155,112);connector(c,-3,236,110,236);
                connector(c,521,236,410,236);
                c.restore();
            }
            private void connector(android.graphics.Canvas c,int fromX,int fromY,int toX,int toY){
                c.drawLine(fromX*2,fromY*2,toX*2,toY*2,paint);c.drawCircle(toX*2,toY*2,3,paint);
            }
            private void highlight(android.graphics.Canvas c,ComfortState.Field heat,ComfortState.Field vent,boolean right){
                Integer h=current(heat),v=current(vent);int id=0;
                if(h!=null&&h>1)id=h==2?R.drawable.song_oem_driver_seat_heat_one:R.drawable.song_oem_driver_seat_heat_two;
                else if(v!=null&&v>1)id=v==2?R.drawable.song_oem_driver_seat_vent_one:R.drawable.song_oem_driver_seat_vent_two;
                if(id==0)return;c.save();if(right){c.translate(1036,0);c.scale(-1,1);}image(c,id,0,0,518,680);c.restore();
            }
        }
    }
    private Integer current(ComfortState.Field field){return available()?controller.current(field):null;}
    /** Scale art, text and touch targets together to the actual pane, including a full-width dock. */
    private final class FittedPanel extends FrameLayout {
        private int designWidth,designHeight;
        FittedPanel(Context context){super(context);setClipChildren(true);}
        void show(View panel,int width,int height){
            removeAllViews();designWidth=dp(width);designHeight=dp(height);
            addView(panel,new FrameLayout.LayoutParams(designWidth,designHeight));
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width,height);
            if(getChildCount()>0)getChildAt(0).measure(MeasureSpec.makeMeasureSpec(designWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(designHeight,MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            if(getChildCount()==0)return;
            View panel=getChildAt(0);float scale=Math.min(getWidth()/(float)designWidth,getHeight()/(float)designHeight);
            panel.setPivotX(0);panel.setPivotY(0);panel.setScaleX(scale);panel.setScaleY(scale);
            int x=Math.round((getWidth()-designWidth*scale)/2),y=Math.round((getHeight()-designHeight*scale)/2);
            panel.layout(x,y,x+designWidth,y+designHeight);
        }
    }
    private boolean available(){return foreground&&isAttachedToWindow()&&controller.available();}
    private void refresh(){
        for(Runnable update:bindings)update.run();
        if(AppPreferences.isDemoModeEnabled(getContext()))status.setText("Демо: кнопки меняют только показания на экране");
        else if(controller.pending())status.setText("Применяем настройки…");
        else status.setText(available()?"Состояния автомобиля · обновляются автоматически":"Нет свежих данных от автомобиля · управление недоступно");
    }
    void setActive(boolean value){foreground=value;updateRunning();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateRunning();}
    @Override protected void onDetachedFromWindow(){unsubscribe();super.onDetachedFromWindow();}
    private void updateRunning(){
        boolean shouldListen=foreground&&isAttachedToWindow();
        if(shouldListen&&!subscribed){subscribed=true;controller.addListener(onStateChanged);}
        else if(!shouldListen)unsubscribe();
        refresh();
    }
    private void unsubscribe(){if(subscribed){subscribed=false;controller.removeListener(onStateChanged);}}
    private void write(ComfortState.Field field,int value){
        if(!available()||current(field)==null)return;
        try{controller.request(field,value);}catch(IllegalArgumentException ignored){/* unavailable or out of range */}
    }
    private static String fieldLabel(ComfortState.Field field){
        switch(field){
            case DRIVER_HEAT:return "Подогрев сиденья водителя";
            case PASSENGER_HEAT:return "Подогрев сиденья пассажира";
            case DRIVER_VENT:return "Вентиляция сиденья водителя";
            case PASSENGER_VENT:return "Вентиляция сиденья пассажира";
            case WHEEL:return "Подогрев руля";
            case DRIVER_TEMP:return "Температура водителя";
            case PASSENGER_TEMP:return "Температура пассажира";
            case FAN:return "Скорость вентилятора";
            case WIND:return "Направление обдува";
            case POWER:return "Питание климата";
            case AUTO:return "Автоматический режим";
            case COMPRESSOR:return "Кондиционер";
            case DUAL:return "Раздельная температура";
            case FRONT:return "Обдув лобового стекла";
            case REAR:return "Обогрев зеркал и заднего стекла";
            case CYCLE:return "Рециркуляция";
            default:return "Климат";
        }
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private int accent(){return getContext().getColor(R.color.accent);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(getContext());v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(16),dp(12),dp(16),dp(16));v.setBackground(shape(getContext().getColor(R.color.surface_light),0));return v;}
    private LayoutParams weighted(float weight,int margin){LayoutParams p=new LayoutParams(0,-2,weight);p.leftMargin=dp(margin);return p;}
    private void add(LinearLayout parent,View v,int top){LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(v,p);}
    private TextView text(String value,int size,boolean muted){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(getContext().getColor(muted?R.color.text_secondary:R.color.text_primary));v.setTypeface(getResources().getFont(R.font.ui7_regular));return v;}
    private Button button(String title,Runnable action){Button b=new Button(getContext());b.setAllCaps(false);b.setText(title);b.setTextSize(15);b.setTypeface(getResources().getFont(R.font.ui7_regular));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(50));b.setMinimumHeight(dp(50));b.setPadding(dp(6),dp(6),dp(6),dp(6));b.setOnClickListener(v->action.run());style(b,false,true);return b;}
    private GradientDrawable shape(int color,int stroke){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(12));if(stroke!=0)bg.setStroke(dp(1),stroke);return bg;}
    private void style(Button b,boolean selected,boolean enabled){b.setSelected(selected);b.setEnabled(enabled);b.setAlpha(enabled?1:.35f);b.setTextColor(selected?accent():getContext().getColor(R.color.text_primary));b.setBackground(shape(getContext().getColor(R.color.button),selected?accent():0));}
}
