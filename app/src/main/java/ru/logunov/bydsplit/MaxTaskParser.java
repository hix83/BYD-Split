package ru.logunov.bydsplit;
import java.util.*;
import java.util.regex.*;

final class MaxTaskParser {
    private static final Pattern ROOT=Pattern.compile("^RootTask id=(\\d+).*\\bdisplayId=(\\d+)\\b");
    private static final Pattern MAX=Pattern.compile("\\bru\\.oneme\\.app/");
    static List<Integer> rootsToMove(String dump,int target){return scan(dump,target,false);}
    static boolean hasMaxOnDisplay(String dump,int target){return !scan(dump,target,true).isEmpty();}
    private static List<Integer> scan(String dump,int target,boolean same) {
        Set<Integer> result=new LinkedHashSet<>();int root=-1,display=-1;
        for(String line:dump.split("\\n")) {
            Matcher m=ROOT.matcher(line);
            if(m.find()){root=Integer.parseInt(m.group(1));display=Integer.parseInt(m.group(2));}
            if(root>=0&&line.trim().startsWith("taskId=")&&MAX.matcher(line).find()&&(same?display==target:display!=target))result.add(root);
        }
        return new ArrayList<>(result);
    }
}
