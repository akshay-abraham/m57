# M57 for Android

This is the standalone M57 project, extracted from Lyra's
`android/lyra-blocker` directory at Lyra commit `125a1ca`. Version 0.6.0 source
includes the new administrator UI, guided setup, editable AI rules, short-video
controls, family DNS/browser filtering and Lyra handoff. Android compilation,
live downloads and real-device acceptance remain pending; see
[feature status](docs/status.md) and [device checks](DEVICE_TESTS.md).

A small, native Android 14+ Curbox fork that redirects supported AI apps to
Lyra’s learning harness. The web app and this Android app are separate programs.
Original M57 work is **AGPLv3-only**; Curbox-derived portions retain GPLv3.
See [LICENSING.md](LICENSING.md), [LICENSE](LICENSE) and [NOTICE](NOTICE) for
the component terms and attribution. StevenBlack’s full MIT license is included
in [LICENSES/MIT-StevenBlack.txt](LICENSES/MIT-StevenBlack.txt) and the in-app notices.

## Administrator navigation

The public Home stays simple. Unlock **Administrator access** to use the
three-tab dock: **Overview**, **Controls** and **Settings**. Controls offers
searchable categories for AI redirection, short videos, family browsing and
device access. Breadcrumbs and Android Back return to the parent page; leaving
M57 locks the administrator space.

The labelled dock hides on downward scroll, returns on upward scroll or near
the bottom, and hides while the keyboard is open. Scroll hiding is disabled
when touch exploration is enabled. Family controls are wired to the local DNS
VPN and browser-domain layer. Short-video controls offer blocking or reminders,
with per-platform switches and experimental extra platforms.
All new Android flows still require compilation and device verification.

Password recovery remains a design task: there is no reset service, self-service
reset or master password in this build. Keep the password safe.

## Family domains: DNS VPN and browser layer

Open **Administrator access → Controls → Family browsing**. Categories start
off: enable the lists you want, then choose **Start local VPN filter** and accept
Android's VPN approval. The browser switch is separate and needs M57's
accessibility service. Category pages show domain counts, last sync, stale/error
state, examples, sources and licensing. Administrator allow/block exceptions
apply to both layers; allow entries take priority, including subdomains.

The VPN is an on-device, split-route UDP DNS filter. It uses Android's resolver
on the underlying network; it does not send web traffic to an M57 server or
provide a traffic-encryption VPN. Another VPN cannot run in the same profile.
Private DNS, browser Secure DNS, cached IPs/direct-IP connections and TCP DNS
can bypass or fall outside this first implementation. Always-on lockdown is
not supported for this DNS-only design. Known URL fields provide extra browser
interception in Chrome-related browsers, Samsung Internet, Firefox variants,
Firefox Focus and Opera; hidden URLs and unverified layouts are not universal
coverage. These limits are shown in the Family source/coverage page.

Enabled lists sync approximately every 24 hours and on request, using ordered
HTTPS raw GitHub/CDN/upstream-mirror candidates. Validation checks domains,
upstream counts, size and unexpected shrinkage; atomic writes retain the last
working copy on failed updates. Live mirror availability has not been verified
inside this restricted environment. [Integration details](docs/hosts-integration.md).

No domain lists are bundled in the APK. Attribution metadata and the aggregator's
MIT text are included, with contributing source terms kept distinct. Original M57 work is AGPLv3-only with inherited GPLv3 portions retained;
[LICENSING.md](LICENSING.md) records the applied arrangement.

## Background running

For Samsung, Xiaomi, Oppo, Vivo, Realme and OnePlus, **Settings → Device access**
includes **Allow background running**. This opens Android's system request to
ignore battery optimisation (with a standard settings fallback if unavailable).
The displayed exemption status is read from Android, not inferred from opening
the dialog. Manufacturer startup/background settings are a separate action.

