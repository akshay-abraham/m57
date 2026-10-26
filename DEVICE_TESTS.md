# Android 14 acceptance checklist

These are required device checks, not results already observed. Run on an
Android 14 phone/emulator and repeat core flows on the school’s OEM devices.

First 0.4.0 target: Samsung Galaxy M13 4G, One UI 6, English (UK). No checks
below have yet been run on that device. Version 0.1–0.3 update checks are
historical baselines; the current password-migration checks are listed below.

1. Install the APK, confirm disclosure, set a 12–128 character password and enable accessibility.
   Confirm the home screen shows protection enabled. Canceling consent or password
   setup must leave protection inactive.
2. Open ChatGPT and Gemini from launcher, recents, notifications and assistant
   shortcuts. Confirm the Lyra shield covers the app, a tap redirects immediately,
   and the default five-second countdown redirects once. Repeat rapid switches.
3. Install Lyra as a Chrome PWA. Verify redirection opens the installed standalone
   PWA at `/chat`; test explicit PWA-package configuration, uninstall the PWA,
   and verify Chrome fallback. Repeat with Chrome disabled and another browser.
   With no handler installed, verify the shield stays with retry instructions.
4. In WhatsApp and WhatsApp Business, open Meta AI and verify the redirect.
   Check home-screen search, shortcut, ordinary chats, a message containing
   `Meta AI`, quoted messages, contact names and group names. Record actual
   resource IDs/locales. Inline `@Meta AI` is not covered by the initial rule.
5. Open Gemini through the Google app and overlay/assistant invocation. Confirm
   the supported header is exposed and detected. Search Google for `Gemini` and
   verify ordinary search results remain usable.
6. Open the catalog AI domains in Chrome (including a subdomain); verify blocking.
   Open Lyra and lookalike URLs such as `chatgpt.com.example.com`; verify no
   block. Check URL editing, keyboard, tab switching and private browsing.
7. During countdown, go Home, press Back, switch apps, rotate, open quick settings,
   turn the screen off and unlock. No stale redirect should occur outside the
   blocked context. Reopening a blocked app must show a new countdown.
8. Pause with the administrator password. Check all three presets, a custom
   future time and an indefinite pause. Verify automatic resumption without a
   new foreground event, after service restart and after phone reboot. An
   indefinite pause must stay paused until an administrator resumes it. Verify
   cancelling the custom picker does not change the schedule. Same-boot clock
   changes must not extend/shorten elapsed waits; check the documented reboot
   dependence on wall time separately.
9. Enter five wrong passwords; verify a 30-second wait, including after reopening
   the activity. Verify escalation at ten/fifteen failures and the cap. Background
   the app while verification or password creation is running: returning
   must not unlock settings. Confirm URL/rule edits, pause and policy controls
   cannot be reached using arbitrary launcher intent extras or saved state.
10. Restart the phone with accessibility enabled; verify service rebind and normal
    blocking after unlock. Check OEM battery restrictions, screen readers,
    large text, landscape, display scaling, split screen, keyboards and navigation.
11. Enable ordinary administrator protection and verify the removal confirmation.
    Verify the documented Android Settings revocation path remains available.
12. On an eligible test device only, provision device ownership, apply managed
    protection, then verify uninstall/force-stop/clear-data restrictions. Use the
    password to pause and release policies/ownership; verify uninstall works afterward.
    Recovery is not implemented; validate a deliberate recovery plan before production enrollment.
13. Measure APK size, idle CPU/battery and event processing on the actual device.
    No size, latency, reboot-resilience or tamper-resistance figures are claimed
    until these measurements and checks have been completed.
14. Open the native YouTube Shorts viewer. Verify the glass reminder appears,
    waiting at least 20 seconds does not redirect, and Dismiss keeps the current
    video playing. Swipe through several Shorts: no repeated popup. Leave the
    viewer for at least two seconds, return, and verify a new reminder. Check
    YouTube Home, search results and ordinary videos remain unaffected. Repeat
    on supported ReVanced/Morphe builds and Chrome `/shorts/` URLs. Leave Shorts
    must perform Back only on a deliberate tap and never open Lyra.
15. Inspect the UI with several apps behind it, large text, landscape and a screen
    reader. Confirm warm glass, orange-only icons, neutral text and no purple/blue
    accents. Toggle battery saver/system blur while the popup is visible: the
    opaque fallback must stay readable. Reopen repeatedly and check message
    variation without a consecutive repeat. AI countdown behavior stays intact.
16. Install this update over version 0.1.0 using the same signing key. Verify PIN/settings remain intact,
    version 0.2.0 is shown, and the seven new AI packages receive redirects.
