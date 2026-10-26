package ru.logunov.bydsplit;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Shell-UID read bridge. Opens only a disposable copy, never BYD's live SQLite file. */
public final class NativeEnergyHistoryBridge {
    private static final File SOURCE=new File("/sdcard/energydata/EC_database.db");
    public static void main(String[] args){
        try{System.out.println("BYD_HISTORY="+readRows(SOURCE,new File("/data/local/tmp")));}
        catch(Exception e){System.out.println("BYD_HISTORY_ERROR="+e.getMessage());}
    }
    static JSONArray readRows(File source,File temporaryRoot) throws Exception {
        File dir=null;
        try {
            if(!source.isFile())throw new IOException("Штатный журнал BYD не найден");
            dir=Files.createTempDirectory(temporaryRoot.toPath(),"bydsplit-energy-").toFile();
            File copy=new File(dir,"history.db");boolean stable=false;
            for(int attempt=0;attempt<3&&!stable;attempt++){
                String before=fingerprint(source);
                for(File f:dir.listFiles())if(!f.delete())throw new IOException("snapshot cleanup");
                for(String suffix:new String[]{"","-wal","-shm","-journal"}){
                    File original=new File(source.getPath()+suffix);
                    if(original.isFile())Files.copy(original.toPath(),new File(copy.getPath()+suffix).toPath(),StandardCopyOption.REPLACE_EXISTING);
                }
                stable=before.equals(fingerprint(source));
            }
            if(!stable)throw new IOException("Журнал BYD меняется, повторим чтение позже");
            JSONArray rows=new JSONArray();
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(copy.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
                try(Cursor c=db.rawQuery("PRAGMA quick_check",null)){if(!c.moveToFirst()||!"ok".equals(c.getString(0)))throw new IOException("invalid snapshot");}
                boolean fuel=false;try(Cursor c=db.rawQuery("PRAGMA table_info(EnergyConsumption)",null)){while(c.moveToNext())if("fuel".equals(c.getString(1)))fuel=true;}
                try(Cursor c=db.rawQuery("SELECT _id,start_timestamp,end_timestamp,duration,trip,electricity,"+(fuel?"fuel":"NULL")+",is_deleted FROM EnergyConsumption ORDER BY start_timestamp DESC LIMIT 20000",null)){
                    while(c.moveToNext()){
                        JSONArray row=new JSONArray();for(int i=0;i<8;i++)row.put(c.isNull(i)?JSONObject.NULL:i>=4&&i<=6?c.getDouble(i):c.getLong(i));rows.put(row);
                    }
                }
            }
            return rows;
        }finally{if(dir!=null){File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}}
    }
    private static String fingerprint(File source){StringBuilder s=new StringBuilder();for(String suffix:new String[]{"","-wal","-journal"}){File f=new File(source.getPath()+suffix);s.append(f.exists()).append(':').append(f.length()).append(':').append(f.lastModified()).append(';');}return s.toString();}
}
