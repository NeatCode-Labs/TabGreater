/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Overlay templates from KLineChart Pro — https://github.com/klinecharts/pro
   Source: src/extension/*.ts (and src/extension/utils.ts) at commit
   383722df91fed0ac0befe12cb87e67a2f112e121, Apache License 2.0. Copyright the KLineChart Pro
   authors (author per package.json: liihuu; the upstream files carry no copyright line).
   Ported to plain JavaScript for TabGreater: TypeScript types, ES modules, spread syntax, template
   literals and optional chaining removed; utils.ts helpers are local functions; the templates'
   hard-coded fill colours are dropped so the chart's global overlay styles (chart.js,
   buildStyles().overlay) apply; fibonacciSegment/fibonacciExtension format their level prices
   like the built-in fibonacciLine (thousands separator). The geometry is unchanged. See NOTICE and
   docs/VENDORED-KLINECHART.md ("Adapted overlay templates").

   Loaded after vendor/klinecharts.js and before chart.js; registers 17 templates at load:
   arrow, circle, rect, triangle, parallelogram, fibonacciCircle, fibonacciSegment, fibonacciSpiral,
   fibonacciSpeedResistanceFan, fibonacciExtension, gannBox, threeWaves, fiveWaves, eightWaves,
   anyWaves, abcd, xabcd.                                                                          */
