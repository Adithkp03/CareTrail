#!/usr/bin/env bash
set -euo pipefail
# Review-only source assembly. Never pushes or changes a hosted branch.
HOME_PIN=73a0a2133a3e4bc9e12a4cb3a9659bb12592d807
AUDIO_PIN=4dbb9d564f31e31675db8809352f97474df82ce3
GOOGLE_PIN=5ff99c331837b21df72f07a3211b1ec6a623ae0b
ICON_PIN=d53f5cf317c43c24f1031cdbfab9d60a196f0e83

git config user.name 'CareTrail review build'
git config user.email 'review@localhost'
git fetch --no-tags origin "$AUDIO_PIN" "$GOOGLE_PIN"
git merge-base --is-ancestor "$HOME_PIN" HEAD
git merge-base --is-ancestor "$ICON_PIN" HEAD

if ! git merge --no-commit --no-ff "$AUDIO_PIN"; then
    conflicts=$(git diff --name-only --diff-filter=U)
    if [ "$conflicts" != 'android-native/app/build.gradle.kts' ]; then
        echo 'Unexpected source conflict; review build stopped.' >&2
        exit 1
    fi
    python3 - <<'PY'
from pathlib import Path
import re
path = Path('android-native/app/build.gradle.kts')
source = path.read_text()
pattern = re.compile(r'^<<<<<<<[^\n]*\n(.*?)^=======\n(.*?)^>>>>>>>[^\n]*\n', re.M | re.S)
blocks = list(pattern.finditer(source))
assert len(blocks) == 1, 'Unexpected exclusion conflict shape'
left, right = blocks[0].group(1, 2)
expected_home = ['exclude("com/caretrail/app/HomeLoadingScreenshotTest.class")', 'exclude("com/caretrail/app/HomeLoadingScreenshotTest\\$*")']
expected_audio = ['exclude("com/caretrail/app/AudioScreenScreenshotTest.class")', 'exclude("com/caretrail/app/AudioScreenScreenshotTest\\$*")']
assert [line.strip() for line in left.splitlines() if line.strip()] == expected_home, 'Unexpected Home conflict'
assert [line.strip() for line in right.splitlines() if line.strip()] == expected_audio, 'Unexpected audio conflict'
path.write_text(pattern.sub(lambda match: match.group(1) + match.group(2), source))
PY
    git add android-native/app/build.gradle.kts
fi
git commit --no-gpg-sign -m 'Temporary review assembly: audio owner and both screenshot exclusions'
# Google changes should merge cleanly. Any conflict fails rather than guesses.
git merge --no-gpg-sign --no-ff --no-edit "$GOOGLE_PIN"
git diff --check
test -z "$(git diff --name-only --diff-filter=U)"
{
    printf 'review_only=true\nicon_pr56=%s\nhome_pr57=%s\naudio_pr58=%s\ngoogle_pr59=%s\n' "$ICON_PIN" "$HOME_PIN" "$AUDIO_PIN" "$GOOGLE_PIN"
    printf 'combined_tree='; git rev-parse HEAD^{tree}
    printf 'runner_source='; git rev-parse HEAD~2
    printf 'clinical_pr60=excluded\ncache_pr55=excluded\n'
} > review-source-manifest.txt
git archive --format=zip HEAD -o review-source.zip
