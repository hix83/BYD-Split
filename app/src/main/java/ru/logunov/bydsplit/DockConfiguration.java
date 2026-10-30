package ru.logunov.bydsplit;

import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Stable item IDs leave room for multi-slot widgets without changing saved ordering. */
final class DockConfiguration {
    static final String MODE = "dock_dynamic", ITEMS = "dock_items", ALIGN = "dock_alignment";
    static final int MAX = 20;
    static final String HEIGHT_PERCENT = "dock_height_percent";
    static final int MIN_HEIGHT_PERCENT = 80, MAX_HEIGHT_PERCENT = 150;
    static int heightPercent(Context c) {
        return Math.max(MIN_HEIGHT_PERCENT, Math.min(MAX_HEIGHT_PERCENT,
                AppPreferences.get(c).getInt(HEIGHT_PERCENT, 100)));
    }
    static float scale(Context c) { return heightPercent(c) / 100f; }
    enum Item {
        SONG("BYD SONG", R.drawable.dock_ui7_song),
        CAMERA("Камеры", R.drawable.dock_ui7_camera),
        MUSIC("Музыка", R.drawable.dock_ui7_music),
        MESSAGES("Сообщения", R.drawable.dock_ui7_message),
        SETTINGS("Настройки", R.drawable.dock_ui7_settings),
        DRIVER_HEAT("Подогрев водителя", R.drawable.song_oem_heat_icon, ComfortState.Field.DRIVER_HEAT),
        PASSENGER_HEAT("Подогрев пассажира", R.drawable.song_oem_heat_icon, ComfortState.Field.PASSENGER_HEAT),
        DRIVER_VENT("Вентиляция водителя", R.drawable.song_oem_vent_icon, ComfortState.Field.DRIVER_VENT),
        PASSENGER_VENT("Вентиляция пассажира", R.drawable.song_oem_vent_icon, ComfortState.Field.PASSENGER_VENT),
        MIRRORS("Зеркала и стекло", R.drawable.song_oem_mirrors_icon, ComfortState.Field.REAR),
        WHEEL("Подогрев руля", R.drawable.ui7_steering_heat, ComfortState.Field.WHEEL),
        CLIMATE("Климат", R.drawable.ui7_climate),
        SEATS("Сиденья", R.drawable.ui7_seat),
        POWER("Климат вкл/выкл", R.drawable.song_oem_ac_power_on, ComfortState.Field.POWER),
        AUTO("Автоклимат", R.drawable.song_oem_control_mode, ComfortState.Field.AUTO),
        AC("A/C", R.drawable.song_oem_ac_compressor, ComfortState.Field.COMPRESSOR),
        FRONT("Лобовое стекло", R.drawable.song_oem_ac_front_defrost, ComfortState.Field.FRONT),
        CYCLE("Рециркуляция", R.drawable.song_oem_internal_cycle_mode_had, ComfortState.Field.CYCLE),
        MAX_COOL("Макс. охлаждение", R.drawable.song_oem_ac_max, ComfortState.Field.MAX),
        FAN_DOWN("Вентилятор −", R.drawable.song_oem_ac_wind_level_min_normal, ComfortState.Field.FAN),
        FAN_UP("Вентилятор +", R.drawable.song_oem_ac_wind_level_max_normal, ComfortState.Field.FAN),
        PHONE("Телефон · ICCOA (экспериментально)", R.drawable.dock_ui7_phone),
        EV_RANGE("Запас хода EV · виджет", R.drawable.ui7_ev_range);
        final String title; final int icon; final ComfortState.Field field;
        final int slots;
        Item(String title,int icon){this(title,icon,null);}
        Item(String title,int icon,ComfortState.Field field){this.title=title;this.icon=icon;this.field=field;this.slots="EV_RANGE".equals(name())?3:1;}
        boolean required(){return this==SONG||this==SETTINGS;}
        String caption(){
            switch(this){
                case EV_RANGE:return "Запас EV";
                case PHONE:return "Телефон β";
                case DRIVER_HEAT:return "Подогр. вод.";
                case PASSENGER_HEAT:return "Подогр. пас.";
                case DRIVER_VENT:return "Вент. вод.";
                case PASSENGER_VENT:return "Вент. пас.";
                case MIRRORS:return "Зеркала";
                case WHEEL:return "Руль";
                case POWER:return "Вкл/выкл";
                case AUTO:return "AUTO";
                case FRONT:return "Стекло";
                case CYCLE:return "Рецирк.";
                case MAX_COOL:return "MAX A/C";
                case FAN_DOWN:return "Обдув −";
                case FAN_UP:return "Обдув +";
                default:return title;
            }
        }
    }
    static boolean dynamic(Context c){return AppPreferences.get(c).getBoolean(MODE,false);}
    static List<Item> read(Context c){return decode(AppPreferences.get(c).getString(ITEMS,"SONG,CAMERA,MUSIC,MESSAGES,SETTINGS"));}
    static List<Item> decode(String value){
        LinkedHashSet<Item> items=new LinkedHashSet<>();
        for(String id:value.split(","))try{items.add(Item.valueOf(id));}catch(IllegalArgumentException ignored){}
        items.add(Item.SONG);items.add(Item.SETTINGS);
        List<Item> result=new ArrayList<>(items);
        while(result.size()>MAX){for(int i=result.size()-1;i>=0;i--)if(!result.get(i).required()){result.remove(i);break;}}
        return result;
    }
    static void save(Context c,List<Item> items,int alignment){
        StringBuilder value=new StringBuilder();for(Item item:items){if(value.length()>0)value.append(',');value.append(item.name());}
        AppPreferences.get(c).edit().putString(ITEMS,value.toString()).putInt(ALIGN,Math.max(0,Math.min(2,alignment))).apply();
    }
}
