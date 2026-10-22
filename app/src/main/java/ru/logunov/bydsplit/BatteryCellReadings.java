package ru.logunov.bydsplit;

/** Verified Seagull GB format: numbered measurements, not physical cell locations. */
final class BatteryCellReadings {
    static final int CELL_COUNT=94, PROBE_COUNT=7;
    static final long MAX_AGE_MS=10000;
    final int[] millivolts, temperatures;
    final long cellsAt, temperaturesAt;
    BatteryCellReadings(int[] cells,long ca,int[] temps,long ta) {
        millivolts=cells==null?null:cells.clone(); temperatures=temps==null?null:temps.clone();
        cellsAt=ca;temperaturesAt=ta;
    }
    boolean cellsFresh(long now){return millivolts!=null&&fresh(cellsAt,now);}
    boolean temperaturesFresh(long now){return temperatures!=null&&fresh(temperaturesAt,now);}
    private static boolean fresh(long at,long now){return at>0&&now>=at&&now-at<=MAX_AGE_MS;}
    static BatteryCellReadings parse(String output) {
        int[] cells=null,temps=null;long ca=0,ta=0;
        try {for(String line:output.split("[\\r\\n]+")) {
            if(line.startsWith("CELLS="))cells=values(line.substring(6),CELL_COUNT,1500,4500);
            else if(line.startsWith("TEMPS="))temps=values(line.substring(6),PROBE_COUNT,-39,100);
            else if(line.startsWith("CELLS_AT="))ca=Long.parseLong(line.substring(9));
            else if(line.startsWith("TEMPS_AT="))ta=Long.parseLong(line.substring(9));
        }}catch(NumberFormatException invalid){return new BatteryCellReadings(null,0,null,0);}
        return new BatteryCellReadings(cells,ca,temps,ta);
    }
    private static int[] values(String s,int count,int min,int max) {
        String[] parts=s.split(",",-1);if(parts.length!=count)return null;int[] r=new int[count];
        for(int i=0;i<count;i++){r[i]=Integer.parseInt(parts[i]);if(r[i]<min||r[i]>max)return null;}return r;
    }
    static String csv(int[] a){StringBuilder b=new StringBuilder();for(int v:a){if(b.length()>0)b.append(',');b.append(v);}return b.toString();}
    static final class Decoder {
        private final int[] cells=new int[CELL_COUNT],temps=new int[PROBE_COUNT];
        private int nextCell=-1,nextTemp=-1;private long cellStart,tempStart;
        private int[] completeCells,completeTemps;private long cellsAt,tempsAt;
        synchronized void accept(byte[] b,long now) {
            // Di2: CAN ID (big endian), bus 0 (2 bytes), payload (8 bytes). No timestamp.
            if(b==null||b.length!=14||b[0]!=0||b[1]!=0||b[2]!=4||b[4]!=0||b[5]!=0)return;
            int type=b[3]&255,index=b[6]&255;if(type!=0x3d&&type!=0x3c)return;
            if(type==0x3d&&index>31||type==0x3c&&index>1)return;
            int sum=0;for(int i=6;i<14;i++)sum+=b[i]&255;
            if((sum&255)!=255){if(type==0x3d)nextCell=-1;else nextTemp=-1;return;}
            if(type==0x3d) {
                if(index==0){nextCell=0;cellStart=now;}
                if(index!=nextCell||now<cellStart||now-cellStart>6000){nextCell=-1;return;}
                for(int k=0;k<3;k++) {
                    int pos=index*3+k,v=(b[7+k*2]&255)|((b[8+k*2]&255)<<8);
                    if(pos>=CELL_COUNT){if(v!=65535){nextCell=-1;return;}continue;}
                    if(v<1500||v>4500){nextCell=-1;return;}cells[pos]=v;
                }
                nextCell++;if(index==31){completeCells=cells.clone();cellsAt=now;nextCell=-1;}
            }else {
                if(index==0){nextTemp=0;tempStart=now;}
                if(index!=nextTemp||now<tempStart||now-tempStart>1000){nextTemp=-1;return;}
                for(int k=0;k<6;k++) {
                    int pos=index*6+k,raw=b[7+k]&255;
                    if(pos>=PROBE_COUNT){if(raw!=255){nextTemp=-1;return;}continue;}
                    int v=raw-40;if(v< -39||v>100){nextTemp=-1;return;}temps[pos]=v;
                }
                nextTemp++;if(index==1){completeTemps=temps.clone();tempsAt=now;nextTemp=-1;}
            }
        }
        synchronized BatteryCellReadings snapshot(){return new BatteryCellReadings(completeCells,cellsAt,completeTemps,tempsAt);}
    }
}
