# M57 licensing

M57 original code and documentation are licensed under **GNU AGPL version 3
only (AGPL-3.0-only)**. Copyright © 2026 Akshay K Rooben Abraham and contributors.
The top-level LICENSE contains its full text. No warranty is provided.

This project combines original M57 work with Curbox-derived GPLv3 material
under GPLv3 section 13 and AGPLv3 section 13. Curbox's covered portions retain
their GPL terms; the AGPL network-source requirements apply to the combination
as provided by those sections. This is not a relicensing of Curbox's copyright.

## Component map

- Original M57 Java/Kotlin files: AGPL-3.0-only unless their SPDX header below
  identifies retained or mixed material.
- KeywordMatcher.kt and its retained test, ShortsPolicy.kt and its retained
  test, and adapted res/xml/accessibility_service.xml: GPL-3.0-only. Preserve
  Curbox attribution. Modifications adding AGPL-covered material to these
  files must be marked as a mixed work rather than erase the GPL notice.
- LyraAccessibilityService.kt, BackgroundAccess.kt and family/BrowserDomains.java:
  GPL-3.0-only AND AGPL-3.0-only, reflecting retained/adapted Curbox-derived
  portions and original M57 additions. This means both sets of applicable
  obligations, not a choice to discard one of the licenses.
- gradlew, gradlew.bat and gradle/wrapper/*: Apache-2.0, with existing notices
  preserved. See LICENSES/Apache-2.0.txt.
- Existing Lyra logo/launcher PNG copies: retained GPL-3.0-only terms for these
  bundled copies; no trademark rights are granted.
- Other original M57 configuration, resources, tests and docs: AGPL-3.0-only.
- StevenBlack aggregator MIT license: preserved verbatim in
  LICENSES/MIT-StevenBlack.txt and app assets/StevenBlack-MIT.txt. Credit:
  Steven Black and the StevenBlack/hosts contributors,
  https://github.com/StevenBlack/hosts. Domain lists are not bundled in the APK.
- Contributing hosts sources retain their separate terms. Metadata and links
  are in app/src/main/assets/HOSTS-SOURCES.txt; do not assume every aggregated
  data source is MIT. Refer to each source for its terms.

## Source for distributed builds

For every APK, publish its exact corresponding source, build scripts and
license notices, linked from the same release. Include installation information
where required by the applicable licenses. Modified network-service versions
must meet the applicable AGPL source-offer requirements; this does not by itself
license the separate Lyra application or an independent remote API.

Preserve the in-app license/attribution page. See LICENSE for AGPLv3 and
LICENSES/GPL-3.0-only.txt for the inherited GPLv3 text.
