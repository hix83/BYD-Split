package ru.logunov.bydsplit;

/** BYDAutoAcDevice enums: wind modes are an enumeration, not a bitmask. */
final class ClimateIconState {
    static final ClimateIconState UNKNOWN = new ClimateIconState(0, false, false, false);
    final int fanLevel;
    final boolean face, feet, glass;

    private ClimateIconState(int fanLevel, boolean face, boolean feet, boolean glass) {
        this.fanLevel = fanLevel;
        this.face = face;
        this.feet = feet;
        this.glass = glass;
    }

    static ClimateIconState decode(int power, int fan, int mode) {
        // Missing/off readings must not look like active airflow.
        if (power != 1) return UNKNOWN;
        int level = fan >= 0 && fan <= 7 ? fan : 0;
        return new ClimateIconState(level,
                mode == 1 || mode == 2 || mode == 6 || mode == 7,
                mode == 2 || mode == 3 || mode == 4 || mode == 6,
                mode == 4 || mode == 5 || mode == 6 || mode == 7);
    }
}