17. Update to version 0.3.0 with the same signing key. Verify **M57** appears in
    the launcher, App info and Accessibility settings, while Lyra remains the
    PWA destination. Existing PIN/settings must remain intact.
18. With accessibility/protection enabled, long-press M57 → App info. Verify the
    page closes and M57's administrator guidance opens. Repeat several times,
    including through Settings → Apps. Other apps' App info, the app list/search,
    and other services' accessibility controls
    must stay usable. Record the OEM's actual header/action IDs if detection fails.
19. Use **Administrator access → Settings → Device access → Open App info · allow 60 seconds**. Verify App
    info is usable and AI rules still work. Stay there past 60 seconds: the guard
    should resume. Repeat after service reconnection and reboot. Test the
    chosen protection pause and the maintenance window after releasing removal protection.
20. Install Lyra through Chrome, then use M57's PWA guide and detector. Check
    that selecting a detected package requires Save settings, blank means automatic,
    and an uninstalled/missing PWA shows clear instructions with Chrome fallback.
    Test a different saved Lyra host, multiple matching PWAs and a plain shortcut.
21. Verify the public Home has no administrator dock. After unlock, check all
    three labelled tabs, selected states, search (including multiword searches),
    category breadcrumbs, Android Back and Lock & leave. Scroll down/up and
    near the bottom; check dock visibility, keyboard overlap, touch exploration,
    large fonts and landscape. Planned categories must not imply filtering is active.
22. Update a configured 0.3.0 installation with the same signing key. Verify the
    old PIN first, then create the new password; an incorrect PIN must not
    permit migration. The old PIN must stop working after migration. Preserve
    destination/package/countdown settings. Cancel/background migration and
    verify it cannot complete or unlock a stale screen.
23. On each of the six supported manufacturers, check **Allow background running**
    opens Android's real battery-optimisation request. Deny/dismiss it and ensure
    the UI still says it is not allowed. Grant it and verify the PowerManager
    status after returning/re-unlocking. Check already-exempt devices, unavailable
    OEM activities and standard settings fallbacks. Extra startup controls must
    not be marked granted just because their screen opened. Verify collapsed
    Troubleshooting opens the matching dontkillmyapp.com guide. On other brands,
    the manufacturer-specific section must not appear.
24. Open M57's own accessibility detail page from Samsung Installed apps, Settings
    search and any available shortcut. Verify the narrow guard returns to M57's
    administrator guidance, including repeated visits. Initial enablement,
    authorised 60-second maintenance and a protection pause must remain usable.
    Check the guard resumes after expiry while the page stays open. Inspect the
    actual Settings package/resource IDs if detection fails; never mark this
    device supported based solely on synthetic snapshot tests. Other services,
    Installed apps lists, notifications settings and other App info pages must
    remain usable. Record any OS revocation/bypass cases rather than claiming
    universal prevention.
25. Enable family categories one at a time. Check initial downloads, manual sync,
    category counts/examples, last successful time, HTTPS mirror fallback and
    the approximate daily job. Simulate unavailable/invalid/partial/shrunken
    downloads and confirm the last good cache remains active after restart.
    No remote URL being configured is proof its mirror works on the phone.
26. Start the family VPN through Android approval. Deny and grant it separately;
    only a running service should show active. Check the foreground notification,
    IPv4/IPv6 A/AAAA/HTTPS queries, allowed DNS replies, NXDOMAIN for listed
    domains and SERVFAIL on resolver timeout/offline/overload. Check Wi-Fi/mobile
    changes, DNS answers larger than the MTU, service death, boot, pause/resume
    and VPN revocation/replacement. Verify ordinary web traffic is not routed
    to a remote M57 server. TCP DNS, encrypted/direct/cached paths are explicit
    limits; do not claim they are covered by a UDP packet unit test.
27. Enable the separate browser guard and visit synthetic/custom blocked domains
    in Chrome, Samsung Internet and each claimed browser family. Verify only
    trusted address fields trigger it, leaving Home/search/text unaffected.
    Check compassionate adult guidance, Back/Home/administrator actions, screen
    lock and URL-hidden/private modes. Test supported packages/resource IDs on
    actual versions before claiming complete coverage.
28. Verify allow/block exception precedence across VPN and browser layers and
    category changes. Turning off a category must remove its rules, while other
    enabled categories remain. Test subdomain boundaries, IDNs, no-list/partial
    list states, cancelled VPN approval and no existing VPN consent. Record the
    one-VPN-per-profile tradeoff. Check no query/domain-visit history is written
    to logs or app storage, and that source/license metadata is available.
