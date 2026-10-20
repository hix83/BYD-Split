package ru.logunov.bydsplit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lifetime litres at the two observed trip boundaries. Never derives litres from tank %. */
final class TripFuelCounter {
    // BYD Statistic read-only API, also used by BYDMate's hybrid reader.
    static final int DEVICE = 1014;
    static final int FID = 1246760976;
    static final int TRANSACTION = 7;
    private static final double MAX_TRIP_LITRES = 60.;
    private static final Pattern REPLY = Pattern.compile(
            "Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000\\s+([0-9a-fA-F]{8})(?![0-9a-fA-F])");

    Double startLitres, endLitres;
    boolean invalid;
    private boolean started;
    private Double previous;

    static Double readReply(String reply) {
        if (reply == null) return null;
        Matcher match = REPLY.matcher(reply);
        if (!match.find()) return null;
        return decode((int) Long.parseLong(match.group(1), 16));
    }

    static Double decode(int raw) {
        if (raw == 0xffff || raw == 0xfffff || raw == Integer.MAX_VALUE) return null;
        double value = Float.intBitsToFloat(raw);
        // Zero is returned on unsupported cars, and raw integer sentinels may be subnormal floats.
        return Double.isFinite(value) && value >= .05 && value <= 10000000 ? value : null;
    }

    void sample(Double total) {
        if (!started) {
            startLitres = total;
            started = true; // A missing opening edge cannot be supplied by a later reading.
        }
        if (total != null && previous != null && total < previous) invalid = true;
        if (total != null) previous = total;
        endLitres = total; // A failed closing read must not reuse a stale earlier value.
        if (startLitres != null && total != null && total - startLitres > MAX_TRIP_LITRES) invalid = true;
    }

    void loseClosingEdge() { endLitres = null; }

    Double litres() { return invalid ? null : difference(startLitres, endLitres); }

    static Double difference(Double start, Double end) {
        if (start == null || end == null || !Double.isFinite(start) || !Double.isFinite(end)
                || start < .05 || end < .05) return null;
        double delta = end - start;
        return delta >= 0 && delta <= MAX_TRIP_LITRES ? Math.round(delta * 10) / 10. : null;
    }
}
