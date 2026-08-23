/* TabGreater — KLineChart 10.0.2 host. Plain script, no modules, no build step.
   Kotlin -> JS:  webView.evaluateJavascript("tg.<fn>(<json>)", null)
   JS -> Kotlin:  Native.postMessage(JSON.stringify({id, action, payload}))
   Kotlin -> JS:  replyProxy.postMessage(JSON.stringify({id, result|error}))  -> Native.onmessage
   Live bars:     webView.evaluateJavascript("tg.onBar(<json>)", null)

   Colours are the app's chart tokens.              */
(function (global) {
  'use strict';

  var K = global.klinecharts;
  var CANDLE_PANE = 'candle_pane';          // klinecharts PaneIdConstants.CANDLE
  var SUB_PANE = 'tg_sub_';                 // our own stable sub-pane ids
  var FAM = 'Roboto, "Helvetica Neue", sans-serif';
  var INIT_LIMIT = 500;
  var PAGE_LIMIT = 300;
  var RPC_TIMEOUT = 20000;
  var INIT_RETRY_DELAYS = [1000, 2000, 4000];   // three retries for 'init'; see dataLoader.getBars

  var chart = null;
  var generation = 0;                       // bumped on every symbol/period swap
  var loaderMuted = false;                  // true during a batched symbol+period swap
  var sub = null;                           // { gen: n, callback: fn }
  var candleType = 'candle_solid';

  /* ------------------------------------------------------------------ RPC */

  var seq = 0, pending = Object.create(null);

  function hasBridge() {
    return typeof global.Native !== 'undefined' &&
           typeof global.Native.postMessage === 'function';
  }

  function rpc(action, payload) {
    return new Promise(function (resolve, reject) {
      if (!hasBridge()) { reject(new Error('no native bridge')); return; }
      var id = 'r' + (++seq);
      pending[id] = {
        resolve: resolve, reject: reject,
        timer: setTimeout(function () {
          delete pending[id];
          reject(new Error('timeout:' + action));
        }, RPC_TIMEOUT)
      };
      global.Native.postMessage(JSON.stringify({ id: id, action: action, payload: payload || {} }));
    });
  }

  function onNativeMessage(raw) {
    var m; try { m = JSON.parse(raw); } catch (e) { return; }
    if (!m || !m.id) { return; }
    var p = pending[m.id]; if (!p) { return; }
    clearTimeout(p.timer); delete pending[m.id];
    if (m.error) { p.reject(new Error(m.error)); } else { p.resolve(m.result); }
  }

  if (hasBridge()) { global.Native.onmessage = function (e) { onNativeMessage(e.data); }; }

  function note(kind, text) {
    if (hasBridge()) {
      global.Native.postMessage(JSON.stringify({ action: 'log', payload: { kind: kind, text: String(text) } }));
    }
  }
  global.onerror = function (msg, src, line, col) { note('error', msg + ' @' + line + ':' + col); };

  /* ----------------------------------------------------------- formatting */

  /* Mirrors PriceFormat (core/model): en-US grouping, `.` decimals, no Locale involved. */
  function group(plain) {
    var dot = plain.indexOf('.');
    var integer = dot < 0 ? plain : plain.substring(0, dot);
    var fraction = dot < 0 ? '' : plain.substring(dot);
    if (integer.length <= 3) { return integer + fraction; }
    var out = '', i;
    for (i = 0; i < integer.length; i++) {
      if (i > 0 && (integer.length - i) % 3 === 0) { out += ','; }
      out += integer.charAt(i);
    }
    return out + fraction;
  }

  /* PriceFormat.formatPrice */
  function formatPrice(value, precision) {
    if (!isFinite(value)) { return '—'; }
    var text = Math.abs(value).toFixed(precision);
    var negative = value < 0 && parseFloat(text) !== 0;
    return (negative ? '-' : '') + group(text);
  }

  var COMPACT_UNITS = [[1e12, 'T'], [1e9, 'B'], [1e6, 'M'], [1e3, 'K']];

  /* PriceFormat.formatCompact(value, 2): 31,665.90 · 1.24K · 9.81M · 2.30B · 1.15T */
  function formatCompact(value) {
    if (!isFinite(value)) { return '—'; }
    var magnitude = Math.abs(value), unit = -1, i;
    for (i = 0; i < COMPACT_UNITS.length; i++) {
      if (magnitude >= COMPACT_UNITS[i][0]) { unit = i; break; }
    }
    if (unit < 0) {
      if (Math.abs(parseFloat(value.toFixed(2))) < 1000) { return formatPrice(value, 2); }
      unit = COMPACT_UNITS.length - 1;
    }
    while (true) {
      var scaled = parseFloat((value / COMPACT_UNITS[unit][0]).toFixed(2));
      // 999,999.6 / 1e3 rounds to 1,000.00K: promote to the next unit instead.
      if (Math.abs(scaled) >= 1000 && unit > 0) { unit--; continue; }
      return formatPrice(scaled, 2) + COMPACT_UNITS[unit][1];
    }
  }

  /* --------------------------------------------------------------- theme */

  var THEME = {
    background:  '#141515',
    up:          '#6FA26F',
    down:        '#D9655E',
    volUp:       '#283D29',
    volDown:     '#41211D',
    grid:        '#2C2D2F',
    axisLine:    '#2C2D2F',
    axisText:    '#B0B0B0',
    text:        '#A8ABB2',
    lastTag:     '#73A973',
    crosshair:   '#8B95A5',
    crosshairBg: '#2C2D2F',
    lines: ['#53A8B0', '#FCCD0B', '#FF9E99', '#5856D6', '#FAA426']
  };

  function buildStyles(t) {
    return {
      grid: {
        show: true,
        horizontal: { show: true, size: 1, color: t.grid, style: 'dashed', dashedValue: [2, 2] },
        vertical:   { show: true, size: 1, color: t.grid, style: 'dashed', dashedValue: [2, 2] }
      },
      candle: {
        type: candleType,
        bar: {
          compareRule: 'current_open',
          upColor: t.up,          downColor: t.down,          noChangeColor: t.axisText,
          upBorderColor: t.up,    downBorderColor: t.down,    noChangeBorderColor: t.axisText,
          upWickColor: t.up,      downWickColor: t.down,      noChangeWickColor: t.axisText
        },
        area: {
          lineSize: 2, lineColor: t.up, value: 'close', smooth: false,
          backgroundColor: [
            { offset: 0, color: 'rgba(111,162,111,0.01)' },
            { offset: 1, color: 'rgba(111,162,111,0.28)' }
          ],
          point: { show: false, color: t.up, radius: 4, rippleColor: 'rgba(111,162,111,0.30)', rippleRadius: 8, animation: false, animationDuration: 0 }
        },
        priceMark: {
          show: true,
          // 9 px, not 10: the high mark sits in the top-left corner the OHLC legend also occupies.
          high: { show: true, color: t.axisText, textSize: 9, textFamily: FAM, textOffset: 5 },
          low:  { show: true, color: t.axisText, textSize: 9, textFamily: FAM, textOffset: 5 },
          last: {
            show: true, compareRule: 'current_open',
            upColor: t.lastTag, downColor: t.down, noChangeColor: t.axisText,
            line: { show: true, style: 'dashed', dashedValue: [4, 4], size: 1 },
            text: { show: true, style: 'fill', size: 11, family: FAM, weight: 'normal',
                    color: '#FFFFFF', borderRadius: 2, borderSize: 0, borderColor: 'transparent',
                    paddingLeft: 4, paddingRight: 4, paddingTop: 3, paddingBottom: 3 }
          }
        },
        tooltip: {
          showRule: 'always', showType: 'standard',
          offsetLeft: 8, offsetTop: 6, offsetRight: 8, offsetBottom: 6,
          title:  { show: true, size: 11, family: FAM, weight: 'normal', color: t.axisText,
                    marginLeft: 8, marginTop: 6, marginRight: 8, marginBottom: 2,
                    template: '{ticker} · {period}' },
          // 10 px with tight margins so O/H/L/C still fits on ONE row at 360 dp with six-figure
          // prices — drawStandardTooltipLegends wraps to a second row as soon as it does not.
          legend: { size: 10, family: FAM, weight: 'normal', color: t.text,
                    marginLeft: 6, marginTop: 2, marginRight: 3, marginBottom: 2,
                    defaultValue: '—',
                    template: [ { title: 'O', value: '{open}' }, { title: 'H', value: '{high}' },
                                { title: 'L', value: '{low}' },  { title: 'C', value: '{close}' } ] },
          features: []
        }
      },
      indicator: {
        ohlc: { compareRule: 'current_open', upColor: t.volUp, downColor: t.volDown, noChangeColor: t.axisText },
        bars: [ { style: 'fill', borderStyle: 'solid', borderSize: 1, borderDashedValue: [2, 2],
                  upColor: t.volUp, downColor: t.volDown, noChangeColor: t.axisText } ],
        lines: t.lines.map(function (c) {
          return { style: 'solid', smooth: false, size: 1, dashedValue: [2, 2], color: c };
        }),
        lastValueMark: { show: false, text: { show: false } },
        tooltip: {
          showRule: 'always', showType: 'standard',
          title:  { show: true, showName: true, showParams: true,
                    size: 11, family: FAM, weight: 'normal', color: t.axisText,
                    marginLeft: 8, marginTop: 4, marginRight: 8, marginBottom: 2 },
          legend: { size: 11, family: FAM, weight: 'normal', color: t.text,
                    marginLeft: 8, marginTop: 2, marginRight: 8, marginBottom: 2, defaultValue: '—' },
          features: []
        }
      },
      xAxis: {
        show: true, size: 'auto',
        axisLine: { show: true, size: 1, color: t.axisLine },
        tickLine: { show: false, size: 1, length: 3, color: t.axisLine },
        tickText: { show: true, color: t.axisText, size: 10, family: FAM, weight: 'normal',
                    marginStart: 4, marginEnd: 4 }
      },
      yAxis: {
        show: true, size: 'auto',
        axisLine: { show: true, size: 1, color: t.axisLine },
        tickLine: { show: false, size: 1, length: 3, color: t.axisLine },
        tickText: { show: true, color: t.axisText, size: 10, family: FAM, weight: 'normal',
                    marginStart: 6, marginEnd: 6 }
      },
      separator: { size: 1, color: t.axisLine, fill: true, activeBackgroundColor: 'rgba(139,149,165,0.12)' },
      crosshair: {
        show: true,
        horizontal: {
          show: true,
          line: { show: true, style: 'dashed', dashedValue: [4, 2], size: 1, color: t.crosshair },
          text: { show: true, style: 'fill', color: '#FFFFFF', size: 11, family: FAM, weight: 'normal',
                  borderStyle: 'solid', borderSize: 0, borderColor: t.crosshairBg, borderRadius: 2,
                  backgroundColor: t.crosshairBg,
                  paddingLeft: 4, paddingRight: 4, paddingTop: 3, paddingBottom: 3 },
          features: []
        },
        vertical: {
          show: true,
          line: { show: true, style: 'dashed', dashedValue: [4, 2], size: 1, color: t.crosshair },
          text: { show: true, style: 'fill', color: '#FFFFFF', size: 11, family: FAM, weight: 'normal',
                  borderStyle: 'solid', borderSize: 0, borderColor: t.crosshairBg, borderRadius: 2,
                  backgroundColor: t.crosshairBg,
                  paddingLeft: 4, paddingRight: 4, paddingTop: 3, paddingBottom: 3 }
        }
      }
    };
  }

  /* --------------------------------------------------------- data loader */

  /* One attempt of a getBars round. A failure is NOT terminal:
     - 'init'      : retried with backoff, because klinecharts clears _dataList on an empty init
                     reply and nothing else would ever ask again.
     - 'forward'   : answered with more.forward = true, so the next drag to the left edge retries.
                     That is loop-safe — _addData skips _adjustVisibleRange for an empty array.
     Every path ends in exactly one params.callback(), or the store stays _loading forever. */
  function requestBars(params, gen, attempt) {
    rpc('getBars', {
      exchange:  params.symbol.exchange,
      ticker:    params.symbol.ticker,
      instId:    params.symbol.instId,           // venue-native id, e.g. "BTCUSDT" / "XXBTZEUR"
      type:      params.type,                    // 'init' | 'forward' (older) | 'backward' (newer)
      timestamp: params.timestamp,               // ms; null on 'init'
      span:      params.period.span,
      unit:      params.period.type,             // 'minute'|'hour'|'day'|'week'|'month'
      limit:     params.type === 'init' ? INIT_LIMIT : PAGE_LIMIT
    }).then(function (res) {
      if (gen !== generation) { return; }        // stale: symbol/period changed mid-flight
      params.callback((res && res.bars) || [], {
        forward:  !!(res && res.hasMoreOlder),   // more history available to the left
        backward: false                          // we never page forward past "now"
      });
    })['catch'](function (err) {
      if (gen !== generation) { return; }
      note('warn', 'getBars ' + params.type + ' try ' + (attempt + 1) + ': ' + err.message);
      if (params.type === 'init' && attempt < INIT_RETRY_DELAYS.length) {
        setTimeout(function () {
          if (gen !== generation) { return; }    // a newer swap owns the store now
          requestBars(params, gen, attempt + 1);
        }, INIT_RETRY_DELAYS[attempt]);
        return;                                  // still loading: the callback comes with the retry
      }
      if (params.type === 'init') {
        note('error', 'getBars init gave up after ' + (INIT_RETRY_DELAYS.length + 1) + ' tries');
        params.callback([], false);
      } else {
        params.callback([], { forward: true, backward: false });
      }
    });
  }

  var dataLoader = {
    // params: { type:'init'|'forward'|'backward', timestamp:number|null, symbol, period, callback }
    getBars: function (params) {
      if (loaderMuted) { return; }                 // swallowed half of a batched swap
      requestBars(params, generation, 0);
    },

    subscribeBar: function (params) {
      sub = { gen: generation, callback: params.callback };
      rpc('subscribeBar', {
        exchange: params.symbol.exchange,
        ticker:   params.symbol.ticker,
        instId:   params.symbol.instId,
        span:     params.period.span,
        unit:     params.period.type
      })['catch'](function (e) { note('warn', 'subscribeBar: ' + e.message); });
    },

    unsubscribeBar: function () {
      sub = null;
      rpc('unsubscribeBar', {})['catch'](function () {});
    }
  };

  /* --------------------------------------------------------- indicators */

  /* KLineChart paints a built-in indicator's legend in the FIGURE's colour: getIndicatorTooltipData
     feeds `figure.styles().color` into both the title and the value row, so VOLUME reads in the
     dark volume green (#283D29) and MACD in the bar red (#41211D) — near-black on #141515.
     `indicator.tooltip.legend.color` is only honoured on the OTHER branch of that function, the one
     taken when the indicator carries a `createTooltipDataSource`. So sub-pane indicators get one.
     Main-pane indicators (MA, BOLL, …) keep the engine's per-line colours on purpose: those are the
     bright line colours and they are what identifies the line. */
  function greyLegend(params) {
    var ind = params.indicator;
    var result = ind.result || [];
    var idx = params.crosshair.dataIndex;
    if (idx === undefined || idx === null) { idx = result.length - 1; }
    var row = result[idx] || {};
    var calcParams = ind.calcParams || [];
    var legends = [];
    (ind.figures || []).forEach(function (fig) {
      if (!fig.title) { return; }
      var v = row[fig.key];
      var text = '—';
      if (typeof v === 'number' && isFinite(v)) {
        text = ind.shouldFormatBigNumber ? formatCompact(v) : formatPrice(v, ind.precision);
      }
      legends.push({ title: { text: fig.title, color: THEME.text }, value: { text: text, color: THEME.text } });
    });
    return {
      name: ind.shortName,
      calcParamsText: calcParams.length > 0 ? '(' + calcParams.join(',') + ')' : '',
      legends: legends,
      features: []
    };
  }

  // spec item: { name:'MA', calcParams:[5,10,30], pane:'main'|'sub', height?:number }
  function applyIndicators(spec) {
    if (chart === null) { return; }
    spec = spec || [];
    var wanted = Object.create(null), i, s, id;
    for (i = 0; i < spec.length; i++) {
      s = spec[i];
      wanted['tg_' + s.name] = s;
    }
    // remove what is no longer wanted
    var live = chart.getIndicators();
    for (i = 0; i < live.length; i++) {
      if (live[i].id.indexOf('tg_') === 0 && !wanted[live[i].id]) {
        chart.removeIndicator({ id: live[i].id });
      }
    }
    // create / update the rest
    for (id in wanted) {
      s = wanted[id];
      var paneId = (s.pane === 'main') ? CANDLE_PANE : (SUB_PANE + s.name);
      var existing = chart.getIndicators({ id: id });
      if (existing.length === 0) {
        chart.createIndicator({
          id: id, name: s.name, paneId: paneId,
          calcParams: s.calcParams || undefined,
          createTooltipDataSource: (s.pane === 'main') ? undefined : greyLegend
        }, false);
        if (s.pane !== 'main') {
          chart.setPaneOptions({ id: paneId, height: s.height || 90, minHeight: 40, dragEnabled: true });
        }
      } else if (s.calcParams) {
        chart.overrideIndicator({ id: id, calcParams: s.calcParams });
      }
    }
  }

  /* --------------------------------------------------------------- boot */

  function boot() {
    chart = K.init('chart', {
      locale: 'en-US',
      styles: buildStyles(THEME),
      zoomAnchor: 'last_bar',
      layout: {
        barSpaceLimit: { min: 2, max: 40 },
        pane:  { minHeight: 40, dragEnabled: true },
        yAxis: { name: 'normal', position: 'right', inside: false, gap: { top: 0.15, bottom: 0.1 } }
      },
      formatter: {
        formatBigNumber: function (v) {
          var n = Number(v);
          return isFinite(n) ? formatCompact(n) : String(v);
        }
      }
    });
    chart.setDataLoader(dataLoader);
    // The `log` / `auto` pills are a Compose overlay in the canvas' lower-right corner: 8 dp from
    // the edge, ~88 dp wide, i.e. ~40 dp into the plot area past the y-axis. klinecharts lays out
    // in CSS pixels and the page is `initial-scale=1`, so 1 CSS px == 1 dp here — 88 pushes the
    // last bar (and with it the last x-axis tick label) clear of them (D10 / F4-6), measured on
    // the 360 dp emulator.
    chart.setOffsetRightDistance(88);
    chart.setMaxOffsetRightDistance(160);
    note('info', 'chart ready v' + K.version());
    if (hasBridge()) { global.Native.postMessage(JSON.stringify({ action: 'ready', payload: {} })); }
  }

  /* -------------------------------------------------------- public API  */

  var tg = {
    /**
     * Swap symbol and period atomically: only ONE getBars('init') hits the network.
     * Never call setSymbol/setPeriod back to back from Kotlin.
     * sym   = {exchange, ticker, instId, pricePrecision, volumePrecision}
     * p     = {span, unit} — `unit` is KLineChart's `PeriodType` (it calls the field `type`).
     * label = Timeframe.label ('1m' … '1M'). KLineChart's own `{period}` placeholder renders
     *         {span:1,type:'minute'} as a bare "1" (F4-4), so the title template gets our label.
     */
    setMarket: function (sym, p, label) {
      if (chart === null) { return; }
      generation++;
      chart.setStyles({ candle: { tooltip: { title: {
        template: label ? '{ticker} · ' + label : '{ticker} · {period}'
      } } } });
      loaderMuted = true;
      chart.setPeriod({ span: p.span, type: p.unit });    // fires a getBars we swallow
      loaderMuted = false;
      chart.setSymbol({                                    // fires the real getBars
        exchange: sym.exchange, ticker: sym.ticker, instId: sym.instId,
        pricePrecision: sym.pricePrecision, volumePrecision: sym.volumePrecision
      });
    },

    setIndicators: applyIndicators,

    setScale: function (mode) {                       // 'log' | 'normal'
      if (chart === null) { return; }
      chart.overrideYAxis({ paneId: CANDLE_PANE, name: (mode === 'log') ? 'logarithm' : 'normal' });
    },

    // The "auto" button: undo a manual y-axis drag, re-enable autoscale.
    // setAutoCalcTickFlag is not in the public d.ts but IS on the prototype in the
    // shipped minified bundle (verified). Fallback below if it ever disappears.
    resetAutoScale: function () {
      if (chart === null) { return; }
      var axes = chart.getYAxes({ paneId: CANDLE_PANE }) || [], i;
      var ok = false;
      for (i = 0; i < axes.length; i++) {
        if (typeof axes[i].setAutoCalcTickFlag === 'function') { axes[i].setAutoCalcTickFlag(true); ok = true; }
      }
      if (!ok) {                                       // documented fallback: force axis recreation
        var cur = (axes[0] && axes[0].name) || 'normal';
        chart.overrideYAxis({ paneId: CANDLE_PANE, name: (cur === 'normal') ? 'tg_normal_alt' : 'normal' });
        chart.overrideYAxis({ paneId: CANDLE_PANE, name: cur });
      }
      chart.resize();                                  // forceBuildYAxisTick: true
    },

    setCandleType: function (type) {                   // 'candle_solid'|'candle_stroke'|'ohlc'|'area'
      candleType = type;
      if (chart !== null) { chart.setStyles({ candle: { type: type } }); }
    },

    onBar: function (bar) {                            // pushed from Kotlin via evaluateJavascript
      if (sub && sub.gen === generation) { sub.callback(bar); }
    },

    scrollToRealTime: function () { if (chart !== null) { chart.scrollToRealTime(200); } },
    resize:           function () { if (chart !== null) { chart.resize(); } },
    dispose:          function () { if (chart !== null) { K.dispose(chart); chart = null; } }
  };

  // register the alias y-axis used by the resetAutoScale fallback
  K.registerYAxis({ name: 'tg_normal_alt' });

  global.tg = tg;
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else { boot(); }
})(window);
