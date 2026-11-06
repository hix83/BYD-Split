package ru.logunov.bydsplit;

import java.io.*;
import java.util.*;

/** BYDMate DtcDescriptions, adapted to Java. PolyForm Noncommercial; see NOTICE.
 * Song extension: unclassified model-manual rows and unanimous common code meanings.
 * No ECU identity is inferred from a code or an address.
 */
final class DtcDescriptions {
    static final String REVISION="song-dtc-20261106-1";
    private static final class Text {
        final String zh,en;
        Text(String zh,String en){this.zh=zh.trim();this.en=en.trim();}
    }
    private static final class Entry {
        final String manual,block;final Text text;
        Entry(String manual,String block,Text text){this.manual=manual;this.block=block;this.text=text;}
    }
    static final class Description {
        final String text,source,manual;final boolean exact;
        Description(Text text,boolean exact,String manual,String source){this.text=text.en;this.exact=exact;this.manual=manual;this.source=source;}
    }
    private final Map<String,Text> texts=new HashMap<>();
    private final Map<String,String> energy=new HashMap<>();
    private final Map<String,List<Entry>> rows=new HashMap<>();
    private final Map<Integer,List<String>> models=new HashMap<>();
    DtcDescriptions(Reader textsFile,Reader manualsFile,Reader rowsFile,Reader modelsFile)throws IOException {
        lines(textsFile,f->{if(f.length>=3)texts.put(f[0],new Text(f[1],f[2]));});
        lines(manualsFile,f->{if(f.length>=2)energy.put(f[0],f[1]);});
        Map<String,String> shared=new HashMap<>();
        lines(rowsFile,f->{if(f.length>=4&&texts.containsKey(f[3]))rows.computeIfAbsent(f[2],k->new ArrayList<>())
                .add(new Entry(shared.computeIfAbsent(f[0],k->k),shared.computeIfAbsent(f[1],k->k),texts.get(f[3])));});
        lines(modelsFile,f->{if(f.length>=2)try{List<String> ids=new ArrayList<>();for(String id:f[1].split(","))if(!id.trim().isEmpty())ids.add(id.trim());models.put(Integer.parseInt(f[0]),ids);}catch(NumberFormatException ignored){}});
    }
    Description lookup(int model,String block,String code) {
        List<Entry> candidates=rows.get(code.replace("-",""));if(candidates==null)return null;
        List<String> manuals=models.get(model);
        if(manuals!=null)for(String manual:manuals){
            String type=energy.get(manual);if(type!=null&&!type.equals("DM")&&!type.equals("?"))continue;
            List<Entry> matching=new ArrayList<>(),unclassified=new ArrayList<>(),inManual=new ArrayList<>();
            for(Entry row:candidates)if(row.manual.equals(manual)){
                inManual.add(row);
                if(block==null||row.block.equals(block))matching.add(row);
                if(row.block.isEmpty())unclassified.add(row);
            }
            // Prefer rows for the identified ECU. A manual without a block field is usable
            // only when its exact code has one distinct bilingual definition.
            boolean noBlock=matching.isEmpty()&&!unclassified.isEmpty();
            List<Entry> selected=noBlock?unclassified:matching;if(selected.isEmpty())continue;
            Text unique=unique(noBlock?inManual:selected,true);if(unique==null)return null;
            String origin=manual.equals("61402")?"Справочник Song L DM-i · EN":"Справочник модели BYD · EN";
            boolean unspecified=noBlock||block==null;
            return new Description(unique,true,manual,origin+(unspecified?" · без подтверждения типа блока":""));
        }
        if(block!=null){
            List<Entry> sameBlock=new ArrayList<>();for(Entry row:candidates)if(row.block.equals(block))sameBlock.add(row);
            Text common=unique(sameBlock,false);
            return common==null?null:new Description(common,false,sameBlock.get(0).manual,"Справочник похожих моделей BYD · EN");
        }
        // An unknown ECU must not be assigned an arbitrary model/block meaning. Only a
        // definition unanimous across EVERY occurrence of the full code may be shown.
        Text common=unique(candidates,false);
        return common==null?null:new Description(common,false,"","Общее значение кода BYD · EN · тип блока не подтверждён");
    }
    private static Text unique(List<Entry> entries,boolean bilingual){
        if(entries.isEmpty())return null;Text first=entries.get(0).text;if(first.en.isEmpty())return null;
        for(Entry row:entries)if(!first.en.equalsIgnoreCase(row.text.en)||(bilingual&&!first.zh.equals(row.text.zh)))return null;
        return first;
    }
    private static void lines(Reader reader,java.util.function.Consumer<String[]> consumer)throws IOException {
        try(BufferedReader in=new BufferedReader(reader)){String line;while((line=in.readLine())!=null)if(!line.isEmpty())consumer.accept(line.split("\t",-1));}
    }
}
