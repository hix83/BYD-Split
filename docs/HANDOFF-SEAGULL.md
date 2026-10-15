# Продолжение разработки на другом ПК

Состояние на 15 октября 2026: ветка `seagull`, тег `seagull-v0.9.2-alpha.1`, APK `0.9.2-seagull-alpha.1` / code 37.
Репозиторий: https://github.com/hix83/BYD-Split.

## Получить проект

```powershell
git clone --branch seagull https://github.com/hix83/BYD-Split.git BYD_Split_seagul
cd BYD_Split_seagul
git status
```

Для точного состояния этого выпуска можно создать рабочую ветку от тега:
`git switch -c seagull-work seagull-v0.9.2-alpha.1`.
Перед правками прочитать AGENTS.md, этот файл, docs/SEAGULL-EMULATOR.md
и docs/firmware/di2-adb.md. Стабильную main не сливать целиком.

## Что перенести отдельно

Эти файлы НЕ входят в Git и НЕ загружены в публичный релиз:

- `Di2.2H_ota_26.1.1.2511070.1.zip` — 2 324 192 398 байт.
- `Di5.0_23.1.22.2510203.1_0.zip` — 7 887 108 169 байт.
- При необходимости готовые распаковки и декомпиляции `tmp/ota-di2`,
  `tmp/ota-di5`. Они полезны для дальнейшего анализа; для сборки не нужны.
- Для установки следующих сборок поверх текущей с сохранением данных:
  текущий ключ `C:\Users\hixma\.android\debug.keystore` перенести приватно
  в `%USERPROFILE%\.android\debug.keystore` нового ПК. Перед заменой сохранить
  уже существующий ключ нового ПК. Не публиковать ключ в Git, релизе или чате.
  SHA256 сертификата текущего APK:
  `db20a676e5bc23ee3758c7d11028a52217bc4b214792907f0ae0d89600ef3b98`.
  Новый автоматически созданный debug-ключ несовместим с обновлением
  установленного APK. GitHub CI также использует свой временный ключ;
  его артефакт не следует считать совместимым обновлением этого релиза.
- Опционально `tmp/yandex-apps/ru.yandex.music.apk` и
  `ru.yandex.yandexnavi.apk`, чтобы не скачивать повторно. Версии,
  происхождение, подписи и SHA256 записаны в docs/SEAGULL-EMULATOR.md.

`local.properties`, установленный SDK, JDK, Gradle-кэш и локальный
`build-local.ps1` привязаны к старому ПК. Настроить их заново.
Копировать AVD целиком необязательно: скрипт создаёт его заново.

## Сборка

