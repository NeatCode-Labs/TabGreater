# TabGreater on F-Droid

**Status: live** at **https://f-droid.org/packages/com.neatcode.tabgreater/** since 26 September 2026
(first listed version: 1.1.0, version code 6). The inclusion request,
[fdroiddata!46639](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/46639), was opened on
24 August 2026 and merged on 24 September 2026. The listing is a reproducible build: F-Droid ships
the same APK as GitHub Releases, signed with the NeatCode Labs key.

This document records how the submission was done and what keeps the listing working — see
[Maintenance](#maintenance) for the part that matters on every release.

F-Droid differs from every other store in the one way that decides the whole submission: it
**builds the app itself, from source, on its own machines**. The work is not "package an APK" but
"make the build reproduce on a machine that has never seen this project".

The submission was a merge request against **https://gitlab.com/fdroid/fdroiddata** that added a single
file, `metadata/com.neatcode.tabgreater.yml`. The listing text and images are read from *this*
repository's fastlane layout (`metadata/en-US/`) — never put descriptions in fdroiddata.

## What F-Droid requires

- [x] Free licence, declared (GPL-3.0-or-later, `LICENSE` at the root).
- [x] Public source with an **annotated tag per release** (`vX.Y.Z`).
- [x] No proprietary dependencies — nothing from `com.google.android.gms`, no Firebase, no
      closed-source SDK.
- [x] No tracking, no advertising.
- [x] `dependenciesInfo { includeInApk = false; includeInBundle = false }` in `app/build.gradle.kts`
      — the dependency blob AGP adds by default is signed by Google and is not reproducible.
- [x] The build must not need anything outside the repository: no keystore (`assembleFossRelease`
      produces an unsigned APK without one), no `local.properties` (their `ANDROID_HOME` is enough).

There is **no rule about AI-assisted or LLM-generated code** in F-Droid's inclusion policy.

## The vendored chart library

The app draws its chart with **KLineChart** (Apache-2.0) inside a WebView, so its JavaScript bundle
ships in the assets, next to a readable port of some KLineChart Pro drawing templates and the app's
own page code. A *minified* bundle is what a reviewer objects to — it is not a readable, diffable
form. That is handled, and the answer is short:

- The asset is `app/src/main/assets/chart/vendor/klinecharts.js` — the **unminified** UMD build,
  copied byte-for-byte out of the upstream npm release.
- `docs/VENDORED-KLINECHART.md` records the tarball URL, the integrity hash npm publishes for it,
  the file's SHA-256, and how to reproduce the extraction in four commands.
- `:app:verifyVendoredAssets` runs before every build and **fails it** if the file is not that exact
  release. The claim is enforced by the build, not by a promise in a README.
- Nothing transforms the file, and no npm runs during the build.
- `app/src/main/assets/chart/overlays.js` is third-party code too: 17 drawing templates of
  **KLineChart Pro** (Apache-2.0), hand-ported from its TypeScript to plain JavaScript. It is
  readable, commented source — the preferred form for modifying it — so it is not checksummed like
  a vendored release; nothing is built or downloaded for it. Its provenance (upstream commit, the
  blob SHA of every ported file) and the list of changes are in `docs/VENDORED-KLINECHART.md`,
  "Adapted overlay templates".

A reviewer may still hold that the preferred form is KLineChart's TypeScript source. If that comes
up, the fallback is a `prebuild:` stanza building the bundle from a pinned checkout with a committed
`package-lock.json` — but do not volunteer it. It drags Node into F-Droid's build environment and
becomes a permanent source of breakage on every toolchain bump.

## Reproducible builds: decide before the first acceptance

`Binaries:` plus `AllowedAPKSigningKeys:` make F-Droid compare its own build against the APK
published on GitHub Releases and, when they match, ship **our** APK with **our** signature. Users can
then move between the GitHub download and F-Droid without uninstalling.

**The key choice is one-way.** Ship once under F-Droid's key and switching to ours later forces every
F-Droid user to uninstall and reinstall. Roughly 1500 recipes in fdroiddata use reproducible builds;
it is the normal target, not an exotic one.

Verification happens on F-Droid's buildserver at publish time, not in merge-request CI. But the
build job's artifacts contain both APKs (`tmp/*.apk` is their build, `tmp/binaries/*.apk` is ours),
so the comparison can be made by hand from the CI run:

```bash
# after downloading the fdroid build job artifacts
python - <<'PY'
import zipfile
a = zipfile.ZipFile('tmp/binaries/com.neatcode.tabgreater_1.binary.apk')  # ours, signed
b = zipfile.ZipFile('tmp/com.neatcode.tabgreater_1.apk')                  # theirs, unsigned
na = {i.filename: i for i in a.infolist()}
nb = {i.filename: i for i in b.infolist()}
print('mismatched entries:', [n for n in na.keys() & nb.keys() if na[n].CRC != nb[n].CRC])
PY
```

For 1.0.0 all 1368 entries matched; the files differed only by the 4 096-byte APK Signing Block,
which is exactly what `fdroid verify` strips before comparing. No `postbuild` fix was needed. Every
release up to 1.1.0 was checked the same way while the merge request was open, with no mismatch.

The published listing confirms the outcome: the 1.1.0 APK served from `f-droid.org/repo/` has the
same SHA-256 as the GitHub release asset, and the package page states that it is built and signed by
the original developer. F-Droid's own record of the check is the 1.1.0 build log,
https://monitor.f-droid.org/builds/log/com.neatcode.tabgreater/6, which records "compared built
binary to supplied reference binary successfully".

## The JDK trap

F-Droid's builders run **JDK 21** and toolchain auto-provisioning is disabled, so a project pinned to
`jvmToolchain(17)` fails outright with *"Cannot find a Java installation … matching languageVersion=17"*.

The common workaround — a `prebuild` sed bumping 17 to 21 — **must not be used here**: it makes
F-Droid compile different sources than the published APK was built from, which rules out a
reproducible build. The project itself is therefore on JDK 21 (`gradle/libs.versions.toml`).

## The recipe

```yaml
AntiFeatures:
  NonFreeNet:
    en-US: Prices and candles are fetched from the public market-data APIs of Binance,
      Gate.io, Kraken, KuCoin and MEXC, whose server software is not free. The add-pair
      screen also queries CoinGecko's public ranking at most once a day for its "popular
      pairs" chips; that call is optional and falls back to a cached or built-in list.
      The app has no backend of its own, no account and no API keys, and works with
      any subset of the five exchanges.
Categories:
  - Market & Price
License: GPL-3.0-or-later
AuthorName: NeatCode Labs
WebSite: https://tabgreater.com/
SourceCode: https://github.com/NeatCode-Labs/TabGreater
IssueTracker: https://github.com/NeatCode-Labs/TabGreater/issues
Changelog: https://github.com/NeatCode-Labs/TabGreater/releases

AutoName: TabGreater

RepoType: git
Repo: https://github.com/NeatCode-Labs/TabGreater
Binaries: 
  https://github.com/NeatCode-Labs/TabGreater/releases/download/v%v/TabGreater-%v-foss.apk

Builds:
  - versionName: 1.1.0
    versionCode: 6
    commit: 0511d3e51454417365703970ee46488f88c48206
    subdir: app
    gradle:
      - foss

AllowedAPKSigningKeys: 71befee992ee607eabcdbc69542c7f7be4613c91171e599a47d4ea5594b3a635

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.1.0
CurrentVersionCode: 6
```

That is the recipe as merged. It was filed for 1.0.0, and five more releases (1.0.1 to 1.1.0) went out
while the merge request was open. 1.0.1 to 1.0.4 each added a `Builds:` block; after 1.0.4 the
maintainer asked for old versions to be removed, and 1.1.0 then replaced the remaining block, so the
merged recipe carries only the current one. The live file is
[`metadata/com.neatcode.tabgreater.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.neatcode.tabgreater.yml)
in fdroiddata; later versions are added there, not here.

Every one of these details was learned by having a CI job fail on it or a maintainer ask for it:

| Field | Rule |
| --- | --- |
| `AntiFeatures` | Goes at the very top. An app that depends on non-free network services (here the exchanges' market-data APIs) is asked for `NonFreeNet` with a reason. `fdroid rewritemeta` re-wraps the prose at its own width, so copy the wrapping from the failing job's diff. |
| `Categories` | Comes from a fixed schema list. `Money` is **not** in it; a price watchlist is `Market & Price`. The failing `schema validation` job prints the whole list. |
| `AutoName` | Required — `checkupdates` regenerates the file and diffs it against yours. |
| `Binaries` | Must be wrapped onto the next line (`Binaries: ` + newline + two-space indent). That is what `fdroid rewritemeta` emits, and it diffs against yours. |
| `commit` | A **full commit hash**, never a tag or branch. The maintainer will ask. |
| `gradle` | `[foss]` — the `play` flavour must never be built by F-Droid. |
| `UpdateCheckMode` | `Tags` plus `AutoUpdateMode: Version` makes F-Droid's update check add a build for each new annotated tag to the recipe itself, with no merge request from us. |

## Submitting

Done once, kept as a record of what the process asks for.

1. Fork **https://gitlab.com/fdroid/fdroiddata** (once).
2. Branch off **upstream's** current `master`, not your fork's — a stale fork makes a noisy MR.
   `fdroiddata` has ~143 000 commits, and a `--depth 1` clone **cannot be pushed** ("shallow update
   not allowed"), so clone with `--filter=blob:none` (~244 MB) instead.
3. Add `metadata/com.neatcode.tabgreater.yml`, commit with `-s`, push the branch to your fork.
4. Open the merge request titled **`New app: TabGreater`**, and pick the **"App Inclusion"**
   merge-request template. A free-form description gets sent back. Tick its boxes honestly and
   explain any you leave unticked.
5. Wait for the pipeline. Nine jobs run: `check source code`, `schema validation`, `tools check
   scripts`, `fdroid rewritemeta`, `fdroid lint`, `git redirect`, `checkupdates`, `fdroid build` and
   `check apk` (which scans the built APK for known non-free classes and extra signing blocks).
   Fix failures on the same branch — never open a second MR.
6. Review takes weeks, not days: this one took a month. Besides the recipe review, a volunteer did a
   static review of the APK and source and a launch test in an emulator.
7. If a new version is released while the request is open, update the recipe on the same branch and
   say so in the merge request.

Testing the recipe locally with `fdroid build` before opening the MR saves a round trip, but needs a
Linux environment; the pipeline does the same job.

## Maintenance

- Every new **annotated tag** is picked up by F-Droid's update check (`UpdateCheckMode: Tags`), which
  adds the build to the recipe; no merge request is needed for an ordinary release.
- Publish the signed APK on GitHub Releases under the name the `Binaries:` pattern expects
  (`TabGreater-<version>-foss.apk` on tag `v<version>`), or the reproducible-build comparison cannot
  find it. Build it from a clean clone of the tag with JDK 21, so it matches what F-Droid builds.
- Add `metadata/en-US/changelogs/<versionCode>.txt` in this repository for each release — that is the
  "What's New" F-Droid clients show.
- If the build recipe needs changing (new AGP, new NDK, a new prebuild step), that is a merge request
  against the upstream `master` of `fdroiddata`.
- Watch https://f-droid.org/packages/com.neatcode.tabgreater/ after each tag. A new version takes a
  few days to appear: the update check has to notice the tag, then the build has to run and be
  published. (1.1.0 came in with the inclusion merge on 24 September and was listed on 26 September
  2026.) If a build breaks, or does not reproduce the published APK, the new version simply never
  appears there, silently.
