/* TabGreater — KLineChart 10.0.3 host. Plain script, no modules, no build step.
   Kotlin -> JS:  webView.evaluateJavascript("tg.<fn>(<json>)", null)
   JS -> Kotlin:  Native.postMessage(JSON.stringify({id, action, payload}))
   Kotlin -> JS:  replyProxy.postMessage(JSON.stringify({id, result|error}))  -> Native.onmessage
   Live bars:     webView.evaluateJavascript("tg.onBar(<json>)", null)
   Drawings:      tg.setDrawings / startDrawing / cancelDrawing / setMagnet / setDrawingsVisible /
                  removeSelectedDrawing / toggleSelectedLock / deselectDrawing / clearDrawings /
                  setDrawingText / removeDrawing  (Kotlin -> JS, see "drawings" below)
                  Native.postMessage({action:'drawingsChanged'|'drawingState', payload})  (JS -> Kotlin,
                  fire-and-forget notices without an id)

   Loaded after vendor/klinecharts.js and overlays.js (17 drawing templates ported from
   KLineChart Pro; KLineChart itself ships the other 16 the drawing tools use).
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
    lines: ['#53A8B0', '#FCCD0B', '#FF9E99', '#5856D6', '#FAA426'],
    draw:        '#5BB8F0',                 // user drawings: the palette's sky blue
    drawFill:    'rgba(91,184,240,0.15)',   // the same blue at 15 % for filled shapes
    drawText:    '#FFFFFF',
    drawTextBg:  '#2C2D2F'
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
      },
      /* Every key of the v10 OverlayStyle default (getDefaultOverlayStyle, vendor/klinecharts.js
         11746–11819: point 11769, line 11779, rect 11786, polygon 11795, circle 11803, arc 11811,
         text 11749/11817), re-themed for the dark chart. OverlayView.drawFigures (8949) merges
         these under each template's own `styles` and each figure's `styles` (8960), so a
         template only has to name what differs (e.g. style: 'dashed' / 'stroke_fill').
         Point handles (drawDefaultFigures 8991) double as touch targets: radius + border = 8 px. */
      overlay: {
        point: {
          color: t.draw, borderColor: t.background, borderSize: 2, radius: 6,
          activeColor: t.draw, activeBorderColor: t.background, activeBorderSize: 2, activeRadius: 6
        },
        line:    { style: 'solid', smooth: false, color: t.draw, size: 1, dashedValue: [4, 3] },
        rect:    { style: 'fill', color: t.drawFill, borderColor: t.draw, borderSize: 1, borderRadius: 0,
                   borderStyle: 'solid', borderDashedValue: [4, 3] },
        polygon: { style: 'fill', color: t.drawFill, borderColor: t.draw, borderSize: 1,
                   borderStyle: 'solid', borderDashedValue: [4, 3] },
        circle:  { style: 'fill', color: t.drawFill, borderColor: t.draw, borderSize: 1,
                   borderStyle: 'solid', borderDashedValue: [4, 3] },
        arc:     { style: 'solid', color: t.draw, size: 1, dashedValue: [4, 3] },
        text:    { style: 'fill', color: t.drawText, size: 11, family: FAM, weight: 'normal',
                   borderStyle: 'solid', borderDashedValue: [2, 2], borderSize: 0, borderRadius: 2,
                   borderColor: t.drawTextBg, backgroundColor: t.drawTextBg,
                   paddingLeft: 4, paddingRight: 4, paddingTop: 3, paddingBottom: 3 }
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
      // Restore saved drawings only onto real bars: on an empty chart a moved point would lose its
      // timestamp, and the next drawingsChanged would overwrite the saved set with that.
      if (params.type === 'init' && res && res.bars && res.bars.length > 0) { barsDelivered(gen); }
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

  /* ------------------------------------------------------------- drawings */

  /* How touch drives an overlay in KLineChart 10.0.3 (line numbers in vendor/klinecharts.js):
     - touchstart: EventHandlerImp (1760-1790) -> Event.touchStartEvent (2650), which dispatches
       'mouseDownEvent' into the main widget (2661). OverlayView's own mouseDownEvent (8579) only
       consumes it to start a continuous overlay (brush: startContinuousDrawing, 8588); on a
       finished overlay the hit figure consumes it (_figureMouseDownEvent 8733 ->
       setPressedOverlayInfo). Not consumed -> the chart starts scrolling (2691).
     - touchmove, once past 5 px (ManhattanDistance.CancelTap 1381, checked at 1614):
       Event.touchMoveEvent (2720) -> 'pressedMouseMoveEvent' (2729) -> OverlayView (8623). A brush
       adds a point (8631); a pressed overlay moves, a point handle through eventPressedPointMove
       (8643 -> OverlayImp 8391), the body through eventPressedOtherMove (8646 -> 8414). Not
       consumed -> scroll, or the crosshair drag after a long press.
     - touchend: Event.touchEndEvent (2755) -> 'mouseUpEvent' (2763) -> OverlayView (8595): a brush
       completes (8602-8604, onDrawEnd), a pressed overlay gets onPressedMoveEnd (8611).
     - a tap (no move, no long press): _touchEndHandler -> tapEvent (1697) -> Event.tapEvent (2803)
       -> 'mouseClickEvent' (2808) -> OverlayView (8507). A step-mode overlay in progress takes
       the tap as its next point (stepDrawingModeEventMoveForDrawing 8519, nextStep 8521) and
       finishes on the last one (progressOverlayComplete 8523, onDrawEnd 8524); that same tap then
       click-selects it (8527 -> setClickOverlayInfo 14543 -> onSelected). With nothing in
       progress a hit figure selects its overlay (_figureMouseClickEvent 8751) and a tap on empty
       chart deselects (8533).
     - a second tap within 500 ms and 30 px is a double tap (1690; Delay 1374, ManhattanDistance
       1379) -> mouseDoubleClickEvent (8542) -> forceComplete (8549). That is how anyWaves ends on
       a phone, and why a drawing finished with too few points is discarded (finished()).
     - while an overlay is in progress OverlayView.dispatchEvent (8890) handles every event
       itself, without hit-testing figures. Touch has no hover, so mouseMoveEvent (8480) never
       previews the next point: the overlay shows the points tapped so far.
     Figure hit tests are +-2 px (DEVIATION 5163; lines 5218, arcs 5435); polygons and circles
     are hit anywhere inside (6067, 5529). touchFriendly() below widens lines/arcs for fingers
     and lets an unselected drawing take taps but not drags, so panning over it still pans.
     Overlays are NOT cleared by setSymbol/setPeriod: resetData (13657) -> _clearData (14579)
     leaves StoreImp._overlays alone; only destroy() clears them (14599). setMarket removes ours.
     Overlay instance fields (OverlayImp 8240-8280): id, name, groupId, paneId, points
     [{timestamp, dataIndex, value}], totalStep, currentStep (-1 = finished, 8237), drawingMode
     ('step'|'continuous'), lock, visible, mode ('normal'|'weak_magnet'|'strong_magnet', applied
     by _coordinateToPoint 8822-8880), extendData, styles, zLevel. createOverlay (15364) takes an
     OverlayCreate and returns the id (or null for an unknown name); an OverlayCreate with points
     is born finished (override 8295-8300), without them it becomes the progress overlay
     (addOverlays 14381). Every key of the create object is merged onto the instance (merge, via
     override 8286), which is how the event hooks and createPointFigures below get there.
     simpleAnnotation renders extendData as its label (12471-12530); simpleTag shows it on the
     y-axis, falling back to the price when it is not set (12537-12575). */

  var GROUP = 'tg';                         // groupId of every overlay the app creates
  var EMIT_DEBOUNCE = 150;
  var HIT = 10;                             // touch slop around lines and arcs, CSS px (== dp)
  var CANCEL_TAP_PX = 5;                    // KLineChart's ManhattanDistance.CancelTap (1381)
  // The 30 tools of the drawing toolbar, plus three built-ins the toolbar does not offer
  // (horizontalSegment, verticalRayLine, verticalSegment) so a saved one still restores.
  var TOOLS = [
    'segment', 'rayLine', 'straightLine', 'arrow', 'horizontalStraightLine', 'horizontalRayLine',
    'verticalStraightLine', 'priceLine', 'horizontalSegment', 'verticalRayLine', 'verticalSegment',
    'parallelStraightLine', 'priceChannelLine',
    'fibonacciLine', 'fibonacciExtension', 'fibonacciSegment', 'fibonacciCircle', 'fibonacciSpiral',
    'fibonacciSpeedResistanceFan', 'gannBox',
    'rect', 'circle', 'triangle', 'parallelogram', 'brush',
    'threeWaves', 'fiveWaves', 'eightWaves', 'anyWaves', 'abcd', 'xabcd',
    'simpleAnnotation', 'simpleTag'
  ];
  var TEXT_TOOLS = { simpleAnnotation: true, simpleTag: true };
  var MODES = { normal: true, weak_magnet: true, strong_magnet: true };
  var PRESS_EVENTS = ['onPressedMoveStart', 'onPressedMoving', 'onPressedMoveEnd'];
  // The Compose `log` / `auto` pills cover the time axis this far in from the chart's right edge
  // (8 dp margin + two pills + the 6 dp gap between them). Point dates stay left of them.
  var PILLS_RESERVE = 100;
  var DATE_LABEL_PADDING = 8;               // overlay.text paddingLeft + paddingRight (buildStyles)
  // Templates whose level labels hug the plot's left edge, where the legend is.
  var EDGE_LABEL_TOOLS = { fibonacciLine: true };

  var dr = {
    sym: null,              // { exchange, ticker } of the market on screen
    restore: 'none',        // 'awaiting' (no setDrawings yet) | 'pending' (no bars yet) | 'applied'
    pendingList: null,      // sanitised drawings waiting for bars
    loadedGen: -1,          // generation whose init bars have arrived
    dirty: false,           // something changed before the restore was applied
    cleared: false,         // clearDrawings ran before the restore was applied
    replace: false,         // this setDrawings repeats an applied one: replace, do not add
    emitTimer: null,
    lastEmitted: null,      // JSON of the last drawingsChanged payload, to skip no-op moves
    stateTimer: null,
    lastState: null,
    clickedId: null,        // the overlay KLineChart has click-selected (onSelected/onDeselected)
    needsTextId: null,
    visible: true,
    mode: 'normal',
    quiet: 0                // > 0 while we remove overlays ourselves: onRemoved must not emit
  };

  function notice(action, payload) {
    if (hasBridge()) { global.Native.postMessage(JSON.stringify({ action: action, payload: payload })); }
  }

  function later(fn) { setTimeout(function () { if (chart !== null) { fn(); } }, 0); }

  /* A throw-away instance of a registered template: its totalStep, drawingMode and
     createPointFigures. Constructing one touches no chart. */
  var probes = Object.create(null);
  function probe(name) {
    if (!(name in probes)) {
      var Clazz = typeof name === 'string' ? K.getOverlayClass(name) : null;
      probes[name] = Clazz ? new Clazz() : null;
    }
    return probes[name];
  }

  function isTool(name) { return TOOLS.indexOf(name) >= 0 && probe(name) !== null; }

  // [min, max] points of a finished drawing of this template.
  function pointRange(name) {
    var p = probe(name);
    if (p.drawingMode === 'continuous' || p.totalStep > 1000) { return [2, Infinity]; }   // brush, anyWaves
    return [p.totalStep - 1, p.totalStep - 1];
  }

  function num(v) { return typeof v === 'number' && isFinite(v); }

  /* --- layout: where the candle pane's plot is, and how far down its legend reaches */

  // The candle pane's plot area in chart CSS px (== dp; the chart fills the page), or null
  // before the first layout. ChartImp.getSize (15057) returns the main widget's bounding.
  function plotArea() {
    if (chart === null) { return null; }
    var b = chart.getSize(CANDLE_PANE, 'main');
    if (!b || !(b.width > 0) || !(b.height > 0)) { return null; }
    return { left: b.left || 0, top: b.top || 0, width: b.width, height: b.height };
  }

  // Bottom of the legend KLineChart paints at the top of the candle pane, in pane px: tooltip
  // offsetTop + the title row + the O/H/L/C row, then a row (or more, once it wraps) per indicator
  // drawn on the candle pane (CandleTooltipView 7385-7440). KLineChart does not report it, so it
  // is worked out from the tooltip styles in buildStyles(); the per-figure width is generous.
  function legendBottom(width) {
    var h = 6 + (6 + 11 + 2) + (2 + 10 + 2);
    if (chart === null) { return h; }
    (chart.getIndicators({ paneId: CANDLE_PANE }) || []).forEach(function (ind) {
      var rowWidth = 70 + 80 * ((ind.figures || []).length);
      h += (4 + 11 + 2) * Math.max(1, Math.ceil(rowWidth / Math.max(1, width - 16)));
    });
    return h;
  }

  function shallowCopy(o) {
    var copy = {}, k;
    for (k in o) { if (Object.prototype.hasOwnProperty.call(o, k)) { copy[k] = o[k]; } }
    return copy;
  }

  // A text figure with its labels moved to the plot's right edge when any of them would sit on
  // the legend; the figure is copied, never changed in place. Only for labels drawn at the left
  // edge (EDGE_LABEL_TOOLS). The right edge is the empty area past the last bar
  // (setOffsetRightDistance in boot), so the moved labels cover little.
  function clearOfLegend(figure, bounding) {
    var attrs = [].concat(figure.attrs || []), band = legendBottom(bounding.width), hit = false;
    attrs.forEach(function (a) {
      if (!a) { return; }
      var top = a.baseline === 'bottom' ? a.y - (11 + 6) : a.y;   // text size + paddingTop/Bottom
      if (top < band) { hit = true; }
    });
    if (!hit) { return figure; }
    var copy = shallowCopy(figure);
    copy.attrs = attrs.map(function (a) {
      if (!a) { return a; }
      var moved = shallowCopy(a);
      moved.x = bounding.width - 4;
      moved.align = 'right';
      return moved;
    });
    return copy;
  }

  var measureCtx;
  function labelWidth(text) {
    if (measureCtx === undefined) {
      try { measureCtx = document.createElement('canvas').getContext('2d'); } catch (e) { measureCtx = null; }
    }
    if (!measureCtx) { return text.length * 6.5; }
    measureCtx.font = 'normal 11px ' + FAM;
    return measureCtx.measureText(text).width;
  }

  function pad2(n) { return (n < 10 ? '0' : '') + n; }

  // The crosshair's date format, through KLineChart's own formatter (timezone-aware) when the
  // internal StoreImp.getInnerFormatter (13353) is there, else in the page's local time.
  function formatPointDate(timestamp) {
    var store = typeof chart.getChartStore === 'function' ? chart.getChartStore() : null;
    if (store && typeof store.getInnerFormatter === 'function') {
      return store.getInnerFormatter().formatDate(timestamp, 'YYYY-MM-DD HH:mm', 'crosshair');
    }
    var d = new Date(timestamp);
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' +
      pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }

  /* The time-axis marks of a selected drawing: KLineChart's default (OverlayXAxisView 11138-11165:
     a band between the outer points and each point's date centred under it), except that a date
     is kept whole inside the plot and left of the `log` / `auto` pills instead of running under
     them or off the edge. The template's own createXAxisFigures, if any, still runs. */
  function pointDates(original) {
    return function (params) {
      var overlay = params.overlay, cs = params.coordinates || [], out = [];
      if (chart !== null && overlay.id === dr.clickedId && cs.length > 0) {
        var bounding = params.bounding, size = chart.getSize();
        var limit = bounding.width;
        if (size && size.width > 0) { limit = Math.min(limit, size.width - PILLS_RESERVE - (bounding.left || 0)); }
        var left = Infinity, right = -Infinity, labels = [];
        cs.forEach(function (c, i) {
          left = Math.min(left, c.x);
          right = Math.max(right, c.x);
          var pt = overlay.points[i];
          if (!pt || !num(pt.timestamp)) { return; }
          var text = formatPointDate(pt.timestamp);
          var half = (labelWidth(text) + DATE_LABEL_PADDING) / 2;
          var x = Math.max(half, Math.min(c.x, limit - half));
          labels.push({ type: 'text', attrs: { x: x, y: 0, text: text, align: 'center' }, ignoreEvent: true });
        });
        if (cs.length > 1) {
          out.push({ type: 'rect', attrs: { x: left, y: 0, width: right - left, height: bounding.height }, ignoreEvent: true });
        }
        out = out.concat(labels);
      }
      if (typeof original === 'function') { out = out.concat([].concat(original.call(this, params) || [])); }
      return out;
    };
  }

  /* --- touch hit area: an invisible figure KLineChart hit-tests like any other */

  function nearSegment(p, a, b) {
    var dx = b.x - a.x, dy = b.y - a.y, len2 = dx * dx + dy * dy, t = 0;
    if (len2 > 0) { t = Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2)); }
    var ex = a.x + t * dx - p.x, ey = a.y + t * dy - p.y;
    return ex * ex + ey * ey <= HIT * HIT;
  }

  function nearArc(p, arc) {
    if (!(arc.r > 0)) { return false; }
    var dx = p.x - arc.x, dy = p.y - arc.y;
    if (Math.abs(Math.sqrt(dx * dx + dy * dy) - arc.r) > HIT) { return false; }
    var full = 2 * Math.PI, span = arc.endAngle - arc.startAngle, slack = HIT / arc.r;
    if (span >= full) { return true; }
    var delta = ((Math.atan2(dy, dx) - arc.startAngle) % full + full) % full;
    return delta <= span + slack || delta >= full - slack;
  }

  K.registerFigure({
    name: 'tgHit',
    checkEventOn: function (p, attrs) {
      var list = [].concat(attrs), i, j, cs;
      for (i = 0; i < list.length; i++) {
        if (list[i].arc) {
          if (nearArc(p, list[i].arc)) { return true; }
          continue;
        }
        cs = list[i].coordinates;
        for (j = 1; j < cs.length; j++) {
          if (nearSegment(p, cs[j - 1], cs[j])) { return true; }
        }
      }
      return false;
    },
    draw: function () {}
  });

  function hitAttrs(name, figure) {
    var out = [];
    [].concat(figure.attrs || []).forEach(function (a) {
      if (!a) { return; }
      if (figure.type === 'arc') { out.push({ arc: a }); return; }
      var cs = a.coordinates;
      if (!cs || cs.length < 2) { return; }
      // simpleAnnotation: stretch the stem over the label above it.
      if (name === 'simpleAnnotation') { cs = [cs[0], { x: cs[1].x, y: cs[1].y - 22 }]; }
      out.push({ coordinates: cs });
    });
    return out;
  }

  /* Wraps a template's createPointFigures:
     - lines and arcs get a HIT-wide invisible twin ('tgHit'); annotations and tags, whose own
       figures all ignore events, get one along their stem/line so they can be selected at all;
     - an overlay that is not selected ignores presses (taps still select it), so a pan that
       starts on a drawing pans. Select first, then drag - the handles appear on selection. */
  function touchFriendly(name, original) {
    return function (params) {
      var figures = [].concat((original ? original.call(this, params) : null) || []);
      var selected = params.overlay.id === dr.clickedId;
      var out = [], hits = [];
      figures.forEach(function (f) {
        if (!f) { return; }
        var hittable = f.ignoreEvent !== true;
        if (f.type === 'text' && EDGE_LABEL_TOOLS[name]) { f = clearOfLegend(f, params.bounding); }
        if (hittable && !selected) {
          var copy = {}, k;
          for (k in f) { if (Object.prototype.hasOwnProperty.call(f, k)) { copy[k] = f[k]; } }
          copy.ignoreEvent = PRESS_EVENTS;
          f = copy;
        }
        out.push(f);
        if ((hittable || TEXT_TOOLS[name]) && (f.type === 'line' || f.type === 'arc')) {
          hits = hits.concat(hitAttrs(name, f));
        }
      });
      if (hits.length > 0) {
        out.push({ type: 'tgHit', attrs: hits, ignoreEvent: selected ? false : PRESS_EVENTS });
      }
      return out;
    };
  }

  /* --- the drawings in the store */

  function ours() { return chart === null ? [] : chart.getOverlays({ groupId: GROUP }); }

  function finishedById(id) {
    var list = ours(), i;
    for (i = 0; i < list.length; i++) {
      if (list[i].id === id && !list[i].isDrawing()) { return list[i]; }
    }
    return null;
  }

  function progress() {
    var list = ours(), i;
    for (i = 0; i < list.length; i++) { if (list[i].isDrawing()) { return list[i]; } }
    return null;
  }

  function hasText(o) { return typeof o.extendData === 'string' && o.extendData.length > 0; }

  function finished(o) {
    if (o.paneId !== CANDLE_PANE || !isTool(o.name)) { return false; }
    var range = pointRange(o.name), pts = o.points || [], i;
    if (pts.length < range[0] || pts.length > range[1]) { return false; }
    for (i = 0; i < pts.length; i++) {
      if (!pts[i] || !num(pts[i].timestamp) || !num(pts[i].value)) { return false; }
    }
    return true;
  }

  function serialise() {
    var out = [];
    ours().forEach(function (o) {
      if (o.isDrawing() || o.id === dr.needsTextId || !finished(o)) { return; }
      out.push({
        name: o.name,
        points: o.points.map(function (p) { return { timestamp: p.timestamp, value: p.value }; }),
        lock: !!o.lock,
        mode: MODES[o.mode] ? o.mode : 'normal',
        text: TEXT_TOOLS[o.name] && hasText(o) ? o.extendData : null
      });
    });
    return out;
  }

  function payloadNow() {
    return { exchange: dr.sym.exchange, ticker: dr.sym.ticker, drawings: serialise() };
  }

  function flushEmit() {
    if (dr.emitTimer !== null) { clearTimeout(dr.emitTimer); dr.emitTimer = null; }
    if (chart === null || dr.sym === null || dr.restore !== 'applied') { return; }
    var payload = payloadNow();
    var json = JSON.stringify(payload);
    if (json === dr.lastEmitted) { return; }
    dr.lastEmitted = json;
    notice('drawingsChanged', payload);
  }

  // A finished change: persist it ~150 ms later (moves fire in bursts), never before the saved
  // drawings for this market are back on the chart - that would overwrite them.
  function changed() {
    if (dr.sym === null) { return; }
    if (dr.restore !== 'applied') { dr.dirty = true; return; }
    if (dr.emitTimer !== null) { clearTimeout(dr.emitTimer); }
    dr.emitTimer = setTimeout(flushEmit, EMIT_DEBOUNCE);
  }

  function stateNow() {
    var list = ours(), pr = null, sel = null, count = 0, text = null, textTool = null;
    list.forEach(function (o) {
      if (o.isDrawing()) { pr = o; return; }
      count++;
      if (o.id === dr.clickedId) { sel = o; }
      if (o.id === dr.needsTextId) { text = o.id; textTool = o.name; }
    });
    // Where the Compose strip may go: only while it is shown, so plain panning never re-sends it.
    var area = (pr !== null || sel !== null) ? plotArea() : null;
    return {
      drawing: pr !== null,
      tool: pr !== null ? pr.name : null,
      selectedId: sel !== null ? sel.id : null,
      selectedName: sel !== null ? sel.name : null,
      selectedLocked: sel !== null ? !!sel.lock : false,
      count: count,
      needsTextId: text,
      needsTextTool: textTool,
      visible: dr.visible,
      plotLeft: area !== null ? Math.round(area.left) : 0,
      plotBottom: area !== null ? Math.round(area.top + area.height) : 0,
      plotWidth: area !== null ? Math.round(area.width) : 0,
      // Bottom of the candle legend (title, O/H/L/C, main-pane indicators): the strip stays below it.
      legendBottom: area !== null ? Math.round(area.top + legendBottom(area.width)) : 0
    };
  }

  function emitState() {
    dr.stateTimer = null;
    if (chart === null) { return; }
    var state = stateNow();
    var json = JSON.stringify(state);
    if (json === dr.lastState) { return; }
    dr.lastState = json;
    notice('drawingState', state);
  }

  // Coalesced and deferred: KLineChart fires onDrawEnd before it click-selects the new drawing.
  function stateSoon() {
    if (dr.stateTimer === null) { dr.stateTimer = setTimeout(emitState, 0); }
  }

  function removeQuietly(filter) {
    dr.quiet++;
    try { chart.removeOverlay(filter); } finally { dr.quiet--; }
  }

  // Clears KLineChart's click selection. ChartImp.getChartStore (14718) and
  // StoreImp.setClickOverlayInfo (14543) are not in the public d.ts; both are guarded.
  function deselect() {
    dr.clickedId = null;
    if (chart === null || typeof chart.getChartStore !== 'function') { return; }
    var store = chart.getChartStore();
    var none = function () { return false; };
    if (store && typeof store.setClickOverlayInfo === 'function') {
      store.setClickOverlayInfo(
        { paneId: CANDLE_PANE, overlay: null, figureType: 'none', figureIndex: -1, figure: null }, none, none);
    }
    // A finger leaves KLineChart's hover info on the last overlay it touched, and point handles
    // are drawn for the hovered overlay too (drawDefaultFigures 8996-8998): clear it as well, so
    // a share right after a deselect shows no handles. StoreImp.setHoverOverlayInfo (14504).
    if (store && typeof store.setHoverOverlayInfo === 'function') {
      store.setHoverOverlayInfo(
        { paneId: CANDLE_PANE, overlay: null, figureType: 'none', figureIndex: -1, figure: null }, none, none);
    }
  }

  /* --- event hooks put on every overlay the app creates */

  function onSelected(e) { dr.clickedId = e.overlay.id; stateSoon(); }

  function onDeselected(e) {
    if (dr.clickedId === e.overlay.id) { dr.clickedId = null; }
    stateSoon();
  }

  function onDrawEnd(e) {
    var o = e.overlay;
    if (!finished(o)) {
      // Too few points (a double tap), a point on an axis, or drawn in an indicator pane:
      // drop it and keep the same tool armed.
      var id = o.id, name = o.name;
      later(function () {
        removeQuietly({ id: id });
        if (progress() === null) { createDrawing(name, null); }
        stateSoon();
      });
      return;
    }
    if (TEXT_TOOLS[o.name] && !hasText(o)) { dr.needsTextId = o.id; } else { changed(); }
    stateSoon();
  }

  function onPressedMoveEnd() { changed(); }

  function onRemoved(e) {
    if (e.overlay.id === dr.needsTextId) { dr.needsTextId = null; }
    if (e.overlay.id === dr.clickedId) { dr.clickedId = null; }
    if (dr.quiet === 0) { changed(); }
    stateSoon();
  }

  // spec: null for a new interactive drawing, else a sanitised saved drawing.
  function createDrawing(name, spec) {
    var p = probe(name);
    if (chart === null || p === null) { return null; }
    var create = {
      name: name,
      groupId: GROUP,
      paneId: CANDLE_PANE,
      lock: spec !== null ? spec.lock : false,
      // Always the page's current magnet, restored drawings included: a setMagnet that ran while
      // the restore was still waiting for bars must reach them too ("overrides all existing").
      mode: dr.mode,
      visible: dr.visible,
      createPointFigures: touchFriendly(name, p.createPointFigures),
      onDrawEnd: onDrawEnd,
      onPressedMoveEnd: onPressedMoveEnd,
      onSelected: onSelected,
      onDeselected: onDeselected,
      onRemoved: onRemoved
    };
    if (p.needDefaultXAxisFigure) {                    // the dates of a selected drawing, kept readable
      create.needDefaultXAxisFigure = false;
      create.createXAxisFigures = pointDates(p.createXAxisFigures);
    }
    if (TEXT_TOOLS[name]) {
      create.needDefaultPointFigure = true;            // a draggable anchor once selected
      create.extendData = spec !== null ? spec.text : null;
    }
    if (name === 'simpleAnnotation') {
      create.styles = { polygon: { color: THEME.draw } };   // the arrow head is solid, not a 15 % fill
    }
    if (spec !== null) { create.points = spec.points; }
    return chart.createOverlay(create);
  }

  function sanitise(list) {
    var out = [];
    (Array.isArray(list) ? list : []).forEach(function (d) {
      if (!d || typeof d !== 'object' || !isTool(d.name) || !Array.isArray(d.points)) { return; }
      var points = [], ok = true;
      d.points.forEach(function (p) {
        if (p && num(p.timestamp) && num(p.value)) {
          points.push({ timestamp: p.timestamp, value: p.value });
        } else { ok = false; }
      });
      var range = pointRange(d.name);
      if (!ok || points.length < range[0] || points.length > range[1]) { return; }
      out.push({
        name: d.name,
        points: points,
        lock: d.lock === true,
        mode: MODES[d.mode] ? d.mode : 'normal',
        text: TEXT_TOOLS[d.name] && typeof d.text === 'string' && d.text.length > 0 ? d.text : null
      });
    });
    return out;
  }

  function applyRestore() {
    var list = dr.cleared ? [] : (dr.pendingList || []);
    dr.pendingList = null;
    dr.cleared = false;
    deselect();
    // A repeated setDrawings for the same market replaces the restored set. The first one keeps
    // what the user drew while Room was still loading (dr.dirty) and adds the saved set to it.
    if (dr.replace) {
      ours().forEach(function (o) { if (!o.isDrawing()) { removeQuietly({ id: o.id }); } });
    }
    dr.replace = false;
    list.forEach(function (d) { createDrawing(d.name, d); });
    dr.restore = 'applied';
    dr.lastEmitted = JSON.stringify(payloadNow());
    if (dr.dirty) { dr.dirty = false; dr.lastEmitted = null; changed(); }   // what Room has differs
    stateSoon();
  }

  // Called when the init getBars of generation `gen` has put bars on the chart.
  function barsDelivered(gen) {
    dr.loadedGen = gen;
    if (dr.restore === 'pending') { applyRestore(); }
  }

  // Runs at the top of setMarket, while dr.sym is still the market being left.
  function drawingsBeforeSwap(sym) {
    flushEmit();                                   // the last edit belongs to the old market
    deselect();
    if (chart !== null) { removeQuietly({ groupId: GROUP }); }
    dr.sym = { exchange: sym.exchange, ticker: sym.ticker };
    dr.restore = 'awaiting';
    dr.pendingList = null;
    dr.dirty = false;
    dr.cleared = false;
    dr.replace = false;
    dr.lastEmitted = null;
    dr.needsTextId = null;
    dr.lastState = null;                           // Kotlin resets its mirror on a swap: resend
    stateSoon();
  }

  var drawingApi = {
    /** payload = {exchange, ticker, drawings:[{name, points:[{timestamp, value}], lock, mode, text}]} */
    setDrawings: function (payload) {
      if (chart === null || dr.sym === null || !payload ||
          payload.exchange !== dr.sym.exchange || payload.ticker !== dr.sym.ticker) {
        note('warn', 'setDrawings ignored: not the current market');
        return;
      }
      dr.pendingList = sanitise(payload.drawings);
      dr.replace = dr.replace || dr.restore === 'applied';
      dr.restore = 'pending';
      if (dr.loadedGen === generation) { applyRestore(); }
    },

    /** name = an overlay template name from TOOLS */
    startDrawing: function (name) {
      if (chart === null || !isTool(name)) { note('warn', 'startDrawing: unknown tool ' + name); return; }
      drawingApi.cancelDrawing();
      deselect();
      if (!dr.visible) { drawingApi.setDrawingsVisible(true); }   // never draw into hidden drawings
      createDrawing(name, null);
      stateSoon();
    },

    cancelDrawing: function () {
      var p = progress();
      if (p !== null) { removeQuietly({ id: p.id }); }
      stateSoon();
    },

    /** mode = 'none' | 'weak_magnet' | 'strong_magnet' */
    setMagnet: function (mode) {
      var m = (mode === 'none') ? 'normal' : mode;
      if (!MODES[m]) { return; }
      dr.mode = m;
      // Not a drawing change: no drawingsChanged. Kotlin restores every drawing with the current
      // magnet anyway, and the next real change saves the new mode with it.
      if (chart !== null) { chart.overrideOverlay({ groupId: GROUP, mode: m }); }
    },

    setDrawingsVisible: function (visible) {
      dr.visible = !!visible;
      // Hiding also disarms the tool: an invisible in-progress drawing would still take taps and
      // end up saved without the user ever seeing it.
      if (!dr.visible) { drawingApi.cancelDrawing(); deselect(); }
      if (chart !== null) { chart.overrideOverlay({ groupId: GROUP, visible: dr.visible }); }
      stateSoon();
    },

    removeSelectedDrawing: function () {
      var o = finishedById(dr.clickedId);
      if (o === null) { return; }
      deselect();
      chart.removeOverlay({ id: o.id });              // onRemoved -> drawingsChanged
      stateSoon();
    },

    toggleSelectedLock: function () {
      var o = finishedById(dr.clickedId);
      if (o === null) { return; }
      chart.overrideOverlay({ id: o.id, lock: !o.lock });
      changed();
      stateSoon();
    },

    deselectDrawing: function () { deselect(); stateSoon(); },

    clearDrawings: function () {
      if (chart === null) { return; }
      deselect();
      removeQuietly({ groupId: GROUP });
      dr.needsTextId = null;
      if (dr.restore !== 'applied') { dr.cleared = true; }
      changed();
      stateSoon();
    },

    /** id = overlay id from drawingState.needsTextId / selectedId; text = the label ('' = none) */
    setDrawingText: function (id, text) {
      var o = finishedById(id);
      if (o === null || !TEXT_TOOLS[o.name]) { return; }
      var t = (typeof text === 'string') ? text : '';
      chart.overrideOverlay({ id: id, extendData: t.length > 0 ? t : null });
      if (dr.needsTextId === id) { dr.needsTextId = null; }
      changed();
      stateSoon();
    },

    removeDrawing: function (id) {
      var o = finishedById(id);
      if (o === null) { return; }
      if (dr.clickedId === id) { deselect(); }
      chart.removeOverlay({ id: id });                // onRemoved -> drawingsChanged
      stateSoon();
    }
  };

  // The WebView can be torn down inside the debounce window: persist on the way out.
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') { flushEmit(); }
  });

  /* While a drawing is being placed, a tap outside the candle pane's plot (price axis, time axis,
     an indicator pane) would still become its next point: the y-axis widget of the same pane
     places it with the tapped price and no timestamp (at the plot's far left), another pane takes
     the drawing over (mouseClickEvent 8507-8524). Such gestures are swallowed here, in the
     window's capture phase, before KLineChart's listeners on the chart element see them. */
  var guard = { touch: false };

  function outsidePlot(clientX, clientY) {
    var area = plotArea(), host = document.getElementById('chart');
    if (area === null || host === null) { return false; }
    var r = host.getBoundingClientRect(), x = clientX - r.left, y = clientY - r.top;
    return x < area.left || x >= area.left + area.width || y < area.top || y >= area.top + area.height;
  }

  function placingOutside(clientX, clientY) {
    return chart !== null && progress() !== null && outsidePlot(clientX, clientY);
  }

  function guardTouch(e) {
    if (e.type === 'touchstart' && e.touches.length === 1) {
      guard.touch = placingOutside(e.touches[0].clientX, e.touches[0].clientY);
    }
    if (!guard.touch) { return; }
    e.stopPropagation();
    if (e.type === 'touchend' || e.type === 'touchcancel') {
      if (e.cancelable) { e.preventDefault(); }      // no compatibility mouse events either
      if (e.touches.length === 0) { guard.touch = false; }
    }
  }

  function guardMouse(e) {
    if (placingOutside(e.clientX, e.clientY)) { e.stopPropagation(); }
  }

  ['touchstart', 'touchmove', 'touchend', 'touchcancel'].forEach(function (type) {
    window.addEventListener(type, guardTouch, { capture: true, passive: false });
  });
  ['mousedown', 'mouseup', 'click', 'dblclick'].forEach(function (type) {
    window.addEventListener(type, guardMouse, true);
  });

  /* Android sends ACTION_CANCEL (touchcancel) when a system gesture or a parent view takes a
     touch over mid-gesture. KLineChart only clears its long-tap timer then (EventHandlerImp._init
     1838): no mouseUpEvent (8595), so a moved drawing never reaches onPressedMoveEnd - it would
     sit at its new place unsaved - and a brush stroke stays in progress, the next drag joined to
     it. Its handler also keeps the cancelled touch as active (_activeTouchId, 1759-1761) and
     ignores the next touchstart. A gesture that moved is therefore replayed as a lift on the root
     element KLineChart listens on (_touchEndHandler 1666): it ends the drag or the stroke and
     resets the handler exactly like a real one, and no tap fires because it moved (1628-1636).
     A cancel without movement cannot be replayed (it would become a tap, i.e. a point of the
     drawing being placed); only its drawing-side effects are undone here. */
  var gesture = { x: 0, y: 0, moved: false, seen: false };

  window.addEventListener('touchstart', function (e) {
    if (e.touches.length !== 1) { return; }
    var t = e.touches[0];
    // guardTouch (registered before) has decided already whether the chart sees this gesture.
    gesture = { x: t.clientX, y: t.clientY, moved: false, seen: !guard.touch };
  }, true);

  window.addEventListener('touchmove', function (e) {
    var t = e.touches[0];
    if (t && Math.abs(t.clientX - gesture.x) + Math.abs(t.clientY - gesture.y) >= CANCEL_TAP_PX) {
      gesture.moved = true;
    }
  }, true);

  function touchCancelled(e) {
    if (chart === null || typeof chart.getChartStore !== 'function') { return; }
    var t = e.changedTouches && e.changedTouches.length > 0 ? e.changedTouches[0] : null;
    var replay = gesture.seen && gesture.moved && t !== null && typeof TouchEvent === 'function';
    gesture.seen = false;
    if (replay) {
      try {
        document.documentElement.dispatchEvent(new TouchEvent('touchend',
          { bubbles: true, cancelable: true, changedTouches: [t], touches: [], targetTouches: [] }));
        stateSoon();
        return;
      } catch (err) { note('warn', 'touchcancel replay failed: ' + err); }
    }
    var store = chart.getChartStore(), pr = progress();
    if (pr !== null && typeof pr.isContinuousDrawingMode === 'function' &&
        pr.isContinuousDrawingMode() && !pr.isStart()) {
      pr.forceComplete();
      store.progressOverlayComplete();
      onDrawEnd({ chart: chart, overlay: pr });       // too few points: dropped, tool re-armed
    }
    var info = typeof store.getPressedOverlayInfo === 'function' ? store.getPressedOverlayInfo() : null;
    if (info && info.overlay) {
      if (info.overlay.groupId === GROUP) { changed(); }
      store.setPressedOverlayInfo(
        { paneId: info.paneId, overlay: null, figureType: 'none', figureIndex: -1, figure: null });
    }
    stateSoon();
  }
  window.addEventListener('touchcancel', touchCancelled, true);

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
    // The y-axis width and the pane heights move the plot the drawing strip is placed against;
    // stateNow() only reports them while the strip is up, and unchanged states are not re-sent.
    chart.subscribeAction('onVisibleRangeChange', stateSoon);
    chart.subscribeAction('onPaneDrag', stateSoon);
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
      drawingsBeforeSwap(sym);                            // before the swap: saves, then removes ours
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

    setIndicators: function (spec) {
      applyIndicators(spec);
      setTimeout(stateSoon, 50);                     // panes came or went: the plot moved
    },

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
    resize:           function () {
      if (chart !== null) { chart.resize(); setTimeout(stateSoon, 100); }   // resize waits a frame (14640)
    },
    dispose:          function () {
      if (chart !== null) { flushEmit(); K.dispose(chart); chart = null; }
    },

    // Drawing tools: see "drawings" above for the arguments and the notices they cause.
    setDrawings:           drawingApi.setDrawings,
    startDrawing:          drawingApi.startDrawing,
    cancelDrawing:         drawingApi.cancelDrawing,
    setMagnet:             drawingApi.setMagnet,
    setDrawingsVisible:    drawingApi.setDrawingsVisible,
    removeSelectedDrawing: drawingApi.removeSelectedDrawing,
    toggleSelectedLock:    drawingApi.toggleSelectedLock,
    deselectDrawing:       drawingApi.deselectDrawing,
    clearDrawings:         drawingApi.clearDrawings,
    setDrawingText:        drawingApi.setDrawingText,
    removeDrawing:         drawingApi.removeDrawing
  };

  // register the alias y-axis used by the resetAutoScale fallback
  K.registerYAxis({ name: 'tg_normal_alt' });

  global.tg = tg;
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else { boot(); }
})(window);