Установить Git (включая Git Bash), JDK 17, Android command-line tools.
В переменных окружения задать JAVA_HOME и ANDROID_HOME на свои каталоги.
После принятия лицензий Android установить:

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 'emulator' 'system-images;android-28;default;x86_64'
```

Создать игнорируемый `local.properties` с `sdk.dir=C:/путь/к/Android/Sdk`
(использовать фактический путь, прямые слеши). Затем:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest
& 'C:/Program Files/Git/bin/bash.exe' tools/test_logic.sh
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. JUnit-тестов нет:
testDebugUnitTest выдаёт NO-SOURCE; проверки логики выполняет отдельный скрипт.
Для сверки подписи: `apksigner.bat verify --print-certs <APK>` из build-tools.

## Эмулятор

Нужна включённая аппаратная виртуализация и Windows Hypervisor Platform.

```powershell
.\tools\seagull-emulator.ps1 -SdkRoot $env:ANDROID_HOME -JavaHome $env:JAVA_HOME -InstallApp
$adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
& $adb -s emulator-5560 shell wm size 1366x768
```

AVD: `BYD_Seagull_DiLink2_API28`, API 28 AOSP x86_64, порт 5560,
плотность 160. Начальная физическая конфигурация скрипта 1920×1080;
последняя проверка шрифтов — override 1366×768. Для его отмены: `wm size reset`.
Штатное разрешение автомобиля ещё нужно подтвердить через ADB.

Скрипт включает SwiftShader + GLESDynamicVersion: с ES 2 Навигатор падал
на eglChooseConfig. Для embedded ADB обязательно
`adb -s emulator-5560 reverse tcp:5555 tcp:5561`; скрипт делает это сам.
Приложения Яндекса устанавливаются отдельно через `adb install -r <APK>`.
Без Play Store подходят универсальные официальные APK с x86_64;
ARM-сборки для магнитолы не подходят этому AVD.
Для панели автомобиля включить демо в настройках: в AOSP нет сервисов BYD.
Тестовая GPS-точка: `adb -s emulator-5560 emu geo fix 82.9204 55.0302`.

Проверка обеих панелей instrumentation описана в docs/SEAGULL-EMULATOR.md.
Перед каждым новым запуском остановить тестовый пакет; перед левой проверкой
выбрать Музыку в левой панели. Тестовые Activity не входят в обычный APK.

## Что уже сделано и что нельзя потерять

- UI7: исходная изометрическая схема, батарея, электропривод, шины, компас,
  компоновка 1:2. ДВС и гибридные показатели убраны.
- Кузов CROSS из OTA Di5 выбран владельцем как похожий на Seagull;
  точная модель ресурса не доказана. Дневная/ночная тема следует Android uiMode.
- Значок Song заменён на чайку. Шрифты дока 12 sp, приборной панели +12%.
- Di2VehicleSdk / Di2VehicleBridge: чтение телеметрии и команды климата
  с проверкой значений и чтением результата. Доступ на автомобиле ещё не проверен.
  Числовые Binder-адреса Di5 для Di2 не использовать.
- На API 28 ShellDisplayDaemon владеет публичными виртуальными дисплеями
  от shell UID и передаёт ввод через InputForwarder. Нужны одновременно
  владение дисплеем и INJECT_EVENTS. Запуск помощника использует отдельное
  постоянное ADB-соединение без тайм-аута разовой команды.
- На эмуляторе прошли проверки tap, swipe, multitouch ID 3/7, cancel, Back,
  координат, времён и соединения дольше 17 секунд для обеих панелей.
- Музыка и Навигатор установлены и запускались в панелях. В Музыке проверены
  страницы знакомства без аккаунта; воспроизведение не проверялось.

**Последнее решение владельца:** при переключении на Музыку левая область
иногда не обновляется. Пока ничего в этом механизме не менять: владелец
сначала подтвердит поведение на настоящем DiLink 2. На скриншоте также
встречалась чёрная карта Навигатора; успешный прогон не доказывает отсутствие
артефактов эмулятора. Не объявлять проблему окончательно устранённой.

Следующий шаг — результаты проверки на автомобиле: реальное wm size/density,
выдача SDK-разрешений, чтение данных, климат и переключение панелей.
Пакет и автообновление пока исходные; Seagull — prerelease, ручная установка.

## Текст для нового чата

```text
Продолжаем BYD Seagull / DiLink 2 / Android 9 из hix83/BYD-Split,
ветка seagull, исходная точка seagull-v0.9.2-alpha.1.
Сначала прочитай AGENTS.md и docs/HANDOFF-SEAGULL.md, затем документацию,
на которую они ссылаются. Проверь текущую ветку и рабочее дерево.
Сохраняй исходную схему UI7 и компоновку 1:2, не переделывай дизайн.
Шрифты увеличены, значок Song заменён на чайку, проверяли 1366×768.
Не исправляй пока необновление левой панели при переключении на Музыку:
я сначала проверю это на настоящем DiLink 2 и сообщу результат.
Помоги настроить сборку на этом ПК и продолжить с зафиксированного состояния.
```


## Проверка на машине 15 октября 2026 (macOS)

Реальный экран 1280×720 / 160 dpi, Android 9 / MT6765. На macOS собраны основной и тестовый APK, lint и tools/test_logic.sh прошли на JDK 17 + SDK 35. Локальный build-local.sh и local.properties исключены из Git.

На машине установлен текущий debug APK с ключом Mac (не предыдущий Windows APK). Изменения рабочего дерева ещё требуют отдельного коммита и публикации по запросу владельца. После установки данные восстановлены через Di+, аналогично BYDMate для Чайки. Прямой SDK требует signature GET; COMMON выданы, но их недостаточно. Подробности, разрешения и воспроизводимая диагностика: [di2-diplus.md](firmware/di2-diplus.md). Основной транспорт теперь Di+, SDK остаётся fallback / диагностикой. Помощник экранов: собственный публичный ADB-ключ разрешён штатным USB API авторизованного shell, displayReady=true. Файлы приватных ключей не переносились.

### Battery readings confirmed on physical Di2

See `docs/firmware/di2-diplus.md`: newer BYDMate `dilink2-seagull-mac`
branch uses an ADB autoservice supplement. Independently verified SOH 97%,
battery 25–28 °C, 12 V supply 13.8 V, cell voltages ~3.302–3.343 V.
`LocalAdbManager` now merges these into the Di+ snapshot (30 s refresh),
`Di2Snapshot` passes SOH/temperatures/voltage to the dashboard and cell
voltages to battery details. `Di2BatteryValuesTest` covers real Parcels and
missing/sentinel decoding. No Di5 Celsius offset and no fake SOH values.

### Seagull shortcuts and motor RPM

Removed the MyCar shortcut (not installed on Seagull), leaving four quick
buttons. Climate opens OEM `OPEN_AIR_CONDITIONING` / FullScreenMainActivity,
while live icon fan dots and wind direction remain. Added Di+ templates
`前电机转速` and `空调出风模式` to the primary reader. Signed motor RPM
is preserved, sentinels rejected. See `docs/firmware/di2-diplus.md` for live
readings, APK metadata findings and rejected Binder candidates.

Final device check after installation: app diagnostics report motorRpm 0 at
standstill, wind mode 5, fan level 4, displayReady true. Four-button dashboard
visually checked. Fixed an action-ID/icon-array-index mismatch exposed during
this device check; rebuilt with full local checks before reinstalling.

### Climate target / no TPMS

Owner confirmed no tyre pressure sensors on this Seagull. Removed all four
pressure/temperature callouts and their leader lines from the isometric
scene, plus the tyre section from Technical details. Keep car geometry intact.
Dashboard right column now shows the OEM climate target in Celsius above
auxiliary voltage, using Di+ ACTemp (`主驾驶空调温度`); never substitute cabin
or outside sensor temperature. Missing targets show a dash. Target is carried
in ClimateIconState and survives telemetry copies and GPS bearing updates.

Climate-target/no-TPMS update: full local build/lint/logic checks passed;
`adb install -r` and `am start` succeeded on 127.0.0.1:5038. The tunnel
then briefly disappeared (connection refused), but recovered. Final screenshot
verified on hardware: Climate 28 °C above 13.8 V on the right; no tyre labels
or callouts. Live diagnostics also confirmed temperature 28 and displayReady true.

### Separate Seagull update channel

Prepared 0.9.2-seagull-alpha.2 / versionCode 38. Updater selects the highest
published `seagull-v*` release (including prereleases), exact Seagull APK
asset, and validates Seagull APK version as well as package/code/signature.
Song `v*` releases are excluded. Settings identify the Seagull branch/channel.
See `docs/SEAGULL-UPDATES.md` for naming, signing and publishing contract.
No release was published by this change.

Updater change validation: complete local build/lint/logic suite passed,
including Seagull channel isolation, alpha numeric ordering and exact asset
naming tests. Device deployment not completed: 127.0.0.1:5038 refused connection,
no ADB devices or mDNS targets/USB device were available; local /24 scan
found only the gateway TCP 5555 candidate, already verified not usable ADB.
Current local APK is alpha.2/code38; the car may still have alpha.1/code37.

Updater build subsequently deployed successfully to physical Di2 at
127.0.0.1:5038: `adb install -r` Success, MainActivity launched, package
manager confirms 0.9.2-seagull-alpha.2/versionCode38. Settings preserved.

### alpha.3 / code39: dashboard crash and bars on guest startup

Hardware logcat showed repeatable NPE at VehicleDashboardView.drawHeader: the
primitive `22f` in the demo/live ternary forced nullable live climate target
Float to unbox before the missing-value check. Box the demo operand explicitly.
Regression render test (`am instrument -w -e dashboard_only true ...`) now
renders EMPTY, live and cleared snapshots at two widths with demo disabled.
Passed on API28 AOSP emulator. Physical Di2's OEM AutoMapHelper crashes during
instrumentation process binding before our runner starts; use normal UI checks
on hardware, not an instrumentation pass claim.

WindowManager evidence: after an embedded app launch, focus moved from
MainActivity (systemUI 0x970e) to Telegram guest (0x8508), exposing both bars.
After the bounded guest-startup/redirect settling completes on API28, explicitly
bring existing singleTask MainActivity forward without moving the guest out of
its display. Also restore immersive flags after system-UI visibility changes
while the host is resumed; callbacks stop onPause. No global policy_control
or system settings changed. Full build, lint, logic and emulator render tests pass.

### Optional ADB Remote watchdog

Settings → Behavior: «Автозапуск DiLink Remote ADB» (default OFF).
Foreground service AdbRemoteWatchdogService starts on enable, launcher start
or boot/unlock when preference is enabled; disable stops service/notification.
Hardware package ru.slksoft.dilink runs RelayService in :relay process.
Every 15 s, use an independent on-device ADB connection to check that exact
process via pidof, with a completion marker to distinguish failure from absence.
Only confirmed absence triggers launching the exported MainActivity. Unknown
local ADB health or an Internet outage is not treated as a relay crash. Attempts
are throttled to once per 2 min and timestamp survives service recreation.
The service keeps running while launcher UI is backgrounded; it does not make
force-stopped BYD Split itself immortal. API28 only, separate persistent low
priority notification. Cooldown/disabled/healthy/unknown policy tests added.

Latest alpha.3/code39 APK (including watchdog UI/service) installed successfully
on physical Di2 and MainActivity launched; settings preserved. Main dashboard
visually checked with live data and no tyre labels. After a subsequent launch
attempt WindowManager showed host focus and both bars WINDOW_STATE_HIDDEN,
but the guest launch/touch sequence could not be fully verified: tunnel began
repeatedly dropping before the selected app appeared. Do not claim full guest
interaction validation or a real relay-stop/recovery watchdog test yet.
Watchdog preference remains OFF (default). Final emulator dashboard regression
and full build/lint/logic tests passed. Temporary headless emulator shut down.

### Navigator crash investigation

Physical Di2 crash buffer shows Navigator (`ru.yandex.yandexnavi`) NPE in
Android ViewRootImpl$ViewRootHandler: Display.getDisplayId() called on null,
not OutOfMemoryError. First recorded instance at 18:53:14 followed BYD Split's
Technical view NPE at 18:53:12 by two seconds. A later instance was at 19:02:24.
A live /proc/meminfo sample had ~1.9 GB MemAvailable out of ~3.9 GB total;
this does not prove historical free memory but does not indicate memory
pressure in the observed session. No evidence supporting an OOM diagnosis.

Technical update no longer writes removed tyre widgets; put() tolerates fields
absent from a particular page. Regression covers both detail pages updates.
API28 ShellDisplayDaemon now parks named displays on RELEASE (surface null),
and reuses their IDs on CREATE after pane rebuild. This avoids removing a
Display under a guest ViewRootImpl during ordinary UI reconstruction. At most
four named displays; full helper/process shutdown still releases everything.
This does not promise guest survival after a force-stop/crash of the owning
process. Five-cycle display lifecycle instrumentation added separately from
dashboard/detail-page render test.


Navigator/relay follow-up on hardware:
- Five release/recreate cycles preserve Display ID in API28 instrumentation.
- Dashboard/detail-page render test passed for empty/live/cleared values.
- Repeated Technical open/back transitions preserved launcher and Navigator PIDs,
  with no new Java crash after the historical 19:02 Navigator NPE.
- Watchdog enabled through Settings; foreground service/notification verified.
  It remains ON; owner can disable it in Behavior.
- Warm and cold direct Navigator launches retained ADB. Relay PID 6999 started
  about 77 seconds before cold Navigator PID 7528, so its prior PID change is
  not evidence of that cold launch killing it. MemAvailable ~2.0 GB. Relay
  notification SESSION_ACTIVE; no relay Java crashes in captured crash buffer.
- Owner reports disconnection through their launch path. Root cause of that
  relay/session failure remains unconfirmed; asked picker/system icon/carousel.
  Do not claim it fixed based only on direct-launch tests.
- Installed Remote APK already has WebSocket reconnect, wake/WiFi locks, and
  service alarm restart. State broadcasts are package-scoped. Current watchdog
  handles missing relay PID, not an unhealthy tunnel in a live process.
- Focused on-device log recorder uses setsid, runs for five minutes and exits:
  /data/local/tmp/seagull-relay-focused.log (3 x 1 MB max). It survives tunnel
  loss. Test emulator shut down and hardware test APK removed.

### Black Navigator map and Remote ADB CPU suppression (2026-10-15)

Physical screenshot showed a black GL map while Navigator UI overlays remained.
Activity dump confirmed MapActivity PAUSED with host RESUMED. Remove alpha.3's
forced host foreground at the end of launchOnDisplay. API28 stack reparenting
also returns focus to display0; explicitly `am task focus` the visible guest's
TASK id (not stack id) after bounded startup redirects. Restore guest focus
when returning from battery/technical details or selecting the vehicle dashboard.

Bars now use a package-scoped `policy_control` filter only for embedded packages
while fullscreen is enabled. Preserve original clauses/exclusions, restore the
original setting on display release/fullscreen disable, recover after process
restart, and relinquish ownership if another app/user changes the setting.
No wildcard fullscreen policy is added. The setting can remain until next start
if the process is force-stopped; recovery record is stored in app preferences.
Full build/lint/logic suite passed. API28 different-UID instrumentation confirms
launch leaves guest RESUMED, host foreground pauses it, explicit return resumes
it, and the original immersive policy is restored after release.

Remote ADB failure is now identified, separately from Navigator Java crashes:
19:28:35 system log: DuraSpeed HEALTH_TYPE_CPU, suppressPackages=[ru.slksoft.dilink],
then ActivityManager Force stopping from system_server PID954. Not an inferred
OOM. `dumpsys duraspeed addwhitelist ru.slksoft.dilink` returned true and config
confirmed PlatformWhitelist includes it. No global DuraSpeed disable. Watchdog
reapplies this target-only exemption when enabled (e.g. after reboot). Disabling
the watchdog stops relaunching, but does not remove the platform whitelist entry.
Relay PID9590 remained alive during further CPU unhealthy events after exemption.

Owner confirmed car parked before deployment. Updated APK installed successfully.
Physical Navigator resumed with the SAME PID7528/activity, and screenshot showed
roads/buildings again, bars hidden. This is hardware rendering evidence, not
just the emulator probe. Do not claim a long-duration drive or reboot test.
Final hardware check after latest install: opened technical page via its wrench
button and used the actual Back control (bounds1138,22–1252,64). Navigator
returned RESUMED; same PID7528, relay9590; both WINDOW_STATE_HIDDEN. Final
screencap /tmp/seagull-gl-verified.png has rendered map. Emulator shut down.

### Release checkpoint: alpha.4 / code40

Owner requested publishing the current Seagull work before further refinements.
Release tag seagull-v0.9.2-alpha.4; APK version0.9.2-seagull-alpha.4/code40.
Use the existing local signing key matching the current car installation.
Release notes: docs/releases/seagull-v0.9.2-alpha.4.md. Keep prerelease and
latest=false; Song remains on its stable latest release. No new car installation
requested for this publishing step.
