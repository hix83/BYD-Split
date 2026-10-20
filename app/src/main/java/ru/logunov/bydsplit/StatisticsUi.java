package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

final class StatisticsUi {
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,String value,int size) {
        TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(c.getColor(R.color.text_primary));
        v.setPadding(0,dp(c,5),0,dp(c,5));return v;
    }
    static Button button(Context c,String value,Runnable action) {
        Button b=new Button(c);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(c.getColor(R.color.text_primary));
        GradientDrawable bg=new GradientDrawable();bg.setColor(c.getColor(R.color.button));bg.setCornerRadius(dp(c,12));b.setBackground(bg);
        b.setOnClickListener(v->action.run());return b;
    }
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static void add(LinearLayout target,View child){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(target.getContext(),8);target.addView(child,p);}
    static String number(Double n,String unit){return n==null?"—":String.format(Locale.getDefault(),"%.2f %s",n,unit);}
    static EditText input(Context c,String hint,Double value) {
        EditText v=new EditText(c);v.setTextColor(c.getColor(R.color.text_primary));v.setHintTextColor(c.getColor(R.color.text_secondary));
        v.setHint(hint);v.setSingleLine(true);v.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(value!=null)v.setText(String.format(Locale.US,"%.3f",value));return v;
    }
    static void addInput(LinearLayout parent,String title,EditText input) {
        TextView label=text(parent.getContext(),title,13);
        label.setTextColor(parent.getContext().getColor(R.color.text_secondary));
        add(parent,label);parent.addView(input,new LinearLayout.LayoutParams(-1,-2));
    }
    static ScrollView scroll(Context context,View fields) {
        ScrollView scroll=new ScrollView(context);scroll.addView(fields);return scroll;
    }
    static Double parse(EditText input,boolean required,double max) {
        String s=input.getText().toString().trim();if(s.isEmpty()&&!required)return null;
        try{double n=Double.parseDouble(s.replace(',','.'));if(!Double.isFinite(n)||n<0||n>max)throw new NumberFormatException();return n;}
        catch(NumberFormatException e){input.setError("Укажите число от 0 до "+max);throw e;}
    }
}
