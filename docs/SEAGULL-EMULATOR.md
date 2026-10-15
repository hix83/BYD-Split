# Эмулятор BYD Seagull / Android 9

## Что удалось установить

Штатный поворотный экран Seagull — **10,1 дюйма**, а не 10,8.
Это подтверждено владельцем и [спецификацией BYD](https://www.byd.com/content/dam/byd-site/co/product/seagull/flyer/BYD-SEAGULL-flyer-ES-20231229.pdf).

В найденной официальной спецификации нет точного разрешения и логической
плотности Android. В [документации другого проекта владельца для DiLink 2](https://github.com/hix83/BYDMate-DiLink2-Seagull/blob/dilink2-seagull/docs/DILINK2_HANDOFF.md)
описана проверка эмулятора Android 9 при **1920×1080**. Это полезная исходная
конфигурация для разработки, но не измерение штатного дисплея.

## Созданная конфигурация

| Параметр | Значение |
| --- | --- |
| AVD | `BYD_Seagull_DiLink2_API28` |
| Система | AOSP Android 9, API 28, без Google Play Services |
| Образ | `system-images;android-28;default;x86_64` |
| Экран | 1920×1080, landscape — предварительный профиль |
| Логическая плотность | 160 dpi — рабочее предположение, уточнить через ADB |
| Оперативная память / CPU | 2048 МБ / 4 ядра — ресурсы эмулятора |
| Данные | 4 ГБ |
| ADB | `emulator-5560` |

Диагональ 10,1″ описывает физический экран автомобиля. Физический PPI
нельзя подставлять вместо `wm density`: Android использует логическую
плотность для размеров интерфейса. Размер окна на мониторе компьютера
также не воспроизводит физическую диагональ автомобильного дисплея.

Это обычный AOSP, без прошивки DiLink, автомобильных сервисов, CAN,
штатного климата и приборки. Подходит для проверки интерфейса и API 28;
совместимость автомобильных функций подтверждается на машине.

## Запуск в PowerShell

```powershell
.\tools\seagull-emulator.ps1
# Установить уже собранный debug APK и открыть демонстрационный режим:
.\tools\seagull-emulator.ps1 -InstallApp
```

SDK определяется из `ANDROID_HOME`, `ANDROID_SDK_ROOT` или `local.properties`.
При первом создании нужен действующий `JAVA_HOME` (JDK 17), либо параметр
`-JavaHome 'C:/path/to/jdk-17'`. Можно передать `-SdkRoot 'D:/Android/Sdk'`.
Режим `-SetupOnly` только создаёт AVD. Существующий профиль не перезаписывается.
Логи запуска сохраняются в игнорируемом каталоге `tmp/seagull-emulator`.

## Уточнение по машине

На реальном автомобиле выполнить с явно указанным ADB serial:

```text
adb -s SERIAL shell wm size
adb -s SERIAL shell wm density
adb -s SERIAL shell getprop ro.build.version.release
adb -s SERIAL shell getprop ro.build.version.sdk
adb -s SERIAL shell dumpsys display
```

После получения значений скорректировать `hw.lcd.width`, `hw.lcd.height`
и `hw.lcd.density` в config.ini выключенного AVD и шаблон скрипта.
Системные панели AOSP отличаются от DiLink; доступную область окна
также нужно проверить на автомобиле.
