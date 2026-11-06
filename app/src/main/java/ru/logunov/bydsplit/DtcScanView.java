package ru.logunov.bydsplit;

import android.content.*;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Manual diagnostics and explicit erase, with optional delivery of completed reports. */
final class DtcScanView extends LinearLayout {
    private final DtcScanController controller;
    private final TextView status;
    private final Button scan,cancel,copy,eraseAll,send,configure;
    private final TextView telegramStatus;
    private final TelegramBotController telegram;
    private final Runnable telegramChanged=this::update;
    private Dialog popup;
    private final ProgressBar progress;
    private final LinearLayout results;
    private boolean listening,expanded;
    private final Runnable changed=this::update;
    DtcScanView(Context context){
        super(context);controller=DtcScanController.get(context);telegram=TelegramBotController.get(context);setOrientation(VERTICAL);setPadding(dp(14),dp(12),dp(14),dp(12));
        GradientDrawable background=new GradientDrawable();background.setColor(ThemePalette.color(context,0xff222b35,0xffe8eef5));background.setCornerRadius(dp(14));setBackground(background);
        TextView title=text("Диагностика · коды ошибок",19);title.setTypeface(null,Typeface.BOLD);addView(title);
        TextView note=text("Проверка читает ошибки. Стирание — отдельное действие с подтверждением, только в P при включённой машине и скорости 0.",13);addView(note);
        LinearLayout buttons=new LinearLayout(context);buttons.setGravity(Gravity.CENTER_VERTICAL);
        scan=new Button(context);scan.setText(AppPreferences.isDemoModeEnabled(context)?"Показать пример":"Сканировать ошибки");scan.setAllCaps(false);scan.setOnClickListener(v->{expanded=true;controller.start();});buttons.addView(scan,new LayoutParams(0,dp(48),1));
        cancel=new Button(context);cancel.setText("Остановить");cancel.setAllCaps(false);cancel.setOnClickListener(v->controller.cancel());buttons.addView(cancel,new LayoutParams(dp(130),dp(48)));
        copy=new Button(context);copy.setText("Копировать отчёт");copy.setAllCaps(false);copy.setOnClickListener(v->{ClipboardManager clipboard=context.getSystemService(ClipboardManager.class);if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Диагностика BYD Split",reportText()));});buttons.addView(copy,new LayoutParams(dp(180),dp(48)));addView(buttons);
        style(scan,true);style(copy,false);style(cancel,false);
        LinearLayout actions=new LinearLayout(context);
        eraseAll=new Button(context);eraseAll.setText("Стереть все…");eraseAll.setAllCaps(false);eraseAll.setOnClickListener(v->confirmErase(controller.eligible()));actions.addView(eraseAll,new LayoutParams(0,dp(48),1));
        send=new Button(context);send.setText("Отправить в Telegram");send.setAllCaps(false);send.setOnClickListener(v->{if(telegram.store.connected())telegram.send(this,controller.displayedReport());else popup=IntegrationsSettingsView.show(context);});actions.addView(send,new LayoutParams(0,dp(48),1));
        configure=new Button(context);configure.setText("Интеграции");configure.setAllCaps(false);configure.setOnClickListener(v->popup=IntegrationsSettingsView.show(context));actions.addView(configure,new LayoutParams(0,dp(48),1));addView(actions);
        style(eraseAll,false);style(send,false);style(configure,false);
        telegramStatus=text("",13);addView(telegramStatus);
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(18);addView(progress,new LayoutParams(-1,dp(5)));
        status=text("Отчётов пока нет. Проверка запускается только кнопкой.",13);addView(status);
        TextView toggle=text("Последний отчёт  ▾",15);toggle.setPadding(0,dp(8),0,dp(8));toggle.setOnClickListener(v->{expanded=!expanded;update();});addView(toggle);
        results=new LinearLayout(context);results.setOrientation(VERTICAL);addView(results);
    }
    void setActive(boolean active){if(active==listening)return;listening=active;if(active)controller.addListener(changed);else controller.removeListener(changed);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();setActive(true);telegram.addListener(telegramChanged);}
    @Override protected void onDetachedFromWindow(){setActive(false);telegram.cancel(this);telegram.removeListener(telegramChanged);if(popup!=null)popup.dismiss();popup=null;super.onDetachedFromWindow();}
    private void update(){
        scan.setEnabled(!controller.running);cancel.setVisibility(controller.running?VISIBLE:GONE);progress.setVisibility(controller.running?VISIBLE:GONE);progress.setMax(controller.total);progress.setProgress(controller.done);
        JSONObject report=controller.displayedReport();copy.setEnabled(report!=null);status.setText(controller.message.isEmpty()?"Запуск вручную · до 90 секунд · только в P":controller.message);
        eraseAll.setEnabled(!controller.running&&controller.eligible().length>0);
        send.setEnabled(report!=null&&!controller.running&&!telegram.busy);configure.setEnabled(!controller.running);
        telegramStatus.setText(telegram.store.destination()+(telegram.status.isEmpty()?"":"\n"+telegram.status));
        scan.setAlpha(controller.running?.5f:1f);copy.setAlpha(report==null?.45f:1f);
        results.removeAllViews();results.setVisibility(expanded?VISIBLE:GONE);if(report==null)return;
        try {
            JSONArray blocks=report.getJSONArray("results");int answered=0,now=0,old=0,pending=0,unread=0;
            for(int i=0;i<blocks.length();i++){
                JSONObject b=blocks.getJSONObject(i);String outcome=b.optString("outcome");
                if(Arrays.asList("ok","nrc","truncated","multiframe").contains(outcome))answered++;
                byte[] list=DtcProtocol.unhex(b.optString("list"));
                if(DtcProtocol.count(DtcProtocol.unhex(b.optString("count")))>0&&(list.length==0||!"ok".equals(outcome)||list.length<=3||(list.length-3)%4!=0))unread++;
                for(DtcProtocol.Row row:DtcProtocol.rows(list)){if(row.kind.equals("NOW"))now++;else if(row.kind.equals("OLD"))old++;else pending++;}
            }
            String at=new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(report.optLong("at")));
            results.addView(text((report.optBoolean("demo")?"ДЕМОНСТРАЦИЯ · ":"")+at+"\nОтветили: "+answered+" из "+blocks.length()+"  ·  Текущие: "+now+"  ·  Ожидающие: "+pending+"  ·  Сохранённые: "+old,14));
            if(now+old+pending==0)results.addView(text("В полученных списках отображаемых кодов не найдено. Это не проверка исправности всех систем.",14));
            if(unread>0)results.addView(text("У "+unread+" блоков есть коды, но список прочитать полностью не удалось.",14));
            JSONObject erase=report.optJSONObject("lastErase");
            if(erase!=null){
                if(!report.optBoolean("demo")){
                    String archived=getContext().getSharedPreferences("dtc",0).getString("before_clear_text","");
                    if(!archived.isEmpty()){Button archive=new Button(getContext());archive.setText("Копировать отчёт до стирания");archive.setAllCaps(false);style(archive,false);archive.setOnClickListener(v->{ClipboardManager cb=getContext().getSystemService(ClipboardManager.class);if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("До стирания · BYD Split",archived));});results.addView(archive,new LayoutParams(-1,dp(44)));}
                }
                results.addView(text("Результат стирания · "+erase.optString("state"),15));JSONArray items=erase.optJSONArray("erase");if(items!=null)for(int i=0;i<items.length();i++){JSONObject r=items.getJSONObject(i);results.addView(text(String.format(Locale.ROOT,"0x%03X · ",r.optInt("address"))+r.optString("verdict","Результат неизвестен"),14));}}
            for(int i=0;i<blocks.length();i++)addBlock(blocks.getJSONObject(i));
        }catch(Exception error){results.addView(text("Отчёт не удалось отобразить.",14));}
    }
    private void addBlock(JSONObject block)throws Exception {
        int address=block.getInt("address");String cls=DtcProtocol.blockClass(address,DtcProtocol.unhex(block.optString("identity")));
        DtcEcuNames.Name resolved=DtcReport.blockName(block);
        String name=resolved.label+String.format(Locale.ROOT," · 0x%03X",address),outcome=block.optString("outcome");
        int count=DtcProtocol.count(DtcProtocol.unhex(block.optString("count")));
        String state=outcome.equals("ok")?(count==0?"кодов нет":count>0?"кодов по ответу: "+count:"неполные данные")
                :outcome.equals("skipped")?"не проверен":outcome.equals("no_answer")||outcome.equals("gw_timeout")?"нет ответа":outcome.equals("nrc")?"чтение отклонено блоком":"неполный ответ";
        TextView header=text(name+" — "+state,15);header.setPadding(0,dp(12),0,dp(4));header.setTypeface(null,Typeface.BOLD);results.addView(header);
        results.addView(text(resolved.source,12));
        if(block.optBoolean("staleAfterErase"))results.addView(text("Показания до стирания: повторное чтение не подтверждено. Выполните новую проверку.",13));
        boolean eligible=false;for(int candidate:controller.eligible())if(candidate==address)eligible=true;
        if(eligible){Button erase=new Button(getContext());erase.setText("Стереть ошибки этого блока…");erase.setAllCaps(false);erase.setContentDescription(String.format(Locale.ROOT,"Стереть ошибки блока 0x%03X",address));erase.setEnabled(!controller.running);erase.setOnClickListener(v->confirmErase(new int[]{address}));style(erase,false);results.addView(erase,new LayoutParams(-1,dp(44)));}
        JSONObject descriptions=block.optJSONObject("descriptions");
        for(DtcProtocol.Row row:DtcProtocol.rows(DtcProtocol.unhex(block.optString("list")))){
            TextView code=text(row.code+" · "+(row.kind.equals("NOW")?"Есть сейчас":row.kind.equals("PENDING")?"Ожидающая":"Сохранённая"),17);
            code.setTextColor(row.kind.equals("NOW")?ThemePalette.color(getContext(),0xffff9d94,0xffae2929):row.kind.equals("PENDING")?ThemePalette.color(getContext(),0xffffc779,0xff8d5100):ThemePalette.color(getContext(),0xffa8bacf,0xff475c76));results.addView(code);
            String occurrence=DtcReport.occurrence(block,row.code);if(!occurrence.isEmpty())results.addView(text(occurrence,12));
            JSONObject description=descriptions==null?null:descriptions.optJSONObject(row.code);
            results.addView(text(description==null?"Расшифровка для этого блока не найдена.":description.optString("text")+"\n"+description.optString("source",description.optBoolean("exact")?"Справочник модели BYD · EN":"Справочник похожих моделей BYD · EN"),13));
        }
    }
    private static String label(String cls){return DtcReport.label(cls);}
    private void confirmErase(int[] targets){
        if(targets.length==0)return;
        AlertDialog.Builder dialog=new AlertDialog.Builder(getContext(),ThemePalette.dark(getContext())?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(targets.length==1?String.format(Locale.ROOT,"Стереть ошибки блока 0x%03X?",targets[0]):"Стереть коды в "+targets.length+" блоках?")
            .setMessage("Экспериментальная функция. Записи ошибок в выбранных блоках будут удалены; активная неисправность может вернуть код сразу. Перед стиранием сохранится исходный отчёт. После команды блоки будут перечитаны. Если планируете сервис, сначала отправьте отчёт себе. На вашей Song L стирание ещё требует проверки.")
            .setNegativeButton("Отмена",null).setPositiveButton("Стереть и перепроверить",(d,w)->{expanded=true;controller.erase(targets);});
        if(telegram.store.connected())dialog.setNeutralButton("Сначала отправить отчёт",(d,w)->telegram.send(this,controller.displayedReport()));
        popup=dialog.show();
        GradientDrawable shape=new GradientDrawable();shape.setColor(ThemePalette.color(getContext(),0xff222b35,0xffe8eef5));shape.setCornerRadius(dp(18));
        popup.getWindow().setBackgroundDrawable(shape);
        popup.getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|android.view.View.SYSTEM_UI_FLAG_FULLSCREEN|android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        popup.getWindow().setLayout(Math.min(dp(760),getResources().getDisplayMetrics().widthPixels-dp(32)),android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private String reportText(){return DtcReport.text(controller.displayedReport());}
    private TextView text(String value,int size){TextView text=new TextView(getContext());text.setText(value);text.setTextSize(size);text.setTextColor(ThemePalette.color(getContext(),0xffe4edf7,0xff1d2c3e));return text;}
    private void style(Button button,boolean primary){GradientDrawable shape=new GradientDrawable();shape.setColor(primary?ThemePalette.color(getContext(),0xff1d4f74,0xffd3e8ff):ThemePalette.color(getContext(),0xff2b3745,0xffdce6f1));shape.setCornerRadius(dp(10));button.setBackground(shape);button.setTextColor(ThemePalette.color(getContext(),0xffc9e4ff,0xff164775));button.setTextSize(14);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
