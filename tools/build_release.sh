#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./tools/test_logic.sh
./gradlew assembleRelease assembleDebugAndroidTest lintRelease testReleaseUnitTest
./tools/verify_release_apk.sh app/build/outputs/apk/release/app-release.apk
python3 - <<'PY'
from pathlib import Path
import re, shutil, hashlib
version = re.search(r"versionName '([^']+)'", Path('app/build.gradle').read_text()).group(1)
version = version.replace('-seagull', '')
destination = Path('dist') / ('seagull-v' + version)
destination.mkdir(parents=True, exist_ok=True)
apk = destination / ('BYD-Split-Seagull-' + version + '.apk')
shutil.copyfile('app/build/outputs/apk/release/app-release.apk', apk)
(destination / 'SHA256SUMS.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + '  ' + apk.name + '\n')
print('Release assets:', destination)
PY
