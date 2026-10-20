package ru.logunov.bydsplit;

import android.app.Activity;
import android.widget.*;

final class StatisticsSettingsView extends ScrollView {
    StatisticsSettingsView(Activity activity) {
        super(activity);
        LinearLayout root=StatisticsUi.column(activity);root.setPadding(12,8,12,24);addView(root);
        StatisticsUi.add(root,StatisticsUi.text(activity,"Поездки и тарифы",23));
        Switch enabled=new Switch(activity);enabled.setText("Записывать поездки и зарядки");enabled.setTextColor(activity.getColor(R.color.text_primary));
        enabled.setChecked(AppPreferences.get(activity).getBoolean(StatisticsService.ENABLED,true));
        enabled.setOnCheckedChangeListener((v,checked)->{
            AppPreferences.get(activity).edit().putBoolean(StatisticsService.ENABLED,checked).apply();StatisticsService.sync(activity);
        });StatisticsUi.add(root,enabled);
        StatisticsUi.add(root,StatisticsUi.text(activity,"Поездка начинается при движении и завершается после 5 минут остановки. Запись работает в фоне с уведомлением. В режиме эмулятора реальные поездки не записываются.",14));
        StatisticsUi.add(root,StatisticsUi.text(activity,"Цены для новых записей · ₽",18));
        EditText electricity=StatisticsUi.input(activity,"Электричество · ₽/кВт·ч",(double)AppPreferences.get(activity).getFloat("electricity_price",6));
        EditText fuel=StatisticsUi.input(activity,"Бензин · ₽/л",(double)AppPreferences.get(activity).getFloat("fuel_price",60));
        StatisticsUi.addInput(root,"Домашний / AC тариф · ₽/кВт·ч",electricity);
        EditText dc=StatisticsUi.input(activity,"DC тариф · ₽/кВт·ч",(double)AppPreferences.get(activity).getFloat("dc_electricity_price",AppPreferences.get(activity).getFloat("electricity_price",6)));
        StatisticsUi.addInput(root,"DC зарядка · ₽/кВт·ч",dc);StatisticsUi.addInput(root,"Бензин · ₽/л",fuel);
        EditText losses=StatisticsUi.input(activity,"Потери при зарядке · %",(double)AppPreferences.get(activity).getFloat("charge_loss_percent",0));
        StatisticsUi.addInput(root,"Потери при зарядке · % (0 — не учитывать)",losses);
        EditText capacity=StatisticsUi.input(activity,"Ёмкость батареи · кВт·ч",(double)activity.getSharedPreferences("tech",0).getFloat("capacity",0));
        StatisticsUi.addInput(root,"Ёмкость батареи · кВт·ч (0 — цена последней зарядки)",capacity);
        Switch history=new Switch(activity);history.setText("Тариф поездки по предыдущим зарядкам");history.setTextColor(activity.getColor(R.color.text_primary));
        history.setChecked(AppPreferences.get(activity).getBoolean("trip_price_from_charges",true));
        history.setOnCheckedChangeListener((button,checked)->AppPreferences.get(activity).edit().putBoolean("trip_price_from_charges",checked).apply());
        StatisticsUi.add(root,history);
        StatisticsUi.add(root,StatisticsUi.button(activity,"Сохранить тарифы",()->{
            try {
                double ep=StatisticsUi.parse(electricity,true,100000),fp=StatisticsUi.parse(fuel,true,100000);
                double loss=StatisticsUi.parse(losses,true,50),cap=StatisticsUi.parse(capacity,true,200),dcPrice=StatisticsUi.parse(dc,true,100000);
                AppPreferences.get(activity).edit().putFloat("electricity_price",(float)ep).putFloat("fuel_price",(float)fp).putFloat("charge_loss_percent",(float)loss).putFloat("dc_electricity_price",(float)dcPrice).apply();
                activity.getSharedPreferences("tech",0).edit().putFloat("capacity",(float)cap).apply();
                Toast.makeText(activity,"Тарифы сохранены для следующих записей",Toast.LENGTH_SHORT).show();
            }catch(NumberFormatException ignored){}
        }));
        StatisticsUi.add(root,StatisticsUi.button(activity,"Открыть статистику поездок",()->MainActivity.openStatisticsFromSettings()));
        StatisticsUi.add(root,StatisticsUi.text(activity,"Полная стоимость рассчитывается без ввода расхода: электрическая энергия × тариф + бензин × цена. Энергия берётся из счётчика BMS, при его отсутствии — из непрерывных показаний мощности HV. Это оценка стоимости, а не измерение энергии из розетки.",14));
        StatisticsUi.add(root,StatisticsUi.text(activity,"В режиме предыдущих зарядок учитываются завершённые сессии до начала поездки. При известной ёмкости и SOC цена смешивается с остатком батареи; иначе используется последняя зарядка. Если зарядок нет или последняя старше 60 дней — настроенный тариф. Выбранная цена сохраняется в поездке; будущие зарядки её не меняют.",14));
        StatisticsUi.add(root,StatisticsUi.text(activity,"Заправки учитываются отдельно от расходов поездок. Для расхода «до полного» внесите две полные заправки с одометром и все промежуточные. Процент уровня бака в литры не переводится.",14));
    }
}
