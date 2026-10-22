# Проверка DiLink 2 и Di+ — 15 октября 2026

На подключённой головной системе: Android 9 / API 28, MT6765, k65v1_64_bsp, экран 1280×720, плотность 160. Это измерение устройства, а не расчёт по диагонали.

## Основной источник данных

По запросу владельца используется тот же источник, что в hix83/BYDMate-DiLink2-Seagull, ветка dilink2-seagull: установленный Di+ (`com.van.diplus`), HTTP `http://127.0.0.1:8988/api/getDiPars?text=...`, ответ `{success:true,val:"SOC:...|Speed:..."}`. DiPlusReader формирует фиксированный шаблон, DiPlusValues проверяет диапазоны и единицы. Никаких команд Di+ этот reader не отправляет.

На устройстве подтверждены SOC (81%, затем 87% при зарядке), скорость 0 км/ч, пробег raw 472980 → 47298 км, режим ECO, мощность −16 кВт и параметры климата. Числа только описывают момент проверки. Давление 0, напряжение 0 и полностью нулевые температуры батареи не отображаются как исправные датчики. Неподдерживаемые шаблоны, sentinel, NaN и значения вне диапазонов отбрасываются. SOH / обороты / дополнительные параметры пока не найдены этим способом.

Открытый HTTP разрешён network-security-config только для 127.0.0.1. Внешние соединения остаются HTTPS. Если Di+ недоступен, пробуется SDK и сохраняется причина недоступности в диагностике; вымышленные данные не подставляются. Приложение Di+ должно быть установлено и его локальный сервис запущен.

## SDK и разрешения

`BYDAUTO_SPEED_COMMON`, `STATISTIC_COMMON`, `TYRE_COMMON`, `AC_COMMON` являются runtime-разрешениями на этой машине. APK теперь запрашивает их; они успешно выданы через pm grant. Однако getter SDK дополнительно требует signature-разрешения `BYDAUTO_*_GET`. Наличие COMMON не даёт доступа GET, поэтому прямой SDK не подтверждён как рабочий источник на этой прошивке. Климат через прямой SDK остаётся ограничен этими проверками. Старые Binder-адреса Di5 не перенесены.

## Помощник виртуальных экранов

Установленный APK создаёт собственную пару ADB-ключей. Через уже авторизованный shell можно разрешить только его публичный ключ:

```sh
./tools/authorize-local-adb.sh SERIAL
```

Помощник использует штатный `IUsbManager.allowUsbDebugging`; shell имеет MANAGE_USB. Если ОС запрещает вызов, ошибка сохраняется: root, изменение файлов adb_keys и перенос приватного ключа компьютера не используются. Ключ приложения сохраняется при обновлении APK. На устройстве после авторизации и перезапуска подтверждён `displayReady=true`.

Диагностика только для shell/root:

```sh
adb -s SERIAL shell content call --uri content://ru.logunov.bydsplit.display --method diagnostics
```

Возвращает готовность помощника и текущий источник/данные. Это не открытый интерфейс для посторонних APK. Необновление левой поверхности при переключении Музыки не изменялось; чёрная карта сама по себе ещё не доказывает проблему транспорта ввода.

## SOH and battery supplement, verified 2026-10-15

The newer BYDMate branch `dilink2-seagull-mac` adds a read-only on-device
ADB `autoservice` supplement to Di+. References:
- https://github.com/hix83/BYDMate-DiLink2-Seagull/blob/dilink2-seagull-mac/app/src/main/kotlin/com/bydmate/app/data/platform/PlatformParsReader.kt
- https://github.com/hix83/BYDMate-DiLink2-Seagull/blob/dilink2-seagull-mac/app/src/main/kotlin/com/bydmate/app/data/autoservice/FidRegistry.kt
- https://github.com/hix83/BYDMate-DiLink2-Seagull/blob/dilink2-seagull-mac/app/src/main/kotlin/com/bydmate/app/data/autoservice/AutoserviceClient.kt

Read addresses independently checked on physical DiLink 2 via the user's
`127.0.0.1:5038` tunnel (not imported blindly from Di5):

