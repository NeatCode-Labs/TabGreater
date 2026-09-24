# Vendored: KLineChart

`app/src/main/assets/chart/vendor/klinecharts.js` is the **unminified UMD build** of KLineChart,
copied byte-for-byte out of the upstream npm release. It is the file the chart WebView loads
(`app/src/main/assets/chart/index.html`); nothing in this repository transforms it. `LICENSE` and
`NOTICE` sit beside it and ship in the APK, as Apache-2.0 requires; this file does not.

| | |
| --- | --- |
| Project | KLineChart — https://github.com/klinecharts/KLineChart |
| Version | 10.0.3 |
| Licence | Apache-2.0 (`LICENSE` and `NOTICE` beside the bundle, from the same release) |
| Upstream package | https://registry.npmjs.org/klinecharts/-/klinecharts-10.0.3.tgz |
| Package integrity | `sha512-phvf5CyS7oGE46nuC+nxQxl9D+8+5w8q3reExh0oNeMV1Jws4X0CMHNkOI9xlNA/XWctNS5whZ3PqIJi8YM2Tw==` (as published by the npm registry) |
| File in package | `package/dist/umd/klinecharts.js` |
| Size | 675 288 bytes |
| **SHA-256** | `41c3b8614708c2d26dc572d3910e9e6de19f9547b7b3325aff1f58a8e1b8938c` |

The Gradle build **verifies that checksum on every build** (`:app:verifyVendoredAssets`, wired into
`preBuild`), so the file cannot drift from the release it claims to be.

## Why the unminified build

Upstream recommends the minified `klinecharts.min.js` for production, and that is what the project
carried while it was being written. It was replaced before 1.0.0 was published, because a minified
bundle is not a readable, diffable, reviewable form: F-Droid treats one as a binary blob, and a reader of this repository could not tell
what the chart actually does. The unminified file is the same code from the same release, readable.

The cost is ~440 KB of uncompressed asset (~80 KB in the packaged APK) and a slightly longer parse
when the chart WebView starts. That is a fair price for a dependency anyone can read.

## Verifying by hand

```bash
curl -sSL https://registry.npmjs.org/klinecharts/-/klinecharts-10.0.3.tgz -o klinecharts-10.0.3.tgz
# compare with the integrity hash above
openssl dgst -sha512 -binary klinecharts-10.0.3.tgz | openssl base64 -A
tar -xzf klinecharts-10.0.3.tgz package/dist/umd/klinecharts.js
sha256sum package/dist/umd/klinecharts.js   # must equal the SHA-256 above
```

## Adapted overlay templates

`app/src/main/assets/chart/overlays.js` is **not** a vendored asset: it is adapted source, so there
is no upstream file it could be byte-identical to and the build does not checksum it. It holds 17
drawing templates (arrow, circle, rect, triangle, parallelogram, fibonacciCircle, fibonacciSegment,
fibonacciSpiral, fibonacciSpeedResistanceFan, fibonacciExtension, gannBox, threeWaves, fiveWaves,
eightWaves, anyWaves, abcd, xabcd) that KLineChart Pro ships and KLineChart itself does not; the
other 16 drawing tools are built into the vendored bundle.

| | |
| --- | --- |
| Project | KLineChart Pro — https://github.com/klinecharts/pro |
| Source | `src/extension/*.ts` and `src/extension/utils.ts`, branch `refactor`, commit `383722df91fed0ac0befe12cb87e67a2f112e121` |
| Licence | Apache-2.0, Copyright the KLineChart Pro authors (author per package.json: liihuu; the upstream files carry no copyright line) (text in `LICENSES/Apache-2.0.txt`; the file keeps the upstream Apache-2.0 header) |
| Fetched | 2026-09-24, through the GitHub contents API at that commit |

What the port changed, and nothing else:

- TypeScript types, ES module imports/exports, object spread, template literals and optional
  chaining are rewritten as plain ES5-style JavaScript (the WebView loads it without a build step);
  the `utils` import from `klinecharts` is `window.klinecharts.utils`, the helpers of `utils.ts`
  are local functions, and each template is registered with `klinecharts.registerOverlay` at load.
