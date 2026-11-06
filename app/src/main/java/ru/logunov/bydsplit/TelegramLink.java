package ru.logunov.bydsplit;

import java.security.SecureRandom;

/** One-time bot binding; no background getUpdates loop. Adapted from BYDMate, see NOTICE. */
final class TelegramLink {
    static String newCode(){return String.format(java.util.Locale.ROOT,"%08d",new SecureRandom().nextInt(100000000));}
    static String code(String text){String value=text.trim();String[] parts=value.split("\\s+",2);
        return parts[0].equals("/start")||parts[0].matches("/start@[A-Za-z0-9_]+")?(parts.length==2?parts[1].trim():""):value;}
    static boolean fresh(long issued,long now){return now>=issued&&now-issued<600000;}
}