| Signal | Transaction | Device | FID | Observed value |
|---|---:|---:|---:|---|
| SOH | 5/int | 1014 | 1145045032 | 97% |
| Battery minimum temperature | 5/int | 1014 | 1148190736 | 25 °C |
| Battery maximum temperature | 5/int | 1014 | 1148190752 | 28 °C |
| Auxiliary voltage | 7/float | 1001 | 1128267816 | 13.8 V |
| Minimum cell voltage | 7/float | 1014 | 1147142160 | about 3.302 V |
| Maximum cell voltage | 7/float | 1014 | 1147142192 | about 3.343 V |

Temperatures are actual Celsius: **no -40 offset**. Float return values are
IEEE754 words in the Parcel, not decimal integers. Failed Parcels and invalid
values are omitted. Zero Celsius is valid from Binder (unlike Di+'s known
zero placeholders). The supplement refreshes every 30 seconds through a
dedicated local ADB connection and fills only missing Di+ fields. Failed
refreshes clear cached supplementary values rather than retaining stale data.
Cell voltages are also connected to battery details. No vehicle write calls.

After installation, the app's guarded diagnostics confirmed
`batterySohPercent:97`, `batteryMinTempC:25`, `batteryMaxTempC:28`,
`auxiliaryVoltage:13.800000190734863`, cell voltages about 3320/3332 mV,
`batterySource:autoservice Di2`, `displayReady:true`. Thus the on-device
app ADB path was checked as well as host ADB. Build, lint and logic tests passed.

## Motor RPM and native climate, 2026-10-15

The installed Di+ supports `MotorRpm:{前电机转速}` and
`Wind:{空调出风模式}`. Live requests returned RPM -2998, -709, then 0
at standstill (`Speed:0`, `Gear:1`); wind mode 5, fan level 4. Inspection
of the installed APK confirmed these templates reference front motor speed
and BYD AC wind mode, respectively. Di+ explicitly negates its incoming
front-motor-speed event; preserve the signed source reading rather than
inventing an offset or deriving RPM from vehicle speed. Accept ±20000 RPM;
reject sentinels/unresolved templates. Main dashboard and technical page
receive `motorRpm` through the common vehicle snapshot.

Direct Binder candidates read from this vehicle's SDK metadata:
`ENGINE_FRONT_MOTOR_SPEED=1141901320`, device engine 1012. tx5 returned
-10011 and tx7 returned -1f, so this route is unavailable; do not use these
sentinels as RPM. `ENGINE_SPEED` is not a validated electric motor source.

Native climate resolver verified from `dumpsys package com.byd.airconditioning`:
`OPEN_AIR_CONDITIONING` →
`com.byd.airconditioning/.mainactivity.FullScreenMainActivity`.
The climate shortcut now opens this activity, retaining `ClimateIconRenderer`
and live Di+ fan/wind values. BYD SDK AC enum values 1–7 were independently
checked against the installed framework and match `ClimateIconState`.
The unavailable MyCar shortcut has been removed from the Seagull quick row;
remaining actions: climate, vehicle settings, home, technical details.

On-device shortcut tap verified: first quick button opens the OEM climate
activity with populated controls (fan level 4, defrost airflow), not an empty
screen. Native climate controls were not changed during this check.


## Pedal percentages (22 October 2026)

Di+ parameter names from owner's beta20 RU APK: `{加速踏板深度}` (accelerator)
and `{刹车深度}` (brake). DiPlusReader requests Accelerator/Brake, parser emits
accelerator/brake, and the map-based technical snapshot retains them as 0..100%.
SDK fallback reads BYDAutoSpeedDevice.getAccelerateDeepness/getBrakeDeepness.
These are getters; no pedal or powertrain commands are issued. No native Di5
pedal FIDs were reused for Di2. Tests use fixtures; live pedal response remains
to be checked when the Seagull is connected. Unsupported replies show “—”.


Live confirmation, 22 October: alpha.6/code42 installed; guarded diagnostics
include accelerator/brake. Parked manual test Brake 59/87%, Accelerator99%,
both return to0, speed0 throughout. User confirmed display response.


## Individual BMS measurements and thermal data — 22 October 2026

