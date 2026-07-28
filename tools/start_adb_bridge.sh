#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
serial="${1:-10.14.32.18:5555}"
mode="${2:-dilink}"
remote_script="/data/local/tmp/byd_split_bridge.sh"
remote_log="/data/local/tmp/byd_split_bridge.log"
input_log="/data/local/tmp/byd_input_daemon.log"
steering_log="/data/local/tmp/byd_steering_daemon.log"

stop_app_process_class() {
    class_name=$1
    adb -s "$serial" shell "
        for pid in \$(pidof app_process); do
            cmd=\$(tr '\\0' ' ' </proc/\$pid/cmdline)
            case \"\$cmd\" in
                \"app_process /system/bin $class_name\"*|\
                \"/system/bin/app_process /system/bin $class_name\"*)
                    kill \"\$pid\" 2>/dev/null || true
                    ;;
            esac
        done
    "
}

adb -s "$serial" push "$project_dir/tools/byd_split_bridge.sh" "$remote_script"
adb -s "$serial" shell chmod 700 "$remote_script"
adb -s "$serial" shell pkill -f 'nc -s 127.0.0.1 -L -p 37527' 2>/dev/null || true
adb -s "$serial" shell "nohup /system/bin/nc -4 -s 127.0.0.1 -L -p 37527 /system/bin/sh $remote_script >$remote_log 2>&1 </dev/null &"

apk_path=$(adb -s "$serial" shell pm path ru.logunov.bydsplit \
    | tr -d '\r' | sed -n 's/^package://p' | head -n 1)
if [ -n "$apk_path" ]; then
    stop_app_process_class ru.logunov.bydsplit.ShellInputDaemon
    adb -s "$serial" shell \
        "nohup /system/bin/sh -c 'export CLASSPATH=$apk_path; exec /system/bin/app_process /system/bin ru.logunov.bydsplit.ShellInputDaemon' >$input_log 2>&1 </dev/null &"
    if [ "$mode" = "dilink" ]; then
        stop_app_process_class ru.logunov.bydsplit.SteeringInputDaemon
        adb -s "$serial" shell \
            "nohup /system/bin/sh -c 'export CLASSPATH=$apk_path; exec /system/bin/app_process /system/bin ru.logunov.bydsplit.SteeringInputDaemon' >$steering_log 2>&1 </dev/null &"

        accessibility_service="ru.logunov.bydsplit/ru.logunov.bydsplit.SteeringAccessibilityService"
        enabled_services=$(adb -s "$serial" shell settings get secure \
            enabled_accessibility_services | tr -d '\r')
        case "$enabled_services" in
            *"$accessibility_service"*) ;;
            null|"")
                enabled_services="$accessibility_service"
                ;;
            *)
                enabled_services="$enabled_services:$accessibility_service"
                ;;
        esac
        adb -s "$serial" shell settings put secure enabled_accessibility_services \
            "$enabled_services"
        adb -s "$serial" shell settings put secure accessibility_enabled 1
    fi
fi
echo "BYD Split ADB bridge started on $serial"
