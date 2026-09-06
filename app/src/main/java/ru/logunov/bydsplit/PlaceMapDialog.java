package ru.logunov.bydsplit;

import android.app.*;
import android.content.*;
import android.location.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.webkit.*;
import android.widget.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** Local map UI. Network is used only for visible OSM tiles; no JavaScript native bridge. */
final class PlaceMapDialog {
    static void show(Activity activity, CameraPlace existing, Runnable saved) {
        Context c=new ContextThemeWrapper(activity,android.R.style.Theme_Material_NoActionBar);
        LinearLayout form=new LinearLayout(c);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(24,12,24,12);
        EditText name=new EditText(c);name.setSingleLine(true);name.setHint("Название места");name.setText(existing==null?"":existing.name);form.addView(name);
        TextView hint=new TextView(c);hint.setText("Нажмите на карту, чтобы выбрать центр зоны. Масштаб: + / − или жест двумя пальцами.");hint.setPadding(0,8,0,8);form.addView(hint);
        WebView map=new WebView(c);map.setBackgroundColor(0xFF1D252E);map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setAllowFileAccess(false);map.getSettings().setAllowContentAccess(false);
        map.getSettings().setUserAgentString(map.getSettings().getUserAgentString()+" BYDSplit/0.8 PlacePicker");
        map.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}});
        form.addView(map,new LinearLayout.LayoutParams(-1,Math.round(activity.getResources().getDisplayMetrics().heightPixels*.42f)));
        int initial=existing==null?50:Math.round(existing.radiusMeters);
        TextView radiusLabel=new TextView(c);radiusLabel.setText("Радиус: "+initial+" м");form.addView(radiusLabel);
        SeekBar radius=new SeekBar(c);radius.setMax(99);radius.setProgress(Math.max(0,Math.min(99,initial/10-1)));radius.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF2682DF));radius.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFF2682DF));form.addView(radius);
        final int[] meters={initial};radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean u){if(!u)return;meters[0]=(p+1)*10;radiusLabel.setText("Радиус: "+meters[0]+" м");map.evaluateJavascript("setRadius("+meters[0]+")",null);}});
        Button here=new Button(c);here.setText("Моё местоположение");here.setAllCaps(false);form.addView(here);
        here.setOnClickListener(v->{
            if(activity.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},342);Toast.makeText(c,"Разрешите геопозицию и нажмите ещё раз",Toast.LENGTH_SHORT).show();return;}
            Location location=lastLocation(activity);if(location==null){Toast.makeText(c,"Местоположение пока недоступно",Toast.LENGTH_SHORT).show();return;}
            map.evaluateJavascript("pick("+location.getLatitude()+","+location.getLongitude()+",true)",null);
        });
        double lat=existing==null?55:existing.latitude,lon=existing==null?60:existing.longitude;int zoom=existing==null?3:17;
        Location last=lastLocation(activity);if(existing==null&&last!=null){lat=last.getLatitude();lon=last.getLongitude();zoom=16;}
        try {
            String css=read(activity,"maps/leaflet.css"),js=read(activity,"maps/leaflet.js");
            String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>"+css+"html,body,#map{height:100%;margin:0}#error{position:absolute;top:8px;left:60px;z-index:999;background:#fff;color:#222;padding:6px;display:none}</style></head><body><div id='map'></div><div id='error'>Карта недоступна. Проверьте интернет.</div><script>"+js+"\nvar map=L.map('map').setView(["+lat+","+lon+"],"+zoom+");var tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>'}).addTo(map);tiles.on('tileerror',()=>document.getElementById('error').style.display='block');tiles.on('tileload',()=>document.getElementById('error').style.display='none');var selected=null,zone=null,dot=null,radius="+initial+";function pick(lat,lng,focus){selected=[lat,lng];if(zone){zone.setLatLng(selected);dot.setLatLng(selected)}else{zone=L.circle(selected,{radius:radius,color:'#2682df',weight:2,fillOpacity:.18}).addTo(map);dot=L.circleMarker(selected,{radius:5,color:'#fff',weight:2,fillColor:'#2682df',fillOpacity:1}).addTo(map)}if(focus)map.setView(selected,17)}function setRadius(r){radius=r;if(zone)zone.setRadius(r)}map.on('click',e=>pick(e.latlng.lat,e.latlng.lng,false));"+(existing==null?"":"pick("+lat+","+lon+",false);")+"</script></body></html>";
            map.loadDataWithBaseURL("https://appassets.androidplatform.net/bydsplit/maps/",html,"text/html","UTF-8",null);
        } catch(Exception e){hint.setText("Не удалось открыть карту");}
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(existing==null?"Место на карте":"Изменить место").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("Введите название");return;}map.evaluateJavascript("JSON.stringify(selected)",json->{try{Object decoded=new JSONTokener(json).nextValue();JSONArray point=new JSONArray((String)decoded);double x=point.getDouble(0),y=point.getDouble(1);if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>90||Math.abs(y)>180)throw new IllegalArgumentException();List<CameraPlace> places=new ArrayList<>(AppPreferences.getCameraPlaces(activity));if(existing!=null)places.removeIf(p->p.name.equals(existing.name)&&p.latitude==existing.latitude&&p.longitude==existing.longitude);places.add(new CameraPlace(title,x,y,meters[0]));AppPreferences.setCameraPlaces(activity,places);dialog.dismiss();saved.run();}catch(Exception e){Toast.makeText(c,"Выберите точку на карте",Toast.LENGTH_SHORT).show();}});});dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF15181F));dialog.getWindow().setLayout(Math.round(activity.getResources().getDisplayMetrics().widthPixels*.80f),-2);});
        dialog.setOnDismissListener(d->{map.stopLoading();map.destroy();});dialog.show();
    }
    private static Location lastLocation(Activity a){if(a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null;LocationManager m=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);Location best=null;for(String p:m.getProviders(true)){Location l=m.getLastKnownLocation(p);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}return best;}
    private static String read(Context c,String path)throws Exception{try(java.io.InputStream in=c.getAssets().open(path);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
}
