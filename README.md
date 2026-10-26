# M57 for Android

A small, native Android 14+ Curbox fork that redirects supported AI apps to
Lyra’s learning harness. The web app and this Android app are separate programs.
This directory is **GPLv3**, including the Lyra changes. See [LICENSE](LICENSE)
and [NOTICE](NOTICE) for attribution and distribution terms.

## Behavior

- Opening a known AI app shows a warm, translucent glass card, with neutral text,
  orange accents and orange-only Lyra icons. There are no purple or blue UI accents.
  The app behind the card is blurred using Android's cross-window blur, with a
  more opaque readable fallback when the device disables blur. The primary button opens Lyra immediately, or
  automatically after five seconds. The administrator can choose 3–15 seconds.
- The initial catalog covers ChatGPT, Gemini, Claude, Copilot, Perplexity,
  DeepSeek, Grok, Mistral, Poe, Character.AI, Merlin, Meta AI, Nova, Genie, ChatOn,
  Chat Smith, Pi, Replika and Qwen Studio. Add more package IDs in
  PIN-protected settings. This is an explicit catalog, not universal AI detection.
- WhatsApp and WhatsApp Business remain usable. A visible conversation header
  titled `Meta AI` triggers the redirect. Messages mentioning Meta AI, the home
  search prompt and the AI shortcut alone do not trigger it.
- In the Google app, a supported toolbar/assistant title of `Gemini` triggers
  the redirect. Ordinary Google searches remain usable.
- Chrome, Chrome Beta/Dev and Chromium address bars are checked for the supported
  AI domains. Lyra’s own site is allowed. Other browsers, incognito surfaces that
  hide URLs, embedded webviews and unsupported UI layouts are not covered.
- The original app is covered, not force-stopped. The countdown and a tap share
  one launch path. Leaving the app or locking the screen cancels the countdown.
  If launching fails, the overlay stays and the button allows another attempt.
- Each popup picks a short local message without repeating the immediately
  previous one, inspired by the chat greeting. AI messages describe accessing
  models through Lyra's safer guided learning environment. Copy stays still
  while the popup is open, so the countdown does not interrupt reading.
- YouTube Shorts shows a **reminder only**, with **Leave Shorts** and **Dismiss**.
  It has no countdown, no automatic navigation and no Lyra link. Leave Shorts
  performs Back only when tapped; dismissing leaves the current video in place.
  Supported native clients are YouTube, ReVanced and Morphe. Chrome Shorts URLs
  are also recognized when the address bar is exposed. Home, ordinary videos,
  Shorts search results and the Shorts navigation tab alone do not trigger it.
  The reminder appears once per visit; a two-second exit separates visits and
  avoids reappearing during transient player updates. AI redirects remain separate.

The destination defaults to `https://lyra-edu.vercel.app/chat`. A school can set
its own HTTPS Lyra address in administrator settings. The launcher tries the
configured PWA package, an installed Chromium WebAPK matching that URL, Chrome,
then the default browser. Install Lyra from Chrome first for standalone PWA use.
Android/Chrome control URL handling, so verify this on the actual device. If
automatic detection does not find the PWA, set its installed package ID.

## Find and connect the Lyra PWA

M57 is the installed Android blocker. Lyra is the web app it opens. The Android
launcher, App info and accessibility service now use the name **M57**, matching
the landing page. The Android package ID stays `com.lyra.blocker`, so an update
signed with the same key preserves the installed app and its PIN/settings.

1. In Chrome on the phone, open the Lyra address saved in M57. The default is
   `https://lyra-edu.vercel.app/chat`.
2. Open Chrome's menu and choose **Install app**, **Add to Home screen**, or
   **Install and create shortcut → Install**, depending on the Chrome version.
3. Find **Lyra** in the home screen/app drawer and open it. An installed PWA
   normally opens without Chrome's address bar. A plain shortcut may open a tab.
4. Open **M57 → Manage protection → PIN → Advanced app rules → Find installed
   Lyra PWA**. Pick the detected Lyra entry and tap **Save settings**.
5. Tap **Open Lyra** to test. Leaving the PWA package field blank uses automatic
   detection. If no matching PWA is installed/detectable, Chrome is the fallback.

The home screen also includes **Set up or find the Lyra PWA**, with these
instructions and an **Open in Chrome** button. Detection uses only installed
Chromium WebAPKs that advertise support for the configured HTTPS address; it
does not request a list of every installed app.

