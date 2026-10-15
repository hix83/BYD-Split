package ru.logunov.bydsplit;

public final class ThemePolicyTest {
    public static void main(String[] args) {
        check(!ThemePolicy.isNight("day",32,false));
        check(ThemePolicy.isNight("night",16,true));
        check(ThemePolicy.isNight("auto",32,true));
        check(!ThemePolicy.isNight("auto",16,false));
        check(!ThemePolicy.isNight("auto",0,true));
        check(ThemePolicy.isNight("auto",0,false));
        check(ThemePolicy.isNight("auto",0,null));
        check(!ThemePolicy.isNight("invalid",16,null));
        System.out.println("ThemePolicy: 8 precedence and fallback checks passed");
    }
    private static void check(boolean value) { if(!value) throw new AssertionError(); }
}
