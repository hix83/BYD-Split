package ru.logunov.bydsplit;

public final class EvRangeMathTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        int[] batch=EvRangeMath.decodeBatch("Result: Parcel(00000000 42880000 '....')\nResult: Parcel(ffffffff 00000009)\nResult: Parcel(00000000 00000208)");
        check(Float.intBitsToFloat(batch[0])==68);check(EvRangeMath.nativeKm(batch[1])==null);check(batch[2]==520);
        check(EvRangeMath.decodeBatch("Parcel(00000000 00000048)")==null);
        check(EvRangeMath.decodeBatch("Parcel(00000000) Parcel(00000000 00000000) Parcel(00000000 000007fe)")[1]==0);
        int[] extended=EvRangeMath.decodeBatch("Parcel(00000000 42880000) Parcel(00000000 00000048) Parcel(00000000 00000208) Parcel(00000000 00000041) Parcel(ffffffff) Parcel(00000000 00000041) Parcel(00000000 4149999a) Parcel(00000000 00000017)",8);
        check(extended[4]==2046&&extended[5]==65&&extended[7]==23);
        check(EvRangeMath.nativeKm(2046)==null);check(EvRangeMath.nativeKm(-1)==null);
        check(EvRangeMath.nativeKm(Integer.MAX_VALUE)==null);check(EvRangeMath.nativeKm(0)==0);
        check(EvRangeMath.nativeKm(72)==72);
        check(EvRangeMath.soc(65535)==null);check(EvRangeMath.soc(1048575)==null);
        check(EvRangeMath.soc(-10013)==null);check(EvRangeMath.soc(Float.floatToIntBits(-1f))==null);
        check(EvRangeMath.soc(0)==0);check(EvRangeMath.soc(Float.floatToIntBits(68f))==68);
        check(Math.abs(EvRangeMath.forecast(68f,18.3,25,16.3)-48.276)<.01);
        check(EvRangeMath.forecast(20f,18.3,25,16.3)==0);
        check(EvRangeMath.forecast(100f,18.3,0,18.3)==100);
        check(EvRangeMath.forecast(null,18.3,25,16.3)==null);
        check(EvRangeMath.forecast(Float.NaN,18.3,25,16.3)==null);
        check(EvRangeMath.forecast(68f,0,25,16.3)==null);
        check(EvRangeMath.forecast(68f,18.3,100,16.3)==null);
        check(EvRangeMath.forecast(68f,18.3,25,0)==null);
        check(EvRangeMath.forecast(68f,Double.POSITIVE_INFINITY,25,16.3)==null);
        check(EvRangeMath.forecast(68f,18.3,25,Double.NaN)==null);
        System.out.println("EV native sentinel, valid zero, reserve, forecast and invalid inputs passed");
    }
}
