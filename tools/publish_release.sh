#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"
notes=${1:?Usage: tools/publish_release.sh RELEASE_NOTES_FILE}
[ -f "$notes" ] || { echo 'Release notes file not found.' >&2; exit 1; }
[ "$(git branch --show-current)" = seagull ] || { echo 'Publish from seagull only.' >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo 'Commit changes before publishing.' >&2; exit 1; }
tag=$(python3 - <<'PY'
from pathlib import Path
import re
version = re.search(r"versionName '([^']+)'", Path('app/build.gradle').read_text()).group(1)
assert '-seagull' in version
print('seagull-v' + version.replace('-seagull', ''))
PY
)
[ "$(git rev-parse "$tag^{}")" = "$(git rev-parse HEAD)" ] || { echo 'Tag must point at HEAD.' >&2; exit 1; }
if gh release view "$tag" --repo hix83/BYD-Split >/dev/null 2>&1; then
    echo 'Release already exists; publish a new version, do not replace signed assets.' >&2; exit 1
fi
./tools/build_release.sh
version=${tag#seagull-v}
apk="dist/$tag/BYD-Split-Seagull-$version.apk"
./tools/verify_release_apk.sh "$apk"
gh release create "$tag" "$apk" "dist/$tag/SHA256SUMS.txt" \
    --repo hix83/BYD-Split --verify-tag --draft --prerelease --latest=false \
    --title "BYD Split — Seagull $version" --notes-file "$notes"
# GitHub's digest must match the verified local APK before publishing the draft.
gh release view "$tag" --repo hix83/BYD-Split --json assets > "dist/$tag/uploaded-assets.json"
python3 - "$tag" "$apk" <<'PY'
from pathlib import Path
import sys, json, hashlib
tag, apk = sys.argv[1:]
assets = json.loads(Path('dist', tag, 'uploaded-assets.json').read_text())['assets']
asset = next(a for a in assets if a['name'] == Path(apk).name)
assert asset['state'] == 'uploaded'
assert asset['digest'] == 'sha256:' + hashlib.sha256(Path(apk).read_bytes()).hexdigest(), 'GitHub digest mismatch'
PY
gh release edit "$tag" --repo hix83/BYD-Split --draft=false --prerelease --latest=false
