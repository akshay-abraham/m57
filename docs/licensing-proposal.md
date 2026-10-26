# M57 licensing decision — applied 26 October 2026

Akshay wants protection against both closed-source distributed forks and
modified hosted versions whose changes are kept private. Akshay authorised AGPLv3 for original M57 material. The applied component
map is in [LICENSING.md](../LICENSING.md); it preserves other contributors’
licenses and copyright notices.

## Recommendation

The applied choice is AGPL-3.0-only for original M57 modules, retaining GPL-3.0 for Curbox-derived
portions and preserving Apache-2.0 notices for the Gradle wrapper. Distribute
the resulting combination with an explicit file/component license map and both
license texts. Keep existing upstream copyright/provenance notices.

GPLv3 section 13 expressly permits combination with AGPLv3. It also says the
GPL terms continue to apply to the GPL-covered part; the special AGPL network
requirements apply to the combination. This permission is not a blanket right
to erase Curbox's license or label inherited GPL-only code solely AGPL.
[GPLv3 section 13](https://www.gnu.org/licenses/gpl-3.0.en.html#section13),
[GNU compatibility FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#v3Notwithstanding).

GPLv3 already requires corresponding source and GPL-compliant terms when
modified covered APKs are distributed. AGPLv3 adds source-offer requirements
for users interacting remotely with a modified network service. This extra
condition is particularly relevant to a future hosted M57 component; it adds
less to the ordinary Android APK distribution case.
[GNU's AGPL explanation](https://www.gnu.org/licenses/why-affero-gpl.html).

Licensing an Android client under AGPL does not by itself settle the license
of a separate remote API or Lyra's web application. Analyse any combined work
and separately license original hosted components if that is the desired
policy. Keep the separate Lyra application outside this M57 decision unless
Akshay explicitly chooses otherwise.

Both GPL and AGPL remain free-software licenses: other people can fork and
commercially distribute compliant versions. They do not reserve all sales or
competition to the original author. Trademark/name rules are a separate
decision, not an extra restriction to append to GPL-covered code.

## Concrete work before changing the notices

1. Inventory the exact copied/adapted Curbox files, original M57 files,
   mixed files and third-party wrapper/assets/data terms. Check copyright
   ownership/permissions for any existing contributions being relicensed.
2. Choose AGPL-3.0-only versus AGPL-3.0-or-later for original work. Do not infer
   an or-later grant merely because a license text contains its standard
   instructions for authors.
3. Prepare both license texts, a license map and accurate per-file SPDX
   expressions. A mixed-file notice must reflect actual rights, not a global
   search/replace of every GPL header.
4. Update README, NOTICE, in-app notices, build/source instructions and the
   release pipeline together. Link each APK to its corresponding source.
5. Add an appropriate source offer in any future modified hosted AGPL component
   and check its actual deployment obligations before release.

Current state: original M57 work is AGPL-3.0-only, inherited and mixed terms
are mapped in LICENSING.md, and full AGPL/GPL/Apache/StevenBlack MIT texts
are bundled. No remote release has been published yet.
