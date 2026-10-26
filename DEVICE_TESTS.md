# Android 14 acceptance checklist

These are required device checks, not results already observed. Run on an
Android 14 phone/emulator and repeat core flows on the school’s OEM devices.

1. Install the APK, confirm disclosure, set a PIN and enable accessibility.
   Confirm the home screen shows protection enabled. Canceling consent or PIN
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
8. Pause with the PIN. Leave a blocked app foregrounded without touching it for
   five minutes: protection must return. Restart the blocker process during a
   pause and verify expiry. Reboot and confirm the pause ends.
9. Enter five wrong PINs; verify a 30-second wait, including after reopening the
   activity. Background the app while PIN verification is running: returning
   must not unlock settings. Confirm URL/rule edits, pause and policy controls
   cannot be reached using arbitrary launcher intent extras or saved state.
10. Restart the phone with accessibility enabled; verify service rebind and normal
    blocking after unlock. Check OEM battery restrictions, screen readers,
    large text, landscape, display scaling, split screen, keyboards and navigation.
11. Enable ordinary administrator protection and verify the removal confirmation.
    Verify the documented Android Settings revocation path remains available.
12. On an eligible test device only, provision device ownership, apply managed
    protection, then verify uninstall/force-stop/clear-data restrictions. Use the
    PIN to pause and release policies/ownership; verify uninstall works afterward.
    Test PIN-loss recovery before any production enrollment.
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
    page closes and Lyra opens. Repeat several times, including through Settings
    → Apps. Other apps' App info, the app list/search, and accessibility controls
    must stay usable. Record the OEM's actual header/action IDs if detection fails.
19. Use **Manage protection → PIN → Open App info · allow 60 seconds**. Verify App
    info is usable and AI rules still work. Stay there past 60 seconds: the guard
    should resume. Repeat after service reconnection and reboot. Test the
    five-minute pause and the maintenance window after releasing removal protection.
20. Install Lyra through Chrome, then use M57's PWA guide and detector. Check
    that selecting a detected package requires Save settings, blank means automatic,
    and an uninstalled/missing PWA shows clear instructions with Chrome fallback.
    Test a different saved Lyra host, multiple matching PWAs and a plain shortcut.
