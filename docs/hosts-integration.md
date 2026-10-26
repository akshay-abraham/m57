# Hosts-list integration review

Reviewed 26 October 2026 against `/home/akshay/hosts` at `7df0d76`.
This is the StevenBlack/hosts repository. The 0.5.0 source pass now implements
a list updater, category controls, local UDP DNS VPN and browser-domain guard.
No system hosts file was changed. Native compilation, live HTTPS updates and
phone verification remain pending; implementation is not proof of device coverage.

## Available sources and separate switches

Use the base list and individual category-only lists so each administrator
switch remains independent. Downloading a combined base-plus-category list for
each switch would unintentionally enable other categories and duplicate data.

| Control | Repository path | Primary HTTPS download |
| --- | --- | --- |
| Adware and malware | `hosts` | `https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts` |
| Adult-content domains | `alternates/porn-only/hosts` | `https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts` |
| Gambling | `alternates/gambling-only/hosts` | `https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts` |
| Social websites | `alternates/social-only/hosts` | `https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social-only/hosts` |
| Sites in the upstream fakenews category | `alternates/fakenews-only/hosts` | `https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-only/hosts` |

The combined lists are available if later useful, but are not needed to make
independent category switches. The base adware/malware list is itself combined;
the repo does not provide clean separate malware-only and adware-only switches.
Final enabled categories/defaults and wording still need a product decision.

Social-domain blocking can block whole services, including their normal feeds.
It does not implement the separate requirement to block only Reels while allowing
Instagram Home. Keep feature-level accessibility detection separate from
optional whole-site family categories. Hosts entries are domains, not full URL
paths, so they also cannot selectively block just a GitHub repository path.

## Implemented connection

- `family/FamilyCatalog.kt` defines independent categories and ordered HTTPS
  download candidates. `FamilyStore.kt` persists settings, counts, last sync,
  last successful source and failures, separately from actual VPN-running state.
- `FamilyRepository.kt` runs serial background work. Downloads are bounded,
  restricted to HTTPS with trusted redirect hosts and strict UTF-8, then parsed
  by `HostRules.validatedDownload`. Empty/HTML/oversized lists, inconsistent
  declared counts and major shrinkage are rejected before atomic cache writes.
  A failed mirror chain keeps the last working file and reports failure.
- `FamilySyncJob.kt` uses native JobScheduler for approximate 24-hour updates
  and manual sync on an available network. Job scheduling is not an exact-time
  guarantee. No upstream installer is run and `/etc/hosts` is never touched.
- `HostRules.Index` is one immutable matching snapshot shared by VPN and browser
  interception. It retains category labels, applies domain/subdomain boundaries,
  and gives administrator allow entries priority over category/custom blocks.
- `FamilyVpnService.kt` establishes a local split-route VPN for virtual IPv4 and
  IPv6 DNS addresses. It handles UDP DNS packets, returns NXDOMAIN for blocked
  names and forwards allowed raw queries using DnsResolver on a non-VPN underlying
  network. Requests are bounded and time out with SERVFAIL rather than invented
  answers. The updater app is exempted to prevent list downloads blocking
  themselves. No browsing query/history is persisted.
- The service is started after Android VPN approval with the VPN-appropriate
  foreground-service type and notification. Revocation clears requested state;
  a previously approved/requested filter is reconciled after app launch, boot
  or list updates. Global administrator pauses bypass matching while keeping
  DNS resolution available. No other VPN is silently started/replaced without
  the Android approval path.
- `BrowserDomains.java` and the accessibility service check only known address
  fields in supported browser packages. Matching sites show a family-category
  intervention, including non-shaming adult-content guidance. The browser
  switch is independent of VPN enablement and uses the same cached rules.
- `FamilyPages.kt` supplies category pages, examples, last-sync/error/source
  status, manual sync, explicit allow/block exceptions and source/license popups.
  Categories default off. Successful list sync and VPN/browser readiness are
  displayed separately. A partial cached rule set may run while an enabled
  category is missing; the UI and notification identify incomplete lists.

## Practical coverage and testing limits

The first VPN interface handles UDP DNS, not a full TCP/IP forwarding stack.
TCP DNS, cached/direct IP connections and browser Secure DNS/Private DNS can
fall outside the DNS interception path. This is not a guarantee of blocking all
network access to every listed site. The browser layer helps only where a
supported, visible address field is available. No TLS interception is used.
The source UI states these limits; no always-on lockdown support is advertised.

Android allows one active VPN per user/profile. This is a local domain filter,
not a remote privacy/encryption VPN. Network switching, IPv4/IPv6 behaviour,
foreground-service permission handling, reboot/revocation and all browser UI
IDs need real-device checks, starting on the Samsung M13/One UI 6/English UK.

Passed JVM checks: strict parsing, domain and URL boundaries, exceptions,
malformed/truncated/shrunken list rejection, browser resource boundaries,
IPv4/IPv6 DNS packet handling, response ID validation and IP/UDP checksums.
The real local source files also pass the download validator: 71,491 base,
76,793 adult, 6,712 gambling, 3,808 social and 2,187 fakenews valid domains.
Counts exclude invalid/system entries. This does not validate a live Android
VPN or an actual HTTP download.

Run these checks with `./scripts/check-jvm.sh /home/akshay/hosts`. Full Gradle
build/lint and phone tests are still blocked by unavailable SDK/Gradle downloads
and sandbox device access. See `DEVICE_TESTS.md` for the acceptance cases.

## Backups and attribution

The upstream README lists `http://sbc.io/hosts/...` mirror URLs. An attempted
HTTPS probe of `https://sbc.io/hosts/hosts` timed out in the review tool, so that
HTTPS endpoint is not yet a verified automatic fallback. Do not silently
downgrade to HTTP. The implementation tries raw GitHub, jsDelivr and that HTTPS upstream mirror
in order, accepting only validated content. Live accessibility of the CDN and
mirror remains unverified; no candidate is called a verified backup merely
because its URL is configured. Distinguish independent mirrors from URLs that
still depend on GitHub infrastructure.
The saved last-good copy supplies offline continuity, not a fresh remote update.

`license.txt` at the repo root is MIT, but the aggregated source table and
individual `data/**/update.json` / `extensions/**/update.json` records list
different source terms. For example, the checked extension metadata includes
CC BY 4.0 and MIT, and the base source table includes non-commercial terms.
Audit the relevant contributing sources before deciding which lists to bundle
or redistribute; do not describe every contributed list as MIT solely because
the aggregator's code has that license. Preserve attribution and show actual
source terms in the requested licenses popup.

References: [StevenBlack/hosts](https://github.com/StevenBlack/hosts),
[local source README](/home/akshay/hosts/readme.md),
[Android VPN API guide](https://developer.android.com/develop/connectivity/vpn).
