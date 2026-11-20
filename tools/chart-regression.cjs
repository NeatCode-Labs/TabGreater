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
const bandBars = (low, high, open, close) => [0, 1, 2].map(i => ({
  timestamp: 1791273600000 + i * 3600000,
  open, high, low, close, volume: 100,
}));
const variedBars = (closes) => closes.map((close, i) => ({
  timestamp: 1791273600000 + i * 3600000,
  open: close, high: close * 1.1, low: close * 0.9, close, volume: 100,
}));
const tightHighBars = (closes) => closes.map((close, i) => ({
  timestamp: 1791273600000 + i * 3600000,
  open: close, high: close + 0.01, low: close - 0.01, close, volume: 100,
}));

async function paint(page) {
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function loadBars(f, symbol, series) {
  const request = await f.swap(symbol);
  await f.reply(request, { bars: series, hasMoreOlder: false });
  await f.page.waitForFunction(() => testChart && testChart.getDataList().length > 0);
  await paint(f.page);
  assert.equal((await f.data()).length, series.length);
}

function near(actual, expected, tolerance, message) {
  assert.ok(Number.isFinite(actual), `${message}: expected a finite value, got ${actual}`);
  assert.ok(Math.abs(actual - expected) <= tolerance,
    `${message}: expected ${expected} ± ${tolerance}, got ${actual}`);
}

function expectLinearMapping(snapshot, label) {
  const { from, to } = snapshot.range;
  assert.ok(Number.isFinite(from) && Number.isFinite(to) && to > from,
    `${label}: y-axis range must be finite and increasing`);
  const middle = (from + to) / 2;
  near(snapshot.pixels[1], (snapshot.pixels[0] + snapshot.pixels[2]) / 2, 2,
    `${label}: midpoint must map linearly`);
  near(snapshot.middleValue, middle, Math.max(Math.abs(middle) * 0.01, 0.01),
    `${label}: pixel-to-value conversion must use the same linear scale`);
}

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
  ['log scale round-trips positive sub-unit prices and labels them positively', async f => {
    await loadBars(f, A, bandBars(0.01, 0.1, 0.025, 0.075));
    await f.page.evaluate(() => tg.setScale('log'));
    await paint(f.page);
    const result = await f.page.evaluate(values => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      return {
        range: axis.getRange(),
        height: axis.getBounding().height,
        ticks: axis.getTicks().map(tick => ({ text: tick.text, value: Number(tick.value) })),
        points: values.map(value => ({
          value,
          pixel: axis.convertToPixel(value),
          roundTrip: axis.convertFromPixel(axis.convertToPixel(value)),
        })),
      };
    }, [0.01, Math.sqrt(0.01 * 0.1), 0.1]);
    assert.ok(result.ticks.length > 0, 'the logarithmic axis should have ticks');
    assert.ok(result.ticks.every(tick => Number.isFinite(tick.value) && tick.value > 0),
      `sub-unit positive prices must not acquire zero/negative labels: ${JSON.stringify(result.ticks)}`);
    for (const point of result.points) {
      near(point.roundTrip, point.value, point.value * 0.03, `log round-trip at ${point.value}`);
    }
    near(result.points[1].pixel, (result.points[0].pixel + result.points[2].pixel) / 2, 2,
      'geometric midpoint below one must be halfway on the log axis');
  }],
  ['log scale stays logarithmic across one', async f => {
    await loadBars(f, A, bandBars(0.1, 10, 0.25, 8));
    await f.page.evaluate(() => tg.setScale('log'));
    await paint(f.page);
    const result = await f.page.evaluate(values => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      return {
        ticks: axis.getTicks().map(tick => ({ text: tick.text, value: Number(tick.value) })),
        points: values.map(value => ({
          value,
          pixel: axis.convertToPixel(value),
          roundTrip: axis.convertFromPixel(axis.convertToPixel(value)),
        })),
      };
    }, [0.1, 1, 10]);
    assert.ok(result.ticks.length > 0);
    assert.ok(result.ticks.every(tick => Number.isFinite(tick.value) && tick.value > 0),
      `positive range crossing one must keep positive ticks: ${JSON.stringify(result.ticks)}`);
    for (const point of result.points) {
      near(point.roundTrip, point.value, point.value * 0.03, `cross-one round-trip at ${point.value}`);
    }
    near(result.points[1].pixel, (result.points[0].pixel + result.points[2].pixel) / 2, 2,
      'one must be halfway between 0.1 and 10 on the log axis');
  }],
  ['flat and tight high-priced bars retain a visible price range on log scale', async f => {
    await loadBars(f, A, bandBars(99999.99, 100000.01, 100000, 100000));
    await f.page.evaluate(() => tg.setScale('log'));
    await paint(f.page);
    const flat = await f.page.evaluate(() => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      return {
        height: axis.getBounding().height,
        low: axis.convertToPixel(99999.99),
        high: axis.convertToPixel(100000.01),
        ticks: axis.getTicks().length,
      };
    });
    assert.ok(flat.ticks > 0, 'flat data still needs a usable price axis');
    assert.ok(flat.low > 0 && flat.low < flat.height && flat.high > 0 && flat.high < flat.height,
      `flat high-priced data must remain inside the plot: ${JSON.stringify(flat)}`);
    assert.ok(Math.abs(flat.high - flat.low) >= 10,
      `the 0.02 price band should not collapse to a sub-pixel at 100,000: ${JSON.stringify(flat)}`);

    await loadBars(f, B, tightHighBars([99999.99, 100000, 100000.01]));
    await paint(f.page);
    const tight = await f.page.evaluate(() => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      return {
        height: axis.getBounding().height,
        low: axis.convertToPixel(99999.98),
        high: axis.convertToPixel(100000.02),
        ticks: axis.getTicks().length,
      };
    });
    assert.ok(tight.ticks > 0);
    assert.ok(tight.low > 0 && tight.low < tight.height && tight.high > 0 && tight.high < tight.height,
      `a tight non-flat high-price range must remain inside the plot: ${JSON.stringify(tight)}`);
    assert.ok(Math.abs(tight.high - tight.low) >= 10,
      `a 0.04 price band around 100,000 should remain visible: ${JSON.stringify(tight)}`);
  }],
  ['log scale falls back to linear for candle ranges containing zero', async f => {
    await loadBars(f, A, bandBars(0, 2, 0.5, 1.5));
    await f.page.evaluate(() => tg.setScale('log'));
    await paint(f.page);
    const zeroRange = await f.page.evaluate(() => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      const range = axis.getRange();
      const middle = (range.from + range.to) / 2;
      return {
        range,
        pixels: [range.from, middle, range.to].map(value => axis.convertToPixel(value)),
        middleValue: axis.convertFromPixel(axis.convertToPixel(middle)),
      };
    });
    expectLinearMapping(zeroRange, 'zero-containing candle range');

    // The chart keeps the selected log mode across a market swap; a negative candle range must
    // take the same safe linear path rather than creating invalid logarithms.
    await loadBars(f, B, bandBars(-2, -1, -1.75, -1.25));
    await paint(f.page);
    const negativeRange = await f.page.evaluate(() => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      const range = axis.getRange();
      const middle = (range.from + range.to) / 2;
      return {
        range,
        pixels: [range.from, middle, range.to].map(value => axis.convertToPixel(value)),
        middleValue: axis.convertFromPixel(axis.convertToPixel(middle)),
      };
    });
    expectLinearMapping(negativeRange, 'negative candle range after market switch');
  }],
  ['a nonpositive main-pane indicator range uses linear mapping', async f => {
    await loadBars(f, A, variedBars([0.1, 1, 0.1, 1, 0.1]));
    await f.page.evaluate(() => {
      tg.setIndicators([{ name: 'BOLL', calcParams: [2, 2], pane: 'main' }]);
      tg.setScale('log');
    });
    await f.page.waitForFunction(() => {
      const indicator = testChart.getIndicators({ id: 'tg_BOLL' })[0];
      return indicator && indicator.result.some(row => Object.values(row).some(value => Number.isFinite(value) && value <= 0));
    }, undefined, { timeout: 3000 });
    await paint(f.page);
    const snapshot = await f.page.evaluate(() => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      const range = axis.getRange();
      const middle = (range.from + range.to) / 2;
      const indicator = testChart.getIndicators({ id: 'tg_BOLL' })[0];
      return {
        range,
        indicatorMinimum: Math.min(...indicator.result.flatMap(row => Object.values(row).filter(Number.isFinite))),
        pixels: [range.from, middle, range.to].map(value => axis.convertToPixel(value)),
        middleValue: axis.convertFromPixel(axis.convertToPixel(middle)),
      };
    });
    assert.ok(snapshot.indicatorMinimum <= 0,
      `fixture must produce a nonpositive BOLL extreme: ${snapshot.indicatorMinimum}`);
    assert.ok(snapshot.range.from <= 0,
      `main-pane indicator extrema must enter the axis range: ${JSON.stringify(snapshot.range)}`);
    expectLinearMapping(snapshot, 'nonpositive main-pane indicator range');
  }],
  ['manual y-axis zoom that would cross zero retains the last positive range', async f => {
    await loadBars(f, A, bandBars(0.01, 0.1, 0.025, 0.075));
    await f.page.evaluate(() => tg.setScale('log'));
    await paint(f.page);
    const chartBox = await f.page.locator('#chart').boundingBox();
    const before = await f.page.evaluate(() => {
      const bounds = testChart.getYAxes({ paneId: 'candle_pane' })[0].getBounding();
      return {
        bounds: { left: bounds.left, top: bounds.top, width: bounds.width, height: bounds.height },
        range: testChart.getYAxes({ paneId: 'candle_pane' })[0].getRange(),
      };
    });
    const top = chartBox.y + before.bounds.top;
    const x = chartBox.x + before.bounds.left + before.bounds.width / 2;
    const startY = top + before.bounds.height * 0.2;
    const endY = top + before.bounds.height * 0.8;
    const scaleFactor = endY / startY;
    const wouldBeFrom = before.range.from - (before.range.to - before.range.from) * (scaleFactor - 1) / 2;
    assert.ok(before.range.from > 0 && wouldBeFrom <= 0,
      `the manual drag must attempt to cross zero: before=${JSON.stringify(before.range)}, attemptedFrom=${wouldBeFrom}`);
    await f.page.mouse.move(x, startY);
    await f.page.mouse.down();
    await f.page.mouse.move(x, endY, { steps: 12 });
    await f.page.mouse.up();
    await paint(f.page);
    const after = await f.page.evaluate(() => {
      const axis = testChart.getYAxes({ paneId: 'candle_pane' })[0];
      return axis.getRange();
    });
    assert.ok(Number.isFinite(after.from) && Number.isFinite(after.to) && after.from > 0 && after.to > 0,
      `a rejected zoom must leave a drawable positive range: ${JSON.stringify(after)}`);
    near(after.from, before.range.from, Math.max(Math.abs(before.range.from) * 1e-6, 1e-9),
      'rejected manual zoom keeps the previous lower bound');
    near(after.to, before.range.to, Math.max(Math.abs(before.range.to) * 1e-6, 1e-9),
      'rejected manual zoom keeps the previous upper bound');
  }],
  ['sub-unit drawings save positive prices and survive log/linear switching', async f => {
    const series = bandBars(0.01, 0.1, 0.025, 0.075);
    await loadBars(f, A, series);
    await f.page.evaluate(symbol => tg.setDrawings({ ...symbol, drawings: [] }), A);
    await paint(f.page);
    await f.page.evaluate(() => {
      tg.setMagnet('none');
      tg.setScale('log');
      tg.startDrawing('horizontalStraightLine');
    });
    await paint(f.page);
    const point = await f.page.evaluate(value =>
      testChart.convertToPixel(value, { paneId: 'candle_pane', absolute: true }),
    { timestamp: series[1].timestamp, value: 0.05 });
    const chartBox = await f.page.locator('#chart').boundingBox();
    await f.page.mouse.move(chartBox.x + point.x, chartBox.y + point.y);
    await f.page.mouse.click(chartBox.x + point.x, chartBox.y + point.y);
    await f.page.waitForFunction(() => requests.some(r => r.action === 'drawingsChanged'), undefined, { timeout: 3000 });
    const payload = (await f.requests('drawingsChanged')).at(-1).payload;
    assert.equal(payload.drawings.length, 1);
    assert.equal(payload.drawings[0].points.length, 1);
    near(payload.drawings[0].points[0].value, 0.05, 0.05 * 0.05, 'persisted drawing price');

    for (const mode of ['normal', 'log']) {
      await f.page.evaluate(mode => tg.setScale(mode), mode);
      await paint(f.page);
      const restoredPoints = await f.page.evaluate(() => testChart.getOverlays()
        .filter(overlay => overlay.groupId === 'tg')
        .flatMap(overlay => overlay.points.map(point => point.value)));
      assert.equal(restoredPoints.length, 1);
      assert.ok(restoredPoints.every(value => Number.isFinite(value) && value > 0),
        `drawing anchors must remain positive after switching to ${mode}: ${JSON.stringify(restoredPoints)}`);
    }
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
