package ru.logunov.bydsplit;
import java.nio.charset.StandardCharsets;
/** BYD text buffer: UTF-16LE, at most 254 bytes, complete surrogate pairs. */
final class ClusterTrackText {
    static byte[] encode(String text){
        if(text==null||text.isEmpty())text=" ";
        int count=Math.min(text.length(),127);
        if(count<text.length()&&count>0&&Character.isHighSurrogate(text.charAt(count-1)))count--;
        return text.substring(0,count).getBytes(StandardCharsets.UTF_16LE);
    }
}
