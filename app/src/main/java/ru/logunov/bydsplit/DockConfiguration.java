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
    /** Stable value IDs support both built-in functions and installed application components. */
    static final class Item {
        private static int nextOrdinal;
        static final Item SONG=new Item("SONG","BYD SONG", R.drawable.dock_ui7_song);
        static final Item CAMERA=new Item("CAMERA","Камеры", R.drawable.dock_ui7_camera);
        static final Item MUSIC=new Item("MUSIC","Музыка", R.drawable.dock_ui7_music);
        static final Item MESSAGES=new Item("MESSAGES","Сообщения", R.drawable.dock_ui7_message);
        static final Item SETTINGS=new Item("SETTINGS","Настройки", R.drawable.dock_ui7_settings);
        static final Item DRIVER_HEAT=new Item("DRIVER_HEAT","Подогрев водителя", R.drawable.song_oem_heat_icon, ComfortState.Field.DRIVER_HEAT);
        static final Item PASSENGER_HEAT=new Item("PASSENGER_HEAT","Подогрев пассажира", R.drawable.song_oem_heat_icon, ComfortState.Field.PASSENGER_HEAT);
        static final Item DRIVER_VENT=new Item("DRIVER_VENT","Вентиляция водителя", R.drawable.song_oem_vent_icon, ComfortState.Field.DRIVER_VENT);
        static final Item PASSENGER_VENT=new Item("PASSENGER_VENT","Вентиляция пассажира", R.drawable.song_oem_vent_icon, ComfortState.Field.PASSENGER_VENT);
        static final Item MIRRORS=new Item("MIRRORS","Зеркала и стекло", R.drawable.song_oem_mirrors_icon, ComfortState.Field.REAR);
        static final Item WHEEL=new Item("WHEEL","Подогрев руля", R.drawable.ui7_steering_heat, ComfortState.Field.WHEEL);
        static final Item CLIMATE=new Item("CLIMATE","Климат", R.drawable.ui7_climate);
        static final Item SEATS=new Item("SEATS","Сиденья", R.drawable.ui7_seat);
        static final Item POWER=new Item("POWER","Климат вкл/выкл", R.drawable.song_oem_ac_power_on, ComfortState.Field.POWER);
        static final Item AUTO=new Item("AUTO","Автоклимат", R.drawable.song_oem_control_mode, ComfortState.Field.AUTO);
        static final Item AC=new Item("AC","A/C", R.drawable.song_oem_ac_compressor, ComfortState.Field.COMPRESSOR);
        static final Item FRONT=new Item("FRONT","Лобовое стекло", R.drawable.song_oem_ac_front_defrost, ComfortState.Field.FRONT);
        static final Item CYCLE=new Item("CYCLE","Рециркуляция", R.drawable.song_oem_internal_cycle_mode_had, ComfortState.Field.CYCLE);
        static final Item MAX_COOL=new Item("MAX_COOL","Макс. охлаждение", R.drawable.song_oem_ac_max, ComfortState.Field.MAX);
        static final Item FAN_DOWN=new Item("FAN_DOWN","Вентилятор −", R.drawable.song_oem_ac_wind_level_min_normal, ComfortState.Field.FAN);
        static final Item FAN_UP=new Item("FAN_UP","Вентилятор +", R.drawable.song_oem_ac_wind_level_max_normal, ComfortState.Field.FAN);
        static final Item PHONE=new Item("PHONE","Телефон · ICCOA (экспериментально)", R.drawable.dock_ui7_phone);
        static final Item EV_RANGE=new Item("EV_RANGE","Запас хода EV · виджет", R.drawable.ui7_ev_range);
        private static final Item[] BUILT_INS={SONG,CAMERA,MUSIC,MESSAGES,SETTINGS,DRIVER_HEAT,PASSENGER_HEAT,DRIVER_VENT,PASSENGER_VENT,MIRRORS,WHEEL,CLIMATE,SEATS,POWER,AUTO,AC,FRONT,CYCLE,MAX_COOL,FAN_DOWN,FAN_UP,PHONE,EV_RANGE};
        final String title; final int icon; final ComfortState.Field field; final int slots;
        final android.graphics.drawable.Drawable appIcon;
        private final String id;private final int ordinal;
        private Item(String id,String title,int icon){this(id,title,icon,null);}
        private Item(String id,String title,int icon,ComfortState.Field field){
            this.id=id;this.title=title;this.icon=icon;this.field=field;
            this.slots=id.equals("EV_RANGE")?3:1;this.ordinal=nextOrdinal++;this.appIcon=null;
        }
        private Item(String component,String title,android.graphics.drawable.Drawable icon){
            this.id="APP:"+component;this.title=title;this.appIcon=icon;
            this.icon=android.R.drawable.sym_def_app_icon;this.field=null;this.slots=1;this.ordinal=1000;
        }
        static Item app(AppEntry entry){return new Item(entry.component.flattenToString(),entry.label,entry.icon);}
        static Item[] values(){return BUILT_INS.clone();}
        static Item valueOf(String id){
            for(Item item:BUILT_INS)if(item.id.equals(id))return item;
            if(id.startsWith("APP:")&&id.substring(4).matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")){
                android.content.ComponentName component=android.content.ComponentName.unflattenFromString(id.substring(4));
                if(component!=null)return new Item(component.flattenToString(),component.getPackageName(),null);
            }
            throw new IllegalArgumentException("Unknown dock item");
        }
        boolean isApp(){return id.startsWith("APP:");}
        android.content.ComponentName component(){return isApp()?android.content.ComponentName.unflattenFromString(id.substring(4)):null;}
        String name(){return id;}int ordinal(){return ordinal;}
        boolean required(){return this==SONG||this==SETTINGS;}
        @Override public boolean equals(Object other){return other instanceof Item&&id.equals(((Item)other).id);}
        @Override public int hashCode(){return id.hashCode();}
        String caption(){
            if(this==EV_RANGE)return "Запас EV";
            if(this==PHONE)return "Телефон β";
            if(this==DRIVER_HEAT)return "Подогр. вод.";
            if(this==PASSENGER_HEAT)return "Подогр. пас.";
            if(this==DRIVER_VENT)return "Вент. вод.";
            if(this==PASSENGER_VENT)return "Вент. пас.";
            if(this==MIRRORS)return "Зеркала";
            if(this==WHEEL)return "Руль";
            if(this==POWER)return "Вкл/выкл";
            if(this==AUTO)return "AUTO";
            if(this==FRONT)return "Стекло";
            if(this==CYCLE)return "Рецирк.";
            if(this==MAX_COOL)return "MAX A/C";
            if(this==FAN_DOWN)return "Обдув −";
            if(this==FAN_UP)return "Обдув +";
            return title;
        }
    }
    static boolean dynamic(Context c){return AppPreferences.get(c).getBoolean(MODE,false);}
    static List<Item> read(Context c){
        List<Item> items=decode(AppPreferences.get(c).getString(ITEMS,"SONG,CAMERA,MUSIC,MESSAGES,SETTINGS"));
        AppRepository repository=new AppRepository(c);
        for(int i=0;i<items.size();i++)if(items.get(i).isApp()){
            AppEntry app=repository.resolve(items.get(i).component());if(app!=null)items.set(i,Item.app(app));
        }
        return items;
    }
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
