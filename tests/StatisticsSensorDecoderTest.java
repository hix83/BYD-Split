package ru.logunov.bydsplit;
public final class StatisticsSensorDecoderTest {
    public static void main(String[] args) {
        if(StatisticsSensorDecoder.decode(1,Float.floatToIntBits(-1f))!=null)throw new AssertionError("live Song unavailable current accepted");
        if(StatisticsSensorDecoder.decode(1,65535)!=null)throw new AssertionError("link sentinel accepted");
        if(StatisticsSensorDecoder.decode(1,1048575)!=null)throw new AssertionError("initialization sentinel accepted");
        if(StatisticsSensorDecoder.decode(0,-1)!=null)throw new AssertionError("invalid volts");
        near(StatisticsSensorDecoder.decode(0,341),341);
        near(StatisticsSensorDecoder.decode(1,Float.floatToIntBits(-2.5f)),-2.5);
        near(StatisticsSensorDecoder.decode(1,Float.floatToIntBits(0)),0);
        near(StatisticsSensorDecoder.decode(2,118813),11881.3);
        System.out.println("Live Song missing-current regression and independent sensor decoding passed");
    }
    static void near(Double a,double b){if(a==null||Math.abs(a-b)>1e-6)throw new AssertionError(a+" != "+b);}
}
