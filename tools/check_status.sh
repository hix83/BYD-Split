#!/bin/sh
set -eu

serial="${1:-10.14.32.18:5555}"
mode="${2:-dilink}"
token="d5c7a1429b68460e"

check_port() {
    label="$1"
    port="$2"
    command="$3"
    response=$(adb -s "$serial" shell \
        "echo '$command $token' | nc -w 1 127.0.0.1 $port" \
        2>/dev/null | tr -d '\r')
    if [ "$response" = "OK" ]; then
        echo "OK   $label"
    else
        echo "FAIL $label"
        return 1
    fi
}

failed=0
check_port "касания и мультитач" 37528 "BYD_FAST_PING_V1" || failed=1
if [ "$mode" = "dilink" ]; then
    check_port "кнопка руля" 37530 "BYD_STEERING_PING_V1" || failed=1
else
    echo "SKIP кнопка руля (Android Emulator)"
fi

exit "$failed"
