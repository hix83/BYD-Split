#!/bin/sh
set -eu
serial=${1:?Usage: authorize-local-adb.sh SERIAL}
apk=$(adb -s "$serial" shell pm path ru.logunov.bydsplit | tr -d '\r' | sed -n 's/^package://p' | head -n 1)
case "$apk" in /data/app/*/base.apk) ;; *) echo 'Unexpected installed APK path' >&2; exit 1;; esac
# The shell must already be authorized; only the app's public key is passed to USB service.
adb -s "$serial" shell "CLASSPATH=$apk app_process /system/bin ru.logunov.bydsplit.AdbAuthorizationHelper"
