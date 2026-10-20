package ru.logunov.bydsplit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only cumulative BMS energy. Missing boundaries never turn into zero consumption. */
final class TripEnergyCounter {
    static final int DEVICE = 1014, FID = 1032871984, TRANSACTION = 7;
    private static final Pattern REPLY = Pattern.compile(
            "Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000\\s+([0-9a-fA-F]{8})(?![0-9a-fA-F])");
    Double startKwh, endKwh;
    boolean invalid;
    private boolean started;
    private Double previous;

    static Double readReply(String reply) {
        Matcher m = REPLY.matcher(reply == null ? "" : reply);
        if (!m.find()) return null;
        int raw = (int) Long.parseLong(m.group(1), 16);
        if (raw == 65535 || raw == 1048575 || raw == Integer.MAX_VALUE) return null;
        double value = Float.intBitsToFloat(raw);
        return Double.isFinite(value) && value >= .01 && value <= 10000000 ? value : null;
    }
    void sample(Double value) {
        if (!started) { startKwh = value; started = true; }
        if (value != null && previous != null && value < previous) invalid = true;
        if (value != null) previous = value;
        endKwh = value;
    }
    void loseClosingEdge() { endKwh = null; }
    static Double difference(Double start, Double end, boolean invalid, long durationMs) {
        if (invalid || start == null || end == null || !Double.isFinite(start) || !Double.isFinite(end)
                || start < 0 || end < 0 || durationMs < 0) return null;
        double delta = end - start;
        // A generous 600 kW ceiling plus 1 kWh for quantization; rejects corrupt jumps.
        return delta >= 0 && delta <= durationMs / 3600000. * 600 + 1 ? delta : null;
    }
}
