package ru.logunov.bydsplit;

import java.util.*;

/** Pure temperature trigger engine; outputs are applied by the selected actuator. */
final class TemperatureRules {
    static final class Condition {
        boolean below; float threshold;
        String placeKey;
        boolean cabin;
        Condition(boolean below, float threshold) { this.below=below; this.threshold=threshold; }
    }
    static final class Rule {
        String id, name;
        boolean enabled = true, below = true;
        float threshold = 5;
        boolean all = true, startupOnly;
        int[] durationMinutes = new int[6];
        List<Condition> conditions = new ArrayList<>();
        int[] levels = {-1,-1,-1,-1,-1,-1}; // -1 unchanged, 0 off
    }
    interface Actuator { void apply(int action, int level); }
    private final Map<String,String> revisions = new HashMap<>();
    private final Set<String> latched = new HashSet<>();
    private final Set<String> startupChecked = new HashSet<>();
    private final Map<String,Set<Integer>> completed = new HashMap<>();
    private final Map<String,Long> retryAt = new HashMap<>();
    private final Map<Integer,Long> offAt = new HashMap<>();
    Map<Integer,Long> pendingOffs() { return new HashMap<>(offAt); }
    void restoreOff(int action,long deadline) { if(action>=0&&action<5)offAt.put(action,deadline); }
    void newSession() { revisions.clear(); latched.clear(); startupChecked.clear(); completed.clear(); retryAt.clear(); }
    void reset() { newSession(); offAt.clear(); }
    List<String> evaluate(List<Rule> rules, Float temperature, Actuator actuator) {
        return evaluate(rules, temperature, Collections.emptyMap(), actuator);
    }
    List<String> evaluate(List<Rule> rules, Float temperature, Map<String,Boolean> places, Actuator actuator) {
        return evaluate(rules, temperature, null, places, System.nanoTime()/1000000L, actuator);
    }
    List<String> evaluate(List<Rule> rules, Float temperature, Float cabin, Map<String,Boolean> places, long now, Actuator actuator) {
        for(Integer a:new ArrayList<>(offAt.keySet()))if(now>=offAt.get(a)){try { actuator.apply(a,0);offAt.remove(a); } catch(RuntimeException unavailable) { offAt.put(a,now+30000L); }}
        List<String> fired = new ArrayList<>();
        Set<String> present=new HashSet<>();for(Rule r:rules)present.add(r.id);
        revisions.keySet().retainAll(present);startupChecked.retainAll(present);latched.retainAll(present);
        completed.keySet().retainAll(present);retryAt.keySet().retainAll(present);
        for (Rule r : rules) {
            String revision=revision(r);
            String previous=revisions.put(r.id,revision);
            if(previous!=null&&!previous.equals(revision)){
                latched.remove(r.id);startupChecked.remove(r.id);completed.remove(r.id);retryAt.remove(r.id);
            }
            if (!r.enabled) { latched.remove(r.id); startupChecked.remove(r.id); completed.remove(r.id); retryAt.remove(r.id); continue; }
            List<Condition> conditions = r.conditions.isEmpty()
                    ? Arrays.asList(new Condition(r.below,r.threshold)) : r.conditions;
            boolean matches = r.all, rearm = !r.all, allKnown = true;
            for (Condition c : conditions) {
                Float measured = c.cabin ? cabin : temperature;
                boolean known = measured != null && Float.isFinite(measured);
                allKnown &= c.placeKey != null ? places.containsKey(c.placeKey) : known;
                boolean hit = c.placeKey != null ? Boolean.TRUE.equals(places.get(c.placeKey))
                        : known && (c.below ? measured <= c.threshold : measured >= c.threshold);
                boolean outside = c.placeKey != null ? Boolean.FALSE.equals(places.get(c.placeKey))
                        : known && (c.below ? measured > c.threshold + 2 : measured < c.threshold - 2);
                matches = r.all ? matches && hit : matches || hit;
                rearm = r.all ? rearm || outside : rearm && outside;
            }
            if(r.startupOnly){
                if(startupChecked.contains(r.id)||!allKnown)continue;
                if(!matches){startupChecked.add(r.id);continue;}
            }
            if (rearm && !r.startupOnly) { latched.remove(r.id); completed.remove(r.id); retryAt.remove(r.id); }
            if (!matches || latched.contains(r.id)) continue;
            if(now<retryAt.getOrDefault(r.id,0L))continue;
            boolean success=true;
            Set<Integer> done=completed.computeIfAbsent(r.id,id->new HashSet<>());
            for (int a=0;a<r.levels.length;a++) if (r.levels[a]>=0) {
                if(done.contains(a))continue;
                try { actuator.apply(a,r.levels[a]); } catch(RuntimeException unavailable) { success=false; continue; }
                done.add(a);
                offAt.remove(a);
                if(a>0&&a<5&&r.levels[a]>0)offAt.remove(a<=2?a+2:a-2);
                if(a<5&&r.levels[a]>0&&r.durationMinutes[a]>0)offAt.put(a,now+r.durationMinutes[a]*60000L);
            }
            if(success){latched.add(r.id);if(r.startupOnly)startupChecked.add(r.id);completed.remove(r.id);retryAt.remove(r.id);fired.add(r.name);}
            else retryAt.put(r.id,now+30000L);
        }
        return fired;
    }
    private static String revision(Rule r){
        StringBuilder value=new StringBuilder().append(r.enabled).append('|').append(r.all).append('|')
            .append(r.startupOnly).append('|').append(r.below).append('|').append(r.threshold)
            .append('|').append(Arrays.toString(r.levels)).append('|').append(Arrays.toString(r.durationMinutes));
        for(Condition c:r.conditions)value.append('|').append(c.below).append(':').append(c.cabin)
            .append(':').append(c.threshold).append(':').append(c.placeKey);
        return value.toString();
    }

}
