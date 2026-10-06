package ru.logunov.bydsplit;
public final class UpdateVersionTest {
    public static void main(String[] args){
        check(true,"v0.9.1","0.9.0");check(false,"v0.9.1","0.9.1");
        check(false,"v0.8.9","0.9.0");check(true,"v0.10.0","0.9.9");
        check(true,"v1.0.0","0.9.9");check(true,"v0.9.1","0.8.3-ui7style");
        check(false,"garbage","0.9.1");System.out.println("UpdateVersion: passed");
    }
    static void check(boolean expected,String a,String b){if(UpdateVersion.newer(a,b)!=expected)throw new AssertionError(a+" / "+b);}
}
