package ru.logunov.bydsplit;

import android.content.Context;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Connection profiles are opt-in; direct mode also works with a VPN configured on the HU. */
final class TelegramRouteView extends LinearLayout {
    private final TelegramBotController controller;
    private final LinearLayout form,relay,proxy;
    private final Spinner mode;
    private final EditText url,key,host,port,user,password;
    private final TextView status,summary;
    private final Button save,toggle;
    private TelegramRoute saved=TelegramRoute.direct();
    private final Runnable changed=this::refresh;
    TelegramRouteView(Context context,TelegramBotController controller){
        super(context);this.controller=controller;setOrientation(VERTICAL);
        summary=text("");addView(summary);
        form=new LinearLayout(context);form.setOrientation(VERTICAL);form.setVisibility(GONE);
        toggle=button("Настроить соединение",()->form.setVisibility(form.getVisibility()==VISIBLE?GONE:VISIBLE));addView(toggle);addView(form);
        mode=new Spinner(context);mode.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,new String[]{"Напрямую / VPN на ГУ","HTTPS-шлюз Split","HTTP-прокси","SOCKS5-прокси"}));form.addView(mode,new LayoutParams(-1,dp(50)));
        relay=new LinearLayout(context);relay.setOrientation(VERTICAL);form.addView(relay);
        url=field("https://адрес-вашего-шлюза",false,false);relay.addView(url);key=field("Ключ шлюза · пустое поле сохраняет прежний ключ",true,false);relay.addView(key);
        relay.addView(text("Совместимый шлюз Split на вашем сервере. Через него проходят токен бота и отчёт. Можно развернуть собственный экземпляр; ключ доступа выдаёт его администратор."));
        proxy=new LinearLayout(context);proxy.setOrientation(VERTICAL);form.addView(proxy);
        host=field("Адрес прокси без http://",false,false);proxy.addView(host);port=field("Порт",false,true);proxy.addView(port);
        user=field("Логин · необязательно",false,false);proxy.addView(user);password=field("Пароль · пустое поле сохраняет прежний",true,false);proxy.addView(password);
        proxy.addView(text("Обычный HTTP CONNECT или SOCKS5, не MTProto-прокси из Telegram. Настройка применяется только к Telegram в Split. Поля логина и пароля оставьте пустыми для прокси без авторизации."));
        form.addView(text("В прямом режиме используются сеть и VPN головного устройства. VPN на телефоне может не распространяться на раздаваемую точку доступа."));
        save=button("Сохранить соединение",this::save);form.addView(save);status=text("");form.addView(status);
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int p,long id){showFields();}public void onNothingSelected(AdapterView<?> a){}});
        try{saved=controller.routeStore.get();}catch(TelegramBotApi.Failure e){status.setText(e.userText());}
        mode.setSelection(saved.mode.ordinal());url.setText(saved.url);host.setText(saved.host);port.setText(saved.port==0?"":Integer.toString(saved.port));user.setText(saved.username);showFields();refresh();
    }
    private void showFields(){int selected=mode.getSelectedItemPosition();relay.setVisibility(selected==1?VISIBLE:GONE);proxy.setVisibility(selected>=2?VISIBLE:GONE);}
    private void save(){
        if(controller.busy)return;
        try{
            TelegramRoute.Mode selected=TelegramRoute.Mode.values()[mode.getSelectedItemPosition()];TelegramRoute value;
            if(selected==TelegramRoute.Mode.DIRECT)value=TelegramRoute.direct();
            else if(selected==TelegramRoute.Mode.RELAY){
                String secret=key.getText().toString().trim(),address=url.getText().toString().trim();
                if(secret.isEmpty()&&saved.mode==selected&&address.replaceAll("/+$","").equals(saved.url))secret=saved.relayKey;
                value=TelegramRoute.relay(address,secret);
            }else{
                String username=user.getText().toString(),secret=password.getText().toString();int number=Integer.parseInt(port.getText().toString().trim());
                TelegramRoute candidate=TelegramRoute.proxy(selected,host.getText().toString(),number,username,secret);
                if(secret.isEmpty()&&!username.isEmpty()&&candidate.sameDestination(saved))secret=saved.password;
                value=TelegramRoute.proxy(selected,host.getText().toString(),number,username,secret);
            }
            controller.routeStore.save(value);saved=value;key.setText("");password.setText("");controller.back();status.setText("Соединение сохранено. Теперь можно проверить токен или отправить отчёт.");form.setVisibility(GONE);refresh();
            Toast.makeText(getContext(),"Соединение Telegram сохранено",Toast.LENGTH_SHORT).show();
        }catch(IllegalArgumentException e){status.setText("Проверьте адрес, порт и ключ/пароль. Шлюз требует HTTPS. При смене адреса или логина введите секрет заново.");}
        catch(Exception e){status.setText("Не удалось сохранить соединение. Повторите настройку.");}
    }
    private void refresh(){summary.setText("Соединение Telegram: "+saved.label());save.setEnabled(!controller.busy);toggle.setEnabled(!controller.busy);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();controller.addListener(changed);}
    @Override protected void onDetachedFromWindow(){controller.removeListener(changed);super.onDetachedFromWindow();}
    private TextView text(String value){TextView v=new TextView(getContext());v.setText(value);v.setTextColor(ThemePalette.text(getContext()));v.setTextSize(14);v.setPadding(0,dp(8),0,dp(8));return v;}
    private EditText field(String hint,boolean secret,boolean numeric){EditText v=new EditText(getContext());v.setHint(hint);v.setSingleLine(true);v.setSaveEnabled(false);v.setTextColor(ThemePalette.text(getContext()));v.setInputType(numeric?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));v.setTypeface(android.graphics.Typeface.DEFAULT);v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);v.setLayoutParams(new LayoutParams(-1,dp(54)));return v;}
    private Button button(String name,Runnable action){Button v=new Button(getContext());v.setText(name);v.setAllCaps(false);v.setTextColor(ThemePalette.color(getContext(),0xffc9e4ff,0xff164775));android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(ThemePalette.color(getContext(),0xff2b3745,0xffdce6f1));shape.setCornerRadius(dp(10));v.setBackground(shape);v.setLayoutParams(new LayoutParams(-1,dp(50)));v.setOnClickListener(w->action.run());return v;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
