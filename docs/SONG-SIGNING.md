# Общая подпись Song на Маке и Windows

Song должна сохранять ключ уже опубликованных APK. Сертификат проверен по
релизам 0.9.8 и 0.9.10; SHA-256:

```text
63442ad5ab88b867970e1d7480aa2e849ec46f63b20c61b2da837aa43d715d10
```

Перенос сохраняет приватный ключ и сертификат, меняет только контейнер и пароль.
Архив содержит приватный ключ и пароль, он не зашифрован отдельным паролем.
Передавать его лично, например через свой USB-накопитель. Не помещать в GitHub,
чат, публичный файловый обменник или каталог проекта.

## На Маке: подготовить архив

Подтянуть `main`. Нужны Python 3 и JDK 17 (`JAVA_HOME` либо `keytool` в PATH).
Запустить из каталога Song:

```sh
python3 tools/song-signing.py export
```

По умолчанию исходник — `~/.android/debug.keystore`, alias `androiddebugkey`.
Пароль вводится скрыто; Enter означает стандартный `android`. Если ключ находится
в другом месте, указать `--source /путь/к/ключу` и при необходимости `--source-alias`.
Скрипт проверит сертификат, импортирует существующий ключ в PKCS12 и создаст:

```text
~/.byd-split/song-signing/song-release.p12
~/.byd-split/song-signing/keystore.properties
~/.byd-split/transfer/BYD-Split-Song-signing.zip
```

Существующие каталоги/архивы не заменяются. При повторном запуске использовать
уже созданный архив либо новые пути `--output-dir` и `--archive`.
Скрипт не меняет исходный Android debug-ключ, пароль не выводится и не попадает
в аргументы команд. На Маке новый каталог сразу подходит для release-сборки.

## Перенести вручную и подключить на Windows

Скопировать архив, например, в
`%USERPROFILE%/.byd-split/transfer/BYD-Split-Song-signing.zip`.
Из каталога Song запустить Python 3 (команда зависит от установленного Python):

```powershell
python tools/song-signing.py import --archive "$env:USERPROFILE/.byd-split/transfer/BYD-Split-Song-signing.zip"
```

Codex может использовать собственный доступный Python 3; скрипту не нужны
сторонние пакеты. Нужен `keytool` из JDK 17.

Импорт проверяет состав архива, сертификат и возможность использовать приватный
ключ. Файлы устанавливаются только после проверки, в новый каталог:

```text
%USERPROFILE%/.byd-split/song-signing/song-release.p12
%USERPROFILE%/.byd-split/song-signing/keystore.properties
```

Если каталог существует, импорт остановится, ничего не перезаписывая.
Каталог `.byd-split/signing` с ключом Seagull не используется для Song.

## Сборка и публикация с любого компьютера

Gradle ищет конфигурацию в таком порядке:

1. Путь из `BYD_SONG_SIGNING_PROPERTIES`, если переменная задана.
2. `song-keystore.properties` в корне проекта (игнорируется Git).
3. `~/.byd-split/song-signing/keystore.properties`.

Путь к `storeFile` считается относительно файла properties, поэтому оба
компьютера используют одинаковый комплект без локальных путей в Git.
Неизвестный сертификат отклоняется; без ключа release-сборка завершается ошибкой.
Debug-сборки продолжают использовать локальный Android debug-ключ для эмулятора.

```sh
# Mac
./gradlew assembleRelease lintRelease
```

```powershell
# Windows
.\gradlew.bat assembleRelease lintRelease
```

Подписанный результат: `app/build/outputs/apk/release/app-release.apk`.
Перед публикацией проверить сертификат через `apksigner verify --print-certs`,
пакет и увеличенные versionCode/versionName через `aapt dump badging`.
Не загружать старый `app-release-unsigned.apk`, оставшийся от прежних сборок.
Для GitHub Releases сохраняется имя `BYD-Split-<версия>.apk` и стабильный latest.
Сам перенос ключа не требует выпуска новой версии приложения.

## Проверки механизма переноса

```sh
python3 tools/test_song_signing.py
```

Тесты используют временный ключ: проверяют сохранение сертификата и приватного
ключа при переносе, отказ при другом сертификате, защиту от перезаписи и
небезопасных путей в архиве. Это не подтверждение переноса настоящего ключа:
окончательная проверка — подпись release APK настоящим ключом на обоих ПК.
