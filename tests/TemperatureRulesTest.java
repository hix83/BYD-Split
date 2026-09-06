package ru.logunov.bydsplit;
import java.util.*;
public class TemperatureRulesTest {
 public static void main(String[] args){
  TemperatureRules e=new TemperatureRules();TemperatureRules.Rule r=new TemperatureRules.Rule();r.id="winter";r.name="winter";r.threshold=5;r.levels[0]=1;
  List<TemperatureRules.Rule> rules=Arrays.asList(r);List<Integer> calls=new ArrayList<>();TemperatureRules.Actuator a=(id,l)->calls.add(l);
  e.evaluate(rules,null,a);e.evaluate(rules,Float.NaN,a);check(calls.size()==0);
  e.evaluate(rules,5f,a);check(calls.size()==1);
  e.evaluate(rules,4f,a);e.evaluate(rules,6f,a);e.evaluate(rules,5f,a);check(calls.size()==1);
  e.evaluate(rules,8f,a);e.evaluate(rules,5f,a);check(calls.size()==2);
  r.enabled=false;e.evaluate(rules,0f,a);check(calls.size()==2);
  r.enabled=true;r.below=false;r.threshold=20;r.levels[0]=0;e.evaluate(rules,21f,a);check(calls.size()==3&&calls.get(2)==0);
  e.reset();calls.clear();r.all=true;r.conditions=Arrays.asList(new TemperatureRules.Condition(false,0),new TemperatureRules.Condition(true,10));
  e.evaluate(rules,-5f,a);check(calls.isEmpty());e.evaluate(rules,5f,a);check(calls.size()==1);e.evaluate(rules,15f,a);e.evaluate(rules,5f,a);check(calls.size()==2);
  e.reset();calls.clear();r.all=false;r.conditions=Arrays.asList(new TemperatureRules.Condition(true,0),new TemperatureRules.Condition(false,20));
  e.evaluate(rules,10f,a);check(calls.isEmpty());e.evaluate(rules,-1f,a);check(calls.size()==1);e.evaluate(rules,10f,a);e.evaluate(rules,21f,a);check(calls.size()==2);
  e.reset();calls.clear();TemperatureRules.Condition place=new TemperatureRules.Condition(true,0);place.placeKey="home";r.conditions=Arrays.asList(place);r.all=true;r.levels=new int[]{-1,-1,-1,-1,-1,0};
  e.evaluate(rules,null,Collections.singletonMap("home",false),a);check(calls.isEmpty());
  e.evaluate(rules,null,Collections.singletonMap("home",true),a);e.evaluate(rules,null,Collections.singletonMap("home",true),a);check(calls.size()==1);
  e.evaluate(rules,null,Collections.singletonMap("home",false),a);e.evaluate(rules,null,Collections.singletonMap("home",true),a);check(calls.size()==2);
  e.reset();calls.clear();TemperatureRules.Condition cabin=new TemperatureRules.Condition(true,10);cabin.cabin=true;r.conditions=Arrays.asList(cabin);r.startupOnly=true;r.levels=new int[]{1,-1,-1,-1,-1,-1};r.durationMinutes[0]=1;
  e.evaluate(rules,30f,null,Collections.emptyMap(),0,a);check(calls.isEmpty());
  e.evaluate(rules,30f,5f,Collections.emptyMap(),1,a);check(calls.size()==1);
  e.evaluate(rules,30f,5f,Collections.emptyMap(),60001,a);check(calls.size()==2&&calls.get(1)==0);
  e.evaluate(rules,30f,1f,Collections.emptyMap(),70000,a);check(calls.size()==2);
  e.reset();calls.clear();e.evaluate(rules,0f,20f,Collections.emptyMap(),0,a);e.evaluate(rules,0f,5f,Collections.emptyMap(),1000,a);check(calls.isEmpty());
  e.reset();calls.clear();r.startupOnly=false;
  e.evaluate(rules,0f,5f,Collections.emptyMap(),0,(id,l)->{throw new IllegalStateException("offline");});
  e.evaluate(rules,0f,5f,Collections.emptyMap(),1,a);check(calls.isEmpty());check(e.pendingOffs().isEmpty());
  e.restoreOff(0,10);e.evaluate(Collections.emptyList(),null,null,Collections.emptyMap(),10,(id,l)->{throw new IllegalStateException("offline");});
  check(e.pendingOffs().get(0)==30010L);
  e.evaluate(Collections.emptyList(),null,null,Collections.emptyMap(),30010,a);check(calls.size()==1&&calls.get(0)==0);check(e.pendingOffs().isEmpty());
  System.out.println("Temperature trigger checks passed");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
}
