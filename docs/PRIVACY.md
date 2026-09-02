# Privacy policy

*TabGreater, by NeatCode Labs — last updated 2026-08-23*

## What TabGreater collects

Nothing. The app has no server, no account, no analytics, no crash reporting and no advertising. No data about you or your device is sent to NeatCode Labs.

## What leaves your device

TabGreater fetches prices and candles **directly** from the public market-data APIs of the exchanges you use (Binance, Gate.io, Kraken, KuCoin, MEXC). Each of those requests carries, as every internet request does, your IP address, and it names the trading pairs you are watching. The exchanges receive and process that information under their own terms of service and privacy policies; NeatCode Labs has no access to it and no control over it. All connections use HTTPS / WSS.

The "popular pairs" shortcuts on the add-pair screen and in the widget setup come from CoinGecko's public market-cap ranking (https://www.coingecko.com): the app asks for that list at most once a day, and that request, like any other, carries your IP address. Nothing else leaves the device. The chart runs in a WebView that loads only files bundled inside the app.

## What stays on your device

Watchlists, settings, widget configuration and the candle cache are stored in the app's private storage. If Android Backup is enabled on your phone, Android includes that storage in your Google account backup, like it does for other apps. "Export watchlists" writes a JSON file to a location you choose; TabGreater never reads it again unless you import it.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | fetching prices from the exchanges and the daily popular-pairs list |
| Foreground service (special use), exact alarms, boot completed, wake lock | keeping home-screen widgets up to date on the cadence you choose, without a notification |
| Request ignore battery optimisations | optional; only when you tap *Battery optimisation* in Settings |

The app never asks for notification permission and sends no notifications.

## Contact

Open an issue at https://github.com/NeatCode-Labs/TabGreater/issues.