See [Chrome's Android installation instructions](https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DAndroid&hl=en).

## App info protection

When protection is enabled, M57 recognizes its own App info header together with
App info action controls in `com.android.settings`, leaves that page and opens
Lyra. Every detected attempt is handled; repeated attempts are rate limited to
one redirect per second. It checks the current screen again before retrying.
Other apps' App info pages, app lists and Settings search results are unaffected.

To intentionally change Android settings, use **Manage protection → PIN → Open
App info · allow 60 seconds**. This permits maintenance without pausing AI rules.
After 60 seconds the guard resumes, including when App info is still open. The
five-minute protection pause also permits access. Releasing administrator/device
owner policies grants a 60-second window for removal; the PIN-protected button
can reopen it. Restarting the phone ends the temporary access window.

This accessibility guard is best effort. OEM layouts with different resource IDs
can be missed, and it depends on accessibility remaining enabled. Android's
accessibility controls remain available. Device-owner policies provide the
supported force-stop, data-clear and uninstall restrictions described below.

Copilot rules cover both its legacy package and the current Microsoft Copilot
package, which also includes Office features; the whole package is redirected.
The dedicated Meta AI app is also the companion for Meta glasses, so its whole
app is redirected. Remove or narrow those catalog entries before deployment if
those non-chat functions must remain available.

## Build and install

Use JDK 17, Android SDK platform 35 and build-tools 35.0.0. Gradle 8.13 and the
Android/Kotlin plugins are pinned to the Curbox checkout’s versions. No runtime
libraries are added; the only explicit library dependency is JUnit for tests.

```sh
cd android/lyra-blocker
# Set ANDROID_HOME or create ignored local.properties containing sdk.dir=/path/to/sdk
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `M57 Android app` GitHub Actions workflow runs those checks and uploads
a debug APK. Release builds use shrinking; signing must be configured privately
before distribution. Do not commit signing keys. `./gradlew :app:assembleRelease`
produces an unsigned release artifact until signing is configured.

Open **M57 → Set up protection**, agree to the access disclosure, set
and confirm a 6–12 digit administrator PIN, then enable Lyra under Android
Accessibility settings. On Android 14, a sideloaded APK may require **App info →
menu → Allow restricted settings** before accessibility can be enabled. Android
asks for that permission itself; the app does not grant itself access.

## Resilience and overrides

The blocker is a system-bound accessibility service. It does not need a polling
loop, foreground-service notification, overlay permission, root, Shizuku, usage
access, Firebase, or the network permission. Android manages service binding and
reconnection. Protection resumes when the service reconnects; five-minute pauses
use elapsed time plus the boot count, so rebooting ends a pause. Pause expiration
also schedules a recheck when no new screen events arrive. PIN derivation runs
off the UI thread with salted PBKDF2-HMAC-SHA256 (120,000 rounds); failed PIN
attempts are rate limited. Settings never trust incoming intent extras.

**Ordinary personal device:** optionally enable administrator access in
**Manage protection → Add removal protection**. This adds a deactivation step
before uninstalling and requests no wipe, lock or password policies. Android
Settings can revoke accessibility and administrator access; force-stop, safe
mode and app removal remain possible. This mode is a focus aid, not a tamperproof
school enforcement system.

**Managed school/test device:** on a device eligible for device-owner provisioning,
provision the receiver explicitly, then open PIN-protected settings and choose
**Apply managed-device protection**. This disables uninstall, force-stop and
clearing data for the blocker using supported device-owner policies. The app
does not provision itself. Its accessibility guard redirects its own detected App info page; PIN-protected maintenance access remains available. Device-owner enrollment
usually requires a newly set-up device without existing accounts; do not reset a
personal device just to test this app.

```sh
# Only on an eligible, intentionally managed test device:
adb shell dpm set-device-owner com.lyra.blocker/.ProtectionAdminReceiver
```

An administrator can use **Manage protection → Override: pause for 5 minutes**,
or **Release removal protection** to remove policies and device ownership before
uninstalling. Keep the PIN safe; there is no unauthenticated PIN-reset shortcut.
For a debug APK marked `testOnly`, ADB can recover a test device with
`adb shell dpm remove-active-admin com.lyra.blocker/.ProtectionAdminReceiver`.
Do not rely on that command as recovery for a release build. Managed-device
enrollment/recovery and resistance to safe mode or accessibility revocation need
deployment-specific device testing; those guarantees are not claimed here.

## Detection and privacy limits

Only app identity, supported header IDs and browser address-bar IDs are read;
message bodies are neither read nor stored. No analytics, account system, database
or background network requests are included. Local settings and the PIN hash
are private app preferences, excluded from Android backup.

WhatsApp/Gemini resource IDs are third-party UI details and may change. A contact
renamed exactly `Meta AI` can match the same header; translated titles, inline
`@Meta AI` interactions and future UI layouts may be missed. An English header
rule is the initial implementation, not a verified universal classifier. Check
and update `BlockPolicy` and the header IDs against the installed app versions.
No real-device or emulator verification has been performed in this workspace.

## Verification

Unit tests cover app rules, Meta AI headers, normal-message/shortcut false
positives, Google search false positives, domain boundaries, destination checks
and the retained Curbox matcher, plus Shorts players/URLs, false positives and
reminder visit handling. This workspace could not run Gradle because its
distribution download was blocked and no Android SDK is installed. The CI
workflow is provided but has not been run from this workspace.

Before deployment, use the [device acceptance checklist](DEVICE_TESTS.md). In
particular, validate Meta AI screen IDs, Android 14 launch behavior, installed
PWA routing and managed-device recovery on the intended phone.

Platform references:
[accessibility services](https://developer.android.com/guide/topics/ui/accessibility/views/service),
[background activity launch rules](https://developer.android.com/guide/components/activities/background-starts),
[device policy APIs](https://developer.android.com/reference/android/app/admin/DevicePolicyManager).

New catalog package IDs were checked against the Play listings for
[Nova](https://play.google.com/store/apps/details?id=com.scaleup.chatai),
[Genie](https://play.google.com/store/apps/details?id=co.appnation.geniechat),
[ChatOn](https://play.google.com/store/apps/details?id=ai.chat.gpt.bot),
[Chat Smith](https://play.google.com/store/apps/details?id=com.smartwidgetlabs.chatgpt),
[Pi](https://play.google.com/store/apps/details?id=ai.inflection.pi),
[Replika](https://play.google.com/store/apps/details?id=ai.replika.app), and
[Qwen Studio](https://play.google.com/store/apps/details?id=ai.qwenlm.chat.android).

Blur uses Android's [cross-window blur APIs](<https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setBlurBehindRadius(int)>).
Device GPU support and battery saver can disable this effect; the app listens
for those changes and increases opacity/dimming to preserve readability.
