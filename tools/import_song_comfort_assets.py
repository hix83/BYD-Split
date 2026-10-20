#!/usr/bin/env python3
"""Import the owner's installed Song AirConditioning APK artwork without bitmap edits.
Usage: python3 tools/import_song_comfort_assets.py /path/to/AirConditioning.apk
Lottie icon Bezier paths are flattened at their first frame into Android vectors.
"""
import json, math, pathlib, sys, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/res'
APK=zipfile.ZipFile(sys.argv[1])
NAMES=['seat_bg_default','driver_seat_default','driver_seat_heat_one','driver_seat_heat_two','driver_seat_vent_one','driver_seat_vent_two','passenger_seat_default','rear_left_seat_default','rear_right_seat_default','steer_wheel','steer_wheel_heat','rear_mirror_heat_state']
(ROOT/'drawable-xhdpi').mkdir(exist_ok=True)
for name in NAMES:
    (ROOT/'drawable-xhdpi'/f'song_oem_{name}.webp').write_bytes(APK.read(f'res/drawable-xhdpi-v4/{name}.webp'))
def value(v,default):
    if not v:return default
    k=v.get('k',default)
    return k[0]['s'] if v.get('a') else k
# Matrix (a,b,c,d,tx,ty).
def mul(a,b):
    return (a[0]*b[0]+a[2]*b[1],a[1]*b[0]+a[3]*b[1],a[0]*b[2]+a[2]*b[3],a[1]*b[2]+a[3]*b[3],a[0]*b[4]+a[2]*b[5]+a[4],a[1]*b[4]+a[3]*b[5]+a[5])
def transform(k):
    p=value(k.get('p'),[0,0]);anchor=value(k.get('a'),[0,0]);s=value(k.get('s'),[100,100]);r=value(k.get('r'),0)
    if isinstance(r,list):r=r[0]
    c=math.cos(math.radians(r));sn=math.sin(math.radians(r));m=(c*s[0]/100,sn*s[0]/100,-sn*s[1]/100,c*s[1]/100,p[0],p[1])
    return mul(m,(1,0,0,1,-anchor[0],-anchor[1]))
def point(m,p):return (m[0]*p[0]+m[2]*p[1]+m[4],m[1]*p[0]+m[3]*p[1]+m[5])
def fmt(p):return ','.join(f'{v:.4f}'.rstrip('0').rstrip('.') for v in p)
def bezier(shape,m):
    v=shape['v'];i=shape['i'];o=shape['o'];out=['M'+fmt(point(m,v[0]))]
    for n in range(1,len(v)+(1 if shape.get('c') else 0)):
        a=(n-1)%len(v);b=n%len(v)
        cp1=[v[a][j]+o[a][j] for j in range(2)];cp2=[v[b][j]+i[b][j] for j in range(2)]
        out.append('C'+fmt(point(m,cp1))+','+fmt(point(m,cp2))+','+fmt(point(m,v[b])))
    if shape.get('c'):out.append('Z')
    return ''.join(out)
def shapes(items,m,paths):
    tr=next((s for s in items if s['ty']=='tr'),{})
    m=mul(m,transform(tr))
    compound=[]
    for item in items:
        if item['ty']=='gr':shapes(item['it'],m,paths)
        elif item['ty']=='sh':compound.append(bezier(value(item['ks'],None),m))
    if compound:
        fill=next((item for item in items if item['ty']=='fl'),None)
        stroke=next((item for item in items if item['ty']=='st'),None)
        width=value(stroke.get('w'),1)*math.hypot(m[0],m[1]) if stroke else 0
        paths.append((''.join(compound),bool(fill),width))
def vector(source,dest):
    doc=json.loads(APK.read('assets/'+source));assets={a['id']:a for a in doc.get('assets',[])};paths=[]
    def layers(items,parent):
        by_id={layer['ind']:layer for layer in items}
        def local(layer):
            own=transform(layer.get('ks',{}))
            return mul(local(by_id[layer['parent']]),own) if 'parent' in layer else own
        result=[]
        for layer in reversed(items):
            if layer.get('nm') in ['01','02','03'] or layer.get('nm','').startswith('ON '):continue
            m=mul(parent,local(layer));fragment=[]
            if layer['ty']==0:fragment=layers(assets[layer['refId']]['layers'],m)
            elif layer['ty']==4:
                raw=[];shapes(layer.get('shapes',[]),m,raw)
                fragment=[f'    <path android:fillColor="{"#FFFFFFFF" if fill else "#00000000"}" android:strokeColor="{"#FFFFFFFF" if width else "#00000000"}" android:strokeWidth="{width:.4f}" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{p}" />\n' for p,fill,width in raw]
            masks=layer.get('masksProperties',[])
            if masks:
                clip=bezier(value(masks[0]['pt'],None),m)
                fragment=['<group><clip-path android:pathData="'+clip+'" />\n']+fragment+['</group>\n']
            result.extend(fragment)
        return result
    paths=layers(doc['layers'],(1,0,0,1,0,0))
    xml=f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Bezier paths from the installed Song OEM icon; live levels are separate. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{doc['w']}dp" android:height="{doc['h']}dp" android:viewportWidth="{doc['w']}" android:viewportHeight="{doc['h']}">
'''
    xml+=''.join(paths)+'</vector>\n'
    (ROOT/'drawable'/dest).write_text(xml)
vector('icon_Seat_heat_1-0_max_level_3_night.json','song_oem_heat_icon.xml')
vector('icon_Seat_ventilation_1-0_max_level_3_night.json','song_oem_vent_icon.xml')

vector('icon_rear_window_rearview_mirror_heat_close_night.json','song_oem_mirrors_icon.xml')

# Climate panel: unchanged source images, including OEM day/night variants.
climate=['wind_mode_background.webp','ac_temp_pick_minus.png','ac_temp_pick_plus.png','ac_power_on.png','control_mode.png','ac_compressor.png','ac_max.png','ac_front_defrost.png','internal_cycle_mode_had.png','out_cycle_mode_had.png','ac_wind_level_min_normal.png','ac_wind_level_max_normal.png','wind_mode_defrost_thumb_gray.png','wind_mode_face_thumb_gray.png','wind_mode_foot_thumb_gray.png']
for zone in ['defrost','face','foot']:
    climate.append(f'wind_mode_{zone}_off.webp')
    climate += [f'wind_mode_{zone}_animation_{frame:02}.png' for frame in [0]]
for name in climate:
    for variant,source in [('drawable-xhdpi','drawable-xhdpi-v4'),('drawable-night-xhdpi','drawable-night-xhdpi-v8')]:
        path=f'res/{source}/{name}'
        if path in APK.namelist():
            (ROOT/variant).mkdir(exist_ok=True)
            (ROOT/variant/f'song_oem_{name}').write_bytes(APK.read(path))

vector('ac_ventilation_en.json','song_oem_vent_only_icon.xml')
