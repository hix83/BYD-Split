package ru.logunov.bydsplit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Separate release namespace for builds made from the seagull branch. */
final class SeagullUpdateChannel {
    private static final Pattern VERSION = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-(alpha|beta|rc)\\.([1-9][0-9]*))?$");

    static String versionFromTag(String tag) {
        if (tag == null || !tag.startsWith("seagull-v")) return null;
        String version = tag.substring("seagull-v".length());
        return parse(version) == null ? null : version;
    }

    static boolean acceptsApkVersion(String version) {
        return installedVersion(version) != null;
    }

    static String assetName(String tag) {
        String version = versionFromTag(tag);
        return version == null ? null : "BYD-Split-Seagull-" + version + ".apk";
    }

    static boolean newer(String tag, String installed) {
        String candidate = versionFromTag(tag), current = installedVersion(installed);
        return candidate != null && current != null && compare(parse(candidate), parse(current)) > 0;
    }

    static boolean newerTag(String first, String second) {
        String a = versionFromTag(first), b = versionFromTag(second);
        return a != null && (b == null || compare(parse(a), parse(b)) > 0);
    }

    private static String installedVersion(String value) {
        if (value == null) return null;
        String version;
        if (value.endsWith("-seagull")) version = value.substring(0, value.length() - 8);
        else if (value.contains("-seagull-")) version = value.replace("-seagull-", "-");
        else return null;
        return parse(version) == null ? null : version;
    }

    private static int[] parse(String version) {
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.matches()) return null;
        try {
            int stage = matcher.group(4) == null ? 3 : matcher.group(4).equals("alpha") ? 0 : matcher.group(4).equals("beta") ? 1 : 2;
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)), stage,
                    matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5))};
        } catch (NumberFormatException error) { return null; }
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            int comparison = Integer.compare(a[i], b[i]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }
}
