package ru.logunov.bydsplit;

import java.util.Collection;

/** Adds only embedded package names, preserving existing filters and exclusions. */
final class EmbeddedImmersivePolicy {
    static String withPackages(String original, Collection<String> packages) {
        if (packages.isEmpty()) return original;
        String additions = String.join(",", packages);
        StringBuilder result = new StringBuilder();
        boolean status = false, navigation = false;
        for (String clause : original.split(":")) {
            if (clause.isEmpty()) continue;
            boolean full = clause.startsWith("immersive.full=");
            boolean barStatus = clause.startsWith("immersive.status=");
            boolean barNavigation = clause.startsWith("immersive.navigation=");
            if (full || barStatus || barNavigation) clause += "," + additions;
            status |= full || barStatus;
            navigation |= full || barNavigation;
            if (result.length() > 0) result.append(':');
            result.append(clause);
        }
        if (!status && !navigation) append(result, "immersive.full=" + additions);
        else {
            if (!status) append(result, "immersive.status=" + additions);
            if (!navigation) append(result, "immersive.navigation=" + additions);
        }
        return result.toString();
    }
    private static void append(StringBuilder result, String clause) {
        if (result.length() > 0) result.append(':');
        result.append(clause);
    }
}
