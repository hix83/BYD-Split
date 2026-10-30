package ru.logunov.bydsplit;

/** DM-i native range stays separate from the optional, consumption-based forecast.
 * Formula adapted from BYDMate RangeCalculator / ManualRangeCalculator; see NOTICE. */
final class EvRangeMath {
    static int[] decodeBatch(String output) {return decodeBatch(output,3);}
    static int[] decodeBatch(String output,int fields) {
        java.util.regex.Matcher parcels=java.util.regex.Pattern.compile("Parcel\\((.*?)\\)",java.util.regex.Pattern.DOTALL).matcher(output);
        int[] raw=new int[fields];java.util.Arrays.fill(raw,2046);raw[0]=Float.floatToIntBits(Float.NaN);int i=0;
        while(parcels.find()&&i<fields){
            java.util.regex.Matcher words=java.util.regex.Pattern.compile("^00000000\\s+([0-9a-fA-F]{8})\\b").matcher(parcels.group(1).trim());
            if(words.find())raw[i]=(int)Long.parseLong(words.group(1),16);
            i++;
        }
        return i==fields?raw:null;
    }
    static Integer nativeKm(int raw) { return raw >= 0 && raw < 2046 ? raw : null; }
    static Float soc(int raw) {
        if(raw==65535||raw==1048575)return null;
        float value=Float.intBitsToFloat(raw);
        return Float.isFinite(value)&&value>=0&&value<=100&&(value==0||value>=Float.MIN_NORMAL)?value:null;
    }
    static Double remaining(Float soc, double capacity, double reserve) {
        if(soc==null||!Float.isFinite(soc)||soc<0||soc>100||!Double.isFinite(capacity)
                ||capacity<1||capacity>1000||!Double.isFinite(reserve)||reserve<0||reserve>=100)return null;
        return Math.max(0,soc-reserve)*capacity/100.;
    }
    static Double forecast(Float soc,double capacity,double reserve,double consumption) {
        Double energy=remaining(soc,capacity,reserve);
        return energy==null||!Double.isFinite(consumption)||consumption<1||consumption>100
                ?null:energy*100/consumption;
    }
}
