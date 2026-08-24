# Submitting TabGreater to F-Droid

F-Droid differs from every other store in the one way that decides the whole submission: it
**builds the app itself, from source, on its own machines**. The work is not "package an APK" but
"make the build reproduce on a machine that has never seen this project".

The submission is a merge request against **https://gitlab.com/fdroid/fdroiddata** that adds a single
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

The app draws its chart with **KLineChart** (Apache-2.0) inside a WebView, so one JavaScript file
ships in the assets. A *minified* bundle is what a reviewer objects to — it is not a readable,
diffable form. That is handled, and the answer is short:

- The asset is `app/src/main/assets/chart/vendor/klinecharts.js` — the **unminified** UMD build,
  copied byte-for-byte out of the upstream npm release.
- `docs/VENDORED-KLINECHART.md` records the tarball URL, the integrity hash npm publishes for it,
  the file's SHA-256, and how to reproduce the extraction in four commands.
- `:app:verifyVendoredAssets` runs before every build and **fails it** if the file is not that exact
  release. The claim is enforced by the build, not by a promise in a README.
- Nothing transforms the file, and no npm runs during the build.

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
which is exactly what `fdroid verify` strips before comparing. No `postbuild` fix was needed.

## The JDK trap

F-Droid's builders run **JDK 21** and toolchain auto-provisioning is disabled, so a project pinned to
`jvmToolchain(17)` fails outright with *"Cannot find a Java installation … matching languageVersion=17"*.

The common workaround — a `prebuild` sed bumping 17 to 21 — **must not be used here**: it makes
F-Droid compile different sources than the published APK was built from, which rules out a
reproducible build. The project itself is therefore on JDK 21 (`gradle/libs.versions.toml`).

## The recipe

```yaml
Categories:
  - Market & Price
License: GPL-3.0-or-later
AuthorName: NeatCode Labs
WebSite: https://neatcodelabs.com/
SourceCode: https://github.com/NeatCode-Labs/TabGreater
IssueTracker: https://github.com/NeatCode-Labs/TabGreater/issues
Changelog: https://github.com/NeatCode-Labs/TabGreater/releases

AutoName: TabGreater

RepoType: git
Repo: https://github.com/NeatCode-Labs/TabGreater
Binaries: 
  https://github.com/NeatCode-Labs/TabGreater/releases/download/v%v/TabGreater-%v-foss.apk

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: 3a178be9037a49626a82e197343e9dd39cf1a6ab
    subdir: app
    gradle:
      - foss

AllowedAPKSigningKeys: 71befee992ee607eabcdbc69542c7f7be4613c91171e599a47d4ea5594b3a635

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

Every one of these details was learned by having a CI job fail on it:

| Field | Rule |
| --- | --- |
| `Categories` | Comes from a fixed schema list. `Money` is **not** in it; a price watchlist is `Market & Price`. The failing `schema validation` job prints the whole list. |
| `AutoName` | Required — `checkupdates` regenerates the file and diffs it against yours. |
| `Binaries` | Must be wrapped onto the next line (`Binaries: ` + newline + two-space indent). That is what `fdroid rewritemeta` emits, and it diffs against yours. |
| `commit` | A **full commit hash**, never a tag or branch. The maintainer will ask. |
| `gradle` | `[foss]` — the `play` flavour must never be built by F-Droid. |
| `UpdateCheckMode` | `Tags` plus `AutoUpdateMode: Version` picks up new annotated tags without a metadata change. |

## Submitting

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
6. Review takes weeks, not days.

Testing the recipe locally with `fdroid build` before opening the MR saves a round trip, but needs a
Linux environment; the pipeline does the same job.

## After acceptance

- Every new **annotated tag** triggers a rebuild; nothing else is needed.
- Add `metadata/en-US/changelogs/<versionCode>.txt` in this repository for each release — that is the
  "What's New" F-Droid clients show.
- Keep the app's release APK name matching the `Binaries:` pattern, or the reproducible-build
  comparison cannot find it.
- If the build recipe needs changing (new AGP, new NDK, a new prebuild step), that is a fresh merge
  request against `fdroiddata`.
- Watch https://f-droid.org/packages/com.neatcode.tabgreater/ after each tag: if a build breaks, the
  new version simply never appears there, silently.