(function (global) {
  'use strict';

  var K = global.klinecharts;
  var utils = K.utils;

  /* ------------------------------------------------------------ utils.ts */

  function getRotateCoordinate(coordinate, targetCoordinate, angle) {
    var x = (coordinate.x - targetCoordinate.x) * Math.cos(angle) - (coordinate.y - targetCoordinate.y) * Math.sin(angle) + targetCoordinate.x;
    var y = (coordinate.x - targetCoordinate.x) * Math.sin(angle) + (coordinate.y - targetCoordinate.y) * Math.cos(angle) + targetCoordinate.y;
    return { x: x, y: y };
  }

  function getRayLine(coordinates, bounding) {
    if (coordinates.length > 1) {
      var coordinate;
      if (coordinates[0].x === coordinates[1].x && coordinates[0].y !== coordinates[1].y) {
        if (coordinates[0].y < coordinates[1].y) {
          coordinate = { x: coordinates[0].x, y: bounding.height };
        } else {
          coordinate = { x: coordinates[0].x, y: 0 };
        }
      } else if (coordinates[0].x > coordinates[1].x) {
        coordinate = {
          x: 0,
          y: utils.getLinearYFromCoordinates(coordinates[0], coordinates[1], { x: 0, y: coordinates[0].y })
        };
      } else {
        coordinate = {
          x: bounding.width,
          y: utils.getLinearYFromCoordinates(coordinates[0], coordinates[1], { x: bounding.width, y: coordinates[0].y })
        };
      }
      return { coordinates: [coordinates[0], coordinate] };
    }
    return [];
  }

  function getDistance(coordinate1, coordinate2) {
    var xDis = Math.abs(coordinate1.x - coordinate2.x);
    var yDis = Math.abs(coordinate1.y - coordinate2.y);
    return Math.sqrt(xDis * xDis + yDis * yDis);
  }

  /* chart.getSymbol()?.pricePrecision ?? 2 */
  function pricePrecision(chart) {
    var symbol = chart && typeof chart.getSymbol === 'function' ? chart.getSymbol() : null;
    var p = symbol ? symbol.pricePrecision : null;
    return (typeof p === 'number' && isFinite(p)) ? p : 2;
  }

  /* A level price as the built-in fibonacciLine prints it (vendor/klinecharts.js 12013): the
     chart's thousands separator and decimal fold over toFixed. Pro prints the bare toFixed. */
  function levelPrice(chart, value, precision) {
    var text = value.toFixed(precision);
    if (chart && typeof chart.getThousandsSeparator === 'function' && typeof chart.getDecimalFold === 'function') {
      text = chart.getDecimalFold().format(chart.getThousandsSeparator().format(text));
    }
    return text;
  }

  /* The angle of the segment c0 -> c1, shared by arrow and fibonacciSpiral. */
  function segmentAngle(c0, c1) {
    var flag = c1.x > c0.x ? 0 : 1;
    var kb = utils.getLinearSlopeIntercept(c0, c1);
    if (kb) {
      return Math.atan(kb[0]) + Math.PI * flag;
    }
    return c1.y > c0.y ? Math.PI / 2 : Math.PI / 2 * 3;
  }

  /* `{ ...coordinate, text: '(i)', baseline: 'bottom' }` for the wave/pattern labels. */
  function labels(coordinates, tags) {
    return coordinates.map(function (coordinate, i) {
      return {
        x: coordinate.x,
        y: coordinate.y,
        text: '(' + (tags ? tags[i] : i) + ')',
        baseline: 'bottom'
      };
    });
  }

  /* ------------------------------------------------------------ arrow.ts */

  var arrow = {
    name: 'arrow',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      if (coordinates.length > 1) {
        var offsetAngle = segmentAngle(coordinates[0], coordinates[1]);
        var rotateCoordinate1 = getRotateCoordinate({ x: coordinates[1].x - 8, y: coordinates[1].y + 4 }, coordinates[1], offsetAngle);
        var rotateCoordinate2 = getRotateCoordinate({ x: coordinates[1].x - 8, y: coordinates[1].y - 4 }, coordinates[1], offsetAngle);
        return [
          { type: 'line', attrs: { coordinates: coordinates } },
          { type: 'line', ignoreEvent: true, attrs: { coordinates: [rotateCoordinate1, coordinates[1], rotateCoordinate2] } }
        ];
      }
      return [];
    }
  };

  /* ----------------------------------------------------------- circle.ts */

  var circle = {
    name: 'circle',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      if (coordinates.length > 1) {
        var radius = getDistance(coordinates[0], coordinates[1]);
        return {
          type: 'circle',
          attrs: { x: coordinates[0].x, y: coordinates[0].y, r: radius },
          styles: { style: 'stroke_fill' }
        };
      }
      return [];
    }
  };

  /* ------------------------------------------------------------- rect.ts */

  var rect = {
    name: 'rect',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      if (coordinates.length > 1) {
        return [
          {
            type: 'polygon',
            attrs: {
              coordinates: [
                coordinates[0],
                { x: coordinates[1].x, y: coordinates[0].y },
                coordinates[1],
                { x: coordinates[0].x, y: coordinates[1].y }
              ]
            },
            styles: { style: 'stroke_fill' }
          }
        ];
      }
      return [];
    }
  };

  /* --------------------------------------------------------- triangle.ts */

  var triangle = {
    name: 'triangle',
    totalStep: 4,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      return [
        { type: 'polygon', attrs: { coordinates: params.coordinates }, styles: { style: 'stroke_fill' } }
      ];
    }
  };

  /* ---------------------------------------------------- parallelogram.ts */

  var parallelogram = {
    name: 'parallelogram',
    totalStep: 4,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      if (coordinates.length === 2) {
        return [
          { type: 'line', ignoreEvent: true, attrs: { coordinates: coordinates } }
        ];
      }
      if (coordinates.length === 3) {
        var coordinate = { x: coordinates[0].x + (coordinates[2].x - coordinates[1].x), y: coordinates[2].y };
        return [
          {
            type: 'polygon',
            attrs: { coordinates: [coordinates[0], coordinates[1], coordinates[2], coordinate] },
            styles: { style: 'stroke_fill' }
          }
        ];
      }
      return [];
    },
    performEventPressedMove: function (params) {
      if (params.performPointIndex < 2) {
        params.points[0].value = params.performPoint.value;
        params.points[1].value = params.performPoint.value;
      }
    },
    performEventMoveForDrawing: function (params) {
      if (params.currentStep === 2) {
        params.points[0].value = params.performPoint.value;
      }
    }
  };

  /* -------------------------------------------------- fibonacciCircle.ts */

  var fibonacciCircle = {
    name: 'fibonacciCircle',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      if (coordinates.length > 1) {
        var xDis = Math.abs(coordinates[0].x - coordinates[1].x);
        var yDis = Math.abs(coordinates[0].y - coordinates[1].y);
        var radius = Math.sqrt(xDis * xDis + yDis * yDis);
        var percents = [0.236, 0.382, 0.5, 0.618, 0.786, 1];
        var circles = [];
        var texts = [];
        percents.forEach(function (percent) {
          var r = radius * percent;
          circles.push({ x: coordinates[0].x, y: coordinates[0].y, r: r });
          texts.push({
            x: coordinates[0].x,
            y: coordinates[0].y + r + 6,
            text: (percent * 100).toFixed(1) + '%'
          });
        });
        return [
          { type: 'circle', attrs: circles, styles: { style: 'stroke' } },
          { type: 'text', ignoreEvent: true, attrs: texts }
        ];
      }
      return [];
    }
  };

  /* ------------------------------------------------- fibonacciSegment.ts */

  var fibonacciSegment = {
    name: 'fibonacciSegment',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var chart = params.chart, coordinates = params.coordinates, overlay = params.overlay;
      var lines = [];
      var texts = [];
      if (coordinates.length > 1) {
        var textX = coordinates[1].x > coordinates[0].x ? coordinates[0].x : coordinates[1].x;
        var percents = [1, 0.786, 0.618, 0.5, 0.382, 0.236, 0];
        var yDif = coordinates[0].y - coordinates[1].y;
        var points = overlay.points;
        var valueDif = Number(points[0].value) - Number(points[1].value);
        var precision = pricePrecision(chart);
        percents.forEach(function (percent) {
          var y = coordinates[1].y + yDif * percent;
          var price = levelPrice(chart, Number(points[1].value) + valueDif * percent, precision);
          lines.push({ coordinates: [{ x: coordinates[0].x, y: y }, { x: coordinates[1].x, y: y }] });
          texts.push({
            x: textX,
            y: y,
            text: price + ' (' + (percent * 100).toFixed(1) + '%)',
            baseline: 'bottom'
          });
        });
      }
      return [
        { type: 'line', attrs: lines },
        { type: 'text', ignoreEvent: true, attrs: texts }
      ];
    }
  };

  /* -------------------------------------------------- fibonacciSpiral.ts */

  var fibonacciSpiral = {
    name: 'fibonacciSpiral',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates, bounding = params.bounding;
      if (coordinates.length > 1) {
        var startRadius = getDistance(coordinates[0], coordinates[1]) / Math.sqrt(24);
        var offsetAngle = segmentAngle(coordinates[0], coordinates[1]);
        var rotateCoordinate1 = getRotateCoordinate(
          { x: coordinates[0].x - startRadius, y: coordinates[0].y },
          coordinates[0],
          offsetAngle
        );
        var rotateCoordinate2 = getRotateCoordinate(
          { x: coordinates[0].x - startRadius, y: coordinates[0].y - startRadius },
          coordinates[0],
          offsetAngle
        );
        var arcs = [{
          x: rotateCoordinate1.x,
          y: rotateCoordinate1.y,
          r: startRadius,
          startAngle: offsetAngle,
          endAngle: offsetAngle + Math.PI / 2
        }, {
          x: rotateCoordinate2.x,
          y: rotateCoordinate2.y,
          r: startRadius * 2,
          startAngle: offsetAngle + Math.PI / 2,
          endAngle: offsetAngle + Math.PI
        }];
        var x = coordinates[0].x - startRadius;
        var y = coordinates[0].y - startRadius;
        for (var i = 2; i < 9; i++) {
          var r = arcs[i - 2].r + arcs[i - 1].r;
          var startAngle = 0;
          switch (i % 4) {
            case 0: {
              startAngle = offsetAngle;
              x -= arcs[i - 2].r;
              break;
            }
            case 1: {
              startAngle = offsetAngle + Math.PI / 2;
              y -= arcs[i - 2].r;
              break;
            }
            case 2: {
              startAngle = offsetAngle + Math.PI;
              x += arcs[i - 2].r;
              break;
            }
            case 3: {
              startAngle = offsetAngle + Math.PI / 2 * 3;
              y += arcs[i - 2].r;
              break;
            }
          }
          var endAngle = startAngle + Math.PI / 2;
          var rotateCoordinate = getRotateCoordinate({ x: x, y: y }, coordinates[0], offsetAngle);
          arcs.push({
            x: rotateCoordinate.x,
            y: rotateCoordinate.y,
            r: r,
            startAngle: startAngle,
            endAngle: endAngle
          });
        }
        return [
          { type: 'arc', attrs: arcs },
          { type: 'line', attrs: getRayLine(coordinates, bounding) }
        ];
      }
      return [];
    }
  };

  /* -------------------------------------- fibonacciSpeedResistanceFan.ts */

  var fibonacciSpeedResistanceFan = {
    name: 'fibonacciSpeedResistanceFan',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates, bounding = params.bounding;
      var lines1 = [];
      var lines2 = [];
      var texts = [];
      if (coordinates.length > 1) {
        var xOffset = coordinates[1].x > coordinates[0].x ? -38 : 4;
        var yOffset = coordinates[1].y > coordinates[0].y ? -2 : 20;
        var xDistance = coordinates[1].x - coordinates[0].x;
        var yDistance = coordinates[1].y - coordinates[0].y;
        var percents = [1, 0.75, 0.618, 0.5, 0.382, 0.25, 0];
        percents.forEach(function (percent) {
          var x = coordinates[1].x - xDistance * percent;
          var y = coordinates[1].y - yDistance * percent;
          lines1.push({ coordinates: [{ x: x, y: coordinates[0].y }, { x: x, y: coordinates[1].y }] });
          lines1.push({ coordinates: [{ x: coordinates[0].x, y: y }, { x: coordinates[1].x, y: y }] });
          lines2 = lines2.concat(getRayLine([coordinates[0], { x: x, y: coordinates[1].y }], bounding));
          lines2 = lines2.concat(getRayLine([coordinates[0], { x: coordinates[1].x, y: y }], bounding));
          texts.unshift({
            x: coordinates[0].x + xOffset,
            y: y + 10,
            text: percent.toFixed(3)
          });
          texts.unshift({
            x: x - 18,
            y: coordinates[0].y + yOffset,
            text: percent.toFixed(3)
          });
        });
      }
      return [
        { type: 'line', attrs: lines1 },
        { type: 'line', attrs: lines2 },
        { type: 'text', ignoreEvent: true, attrs: texts }
      ];
    }
  };

  /* ----------------------------------------------- fibonacciExtension.ts */

  var fibonacciExtension = {
    name: 'fibonacciExtension',
    totalStep: 4,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var chart = params.chart, coordinates = params.coordinates, overlay = params.overlay;
      var fbLines = [];
      var texts = [];
      if (coordinates.length > 2) {
        var points = overlay.points;
        var valueDif = Number(points[1].value) - Number(points[0].value);
        var yDif = coordinates[1].y - coordinates[0].y;
        var percents = [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1];
        var textX = coordinates[2].x > coordinates[1].x ? coordinates[1].x : coordinates[2].x;
        var precision = pricePrecision(chart);
        percents.forEach(function (percent) {
          var y = coordinates[2].y + yDif * percent;
          var price = levelPrice(chart, Number(points[2].value) + valueDif * percent, precision);
          fbLines.push({ coordinates: [{ x: coordinates[1].x, y: y }, { x: coordinates[2].x, y: y }] });
          texts.push({
            x: textX,
            y: y,
            text: price + ' (' + (percent * 100).toFixed(1) + '%)',
            baseline: 'bottom'
          });
        });
      }
      return [
        { type: 'line', attrs: { coordinates: coordinates }, styles: { style: 'dashed' } },
        { type: 'line', attrs: fbLines },
        { type: 'text', ignoreEvent: true, attrs: texts }
      ];
    }
  };

  /* ---------------------------------------------------------- gannBox.ts */

  var gannBox = {
    name: 'gannBox',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      if (coordinates.length > 1) {
        var c0 = coordinates[0], c1 = coordinates[1];
        var quarterYDis = (c1.y - c0.y) / 4;
        var xDis = c1.x - c0.x;
        var dashedLines = [
          { coordinates: [c0, { x: c1.x, y: c1.y - quarterYDis }] },
          { coordinates: [c0, { x: c1.x, y: c1.y - quarterYDis * 2 }] },
          { coordinates: [{ x: c0.x, y: c1.y }, { x: c1.x, y: c0.y + quarterYDis }] },
          { coordinates: [{ x: c0.x, y: c1.y }, { x: c1.x, y: c0.y + quarterYDis * 2 }] },

          { coordinates: [{ x: c0.x, y: c0.y }, { x: c0.x + xDis * 0.236, y: c1.y }] },
          { coordinates: [{ x: c0.x, y: c0.y }, { x: c0.x + xDis * 0.5, y: c1.y }] },

          { coordinates: [{ x: c0.x, y: c1.y }, { x: c0.x + xDis * 0.236, y: c0.y }] },
          { coordinates: [{ x: c0.x, y: c1.y }, { x: c0.x + xDis * 0.5, y: c0.y }] }
        ];
        var solidLines = [
          { coordinates: [c0, c1] },
          { coordinates: [{ x: c0.x, y: c1.y }, { x: c1.x, y: c0.y }] }
        ];
        return [
          {
            type: 'line',
            attrs: [
              { coordinates: [c0, { x: c1.x, y: c0.y }] },
              { coordinates: [{ x: c1.x, y: c0.y }, c1] },
              { coordinates: [c1, { x: c0.x, y: c1.y }] },
              { coordinates: [{ x: c0.x, y: c1.y }, c0] }
            ]
          },
          {
            type: 'polygon',
            ignoreEvent: true,
            attrs: {
              coordinates: [c0, { x: c1.x, y: c0.y }, c1, { x: c0.x, y: c1.y }]
            },
            styles: { style: 'fill' }
          },
          { type: 'line', attrs: dashedLines, styles: { style: 'dashed' } },
          { type: 'line', attrs: solidLines }
        ];
      }
      return [];
    }
  };

  /* ---------------------------- threeWaves.ts, fiveWaves.ts, eightWaves.ts, anyWaves.ts */

  function waves(name, totalStep) {
    return {
      name: name,
      totalStep: totalStep,
      needDefaultPointFigure: true,
      needDefaultXAxisFigure: true,
      needDefaultYAxisFigure: true,
      createPointFigures: function (params) {
        var coordinates = params.coordinates;
        return [
          { type: 'line', attrs: { coordinates: coordinates } },
          { type: 'text', ignoreEvent: true, attrs: labels(coordinates, null) }
        ];
      }
    };
  }

  var threeWaves = waves('threeWaves', 5);
  var fiveWaves = waves('fiveWaves', 7);
  var eightWaves = waves('eightWaves', 10);
  var anyWaves = waves('anyWaves', Number.MAX_SAFE_INTEGER);

  /* ------------------------------------------------------------- abcd.ts */

  var abcd = {
    name: 'abcd',
    totalStep: 5,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      var acLineCoordinates = [];
      var bdLineCoordinates = [];
      var texts = labels(coordinates, ['A', 'B', 'C', 'D']);
      if (coordinates.length > 2) {
        acLineCoordinates = [coordinates[0], coordinates[2]];
        if (coordinates.length > 3) {
          bdLineCoordinates = [coordinates[1], coordinates[3]];
        }
      }
      return [
        { type: 'line', attrs: { coordinates: coordinates } },
        {
          type: 'line',
          attrs: [{ coordinates: acLineCoordinates }, { coordinates: bdLineCoordinates }],
          styles: { style: 'dashed' }
        },
        { type: 'text', ignoreEvent: true, attrs: texts }
      ];
    }
  };

  /* ------------------------------------------------------------ xabcd.ts */

  var xabcd = {
    name: 'xabcd',
    totalStep: 6,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (params) {
      var coordinates = params.coordinates;
      var dashedLines = [];
      var polygons = [];
      var texts = labels(coordinates, ['X', 'A', 'B', 'C', 'D']);
      if (coordinates.length > 2) {
        dashedLines.push({ coordinates: [coordinates[0], coordinates[2]] });
        polygons.push({ coordinates: [coordinates[0], coordinates[1], coordinates[2]] });
        if (coordinates.length > 3) {
          dashedLines.push({ coordinates: [coordinates[1], coordinates[3]] });
          if (coordinates.length > 4) {
            dashedLines.push({ coordinates: [coordinates[2], coordinates[4]] });
            polygons.push({ coordinates: [coordinates[2], coordinates[3], coordinates[4]] });
          }
        }
      }
      return [
        { type: 'line', attrs: { coordinates: coordinates } },
        { type: 'line', attrs: dashedLines, styles: { style: 'dashed' } },
        { type: 'polygon', ignoreEvent: true, attrs: polygons },
        { type: 'text', ignoreEvent: true, attrs: texts }
      ];
    }
  };

  /* ------------------------------------------------------------ index.ts */

  [
    arrow,
    circle, rect, triangle, parallelogram,
    fibonacciCircle, fibonacciSegment, fibonacciSpiral,
    fibonacciSpeedResistanceFan, fibonacciExtension, gannBox,
    threeWaves, fiveWaves, eightWaves, anyWaves, abcd, xabcd
  ].forEach(function (template) {
    K.registerOverlay(template);
  });
})(window);
