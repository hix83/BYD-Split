package ru.logunov.bydsplit;

import java.io.*;
import java.util.*;

/** BYDMate DtcDescriptions, adapted to Java. PolyForm Noncommercial; see NOTICE. */
final class DtcDescriptions {
    private static final class Entry {
        final String manual,block,text;
        Entry(String manual,String block,String text){this.manual=manual;this.block=block;this.text=text;}
    }
    static final class Description {
        final String text;final boolean exact;
        Description(String text,boolean exact){this.text=text;this.exact=exact;}
    }
    private final Map<String,String> texts=new HashMap<>(),energy=new HashMap<>();
    private final Map<String,List<Entry>> rows=new HashMap<>();
    private final Map<Integer,List<String>> models=new HashMap<>();
    DtcDescriptions(Reader textsFile,Reader manualsFile,Reader rowsFile,Reader modelsFile)throws IOException {
        lines(textsFile,f->{if(f.length>=3)texts.put(f[0],f[2].trim());});
        lines(manualsFile,f->{if(f.length>=2)energy.put(f[0],f[1]);});
        Map<String,String> shared=new HashMap<>();
        lines(rowsFile,f->{if(f.length>=4&&texts.containsKey(f[3]))rows.computeIfAbsent(f[2],k->new ArrayList<>())
                .add(new Entry(shared.computeIfAbsent(f[0],k->k),shared.computeIfAbsent(f[1],k->k),f[3]));});
        lines(modelsFile,f->{if(f.length>=2)try{models.put(Integer.parseInt(f[0]),Arrays.asList(f[1].split(",")));}catch(NumberFormatException ignored){}});
    }
    Description lookup(int model,String block,String code) {
        List<Entry> candidates=rows.get(code.replace("-",""));if(candidates==null)return null;
        List<String> manuals=models.get(model);
        if(manuals!=null)for(String manual:manuals){
            String type=energy.get(manual);if(type!=null&&!type.equals("DM")&&!type.equals("?"))continue;
            Set<String> found=new HashSet<>();for(Entry row:candidates)if(row.manual.equals(manual)&&(block==null||row.block.equals(block)))found.add(texts.get(row.text));
            if(!found.isEmpty())return found.size()==1?new Description(found.iterator().next(),true):null;
        }
        if(block==null)return null;
        String common=null;
        for(Entry row:candidates)if(row.block.equals(block)){
            String text=texts.get(row.text);if(common==null)common=text;else if(!common.equalsIgnoreCase(text))return null;
        }
        return common==null?null:new Description(common,false);
    }
    private static void lines(Reader reader,java.util.function.Consumer<String[]> consumer)throws IOException {
        try(BufferedReader in=new BufferedReader(reader)){String line;while((line=in.readLine())!=null)if(!line.isEmpty())consumer.accept(line.split("\t",-1));}
    }
}
