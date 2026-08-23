# Vendored: KLineChart

`klinecharts.js` in this directory is the **unminified UMD build** of KLineChart, copied
byte-for-byte out of the upstream npm release. It is the file the chart WebView loads
(`../index.html`); nothing in this repository transforms it.

| | |
| --- | --- |
| Project | KLineChart — https://github.com/klinecharts/KLineChart |
| Version | 10.0.2 |
| Licence | Apache-2.0 (`LICENSE` and `NOTICE` in this directory, from the same release) |
| Upstream package | https://registry.npmjs.org/klinecharts/-/klinecharts-10.0.2.tgz |
| Package integrity | `sha512-OJmaG047vd6RPKXxQAnbGPnfUQ3tKCKBlxk/oMvRJBXO6CyaZuqFGQ8ccoU6YTNew7DV8ynbsfti/JERe+M5IQ==` (as published by the npm registry) |
| File in package | `package/dist/umd/klinecharts.js` |
| Size | 674 471 bytes |
| **SHA-256** | `44dd99a21a637abc8bd398146e23581e862ede18702890f54ce200fab5d02ca6` |

The Gradle build **verifies that checksum on every build** (`:app:verifyVendoredAssets`, wired into
`preBuild`), so the file cannot drift from the release it claims to be.

## Why the unminified build

The minified `klinecharts.min.js` is what upstream recommends for production and is what this
project shipped up to 1.0.0. It was replaced because a minified bundle is not a readable, diffable,
reviewable form: F-Droid treats one as a binary blob, and a reader of this repository could not tell
what the chart actually does. The unminified file is the same code from the same release, readable.

The cost is ~440 KB of uncompressed asset (~80 KB in the packaged APK) and a slightly longer parse
when the chart WebView starts. That is a fair price for a dependency anyone can read.

## Verifying by hand

```bash
curl -sSL https://registry.npmjs.org/klinecharts/-/klinecharts-10.0.2.tgz -o klinecharts-10.0.2.tgz
# compare with the integrity hash above
openssl dgst -sha512 -binary klinecharts-10.0.2.tgz | openssl base64 -A
tar -xzf klinecharts-10.0.2.tgz package/dist/umd/klinecharts.js
sha256sum package/dist/umd/klinecharts.js   # must equal the SHA-256 above
```

## Updating

1. Fetch the new release tarball and check it against the integrity string the npm registry
   publishes for that version.
2. Extract `package/dist/umd/klinecharts.js` and copy it here, along with `LICENSE` and `NOTICE`
   from the same tarball.
3. Update the version, size and SHA-256 in this file **and** in `app/build.gradle.kts`
   (`vendoredAssets`), which is what the build checks against.
4. Re-check the chart on a device: candles, all eleven indicators, timeframes, fullscreen, share.
