package ru.logunov.bydsplit;

/** Song GB stream: numbered measurements, never a physical cell/sensor placement. */
final class BatteryCellReadings {
    static final long MAX_AGE_MS = 10000;
    final int[] millivolts, temperatures;
    final long cellsAt, temperaturesAt;
    BatteryCellReadings(int[] cells, long cellsAt, int[] temps, long temperaturesAt) {
        millivolts=cells==null?null:cells.clone(); temperatures=temps==null?null:temps.clone();
        this.cellsAt=cellsAt;this.temperaturesAt=temperaturesAt;
    }
    boolean cellsFresh(long now){return millivolts!=null&&fresh(cellsAt,now);}
    boolean temperaturesFresh(long now){return temperatures!=null&&fresh(temperaturesAt,now);}
    private static boolean fresh(long at,long now){return at>0&&now>=at&&now-at<=MAX_AGE_MS;}
    static BatteryCellReadings parse(String output) {
        int[] cells=null,temps=null;long ca=0,ta=0;
        try {
            for(String line:output.split("[\r\n]+")) {
                if(line.startsWith("CELLS="))cells=values(line.substring(6),106,1500,4500);
                else if(line.startsWith("TEMPS="))temps=values(line.substring(6),12,-39,100);
                else if(line.startsWith("CELLS_AT="))ca=Long.parseLong(line.substring(9));
                else if(line.startsWith("TEMPS_AT="))ta=Long.parseLong(line.substring(9));
            }
        } catch(NumberFormatException invalid){return new BatteryCellReadings(null,0,null,0);}
        return new BatteryCellReadings(cells,ca,temps,ta);
    }
    private static int[] values(String value,int count,int min,int max) {
        String[] parts=value.split(",",-1);if(parts.length!=count)return null;
        int[] result=new int[count];
        for(int i=0;i<count;i++){result[i]=Integer.parseInt(parts[i]);if(result[i]<min||result[i]>max)return null;}
        return result;
    }
    static String csv(int[] values){StringBuilder s=new StringBuilder();for(int v:values){if(s.length()>0)s.append(',');s.append(v);}return s.toString();}

    /** Only the 106-cell / 12-probe format verified on this Song is supported. */
    static final class Decoder {
        private final int[] cells=new int[106],temps=new int[12];
        private int nextCellFrame=-1,nextTempFrame=-1;
        private long cellsStarted,tempsStarted;
        private int[] completeCells,completeTemps;
        private long cellsAt,tempsAt;
        synchronized void accept(byte[] frame,long now) {
            // OEM envelope: big-endian CAN ID, bus/flags 0x0002, timestamp, 8-byte payload.
            if(frame==null||frame.length!=18||frame[0]!=0||frame[1]!=0||frame[2]!=4||frame[4]!=0||frame[5]!=2)return;
            int type=frame[3]&255,index=frame[10]&255;
            if(type==0x3d) {
                if(index>35)return; // 0xfd is a different summary frame, not three cells.
                if(index==0){nextCellFrame=0;cellsStarted=now;}
                if(index!=nextCellFrame||now<cellsStarted||now-cellsStarted>6000){nextCellFrame=-1;return;}
                for(int k=0;k<3;k++) {
                    int pos=index*3+k,v=(frame[11+k*2]&255)|((frame[12+k*2]&255)<<8);
                    if(pos>=cells.length){if(v!=65535){nextCellFrame=-1;return;}continue;}
                    if(v<1500||v>4500){nextCellFrame=-1;return;}cells[pos]=v;
                }
                nextCellFrame++;
                if(index==35){completeCells=cells.clone();cellsAt=now;nextCellFrame=-1;}
            } else if(type==0x3c) {
                if(index>1)return;
                int sum=0;for(int i=10;i<18;i++)sum+=frame[i]&255;
                if((sum&255)!=255){nextTempFrame=-1;return;}
                if(index==0){nextTempFrame=0;tempsStarted=now;}
                if(index!=nextTempFrame||now<tempsStarted||now-tempsStarted>1000){nextTempFrame=-1;return;}
                for(int k=0;k<6;k++){int v=(frame[11+k]&255)-40;if(v< -39||v>100){nextTempFrame=-1;return;}temps[index*6+k]=v;}
                nextTempFrame++;
                if(index==1){completeTemps=temps.clone();tempsAt=now;nextTempFrame=-1;}
            }
        }
        synchronized BatteryCellReadings snapshot(){return new BatteryCellReadings(completeCells,cellsAt,completeTemps,tempsAt);}
    }
}
