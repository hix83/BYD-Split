package ru.logunov.bydsplit;
/** Stable GitHub release tags use vMAJOR.MINOR.PATCH. */
final class UpdateVersion {
    static boolean newer(String candidate,String installed){
        int[] a=parse(candidate),b=parse(installed);
        if(a==null||b==null)return false;
        for(int i=0;i<3;i++)if(a[i]!=b[i])return a[i]>b[i];
        return false;
    }
    private static int[] parse(String value){
        if(value==null)return null;
        String[] parts=value.replaceFirst("^v", "").split("-",2)[0].split("\\.");
        if(parts.length!=3)return null;
        try{return new int[]{Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2])};}
        catch(NumberFormatException e){return null;}
    }
}
