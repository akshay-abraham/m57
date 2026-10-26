# M57 0.6.2 implementation status

Updated 26 October 2026. Akshay's normal terminal now has JDK 17/SDK 35;
its previous debug/release tests and APK assembly completed, while lint exposed
two API-analysis errors. Those errors have targeted documented fixes in source.
The restricted agent runner still cannot start Gradle's file-lock networking.
Full signed Gradle/CI success and device acceptance must be recorded separately.

| Area | Source implementation |
| --- | --- |
| Navigation | Public Home; admin Overview/Controls/Settings; search, breadcrumbs, keyboard/scroll-aware dock |
| Credentials | 12–128 character passwords, confirmation/guidance, verified old-PIN migration, PBKDF2 and escalating waits |
| Schedules | 15-minute, one-hour, three-hour, custom calendar/date/time and indefinite pause |
| Device setup | Resumable steps, accessibility disclosure, optional removal protection, native battery prompt and six-vendor troubleshooting |
| Settings guard | Narrow own App info/accessibility-detail recognition and authenticated maintenance window |
| AI | Editable built-in/custom apps/domains, grouped experimental Meta service controls |
| Short videos | Block by default or reminder; YouTube and Instagram controls; experimental extra platforms |
| Interventions | Full-screen blur/fallback, smooth ring, rotating copy, separate video/family actions |
| Family browsing | Five independent categories, HTTPS mirror chain, validation, atomic cache, daily/manual sync, exceptions, local UDP DNS VPN and browser guard |
| Lyra handoff | Scoped WebAPK discovery, stable source hints, current available model resolved by companion Lyra code only for new chats |
| Openness | AGPL original work, retained Curbox GPL, Apache wrapper, full StevenBlack MIT and contributing-source metadata |
| Distribution | Signed tag workflow, certificate continuity checks, exact source archive, checksums and About source link |

Account/password recovery is deliberately excluded. A packaged Lyra/Bubblewrap
app, parent app, remote family control, expanded DNS transport handling and
additional platform coverage remain future work. All ideas stay in Akshay's
Lyra to-do list; they have not been silently dropped.

## Verification observed here

- JVM credential, migration, cooldown, pause, OEM selection and narrow Settings
  matching checks pass. Java uses source/target 17 on the available stripped JDK
  25 compiler, which warns that release-17 API compatibility is not proven.
- Actual local StevenBlack lists pass parsing/validation: 71,491 base, 76,793
  adult, 6,712 gambling, 3,808 social and 2,187 upstream fakenews domains.
- DNS IPv4/IPv6 UDP parsing, query/answer checks, checksums, malformed input,
  failure responses, browser/domain boundaries, media/Meta rules, WebAPK scope
  and repository URL boundaries pass pure JVM checks.
- Companion Lyra typecheck, full ESLint and all 14 unit-test files pass,
  including source validation and dynamic model-suggestion checks.
- Android XML parsing, diff whitespace, license-byte checks and release metadata
  checks pass. These checks do not compile Kotlin or run an Android VPN.

The Gradle command `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
failed at downloading Gradle 8.13 with `java.net.SocketException: Operation not
permitted`. This environment has no Android SDK; no APK, native lint result or
real-device success has been produced. Live HTTPS list fallbacks, standalone PWA
launch, VPN networking and Samsung M13/One UI 6/English UK recognition still need
the [device acceptance checklist](../DEVICE_TESTS.md).

The repository-publication result is recorded in the local Lyra work log. A
prepared workflow is not proof of a successful GitHub run or published release.

## First GitHub build and 0.6.1 correction

Akshay created the public repository and ran the v0.6.0 release workflow. The
provided CI log confirms the debug app's Kotlin and Java compilation completed.
Unit-test Java compilation then failed on `Files.readString`, which is absent
from the configured Android API. The helper now uses explicit UTF-8 decoding of
`Files.readAllBytes`. Version 0.6.1 / code 7 provides a new release tag without
rewriting v0.6.0. Full CI and real-device acceptance are still outstanding.

Publication was attempted using `gh repo create akshay-abraham/m57 --public
--source=. --remote=origin --push`; the CLI could not connect to api.github.com.
No origin/public repository, tag or release was created by that attempt. Release
secrets remain local. The default Lyra Turbopack build likewise hit a restricted
local port-binding operation; successful static checks do not replace that build.

## 0.6.2 review and signed-release automation

- VPN consent is checked before foreground startup. Documented configured-VPN
  eligibility and DNS flag combinations have narrowly scoped lint suppressions.
- DNS chooses the active validated non-VPN network instead of the latest callback;
  loss/shutdown clear selection. Regression checks reject mismatched DNS operations
  and question types. Countdown/dock drawing objects are reused.
- Cloud and device-transfer exclusions are explicit for private app data.
- All current main Java/Kotlin files compile against SDK 35 using cached compiler
  tools outside Gradle. Eight JVM suites pass on real JDK 17; release-preparation
  tests pass; the established signing certificate passes and an incorrect one fails.
- Standalone lint with the cached debug model exits 0 (0 errors, 5 warnings). The
  cached merged manifest/classes are older artifacts, so this does not replace a
  fresh debug/release Gradle run. No current signed APK is claimed from that check.
- The local signed-build command gates output on all Gradle checks and signature
  verification, then includes exact source/checksums. No old APK is substituted.
- GitHub's Prepare a signed release workflow accepts a version/notes, increments
  versionCode, preserves prior notes, commits/tags and dispatches the signed job.
  The job reuses established encrypted Actions secrets and checks the certificate
  before building and before publishing. Automatic generated notes are included.
- Workflow upload/execution and secrets provisioning require GitHub connectivity
  outside the restricted runner. Source keys are never stored in the repository.