Reference mechanism: owner's uncommitted Song BatteryCellBridge/Readings in
the parent checkout (read only, parent files unchanged). Seagull independently
verified by a bounded GB subscription: autoservice dev1039, event -1728053217.
BODYWORK_BATTERY_SINGLE_NUM=94 (dev1001, FID1134559240),
GB_BATTERY_PROBE_NUM=7 (dev1039, FID1134559248). SDK constants must be
resolved after ActivityThread.systemMain/system context initialization: the
OEM model mapping rewrites inherited feature constants. Metadata alone before
initialization can show default FIDs shifted by2048. No default ID scan used.

Di2 GB CAN envelope is14 bytes: big-endian CAN ID4, bus2, payload8; there is
no timestamp field. This differs from the Song18-byte/bus2 profile.
For captured Seagull BMS frames bus=0. Payload byte7 is checksum: sum of all
8 payload bytes modulo256=255. 0x43d: index0..31, three LE uint16 mV
per frame; final frame contains cell94 and two ffff pads. 0x43c: index0..1,
six unsigned byte temperatures per frame, subtract40 C; final frame has
probe7 and five ff pads. Summary indices are ignored, not treated as cells.
Five complete94-cell sweeps measured3270..3275mV (4..5mV spread), all7
probes12C. Numbering is BMS order, not a claimed physical layout. Captured
fixture tests checksum, missing/out-of-order frames, padding, sentinel/ranges,
slow/incomplete sweeps and freshness. Unsupported pack counts are rejected.

Read-only scalar signals confirmed on this Di2 (tx5=int unless stated):
- motor19C: dev1039/FID1154482192 (GB_FRONT_MOTOR_TEMP);
- inverter17C, later16C: dev1039/FID1154482184 (GB_FRONT_MOTOR_IPM_TEMP);
- HV308V: dev1009/FID1145045000; agrees with94*~3.274V;
- current~0.4..0.5A: tx7 float/dev1039/FID1151336480;
- insulation6934, later6085kOhm: dev1039/FID1134559256,
  display6.934/6.085MOhm (same native GB contract/scale as Song).
No Di5 temperature offsets applied. Missing float -1 and error/sentinel
replies are rejected. PowerHV=V*A in watts; SOC/hour is an estimate from
this power and manually configured capacity, not a direct BMS measurement.

Individual measurements are collected only while the battery/technical page
is active, using a separate local ADB connection. Each subscription ends
on a complete sweep or8.5s timeout and unregisters in finally; no CAN/vehicle
write commands, UDS requests or monitoring service added. Age limit10s;
partial/stale sets never become a chart. Voltage axis is explicitly zoomed
and labelled in mV. Bar tap shows numbered measurement and exact voltage.
Technical BMS limit cards removed at owner's request; insulation, power and
SOC/hour retained. New charts show94 voltages and7 temperatures.


## Climate telemetry / alpha.9 — 22 October 2026

Owner confirms measured cabin and outdoor temperatures are unavailable on
this Seagull. Live Di+ replies InsideTemp=-2000, ExtTemp=-128 even after
manual climate activation. Instrument.getOutCarTemperature path
(dev1007/FID1246769216) returns195, SDK TEMPERATURE_INVALID. Do not divide
this sentinel or map target temperature into cabin temperature. Related
BYDMate fork deliberately aliases target into cabin for its UI; this project
does not copy that alias. SDK getTemprature(3) is REAR TARGET, not a cabin
sensor; removed that probe and corrected the previous SDK comment. The old
insideRaw documentation above is historical and must not be used as a sensor.

Read-only native A/C mode verified tx5/dev1000/FID1077936137:
0=off,1=on, per initialized Di2 AC_COMPRESSOR_MODE and SDK enum. This
is A/C mode status, not compressor watts/RPM or proof of mechanical rotation.
Separate climate power remains Di+ ACStatus (native FID1077936144).
Before manual test climate0/fan0; after user toggled A/C: climate1/fan7,
A/Cmode0, target27C, wind4 (glass+feet), recirculation0 (outside air).
The intermediate A/C-on edge was not captured; parser regression covers
both boolean states and missing/error replies.

Technical climate cards now show climate power, A/C mode, target temperature,
fan0..7, airflow and recirculation. Removed unavailable cabin/outdoor cards
at owner's direction. Current map-based TechSnapshot explicitly propagates
validated climate enums; missing is not off, target is never a sensor.
Release alpha.9/code45 uses permanent key; build,lint,logic tests passed.
