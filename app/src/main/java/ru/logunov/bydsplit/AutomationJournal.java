package ru.logunov.bydsplit;

import java.text.SimpleDateFormat;
import java.util.*;

/** Bounded session journal, separate from the simulator. */
final class AutomationJournal {
    private static final Deque<String> entries=new ArrayDeque<>();
    static synchronized void add(String text) {
        entries.addFirst(new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+"  "+text);
        while(entries.size()>60)entries.removeLast();
    }
    static synchronized String text() {
        return entries.isEmpty()?"В этом запуске срабатываний ещё не было":String.join("\n\n",entries);
    }
}
