package ru.logunov.bydsplit;

import java.io.*;
import java.nio.file.*;

/** Song L reference-code regression, plus conflicting-manual guards. No car commands. */
public final class DtcDescriptionsTest {
    public static void main(String[] args)throws Exception{
        String root="app/src/main/assets/dtc/";
        DtcDescriptions d=new DtcDescriptions(Files.newBufferedReader(Path.of(root+"texts.tsv")),Files.newBufferedReader(Path.of(root+"manuals.tsv")),Files.newBufferedReader(Path.of(root+"rows.tsv")),Files.newBufferedReader(Path.of(root+"models.tsv")));
        String[][] cases={
            {"B2250-00","Front left window not initialized"},
            {"B2225-13","Front left window motor open circuit"},
            {"B2226-13","Rear left window motor open circuit"},
            {"B1C2A-00","MG/ECM verification Keyless failed fault"},
            {"B1C11-00","Rear right door lock fault"},
            {"B1BF4-00","Front wiper reset signal fault"},
            {"B1C08-00","Rear wiper reset signal fault"},
            {"B2249-13","Rear right window motor open circuit"},
            {"U0253-87","Lost communication with compressor"},
            {"P1A3C-00","Traction battery single cell temperature general too low"}
        };
        for(String[] c:cases){DtcDescriptions.Description desc=d.lookup(161,c[0].equals("B2249-13")?"BODY_RIGHT":null,c[0]);
            check(desc!=null&&desc.text.equals(c[1]),"Song description missing: "+c[0]);
            boolean common=c[0].equals("B1C2A-00")||c[0].equals("P1A3C-00");
            check(desc.exact!=common,"model/common provenance: "+c[0]);
            check(common?desc.source.contains("Общее значение"):desc.manual.equals("61402"),"source lost: "+c[0]);
        }
        check(DtcProtocol.blockClass(0x7e7,DtcProtocol.unhex("62F1944A520010E30D00017C"))==null,"description guessed an ECU identity");
        DtcDescriptions conflict=fixture("m\t\tU100000\t1\nm\tBODY_LEFT\tU100000\t2\n");
        check(conflict.lookup(161,null,"U1000-00")==null,"ambiguous model code guessed");
        check(conflict.lookup(161,"BODY_RIGHT","U1000-00")==null,"unclassified row ignored a conflicting definition");
        check(conflict.lookup(161,"BODY_LEFT","U1000-00").text.equals("Other meaning"),"specific ECU row not preferred");
        check(conflict.lookup(999,null,"U1000-00")==null,"conflicting common meaning guessed");
        DtcDescriptions same=fixture("m\tBODY_LEFT\tU100000\t1\nn\tBODY_RIGHT\tU100000\t1\n");
        check(same.lookup(999,null,"U1000-00")!=null&&!same.lookup(999,null,"U1000-00").exact,"unanimous common definition unavailable");
        check(same.lookup(999,"BMS","U1000-00")==null,"known unrelated block got a generic meaning");
        DtcDescriptions bilingual=new DtcDescriptions(new StringReader("1\tzh-one\tSame English\n2\tzh-two\tSame English\n"),new StringReader("m\tDM\n"),new StringReader("m\t\tU100000\t1\nm\t\tU100000\t2\n"),new StringReader("161\tm\n"));
        check(bilingual.lookup(161,null,"U1000-00")==null,"distinct original texts merged as exact");
        System.out.println("DtcDescriptionsTest PASS: 10 Song L reference codes, 8 model/2 unanimous common sources, strict ambiguities, unclassified manual rows, no guessed ECU identity");
    }
    private static DtcDescriptions fixture(String rows)throws Exception{return new DtcDescriptions(new StringReader("1\tzh\tFirst meaning\n2\tzh-other\tOther meaning\n"),new StringReader("m\tDM\nn\tEV\n"),new StringReader(rows),new StringReader("161\tm\n"));}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
