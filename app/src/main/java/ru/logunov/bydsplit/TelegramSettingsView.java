package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Own-bot setup, following BYDMate's token + one-time-code flow. */
final class TelegramSettingsView extends ScrollView {
    private final TelegramBotController controller;
    private final LinearLayout content;
    private final EditText token;
    private final TextView state,help;
    private final Button begin,check,confirm,back,disconnect,copy;
    private final Switch automatic;
    private final Runnable changed=this::refresh;
    TelegramSettingsView(Context context){
        super(context);controller=TelegramBotController.get(context);setFillViewport(true);
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(12),dp(18),dp(18));addView(content,new ScrollView.LayoutParams(-1,-2));
        content.addView(text("Telegram · отчёты диагностики",22));
        content.addView(new TelegramRouteView(context,controller));
        content.addView(text("Создайте своего бота через @BotFather → /newbot. Введите токен здесь. Код привяжет отчёты к вашему личному чату или закрытой группе.",15));
        state=text("",15);content.addView(state);
        token=new EditText(context);token.setHint("Токен своего бота");token.setSingleLine(true);token.setSaveEnabled(false);token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);token.setTextColor(ThemePalette.text(context));content.addView(token,new LinearLayout.LayoutParams(-1,dp(54)));
        begin=button("Проверить токен",()->{String value=token.getText().toString().trim();controller.begin(this,value);token.setText("");});
        help=text("",15);content.addView(help);
        copy=button("Скопировать код",()->{ClipboardManager clipboard=context.getSystemService(ClipboardManager.class);clipboard.setPrimaryClip(ClipData.newPlainText("Код подключения BYD Split",controller.code));Toast.makeText(context,"Код скопирован",Toast.LENGTH_SHORT).show();});
        check=button("Проверить код",()->controller.check(this));
        confirm=button("Подключить этот чат",()->controller.confirm(this));
        back=button("Назад к токену",controller::back);
        disconnect=button("Отключить бота",controller::disconnect);
        automatic=new Switch(context);automatic.setText("Отправлять отчёт после ручной проверки ошибок");automatic.setTextColor(ThemePalette.text(context));automatic.setChecked(controller.store.automatic());automatic.setOnCheckedChangeListener((b,enabled)->controller.store.automatic(enabled));content.addView(automatic);
        content.addView(text("Автоматические сканирования не запускаются. Отправляется только готовый отчёт: блоки, коды, статусы и доступные расшифровки. Без GPS, поездок, токенов и сырых журналов. Длинный отчёт приходит TXT-файлом. При отсутствии сети отчёт остаётся в приложении; повторная отправка — вручную.",13));
        refresh();
    }
    private void refresh(){
        state.setText(controller.store.destination()+"\n"+controller.status);
        boolean input=controller.stage.equals("token"),code=controller.stage.equals("code"),chat=controller.stage.equals("confirm");
        token.setVisibility(input?VISIBLE:GONE);begin.setVisibility(input?VISIBLE:GONE);check.setVisibility(code?VISIBLE:GONE);copy.setVisibility(code?VISIBLE:GONE);
        confirm.setVisibility(chat?VISIBLE:GONE);back.setVisibility(code||chat?VISIBLE:GONE);disconnect.setVisibility(controller.store.connected()?VISIBLE:GONE);
        help.setText(code?"Бот: @"+controller.bot+"\nКод: "+controller.code+"\nОтправьте код личным сообщением боту. Для закрытой группы: /start@"+controller.bot+" "+controller.code+"\nКод действует 10 минут.":chat?"Бот: @"+controller.bot+"\nЧат: "+controller.chatName:"");
        help.setVisibility(code||chat?VISIBLE:GONE);
        for(Button b:new Button[]{begin,check,confirm,back,disconnect,copy})b.setEnabled(!controller.busy);
        automatic.setChecked(controller.store.automatic());automatic.setEnabled(controller.store.connected()&&!controller.busy);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();controller.addListener(changed);}
    @Override protected void onDetachedFromWindow(){controller.cancel(this);controller.removeListener(changed);super.onDetachedFromWindow();}
    private TextView text(String value,int size){TextView view=new TextView(getContext());view.setText(value);view.setTextSize(size);view.setTextColor(ThemePalette.text(getContext()));view.setPadding(0,dp(8),0,dp(8));return view;}
    private Button button(String name,Runnable click){Button button=new Button(getContext());button.setText(name);button.setAllCaps(false);android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(ThemePalette.color(getContext(),0xff2b3745,0xffdce6f1));shape.setCornerRadius(dp(10));button.setBackground(shape);button.setTextColor(ThemePalette.color(getContext(),0xffc9e4ff,0xff164775));button.setOnClickListener(v->click.run());content.addView(button,new LinearLayout.LayoutParams(-1,dp(52)));return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
