#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
apk=${1:?Usage: tools/verify_release_apk.sh APK}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
apksigner="$sdk/build-tools/35.0.0/apksigner"
aapt="$sdk/build-tools/35.0.0/aapt"
expected=$(cat "$project_dir/signing/seagull-release-cert.sha256")
actual=$("$apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
[ "$actual" = "$expected" ] || { echo 'Release certificate mismatch; refusing APK.' >&2; exit 1; }
badging=$("$aapt" dump badging "$apk")
printf '%s\n' "$badging" | head -n 1 | grep -Eq "name='ru.logunov.bydsplit'.*versionName='[0-9]+\.[0-9]+\.[0-9]+-seagull(-[a-z]+\.[0-9]+)?'" || {
    echo 'Not a Seagull APK.' >&2; exit 1;
}
if printf '%s\n' "$badging" | grep -q '^application-debuggable'; then
    echo 'Debuggable APK cannot be used as a Seagull release.' >&2; exit 1
fi
printf '%s\n' "$badging" | head -n 1
printf 'Verified Seagull release certificate: %s\n' "$actual"
