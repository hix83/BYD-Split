#!/bin/sh
set -eu

serial="${1:-10.198.105.18:5555}"
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
apk="$script_dir/BYD-Split-0.6.9-v20-installed.apk"

case "$serial" in
    *:*) adb connect "$serial" >/dev/null 2>&1 || true ;;
esac

adb -s "$serial" get-state >/dev/null
adb -s "$serial" install -r -d "$apk"
adb -s "$serial" shell am force-stop ru.logunov.bydsplit
adb -s "$serial" shell am start -n ru.logunov.bydsplit/.MainActivity

echo "BYD Split 0.6.9 (20) восстановлен на $serial"
