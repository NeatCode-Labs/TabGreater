# Building TabGreater

## Toolchain (locked)

AGP 8.13.0 · Gradle 8.13 · Kotlin 2.4.0 · KSP 2.3.11 · JDK 21 · compileSdk / targetSdk 36 · minSdk 26. Versions live in `gradle/libs.versions.toml`; bumping them needs compileSdk 37 / AGP 9.1 for several AndroidX artifacts, so do not bump casually.

## Flavours

Two product flavours share the same code and package name and differ only in what each store allows:

| Flavour | Channels | Differences |
|---|---|---|
| `foss` (default) | GitHub Releases, F-Droid | full feature set: exact widget alarms, one-tap battery-optimisation dialog |
| `play` | Google Play | drops `USE_EXACT_ALARM` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play policy); the battery row opens the system list instead. Donations are shown in both flavours (a tip that unlocks nothing is a peer-to-peer payment under Play's payments policy) |

```bash
./gradlew assembleFossDebug      # debug APK, package com.neatcode.tabgreater.debug
./gradlew assemblePlayDebug
./gradlew test                   # unit tests, every module
./gradlew :app:lintFossDebug     # lint (must stay at zero warnings)
```

`local.properties` (`sdk.dir=…`) is git-ignored; create it if Gradle cannot find the SDK.

## Modules

| Module | Contents |
|---|---|
| `:core:model` | pure Kotlin: `MarketKey` (`exchange:BASE/QUOTE`), `Market` with its `AssetClass` (crypto or tokenized stock), `Ticker`, `Candle`, watchlist types, design tokens, number formatting, backup codec |
| `:core:exchange` | pure Kotlin: `ExchangeAdapter` + Binance / Gate.io / Kraken / KuCoin / MEXC adapters, stock-token classification of their catalogues (`StockTokens`), WebSocket plumbing, rate limiting, candle aggregation |
| `:core:data` | Room (watchlists, markets, candle cache, ticker snapshots) and DataStore settings |
| `:core:live` | live market data (WebSocket fan-in), the widget refresh service and its alarms |
| `:feature:chart` | the KLineChart WebView bridge |
| `:widget` | Glance home-screen widget + configuration activity |
| `:app` | Compose UI, navigation, Koin wiring |

The chart library (KLineChart 10.0.3, Apache-2.0) is vendored at `app/src/main/assets/chart/vendor/klinecharts.js` — the **unminified** UMD build, copied byte-for-byte from the upstream npm release, with its LICENSE and NOTICE. No npm is involved in the build. `:app:verifyVendoredAssets` runs before every build and fails it if that file is not the exact upstream release recorded in `VENDORED-KLINECHART.md`.

### Chart host regression checks

`node tools/chart-regression.cjs` runs the actual vendored engine and production chart host in a headless browser, with a simulated native bridge and all browser network requests blocked. It covers offline market/period changes, identical-target refresh, late replies, abandoned retries, live updates and drawing restoration. It requires Node.js and Playwright available through normal module resolution (or `NODE_PATH`); `CHART_TEST_CHANNEL=msedge` selects an installed Edge instead of Playwright's Chromium. These are development-only prerequisites, not Android build dependencies.

Also check the signed APK on an emulator: load one market online, enable airplane mode, open another market in the same process, return to the first market, then restore connectivity and retry. Separately load a chart online and change its interval after disabling connectivity (interval controls are disabled once the chart becomes unavailable). Neither the outgoing candles nor their price axis should appear under the new target's header while loading or unavailable. Repeat using Back/watchlist navigation and a chart deep link. A cold offline restart separately verifies the watchlist's `Cached` label; a warm resume within ten minutes does not by itself turn a recently confirmed price into `Cached`.

## Release

`./gradlew assembleFossRelease` produces the R8-minified APK for GitHub Releases and F-Droid; `./gradlew bundlePlayRelease` the AAB for Google Play. Both local artifacts are signed with the key named in `keystore.properties` (git-ignored). This does not establish which signing certificate Google Play uses for delivered APKs or whether they can update a FOSS installation. F-Droid metadata (descriptions, changelogs, icon, screenshots) lives in `metadata/en-US/`. Releases on GitHub are signed with the NeatCode Labs key; its SHA-256 certificate fingerprint is published in the release notes so you can verify a FOSS APK with `apksigner verify --print-certs`. For ready-to-install builds from Google Play, F-Droid or GitHub, see [Installation](../README.md#installation).

Regenerating the launcher icon and the app-bar brand glyph from `art/launcher-logo.jpg`: `python tools/launcher_icon.py` (needs Pillow and numpy).
