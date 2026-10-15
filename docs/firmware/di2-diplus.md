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
