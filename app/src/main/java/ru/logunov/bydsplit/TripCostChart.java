package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;
import java.text.SimpleDateFormat;

/** Seven most recent recorded days, with independent coverage for each day's total. */
final class TripCostChart extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<List<StatisticsStore.Entry>> days=new ArrayList<>();
    TripCostChart(Context context,List<StatisticsStore.Entry> entries){
        super(context);
        Map<String,List<StatisticsStore.Entry>> grouped=new LinkedHashMap<>();
        SimpleDateFormat key=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
        for(StatisticsStore.Entry e:entries)if("trip".equals(e.type)){
            String k=key.format(new Date(e.start));
            if(!grouped.containsKey(k)){if(grouped.size()==7)continue;grouped.put(k,new ArrayList<>());}
            grouped.get(k).add(e);
        }
        days.addAll(grouped.values());Collections.reverse(days);
        setContentDescription("Стоимость последних дней с поездками: зелёный — электричество, янтарный — бензин. Звёздочка означает неполный итог дня.");
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(days.isEmpty())return;
        float d=getResources().getDisplayMetrics().density,left=8*d,bottom=getHeight()-26*d,top=25*d;
        float width=(getWidth()-16*d)/days.size(),bar=Math.min(48*d,width*.52f);
        double max=1;for(List<StatisticsStore.Entry> day:days)max=Math.max(max,new StatisticsSummary(day).cost);
        paint.setStrokeWidth(d);paint.setColor(getContext().getColor(R.color.button));
        for(int i=0;i<3;i++){float y=top+(bottom-top)*i/2;c.drawLine(left,y,getWidth()-left,y,paint);}
        for(int i=0;i<days.size();i++){
            StatisticsSummary s=new StatisticsSummary(days.get(i));float x=left+width*(i+.5f);
            float eh=(float)(s.pricedElectricity/max)*(bottom-top),fh=(float)(s.pricedFuel/max)*(bottom-top);
            paint.setColor(getContext().getColor(R.color.success));c.drawRect(x-bar/2,bottom-eh,x+bar/2,bottom,paint);
            paint.setColor(getContext().getColor(R.color.warning));c.drawRect(x-bar/2,bottom-eh-fh,x+bar/2,bottom-eh,paint);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(12*d);paint.setColor(getContext().getColor(R.color.text_primary));
            String value=s.priced==0?"—":String.format(Locale.getDefault(),"%.0f",s.cost)+(s.priced<s.trips?"*":"");
            c.drawText(value,x,Math.max(14*d,bottom-eh-fh-7*d),paint);
            paint.setColor(getContext().getColor(R.color.text_secondary));
            c.drawText(new SimpleDateFormat("dd.MM",Locale.getDefault()).format(new Date(days.get(i).get(0).start)),x,getHeight()-6*d,paint);
        }
    }
}
