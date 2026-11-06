package ru.logunov.bydsplit;

import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Shared, complete plain-text report for the UI, clipboard and Telegram. */
final class DtcReport {
    static DtcScanner.Result decode(JSONObject value){
        DtcScanner.Result result=new DtcScanner.Result(value.optInt("address"));result.outcome=value.optString("outcome");
        result.count=DtcProtocol.unhex(value.optString("count"));result.list=DtcProtocol.unhex(value.optString("list"));result.identity=DtcProtocol.unhex(value.optString("identity"));return result;
    }
    static String label(String cls){return DtcEcuNames.label(cls);}
    static DtcEcuNames.Name blockName(JSONObject b){return DtcEcuNames.resolve(b.optInt("address"),DtcProtocol.unhex(b.optString("identity")),DtcProtocol.unhex(b.optString("name")));}
    static String occurrence(JSONObject block,String code){
        JSONArray snapshots=block.optJSONArray("snapshots");if(snapshots==null)return "";
        for(int i=0;i<snapshots.length();i++){
            JSONObject s=snapshots.optJSONObject(i);if(s==null||!code.equals(s.optString("code")))continue;
            String outcome=s.optString("outcome");
            if(outcome.equals("ok"))return "Дата и пробег возникновения: формат стоп-кадра пока не расшифрован.";
            if(outcome.equals("nrc"))return "Дата и пробег возникновения: блок отклонил чтение стоп-кадра.";
            if(outcome.equals("multiframe")||outcome.equals("truncated"))return "Дата и пробег возникновения: стоп-кадр прочитан не полностью.";
            return "Дата и пробег возникновения: ответ на чтение стоп-кадра не получен.";
        }
        return "";
    }
    static boolean hasRows(JSONObject b){try{return !DtcProtocol.rows(DtcProtocol.unhex(b.optString("list"))).isEmpty();}catch(Exception e){return false;}}
    static int[] eligible(JSONObject report,long now){
        if(report==null)return new int[0];long age=now-report.optLong("at");if(age<0||age>600000)return new int[0];
        List<Integer> addresses=new ArrayList<>();JSONArray blocks=report.optJSONArray("results");JSONObject locked=report.optJSONObject("eraseLocked");
        if(blocks!=null)for(int i=0;i<blocks.length();i++){JSONObject b=blocks.optJSONObject(i);if(b==null)continue;int a=b.optInt("address");
            if(DtcProtocol.addressAllowed(a)&&hasRows(b)&&(locked==null||!locked.optBoolean(Integer.toString(a)))&&!addresses.contains(a))addresses.add(a);}
        Collections.sort(addresses);int[] result=new int[addresses.size()];for(int i=0;i<result.length;i++)result[i]=addresses.get(i);return result;
    }
    static String text(JSONObject report){
        if(report==null)return "Отчёта пока нет.";
        StringBuilder out=new StringBuilder("BYD Split · диагностика Song\n");
        if(report.optBoolean("demo"))out.append("ДЕМОНСТРАЦИОННЫЕ ДАННЫЕ\n");
        out.append("Проверка: ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.ROOT).format(new Date(report.optLong("at")))).append('\n');
        JSONArray blocks=report.optJSONArray("results");int answered=0,now=0,old=0,pending=0;
        StringBuilder details=new StringBuilder();
        if(blocks!=null)for(int i=0;i<blocks.length();i++)try{
            JSONObject b=blocks.getJSONObject(i);int a=b.getInt("address");String outcome=b.optString("outcome");
            if(Arrays.asList("ok","nrc","truncated","multiframe").contains(outcome))answered++;
            DtcEcuNames.Name name=blockName(b);
            details.append('\n').append(name.label).append(String.format(Locale.ROOT," · 0x%03X",a)).append('\n').append(name.source).append('\n');
            if(b.optBoolean("staleAfterErase"))details.append("Данные до стирания: повторное чтение не подтверждено.\n");
            int count=DtcProtocol.count(DtcProtocol.unhex(b.optString("count")));
            List<DtcProtocol.Row> rows=DtcProtocol.rows(DtcProtocol.unhex(b.optString("list")));
            if(!"ok".equals(outcome))details.append("Не получен полный ответ: ").append(outcome).append('\n');
            else if(count==0)details.append("Кодов по ответу блока нет.\n");
            else if(count>0&&rows.isEmpty())details.append("Блок сообщает о кодах, список не прочитан полностью.\n");
            JSONObject descriptions=b.optJSONObject("descriptions");
            for(DtcProtocol.Row row:rows){
                if(row.kind.equals("NOW"))now++;else if(row.kind.equals("OLD"))old++;else pending++;
                details.append(row.code).append(" · ").append(row.kind.equals("NOW")?"Есть сейчас":row.kind.equals("OLD")?"Сохранённая":"Ожидающая").append('\n');
                String occurrence=occurrence(b,row.code);if(!occurrence.isEmpty())details.append(occurrence).append('\n');
                JSONObject desc=descriptions==null?null:descriptions.optJSONObject(row.code);
                details.append(desc==null?"Расшифровка для этого блока не найдена.":desc.optString("text")+("\n"+desc.optString("source",desc.optBoolean("exact")?"Справочник модели · EN":"Справочник похожих моделей · EN"))).append('\n');
            }
        }catch(Exception e){details.append("Неполные данные блока.\n");}
        out.append("Ответили: ").append(answered).append(" из ").append(blocks==null?0:blocks.length()).append(" · текущие: ").append(now).append(" · ожидающие: ").append(pending).append(" · сохранённые: ").append(old).append('\n');
        JSONObject erase=report.optJSONObject("lastErase");if(erase!=null){out.append("\nПоследнее стирание: ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.ROOT).format(new Date(erase.optLong("at",report.optLong("at"))))).append(" · ").append(erase.optString("state")).append(' ').append(erase.optString("reason")).append('\n');JSONArray results=erase.optJSONArray("erase");
            if(results!=null)for(int i=0;i<results.length();i++){JSONObject r=results.optJSONObject(i);if(r!=null)out.append(String.format(Locale.ROOT,"0x%03X · ",r.optInt("address"))).append(r.optString("verdict","Результат неизвестен")).append('\n');}}
        return out.append(details).append("\nНет ответа или расшифровки — не означает отсутствие неисправности. Код описывает обнаруженное событие, а не устанавливает причину поломки.\n").toString();
    }
}
