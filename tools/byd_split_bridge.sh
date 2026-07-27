#!/system/bin/sh

# Executed by toybox netcat under the ADB shell UID. The Android application
# cannot hold BYD's signature-only STATUS_BAR permission, while the shell UID
# is explicitly granted it by the firmware.

read -r protocol token argument_one argument_two argument_three argument_four argument_five argument_six

if [ "$token" != "d5c7a1429b68460e" ]; then
    echo "ERR unauthorized"
    exit 1
fi

is_valid_package() {
    case "$1" in
        ""|*[!A-Za-z0-9._]*) return 1 ;;
        *) return 0 ;;
    esac
}

if [ "$protocol" = "BYD_PING_V1" ]; then
    echo "OK"
    exit 0
fi

if [ "$protocol" = "BYD_CONTROL_V1" ] &&
   { [ "$argument_one" = "RESTART_HELPERS" ] ||
     [ "$argument_one" = "RESTART_INPUT" ]; }; then
    apk_path=$(/system/bin/pm path ru.logunov.bydsplit \
        | /system/bin/sed -n 's/^package://p' | /system/bin/head -n 1)
    if [ -z "$apk_path" ]; then
        echo "ERR apk-not-found"
        exit 1
    fi

    /system/bin/pkill -f 'ru.logunov.bydsplit.ShellInputDaemon' \
        2>/dev/null || true
    nohup /system/bin/sh -c \
        "export CLASSPATH=$apk_path; exec /system/bin/app_process /system/bin ru.logunov.bydsplit.ShellInputDaemon" \
        >/data/local/tmp/byd_input_daemon.log 2>&1 </dev/null &

    if [ "$argument_one" = "RESTART_HELPERS" ]; then
        /system/bin/pkill -f 'ru.logunov.bydsplit.SteeringInputDaemon' \
            2>/dev/null || true
        nohup /system/bin/sh -c \
            "export CLASSPATH=$apk_path; exec /system/bin/app_process /system/bin ru.logunov.bydsplit.SteeringInputDaemon" \
            >/data/local/tmp/byd_steering_daemon.log 2>&1 </dev/null &
    fi

    echo "OK"
    exit 0
fi

if [ "$protocol" = "BYD_EMBED_V1" ]; then
    display_id="$argument_one"
    component="$argument_two"
    package_name="${component%%/*}"
    case "$display_id" in
        ""|*[!0-9]*) echo "ERR invalid-display"; exit 1 ;;
    esac
    case "$component" in
        ""|*[!A-Za-z0-9._/\$]*) echo "ERR invalid-component"; exit 1 ;;
    esac
    if ! is_valid_package "$package_name"; then
        echo "ERR invalid-package"
        exit 1
    fi

    result=$(/system/bin/am start --display "$display_id" \
        -f 0x00010000 -n "$component" 2>&1)
    /system/bin/sleep 0.2
    task_id=$(/system/bin/am stack list |
        /system/bin/awk -v target="$package_name/" '
            /^RootTask id=/ {
                split($2, parts, "=")
                root_task = parts[2]
            }
            index($0, target) > 0 {
                print root_task
                exit
            }
        ')
    if [ -n "$task_id" ]; then
        /system/bin/am display move-stack "$task_id" "$display_id"
    fi
    case "$result" in
        *Starting*|*"Activity not started"*) echo "OK" ;;
        *) echo "ERR launch"; exit 1 ;;
    esac
    exit 0
fi

is_valid_number() {
    case "$1" in
        ""|*[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

if [ "$protocol" = "BYD_INPUT_V1" ]; then
    display_id="$argument_one"
    start_x="$argument_two"
    start_y="$argument_three"
    end_x="$argument_four"
    end_y="$argument_five"
    duration="$argument_six"
    if ! is_valid_number "$display_id" ||
       ! is_valid_number "$start_x" ||
       ! is_valid_number "$start_y" ||
       ! is_valid_number "$end_x" ||
       ! is_valid_number "$end_y" ||
       ! is_valid_number "$duration"; then
        echo "ERR invalid-input"
        exit 1
    fi

    if [ "$start_x" = "$end_x" ] && [ "$start_y" = "$end_y" ] &&
       [ "$duration" -lt 500 ]; then
        result=$(/system/bin/input -d "$display_id" tap \
            "$start_x" "$start_y" 2>&1)
    else
        result=$(/system/bin/input -d "$display_id" swipe \
            "$start_x" "$start_y" "$end_x" "$end_y" "$duration" 2>&1)
    fi
    echo "OK"
    exit 0
fi

if [ "$protocol" = "BYD_KEY_V1" ]; then
    display_id="$argument_one"
    key_name="$argument_two"
    if ! is_valid_number "$display_id" || [ "$key_name" != "BACK" ]; then
        echo "ERR invalid-key"
        exit 1
    fi
    /system/bin/input -d "$display_id" keyevent KEYCODE_BACK
    echo "OK"
    exit 0
fi

if [ "$protocol" = "BYD_MOTION_V1" ]; then
    display_id="$argument_one"
    motion_action="$argument_two"
    motion_x="$argument_three"
    motion_y="$argument_four"
    if ! is_valid_number "$display_id" ||
       ! is_valid_number "$motion_x" ||
       ! is_valid_number "$motion_y"; then
        echo "ERR invalid-motion"
        exit 1
    fi
    case "$motion_action" in
        DOWN|MOVE|UP|CANCEL) ;;
        *) echo "ERR invalid-action"; exit 1 ;;
    esac
    /system/bin/input -d "$display_id" motionevent \
        "$motion_action" "$motion_x" "$motion_y"
    echo "OK"
    exit 0
fi

if [ "$protocol" != "BYD_SPLIT_V1" ] ||
   ! is_valid_package "$argument_one" ||
   ! is_valid_package "$argument_two"; then
    echo "ERR invalid-command"
    exit 1
fi

result=$(/system/bin/service call statusbar 72 \
    s16 "$argument_one" s16 "$argument_two" 2>&1)

case "$result" in
    *Parcel*) echo "OK" ;;
    *)
        echo "ERR service-call"
        exit 1
        ;;
esac
