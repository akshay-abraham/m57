# Building and publishing M57

The public repository is intended to be `akshay-abraham/m57`. Repository creation
and a successful Actions run must be confirmed separately; these files alone do
not mean an APK has been built or published.

## Local checks

On Linux x86_64, install the user-local JDK 17 and Android toolchain with:

```sh
bash scripts/setup-android.sh
source ~/.config/m57/android-env.sh
bash scripts/check-android.sh
```

The setup verifies archive checksums, uses the official Temurin and Google
downloads, and asks you to review Android SDK licenses. It keeps system Java
unchanged. The check script runs debug and release unit tests, lint and APK
assembly with two workers. Without release signing environment variables, its
release APK is unsigned; signing and certificate verification stay separate.
Full lint reports are under `app/build/reports/`. A failing check does not create
or push a release tag.

Use JDK 17, Android SDK platform 35 and build-tools 35.0.0. Set `ANDROID_HOME`
to the installed SDK. Run:

```sh
./scripts/check-jvm.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

For an exact source link, set `M57_SOURCE_REF` to the full 40-character commit
SHA when building. An archive build can use the commit listed in its release.
Unversioned local builds identify themselves as such in About.

## Signing continuity

Keep application ID `com.lyra.blocker` and the established signing key when
updating an existing installation. A different key cannot update that APK in
place. Preserve and back up the existing keystore privately before changing
distribution. A CI-generated debug key is not a stable production identity.
Akshay confirmed the installed app is a debug APK with no release key yet. A
separate private RSA-4096 release key is now stored locally under
`~/.local/share/m57/signing`, with owner-only directory/file permissions. The
existing local debug keystore was preserved there too; its fingerprint has not
been compared with the installed phone APK. Credentials are in the private
`credentials.json`, with public certificate/digest alongside. None of this
private directory belongs in Git. Back it up privately before using the key.

The new release key cannot update a debug-signed installation in place. Keep
that installation intact until its configuration/protection has been deliberately
migrated. No uninstall, data clearing or signing-key replacement was performed.

Configure repository Actions secrets privately, never in a commit or issue:

| Secret | Value |
| --- | --- |
| `M57_KEYSTORE_BASE64` | Base64 of the existing distribution keystore |
| `M57_STORE_PASSWORD` | Its store password |
| `M57_KEY_ALIAS` | Its signing alias |
| `M57_KEY_PASSWORD` | Its key password |
| `M57_SIGNER_SHA256` | Expected public certificate SHA-256 digest, with or without colons |

After the public repository exists, configure its Actions secrets without printing
passwords or passing them as command-line arguments:

```sh
python3 scripts/upload-signing-secrets.py --repository akshay-abraham/m57
```

This setup command sends the key to the explicitly named repository. It requires
GitHub CLI network/auth access and repository administration. It is never run
by pull-request checks.

For local signed builds, use `M57_KEYSTORE` (absolute path) and the corresponding
password/alias environment variables. With no signing configuration Gradle may
produce an unsigned release file; it must not be distributed as an installable
release. The release workflow requires secrets and verifies the APK signature
and expected certificate before publishing. Signing material is restored only
on the tag job and removed afterwards; pull-request checks receive none of it.

## Tag releases

Update Android versionCode/versionName and add `docs/releases/<version>.md`.
Run the checklist on the intended device and record results honestly. Push a
matching version tag, for example `v0.6.0`, to run tests, lint, debug/release
builds, signature verification and automatic GitHub prerelease publication.
Only push a release tag when its source is ready for distribution. The initial
pipeline marks builds as prereleases because device acceptance is outstanding.
No tag or APK has been published as part of preparing this workflow.

Each release includes a signed APK, a `git archive` of its exact source,
SHA256SUMS, public certificate details and licenses/notices. Source archives
contain the build scripts, wrapper and complete license texts. The APK's About
page links the exact commit. Keep source archives available with their APKs.
Modified network deployments must also meet the applicable AGPL source offer.

The companion Lyra code is maintained in its own repository and is not included
in this Android source archive. See [the handoff protocol](handoff.md).
