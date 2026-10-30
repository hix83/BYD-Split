package ru.logunov.bydsplit;

import android.app.*;
import android.widget.*;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;

final class EvRangeSettings {
    static AlertDialog show(Activity activity){
        EvRangeController data=EvRangeController.get(activity);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(activity,24);root.setPadding(pad,dp(activity,8),pad,pad);
        TextView help=new TextView(activity);help.setText("Прогноз использует реальный расход последних 100 км электрических участков, до 10 поездок за 30 дней. Нужны минимум 5 км с подтверждённо выключенным ДВС. Рекуперация учитывается; зарядка и пропуски данных исключаются. Включите запись поездок для обучения. Ёмкость и резерв задаются ниже; настройки машины не меняются.");root.addView(help);
        Double energy=data.energy();
        TextView summary=new TextView(activity);summary.setText(data.nativeText()+" · "+data.socText()+"\n"+data.forecastText()+(energy==null?"":String.format(java.util.Locale.ROOT," · выше резерва ≈ %.1f кВт·ч",energy))+"\n"+data.learningText());summary.setPadding(0,dp(activity,10),0,0);root.addView(summary);
        String[] labels={"Ёмкость батареи, кВт·ч (1–1000)","Резерв SOC, % (0–99)"};
        double[] values={data.capacity(),data.reserve()};EditText[] inputs=new EditText[2];
        for(int i=0;i<2;i++){
            TextView label=new TextView(activity);label.setText(labels[i]);label.setPadding(0,dp(activity,12),0,dp(activity,5));root.addView(label);
            inputs[i]=new EditText(activity);inputs[i].setSingleLine(true);
            inputs[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            inputs[i].setText(values[i]==0&&i==0?"":String.format(java.util.Locale.ROOT,"%.1f",values[i]));
            inputs[i].setContentDescription(labels[i]);inputs[i].setPadding(dp(activity,14),0,dp(activity,14),0);
            inputs[i].setBackground(shape(activity,activity.getColor(R.color.button),12));
            root.addView(inputs[i],new LinearLayout.LayoutParams(-1,dp(activity,44)));
        }
        for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof TextView){TextView t=(TextView)root.getChildAt(i);t.setTextColor(ThemePalette.text(activity));t.setTypeface(activity.getResources().getFont(R.font.ui7_regular));}
        TextView title=new TextView(activity);title.setText("Прогноз EV · экспериментальный");title.setTextSize(22);title.setTextColor(ThemePalette.text(activity));title.setTypeface(activity.getResources().getFont(R.font.ui7_regular));title.setPadding(pad,dp(activity,20),pad,dp(activity,12));
        LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.END);actions.setPadding(0,dp(activity,16),0,0);
        Button cancel=action(activity,"Отмена",false),save=action(activity,"Сохранить",true);
        actions.addView(cancel,new LinearLayout.LayoutParams(dp(activity,120),dp(activity,44)));
        LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(dp(activity,120),dp(activity,44));saveParams.setMarginStart(dp(activity,10));actions.addView(save,saveParams);root.addView(actions);
        ScrollView scroll=new ScrollView(activity);scroll.addView(root);
        AlertDialog dialog=new AlertDialog.Builder(activity,ThemePalette.dialogTheme(activity)).setCustomTitle(title).setView(scroll).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(shape(activity,activity.getColor(R.color.surface_light),20));
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);dialog.getWindow().setDimAmount(.4f);
        dialog.getWindow().setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,40),dp(activity,600)),-2);
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            float[] result=new float[2];
            for(int i=0;i<2;i++)try{result[i]=Float.parseFloat(inputs[i].getText().toString().trim().replace(',','.'));if(!Float.isFinite(result[i])||result[i]<(i==0?1:0)||result[i]>(i==0?1000:99))throw new NumberFormatException();}catch(NumberFormatException error){inputs[i].setError("Проверьте диапазон");return;}
            AppPreferences.get(activity).edit().putFloat(EvRangeController.CAPACITY,result[0]).putFloat(EvRangeController.RESERVE,result[1]).apply();data.notifyChanged();dialog.dismiss();
        });
        return dialog;
    }
    private static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable shape(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    private static Button action(Activity a,String title,boolean primary){
        Button b=new Button(a);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(a.getResources().getFont(R.font.ui7_regular));
        b.setTextColor(primary?android.graphics.Color.WHITE:ThemePalette.text(a));b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),shape(a,a.getColor(primary?R.color.accent:R.color.button),14),null));return b;
    }
}
