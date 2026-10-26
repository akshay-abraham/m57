#!/usr/bin/env sh
set -eu

cd -- "$(dirname -- "$0")/.."
check_dir=$(mktemp -d "${TMPDIR:-/tmp}/m57-jvm-checks.XXXXXX")
trap 'rm -rf "$check_dir"' EXIT HUP INT TERM

# These pure policy checks do not require Gradle, an Android SDK or network access.
if command -v javac >/dev/null 2>&1; then
    javac --release 17 -d "$check_dir" \
        app/src/main/java/com/lyra/blocker/PasswordCredential.java \
        app/src/main/java/com/lyra/blocker/ProtectionTiming.java \
        app/src/main/java/com/lyra/blocker/BackgroundHelp.java \
        app/src/main/java/com/lyra/blocker/AccessibilitySettingsPolicy.java \
        app/src/main/java/com/lyra/blocker/family/*.java \
        app/src/test/java/com/lyra/blocker/*Checks.java
else
    # Some stripped runtimes retain jdk.compiler without the javac executable/ct.sym.
    java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$check_dir" \
        app/src/main/java/com/lyra/blocker/PasswordCredential.java \
        app/src/main/java/com/lyra/blocker/ProtectionTiming.java \
        app/src/main/java/com/lyra/blocker/BackgroundHelp.java \
        app/src/main/java/com/lyra/blocker/AccessibilitySettingsPolicy.java \
        app/src/main/java/com/lyra/blocker/family/*.java \
        app/src/test/java/com/lyra/blocker/*Checks.java
fi

for suite in PasswordCredentialChecks ProtectionTimingChecks BackgroundHelpChecks AccessibilitySettingsChecks FamilyRuleChecks DnsPacketChecks; do
    if [ "$suite" = FamilyRuleChecks ] && [ "$#" -gt 0 ]; then
        java -cp "$check_dir" "com.lyra.blocker.$suite" "$1"
    else
        java -cp "$check_dir" "com.lyra.blocker.$suite"
    fi
done
