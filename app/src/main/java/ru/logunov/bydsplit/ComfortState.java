package ru.logunov.bydsplit;

import java.util.EnumMap;

/** Firmware-independent ranges and the explicit UI-to-SDK command contract. */
final class ComfortState {
    enum Field {
        POWER("AC_POWER_STATE"), AUTO("AC_CTRL_MODE"), COMPRESSOR("AC_COMPRESSOR_MODE"),
        CYCLE("AC_CYCLE_MODE"), FRONT("AC_DEFROST_FRONT_STATE"), REAR("AC_DEFROST_REAR_STATE"),
        DUAL("AC_TEMPCTRL_SEPARATE_STATE"), UNIT("AC_TEMPERATURE_UNIT"),
        DRIVER_TEMP("AC_TEMP_MAIN"), PASSENGER_TEMP("AC_TEMP_DEPUTY"),
        FAN("AC_WIND_LEVEL"), WIND("AC_WIND_MODE"),
        MAX("AC_MAX_COOLING_STATE"), VENT("AC_VENTILATION_STATE"),
        WHEEL("SET_STEERING_WHEEL_HEAT_STATE"),
        DRIVER_HEAT("SET_DRIVER_SEAT_HEATING_STATE"), PASSENGER_HEAT("SET_PASSENGER_SEAT_HEATING_STATE"),
        DRIVER_VENT("SET_DRIVER_SEAT_VENTILATING_STATE"), PASSENGER_VENT("SET_PASSENGER_SEAT_VENTILATING_STATE");
        final String symbol;
        Field(String symbol) { this.symbol = symbol; }
        boolean seat() { return ordinal() >= WHEEL.ordinal(); }
        int comfortAction() { return ordinal() - WHEEL.ordinal(); }
        boolean valid(int value, Integer unit) {
            if (seat()) return value >= 1 && value <= (this == WHEEL ? 2 : 3);
            if (this == DRIVER_TEMP || this == PASSENGER_TEMP)
                return unit != null && (unit == 1 ? value >= 17 && value <= 33 : unit == 0 && value >= 64 && value <= 91);
            if (this == FAN) return value >= 0 && value <= 7;
            if (this == WIND) return value >= 1 && value <= 7;
            return value >= 0 && value <= 1;
        }
    }
    private final EnumMap<Field,Integer> values = new EnumMap<>(Field.class);
    static ComfortState parse(String output) {
        ComfortState state = new ComfortState();
        for (String line : output.split("[\\r\\n]+")) {
            String[] parts = line.trim().split("=");
            if (parts.length != 2) continue;
            try { state.values.put(Field.valueOf(parts[0]), Integer.valueOf(parts[1])); }
            catch (IllegalArgumentException ignored) { /* SDK logs / unsupported fields are not values. */ }
        }
        return state;
    }
    Integer get(Field field) {
        Integer raw = values.get(field);
        return raw != null && field.valid(raw, values.get(Field.UNIT)) ? raw : null;
    }
    ComfortState withRaw(Field field,int raw){
        ComfortState copy=new ComfortState();copy.values.putAll(values);copy.values.put(field,raw);return copy;
    }
    boolean any() { for (Field f : Field.values()) if (get(f) != null) return true; return false; }
    int nextUiValue(Field field) {
        Integer raw=get(field);
        if(raw==null)throw new IllegalArgumentException("Unavailable field");
        if(!field.seat())return raw==0?1:0;
        int level=raw-1;
        return level==0?(field==Field.WHEEL?1:2):level-1;
    }
    static int windMask(int mode){int[] masks={0,1,3,2,6,4,7,5};return mode>=1&&mode<=7?masks[mode]:0;}
    static int windMode(int mask){for(int mode=1;mode<=7;mode++)if(windMask(mode)==mask)return mode;return -1;}
    static final class Command {
        final String method;
        final int[] args;
        final int expected;
        Command(String method, int expected, int... args) { this.method=method;this.expected=expected;this.args=args; }
    }
    /** Same ordered batch as BYDAutoAcDevice on Song's framework. Zero means an untouched temperature zone. */
    String[] acFeatures(Field field) {
        if (field.seat() || field == Field.UNIT) throw new IllegalArgumentException("Not an AC command");
        if (field == Field.DRIVER_TEMP || field == Field.PASSENGER_TEMP)
            return new String[]{"AC_TEMPERATURE_UNIT_SET", "AC_TEMP_MAIN_SET", "AC_TEMP_DEPUTY_SET", "AC_TEMP_REAR_SET", "AC_CTRL_SOURCE_SET"};
        if (field == Field.MAX) return new String[]{field.symbol + "_SET"};
        return new String[]{field.symbol + "_SET", "AC_CTRL_SOURCE_SET"};
    }
    int[] acValues(Field field, int value) {
        Command command = command(field, value);
        if (field == Field.DRIVER_TEMP || field == Field.PASSENGER_TEMP)
            return new int[]{get(Field.UNIT), field == Field.DRIVER_TEMP ? value : 0, field == Field.PASSENGER_TEMP ? value : 0, 0, 0};
        if (field == Field.MAX) return new int[]{command.expected};
        return new int[]{command.expected, 0};
    }
    Command command(Field field, int uiValue) {
        if (get(field) == null || field == Field.UNIT) throw new IllegalArgumentException("Unavailable field");
        int raw = field.seat() ? uiValue + 1 : uiValue;
        if (!field.valid(raw, get(Field.UNIT))) throw new IllegalArgumentException("Invalid value");
        switch (field) {
            case MAX: return new Command("setAcMaxCoolingState",raw,raw);
            case VENT: return new Command("setAcVentilationState",raw,0,raw);
            case POWER: return new Command(raw == 1 ? "start" : "stop", raw, 0);
            case AUTO: return new Command("setAcControlMode",raw,0,raw);
            case COMPRESSOR: return new Command("setAcCompressorMode",raw,0,raw);
            case CYCLE: return new Command("setAcCycleMode",raw,0,raw);
            case FRONT: return new Command("setAcDefrostState",raw,0,1,raw);
            case REAR: return new Command("setAcDefrostState",raw,0,2,raw);
            case DUAL: return new Command("setAcTemperatureControlMode",raw,0,raw);
            case FAN: return new Command("setAcWindLevel",raw,0,raw);
            case WIND: return new Command("setAcWindMode",raw,0,raw);
            case DRIVER_TEMP: case PASSENGER_TEMP:
                return new Command("setAcTemperature",raw,field == Field.DRIVER_TEMP ? 1 : 2,raw,0,get(Field.UNIT));
            default: return new Command("comfort",raw,field.comfortAction(),uiValue);
        }
    }
}
