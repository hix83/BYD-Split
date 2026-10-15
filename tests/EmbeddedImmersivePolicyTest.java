package ru.logunov.bydsplit;

import java.util.Arrays;
import java.util.Collections;

public final class EmbeddedImmersivePolicyTest {
    public static void main(String[] args) {
        check("immersive.full=ru.yandex.yandexnavi", "");
        check("immersive.full=existing,-excluded,ru.yandex.yandexnavi:immersive.preconfirms=apps",
                "immersive.full=existing,-excluded:immersive.preconfirms=apps");
        check("immersive.status=existing,ru.yandex.yandexnavi:immersive.navigation=ru.yandex.yandexnavi",
                "immersive.status=existing");
        check("immersive.navigation=existing,ru.yandex.yandexnavi:immersive.status=ru.yandex.yandexnavi",
                "immersive.navigation=existing");
        String original = "immersive.full=apps,-excluded";
        if (!original.equals(EmbeddedImmersivePolicy.withPackages(original, Collections.emptyList())))
            throw new AssertionError("Empty displays must restore original exactly");
        String both = EmbeddedImmersivePolicy.withPackages("", Arrays.asList("a", "b"));
        if (!"immersive.full=a,b".equals(both)) throw new AssertionError(both);
        System.out.println("EmbeddedImmersivePolicyTest passed");
    }
    private static void check(String expected, String original) {
        String actual = EmbeddedImmersivePolicy.withPackages(original, Collections.singleton("ru.yandex.yandexnavi"));
        if (!expected.equals(actual)) throw new AssertionError(actual);
    }
}
