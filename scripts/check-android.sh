#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
if [[ -f "$HOME/.config/m57/android-env.sh" ]]; then
    source "$HOME/.config/m57/android-env.sh"
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" || -z "${ANDROID_HOME:-}" || ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]]; then
    echo 'Install the toolchain first: bash scripts/setup-android.sh' >&2
    exit 1
fi
java -version
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
    :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease \
    --no-daemon --max-workers=2 --continue --console=plain