- `chart.getSymbol()?.pricePrecision ?? 2` is an explicit guard with the same default.
- The hard-coded fill colour (`rgba(22, 119, 255, 0.15)`) of circle, rect, triangle,
  parallelogram, gannBox and xabcd is dropped, so the app's overlay theme (`chart.js`,
  `buildStyles().overlay`) applies to them like to the built-in tools.
- fibonacciSegment and fibonacciExtension print their level prices through the chart's thousands
  separator, as the built-in fibonacciLine does.

`refactor` is a working branch that upstream may rebase before it is merged, after which the
commit above might no longer be reachable. The git blob SHA of every ported file identifies its
exact content independently of any branch (`git hash-object <file>` on a copy reproduces it; the
GitHub API lists it under `src/extension?ref=<commit>`). Once `refactor` is merged, re-point the
source to `main` or a release tag and check the blobs still match.

| File | Blob SHA | Bytes |
| --- | --- | --- |
| `abcd.ts` | `1fbf3d7cd040d0a0e77c612e4d9b55f076d12890` | 1665 |
| `anyWaves.ts` | `4ad6c39b27388a0dc7413c940ef03be9cd83e1ec` | 1181 |
| `arrow.ts` | `b7714ed651543d68834e62dc6674b12c2ee57d69` | 1901 |
| `circle.ts` | `e8faa3fbd3d35c4996c3f7ac1425ae465da7b5d3` | 1246 |
| `eightWaves.ts` | `40ece2ba3baa762f5a36fe5bd97d4f5b775c708f` | 1166 |
| `fibonacciCircle.ts` | `ad3ae6b197c93dd0b9eccbf139286c7b5235739e` | 1803 |
| `fibonacciExtension.ts` | `f2da106dfeafe4a875435d338275d28382e39b61` | 2126 |
| `fibonacciSegment.ts` | `8a0d079805cff854888b62ca57ebf4137412ddca` | 2001 |
| `fibonacciSpeedResistanceFan.ts` | `5e65d299e320aefafbc3292f4a3385d8c35fda59` | 2486 |
| `fibonacciSpiral.ts` | `e7b794ac2a0c27ddfaa6c50ba96f1129c4d83733` | 3401 |
| `fiveWaves.ts` | `b87d9b6a50332c89294421d98efe1f93bdf814d0` | 1162 |
| `gannBox.ts` | `3922a912677acb6980b2f8dce0877fb1555aef56` | 3338 |
| `parallelogram.ts` | `7bc7770d29d80b11d4e9b3e57f5486c1d0b85738` | 1857 |
| `rect.ts` | `15a2428fe5a4cb743d675b969ed85017760ae139` | 1337 |
| `threeWaves.ts` | `67dac983eeb6e066e9dea15f1e06c1354db65fcf` | 1165 |
| `triangle.ts` | `eefc9505ce86c10d0be84c2ba3594005ceb3802b` | 1063 |
| `utils.ts` | `2bd603049f60b674320f5e01857f3a2737873392` | 2287 |
| `xabcd.ts` | `d179265cd9f33cc1456a94d474e73ece625e62f1` | 2116 |

The geometry of every template is unchanged. When KLineChart is upgraded, re-check that the
templates still load: `registerOverlay`, `utils.getLinearSlopeIntercept`,
`utils.getLinearYFromCoordinates` and the `line`, `polygon`, `circle`, `arc` and `text` figures
must exist in the new bundle.

## Updating

1. Fetch the new release tarball and check it against the integrity string the npm registry
   publishes for that version.
2. Extract `package/dist/umd/klinecharts.js` into `app/src/main/assets/chart/vendor/`, along with
   `LICENSE` and `NOTICE` from the same tarball.
3. Update the version, size and SHA-256 in this file **and** in `app/build.gradle.kts`
   (`vendoredAssets`), which is what the build checks against.
4. Re-check the chart on a device: candles, all eleven indicators, timeframes, fullscreen, share.
