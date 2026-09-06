package ru.logunov.bydsplit;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import org.json.*;
import java.util.*;

/** Persistent rule editor and isolated emulator actuator. */
final class TemperatureAutomationView extends LinearLayout {
    private static final String[] ACTIONS = {"Подогрев руля", "Подогрев · водитель", "Подогрев · пассажир", "Вентиляция · водитель", "Вентиляция · пассажир", "Открыть камеру парковки"};
    private final List<TemperatureRules.Rule> rules = new ArrayList<>();
    private final TemperatureRules engine = new TemperatureRules();
    private final int[] state = new int[6];
    private final LinearLayout body;
    private final Context owner;
    private TextView result;
    private int temperature = 10, cabinTemperature = 18;
    private long demoTimeOffset;
    private boolean testing;
    private AlertDialog testDialog;
    private final Runnable timerTick = new Runnable(){public void run(){if(testing)evaluate();postDelayed(this,1000);}};
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();postDelayed(timerTick,1000);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(timerTick);if(testDialog!=null)testDialog.dismiss();super.onDetachedFromWindow();}
    private final Map<String,Boolean> demoPlaces = new HashMap<>();
    TemperatureAutomationView(Context c) {
        super(new android.view.ContextThemeWrapper(c, android.R.style.Theme_Material_NoActionBar)); setOrientation(VERTICAL);
        owner = c;
        body = column(); addView(body,new LinearLayout.LayoutParams(-1,-1)); load(); render();
    }
    private LinearLayout column() { LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);v.setPadding(18,12,18,12);return v; }
    private TextView text(String s,int size) { TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(0xFFDAE1EB);t.setPadding(0,8,0,8);t.setTypeface(getResources().getFont(R.font.ui7_regular));return t; }
    private Button button(String s,Runnable r) { Button b=new Button(getContext());b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFF262D33);bg.setCornerRadius(18);b.setBackground(bg);b.setPadding(18,12,18,12);b.setOnClickListener(v->r.run());return b; }
    private ImageButton iconButton(String label,int resource,Runnable action){
        ImageButton b=new ImageButton(getContext());b.setImageResource(resource);b.setContentDescription(label);b.setTooltipText(label);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFF262D33);bg.setCornerRadius(18);b.setBackground(bg);
        b.setOnClickListener(v->action.run());return b;
    }
    private void render() {
        body.removeAllViews();body.addView(text("Автоматизации",26));
        body.addView(text("Правила",22));
        body.addView(text("Настройте условия и действия для каждого правила.",14));
        body.addView(button("＋ Добавить правило",()->edit(null)));
        ScrollView ruleScroll=new ScrollView(getContext());ruleScroll.setFillViewport(true);ruleScroll.setClipToPadding(true);
        ruleScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout ruleList=new LinearLayout(getContext());ruleList.setOrientation(VERTICAL);
        ruleScroll.addView(ruleList,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(-1,0,1f);listParams.topMargin=12;body.addView(ruleScroll,listParams);
        for (TemperatureRules.Rule r : new ArrayList<>(rules)) {
            LinearLayout card=column();
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFF1D252E);bg.setCornerRadius(20);card.setBackground(bg);
            LinearLayout header=new LinearLayout(getContext());header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            boolean hasPlace=false,hasTemperature=r.conditions.isEmpty();for(TemperatureRules.Condition condition:r.conditions){hasPlace|=condition.placeKey!=null;hasTemperature|=condition.placeKey==null;}
            for(int kind=0;kind<2;kind++)if(kind==0?hasTemperature:hasPlace){ImageView icon=new ImageView(getContext());icon.setImageResource(kind==0?R.drawable.rule_temperature:R.drawable.rule_place);icon.setContentDescription(kind==0?"Температура":"Геозона");header.addView(icon,new LinearLayout.LayoutParams(48,48));}
            TextView title=text(r.name,20);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            ImageButton edit=iconButton("Изменить",R.drawable.rule_edit,()->edit(r));
            LinearLayout.LayoutParams editParams=new LinearLayout.LayoutParams(160,64);editParams.leftMargin=12;header.addView(edit,editParams);
            ImageButton delete=iconButton("Удалить",R.drawable.rule_delete,()->new AlertDialog.Builder(getContext()).setMessage("Удалить правило «"+r.name+"»?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{rules.remove(r);save();engine.reset();render();}).show());
            LinearLayout.LayoutParams deleteParams=new LinearLayout.LayoutParams(145,64);deleteParams.leftMargin=12;header.addView(delete,deleteParams);
            Switch toggle=new Switch(getContext());toggle.setContentDescription("Активность правила "+r.name);toggle.setText(r.enabled?"Включено":"Выключено");toggle.setTextColor(Color.WHITE);toggle.setChecked(r.enabled);
            toggle.setOnCheckedChangeListener((v,on)->{r.enabled=on;toggle.setText(on?"Включено":"Выключено");save();engine.reset();});
            LinearLayout.LayoutParams toggleParams=new LinearLayout.LayoutParams(230,64);toggleParams.leftMargin=20;header.addView(toggle,toggleParams);card.addView(header);
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.topMargin=12;ruleList.addView(card,cardParams);
        }
        LinearLayout.LayoutParams testParams=new LinearLayout.LayoutParams(-1,-2);
        testParams.topMargin=Math.round(12*getResources().getDisplayMetrics().density);
        body.addView(button("Проверить правила",this::showRuleTests),testParams);
    }
    private void showRuleTests(){
        LinearLayout body=column();ScrollView scroll=new ScrollView(getContext());scroll.addView(body);
        testing=false;engine.reset();Arrays.fill(state,0);demoTimeOffset=0;
        body.addView(button("Журнал автомобиля",()->new AlertDialog.Builder(getContext()).setTitle("Срабатывания · текущий запуск").setMessage(AutomationJournal.text()).setPositiveButton("Закрыть",null).show()));
        body.addView(text("Симуляция · команды автомобилю не отправляются",16));
        for(CameraPlace place:AppPreferences.getCameraPlaces(getContext())){
            Switch inside=new Switch(getContext());inside.setText("Внутри места · "+place.name);inside.setTextColor(Color.WHITE);
            String key=AutomationStore.key(place);demoPlaces.putIfAbsent(key,false);inside.setChecked(Boolean.TRUE.equals(demoPlaces.get(key)));
            inside.setOnCheckedChangeListener((v,on)->{demoPlaces.put(key,on);evaluate();});body.addView(inside);
        }
        TextView value=text("Снаружи: "+temperature+" °C",20);body.addView(value);
        SeekBar slider=new SeekBar(getContext());slider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF2682DF));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF2682DF));slider.setMax(100);slider.setProgress(temperature+40);body.addView(slider);
        result=text("",15);body.addView(result);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} public void onProgressChanged(SeekBar s,int p,boolean user){temperature=p-40;value.setText("Снаружи: "+temperature+" °C");if(user)evaluate();}});
        TextView cabinValue=text("В салоне: "+cabinTemperature+" °C",20);body.addView(cabinValue);
        SeekBar cabinSlider=new SeekBar(getContext());cabinSlider.setMax(100);cabinSlider.setProgress(cabinTemperature+40);body.addView(cabinSlider);
        cabinSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean u){cabinTemperature=p-40;cabinValue.setText("В салоне: "+cabinTemperature+" °C");if(u)evaluate();}});
        body.addView(button("＋ 1 минута в проверке",()->{demoTimeOffset+=60000;evaluate();}));
        body.addView(button("Имитировать запуск программы",()->{engine.reset();Arrays.fill(state,0);evaluate();}));
        updateResult(Collections.emptyList());
        testDialog=new AlertDialog.Builder(getContext()).setTitle("Проверка правил").setView(scroll).setPositiveButton("Закрыть",null).create();
        testDialog.setOnDismissListener(d->{testing=false;engine.reset();result=null;testDialog=null;});
        testDialog.show();testDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF15181F));
    }
    private static String levelLabel(int level){return level==0?"Выключить":"Уровень "+level;}
    private void evaluate(){testing=true;List<String> fired=engine.evaluate(rules,(float)temperature,(float)cabinTemperature,demoPlaces,android.os.SystemClock.elapsedRealtime()+demoTimeOffset,(a,l)->{state[a]=a==5?1:l;if(l>0&&a>0&&a<5){int other=a<=2?a+2:a-2;state[other]=0;}});updateResult(fired);}
    private void updateResult(List<String> fired){if(result==null)return;StringBuilder s=new StringBuilder(fired.isEmpty()?"Демо · состояние функций":"Сработало: "+String.join(", ",fired));for(int a=0;a<6;a++)s.append("\n").append(ACTIONS[a]).append(": ").append(state[a]==0?"выключено":state[a]);result.setText(s);}
    private Spinner spinner(String[] labels,int selected){Spinner s=new Spinner(getContext());s.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,labels));s.setSelection(selected);return s;}
    private void edit(TemperatureRules.Rule existing) {
        LinearLayout form=column(); ScrollView scroll=new ScrollView(getContext());scroll.addView(form);
        EditText name=new EditText(getContext());name.setSingleLine(true);name.setHint("Название правила");
        name.setText(existing==null?"":existing.name);form.addView(name);
        Spinner execution=spinner(new String[]{"При выполнении условий","Один раз при запуске программы"},existing==null||existing.startupOnly?1:0);form.addView(execution);
        form.addView(text("ЕСЛИ · условия",20));
        Spinner logic=spinner(new String[]{"Выполнены все условия (И)","Выполнено любое условие (ИЛИ)"},existing==null||existing.all?0:1);form.addView(logic);
        LinearLayout conditions=column();form.addView(conditions);
        List<Spinner> comparisons=new ArrayList<>();List<EditText> thresholds=new ArrayList<>();List<String> placeKeys=new ArrayList<>();List<Boolean> cabinFlags=new ArrayList<>();
        java.util.function.Consumer<TemperatureRules.Condition> addCondition=c->{
            LinearLayout row=column();row.setBackgroundColor(0xFF1D252E);
            row.addView(text(c.placeKey==null?(c.cabin?"Температура в салоне":"Наружная температура"):"Внутри места · "+c.placeKey.split("\\|")[0],17));
            Spinner compare=spinner(new String[]{"Не выше (≤)","Не ниже (≥)"},c.below?0:1);
            EditText threshold=new EditText(getContext());threshold.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);threshold.setText(String.valueOf(c.threshold));
            if(c.placeKey==null){row.addView(compare);row.addView(threshold);}comparisons.add(compare);thresholds.add(threshold);placeKeys.add(c.placeKey);cabinFlags.add(c.cabin);
            row.addView(button("Удалить условие",()->{int i=thresholds.indexOf(threshold);thresholds.remove(i);comparisons.remove(i);placeKeys.remove(i);cabinFlags.remove(i);conditions.removeView(row);}));conditions.addView(row);
        };
        form.addView(button("＋ Добавить условие",()->new AlertDialog.Builder(getContext()).setTitle("Выберите триггер").setItems(new String[]{"Наружная температура","Температура в салоне","Въезд в геозону"},(d,w)->{
            if(w<2){TemperatureRules.Condition cond=new TemperatureRules.Condition(true,10);cond.cabin=w==1;addCondition.accept(cond);}
            else {List<CameraPlace> places=AppPreferences.getCameraPlaces(getContext());if(places.isEmpty()){Toast.makeText(getContext(),"Сначала добавьте зону в разделе «Места»",Toast.LENGTH_SHORT).show();return;}
                String[] names=new String[places.size()];for(int i=0;i<names.length;i++)names[i]=places.get(i).name;
                new AlertDialog.Builder(getContext()).setTitle("Выберите место").setItems(names,(dd,i)->{TemperatureRules.Condition cond=new TemperatureRules.Condition(true,0);cond.placeKey=AutomationStore.key(places.get(i));execution.setSelection(0);addCondition.accept(cond);}).show();}
        }).show()));
        form.addView(text("ТОГДА · действия",20));LinearLayout actions=column();form.addView(actions);
        List<Integer> actionIds=new ArrayList<>();List<Spinner> levels=new ArrayList<>();List<EditText> durations=new ArrayList<>();
        java.util.function.BiConsumer<Integer,Integer> addAction=(id,level)->{
            if(actionIds.contains(id)){Toast.makeText(getContext(),"Это действие уже добавлено",Toast.LENGTH_SHORT).show();return;}
            LinearLayout row=column();row.setBackgroundColor(0xFF1D252E);TextView label=text(ACTIONS[id],17);
            android.graphics.drawable.Drawable icon=getContext().getDrawable(id==5?R.drawable.ui7_camera:id==0?R.drawable.ui7_steering_heat:R.drawable.ui7_climate);icon.setTint(Color.WHITE);icon.setBounds(0,0,38,38);label.setCompoundDrawables(icon,null,null,null);label.setCompoundDrawablePadding(12);row.addView(label);
            Spinner value=spinner(id==5?new String[]{"Открыть"}:id==0?new String[]{"Выключить","Включить"}:new String[]{"Выключить","Уровень 1","Уровень 2","Уровень 3"},id==5?0:level);row.addView(value);actionIds.add(id);levels.add(value);
            EditText minutes=new EditText(getContext());minutes.setInputType(InputType.TYPE_CLASS_NUMBER);minutes.setHint("Минуты, 0 — без таймера");minutes.setText(String.valueOf(existing!=null?existing.durationMinutes[id]:id<=2?10:0));durations.add(minutes);
            if(id<5){row.addView(text("Выключить через, мин · 0 — без таймера",14));row.addView(minutes);}
            row.addView(button("Удалить действие",()->{int i=levels.indexOf(value);levels.remove(i);durations.remove(i);actionIds.remove(i);actions.removeView(row);}));actions.addView(row);
        };
        form.addView(button("＋ Добавить действие",()->new AlertDialog.Builder(getContext()).setTitle("Выберите действие").setItems(ACTIONS,(d,id)->addAction.accept(id,1)).show()));
        if(existing!=null){
            if(existing.conditions.isEmpty())addCondition.accept(new TemperatureRules.Condition(existing.below,existing.threshold));
            else for(TemperatureRules.Condition c:existing.conditions)addCondition.accept(c);
            for(int i=0;i<6;i++)if(existing.levels[i]>=0)addAction.accept(i,existing.levels[i]);
        }
        AlertDialog dialog=new AlertDialog.Builder(getContext()).setTitle(existing==null?"Новое правило":"Изменить правило").setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("Введите название");return;}
            if(thresholds.isEmpty()||levels.isEmpty()){Toast.makeText(getContext(),"Добавьте условие и действие",Toast.LENGTH_SHORT).show();return;}
            List<TemperatureRules.Condition> parsed=new ArrayList<>();
            for(int i=0;i<thresholds.size();i++){EditText field=thresholds.get(i);if(placeKeys.get(i)!=null){TemperatureRules.Condition cond=new TemperatureRules.Condition(true,0);cond.placeKey=placeKeys.get(i);parsed.add(cond);continue;}float t;try{t=Float.parseFloat(field.getText().toString().replace(',','.'));}catch(Exception e){field.setError("Введите температуру");return;}if(!Float.isFinite(t)||t < -60||t>60){field.setError("От −60 до +60 °C");return;}TemperatureRules.Condition cond=new TemperatureRules.Condition(comparisons.get(i).getSelectedItemPosition()==0,t);cond.cabin=cabinFlags.get(i);parsed.add(cond);}
            int[] selected={-1,-1,-1,-1,-1,-1};for(int i=0;i<levels.size();i++)selected[actionIds.get(i)]=levels.get(i).getSelectedItemPosition();
            if((selected[1]>0&&selected[3]>0)||(selected[2]>0&&selected[4]>0)){Toast.makeText(getContext(),"Подогрев и вентиляция одного сиденья несовместимы",Toast.LENGTH_SHORT).show();return;}
            int[] timeouts=new int[6];for(int i=0;i<durations.size();i++){if(actionIds.get(i)==5)continue;try{int m=Integer.parseInt(durations.get(i).getText().toString());if(m<0||m>120)throw new NumberFormatException();timeouts[actionIds.get(i)]=m;}catch(NumberFormatException e){durations.get(i).setError("От 0 до 120 минут");return;}}
            TemperatureRules.Rule r=existing==null?new TemperatureRules.Rule():existing;r.name=title;r.conditions=parsed;r.all=logic.getSelectedItemPosition()==0;r.levels=selected;r.startupOnly=execution.getSelectedItemPosition()==1;r.durationMinutes=timeouts;
            if(existing==null){r.id=UUID.randomUUID().toString();rules.add(r);}save();engine.reset();render();dialog.dismiss();
        }));dialog.show();dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF15181F));dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void save(){AutomationStore.save(getContext(),rules);}
    private void load(){rules.addAll(AutomationStore.load(getContext()));}
}
