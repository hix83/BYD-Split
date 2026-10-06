package ru.logunov.bydsplit;
import java.nio.charset.StandardCharsets;
public final class ClusterTrackTextTest {
    public static void main(String[] args){
        check(" ",ClusterTrackText.encode(null));
        check(" ",ClusterTrackText.encode(""));
        check("Название — Исполнитель",ClusterTrackText.encode("Название — Исполнитель"));
        String prefix="a".repeat(126);
        check(prefix,ClusterTrackText.encode(prefix+"🎵suffix"));
        check("a".repeat(127),ClusterTrackText.encode("a".repeat(200)));
        if(ClusterTrackText.encode("Я")[0]!=(byte)0x2f)throw new AssertionError("UTF-16LE");
        System.out.println("ClusterTrackText: passed");
    }
    private static void check(String expected,byte[] actual){
        if(actual.length>254||!expected.equals(new String(actual,StandardCharsets.UTF_16LE)))throw new AssertionError(expected);
    }
}
