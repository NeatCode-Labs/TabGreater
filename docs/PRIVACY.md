# Privacy policy

*TabGreater, by NeatCode Labs — last updated 2026-10-06*

## What TabGreater collects

Nothing. The app has no server, no account, no analytics, no crash reporting and no advertising. No data about you or your device is sent to NeatCode Labs.

## What leaves your device

TabGreater fetches prices and candles **directly** from the public market-data APIs of the exchanges you use (Binance, Gate.io, Kraken, KuCoin, MEXC). Each of those requests carries, as every internet request does, your IP address, and it names the trading pairs you are watching. The app also downloads the five exchanges' lists of markets, which the pair search runs on, at most once a day and again after an install or update; those requests name no pair, and what you type into the search never leaves the device. The exchanges receive and process that information under their own terms of service and privacy policies; NeatCode Labs has no access to it and no control over it. All connections use HTTPS / WSS.

The crypto "popular pairs" shortcuts on the add-pair screen and in the widget setup come from CoinGecko's public market-cap ranking (https://www.coingecko.com): the app asks for that list at most once a day, and that request, like any other, carries your IP address. The stock tickers offered under *Stocks* are ranked on the device from the exchanges' lists of markets. Nothing else leaves the device. The chart runs in a WebView that loads only files bundled inside the app.

## What stays on your device

Watchlists, chart drawings (including any text you add to them), settings, widget configuration, the exchanges' lists of markets and the candle cache are stored in the app's private storage. If Android Backup is enabled on your phone, Android includes that storage in your Google account backup, like it does for other apps. "Export watchlists" writes a JSON file to a location you choose; TabGreater never reads it again unless you import it.

## Permissions

| Permission | Why |
|---|---|
| Internet, network state | fetching prices from the exchanges and the daily popular-pairs list |
| Foreground service (special use), exact alarms, boot completed, wake lock | keeping home-screen widgets up to date on the cadence you choose; Android requires a foreground-service notification |
| Request ignore battery optimisations | optional; only when you tap *Battery optimisation* in Settings |

The app does not request Android's `POST_NOTIFICATIONS` permission. Widget updates use a foreground service, for which Android requires an ongoing notification. On Android 13 and later, when that permission is not granted, Android omits the foreground-service notice from the notification drawer but still lists the running service in Task Manager. Android 12 and earlier use different rules and may show the notice in the notification shade, so the app cannot promise complete invisibility. See [Android's notification-permission documentation](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Contact

Open an issue at https://github.com/NeatCode-Labs/TabGreater/issues.
