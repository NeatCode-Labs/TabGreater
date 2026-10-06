// Real vendored KLineChart + production host, with an offline, controllable native bridge.
// Requires Playwright; see docs/BUILDING.md. No exchange or application data is contacted.
const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require('playwright');

const assets = path.resolve(__dirname, '../app/src/main/assets/chart');
const A = { exchange: 'gate', ticker: 'SPCX/USDT', instId: 'SPCX_USDT', pricePrecision: 2, volumePrecision: 2 };
const B = { exchange: 'binance', ticker: 'BTC/USDT', instId: 'BTCUSDT', pricePrecision: 2, volumePrecision: 2 };
const HOUR = { span: 1, unit: 'hour' };
const DAY = { span: 1, unit: 'day' };
const bars = (price) => [0, 1, 2].map(i => ({
  timestamp: 1791273600000 + i * 3600000,
  open: price, high: price + 1, low: price - 1, close: price, volume: 100,
}));

async function fixture(browser) {
  const context = await browser.newContext({ viewport: { width: 400, height: 700 } });
  await context.route('**/*', route => route.abort());
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.setContent('<div id="chart" style="width:400px;height:700px"></div>');
  await page.evaluate(() => {
    window.requests = [];
    window.Native = {
      postMessage(raw) {
        const message = JSON.parse(raw);
        requests.push(message);
        if (message.id && message.action !== 'getBars') {
          queueMicrotask(() => Native.onmessage({ data: JSON.stringify({ id: message.id, result: {} }) }));
        }
      },
    };
  });
  await page.addScriptTag({ path: path.join(assets, 'vendor/klinecharts.js') });
  await page.evaluate(() => {
    const init = klinecharts.init;
    klinecharts.init = (...args) => (window.testChart = init(...args));
  });
  await page.addScriptTag({ path: path.join(assets, 'overlays.js') });
  await page.addScriptTag({ path: process.env.CHART_HOST_SCRIPT || path.join(assets, 'chart.js') });
  let generation = 0;
  return {
    page,
    async swap(symbol, period = HOUR) {
      await page.evaluate(({ symbol, period, generation }) => {
        tg.setMarket(symbol, period, period.unit === 'day' ? '1D' : '1H', 1, generation);
      }, { symbol, period, generation: ++generation });
      return (await this.requests('getBars')).at(-1);
    },
    requests(action) { return page.evaluate(action => requests.filter(r => r.action === action), action); },
    data() { return page.evaluate(() => testChart.getDataList()); },
    async reply(request, result) {
      await page.evaluate(({ id, result }) => Native.onmessage({ data: JSON.stringify({ id, result }) }), { id: request.id, result });
    },
    async fail(request, retryable = true) {
      await page.evaluate(({ id, retryable }) => Native.onmessage({ data: JSON.stringify({
        id, error: 'offline fixture', failureKind: 'TRANSIENT', retryable,
      }) }), { id: request.id, retryable });
    },
    async load(symbol = A, period = HOUR, price = 166.77) {
      const request = await this.swap(symbol, period);
      await this.reply(request, { bars: bars(price), hasMoreOlder: false });
      assert.equal((await this.data()).at(-1).close, price);
    },
    async close() {
      await context.close();
      assert.deepEqual(errors, [], 'no browser script errors');
    },
  };
}

const tests = [
  ['offline market switch clears the previous market immediately and during retry', async f => {
    await f.load();
    const request = await f.swap(B);
    assert.deepEqual(await f.data(), []);
    await f.fail(request);
    assert.deepEqual(await f.data(), []);
    assert.equal((await f.requests('subscribeBar')).length, 1, 'clearing must not open an intermediate stream');
    await f.page.waitForFunction(() => requests.filter(r => r.action === 'getBars').length === 3);
    await f.fail((await f.requests('getBars')).at(-1), false);
    assert.deepEqual(await f.data(), []);
  }],
  ['offline period switch cannot relabel hourly bars as daily bars', async f => {
    await f.load();
    const request = await f.swap(A, DAY);
    assert.deepEqual(await f.data(), []);
    await f.fail(request);
    assert.deepEqual(await f.data(), []);
  }],
  ['same target keeps its bars while refresh is pending', async f => {
    await f.load();
    const before = await f.data();
    const request = await f.swap({ ...A });
    assert.deepEqual(await f.data(), before);
    await f.fail(request);
    assert.deepEqual(await f.data(), before);
  }],
  ['exchange and native instrument identity are part of the series identity', async f => {
    await f.load();
    await f.swap({ ...A, exchange: 'mexc' });
    assert.deepEqual(await f.data(), []);
    await f.load();
    await f.swap({ ...A, instId: 'replacement_instrument' });
    assert.deepEqual(await f.data(), []);
  }],
  ['late responses after rapid A/B/A swaps cannot repopulate or replace bars', async f => {
    await f.load();
    const oldA = await f.swap(A);
    const oldB = await f.swap(B);
    const currentA = await f.swap(A);
    await f.reply(oldA, { bars: bars(170) });
    await f.reply(oldB, { bars: bars(85545) });
    assert.deepEqual(await f.data(), []);
    await f.reply(currentA, { bars: bars(166.77) });
    assert.equal((await f.data()).at(-1).close, 166.77);
    assert.equal((await f.requests('getBars')).length, 4, 'one init per swap');
  }],
  ['a retry for the abandoned target cannot run after a new target succeeds', async f => {
    await f.load();
    const oldA = await f.swap(A);
    await f.fail(oldA);
    const currentB = await f.swap(B);
    await f.reply(currentB, { bars: bars(85545) });
    await f.page.waitForTimeout(1200); // exceed the first scheduled retry
    assert.equal((await f.requests('getBars')).length, 3);
    assert.equal((await f.data()).at(-1).close, 85545);
  }],
  ['new-target success restores only its drawings and accepts its live bars', async f => {
    await f.load();
    const request = await f.swap(B);
    await f.page.evaluate(({ symbol, timestamp }) => tg.setDrawings({ ...symbol, drawings: [
      { name: 'horizontalStraightLine', points: [{ timestamp, value: 85000 }], lock: false, mode: 'normal' },
    ] }), { symbol: B, timestamp: bars(85545)[0].timestamp });
    assert.deepEqual(await f.data(), []);
    await f.reply(request, { bars: bars(85545) });
    assert.equal((await f.requests('subscribeBar')).length, 2);
    assert.equal(await f.page.evaluate(() => testChart.getOverlays().length), 1);
    await f.page.evaluate(bar => tg.onBar(bar), bars(85546).at(-1));
    assert.equal((await f.data()).at(-1).close, 85546);
  }],
];

(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.CHART_TEST_CHANNEL ? { channel: process.env.CHART_TEST_CHANNEL } : {}) });
  let failed = 0;
  try {
    for (const [name, run] of tests) {
      const f = await fixture(browser);
      try { await run(f); console.log(`PASS ${name}`); }
      catch (error) { failed++; console.error(`FAIL ${name}\n${error.stack}`); }
      finally { await f.close(); }
    }
  } finally { await browser.close(); }
  console.log(`${tests.length - failed}/${tests.length} passed (real vendored engine; simulated bridge)`);
  process.exitCode = failed ? 1 : 0;
})().catch(error => { console.error(error); process.exitCode = 1; });
