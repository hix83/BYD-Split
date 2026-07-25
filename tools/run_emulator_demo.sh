#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
serial="${1:-emulator-5554}"
avd="${BYD_SPLIT_AVD:-BYD_AUTO_Dilink5.0_virtual}"
emulator_bin="${ANDROID_HOME:-$HOME/Library/Android/sdk}/emulator/emulator"
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

if ! adb -s "$serial" get-state >/dev/null 2>&1; then
    if [ ! -x "$emulator_bin" ]; then
        echo "Ошибка: Android Emulator не найден: $emulator_bin" >&2
        exit 1
    fi
    nohup "$emulator_bin" -avd "$avd" -no-snapshot-save \
        >/tmp/byd-split-emulator.log 2>&1 </dev/null &
    adb -s "$serial" wait-for-device
    while [ "$(adb -s "$serial" shell getprop sys.boot_completed \
            2>/dev/null | tr -d '\r')" != "1" ]; do
        sleep 2
    done
fi

./gradlew assembleDebug
adb -s "$serial" install -r "$apk"
"$project_dir/tools/start_adb_bridge.sh" "$serial" emulator
adb -s "$serial" shell am start \
    -n ru.logunov.bydsplit/.MainActivity \
    --ez demo_mode true >/dev/null

echo "Демо BYD Split запущено на $serial (AVD: $avd)"