An expandable **Troubleshooting & extra help** section retains the matching
[dontkillmyapp.com guide](https://dontkillmyapp.com/) for the detected vendor.
OEM settings and guide steps vary by software version. No native permission or
background-kill behaviour has yet been verified on the target phone.

## Behavior

- Opening a known AI app shows a warm, translucent full-screen intervention, with neutral text,
  orange accents and orange-only Lyra icons. There are no purple or blue UI accents.
  The app behind the screen is blurred using Android's cross-window blur, with a
  more opaque readable fallback when the device disables blur. The primary button opens Lyra immediately, or
  automatically after five seconds. The administrator can choose 3–15 seconds.
- The initial catalog covers ChatGPT, Gemini, Claude, Copilot, Perplexity,
  DeepSeek, Grok, Mistral, Poe, Character.AI, Merlin, Meta AI, Nova, Genie, ChatOn,
  Chat Smith, Pi, Replika and Qwen Studio. Add more package IDs in
  password-protected settings. This is an explicit catalog, not universal AI detection.
- WhatsApp and WhatsApp Business remain usable. A visible conversation header
  titled `Meta AI` triggers the redirect. Messages mentioning Meta AI, the home
  search prompt and the AI shortcut alone do not trigger it.
- In the Google app, a supported toolbar/assistant title of `Gemini` triggers
  the redirect. Ordinary Google searches remain usable.
- Known address fields in supported browser packages are checked for AI domains.
  Lyra’s own site is allowed. Hidden URLs, embedded webviews and unsupported UI
  layouts can be missed; coverage needs device verification.
- The original app is covered, not force-stopped. The countdown and a tap share
  one launch path. Leaving the app or locking the screen cancels the countdown.
  If launching fails, the overlay stays and the button allows another attempt.
- Each popup picks a short local message without repeating the immediately
  previous one, inspired by the chat greeting. AI messages describe accessing
  models through Lyra's safer guided learning environment. Copy stays still
  while the popup is open, so the countdown does not interrupt reading.
- YouTube Shorts and full-screen Instagram Reels have separate switches. The
  administrator chooses **Block** (default) or **Reminder**. Block offers Back
  and Home; reminder additionally allows Dismiss and appears once per visit.
  Neither mode opens Lyra automatically. YouTube Home/ordinary videos and
  Instagram feeds are not intentionally blocked by these viewer rules.
- Experimental extra platforms include Snapchat Spotlight, Facebook Reels,
  TikTok and whole-app Josh/Josh Lite. The extra group starts off; per-platform
  choices are collapsed behind secondary controls. Josh includes chat/calls,
  so its whole-app tradeoff is disclosed. See [device checks](DEVICE_TESTS.md).
- Built-in app/domain rules can be disabled individually and restored. Custom
  packages/domains are supported. Meta protection has a grouped experimental
  switch and collapsed service choices for WhatsApp, Instagram, Facebook,
  Messenger and the dedicated Meta AI app. Header recognition varies by version.
- AI redirects send a service hint for a **new conversation only**. Lyra's
  authenticated companion endpoint resolves a current available model; manual
  choices and existing conversations retain their selected model. The APK has
  no model IDs. See [handoff and school configuration](docs/handoff.md).
- A separate Settings switch guards the M57 repository URL path in supported
  browsers. Global pause and authorised maintenance permit source review.
  This does not block other GitHub projects or change the open-source license.

The destination defaults to `https://lyra-edu.vercel.app/chat`. A school can set
its own HTTPS Lyra address in administrator settings. The launcher tries the
configured PWA package, an installed Chromium WebAPK matching that URL, Chrome,
then the default browser. Install Lyra from Chrome first for standalone PWA use.
Android/Chrome control URL handling, so verify this on the actual device. The saved PWA package must pass the same origin/scope checks as automatic
discovery. A different school’s PWA is not selected.

## Find and connect the Lyra PWA

M57 is the installed Android blocker. Lyra is the web app it opens. The Android
launcher, App info and accessibility service now use the name **M57**, matching
the landing page. The Android package ID stays `com.lyra.blocker`, so an update
signed with the same key preserves the installed app and its password/settings.

1. In Chrome on the phone, open the Lyra address saved in M57. The default is
   `https://lyra-edu.vercel.app/chat`.
2. Open Chrome's menu and choose **Install app**, **Add to Home screen**, or
   **Install and create shortcut → Install**, depending on the Chrome version.
3. Find **Lyra** in the home screen/app drawer and open it. An installed PWA
   normally opens without Chrome's address bar. A plain shortcut may open a tab.
4. Open **M57 → Administrator access → Controls → AI redirection → PWA and advanced app rules → Find installed
   Lyra PWA**. Pick the detected Lyra entry and tap **Save AI settings**.
5. Tap **Open Lyra** to test. Leaving the PWA package field blank uses automatic
   detection. If no matching PWA is installed/detectable, Chrome is the fallback.

The home screen also includes **Set up or find the Lyra PWA**, with these
instructions and an **Open in Chrome** button. Detection uses only installed
Chromium WebAPKs with matching HTTPS origin and path scope, discovered through
HTTPS handlers and launcher metadata. Plain shortcuts can use browser fallback.

See [Chrome's Android installation instructions](https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DAndroid&hl=en).

## App info protection

When protection is enabled, M57 recognizes its own App info header together with
App info action controls in `com.android.settings`, leaves that page and opens
M57's administrator guidance. The draft accessibility-detail guard also requires
M57's exact service description and a switch alongside its title; it is not yet
verified on the target Samsung Galaxy M13 4G / One UI 6 / English (UK). Every detected attempt is handled; repeated attempts are rate limited to
one redirect per second. It checks the current screen again before retrying.
Other apps' App info pages, app lists and Settings search results are unaffected.

To intentionally change Android settings, use **Administrator access → Settings → Device access → Open
App info · allow 60 seconds**. This permits maintenance without pausing AI rules.
After 60 seconds the guard resumes, including when App info is still open. An administrator-selected protection pause also permits access. Releasing administrator/device
owner policies grants a 60-second window for removal; the password-protected button
can reopen it. Restarting the phone ends the temporary access window.

This accessibility guard is best effort. OEM layouts with different resource IDs
can be missed, and it depends on accessibility remaining enabled. Other services' accessibility controls remain available; the guard cannot
guarantee that Android never revokes M57. Device-owner policies provide the
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
cd /path/to/m57
# Set ANDROID_HOME or create ignored local.properties containing sdk.dir=/path/to/sdk
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `M57 Android app` GitHub Actions workflow runs those checks and uploads
a debug APK. Release builds use shrinking; signing must be configured privately
before distribution. Do not commit signing keys. The [signed tag workflow](docs/releases.md)
checks tests/lint, the certificate identity and exact source before creating a
prerelease. Without signing configuration a local release artifact is unsigned.

Open **M57 → Set up M57**, agree to the access disclosure, then set
and confirm a 12–128 character administrator password. Under **Settings →
Device access**, enable M57 in Android Accessibility settings. Existing
installations verify their old PIN once before creating a password. On Android 14, a sideloaded APK may require **App info →
menu → Allow restricted settings** before accessibility can be enabled. Android
asks for that permission itself; the app does not grant itself access.

## Resilience and overrides

The AI/Shorts blocker is a system-bound accessibility service without a polling
loop. Family DNS filtering adds a foreground VPN service and notification;
list updates use Android JobScheduler and network access. Root, Shizuku, usage
access, Firebase and a separate overlay permission are not used. Android manages service binding and
reconnection. Protection resumes when the service reconnects, subject to the administrator's
chosen pause. Timed pauses use elapsed time during the same boot and a saved wall-clock
deadline after reboot. An indefinite pause persists until an administrator
resumes protection. Pause expiration
also schedules a recheck when no new screen events arrive. Password derivation runs
off the UI thread with salted PBKDF2-HMAC-SHA256 (600,000 rounds). Legacy
PIN verification retains its original 120,000 rounds only for migration. Failed
attempts trigger escalating waits: 30 seconds after five failures, doubling
every five further failures up to 30 minutes. During one boot, changing the wall
clock does not shorten the wait. After reboot, waits depend on the saved wall
deadline; this is not a guarantee against changing the system clock across a reboot. Settings never trust incoming intent extras.

**Ordinary personal device:** optionally enable administrator access in
**Administrator access → Settings → Device access → Add removal protection**. This adds a deactivation step
before uninstalling and requests no wipe, lock or password policies. Android
Settings can revoke accessibility and administrator access; force-stop, safe
mode and app removal remain possible. This mode is a focus aid, not a tamperproof
school enforcement system.

**Managed school/test device:** on a device eligible for device-owner provisioning,
provision the receiver explicitly, then open password-protected settings and choose
**Apply managed-device protection**. This disables uninstall, force-stop and
clearing data for the blocker using supported device-owner policies. The app
does not provision itself. Its accessibility guard redirects its own detected App info page; password-protected maintenance access remains available. Device-owner enrollment
usually requires a newly set-up device without existing accounts; do not reset a
personal device just to test this app.

```sh
# Only on an eligible, intentionally managed test device:
adb shell dpm set-device-owner com.lyra.blocker/.ProtectionAdminReceiver
```

An administrator can use **Overview → Protection schedule** to pause for 15 minutes, one hour,
three hours, a custom end date/time or until manually resumed. Use **Settings →
Device access → Release removal protection** to remove policies and device ownership before
uninstalling. Keep the password safe; there is no unauthenticated password-reset shortcut.
For a debug APK marked `testOnly`, ADB can recover a test device with
`adb shell dpm remove-active-admin com.lyra.blocker/.ProtectionAdminReceiver`.
Do not rely on that command as recovery for a release build. Managed-device
enrollment/recovery and resistance to safe mode or accessibility revocation need
deployment-specific device testing; those guarantees are not claimed here.

## Detection and privacy limits

Only app identity, supported header IDs and browser address-bar IDs are read;
message bodies are neither read nor stored. Family browser protection reads
only known address-bar fields, and the DNS service processes queries transiently
without saving browsing history. Enabled hosts lists are downloaded over HTTPS
on manual sync and an approximate 24-hour schedule. No analytics or account
system is included. Settings, password hashes and cached domain lists stay in
private app storage, excluded from Android backup.

WhatsApp/Gemini resource IDs are third-party UI details and may change. A contact
renamed exactly `Meta AI` can match the same header; translated titles, inline
`@Meta AI` interactions and future UI layouts may be missed. An English header
rule is the initial implementation, not a verified universal classifier. Check
and update `BlockPolicy` and the header IDs against the installed app versions.
No real-device or emulator verification has been performed in this workspace.

## Verification

New password hashing/migration checks and protection timing/cooldown checks
passed on the available JVM. Full Android compilation, lint and device checks
remain pending; see [the exact verification limits](docs/status.md).

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
