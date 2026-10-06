# ICCOA Carlink: автомобильная сторона для BYD Split

Дата проверки: 5 октября 2026 года.

## Вывод

Для первого MVP следует использовать готовый автомобильный receiver и запускать его
Activity на существующем компактном `VirtualDisplay` BYD Split. Это даёт требуемое
отображение в 1/3 экрана и обратный touch без изменения закрытого протокола ICCOA.

Прямое встраивание видеоповерхности receiver в `View` BYD Split сейчас невозможно:
его транспортные сервисы не экспортированы, публичного `setSurface(...)` API в APK
не обнаружено. Официальный ICCOA SDK существует, но распространяется через процедуру
Carlink接入, а не как открытая Maven/GitHub-зависимость.

## ICCOA Carlink 1.2.2

- Источник: <https://www.nrdoc.com/byd/index.html>
- Файл: `ICCOA_Carlink_v1.2.2.apk`
- SHA-256: `eb15e5a62cea12c1ecb8bf0e20f8c5369fb6eaa34820720130c3b6073053a3e3`
- Package: `com.ucarhu.demo`
- Launcher: `com.ucarhu.demo.UCarDemoActivity`
- Version: `v1.2.2` (`versionCode=2023`)
- minSdk: 24
- targetSdk: 30
- ABI: `arm64-v8a`, `armeabi-v7a`; USB-слой также содержит `x86` и `x86_64`
- Подпись: стандартный Android test key, SHA-256 сертификата
  `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`

### Подтверждение ICCOA

В APK присутствуют:

- строки `ICCOA`, `ICCOA CarLink`, `DIRECT-ICCOA-`;
- `UCarProto` с auth, video, audio, navigation, phone, key и touch/control
  сообщениями;
- BLE, Wi-Fi P2P, Wi-Fi AP и Android Open Accessory transport;
- H.264 decoder (`video/avc`, `MediaCodec.createDecoderByType`);
- RTP/RTSP и UIBC с `Keyboard`, `Mouse`, `SingleTouch`;
- `SurfaceView` для вывода;
- нативные библиотеки `libovmsink.so` и `libusbio.so`.

Это настоящий car-side receiver, а не одноимённый проект для CarPlay, Android Auto,
ADB или scrcpy.

### Компоненты

- `com.share.connect.ble.BluetoothLeService`
- `com.share.connect.wifip2p.WifiP2pService`
- `com.share.connect.wifiap.WifiApService`
- `com.share.connect.ShareLinkService`

Все сервисы имеют `exported=false`. Поэтому безопасная доступная интеграция — запуск
launcher Activity на виртуальном дисплее. Receiver сам владеет соединением,
декодированием и обратным управлением.

### Ограничения

APK запрашивает системные разрешения (`BLUETOOTH_PRIVILEGED`, `NETWORK_STACK`,
`TETHER_PRIVILEGED`, `MANAGE_USB` и другие). При обычной установке Android не выдаст
signature/privileged-разрешения. Нужно проверить на DiLink 5, достаточно ли обычных
BLE/P2P API для выбранного сценария, либо receiver потребует системную установку.

Версия 1.2.2 соответствует классическому Carlink 1.x. Малое окно Carlink 1.6 и
зеркалирование 2.0 официально требуют более новую головную сторону; совместимость
этого APK с CarWith 3.6+ нужно подтвердить на Xiaomi.

## Интеграция в BYD Split

BYD Split теперь разрешает пакет `com.ucarhu.demo` в списке приложений компактной
области. После установки receiver появится в выборе приложений для левой области и
будет запущен на отдельном `VirtualDisplay`:

1. размер виртуального дисплея равен области 1/3 экрана;
2. receiver рисует собственный `SurfaceView` внутри этого дисплея;
3. существующая маршрутизация BYD Split передаёт touch на виртуальный дисплей;
4. основная правая область приложения продолжает работать независимо.

## Проверка на DiLink 5

Когда машина снова будет доступна:

1. проверить ADB-устройство и подтвердить, что это физический DiLink 5;
2. установить APK и выдать только доступные runtime-разрешения;
3. запустить `com.ucarhu.demo/.UCarDemoActivity` сначала на основном экране;
4. проверить обнаружение из Xiaomi CarWith и шестизначный код;
5. выбрать ICCOA Carlink в компактной области BYD Split;
6. проверить видео, одиночный touch, drag, звук и повторное подключение;
7. собрать `logcat`, список сокетов и сетевой trace при проблемах;
8. отдельно проверить поведение при смене ориентации и возврате из фонового режима.

## Официальные сведения

- ICCOA описывает Carlink SDK как SDK для автомобильных приложений с беспроводным
  подключением, видеопотоком и передачей касаний: <https://www.iccoa.cn/suit_1.html>
- Официальный список различает Carlink 1.0, 1.5, малое окно 1.6 и зеркало 2.0:
  <https://www.iccoa.cn/site/iccoaCase>
- Технические требования опубликованы ICCOA, но реализация SDK не открыта:
  <https://www.iccoa.cn/tech/>

## Отброшенные проекты

`wxip/carlink`, `Slyosx31/Carlink`, Carlinkit CPC200 и похожие проекты не являются
ICCOA receiver: они используют ADB/scrcpy, Android Auto или CarPlay. Их нельзя
считать совместимыми с Xiaomi CarWith.
