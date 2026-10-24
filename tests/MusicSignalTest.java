package ru.logunov.bydsplit;
import java.util.Arrays;
public final class MusicSignalTest {
    public static void main(String[] args) {
        byte[] silence=new byte[256];Arrays.fill(silence,(byte)128);
        for(float v:MusicSignal.waveform(silence))check(v==0,"DC waveform");
        for(float v:MusicSignal.waveform(new byte[256]))check(v==0,"zero buffer");
        byte[] fft=new byte[256];fft[0]=127;fft[1]=127;
        for(float v:MusicSignal.spectrum(fft))check(v==0,"DC or Nyquist leaked");
        fft[16]=48;float[] low=MusicSignal.spectrum(fft);fft[16]=0;fft[160]=-48;
        float[] high=MusicSignal.spectrum(fft);
        int a=peak(low),b=peak(high);check(a>=0&&b>a,"frequency order");
        for(float v:high)check(Float.isFinite(v)&&v>=0&&v<=1,"range");
        for(byte[] input:new byte[][]{null,new byte[0],new byte[1],new byte[7]})
            for(float v:MusicSignal.spectrum(input))check(v==0,"short FFT");
        byte[] wave=new byte[256];for(int i=0;i<wave.length;i++)wave[i]=(byte)(128+80*Math.sin(i*.2));
        boolean pos=false,neg=false;for(float v:MusicSignal.waveform(wave)){pos|=v>.4;neg|=v<-.4;}
        check(pos&&neg,"waveform polarity");
        check(AmbientPalette.color(4,18)==0xffffe28c,"Song palette uses one-based color 18");
        check(AmbientPalette.color(4,0)==null&&AmbientPalette.color(4,32)==null,"invalid palette index");
        check(AmbientPalette.color(65535,18)==null&&AmbientPalette.color(4,65535)==null,"vehicle sentinel");
        check(AmbientPalette.color(3,7)!=null&&AmbientPalette.color(5,64)!=null&&AmbientPalette.color(6,127)!=null,"palette sizes");
        System.out.println("PASS: silence, DC rejection, signed FFT, ordered frequencies, short inputs, waveform polarity");
    }
    static int peak(float[] values){int index=-1;float max=0;for(int i=0;i<values.length;i++)if(values[i]>max){max=values[i];index=i;}return index;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
