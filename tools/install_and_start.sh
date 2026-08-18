#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
serial="${1:-10.14.32.18:5555}"
apk="$project_dir/app/build/outputs/apk/debug/app-debug.apk"

cd "$project_dir"

android_studio_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$android_studio_jbr" ]; then
    export JAVA_HOME="$android_studio_jbr"
fi
default_android_home="$HOME/Library/Android/sdk"
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$default_android_home" ]; then
    export ANDROID_HOME="$default_android_home"
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "Ошибка: adb не найден. Установите Android Platform Tools." >&2
    exit 1
fi

case "$serial" in
    *:*)
        adb connect "$serial" >/dev/null 2>&1 || true
        ;;
esac

if ! adb -s "$serial" get-state 2>/dev/null | grep -q '^device$'; then
    echo "Ошибка: устройство $serial не подключено или не подтвердило RSA-ключ." >&2
    echo "Проверьте ADB и выполните: adb devices -l" >&2
    exit 1
fi

if [ "${BYD_SPLIT_SKIP_BUILD:-0}" != "1" ] || [ ! -f "$apk" ]; then
    ./gradlew assembleDebug
fi

adb -s "$serial" install -r "$apk"
"$project_dir/tools/start_helpers.sh" "$serial"
adb -s "$serial" shell am start \
    -n ru.logunov.bydsplit/.MainActivity >/dev/null

echo "BYD Split установлен и запущен на $serial"
