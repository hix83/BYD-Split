# Разработка BYD Split · Seagull

Пользовательское описание находится в [README](../README.md).
Этот документ — входная точка для сборки, диагностики и сопровождения ветки.

## Начало работы

Ветка — `seagull`. Исходная база Song сохранена в истории Git;
переносить изменения из main выборочно, проверяя Android 9 / API 28.

- [AGENTS.md](../AGENTS.md) — инструкции для ИИ-агентов и ограничения изменений.
- [HANDOFF-SEAGULL.md](HANDOFF-SEAGULL.md) — результаты проверок, перенос между
  ПК, незавершённые задачи и текст для нового чата. Новые записи внизу
  уточняют исторические записи выше.
- [SEAGULL.md](SEAGULL.md) — исходный план адаптации, не текущая сводка релиза.

## Сборка и проверки

JDK 17, Android SDK Platform 35 / Build Tools 35.0.0, Gradle Wrapper 8.9.
Локальный SDK задаётся в игнорируемом local.properties или окружении.

```powershell
# Windows
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest
& 'C:/Program Files/Git/bin/bash.exe' tools/test_logic.sh
```

```sh
# macOS / Linux
./gradlew assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest
./tools/test_logic.sh
```

JUnit-набор пока отсутствует: testDebugUnitTest выдаёт NO-SOURCE.
Проверки логики выполняет tools/test_logic.sh, проверки интерфейса и ввода —
отдельный instrumentation APK на эмуляторе API28. Инструкции и режимы:
[SEAGULL-EMULATOR.md](SEAGULL-EMULATOR.md).

Для подписанного выпуска: `assembleRelease`. Подключение общего keystore,
публичный отпечаток и ограничения перехода со старой подписи:
[SEAGULL-SIGNING.md](SEAGULL-SIGNING.md).
Приватные ключи и properties с паролями не коммитить.

APK находятся в app/build/outputs/apk/debug/ и app/build/outputs/apk/release/.
CI без общего ключа собирает debug для проверки, его подпись не является
подписью публичного релиза.

## Выпуски

Опубликован alpha.4 / code40; текущая рабочая ветка дополнительно содержит
ожидание ADB, его индикатор и новые настройки внутри основной области.
Эти изменения пока не опубликованы. Создан отдельный постоянный релизный
ключ; переход установленного alpha.4 на него ещё не выполнен.

Правила тегов, имён APK, выбора канала и увеличения versionCode:
[SEAGULL-UPDATES.md](SEAGULL-UPDATES.md). Подробности каждого выпуска —
[docs/releases](releases/). Не использовать общий releases/latest для Seagull.

## Устройство и источники данных

На проверенном автомобиле: Android 9, DiLink 2, MT6765, 1280×720, 160 dpi.
Это результаты измерения конкретного устройства; на другой комплектации
проверять wm size / wm density заново.

- [SDK и происхождение ресурсов OTA](firmware/README.md).
- [ADB-помощники Di2 и климат](firmware/di2-adb.md).
- [Проверки Di+ и данных на автомобиле](firmware/di2-diplus.md).

OTA владельца, распаковки, локальные настройки и диагностические выгрузки
остаются вне Git. Для обычной сборки оригинальные OTA не нужны.

## Основные части проекта

| Задача | Код |
| --- | --- |
| Области, навигация и возврат к приложению | MainActivity, EmbeddedAppPane |
| Экран автомобиля, батарея и техника | VehicleDashboardView, BatteryDetailsView |
| Настройки внутри основной области | SettingsView; SettingsActivity — совместимый вход для уведомлений |
| Подключение ADB и восстановление | LocalAdbClient, LocalAdbManager, AdbHelperRecovery |
| Виртуальные дисплеи и ввод API28 | ShellDisplayDaemon, ShellDisplayBridge, ShellBridgeClient |
| Данные автомобиля | DiPlusReader, DiPlusValues, Di2VehicleBridge, Di2Snapshot |
| Обновления | GithubUpdater, SeagullUpdateChannel |
| Темы | ThemeActivity, ThemeManager, ThemePolicy |

Ограничения и конкретные принятые решения для ИИ хранятся в AGENTS.md,
а не в пользовательском README. Не считать успешный прогон эмулятора
подтверждением функций на автомобиле.
