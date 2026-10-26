#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
set -euo pipefail

log_file=''
while [[ $# -gt 0 ]]; do
    case "$1" in
        --log)
            if [[ $# -lt 2 || -z "$2" || "$2" == --* ]]; then
                echo 'Usage: bash scripts/check-android.sh [--log FILE]' >&2
                exit 2
            fi
            log_file=$2
            shift 2
            ;;
        -h|--help)
            echo 'Usage: bash scripts/check-android.sh [--log FILE]'
            echo 'Streams progress to the terminal. --log also saves stdout and stderr to FILE (overwritten).'
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 2
            ;;
    esac
done

run_checks() {
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
}

if [[ -n "$log_file" ]]; then
    mkdir -p -- "$(dirname -- "$log_file")"
    echo "Saving live check output to: $log_file"
    # pipefail preserves a failing check's exit status while tee streams both outputs.
    run_checks 2>&1 | tee -- "$log_file"
else
    run_checks
fi
