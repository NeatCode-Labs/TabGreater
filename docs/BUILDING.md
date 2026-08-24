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
| `:core:model` | pure Kotlin: `MarketKey` (`exchange:BASE/QUOTE`), `Ticker`, `Candle`, watchlist types, design tokens, number formatting, backup codec |
| `:core:exchange` | pure Kotlin: `ExchangeAdapter` + Binance / Gate.io / Kraken / KuCoin / MEXC adapters, WebSocket plumbing, rate limiting, candle aggregation |
| `:core:data` | Room (watchlists, markets, candle cache, ticker snapshots) and DataStore settings |
| `:core:live` | live market data (WebSocket fan-in), the widget refresh service and its alarms |
| `:feature:chart` | the KLineChart WebView bridge |
| `:widget` | Glance home-screen widget + configuration activity |
| `:app` | Compose UI, navigation, Koin wiring |

The chart library (KLineChart 10.0.2, Apache-2.0) is vendored at `app/src/main/assets/chart/vendor/klinecharts.js` — the **unminified** UMD build, copied byte-for-byte from the upstream npm release, with its LICENSE and NOTICE. No npm is involved in the build. `:app:verifyVendoredAssets` runs before every build and fails it if that file is not the exact upstream release recorded in `VENDORED-KLINECHART.md`.

## Release

`./gradlew assembleFossRelease` produces the R8-minified APK for GitHub Releases and F-Droid; `./gradlew bundlePlayRelease` the AAB for Google Play. Both are signed with the key named in `keystore.properties` (git-ignored). F-Droid metadata (descriptions, changelogs, icon, screenshots) lives in `metadata/en-US/`. Releases on GitHub are signed with the NeatCode Labs key; its SHA-256 certificate fingerprint is published in the release notes so you can verify an APK with `apksigner verify --print-certs`.

Regenerating the launcher icon and the app-bar brand glyph from `art/launcher-logo.jpg`: `python tools/launcher_icon.py` (needs Pillow and numpy).
