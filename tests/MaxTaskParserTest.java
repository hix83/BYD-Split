package ru.logunov.bydsplit;
import java.util.*;
public class MaxTaskParserTest {
 public static void main(String[] args) {
  String dump="RootTask id=4 bounds=[] displayId=0 userId=0\n taskId=4: ru.oneme.app/one.me.android.MainActivity\n"
   +"RootTask id=6 bounds=[] displayId=12 userId=0\n taskId=6: ru.oneme.app/one.me.android.calls.CallNotifierFixActivity\n"
   +"RootTask id=8 bounds=[] displayId=2 userId=0\n taskId=8: ru.oneme.app/one.me.android.MainActivity\n"
   +"RootTask id=9 bounds=[] displayId=0 userId=0\n taskId=9: xru.oneme.app/Fake\n";
  if(!MaxTaskParser.rootsToMove(dump,2).equals(Arrays.asList(4,6)))throw new AssertionError();
  if(!MaxTaskParser.hasMaxOnDisplay(dump,2)||MaxTaskParser.hasMaxOnDisplay(dump,1))throw new AssertionError();
  if(!MaxTaskParser.rootsToMove("taskId=7: ru.oneme.app/Main",2).isEmpty())throw new AssertionError();
  System.out.println("MAX task routing parser checks passed");
 }
}
