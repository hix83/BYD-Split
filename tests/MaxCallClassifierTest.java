package ru.logunov.bydsplit;
public class MaxCallClassifierTest {
 public static void main(String[] args) {
  check(MaxCallClassifier.isLiveCall("ru.oneme.app","call",false,true,0));
  check(MaxCallClassifier.isLiveCall("ru.oneme.app","call",true,false,0));
  check(MaxCallClassifier.isLiveCall("ru.oneme.app","call",false,false,1));
  check(!MaxCallClassifier.isLiveCall("ru.oneme.app","call",false,false,0));
  check(!MaxCallClassifier.isLiveCall("ru.oneme.app","msg",true,true,1));
  check(!MaxCallClassifier.isLiveCall("other.app","call",true,true,1));
  check(!MaxCallClassifier.isLiveCall(null,null,false,false,0));
  System.out.println("MAX call notification classification checks passed");
 }
 static void check(boolean value){if(!value)throw new AssertionError();}
}
