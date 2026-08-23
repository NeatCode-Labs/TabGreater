/**
     * @license
     * KLineChart v10.0.2
     * Copyright (c) 2019 lihu.
     * Licensed under Apache License 2.0 https://www.apache.org/licenses/LICENSE-2.0
     */
(function (global, factory) {
typeof exports === 'object' && typeof module !== 'undefined' ? factory(exports) :
typeof define === 'function' && define.amd ? define(['exports'], factory) :
(global = typeof globalThis !== 'undefined' ? globalThis : global || self, factory(global.klinecharts = {}));
})(this, (function (exports) { 'use strict';

/******************************************************************************
Copyright (c) Microsoft Corporation.

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
***************************************************************************** */
/* global Reflect, Promise, SuppressedError, Symbol, Iterator */

var extendStatics = function(d, b) {
    extendStatics = Object.setPrototypeOf ||
        ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
        function (d, b) { for (var p in b) if (Object.prototype.hasOwnProperty.call(b, p)) d[p] = b[p]; };
    return extendStatics(d, b);
};

function __extends(d, b) {
    if (typeof b !== "function" && b !== null)
        throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
    extendStatics(d, b);
    function __() { this.constructor = d; }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
}

var __assign = function() {
    __assign = Object.assign || function __assign(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p)) t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};

function __rest(s, e) {
    var t = {};
    for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
        for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
            if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
                t[p[i]] = s[p[i]];
        }
    return t;
}

function __awaiter(thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
}

function __generator(thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
}

function __values(o) {
    var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
    if (m) return m.call(o);
    if (o && typeof o.length === "number") return {
        next: function () {
            if (o && i >= o.length) o = void 0;
            return { value: o && o[i++], done: !o };
        }
    };
    throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
}

function __read(o, n) {
    var m = typeof Symbol === "function" && o[Symbol.iterator];
    if (!m) return o;
    var i = m.call(o), r, ar = [], e;
    try {
        while ((n === void 0 || n-- > 0) && !(r = i.next()).done) ar.push(r.value);
    }
    catch (error) { e = { error: error }; }
    finally {
        try {
            if (r && !r.done && (m = i["return"])) m.call(i);
        }
        finally { if (e) throw e.error; }
    }
    return ar;
}

function __spreadArray(to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
}

typeof SuppressedError === "function" ? SuppressedError : function (error, suppressed, message) {
    var e = new Error(message);
    return e.name = "SuppressedError", e.error = error, e.suppressed = suppressed, e;
};

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
// biome-ignore lint/suspicious/noExplicitAny: This recursive utility accepts arbitrary object shapes.
function merge(target, source) {
    if (!isObject(target) && !isObject(source)) {
        return;
    }
    for (var key in source) {
        // biome-ignore lint/suspicious/noPrototypeBuiltins: Object.hasOwn is unavailable for the ES5 target.
        if (Object.prototype.hasOwnProperty.call(source, key)) {
            var targetProp = target[key];
            var sourceProp = source[key];
            if (isObject(sourceProp) && isObject(targetProp)) {
                merge(targetProp, sourceProp);
            }
            else {
                target[key] = clone(sourceProp);
            }
        }
    }
}
function clone(target) {
    if (!isObject(target)) {
        return target;
    }
    // biome-ignore lint/suspicious/noExplicitAny: The cloned container is populated dynamically.
    var copy = null;
    if (isArray(target)) {
        copy = [];
    }
    else {
        copy = {};
    }
    for (var key in target) {
        // biome-ignore lint/suspicious/noPrototypeBuiltins: Object.hasOwn is unavailable for the ES5 target.
        if (Object.prototype.hasOwnProperty.call(target, key)) {
            var v = target[key];
            if (isObject(v)) {
                copy[key] = clone(v);
            }
            else {
                copy[key] = v;
            }
        }
    }
    return copy;
}
function isArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]';
}
function isFunction(value) {
    return typeof value === 'function';
}
function isObject(value) {
    return typeof value === 'object' && isValid(value);
}
function isNumber(value) {
    return typeof value === 'number' && Number.isFinite(value);
}
function isValid(value) {
    return value !== null && value !== undefined;
}
function isBoolean(value) {
    return typeof value === 'boolean';
}
function isString(value) {
    return typeof value === 'string';
}

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
var DEFAULT_REQUEST_ID = -1;
function requestAnimationFrame(fn) {
    if (isFunction(window.requestAnimationFrame)) {
        return window.requestAnimationFrame(fn);
    }
    return window.setTimeout(fn, 20);
}
function cancelAnimationFrame(id) {
    if (isFunction(window.cancelAnimationFrame)) {
        window.cancelAnimationFrame(id);
    }
    else {
        window.clearTimeout(id);
    }
}

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
var Animation = /** @class */ (function () {
    function Animation(options) {
        this._options = { duration: 500, iterationCount: 1 };
        this._currentIterationCount = 0;
        this._running = false;
        this._requestAnimationId = DEFAULT_REQUEST_ID;
        this._time = 0;
        merge(this._options, options);
    }
    Animation.prototype._loop = function () {
        var _this = this;
        this._running = true;
        this._time = performance.now();
        var step = function (timestamp) {
            var _a;
            _this._requestAnimationId = DEFAULT_REQUEST_ID;
            if (_this._running) {
                var time = typeof timestamp === 'number' ? timestamp : performance.now();
                var diffTime = Math.max(0, time - _this._time);
                if (diffTime < _this._options.duration) {
                    (_a = _this._doFrameCallback) === null || _a === void 0 ? void 0 : _a.call(_this, diffTime);
                    if (_this._running && _this._requestAnimationId === DEFAULT_REQUEST_ID) {
                        _this._requestAnimationId = requestAnimationFrame(step);
                    }
                }
                else {
                    _this.stop();
                    _this._currentIterationCount++;
                    if (_this._currentIterationCount < _this._options.iterationCount) {
                        _this._loop();
                    }
                }
            }
        };
        this._requestAnimationId = requestAnimationFrame(step);
    };
    Animation.prototype.doFrame = function (callback) {
        this._doFrameCallback = callback;
        return this;
    };
    Animation.prototype.setDuration = function (duration) {
        this._options.duration = duration;
        return this;
    };
    Animation.prototype.setIterationCount = function (iterationCount) {
        this._options.iterationCount = iterationCount;
        return this;
    };
    Animation.prototype.start = function () {
        if (!this._running) {
            this._currentIterationCount = 0;
            this._loop();
        }
    };
    Animation.prototype.stop = function () {
        var _a;
        if (this._running) {
            this.cancel();
            (_a = this._doFrameCallback) === null || _a === void 0 ? void 0 : _a.call(this, this._options.duration);
        }
        this._running = false;
    };
    Animation.prototype.cancel = function () {
        if (this._requestAnimationId !== DEFAULT_REQUEST_ID) {
            cancelAnimationFrame(this._requestAnimationId);
            this._requestAnimationId = DEFAULT_REQUEST_ID;
        }
        this._running = false;
    };
    return Animation;
}());

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
function createDefaultBounding(bounding) {
    var defaultBounding = {
        width: 0,
        height: 0,
        left: 0,
        right: 0,
        top: 0,
        bottom: 0
    };
    if (isValid(bounding)) {
        merge(defaultBounding, bounding);
    }
    return defaultBounding;
}

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
var measureCtx = null;
/**
 * Get pixel ratio
 * @param canvas
 * @returns {number}
 */
function getPixelRatio(canvas) {
    var _a, _b;
    return (_b = (_a = canvas.ownerDocument.defaultView) === null || _a === void 0 ? void 0 : _a.devicePixelRatio) !== null && _b !== void 0 ? _b : 1;
}
function createFont(size, weight, family) {
    return "".concat(weight !== null && weight !== void 0 ? weight : 'normal', " ").concat(size !== null && size !== void 0 ? size : 12, "px ").concat(family !== null && family !== void 0 ? family : 'Helvetica Neue');
}
/**
 * Measure the width of text
 * @param text
 * @returns {number}
 */
function calcTextWidth(text, size, weight, family) {
    if (!isValid(measureCtx)) {
        var canvas = document.createElement('canvas');
        var pixelRatio = getPixelRatio(canvas);
        measureCtx = canvas.getContext('2d');
        measureCtx.scale(pixelRatio, pixelRatio);
    }
    measureCtx.font = createFont(size, weight, family);
    return Math.round(measureCtx.measureText(text).width);
}

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
/**
 * Create dom
 * @param tagName
 * @param styles
 * @return {*}
 */
function createDom(tagName, styles) {
    var _a;
    var dom = document.createElement(tagName);
    var s = styles !== null && styles !== void 0 ? styles : {};
    // biome-ignore lint/suspicious/useGuardForIn: CSSStyleDeclaration keys are copied intentionally.
    for (var key in s) {
        dom.style[key] = (_a = s[key]) !== null && _a !== void 0 ? _a : '';
    }
    return dom;
}

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
var baseId = 1;
var prevIdTimestamp = Date.now();
function createId(prefix) {
    var timestamp = Date.now();
    if (timestamp === prevIdTimestamp) {
        ++baseId;
    }
    else {
        baseId = 1;
    }
    prevIdTimestamp = timestamp;
    return "".concat(prefix !== null && prefix !== void 0 ? prefix : '').concat(timestamp, "_").concat(baseId);
}

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
// @ts-nocheck
function log(templateText, tagStyle, messageStyle, api, invalidParam, append) {
    {
        var apiStr = api !== '' ? "Call api `".concat(api, "`").concat(invalidParam !== '' || append !== '' ? ', ' : '.') : '';
        var invalidParamStr = invalidParam !== '' ? "invalid parameter `".concat(invalidParam, "`").concat(append !== '' ? ', ' : '.') : '';
        var appendStr = append !== '' ? append : '';
        console.log(templateText, tagStyle, messageStyle, apiStr, invalidParamStr, appendStr);
    }
}
function logWarn(api, invalidParam, append) {
    log('%c😑 klinecharts warning%c %s%s%s', 'padding:3px 4px;border-radius:2px;color:#ffffff;background-color:#FF9600', 'color:#FF9600', api, invalidParam, append !== null && append !== void 0 ? append : '');
}
function logError(api, invalidParam, append) {
    log('%c😟 klinecharts error%c %s%s%s', 'padding:3px 4px;border-radius:2px;color:#ffffff;background-color:#F92855;', 'color:#F92855;', api, invalidParam, append );
}
function logTag() {
    log('%c❤️ Welcome to klinecharts. Version is 10.0.2', 'border-radius:4px;border:dashed 1px #1677FF;line-height:70px;padding:0 20px;margin:16px 0;font-size:14px;color:#1677FF;', '', '', '', '');
}

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
/**
 * Binary search for the nearest result
 * @param dataList
 * @param valueKey
 * @param targetValue
 * @return {number}
 */
function binarySearchNearest(dataList, valueKey, targetValue) {
    var left = 0;
    var right = 0;
    for (right = dataList.length - 1; left !== right;) {
        var midIndex = Math.floor((right + left) / 2);
        var mid = right - left;
        var midValue = dataList[midIndex][valueKey];
        if (targetValue === dataList[left][valueKey]) {
            return left;
        }
        if (targetValue === dataList[right][valueKey]) {
            return right;
        }
        if (targetValue === midValue) {
            return midIndex;
        }
        if (targetValue > midValue) {
            left = midIndex;
        }
        else {
            right = midIndex;
        }
        if (mid <= 2) {
            break;
        }
    }
    return left;
}
/**
 * 优化数字
 * @param value
 * @return {number|number}
 */
function nice(value) {
    var exponent = Math.floor(log10(value));
    var exp10 = index10(exponent);
    var f = value / exp10; // 1 <= f < 10
    var nf = 0;
    if (f < 1.5) {
        nf = 1;
    }
    else if (f < 2.5) {
        nf = 2;
    }
    else if (f < 3.5) {
        nf = 3;
    }
    else if (f < 4.5) {
        nf = 4;
    }
    else if (f < 5.5) {
        nf = 5;
    }
    else if (f < 6.5) {
        nf = 6;
    }
    else {
        nf = 8;
    }
    value = nf * exp10;
    return +value.toFixed(Math.abs(exponent));
}
/**
 * Round
 * @param value
 * @param precision
 * @return {number}
 */
function round(value, precision) {
    precision = Math.max(0, precision !== null && precision !== void 0 ? precision : 0);
    var pow = Math.pow(10, precision);
    return Math.round(value * pow) / pow;
}
/**
 * Get precision
 * @param value
 * @return {number|number}
 */
function getPrecision(value) {
    var str = value.toString();
    var eIndex = str.indexOf('e');
    if (eIndex > 0) {
        var precision = +str.slice(eIndex + 1);
        return precision < 0 ? -precision : 0;
    }
    var dotIndex = str.indexOf('.');
    return dotIndex < 0 ? 0 : str.length - 1 - dotIndex;
}
function getMaxMin(dataList, maxKey, minKey) {
    var _a, _b;
    var maxMin = [Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER];
    var dataLength = dataList.length;
    var index = 0;
    while (index < dataLength) {
        var data = dataList[index];
        maxMin[0] = Math.max(((_a = data[maxKey]) !== null && _a !== void 0 ? _a : Number.MIN_SAFE_INTEGER), maxMin[0]);
        maxMin[1] = Math.min(((_b = data[minKey]) !== null && _b !== void 0 ? _b : Number.MAX_SAFE_INTEGER), maxMin[1]);
        ++index;
    }
    return maxMin;
}
/**
 * log10
 * @param value
 * @return {number}
 */
function log10(value) {
    if (value === 0) {
        return 0;
    }
    return Math.log10(value);
}
/**
 * index 10
 * @param value
 * @return {number}
 */
function index10(value) {
    return Math.pow(10, value);
}

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
var SymbolDefaultPrecisionConstants = {
    PRICE: 2,
    VOLUME: 0
};

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
var reEscapeChar = /\\(\\)?/g;
var rePropName = new RegExp('[^.[\\]]+' + '|' + '\\[(?:' + '([^"\'][^[]*)' + '|' + '(["\'])((?:(?!\\2)[^\\\\]|\\\\.)*?)\\2' + ')\\]' + '|' + '(?=(?:\\.|\\[\\])(?:\\.|\\[\\]|$))', 'g');
function formatValue(data, key, defaultValue) {
    // Fast path: simple single-segment key without path separators.
    // `rePropName` below splits on '.', '[', and ']', so a key containing any of those
    // is compound and must take the general path. A separator-free key parses to a
    // single-element `path` and resolves to `data[key]`, which an inline read matches exactly.
    // This avoids a regex match + closure + array allocation per call, which matters in hot paths
    // (e.g. `eachFigures` runs 4 `formatValue` calls per visible bar per indicator).
    if (key.length > 0 && !key.includes('.') && !key.includes('[') && !key.includes(']')) {
        if (isValid(data)) {
            var value = data[key];
            return isValid(value) ? value : (defaultValue !== null && defaultValue !== void 0 ? defaultValue : '--');
        }
        return defaultValue !== null && defaultValue !== void 0 ? defaultValue : '--';
    }
    if (isValid(data)) {
        var path_1 = [];
        key.replace(rePropName, function (subString) {
            var args = [];
            for (var _i = 1; _i < arguments.length; _i++) {
                args[_i - 1] = arguments[_i];
            }
            var k = subString;
            if (isValid(args[1])) {
                k = args[2].replace(reEscapeChar, '$1');
            }
            else if (isValid(args[0])) {
                k = args[0].trim();
            }
            path_1.push(k);
            return '';
        });
        var value = data;
        var index = 0;
        var length_1 = path_1.length;
        while (isValid(value) && index < length_1) {
            value = value === null || value === void 0 ? void 0 : value[path_1[index++]];
        }
        return isValid(value) ? value : (defaultValue !== null && defaultValue !== void 0 ? defaultValue : '--');
    }
    return defaultValue !== null && defaultValue !== void 0 ? defaultValue : '--';
}
function formatTimestampToDateTime(dateTimeFormat, timestamp) {
    var date = {};
    dateTimeFormat.formatToParts(new Date(timestamp)).forEach(function (_a) {
        var type = _a.type, value = _a.value;
        switch (type) {
            case 'year': {
                date.YYYY = value;
                break;
            }
            case 'month': {
                date.MM = value;
                break;
            }
            case 'day': {
                date.DD = value;
                break;
            }
            case 'hour': {
                date.HH = value === '24' ? '00' : value;
                break;
            }
            case 'minute': {
                date.mm = value;
                break;
            }
            case 'second': {
                date.ss = value;
                break;
            }
        }
    });
    return date;
}
function formatTimestampByTemplate(dateTimeFormat, timestamp, template) {
    var date = formatTimestampToDateTime(dateTimeFormat, timestamp);
    return template.replace(/YYYY|MM|DD|HH|mm|ss/g, function (key) { return date[key]; });
}
function formatPrecision(value, precision) {
    var v = +value;
    if (isNumber(v)) {
        return v.toFixed(precision !== null && precision !== void 0 ? precision : 2);
    }
    return "".concat(value);
}
function formatBigNumber(value) {
    var v = +value;
    if (isNumber(v)) {
        var sign = v < 0 ? '-' : '';
        var a = Math.abs(v);
        if (a >= 1000000000) {
            return "".concat(sign).concat(+(a / 1000000000).toFixed(3), "B");
        }
        if (a >= 1000000) {
            return "".concat(sign).concat(+(a / 1000000).toFixed(3), "M");
        }
        if (a >= 1000) {
            return "".concat(sign).concat(+(a / 1000).toFixed(3), "K");
        }
    }
    return "".concat(value);
}
function formatThousands(value, sign) {
    var vl = "".concat(value);
    if (sign.length === 0) {
        return vl;
    }
    if (vl.includes('.')) {
        var arr = vl.split('.');
        return "".concat(arr[0].replace(/(\d)(?=(\d{3})+$)/g, function ($1) { return "".concat($1).concat(sign); }), ".").concat(arr[1]);
    }
    return vl.replace(/(\d)(?=(\d{3})+$)/g, function ($1) { return "".concat($1).concat(sign); });
}
function formatFoldDecimal(value, threshold) {
    var vl = "".concat(value);
    var reg = new RegExp('\\.0{' + threshold + ',}[1-9][0-9]*$');
    if (reg.test(vl)) {
        var result = vl.split('.');
        var lastIndex = result.length - 1;
        var v = result[lastIndex];
        var match = /0*/.exec(v);
        if (isValid(match)) {
            var count = match[0].length;
            result[lastIndex] = v.replace(/0*/, "0{".concat(count, "}"));
            return result.join('.');
        }
    }
    return vl;
}
function formatTemplateString(template, params) {
    return template.replace(/\{(\w+)\}/g, function (_, key) {
        var value = params[key];
        if (isValid(value)) {
            return value;
        }
        return "{".concat(key, "}");
    });
}

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
var PaneIdConstants = {
    CANDLE: 'candle_pane',
    INDICATOR: 'indicator_pane_',
    X_AXIS: 'x_axis_pane'
};

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
var TICK_COUNT = 8;
function getDefaultAxisRange() {
    return {
        from: 0,
        to: 0,
        range: 0,
        realFrom: 0,
        realTo: 0,
        realRange: 0,
        displayFrom: 0,
        displayTo: 0,
        displayRange: 0
    };
}
var AxisImp = /** @class */ (function () {
    function AxisImp(parent) {
        this.scrollZoomEnabled = true;
        this._range = getDefaultAxisRange();
        this._prevRange = getDefaultAxisRange();
        this._ticks = [];
        this._autoCalcTickFlag = true;
        this._parent = parent;
    }
    AxisImp.prototype.getParent = function () {
        return this._parent;
    };
    AxisImp.prototype.buildTicks = function (force) {
        if (this._autoCalcTickFlag) {
            this._range = this.createRangeImp();
        }
        if (this._prevRange.from !== this._range.from || this._prevRange.to !== this._range.to || force) {
            this._prevRange = this._range;
            this._ticks = this.createTicksImp();
            return true;
        }
        return false;
    };
    AxisImp.prototype.getTicks = function () {
        return this._ticks;
    };
    AxisImp.prototype.setRange = function (range) {
        this._autoCalcTickFlag = false;
        this._range = range;
    };
    AxisImp.prototype.getRange = function () {
        return this._range;
    };
    AxisImp.prototype.setAutoCalcTickFlag = function (flag) {
        this._autoCalcTickFlag = flag;
    };
    AxisImp.prototype.getAutoCalcTickFlag = function () {
        return this._autoCalcTickFlag;
    };
    return AxisImp;
}());

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
var Y_AXIS_ID_PREFIX = 'yAxis_';
var YAxisImp = /** @class */ (function (_super) {
    __extends(YAxisImp, _super);
    function YAxisImp(parent, yAxis) {
        var _this = _super.call(this, parent) || this;
        _this.id = '';
        _this.paneId = '';
        _this.reverse = false;
        _this.inside = false;
        _this.position = 'right';
        _this.gap = {
            top: 0.2,
            bottom: 0.1
        };
        _this.createRange = function (params) { return params.defaultRange; };
        _this.minSpan = function (precision) { return index10(-precision); };
        _this.valueToRealValue = function (value) { return value; };
        _this.realValueToDisplayValue = function (value) { return value; };
        _this.displayValueToRealValue = function (value) { return value; };
        _this.realValueToValue = function (value) { return value; };
        _this.displayValueToText = function (value, precision) { return formatPrecision(value, precision); };
        var minSpan = yAxis.minSpan, valueToRealValue = yAxis.valueToRealValue, realValueToDisplayValue = yAxis.realValueToDisplayValue, displayValueToRealValue = yAxis.displayValueToRealValue, realValueToValue = yAxis.realValueToValue, displayValueToText = yAxis.displayValueToText, others = __rest(yAxis, ["minSpan", "valueToRealValue", "realValueToDisplayValue", "displayValueToRealValue", "realValueToValue", "displayValueToText"]);
        if (isFunction(minSpan)) {
            _this.minSpan = minSpan;
        }
        if (isFunction(valueToRealValue)) {
            _this.valueToRealValue = valueToRealValue;
        }
        if (isFunction(realValueToDisplayValue)) {
            _this.realValueToDisplayValue = realValueToDisplayValue;
        }
        if (isFunction(displayValueToRealValue)) {
            _this.displayValueToRealValue = displayValueToRealValue;
        }
        if (isFunction(realValueToValue)) {
            _this.realValueToValue = realValueToValue;
        }
        if (isFunction(displayValueToText)) {
            _this.displayValueToText = displayValueToText;
        }
        _this.override(others);
        return _this;
    }
    YAxisImp.prototype.override = function (yAxis) {
        var id = yAxis.id, name = yAxis.name, gap = yAxis.gap, others = __rest(yAxis, ["id", "name", "gap"]);
        if (isValid(id) && this.id.length === 0) {
            this.id = id;
        }
        if (!isString(this.name) && isString(name)) {
            this.name = name;
        }
        merge(this.gap, gap);
        merge(this, others);
    };
    YAxisImp.prototype._getIndicatorsByYAxisIds = function () {
        var parent = this.getParent();
        var ids = new Set([this.id]);
        if (parent.isManualYAxis(this.id)) {
            var defaultYAxisId = parent.getDefaultYAxisId();
            if (isValid(defaultYAxisId)) {
                ids.add(defaultYAxisId);
            }
        }
        return parent
            .getChart()
            .getChartStore()
            .getIndicatorsByPaneId(parent.getId())
            .filter(function (indicator) { return ids.has(indicator.yAxisId); });
    };
    YAxisImp.prototype._shouldUseCandleData = function () {
        var parent = this.getParent();
        return this.isInCandle() && (parent.isDefaultYAxis(this.id) || parent.isManualYAxis(this.id));
    };
    YAxisImp.prototype.createRangeImp = function () {
        var _a, _b;
        var parent = this.getParent();
        var chart = parent.getChart();
        var chartStore = chart.getChartStore();
        var paneId = parent.getId();
        var min = Number.MAX_SAFE_INTEGER;
        var max = Number.MIN_SAFE_INTEGER;
        var shouldOhlc = false;
        var specifyMin = Number.MAX_SAFE_INTEGER;
        var specifyMax = Number.MIN_SAFE_INTEGER;
        var indicatorPrecision = Number.MAX_SAFE_INTEGER;
        var indicators = this._getIndicatorsByYAxisIds();
        indicators.forEach(function (indicator) {
            shouldOhlc || (shouldOhlc = indicator.shouldOhlc);
            indicatorPrecision = Math.min(indicatorPrecision, indicator.precision);
            if (isNumber(indicator.minValue)) {
                specifyMin = Math.min(specifyMin, indicator.minValue);
            }
            if (isNumber(indicator.maxValue)) {
                specifyMax = Math.max(specifyMax, indicator.maxValue);
            }
        });
        var precision = 4;
        var inCandle = this.isInCandle();
        if (inCandle) {
            var pricePrecision = (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE;
            if (indicatorPrecision !== Number.MAX_SAFE_INTEGER) {
                precision = Math.min(indicatorPrecision, pricePrecision);
            }
            else {
                precision = pricePrecision;
            }
        }
        else {
            if (indicatorPrecision !== Number.MAX_SAFE_INTEGER) {
                precision = indicatorPrecision;
            }
        }
        var visibleRangeDataList = chartStore.getVisibleRangeDataList();
        var candleStyles = chart.getStyles().candle;
        var isArea = candleStyles.type === 'area';
        var areaValueKey = candleStyles.area.value;
        var shouldUseCandleData = this._shouldUseCandleData();
        var shouldCompareHighLow = (shouldUseCandleData && !isArea) || (!inCandle && shouldOhlc);
        visibleRangeDataList.forEach(function (visibleData) {
            var dataIndex = visibleData.dataIndex;
            var data = visibleData.data.current;
            if (isValid(data)) {
                if (shouldCompareHighLow) {
                    min = Math.min(min, data.low);
                    max = Math.max(max, data.high);
                }
                if (shouldUseCandleData && isArea) {
                    var value = data[areaValueKey];
                    if (isNumber(value)) {
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                    }
                }
            }
            indicators.forEach(function (_a) {
                var _b;
                var result = _a.result, figures = _a.figures;
                var data = (_b = result[dataIndex]) !== null && _b !== void 0 ? _b : {};
                figures.forEach(function (figure) {
                    var value = data[figure.key];
                    if (isNumber(value)) {
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                    }
                });
            });
        });
        if (min !== Number.MAX_SAFE_INTEGER && max !== Number.MIN_SAFE_INTEGER) {
            min = Math.min(specifyMin, min);
            max = Math.max(specifyMax, max);
        }
        else {
            min = 0;
            max = 10;
        }
        var defaultDiff = max - min;
        var defaultRange = {
            from: min,
            to: max,
            range: defaultDiff,
            realFrom: min,
            realTo: max,
            realRange: defaultDiff,
            displayFrom: min,
            displayTo: max,
            displayRange: defaultDiff
        };
        var range = this.createRange({
            chart: chart,
            paneId: paneId,
            defaultRange: defaultRange
        });
        var realFrom = range.realFrom;
        var realTo = range.realTo;
        var realRange = range.realRange;
        var minSpan = this.minSpan(precision);
        if (realFrom === realTo || realRange < minSpan) {
            var minCheck = specifyMin === realFrom;
            var maxCheck = specifyMax === realTo;
            var halfTickCount = TICK_COUNT / 2;
            realFrom = minCheck ? realFrom : maxCheck ? realFrom - TICK_COUNT * minSpan : realFrom - halfTickCount * minSpan;
            realTo = maxCheck ? realTo : minCheck ? realTo + TICK_COUNT * minSpan : realTo + halfTickCount * minSpan;
        }
        var height = this.getBounding().height;
        var _c = this.gap, top = _c.top, bottom = _c.bottom;
        var topRate = top;
        if (topRate >= 1) {
            topRate = topRate / height;
        }
        var bottomRate = bottom;
        if (bottomRate >= 1) {
            bottomRate = bottomRate / height;
        }
        realRange = realTo - realFrom;
        realFrom = realFrom - realRange * bottomRate;
        realTo = realTo + realRange * topRate;
        var from = this.realValueToValue(realFrom, { range: range });
        var to = this.realValueToValue(realTo, { range: range });
        var displayFrom = this.realValueToDisplayValue(realFrom, { range: range });
        var displayTo = this.realValueToDisplayValue(realTo, { range: range });
        return {
            from: from,
            to: to,
            range: to - from,
            realFrom: realFrom,
            realTo: realTo,
            realRange: realTo - realFrom,
            displayFrom: displayFrom,
            displayTo: displayTo,
            displayRange: displayTo - displayFrom
        };
    };
    /**
     * 是否是蜡烛图轴
     * @return {boolean}
     */
    YAxisImp.prototype.isInCandle = function () {
        return this.getParent().getId() === PaneIdConstants.CANDLE;
    };
    /**
     * 是否从y轴0开始
     * @return {boolean}
     */
    YAxisImp.prototype.isFromZero = function () {
        return (this.position === 'left' && this.inside) || (this.position === 'right' && !this.inside);
    };
    YAxisImp.prototype.createTicksImp = function () {
        var _this = this;
        var _a, _b;
        var range = this.getRange();
        var displayFrom = range.displayFrom, displayTo = range.displayTo, displayRange = range.displayRange;
        var ticks = [];
        if (displayRange >= 0) {
            var interval = nice(displayRange / TICK_COUNT);
            var precision_1 = getPrecision(interval);
            var first = round(Math.ceil(displayFrom / interval) * interval, precision_1);
            var last = round(Math.floor(displayTo / interval) * interval, precision_1);
            var n = 0;
            var f = first;
            if (interval !== 0) {
                while (f <= last) {
                    var v = f.toFixed(precision_1);
                    ticks[n] = { text: v, coord: 0, value: v };
                    ++n;
                    f += interval;
                }
            }
        }
        var pane = this.getParent();
        var height = this.getBounding().height;
        var chartStore = pane.getChart().getChartStore();
        var optimalTicks = [];
        var indicators = this._getIndicatorsByYAxisIds();
        var styles = chartStore.getStyles();
        var precision = 0;
        var shouldFormatBigNumber = false;
        if (this._shouldUseCandleData()) {
            precision = (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE;
        }
        else {
            indicators.forEach(function (indicator) {
                precision = Math.max(precision, indicator.precision);
                shouldFormatBigNumber || (shouldFormatBigNumber = indicator.shouldFormatBigNumber);
            });
        }
        var formatter = chartStore.getInnerFormatter();
        var thousandsSeparator = chartStore.getThousandsSeparator();
        var decimalFold = chartStore.getDecimalFold();
        var textHeight = styles.yAxis.tickText.size;
        var validY = NaN;
        ticks.forEach(function (_a) {
            var value = _a.value;
            var v = _this.displayValueToText(+value, precision);
            var y = _this.convertToPixel(_this.realValueToValue(_this.displayValueToRealValue(+value, { range: range }), { range: range }));
            if (shouldFormatBigNumber) {
                v = formatter.formatBigNumber(value);
            }
            v = decimalFold.format(thousandsSeparator.format(v));
            var validYNumber = isNumber(validY);
            if (y > textHeight && y < height - textHeight && ((validYNumber && Math.abs(validY - y) > textHeight * 2) || !validYNumber)) {
                optimalTicks.push({ text: v, coord: y, value: value });
                validY = y;
            }
        });
        if (isFunction(this.createTicks)) {
            return this.createTicks({
                range: this.getRange(),
                bounding: this.getBounding(),
                defaultTicks: optimalTicks
            });
        }
        return optimalTicks;
    };
    YAxisImp.prototype.getAutoSize = function () {
        var _a, _b;
        var pane = this.getParent();
        var chart = pane.getChart();
        var chartStore = chart.getChartStore();
        var styles = chartStore.getStyles();
        var yAxisStyles = styles.yAxis;
        var width = yAxisStyles.size;
        if (width !== 'auto') {
            return width;
        }
        var yAxisWidth = 0;
        if (yAxisStyles.show) {
            if (yAxisStyles.axisLine.show) {
                yAxisWidth += yAxisStyles.axisLine.size;
            }
            if (yAxisStyles.tickLine.show) {
                yAxisWidth += yAxisStyles.tickLine.length;
            }
            if (yAxisStyles.tickText.show) {
                var textWidth_1 = 0;
                this.getTicks().forEach(function (tick) {
                    textWidth_1 = Math.max(textWidth_1, calcTextWidth(tick.text, yAxisStyles.tickText.size, yAxisStyles.tickText.weight, yAxisStyles.tickText.family));
                });
                yAxisWidth += yAxisStyles.tickText.marginStart + yAxisStyles.tickText.marginEnd + textWidth_1;
            }
        }
        var priceMarkStyles = styles.candle.priceMark;
        var lastPriceMarkTextVisible = priceMarkStyles.show && priceMarkStyles.last.show && priceMarkStyles.last.text.show;
        var lastPriceTextWidth = 0;
        var crosshairStyles = styles.crosshair;
        var crosshairHorizontalTextVisible = crosshairStyles.show && crosshairStyles.horizontal.show && crosshairStyles.horizontal.text.show;
        var crosshairHorizontalTextWidth = 0;
        if (lastPriceMarkTextVisible || crosshairHorizontalTextVisible) {
            var pricePrecision = (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE;
            var max = this.getRange().displayTo;
            if (lastPriceMarkTextVisible) {
                var dataList = chartStore.getDataList();
                var data_1 = dataList[dataList.length - 1];
                if (isValid(data_1)) {
                    var _c = priceMarkStyles.last.text, paddingLeft = _c.paddingLeft, paddingRight = _c.paddingRight, size = _c.size, family = _c.family, weight = _c.weight;
                    lastPriceTextWidth = paddingLeft + calcTextWidth(formatPrecision(data_1.close, pricePrecision), size, weight, family) + paddingRight;
                    var formatExtendText_1 = chartStore.getInnerFormatter().formatExtendText;
                    priceMarkStyles.last.extendTexts.forEach(function (item, index) {
                        var text = formatExtendText_1({ type: 'last_price', data: data_1, index: index });
                        if (text.length > 0 && item.show) {
                            lastPriceTextWidth = Math.max(lastPriceTextWidth, item.paddingLeft + calcTextWidth(text, item.size, item.weight, item.family) + item.paddingRight);
                        }
                    });
                }
            }
            if (crosshairHorizontalTextVisible) {
                var indicators = this._getIndicatorsByYAxisIds();
                var indicatorPrecision_1 = 0;
                var shouldFormatBigNumber_1 = false;
                indicators.forEach(function (indicator) {
                    indicatorPrecision_1 = Math.max(indicator.precision, indicatorPrecision_1);
                    shouldFormatBigNumber_1 || (shouldFormatBigNumber_1 = indicator.shouldFormatBigNumber);
                });
                var precision = 2;
                if (this._shouldUseCandleData()) {
                    var lastValueMarkStyles = styles.indicator.lastValueMark;
                    if (lastValueMarkStyles.show && lastValueMarkStyles.text.show) {
                        precision = Math.max(indicatorPrecision_1, pricePrecision);
                    }
                    else {
                        precision = pricePrecision;
                    }
                }
                else {
                    precision = indicatorPrecision_1;
                }
                var valueText = formatPrecision(max, precision);
                if (shouldFormatBigNumber_1) {
                    valueText = chartStore.getInnerFormatter().formatBigNumber(valueText);
                }
                valueText = chartStore.getDecimalFold().format(valueText);
                crosshairHorizontalTextWidth +=
                    crosshairStyles.horizontal.text.paddingLeft + crosshairStyles.horizontal.text.paddingRight + crosshairStyles.horizontal.text.borderSize * 2 + calcTextWidth(valueText, crosshairStyles.horizontal.text.size, crosshairStyles.horizontal.text.weight, crosshairStyles.horizontal.text.family);
            }
        }
        return Math.max(yAxisWidth, lastPriceTextWidth, crosshairHorizontalTextWidth);
    };
    YAxisImp.prototype.getBounding = function () {
        var _a, _b;
        return (_b = (_a = this.getParent().getYAxisWidgetById(this.id)) === null || _a === void 0 ? void 0 : _a.getBounding()) !== null && _b !== void 0 ? _b : this.getParent().getMainWidget().getBounding();
    };
    YAxisImp.prototype.convertFromPixel = function (pixel) {
        var height = this.getBounding().height;
        var range = this.getRange();
        var realFrom = range.realFrom, realRange = range.realRange;
        var rate = this.reverse ? pixel / height : 1 - pixel / height;
        var realValue = rate * realRange + realFrom;
        return this.realValueToValue(realValue, { range: range });
    };
    YAxisImp.prototype.convertToPixel = function (value) {
        var range = this.getRange();
        var realValue = this.valueToRealValue(value, { range: range });
        var height = this.getBounding().height;
        var realFrom = range.realFrom, realRange = range.realRange;
        var rate = (realValue - realFrom) / realRange;
        return this.reverse ? Math.round(rate * height) : Math.round((1 - rate) * height);
    };
    YAxisImp.prototype.convertToNicePixel = function (value) {
        var height = this.getBounding().height;
        var pixel = this.convertToPixel(value);
        return Math.round(Math.max(height * 0.05, Math.min(pixel, height * 0.98)));
    };
    YAxisImp.extend = function (template) {
        var Custom = /** @class */ (function (_super) {
            __extends(Custom, _super);
            function Custom(parent) {
                return _super.call(this, parent, template) || this;
            }
            return Custom;
        }(YAxisImp));
        return Custom;
    };
    return YAxisImp;
}(AxisImp));

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
function isFF() {
    if (typeof window === 'undefined') {
        return false;
    }
    return window.navigator.userAgent.toLowerCase().includes('firefox');
}
function isIOS() {
    if (typeof window === 'undefined') {
        return false;
    }
    return /iPhone|iPad|iPod|iOS/.test(window.navigator.userAgent);
}
function isAppleOS() {
    return /Mac|iPhone|iPad|iPod|iOS/.test(window.navigator.userAgent);
}

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
// we can use `const name = 500;` but with `const enum` this values will be inlined into code
// so we do not need to have it as variables
var Delay = {
    ResetClick: 500,
    LongTap: 500,
    PreventFiresTouchEvents: 500
};
var ManhattanDistance = {
    CancelClick: 5,
    CancelTap: 5,
    DoubleClick: 5,
    DoubleTap: 30
};
var MouseEventButton = {
    Left: 0,
    Middle: 1,
    Right: 2
};
var TOUCH_MIN_RADIUS = 10;
// TODO: get rid of a lot of boolean flags, probably we should replace it with some enum
var EventHandlerImp = /** @class */ (function () {
    function EventHandlerImp(target, handler, options) {
        var _this = this;
        this._clickCount = 0;
        this._clickTimeoutId = null;
        this._clickCoordinate = { x: Number.NEGATIVE_INFINITY, y: Number.POSITIVE_INFINITY };
        this._tapCount = 0;
        this._tapTimeoutId = null;
        this._tapCoordinate = { x: Number.NEGATIVE_INFINITY, y: Number.POSITIVE_INFINITY };
        this._longTapTimeoutId = null;
        this._longTapActive = false;
        this._mouseMoveStartCoordinate = null;
        this._touchMoveStartCoordinate = null;
        this._touchMoveExceededManhattanDistance = false;
        this._cancelClick = false;
        this._cancelTap = false;
        this._unsubscribeOutsideMouseEvents = null;
        this._unsubscribeOutsideTouchEvents = null;
        this._unsubscribeMobileSafariEvents = null;
        this._unsubscribeMousemove = null;
        this._unsubscribeMouseWheel = null;
        this._unsubscribeContextMenu = null;
        this._unsubscribeRootMouseEvents = null;
        this._unsubscribeRootTouchEvents = null;
        this._startPinchMiddleCoordinate = null;
        this._startPinchDistance = 0;
        this._pinchPrevented = false;
        this._preventTouchDragProcess = false;
        this._mousePressed = false;
        this._lastTouchEventTimeStamp = 0;
        // for touchstart/touchmove/touchend events we handle only first touch
        // i.e. we don't support several active touches at the same time (except pinch event)
        this._activeTouchId = null;
        // accept all mouse leave events if it's not an iOS device
        // see _mouseEnterHandler, _mouseMoveHandler, _mouseLeaveHandler
        this._acceptMouseLeave = !isIOS();
        /**
         * In Firefox mouse events dont't fire if the mouse position is outside of the browser's border.
         * To prevent the mouse from hanging while pressed we're subscribing on the mouseleave event of the document element.
         * We're subscribing on mouseleave, but this event is actually fired on mouseup outside of the browser's border.
         */
        this._onFirefoxOutsideMouseUp = function (mouseUpEvent) {
            _this._mouseUpHandler(mouseUpEvent);
        };
        /**
         * Safari doesn't fire touchstart/mousedown events on double tap since iOS 13.
         * There are two possible solutions:
         * 1) Call preventDefault in touchEnd handler. But it also prevents click event from firing.
         * 2) Add listener on dblclick event that fires with the preceding mousedown/mouseup.
         * https://developer.apple.com/forums/thread/125073
         */
        this._onMobileSafariDoubleClick = function (dblClickEvent) {
            if (_this._firesTouchEvents(dblClickEvent)) {
                ++_this._tapCount;
                if (_this._tapTimeoutId !== null && _this._tapCount > 1) {
                    var manhattanDistance = _this._mouseTouchMoveWithDownInfo(_this._getCoordinate(dblClickEvent), _this._tapCoordinate).manhattanDistance;
                    if (manhattanDistance < ManhattanDistance.DoubleTap && !_this._cancelTap) {
                        _this._processEvent(_this._makeCompatEvent(dblClickEvent), _this._handler.doubleTapEvent);
                    }
                    _this._resetTapTimeout();
                }
            }
            else {
                ++_this._clickCount;
                if (_this._clickTimeoutId !== null && _this._clickCount > 1) {
                    var manhattanDistance = _this._mouseTouchMoveWithDownInfo(_this._getCoordinate(dblClickEvent), _this._clickCoordinate).manhattanDistance;
                    if (manhattanDistance < ManhattanDistance.DoubleClick && !_this._cancelClick) {
                        _this._processEvent(_this._makeCompatEvent(dblClickEvent), _this._handler.mouseDoubleClickEvent);
                    }
                    _this._resetClickTimeout();
                }
            }
        };
        this._target = target;
        this._handler = handler;
        this._options = options;
        this._init();
    }
    EventHandlerImp.prototype.destroy = function () {
        if (this._unsubscribeOutsideMouseEvents !== null) {
            this._unsubscribeOutsideMouseEvents();
            this._unsubscribeOutsideMouseEvents = null;
        }
        if (this._unsubscribeOutsideTouchEvents !== null) {
            this._unsubscribeOutsideTouchEvents();
            this._unsubscribeOutsideTouchEvents = null;
        }
        if (this._unsubscribeMousemove !== null) {
            this._unsubscribeMousemove();
            this._unsubscribeMousemove = null;
        }
        if (this._unsubscribeMouseWheel !== null) {
            this._unsubscribeMouseWheel();
            this._unsubscribeMouseWheel = null;
        }
        if (this._unsubscribeContextMenu !== null) {
            this._unsubscribeContextMenu();
            this._unsubscribeContextMenu = null;
        }
        if (this._unsubscribeRootMouseEvents !== null) {
            this._unsubscribeRootMouseEvents();
            this._unsubscribeRootMouseEvents = null;
        }
        if (this._unsubscribeRootTouchEvents !== null) {
            this._unsubscribeRootTouchEvents();
            this._unsubscribeRootTouchEvents = null;
        }
        if (this._unsubscribeMobileSafariEvents !== null) {
            this._unsubscribeMobileSafariEvents();
            this._unsubscribeMobileSafariEvents = null;
        }
        this._clearLongTapTimeout();
        this._resetClickTimeout();
    };
    EventHandlerImp.prototype._mouseEnterHandler = function (enterEvent) {
        var _this = this;
        var _a, _b, _c;
        (_a = this._unsubscribeMousemove) === null || _a === void 0 ? void 0 : _a.call(this);
        (_b = this._unsubscribeMouseWheel) === null || _b === void 0 ? void 0 : _b.call(this);
        (_c = this._unsubscribeContextMenu) === null || _c === void 0 ? void 0 : _c.call(this);
        var boundMouseMoveHandler = this._mouseMoveHandler.bind(this);
        this._unsubscribeMousemove = function () {
            _this._target.removeEventListener('mousemove', boundMouseMoveHandler);
        };
        this._target.addEventListener('mousemove', boundMouseMoveHandler);
        var boundMouseWheel = this._mouseWheelHandler.bind(this);
        this._unsubscribeMouseWheel = function () {
            _this._target.removeEventListener('wheel', boundMouseWheel);
        };
        this._target.addEventListener('wheel', boundMouseWheel, { passive: false });
        var boundContextMenu = this._contextMenuHandler.bind(this);
        this._unsubscribeContextMenu = function () {
            _this._target.removeEventListener('contextmenu', boundContextMenu);
        };
        this._target.addEventListener('contextmenu', boundContextMenu, { passive: false });
        if (this._firesTouchEvents(enterEvent)) {
            return;
        }
        this._processEvent(this._makeCompatEvent(enterEvent), this._handler.mouseEnterEvent);
        this._acceptMouseLeave = true;
    };
    EventHandlerImp.prototype._resetClickTimeout = function () {
        if (this._clickTimeoutId !== null) {
            clearTimeout(this._clickTimeoutId);
        }
        this._clickCount = 0;
        this._clickTimeoutId = null;
        this._clickCoordinate = { x: Number.NEGATIVE_INFINITY, y: Number.POSITIVE_INFINITY };
    };
    EventHandlerImp.prototype._resetTapTimeout = function () {
        if (this._tapTimeoutId !== null) {
            clearTimeout(this._tapTimeoutId);
        }
        this._tapCount = 0;
        this._tapTimeoutId = null;
        this._tapCoordinate = { x: Number.NEGATIVE_INFINITY, y: Number.POSITIVE_INFINITY };
    };
    EventHandlerImp.prototype._mouseMoveHandler = function (moveEvent) {
        if (this._mousePressed || this._touchMoveStartCoordinate !== null) {
            return;
        }
        if (this._firesTouchEvents(moveEvent)) {
            return;
        }
        this._processEvent(this._makeCompatEvent(moveEvent), this._handler.mouseMoveEvent);
        this._acceptMouseLeave = true;
    };
    EventHandlerImp.prototype._mouseWheelHandler = function (wheelEvent) {
        if (Math.abs(wheelEvent.deltaX) > Math.abs(wheelEvent.deltaY)) {
            if (!isValid(this._handler.mouseWheelHortEvent)) {
                return;
            }
            this._preventDefault(wheelEvent);
            if (Math.abs(wheelEvent.deltaX) === 0) {
                return;
            }
            this._handler.mouseWheelHortEvent(this._makeCompatEvent(wheelEvent), -wheelEvent.deltaX);
        }
        else {
            if (!isValid(this._handler.mouseWheelVertEvent)) {
                return;
            }
            var deltaY = -(wheelEvent.deltaY / 100);
            if (deltaY === 0) {
                return;
            }
            this._preventDefault(wheelEvent);
            switch (wheelEvent.deltaMode) {
                case wheelEvent.DOM_DELTA_PAGE: {
                    deltaY *= 120;
                    break;
                }
                case wheelEvent.DOM_DELTA_LINE: {
                    deltaY *= 32;
                    break;
                }
            }
            if (deltaY !== 0) {
                var scale = Math.sign(deltaY) * Math.min(1, Math.abs(deltaY));
                this._handler.mouseWheelVertEvent(this._makeCompatEvent(wheelEvent), scale);
            }
        }
    };
    EventHandlerImp.prototype._contextMenuHandler = function (mouseEvent) {
        this._preventDefault(mouseEvent);
    };
    EventHandlerImp.prototype._touchMoveHandler = function (moveEvent) {
        var touch = this._touchWithId(moveEvent.changedTouches, this._activeTouchId);
        if (touch === null) {
            return;
        }
        this._lastTouchEventTimeStamp = this._eventTimeStamp(moveEvent);
        if (this._startPinchMiddleCoordinate !== null) {
            return;
        }
        if (this._preventTouchDragProcess) {
            return;
        }
        // prevent pinch if move event comes faster than the second touch
        this._pinchPrevented = true;
        var moveInfo = this._mouseTouchMoveWithDownInfo(this._getCoordinate(touch), this._touchMoveStartCoordinate);
        var xOffset = moveInfo.xOffset, yOffset = moveInfo.yOffset, manhattanDistance = moveInfo.manhattanDistance;
        if (!this._touchMoveExceededManhattanDistance && manhattanDistance < ManhattanDistance.CancelTap) {
            return;
        }
        if (!this._touchMoveExceededManhattanDistance) {
            // first time when current position exceeded manhattan distance
            // vertical drag is more important than horizontal drag
            // because we scroll the page vertically often than horizontally
            var correctedXOffset = xOffset * 0.5;
            // a drag can be only if touch page scroll isn't allowed
            var isVertDrag = yOffset >= correctedXOffset && !this._options.treatVertDragAsPageScroll();
            var isHorzDrag = correctedXOffset > yOffset && !this._options.treatHorzDragAsPageScroll();
            // if drag event happened then we should revert preventDefault state to original one
            // and try to process the drag event
            // else we shouldn't prevent default of the event and ignore processing the drag event
            if (!isVertDrag && !isHorzDrag) {
                this._preventTouchDragProcess = true;
            }
            this._touchMoveExceededManhattanDistance = true;
            // if manhattan distance is more that 5 - we should cancel tap event
            this._cancelTap = true;
            this._clearLongTapTimeout();
            this._resetTapTimeout();
        }
        if (!this._preventTouchDragProcess) {
            this._processEvent(this._makeCompatEvent(moveEvent, touch), this._handler.touchMoveEvent);
            // Prevent the browser from handling a drag that has been claimed by the chart.
            // This avoids page scrolling and navigation gestures while panning the chart.
            this._preventDefault(moveEvent);
        }
    };
    EventHandlerImp.prototype._mouseMoveWithDownHandler = function (moveEvent) {
        if (moveEvent.button !== MouseEventButton.Left) {
            return;
        }
        var moveInfo = this._mouseTouchMoveWithDownInfo(this._getCoordinate(moveEvent), this._mouseMoveStartCoordinate);
        var manhattanDistance = moveInfo.manhattanDistance;
        if (manhattanDistance >= ManhattanDistance.CancelClick) {
            // if manhattan distance is more that 5 - we should cancel click event
            this._cancelClick = true;
            this._resetClickTimeout();
        }
        if (this._cancelClick) {
            // if this._cancelClick is true, that means that minimum manhattan distance is already exceeded
            this._processEvent(this._makeCompatEvent(moveEvent), this._handler.pressedMouseMoveEvent);
        }
    };
    EventHandlerImp.prototype._mouseTouchMoveWithDownInfo = function (currentCoordinate, startCoordinate) {
        var xOffset = Math.abs(startCoordinate.x - currentCoordinate.x);
        var yOffset = Math.abs(startCoordinate.y - currentCoordinate.y);
        var manhattanDistance = xOffset + yOffset;
        return { xOffset: xOffset, yOffset: yOffset, manhattanDistance: manhattanDistance };
    };
    EventHandlerImp.prototype._touchEndHandler = function (touchEndEvent) {
        var touch = this._touchWithId(touchEndEvent.changedTouches, this._activeTouchId);
        if (touch === null && touchEndEvent.touches.length === 0) {
            // something went wrong, somehow we missed the required touchend event
            // probably the browser has not sent this event
            touch = touchEndEvent.changedTouches[0];
        }
        if (touch === null) {
            return;
        }
        this._activeTouchId = null;
        this._lastTouchEventTimeStamp = this._eventTimeStamp(touchEndEvent);
        this._clearLongTapTimeout();
        this._touchMoveStartCoordinate = null;
        if (this._unsubscribeRootTouchEvents !== null) {
            this._unsubscribeRootTouchEvents();
            this._unsubscribeRootTouchEvents = null;
        }
        var compatEvent = this._makeCompatEvent(touchEndEvent, touch);
        this._processEvent(compatEvent, this._handler.touchEndEvent);
        ++this._tapCount;
        if (this._tapTimeoutId !== null && this._tapCount > 1) {
            // check that both clicks are near enough
            var manhattanDistance = this._mouseTouchMoveWithDownInfo(this._getCoordinate(touch), this._tapCoordinate).manhattanDistance;
            if (manhattanDistance < ManhattanDistance.DoubleTap && !this._cancelTap) {
                this._processEvent(compatEvent, this._handler.doubleTapEvent);
            }
            this._resetTapTimeout();
        }
        else {
            if (!this._cancelTap) {
                this._processEvent(compatEvent, this._handler.tapEvent);
                // do not fire mouse events if tap handler was executed
                // prevent click event on new dom element (who appeared after tap)
                if (isValid(this._handler.tapEvent)) {
                    this._preventDefault(touchEndEvent);
                }
            }
        }
        // prevent, for example, safari's dblclick-to-zoom or fast-click after long-tap
        // we handle mouseDoubleClickEvent here ourselves
        if (this._tapCount === 0) {
            this._preventDefault(touchEndEvent);
        }
        if (touchEndEvent.touches.length === 0) {
            if (this._longTapActive) {
                this._longTapActive = false;
                // prevent native click event
                this._preventDefault(touchEndEvent);
            }
        }
    };
    EventHandlerImp.prototype._mouseUpHandler = function (mouseUpEvent) {
        if (mouseUpEvent.button !== MouseEventButton.Left) {
            return;
        }
        var compatEvent = this._makeCompatEvent(mouseUpEvent);
        this._mouseMoveStartCoordinate = null;
        this._mousePressed = false;
        if (this._unsubscribeRootMouseEvents !== null) {
            this._unsubscribeRootMouseEvents();
            this._unsubscribeRootMouseEvents = null;
        }
        if (isFF()) {
            var rootElement = this._target.ownerDocument.documentElement;
            rootElement.removeEventListener('mouseleave', this._onFirefoxOutsideMouseUp);
        }
        if (this._firesTouchEvents(mouseUpEvent)) {
            return;
        }
        this._processEvent(compatEvent, this._handler.mouseUpEvent);
        ++this._clickCount;
        if (this._clickTimeoutId !== null && this._clickCount > 1) {
            // check that both clicks are near enough
            var manhattanDistance = this._mouseTouchMoveWithDownInfo(this._getCoordinate(mouseUpEvent), this._clickCoordinate).manhattanDistance;
            if (manhattanDistance < ManhattanDistance.DoubleClick && !this._cancelClick) {
                this._processEvent(compatEvent, this._handler.mouseDoubleClickEvent);
            }
            this._resetClickTimeout();
        }
        else {
            if (!this._cancelClick) {
                this._processEvent(compatEvent, this._handler.mouseClickEvent);
            }
        }
    };
    EventHandlerImp.prototype._clearLongTapTimeout = function () {
        if (this._longTapTimeoutId === null) {
            return;
        }
        clearTimeout(this._longTapTimeoutId);
        this._longTapTimeoutId = null;
    };
    EventHandlerImp.prototype._touchStartHandler = function (downEvent) {
        if (this._activeTouchId !== null) {
            return;
        }
        var touch = downEvent.changedTouches[0];
        this._activeTouchId = touch.identifier;
        this._lastTouchEventTimeStamp = this._eventTimeStamp(downEvent);
        var rootElement = this._target.ownerDocument.documentElement;
        this._cancelTap = false;
        this._touchMoveExceededManhattanDistance = false;
        this._preventTouchDragProcess = false;
        this._touchMoveStartCoordinate = this._getCoordinate(touch);
        if (this._unsubscribeRootTouchEvents !== null) {
            this._unsubscribeRootTouchEvents();
            this._unsubscribeRootTouchEvents = null;
        }
        {
            var boundTouchMoveWithDownHandler_1 = this._touchMoveHandler.bind(this);
            var boundTouchEndHandler_1 = this._touchEndHandler.bind(this);
            this._unsubscribeRootTouchEvents = function () {
                rootElement.removeEventListener('touchmove', boundTouchMoveWithDownHandler_1);
                rootElement.removeEventListener('touchend', boundTouchEndHandler_1);
            };
            rootElement.addEventListener('touchmove', boundTouchMoveWithDownHandler_1, { passive: false });
            rootElement.addEventListener('touchend', boundTouchEndHandler_1, { passive: false });
            this._clearLongTapTimeout();
            this._longTapTimeoutId = setTimeout(this._longTapHandler.bind(this, downEvent), Delay.LongTap);
        }
        this._processEvent(this._makeCompatEvent(downEvent, touch), this._handler.touchStartEvent);
        if (this._tapTimeoutId === null) {
            this._tapCount = 0;
            this._tapTimeoutId = setTimeout(this._resetTapTimeout.bind(this), Delay.ResetClick);
            this._tapCoordinate = this._getCoordinate(touch);
        }
    };
    EventHandlerImp.prototype._mouseDownHandler = function (downEvent) {
        if (downEvent.button === MouseEventButton.Right) {
            this._preventDefault(downEvent);
            this._processEvent(this._makeCompatEvent(downEvent), this._handler.mouseRightClickEvent);
            return;
        }
        if (downEvent.button !== MouseEventButton.Left) {
            return;
        }
        var rootElement = this._target.ownerDocument.documentElement;
        if (isFF()) {
            rootElement.addEventListener('mouseleave', this._onFirefoxOutsideMouseUp);
        }
        this._cancelClick = false;
        this._mouseMoveStartCoordinate = this._getCoordinate(downEvent);
        if (this._unsubscribeRootMouseEvents !== null) {
            this._unsubscribeRootMouseEvents();
            this._unsubscribeRootMouseEvents = null;
        }
        {
            var boundMouseMoveWithDownHandler_1 = this._mouseMoveWithDownHandler.bind(this);
            var boundMouseUpHandler_1 = this._mouseUpHandler.bind(this);
            this._unsubscribeRootMouseEvents = function () {
                rootElement.removeEventListener('mousemove', boundMouseMoveWithDownHandler_1);
                rootElement.removeEventListener('mouseup', boundMouseUpHandler_1);
            };
            rootElement.addEventListener('mousemove', boundMouseMoveWithDownHandler_1);
            rootElement.addEventListener('mouseup', boundMouseUpHandler_1);
        }
        this._mousePressed = true;
        if (this._firesTouchEvents(downEvent)) {
            return;
        }
        this._processEvent(this._makeCompatEvent(downEvent), this._handler.mouseDownEvent);
        if (this._clickTimeoutId === null) {
            this._clickCount = 0;
            this._clickTimeoutId = setTimeout(this._resetClickTimeout.bind(this), Delay.ResetClick);
            this._clickCoordinate = this._getCoordinate(downEvent);
        }
    };
    EventHandlerImp.prototype._init = function () {
        var _this = this;
        this._target.addEventListener('mouseenter', this._mouseEnterHandler.bind(this));
        // Do not show context menu when something went wrong
        this._target.addEventListener('touchcancel', this._clearLongTapTimeout.bind(this));
        {
            var doc_1 = this._target.ownerDocument;
            var outsideHandler_1 = function (event) {
                if (_this._handler.mouseDownOutsideEvent == null) {
                    return;
                }
                if (event.composed && _this._target.contains(event.composedPath()[0])) {
                    return;
                }
                if (event.target !== null && _this._target.contains(event.target)) {
                    return;
                }
                _this._handler.mouseDownOutsideEvent({ x: 0, y: 0, pageX: 0, pageY: 0 });
            };
            this._unsubscribeOutsideTouchEvents = function () {
                doc_1.removeEventListener('touchstart', outsideHandler_1);
            };
            this._unsubscribeOutsideMouseEvents = function () {
                doc_1.removeEventListener('mousedown', outsideHandler_1);
            };
            doc_1.addEventListener('mousedown', outsideHandler_1);
            doc_1.addEventListener('touchstart', outsideHandler_1, { passive: true });
        }
        if (isIOS()) {
            this._unsubscribeMobileSafariEvents = function () {
                _this._target.removeEventListener('dblclick', _this._onMobileSafariDoubleClick);
            };
            this._target.addEventListener('dblclick', this._onMobileSafariDoubleClick);
        }
        this._target.addEventListener('mouseleave', this._mouseLeaveHandler.bind(this));
        this._target.addEventListener('touchstart', this._touchStartHandler.bind(this), { passive: true });
        this._target.addEventListener('mousedown', function (e) {
            if (e.button === MouseEventButton.Middle) {
                // prevent incorrect scrolling event
                e.preventDefault();
                return false;
            }
            return undefined;
        });
        this._target.addEventListener('mousedown', this._mouseDownHandler.bind(this));
        this._initPinch();
        // Hey mobile Safari, what's up?
        // If mobile Safari doesn't have any touchmove handler with passive=false
        // it treats a touchstart and the following touchmove events as cancelable=false,
        // so we can't prevent them (as soon we subscribe on touchmove inside touchstart's handler).
        // And we'll get scroll of the page along with chart's one instead of only chart's scroll.
        // biome-ignore lint/suspicious/noEmptyBlockStatements: Safari requires an empty non-passive handler.
        this._target.addEventListener('touchmove', function () { }, { passive: false });
    };
    EventHandlerImp.prototype._initPinch = function () {
        var _this = this;
        if (!isValid(this._handler.pinchStartEvent) && !isValid(this._handler.pinchEvent) && !isValid(this._handler.pinchEndEvent)) {
            return;
        }
        this._target.addEventListener('touchstart', function (event) {
            _this._checkPinchState(event.touches);
        }, { passive: true });
        this._target.addEventListener('touchmove', function (event) {
            if (event.touches.length !== 2 || _this._startPinchMiddleCoordinate === null) {
                return;
            }
            if (isValid(_this._handler.pinchEvent)) {
                var currentDistance = _this._getTouchDistance(event.touches[0], event.touches[1]);
                var scale = currentDistance / _this._startPinchDistance;
                _this._handler.pinchEvent(__assign(__assign({}, _this._startPinchMiddleCoordinate), { pageX: 0, pageY: 0 }), scale);
                _this._preventDefault(event);
            }
        }, { passive: false });
        this._target.addEventListener('touchend', function (event) {
            _this._checkPinchState(event.touches);
        });
    };
    EventHandlerImp.prototype._checkPinchState = function (touches) {
        if (touches.length === 1) {
            this._pinchPrevented = false;
        }
        if (touches.length !== 2 || this._pinchPrevented || this._longTapActive) {
            this._stopPinch();
        }
        else {
            this._startPinch(touches);
        }
    };
    EventHandlerImp.prototype._startPinch = function (touches) {
        var box = this._target.getBoundingClientRect();
        this._startPinchMiddleCoordinate = {
            x: (touches[0].clientX - box.left + (touches[1].clientX - box.left)) / 2,
            y: (touches[0].clientY - box.top + (touches[1].clientY - box.top)) / 2
        };
        this._startPinchDistance = this._getTouchDistance(touches[0], touches[1]);
        if (isValid(this._handler.pinchStartEvent)) {
            this._handler.pinchStartEvent({ x: 0, y: 0, pageX: 0, pageY: 0 });
        }
        this._clearLongTapTimeout();
    };
    EventHandlerImp.prototype._stopPinch = function () {
        if (this._startPinchMiddleCoordinate === null) {
            return;
        }
        this._startPinchMiddleCoordinate = null;
        if (isValid(this._handler.pinchEndEvent)) {
            this._handler.pinchEndEvent({ x: 0, y: 0, pageX: 0, pageY: 0 });
        }
    };
    EventHandlerImp.prototype._mouseLeaveHandler = function (event) {
        var _a, _b, _c;
        (_a = this._unsubscribeMousemove) === null || _a === void 0 ? void 0 : _a.call(this);
        (_b = this._unsubscribeMouseWheel) === null || _b === void 0 ? void 0 : _b.call(this);
        (_c = this._unsubscribeContextMenu) === null || _c === void 0 ? void 0 : _c.call(this);
        if (this._firesTouchEvents(event)) {
            return;
        }
        if (!this._acceptMouseLeave) {
            // mobile Safari sometimes emits mouse leave event for no reason, there is no way to handle it in other way
            // just ignore this event if there was no mouse move or mouse enter events
            return;
        }
        this._processEvent(this._makeCompatEvent(event), this._handler.mouseLeaveEvent);
        // accept all mouse leave events if it's not an iOS device
        this._acceptMouseLeave = !isIOS();
    };
    EventHandlerImp.prototype._longTapHandler = function (event) {
        var touch = this._touchWithId(event.touches, this._activeTouchId);
        if (touch === null) {
            return;
        }
        this._processEvent(this._makeCompatEvent(event, touch), this._handler.longTapEvent);
        this._cancelTap = true;
        // long tap is active until touchend event with 0 touches occurred
        this._longTapActive = true;
    };
    EventHandlerImp.prototype._firesTouchEvents = function (e) {
        var _a;
        // @ts-ignore
        if (isValid((_a = e.sourceCapabilities) === null || _a === void 0 ? void 0 : _a.firesTouchEvents)) {
            // @ts-ignore
            return e.sourceCapabilities.firesTouchEvents;
        }
        return this._eventTimeStamp(e) < this._lastTouchEventTimeStamp + Delay.PreventFiresTouchEvents;
    };
    EventHandlerImp.prototype._processEvent = function (event, callback) {
        callback === null || callback === void 0 ? void 0 : callback.call(this._handler, event);
    };
    EventHandlerImp.prototype._makeCompatEvent = function (event, touch) {
        var _this = this;
        // TouchEvent has no clientX/Y coordinates:
        // We have to use the last Touch instead
        var eventLike = touch !== null && touch !== void 0 ? touch : event;
        var box = this._target.getBoundingClientRect();
        return {
            x: eventLike.clientX - box.left,
            y: eventLike.clientY - box.top,
            pageX: eventLike.pageX,
            pageY: eventLike.pageY,
            isTouch: !event.type.startsWith('mouse') && event.type !== 'contextmenu' && event.type !== 'click' && event.type !== 'wheel',
            preventDefault: function () {
                if (event.type !== 'touchstart') {
                    // touchstart is passive and cannot be prevented
                    _this._preventDefault(event);
                }
            }
        };
    };
    EventHandlerImp.prototype._getTouchDistance = function (p1, p2) {
        var xDiff = p1.clientX - p2.clientX;
        var yDiff = p1.clientY - p2.clientY;
        return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    };
    EventHandlerImp.prototype._preventDefault = function (event) {
        if (event.cancelable) {
            event.preventDefault();
        }
    };
    EventHandlerImp.prototype._getCoordinate = function (eventLike) {
        return {
            x: eventLike.pageX,
            y: eventLike.pageY
        };
    };
    EventHandlerImp.prototype._eventTimeStamp = function (e) {
        var _a;
        // for some reason e.timestamp is always 0 on iPad with magic mouse, so we use performance.now() as a fallback
        return (_a = e.timeStamp) !== null && _a !== void 0 ? _a : performance.now();
    };
    EventHandlerImp.prototype._touchWithId = function (touches, id) {
        // biome-ignore lint/style/useForOf: TouchList does not implement the iterable protocol in every target browser.
        for (var i = 0; i < touches.length; ++i) {
            if (touches[i].identifier === id) {
                return touches[i];
            }
        }
        return null;
    };
    return EventHandlerImp;
}());

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
var scrollLeft = {
    name: 'scrollLeft',
    keys: 'Shift+ArrowLeft',
    action: function (_a) {
        var chart = _a.chart;
        chart.scrollByDistance(-3 * chart.getBarSpace().bar);
    }
};

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
var scrollRight = {
    name: 'scrollRight',
    keys: 'Shift+ArrowRight',
    action: function (_a) {
        var chart = _a.chart;
        chart.scrollByDistance(3 * chart.getBarSpace().bar);
    }
};

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
var zoomIn = {
    name: 'zoomIn',
    keys: ['Shift+Equal', 'Shift+NumpadAdd'],
    action: function (_a) {
        var chart = _a.chart;
        chart.zoomAtCoordinate(1.05);
    }
};

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
var zoomOut = {
    name: 'zoomOut',
    keys: ['Shift+Minus', 'Shift+NumpadSubtract'],
    action: function (_a) {
        var chart = _a.chart;
        chart.zoomAtCoordinate(0.95);
    }
};

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
var _a;
var hotkeys = (_a = {},
    _a[scrollLeft.name] = scrollLeft,
    _a[scrollRight.name] = scrollRight,
    _a[zoomIn.name] = zoomIn,
    _a[zoomOut.name] = zoomOut,
    _a);
function registerHotkey(hotkey) {
    hotkeys[hotkey.name] = hotkey;
}
function getHotkey(name) {
    var _a;
    return (_a = hotkeys[name]) !== null && _a !== void 0 ? _a : null;
}
function getSupportedHotkeys() {
    return Object.keys(hotkeys);
}

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
var WidgetNameConstants = {
    MAIN: 'main',
    X_AXIS: 'xAxis',
    Y_AXIS: 'yAxis',
    SEPARATOR: 'separator'
};
var REAL_SEPARATOR_HEIGHT = 7;

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
var FLING_DECAY = 0.975;
var FLING_FRAME_DURATION = 1000 / 60;
var FLING_MIN_FRAME_DISTANCE = 1;
var hotkeyModifierAlias = {
    command: 'meta',
    cmd: 'meta',
    control: 'ctrl',
    option: 'alt',
    mod: isAppleOS() ? 'meta' : 'ctrl'
};
var hotkeyAlias = {
    '+': 'equal',
    plus: 'equal',
    add: 'equal',
    numpadadd: 'equal',
    '-': 'minus',
    subtract: 'minus',
    numpadsubtract: 'minus',
    esc: 'escape',
    del: 'delete',
    left: 'arrowleft',
    right: 'arrowright',
    up: 'arrowup',
    down: 'arrowdown'
};
var hotKeyModifierOrder = ['ctrl', 'alt', 'shift', 'meta'];
var Event = /** @class */ (function () {
    function Event(container, chart) {
        var _this = this;
        // 惯性滚动开始时间
        this._flingStartTime = performance.now();
        // 惯性滚动动画
        this._flingScrollAnimation = null;
        // 开始滚动时坐标点
        this._startScrollCoordinate = null;
        // 开始触摸时坐标
        this._touchCoordinate = null;
        // 是否是取消了十字光标
        this._touchCancelCrosshair = false;
        // 是否缩放过
        this._touchZoomed = false;
        // 用来记录捏合缩放的尺寸
        this._pinchScale = 1;
        this._mouseDownWidget = null;
        this._prevYAxisRanges = new Map();
        this._xAxisStartScaleCoordinate = null;
        this._xAxisStartScaleDistance = 0;
        this._xAxisScale = 1;
        this._yAxisStartScaleDistance = 0;
        this._mouseMoveTriggerWidgetInfo = { pane: null, widget: null };
        this._boundKeyBoardDownEvent = function (event) {
            var _a, _b, _c;
            var target = event.target;
            var tagName = target === null || target === void 0 ? void 0 : target.tagName.toLowerCase();
            if (tagName === 'input' || tagName === 'textarea' || (target === null || target === void 0 ? void 0 : target.isContentEditable) === true) {
                return;
            }
            var _d = _this._chart.getHotKey(), enabled = _d.enabled, exclude = _d.exclude;
            if (!enabled) {
                return;
            }
            var eventKeys = [];
            if (event.ctrlKey) {
                eventKeys.push('ctrl');
            }
            if (event.altKey) {
                eventKeys.push('alt');
            }
            if (event.shiftKey) {
                eventKeys.push('shift');
            }
            if (event.metaKey) {
                eventKeys.push('meta');
            }
            var eventCode = event.code.trim().toLowerCase();
            if (/^key[a-z]$/.test(eventCode)) {
                eventKeys.push(eventCode.slice(3));
            }
            else if (/^digit[0-9]$/.test(eventCode)) {
                eventKeys.push(eventCode.slice(5));
            }
            else {
                eventKeys.push((_a = hotkeyAlias[eventCode]) !== null && _a !== void 0 ? _a : eventCode);
            }
            var key = eventKeys.join('+');
            var names = getSupportedHotkeys();
            for (var i = names.length - 1; i >= 0; i--) {
                var name_1 = names[i];
                var hotkey = getHotkey(name_1);
                if (!exclude.includes(name_1) && isValid(hotkey)) {
                    var hotkeyKeys = isArray(hotkey.keys) ? hotkey.keys : [hotkey.keys];
                    var match = hotkeyKeys.some(function (hotkeyKey) {
                        var modifiers = [];
                        var normalKey = '';
                        hotkeyKey
                            .replace(/\+\+$/, '+Plus')
                            .replace(/\+=$/, '+Equal')
                            .split('+')
                            .forEach(function (part) {
                            var _a, _b;
                            var hotkeyPart = (_a = hotkeyModifierAlias[part.trim().toLowerCase()]) !== null && _a !== void 0 ? _a : part;
                            var hotkeyPartValue = hotkeyPart.trim().toLowerCase();
                            var value = '';
                            if (/^key[a-z]$/.test(hotkeyPartValue)) {
                                value = hotkeyPartValue.slice(3);
                            }
                            else if (/^digit[0-9]$/.test(hotkeyPartValue)) {
                                value = hotkeyPartValue.slice(5);
                            }
                            else {
                                value = (_b = hotkeyAlias[hotkeyPartValue]) !== null && _b !== void 0 ? _b : hotkeyPartValue;
                            }
                            if (hotKeyModifierOrder.includes(value)) {
                                if (!modifiers.includes(value)) {
                                    modifiers.push(value);
                                }
                            }
                            else if (value.length > 0) {
                                normalKey = value;
                            }
                        });
                        modifiers.sort(function (a, b) { return hotKeyModifierOrder.indexOf(a) - hotKeyModifierOrder.indexOf(b); });
                        return __spreadArray(__spreadArray([], __read(modifiers), false), [normalKey], false).filter(function (key) { return key.length > 0; }).join('+') === key;
                    });
                    if (match) {
                        var params = { chart: _this._chart, event: event, key: key, hotkey: hotkey };
                        if (!isFunction(hotkey.check) || hotkey.check(params)) {
                            if ((_b = hotkey.preventDefault) !== null && _b !== void 0 ? _b : true) {
                                event.preventDefault();
                            }
                            if ((_c = hotkey.stopPropagation) !== null && _c !== void 0 ? _c : false) {
                                event.stopPropagation();
                            }
                            hotkey.action(params);
                            return;
                        }
                    }
                }
            }
        };
        this._chart = chart;
        this._event = new EventHandlerImp(container, this, {
            treatVertDragAsPageScroll: function () { return false; },
            treatHorzDragAsPageScroll: function () { return false; }
        });
        document.addEventListener('keydown', this._boundKeyBoardDownEvent);
    }
    Event.prototype._getYAxisByWidget = function (widget) {
        if (widget.getName() === WidgetNameConstants.Y_AXIS) {
            return widget.getAxisComponent();
        }
        return widget.getPane().getYAxisComponentById();
    };
    Event.prototype._getYAxisScaleTargetByWidget = function (widget) {
        var yAxis = this._getYAxisByWidget(widget);
        var pane = widget.getPane();
        if (pane.isManualYAxis(yAxis.id)) {
            return pane.getYAxisComponentById();
        }
        return yAxis;
    };
    Event.prototype._syncYAxisValueRange = function (yAxis, sourceRange) {
        var baseRange = yAxis.getRange();
        var from = sourceRange.from, to = sourceRange.to;
        var realFrom = yAxis.valueToRealValue(from, { range: baseRange });
        var realTo = yAxis.valueToRealValue(to, { range: baseRange });
        var displayFrom = yAxis.realValueToDisplayValue(realFrom, { range: baseRange });
        var displayTo = yAxis.realValueToDisplayValue(realTo, { range: baseRange });
        yAxis.setRange({
            from: from,
            to: to,
            range: to - from,
            realFrom: realFrom,
            realTo: realTo,
            realRange: realTo - realFrom,
            displayFrom: displayFrom,
            displayTo: displayTo,
            displayRange: displayTo - displayFrom
        });
    };
    Event.prototype._syncManualYAxesValueRange = function (widget, sourceYAxis) {
        var _this = this;
        var sourceRange = sourceYAxis.getRange();
        widget
            .getPane()
            .getYAxisComponents()
            .forEach(function (axis) {
            var yAxis = axis;
            if (yAxis !== sourceYAxis && widget.getPane().isManualYAxis(yAxis.id)) {
                _this._syncYAxisValueRange(yAxis, sourceRange);
            }
        });
    };
    Event.prototype._resetYAxisAndManualYAxes = function (widget, sourceYAxis) {
        sourceYAxis.setAutoCalcTickFlag(true);
        widget
            .getPane()
            .getYAxisComponents()
            .forEach(function (axis) {
            var yAxis = axis;
            if (widget.getPane().isManualYAxis(yAxis.id)) {
                yAxis.setAutoCalcTickFlag(true);
            }
        });
        this._chart.layout({
            measureWidth: true,
            update: true,
            buildYAxisTick: true
        });
    };
    Event.prototype.pinchStartEvent = function () {
        this._touchZoomed = true;
        this._pinchScale = 1;
        return true;
    };
    Event.prototype.pinchEvent = function (e, scale) {
        if (scale === void 0) { scale = 1; }
        var _a = this._findWidgetByEvent(e), pane = _a.pane, widget = _a.widget;
        if ((pane === null || pane === void 0 ? void 0 : pane.getId()) !== PaneIdConstants.X_AXIS && (widget === null || widget === void 0 ? void 0 : widget.getName()) === WidgetNameConstants.MAIN) {
            var event_1 = this._makeWidgetEvent(e, widget);
            var zoomScale = (scale - this._pinchScale) * 5;
            this._pinchScale = scale;
            this._chart.getChartStore().zoom(zoomScale, { x: event_1.x, y: event_1.y }, 'main');
            return true;
        }
        return false;
    };
    Event.prototype.mouseWheelHortEvent = function (_, distance) {
        if (distance === void 0) { distance = 0; }
        var store = this._chart.getChartStore();
        store.startScroll();
        store.scroll(distance);
        return true;
    };
    Event.prototype.mouseWheelVertEvent = function (e, scale) {
        if (scale === void 0) { scale = 0; }
        var widget = this._findWidgetByEvent(e).widget;
        var event = this._makeWidgetEvent(e, widget);
        var name = widget === null || widget === void 0 ? void 0 : widget.getName();
        if (name === WidgetNameConstants.MAIN) {
            this._chart.getChartStore().zoom(scale, { x: event.x, y: event.y }, 'main');
            return true;
        }
        if (name === WidgetNameConstants.Y_AXIS) {
            var yAxisWidget = widget;
            var yAxis = this._getYAxisByWidget(yAxisWidget);
            if (yAxis.scrollZoomEnabled) {
                var scaleFactor = 1 + scale * 0.05;
                var targetYAxis = this._getYAxisScaleTargetByWidget(yAxisWidget);
                this._zoomYAxis(targetYAxis, scaleFactor);
                this._syncManualYAxesValueRange(yAxisWidget, targetYAxis);
                return true;
            }
        }
        return false;
    };
    Event.prototype.mouseDownEvent = function (e) {
        var e_1, _a;
        var _b = this._findWidgetByEvent(e), pane = _b.pane, widget = _b.widget;
        this._mouseDownWidget = widget;
        if (widget !== null) {
            var event_2 = this._makeWidgetEvent(e, widget);
            var name_2 = widget.getName();
            switch (name_2) {
                case WidgetNameConstants.SEPARATOR: {
                    return widget.dispatchEvent('mouseDownEvent', event_2);
                }
                case WidgetNameConstants.MAIN: {
                    // Dispatch event first to allow overlays (e.g., continuous drawing) to consume it
                    var consumed = widget.dispatchEvent('mouseDownEvent', event_2);
                    // Only start scrolling if the event was not consumed by an overlay
                    if (!consumed) {
                        var yAxes = pane.getYAxisComponents();
                        try {
                            for (var yAxes_1 = __values(yAxes), yAxes_1_1 = yAxes_1.next(); !yAxes_1_1.done; yAxes_1_1 = yAxes_1.next()) {
                                var item = yAxes_1_1.value;
                                var yAxis = item;
                                if (!yAxis.getAutoCalcTickFlag()) {
                                    var range = yAxis.getRange();
                                    this._prevYAxisRanges.set(yAxis, __assign({}, range));
                                }
                            }
                        }
                        catch (e_1_1) { e_1 = { error: e_1_1 }; }
                        finally {
                            try {
                                if (yAxes_1_1 && !yAxes_1_1.done && (_a = yAxes_1.return)) _a.call(yAxes_1);
                            }
                            finally { if (e_1) throw e_1.error; }
                        }
                        this._startScrollCoordinate = { x: event_2.x, y: event_2.y };
                        this._chart.getChartStore().startScroll();
                    }
                    return consumed;
                }
                case WidgetNameConstants.X_AXIS: {
                    return this._processXAxisScrollStartEvent(widget, event_2);
                }
                case WidgetNameConstants.Y_AXIS: {
                    return this._processYAxisScaleStartEvent(widget, event_2);
                }
            }
        }
        return false;
    };
    Event.prototype.mouseMoveEvent = function (e) {
        var _a, _b, _c;
        var _d = this._findWidgetByEvent(e), pane = _d.pane, widget = _d.widget;
        var event = this._makeWidgetEvent(e, widget);
        if (((_a = this._mouseMoveTriggerWidgetInfo.pane) === null || _a === void 0 ? void 0 : _a.getId()) !== (pane === null || pane === void 0 ? void 0 : pane.getId()) || ((_b = this._mouseMoveTriggerWidgetInfo.widget) === null || _b === void 0 ? void 0 : _b.getName()) !== (widget === null || widget === void 0 ? void 0 : widget.getName())) {
            widget === null || widget === void 0 ? void 0 : widget.dispatchEvent('mouseEnterEvent', event);
            (_c = this._mouseMoveTriggerWidgetInfo.widget) === null || _c === void 0 ? void 0 : _c.dispatchEvent('mouseLeaveEvent', event);
            this._mouseMoveTriggerWidgetInfo = { pane: pane, widget: widget };
        }
        if (widget !== null) {
            var name_3 = widget.getName();
            switch (name_3) {
                case WidgetNameConstants.MAIN: {
                    var consumed = widget.dispatchEvent('mouseMoveEvent', event);
                    var crosshair = { x: event.x, y: event.y, paneId: pane === null || pane === void 0 ? void 0 : pane.getId() };
                    if (consumed) {
                        if (widget.getForceCursor() !== 'pointer') {
                            crosshair = undefined;
                        }
                        widget.setCursor('pointer');
                    }
                    else {
                        widget.setCursor('crosshair');
                    }
                    this._chart.getChartStore().setCrosshair(crosshair);
                    return consumed;
                }
                case WidgetNameConstants.SEPARATOR:
                case WidgetNameConstants.X_AXIS:
                case WidgetNameConstants.Y_AXIS: {
                    var consumed = widget.dispatchEvent('mouseMoveEvent', event);
                    this._chart.getChartStore().setCrosshair();
                    return consumed;
                }
            }
        }
        return false;
    };
    Event.prototype.pressedMouseMoveEvent = function (e) {
        var _a, _b;
        if (this._mouseDownWidget !== null && this._mouseDownWidget.getName() === WidgetNameConstants.SEPARATOR) {
            return this._mouseDownWidget.dispatchEvent('pressedMouseMoveEvent', e);
        }
        var _c = this._findWidgetByEvent(e), pane = _c.pane, widget = _c.widget;
        if (widget !== null && ((_a = this._mouseDownWidget) === null || _a === void 0 ? void 0 : _a.getPane().getId()) === (pane === null || pane === void 0 ? void 0 : pane.getId()) && ((_b = this._mouseDownWidget) === null || _b === void 0 ? void 0 : _b.getName()) === widget.getName()) {
            var event_3 = this._makeWidgetEvent(e, widget);
            var name_4 = widget.getName();
            switch (name_4) {
                case WidgetNameConstants.MAIN: {
                    var crosshair = void 0;
                    var consumed = widget.dispatchEvent('pressedMouseMoveEvent', event_3);
                    if (!consumed) {
                        this._processMainScrollingEvent(widget, event_3);
                    }
                    else {
                        // Explicitly update overlay when event was consumed (e.g., continuous drawing)
                        this._chart.updatePane(1 /* UpdateLevel.Overlay */);
                    }
                    if (!consumed || widget.getForceCursor() === 'pointer') {
                        crosshair = { x: event_3.x, y: event_3.y, paneId: pane === null || pane === void 0 ? void 0 : pane.getId() };
                    }
                    this._chart.getChartStore().setCrosshair(crosshair, { forceInvalidate: true });
                    return consumed;
                }
                case WidgetNameConstants.X_AXIS: {
                    return this._processXAxisScrollingEvent(widget, event_3);
                }
                case WidgetNameConstants.Y_AXIS: {
                    return this._processYAxisScalingEvent(widget, event_3);
                }
            }
        }
        return false;
    };
    Event.prototype.mouseUpEvent = function (e) {
        var widget = this._findWidgetByEvent(e).widget;
        var consumed = false;
        if (widget !== null) {
            var event_4 = this._makeWidgetEvent(e, widget);
            var name_5 = widget.getName();
            switch (name_5) {
                case WidgetNameConstants.MAIN:
                case WidgetNameConstants.SEPARATOR:
                case WidgetNameConstants.X_AXIS:
                case WidgetNameConstants.Y_AXIS: {
                    consumed = widget.dispatchEvent('mouseUpEvent', event_4);
                    break;
                }
            }
            if (consumed) {
                this._chart.updatePane(1 /* UpdateLevel.Overlay */);
            }
        }
        this._mouseDownWidget = null;
        this._startScrollCoordinate = null;
        this._prevYAxisRanges.clear();
        this._xAxisStartScaleCoordinate = null;
        this._xAxisStartScaleDistance = 0;
        this._xAxisScale = 1;
        this._yAxisStartScaleDistance = 0;
        return consumed;
    };
    Event.prototype.mouseClickEvent = function (e) {
        var widget = this._findWidgetByEvent(e).widget;
        if (widget !== null) {
            var event_5 = this._makeWidgetEvent(e, widget);
            return widget.dispatchEvent('mouseClickEvent', event_5);
        }
        return false;
    };
    Event.prototype.mouseRightClickEvent = function (e) {
        var widget = this._findWidgetByEvent(e).widget;
        var consumed = false;
        if (widget !== null) {
            var event_6 = this._makeWidgetEvent(e, widget);
            var name_6 = widget.getName();
            switch (name_6) {
                case WidgetNameConstants.MAIN:
                case WidgetNameConstants.X_AXIS:
                case WidgetNameConstants.Y_AXIS: {
                    consumed = widget.dispatchEvent('mouseRightClickEvent', event_6);
                    break;
                }
            }
            if (consumed) {
                this._chart.updatePane(1 /* UpdateLevel.Overlay */);
            }
        }
        return false;
    };
    Event.prototype.mouseDoubleClickEvent = function (e) {
        var widget = this._findWidgetByEvent(e).widget;
        if (widget !== null) {
            var name_7 = widget.getName();
            switch (name_7) {
                case WidgetNameConstants.MAIN: {
                    var event_7 = this._makeWidgetEvent(e, widget);
                    return widget.dispatchEvent('mouseDoubleClickEvent', event_7);
                }
                case WidgetNameConstants.Y_AXIS: {
                    var yAxisWidget = widget;
                    var yAxis = this._getYAxisByWidget(yAxisWidget);
                    var targetYAxis = this._getYAxisScaleTargetByWidget(yAxisWidget);
                    if (!targetYAxis.getAutoCalcTickFlag() || !yAxis.getAutoCalcTickFlag()) {
                        this._resetYAxisAndManualYAxes(yAxisWidget, targetYAxis);
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    };
    Event.prototype.mouseLeaveEvent = function () {
        this._chart.getChartStore().setCrosshair();
        return true;
    };
    Event.prototype.touchStartEvent = function (e) {
        var e_2, _a;
        var _b;
        var _c = this._findWidgetByEvent(e), pane = _c.pane, widget = _c.widget;
        if (widget !== null) {
            var event_8 = this._makeWidgetEvent(e, widget);
            (_b = event_8.preventDefault) === null || _b === void 0 ? void 0 : _b.call(event_8);
            var name_8 = widget.getName();
            switch (name_8) {
                case WidgetNameConstants.MAIN: {
                    var chartStore = this._chart.getChartStore();
                    if (widget.dispatchEvent('mouseDownEvent', event_8)) {
                        this._touchCancelCrosshair = true;
                        this._touchCoordinate = null;
                        chartStore.setCrosshair(undefined, { notInvalidate: true });
                        this._chart.updatePane(1 /* UpdateLevel.Overlay */);
                        return true;
                    }
                    if (this._flingScrollAnimation !== null) {
                        this._flingScrollAnimation.cancel();
                        this._flingScrollAnimation = null;
                    }
                    this._flingStartTime = performance.now();
                    var yAxes = pane.getYAxisComponents();
                    try {
                        for (var yAxes_2 = __values(yAxes), yAxes_2_1 = yAxes_2.next(); !yAxes_2_1.done; yAxes_2_1 = yAxes_2.next()) {
                            var item = yAxes_2_1.value;
                            var yAxis = item;
                            if (!yAxis.getAutoCalcTickFlag()) {
                                var range = yAxis.getRange();
                                this._prevYAxisRanges.set(yAxis, __assign({}, range));
                            }
                        }
                    }
                    catch (e_2_1) { e_2 = { error: e_2_1 }; }
                    finally {
                        try {
                            if (yAxes_2_1 && !yAxes_2_1.done && (_a = yAxes_2.return)) _a.call(yAxes_2);
                        }
                        finally { if (e_2) throw e_2.error; }
                    }
                    this._startScrollCoordinate = { x: event_8.x, y: event_8.y };
                    chartStore.startScroll();
                    this._touchZoomed = false;
                    if (this._touchCoordinate !== null) {
                        var xDif = event_8.x - this._touchCoordinate.x;
                        var yDif = event_8.y - this._touchCoordinate.y;
                        var radius = Math.sqrt(xDif * xDif + yDif * yDif);
                        if (radius < TOUCH_MIN_RADIUS) {
                            this._touchCoordinate = { x: event_8.x, y: event_8.y };
                            chartStore.setCrosshair({ x: event_8.x, y: event_8.y, paneId: pane === null || pane === void 0 ? void 0 : pane.getId() });
                        }
                        else {
                            this._touchCoordinate = null;
                            this._touchCancelCrosshair = true;
                            chartStore.setCrosshair();
                        }
                    }
                    return true;
                }
                case WidgetNameConstants.X_AXIS: {
                    return this._processXAxisScrollStartEvent(widget, event_8);
                }
                case WidgetNameConstants.Y_AXIS: {
                    return this._processYAxisScaleStartEvent(widget, event_8);
                }
            }
        }
        return false;
    };
    Event.prototype.touchMoveEvent = function (e) {
        var _a, _b, _c;
        var _d = this._findWidgetByEvent(e), pane = _d.pane, widget = _d.widget;
        if (widget !== null) {
            var event_9 = this._makeWidgetEvent(e, widget);
            var name_9 = widget.getName();
            var chartStore = this._chart.getChartStore();
            switch (name_9) {
                case WidgetNameConstants.MAIN: {
                    if (widget.dispatchEvent('pressedMouseMoveEvent', event_9)) {
                        (_a = event_9.preventDefault) === null || _a === void 0 ? void 0 : _a.call(event_9);
                        chartStore.setCrosshair(undefined, { notInvalidate: true });
                        this._chart.updatePane(1 /* UpdateLevel.Overlay */);
                        return true;
                    }
                    if (this._touchCoordinate !== null) {
                        (_b = event_9.preventDefault) === null || _b === void 0 ? void 0 : _b.call(event_9);
                        chartStore.setCrosshair({ x: event_9.x, y: event_9.y, paneId: pane === null || pane === void 0 ? void 0 : pane.getId() });
                    }
                    else {
                        this._processMainScrollingEvent(widget, event_9);
                    }
                    return true;
                }
                case WidgetNameConstants.X_AXIS: {
                    (_c = event_9.preventDefault) === null || _c === void 0 ? void 0 : _c.call(event_9);
                    return this._processXAxisScrollingEvent(widget, event_9);
                }
                case WidgetNameConstants.Y_AXIS: {
                    return this._processYAxisScalingEvent(widget, event_9);
                }
            }
        }
        return false;
    };
    Event.prototype.touchEndEvent = function (e) {
        var _this = this;
        var widget = this._findWidgetByEvent(e).widget;
        if (widget !== null) {
            var event_10 = this._makeWidgetEvent(e, widget);
            var name_10 = widget.getName();
            switch (name_10) {
                case WidgetNameConstants.MAIN: {
                    widget.dispatchEvent('mouseUpEvent', event_10);
                    if (this._startScrollCoordinate !== null) {
                        var time = performance.now() - this._flingStartTime;
                        var distance = event_10.x - this._startScrollCoordinate.x;
                        var initialFrameDistance_1 = (distance / (time > 0 ? time : 1)) * 20;
                        if (time < 200 && Math.abs(initialFrameDistance_1) > FLING_MIN_FRAME_DISTANCE) {
                            var store_1 = this._chart.getChartStore();
                            var duration_1 = (Math.log(FLING_MIN_FRAME_DISTANCE / Math.abs(initialFrameDistance_1)) / Math.log(FLING_DECAY)) * FLING_FRAME_DURATION;
                            var animation_1 = new Animation({ duration: duration_1 }).doFrame(function (frameTime) {
                                var frameCount = frameTime / FLING_FRAME_DURATION;
                                var progressDistance = (initialFrameDistance_1 * (1 - Math.pow(FLING_DECAY, frameCount))) / (1 - FLING_DECAY);
                                store_1.scroll(progressDistance);
                                if (frameTime >= duration_1 && _this._flingScrollAnimation === animation_1) {
                                    _this._flingScrollAnimation = null;
                                }
                            });
                            store_1.startScroll();
                            this._flingScrollAnimation = animation_1;
                            animation_1.start();
                        }
                    }
                    return true;
                }
                case WidgetNameConstants.X_AXIS:
                case WidgetNameConstants.Y_AXIS: {
                    var consumed = widget.dispatchEvent('mouseUpEvent', event_10);
                    if (consumed) {
                        this._chart.updatePane(1 /* UpdateLevel.Overlay */);
                    }
                }
            }
            this._startScrollCoordinate = null;
            this._prevYAxisRanges.clear();
            this._xAxisStartScaleCoordinate = null;
            this._xAxisStartScaleDistance = 0;
            this._xAxisScale = 1;
            this._yAxisStartScaleDistance = 0;
        }
        return false;
    };
    Event.prototype.tapEvent = function (e) {
        var _a = this._findWidgetByEvent(e), pane = _a.pane, widget = _a.widget;
        var consumed = false;
        if (widget !== null) {
            var event_11 = this._makeWidgetEvent(e, widget);
            var result = widget.dispatchEvent('mouseClickEvent', event_11);
            if (widget.getName() === WidgetNameConstants.MAIN) {
                var event_12 = this._makeWidgetEvent(e, widget);
                var chartStore = this._chart.getChartStore();
                if (result) {
                    this._touchCancelCrosshair = true;
                    this._touchCoordinate = null;
                    chartStore.setCrosshair(undefined, { notInvalidate: true });
                    consumed = true;
                }
                else {
                    if (!this._touchCancelCrosshair && !this._touchZoomed) {
                        this._touchCoordinate = { x: event_12.x, y: event_12.y };
                        chartStore.setCrosshair({ x: event_12.x, y: event_12.y, paneId: pane === null || pane === void 0 ? void 0 : pane.getId() }, { notInvalidate: true });
                        consumed = true;
                    }
                    this._touchCancelCrosshair = false;
                }
            }
            if (consumed || result) {
                this._chart.updatePane(1 /* UpdateLevel.Overlay */);
            }
        }
        return consumed;
    };
    Event.prototype.doubleTapEvent = function (e) {
        return this.mouseDoubleClickEvent(e);
    };
    Event.prototype.longTapEvent = function (e) {
        var _a = this._findWidgetByEvent(e), pane = _a.pane, widget = _a.widget;
        if (widget !== null && widget.getName() === WidgetNameConstants.MAIN) {
            var event_13 = this._makeWidgetEvent(e, widget);
            this._touchCoordinate = { x: event_13.x, y: event_13.y };
            this._chart.getChartStore().setCrosshair({ x: event_13.x, y: event_13.y, paneId: pane === null || pane === void 0 ? void 0 : pane.getId() });
            return true;
        }
        return false;
    };
    Event.prototype._processMainScrollingEvent = function (widget, event) {
        var e_3, _a;
        var _b;
        if (this._startScrollCoordinate !== null) {
            var yAxes = widget.getPane().getYAxisComponents();
            try {
                for (var yAxes_3 = __values(yAxes), yAxes_3_1 = yAxes_3.next(); !yAxes_3_1.done; yAxes_3_1 = yAxes_3.next()) {
                    var item = yAxes_3_1.value;
                    var yAxis = item;
                    var prevRange = this._prevYAxisRanges.get(yAxis);
                    if (isValid(prevRange) && !yAxis.getAutoCalcTickFlag() && yAxis.scrollZoomEnabled) {
                        (_b = event.preventDefault) === null || _b === void 0 ? void 0 : _b.call(event);
                        var from = prevRange.from, to = prevRange.to, range = prevRange.range;
                        var distance_1 = 0;
                        if (yAxis.reverse) {
                            distance_1 = this._startScrollCoordinate.y - event.y;
                        }
                        else {
                            distance_1 = event.y - this._startScrollCoordinate.y;
                        }
                        var bounding = widget.getBounding();
                        var scale = distance_1 / bounding.height;
                        var difRange = range * scale;
                        var newFrom = from + difRange;
                        var newTo = to + difRange;
                        var newRealFrom = yAxis.valueToRealValue(newFrom, { range: prevRange });
                        var newRealTo = yAxis.valueToRealValue(newTo, { range: prevRange });
                        var newDisplayFrom = yAxis.realValueToDisplayValue(newRealFrom, { range: prevRange });
                        var newDisplayTo = yAxis.realValueToDisplayValue(newRealTo, { range: prevRange });
                        yAxis.setRange({
                            from: newFrom,
                            to: newTo,
                            range: newTo - newFrom,
                            realFrom: newRealFrom,
                            realTo: newRealTo,
                            realRange: newRealTo - newRealFrom,
                            displayFrom: newDisplayFrom,
                            displayTo: newDisplayTo,
                            displayRange: newDisplayTo - newDisplayFrom
                        });
                    }
                }
            }
            catch (e_3_1) { e_3 = { error: e_3_1 }; }
            finally {
                try {
                    if (yAxes_3_1 && !yAxes_3_1.done && (_a = yAxes_3.return)) _a.call(yAxes_3);
                }
                finally { if (e_3) throw e_3.error; }
            }
            var distance = event.x - this._startScrollCoordinate.x;
            this._chart.getChartStore().scroll(distance);
        }
    };
    Event.prototype._processXAxisScrollStartEvent = function (widget, event) {
        var consumed = widget.dispatchEvent('mouseDownEvent', event);
        if (consumed) {
            this._chart.updatePane(1 /* UpdateLevel.Overlay */);
        }
        this._xAxisStartScaleCoordinate = { x: event.x, y: event.y };
        this._xAxisStartScaleDistance = event.pageX;
        return consumed;
    };
    Event.prototype._processXAxisScrollingEvent = function (widget, event) {
        var consumed = widget.dispatchEvent('pressedMouseMoveEvent', event);
        if (!consumed) {
            var xAxis = widget.getPane().getXAxisComponent();
            if (xAxis.scrollZoomEnabled && this._xAxisStartScaleDistance !== 0) {
                var scale = this._xAxisStartScaleDistance / event.pageX;
                if (Number.isFinite(scale)) {
                    var zoomScale = (scale - this._xAxisScale) * 10;
                    this._xAxisScale = scale;
                    this._chart.getChartStore().zoom(zoomScale, this._xAxisStartScaleCoordinate, 'xAxis');
                }
            }
        }
        else {
            this._chart.updatePane(1 /* UpdateLevel.Overlay */);
        }
        return consumed;
    };
    Event.prototype._processYAxisScaleStartEvent = function (widget, event) {
        var consumed = widget.dispatchEvent('mouseDownEvent', event);
        if (consumed) {
            this._chart.updatePane(1 /* UpdateLevel.Overlay */);
        }
        var yAxis = this._getYAxisScaleTargetByWidget(widget);
        var range = yAxis.getRange();
        this._prevYAxisRanges.set(yAxis, __assign({}, range));
        this._yAxisStartScaleDistance = event.pageY;
        return consumed;
    };
    Event.prototype._processYAxisScalingEvent = function (widget, event) {
        var _a;
        var consumed = widget.dispatchEvent('pressedMouseMoveEvent', event);
        if (!consumed) {
            var yAxis = this._getYAxisByWidget(widget);
            var targetYAxis = this._getYAxisScaleTargetByWidget(widget);
            var prevYAxisRange = this._prevYAxisRanges.get(targetYAxis);
            if (isValid(prevYAxisRange) && yAxis.scrollZoomEnabled && this._yAxisStartScaleDistance !== 0) {
                (_a = event.preventDefault) === null || _a === void 0 ? void 0 : _a.call(event);
                var scaleFactor = event.pageY / this._yAxisStartScaleDistance;
                this._zoomYAxis(targetYAxis, scaleFactor, prevYAxisRange);
                this._syncManualYAxesValueRange(widget, targetYAxis);
            }
        }
        else {
            this._chart.updatePane(1 /* UpdateLevel.Overlay */);
        }
        return consumed;
    };
    Event.prototype._zoomYAxis = function (yAxis, scaleFactor, baseRange) {
        var prevYAxisRange = baseRange !== null && baseRange !== void 0 ? baseRange : yAxis.getRange();
        var from = prevYAxisRange.from, to = prevYAxisRange.to, range = prevYAxisRange.range;
        var newRange = range * scaleFactor;
        var difRange = (newRange - range) / 2;
        var newFrom = from - difRange;
        var newTo = to + difRange;
        var newRealFrom = yAxis.valueToRealValue(newFrom, { range: prevYAxisRange });
        var newRealTo = yAxis.valueToRealValue(newTo, { range: prevYAxisRange });
        var newDisplayFrom = yAxis.realValueToDisplayValue(newRealFrom, { range: prevYAxisRange });
        var newDisplayTo = yAxis.realValueToDisplayValue(newRealTo, { range: prevYAxisRange });
        yAxis.setRange({
            from: newFrom,
            to: newTo,
            range: newRange,
            realFrom: newRealFrom,
            realTo: newRealTo,
            realRange: newRealTo - newRealFrom,
            displayFrom: newDisplayFrom,
            displayTo: newDisplayTo,
            displayRange: newDisplayTo - newDisplayFrom
        });
        this._chart.layout({
            measureWidth: true,
            update: true,
            buildYAxisTick: true
        });
    };
    Event.prototype._findWidgetByEvent = function (event) {
        var e_4, _a, e_5, _b, e_6, _c;
        var x = event.x, y = event.y;
        var separatorPanes = this._chart.getSeparatorPanes();
        var separatorSize = this._chart.getStyles().separator.size;
        try {
            for (var separatorPanes_1 = __values(separatorPanes), separatorPanes_1_1 = separatorPanes_1.next(); !separatorPanes_1_1.done; separatorPanes_1_1 = separatorPanes_1.next()) {
                var items = separatorPanes_1_1.value;
                var pane_1 = items[1];
                var bounding = pane_1.getBounding();
                var top_1 = bounding.top - Math.round((REAL_SEPARATOR_HEIGHT - separatorSize) / 2);
                if (x >= bounding.left && x <= bounding.left + bounding.width && y >= top_1 && y <= top_1 + REAL_SEPARATOR_HEIGHT) {
                    return { pane: pane_1, widget: pane_1.getWidget() };
                }
            }
        }
        catch (e_4_1) { e_4 = { error: e_4_1 }; }
        finally {
            try {
                if (separatorPanes_1_1 && !separatorPanes_1_1.done && (_a = separatorPanes_1.return)) _a.call(separatorPanes_1);
            }
            finally { if (e_4) throw e_4.error; }
        }
        var drawPanes = this._chart.getDrawPanes();
        var pane = null;
        try {
            for (var drawPanes_1 = __values(drawPanes), drawPanes_1_1 = drawPanes_1.next(); !drawPanes_1_1.done; drawPanes_1_1 = drawPanes_1.next()) {
                var p = drawPanes_1_1.value;
                var bounding = p.getBounding();
                if (x >= bounding.left && x <= bounding.left + bounding.width && y >= bounding.top && y <= bounding.top + bounding.height) {
                    pane = p;
                    break;
                }
            }
        }
        catch (e_5_1) { e_5 = { error: e_5_1 }; }
        finally {
            try {
                if (drawPanes_1_1 && !drawPanes_1_1.done && (_b = drawPanes_1.return)) _b.call(drawPanes_1);
            }
            finally { if (e_5) throw e_5.error; }
        }
        var widget = null;
        if (pane !== null) {
            if (!isValid(widget)) {
                var mainWidget = pane.getMainWidget();
                var mainBounding = mainWidget.getBounding();
                if (x >= mainBounding.left && x <= mainBounding.left + mainBounding.width && y >= mainBounding.top && y <= mainBounding.top + mainBounding.height) {
                    widget = mainWidget;
                }
            }
            if (!isValid(widget)) {
                try {
                    for (var _d = __values(pane.getYAxisWidgets()), _e = _d.next(); !_e.done; _e = _d.next()) {
                        var yAxisWidget = _e.value;
                        var yAxisBounding = yAxisWidget.getBounding();
                        if (x >= yAxisBounding.left && x <= yAxisBounding.left + yAxisBounding.width && y >= yAxisBounding.top && y <= yAxisBounding.top + yAxisBounding.height) {
                            widget = yAxisWidget;
                            break;
                        }
                    }
                }
                catch (e_6_1) { e_6 = { error: e_6_1 }; }
                finally {
                    try {
                        if (_e && !_e.done && (_c = _d.return)) _c.call(_d);
                    }
                    finally { if (e_6) throw e_6.error; }
                }
            }
        }
        return { pane: pane, widget: widget };
    };
    Event.prototype._makeWidgetEvent = function (event, widget) {
        var _a, _b, _c;
        var bounding = (_a = widget === null || widget === void 0 ? void 0 : widget.getBounding()) !== null && _a !== void 0 ? _a : null;
        return __assign(__assign({}, event), { x: event.x - ((_b = bounding === null || bounding === void 0 ? void 0 : bounding.left) !== null && _b !== void 0 ? _b : 0), y: event.y - ((_c = bounding === null || bounding === void 0 ? void 0 : bounding.top) !== null && _c !== void 0 ? _c : 0) });
    };
    Event.prototype.destroy = function () {
        var _a;
        (_a = this._flingScrollAnimation) === null || _a === void 0 ? void 0 : _a.cancel();
        this._flingScrollAnimation = null;
        document.removeEventListener('keydown', this._boundKeyBoardDownEvent);
        this._event.destroy();
    };
    return Event;
}());

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
function eachFigures(indicator, dataIndex, barSpace, defaultStyles, eachFigureCallback) {
    var result = indicator.result;
    var figures = indicator.figures;
    var styles = indicator.styles;
    var textStyles = formatValue(styles, 'texts', defaultStyles.texts);
    var textStyleCount = textStyles.length;
    var circleStyles = formatValue(styles, 'circles', defaultStyles.circles);
    var circleStyleCount = circleStyles.length;
    var barStyles = formatValue(styles, 'bars', defaultStyles.bars);
    var barStyleCount = barStyles.length;
    var lineStyles = formatValue(styles, 'lines', defaultStyles.lines);
    var lineStyleCount = lineStyles.length;
    var textCount = 0;
    var circleCount = 0;
    var barCount = 0;
    var lineCount = 0;
    var defaultFigureStyles;
    var figureIndex = 0;
    figures.forEach(function (figure) {
        var _a;
        switch (figure.type) {
            case 'text': {
                figureIndex = textCount;
                defaultFigureStyles = textStyles[textCount % textStyleCount];
                textCount++;
                break;
            }
            case 'circle': {
                figureIndex = circleCount;
                var styles_1 = circleStyles[circleCount % circleStyleCount];
                defaultFigureStyles = __assign(__assign({}, styles_1), { color: styles_1.noChangeColor });
                circleCount++;
                break;
            }
            case 'bar': {
                figureIndex = barCount;
                var styles_2 = barStyles[barCount % barStyleCount];
                defaultFigureStyles = __assign(__assign({}, styles_2), { color: styles_2.noChangeColor });
                barCount++;
                break;
            }
            case 'line': {
                figureIndex = lineCount;
                defaultFigureStyles = lineStyles[lineCount % lineStyleCount];
                lineCount++;
                break;
            }
        }
        if (isValid(figure.type)) {
            var ss = (_a = figure.styles) === null || _a === void 0 ? void 0 : _a.call(figure, {
                data: {
                    prev: result[dataIndex - 1],
                    current: result[dataIndex],
                    next: result[dataIndex + 1]
                },
                indicator: indicator,
                barSpace: barSpace,
                defaultStyles: defaultStyles
            });
            eachFigureCallback(figure, __assign(__assign({}, defaultFigureStyles), ss), figureIndex);
        }
    });
}
var IndicatorImp = /** @class */ (function () {
    function IndicatorImp(indicator) {
        this.precision = 4;
        this.calcParams = [];
        this.shouldOhlc = false;
        this.shouldFormatBigNumber = false;
        this.visible = true;
        this.zLevel = 0;
        this.series = 'normal';
        this.figures = [];
        this.minValue = null;
        this.maxValue = null;
        this.styles = null;
        this.shouldUpdate = function (prev, current) {
            var calc = JSON.stringify(prev.calcParams) !== JSON.stringify(current.calcParams) || prev.figures !== current.figures || prev.calc !== current.calc;
            var draw = calc ||
                prev.shortName !== current.shortName ||
                prev.paneId !== current.paneId ||
                prev.yAxisId !== current.yAxisId ||
                prev.series !== current.series ||
                prev.minValue !== current.minValue ||
                prev.maxValue !== current.maxValue ||
                prev.precision !== current.precision ||
                prev.shouldOhlc !== current.shouldOhlc ||
                prev.shouldFormatBigNumber !== current.shouldFormatBigNumber ||
                prev.visible !== current.visible ||
                prev.zLevel !== current.zLevel ||
                prev.extendData !== current.extendData ||
                prev.regenerateFigures !== current.regenerateFigures ||
                prev.createTooltipDataSource !== current.createTooltipDataSource ||
                prev.draw !== current.draw;
            return { calc: calc, draw: draw };
        };
        this.calc = function () { return []; };
        this.regenerateFigures = null;
        this.createTooltipDataSource = null;
        this.draw = null;
        this.result = [];
        this._lockSeriesPrecision = false;
        this.override(indicator);
        this._lockSeriesPrecision = false;
    }
    IndicatorImp.prototype.override = function (indicator) {
        var _a, _b;
        var _c = this, result = _c.result; _c._prevIndicator; var currentOthers = __rest(_c, ["result", "_prevIndicator"]);
        this._prevIndicator = __assign(__assign({}, clone(currentOthers)), { result: result });
        var id = indicator.id, name = indicator.name, shortName = indicator.shortName, precision = indicator.precision, styles = indicator.styles, figures = indicator.figures, calcParams = indicator.calcParams, others = __rest(indicator, ["id", "name", "shortName", "precision", "styles", "figures", "calcParams"]);
        if (!isString(this.id) && isString(id)) {
            this.id = id;
        }
        if (!isString(this.name)) {
            this.name = name !== null && name !== void 0 ? name : '';
        }
        this.shortName = (_a = shortName !== null && shortName !== void 0 ? shortName : this.shortName) !== null && _a !== void 0 ? _a : this.name;
        if (isNumber(precision)) {
            this.precision = precision;
            this._lockSeriesPrecision = true;
        }
        if (isValid(styles)) {
            (_b = this.styles) !== null && _b !== void 0 ? _b : (this.styles = {});
            merge(this.styles, styles);
        }
        merge(this, others);
        if (isValid(calcParams)) {
            this.calcParams = calcParams;
            if (isFunction(this.regenerateFigures)) {
                this.figures = this.regenerateFigures(this.calcParams);
            }
        }
        this.figures = figures !== null && figures !== void 0 ? figures : this.figures;
    };
    IndicatorImp.prototype.setSeriesPrecision = function (precision) {
        if (!this._lockSeriesPrecision) {
            this.precision = precision;
        }
    };
    IndicatorImp.prototype.shouldUpdateImp = function () {
        var sort = this._prevIndicator.zLevel !== this.zLevel;
        var result = this.shouldUpdate(this._prevIndicator, this);
        if (isBoolean(result)) {
            return { calc: result, draw: result, sort: sort };
        }
        return __assign(__assign({}, result), { sort: sort });
    };
    IndicatorImp.prototype.calcImp = function (dataList) {
        return __awaiter(this, void 0, void 0, function () {
            var result;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        _b.trys.push([0, 2, , 3]);
                        return [4 /*yield*/, this.calc(dataList, this)];
                    case 1:
                        result = _b.sent();
                        this.result = result;
                        return [2 /*return*/, true];
                    case 2:
                        _b.sent();
                        return [2 /*return*/, false];
                    case 3: return [2 /*return*/];
                }
            });
        });
    };
    IndicatorImp.extend = function (template) {
        var Custom = /** @class */ (function (_super) {
            __extends(Custom, _super);
            function Custom() {
                return _super.call(this, template) || this;
            }
            return Custom;
        }(IndicatorImp));
        return Custom;
    };
    return IndicatorImp;
}());

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
/**
 * average price
 */
var averagePrice = {
    name: 'AVP',
    shortName: 'AVP',
    series: 'price',
    precision: 2,
    figures: [{ key: 'avp', title: 'AVP: ', type: 'line' }],
    calc: function (dataList) {
        var totalTurnover = 0;
        var totalVolume = 0;
        return dataList.map(function (kLineData) {
            var _a, _b;
            var avp = {};
            var turnover = (_a = kLineData.turnover) !== null && _a !== void 0 ? _a : 0;
            var volume = (_b = kLineData.volume) !== null && _b !== void 0 ? _b : 0;
            totalTurnover += turnover;
            totalVolume += volume;
            if (totalVolume !== 0) {
                avp.avp = totalTurnover / totalVolume;
            }
            return avp;
        });
    }
};

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
var awesomeOscillator = {
    name: 'AO',
    shortName: 'AO',
    calcParams: [5, 34],
    figures: [
        {
            key: 'ao',
            title: 'AO: ',
            type: 'bar',
            baseValue: 0,
            styles: function (_a) {
                var _b, _c;
                var data = _a.data, indicator = _a.indicator, defaultStyles = _a.defaultStyles;
                var prev = data.prev, current = data.current;
                var prevAo = (_b = prev === null || prev === void 0 ? void 0 : prev.ao) !== null && _b !== void 0 ? _b : Number.MIN_SAFE_INTEGER;
                var currentAo = (_c = current === null || current === void 0 ? void 0 : current.ao) !== null && _c !== void 0 ? _c : Number.MIN_SAFE_INTEGER;
                var color = '';
                if (currentAo > prevAo) {
                    color = formatValue(indicator.styles, 'bars[0].upColor', defaultStyles.bars[0].upColor);
                }
                else {
                    color = formatValue(indicator.styles, 'bars[0].downColor', defaultStyles.bars[0].downColor);
                }
                var style = currentAo > prevAo ? 'stroke' : 'fill';
                return { color: color, style: style, borderColor: color };
            }
        }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var maxPeriod = Math.max(params[0], params[1]);
        var shortSum = 0;
        var longSum = 0;
        var short = 0;
        var long = 0;
        return dataList.map(function (kLineData, i) {
            var ao = {};
            var middle = (kLineData.low + kLineData.high) / 2;
            shortSum += middle;
            longSum += middle;
            if (i >= params[0] - 1) {
                short = shortSum / params[0];
                var agoKLineData = dataList[i - (params[0] - 1)];
                shortSum -= (agoKLineData.low + agoKLineData.high) / 2;
            }
            if (i >= params[1] - 1) {
                long = longSum / params[1];
                var agoKLineData = dataList[i - (params[1] - 1)];
                longSum -= (agoKLineData.low + agoKLineData.high) / 2;
            }
            if (i >= maxPeriod - 1) {
                ao.ao = short - long;
            }
            return ao;
        });
    }
};

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
/**
 * BIAS
 * 乖离率=[(当日收盘价-N日平均价)/N日平均价]*100%
 */
var bias = {
    name: 'BIAS',
    shortName: 'BIAS',
    calcParams: [6, 12, 24],
    figures: [
        { key: 'bias1', title: 'BIAS6: ', type: 'line' },
        { key: 'bias2', title: 'BIAS12: ', type: 'line' },
        { key: 'bias3', title: 'BIAS24: ', type: 'line' }
    ],
    regenerateFigures: function (params) { return params.map(function (p, i) { return ({ key: "bias".concat(i + 1), title: "BIAS".concat(p, ": "), type: 'line' }); }); },
    calc: function (dataList, indicator) {
        var params = indicator.calcParams, figures = indicator.figures;
        var closeSums = [];
        return dataList.map(function (kLineData, i) {
            var bias = {};
            var close = kLineData.close;
            params.forEach(function (p, index) {
                var _a;
                closeSums[index] = ((_a = closeSums[index]) !== null && _a !== void 0 ? _a : 0) + close;
                if (i >= p - 1) {
                    var mean = closeSums[index] / params[index];
                    bias[figures[index].key] = ((close - mean) / mean) * 100;
                    closeSums[index] -= dataList[i - (p - 1)].close;
                }
            });
            return bias;
        });
    }
};

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
/**
 * 计算布林指标中的标准差
 * @param dataList
 * @param ma
 * @return {number}
 */
function getBollMd(dataList, ma) {
    var dataSize = dataList.length;
    var sum = 0;
    dataList.forEach(function (data) {
        var closeMa = data.close - ma;
        sum += closeMa * closeMa;
    });
    sum = Math.abs(sum);
    return Math.sqrt(sum / dataSize);
}
/**
 * BOLL
 */
var bollingerBands = {
    name: 'BOLL',
    shortName: 'BOLL',
    series: 'price',
    calcParams: [20, 2],
    precision: 2,
    shouldOhlc: true,
    figures: [
        { key: 'up', title: 'UP: ', type: 'line' },
        { key: 'mid', title: 'MID: ', type: 'line' },
        { key: 'dn', title: 'DN: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var p = params[0] - 1;
        var closeSum = 0;
        return dataList.map(function (kLineData, i) {
            var close = kLineData.close;
            var boll = {};
            closeSum += close;
            if (i >= p) {
                boll.mid = closeSum / params[0];
                var md = getBollMd(dataList.slice(i - p, i + 1), boll.mid);
                boll.up = boll.mid + params[1] * md;
                boll.dn = boll.mid - params[1] * md;
                closeSum -= dataList[i - p].close;
            }
            return boll;
        });
    }
};

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
/**
 * BRAR
 * 默认参数是26。
 * 公式N日BR=N日内（H－CY）之和除以N日内（CY－L）之和*100，
 * 其中，H为当日最高价，L为当日最低价，CY为前一交易日的收盘价，N为设定的时间参数。
 * N日AR=(N日内（H－O）之和除以N日内（O－L）之和)*100，
 * 其中，H为当日最高价，L为当日最低价，O为当日开盘价，N为设定的时间参数
 *
 */
var brar = {
    name: 'BRAR',
    shortName: 'BRAR',
    calcParams: [26],
    figures: [
        { key: 'br', title: 'BR: ', type: 'line' },
        { key: 'ar', title: 'AR: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var hcy = 0;
        var cyl = 0;
        var ho = 0;
        var ol = 0;
        return dataList.map(function (kLineData, i) {
            var _a, _b;
            var brar = {};
            var high = kLineData.high;
            var low = kLineData.low;
            var open = kLineData.open;
            var prevClose = ((_a = dataList[i - 1]) !== null && _a !== void 0 ? _a : kLineData).close;
            ho += high - open;
            ol += open - low;
            hcy += high - prevClose;
            cyl += prevClose - low;
            if (i >= params[0] - 1) {
                if (ol !== 0) {
                    brar.ar = (ho / ol) * 100;
                }
                else {
                    brar.ar = 0;
                }
                if (cyl !== 0) {
                    brar.br = (hcy / cyl) * 100;
                }
                else {
                    brar.br = 0;
                }
                var agoKLineData = dataList[i - (params[0] - 1)];
                var agoHigh = agoKLineData.high;
                var agoLow = agoKLineData.low;
                var agoOpen = agoKLineData.open;
                var agoPreClose = ((_b = dataList[i - params[0]]) !== null && _b !== void 0 ? _b : dataList[i - (params[0] - 1)]).close;
                hcy -= agoHigh - agoPreClose;
                cyl -= agoPreClose - agoLow;
                ho -= agoHigh - agoOpen;
                ol -= agoOpen - agoLow;
            }
            return brar;
        });
    }
};

/**
 * 多空指标
 * 公式: BBI = (MA(CLOSE, M) + MA(CLOSE, N) + MA(CLOSE, O) + MA(CLOSE, P)) / 4
 *
 */
var bullAndBearIndex = {
    name: 'BBI',
    shortName: 'BBI',
    series: 'price',
    precision: 2,
    calcParams: [3, 6, 12, 24],
    shouldOhlc: true,
    figures: [{ key: 'bbi', title: 'BBI: ', type: 'line' }],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var maxPeriod = Math.max.apply(Math, __spreadArray([], __read(params), false));
        var closeSums = [];
        var mas = [];
        return dataList.map(function (kLineData, i) {
            var bbi = {};
            var close = kLineData.close;
            params.forEach(function (p, index) {
                var _a;
                closeSums[index] = ((_a = closeSums[index]) !== null && _a !== void 0 ? _a : 0) + close;
                if (i >= p - 1) {
                    mas[index] = closeSums[index] / p;
                    closeSums[index] -= dataList[i - (p - 1)].close;
                }
            });
            if (i >= maxPeriod - 1) {
                var maSum_1 = 0;
                mas.forEach(function (ma) {
                    maSum_1 += ma;
                });
                bbi.bbi = maSum_1 / 4;
            }
            return bbi;
        });
    }
};

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
/**
 * CCI
 * CCI（N日）=（TP－MA）÷MD÷0.015
 * 其中，TP=（最高价+最低价+收盘价）÷3
 * MA=近N日TP价的累计之和÷N
 * MD=近N日TP - 当前MA绝对值的累计之和÷N
 *
 */
var commodityChannelIndex = {
    name: 'CCI',
    shortName: 'CCI',
    calcParams: [20],
    figures: [{ key: 'cci', title: 'CCI: ', type: 'line' }],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var p = params[0] - 1;
        var tpSum = 0;
        var tpList = [];
        return dataList.map(function (kLineData, i) {
            var cci = {};
            var tp = (kLineData.high + kLineData.low + kLineData.close) / 3;
            tpSum += tp;
            tpList.push(tp);
            if (i >= p) {
                var maTp_1 = tpSum / params[0];
                var sliceTpList = tpList.slice(i - p, i + 1);
                var sum_1 = 0;
                sliceTpList.forEach(function (tp) {
                    sum_1 += Math.abs(tp - maTp_1);
                });
                var md = sum_1 / params[0];
                cci.cci = md !== 0 ? (tp - maTp_1) / md / 0.015 : 0;
                var agoTp = (dataList[i - p].high + dataList[i - p].low + dataList[i - p].close) / 3;
                tpSum -= agoTp;
            }
            return cci;
        });
    }
};

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http:*www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * MID:=REF(HIGH+LOW,1)/2;
 * CR:SUM(MAX(0,HIGH-MID),N)/SUM(MAX(0,MID-LOW),N)*100;
 * MA1:REF(MA(CR,M1),M1/2.5+1);
 * MA2:REF(MA(CR,M2),M2/2.5+1);
 * MA3:REF(MA(CR,M3),M3/2.5+1);
 * MA4:REF(MA(CR,M4),M4/2.5+1);
 * MID赋值:(昨日最高价+昨日最低价)/2
 * 输出带状能量线:0和最高价-MID的较大值的N日累和/0和MID-最低价的较大值的N日累和*100
 * 输出MA1:M1(5)/2.5+1日前的CR的M1(5)日简单移动平均
 * 输出MA2:M2(10)/2.5+1日前的CR的M2(10)日简单移动平均
 * 输出MA3:M3(20)/2.5+1日前的CR的M3(20)日简单移动平均
 * 输出MA4:M4/2.5+1日前的CR的M4日简单移动平均
 *
 */
var currentRatio = {
    name: 'CR',
    shortName: 'CR',
    calcParams: [26, 10, 20, 40, 60],
    figures: [
        { key: 'cr', title: 'CR: ', type: 'line' },
        { key: 'ma1', title: 'MA1: ', type: 'line' },
        { key: 'ma2', title: 'MA2: ', type: 'line' },
        { key: 'ma3', title: 'MA3: ', type: 'line' },
        { key: 'ma4', title: 'MA4: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var ma1ForwardPeriod = Math.ceil(params[1] / 2.5 + 1);
        var ma2ForwardPeriod = Math.ceil(params[2] / 2.5 + 1);
        var ma3ForwardPeriod = Math.ceil(params[3] / 2.5 + 1);
        var ma4ForwardPeriod = Math.ceil(params[4] / 2.5 + 1);
        var ma1Sum = 0;
        var ma1List = [];
        var ma2Sum = 0;
        var ma2List = [];
        var ma3Sum = 0;
        var ma3List = [];
        var ma4Sum = 0;
        var ma4List = [];
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a, _b, _c, _d, _e;
            var cr = {};
            var prevData = (_a = dataList[i - 1]) !== null && _a !== void 0 ? _a : kLineData;
            var prevMid = (prevData.high + prevData.low) / 2;
            var highSubPreMid = Math.max(0, kLineData.high - prevMid);
            var preMidSubLow = Math.max(0, prevMid - kLineData.low);
            if (i >= params[0] - 1) {
                if (preMidSubLow !== 0) {
                    cr.cr = (highSubPreMid / preMidSubLow) * 100;
                }
                else {
                    cr.cr = 0;
                }
                ma1Sum += cr.cr;
                ma2Sum += cr.cr;
                ma3Sum += cr.cr;
                ma4Sum += cr.cr;
                if (i >= params[0] + params[1] - 2) {
                    ma1List.push(ma1Sum / params[1]);
                    if (i >= params[0] + params[1] + ma1ForwardPeriod - 3) {
                        cr.ma1 = ma1List[ma1List.length - 1 - ma1ForwardPeriod];
                    }
                    ma1Sum -= (_b = result[i - (params[1] - 1)].cr) !== null && _b !== void 0 ? _b : 0;
                }
                if (i >= params[0] + params[2] - 2) {
                    ma2List.push(ma2Sum / params[2]);
                    if (i >= params[0] + params[2] + ma2ForwardPeriod - 3) {
                        cr.ma2 = ma2List[ma2List.length - 1 - ma2ForwardPeriod];
                    }
                    ma2Sum -= (_c = result[i - (params[2] - 1)].cr) !== null && _c !== void 0 ? _c : 0;
                }
                if (i >= params[0] + params[3] - 2) {
                    ma3List.push(ma3Sum / params[3]);
                    if (i >= params[0] + params[3] + ma3ForwardPeriod - 3) {
                        cr.ma3 = ma3List[ma3List.length - 1 - ma3ForwardPeriod];
                    }
                    ma3Sum -= (_d = result[i - (params[3] - 1)].cr) !== null && _d !== void 0 ? _d : 0;
                }
                if (i >= params[0] + params[4] - 2) {
                    ma4List.push(ma4Sum / params[4]);
                    if (i >= params[0] + params[4] + ma4ForwardPeriod - 3) {
                        cr.ma4 = ma4List[ma4List.length - 1 - ma4ForwardPeriod];
                    }
                    ma4Sum -= (_e = result[i - (params[4] - 1)].cr) !== null && _e !== void 0 ? _e : 0;
                }
            }
            result.push(cr);
        });
        return result;
    }
};

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
/**
 * DMA
 * 公式：DIF:MA(CLOSE,N1)-MA(CLOSE,N2);DIFMA:MA(DIF,M)
 */
var differentOfMovingAverage = {
    name: 'DMA',
    shortName: 'DMA',
    calcParams: [10, 50, 10],
    figures: [
        { key: 'dma', title: 'DMA: ', type: 'line' },
        { key: 'ama', title: 'AMA: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var maxPeriod = Math.max(params[0], params[1]);
        var closeSum1 = 0;
        var closeSum2 = 0;
        var dmaSum = 0;
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a;
            var dma = {};
            var close = kLineData.close;
            closeSum1 += close;
            closeSum2 += close;
            var ma1 = 0;
            var ma2 = 0;
            if (i >= params[0] - 1) {
                ma1 = closeSum1 / params[0];
                closeSum1 -= dataList[i - (params[0] - 1)].close;
            }
            if (i >= params[1] - 1) {
                ma2 = closeSum2 / params[1];
                closeSum2 -= dataList[i - (params[1] - 1)].close;
            }
            if (i >= maxPeriod - 1) {
                var dif = ma1 - ma2;
                dma.dma = dif;
                dmaSum += dif;
                if (i >= maxPeriod + params[2] - 2) {
                    dma.ama = dmaSum / params[2];
                    dmaSum -= (_a = result[i - (params[2] - 1)].dma) !== null && _a !== void 0 ? _a : 0;
                }
            }
            result.push(dma);
        });
        return result;
    }
};

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
/**
 * DMI
 *
 * MTR:=EXPMEMA(MAX(MAX(HIGH-LOW,ABS(HIGH-REF(CLOSE,1))),ABS(REF(CLOSE,1)-LOW)),N)
 * HD :=HIGH-REF(HIGH,1);
 * LD :=REF(LOW,1)-LOW;
 * DMP:=EXPMEMA(IF(HD>0&&HD>LD,HD,0),N);
 * DMM:=EXPMEMA(IF(LD>0&&LD>HD,LD,0),N);
 *
 * PDI: DMP*100/MTR;
 * MDI: DMM*100/MTR;
 * ADX: EXPMEMA(ABS(MDI-PDI)/(MDI+PDI)*100,MM);
 * ADXR:EXPMEMA(ADX,MM);
 * 公式含义：
 * MTR赋值:最高价-最低价和最高价-昨收的绝对值的较大值和昨收-最低价的绝对值的较大值的N日指数平滑移动平均
 * HD赋值:最高价-昨日最高价
 * LD赋值:昨日最低价-最低价
 * DMP赋值:如果HD>0并且HD>LD,返回HD,否则返回0的N日指数平滑移动平均
 * DMM赋值:如果LD>0并且LD>HD,返回LD,否则返回0的N日指数平滑移动平均
 * 输出PDI:DMP*100/MTR
 * 输出MDI:DMM*100/MTR
 * 输出ADX:MDI-PDI的绝对值/(MDI+PDI)*100的MM日指数平滑移动平均
 * 输出ADXR:ADX的MM日指数平滑移动平均
 *
 */
var directionalMovementIndex = {
    name: 'DMI',
    shortName: 'DMI',
    calcParams: [14, 6],
    figures: [
        { key: 'pdi', title: 'PDI: ', type: 'line' },
        { key: 'mdi', title: 'MDI: ', type: 'line' },
        { key: 'adx', title: 'ADX: ', type: 'line' },
        { key: 'adxr', title: 'ADXR: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var trSum = 0;
        var hSum = 0;
        var lSum = 0;
        var mtr = 0;
        var dmp = 0;
        var dmm = 0;
        var dxSum = 0;
        var adx = 0;
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a, _b;
            var dmi = {};
            var prevKLineData = (_a = dataList[i - 1]) !== null && _a !== void 0 ? _a : kLineData;
            var preClose = prevKLineData.close;
            var high = kLineData.high;
            var low = kLineData.low;
            var hl = high - low;
            var hcy = Math.abs(high - preClose);
            var lcy = Math.abs(preClose - low);
            var hhy = high - prevKLineData.high;
            var lyl = prevKLineData.low - low;
            var tr = Math.max(Math.max(hl, hcy), lcy);
            var h = hhy > 0 && hhy > lyl ? hhy : 0;
            var l = lyl > 0 && lyl > hhy ? lyl : 0;
            trSum += tr;
            hSum += h;
            lSum += l;
            if (i >= params[0] - 1) {
                if (i > params[0] - 1) {
                    mtr = mtr - mtr / params[0] + tr;
                    dmp = dmp - dmp / params[0] + h;
                    dmm = dmm - dmm / params[0] + l;
                }
                else {
                    mtr = trSum;
                    dmp = hSum;
                    dmm = lSum;
                }
                var pdi = 0;
                var mdi = 0;
                if (mtr !== 0) {
                    pdi = (dmp * 100) / mtr;
                    mdi = (dmm * 100) / mtr;
                }
                dmi.pdi = pdi;
                dmi.mdi = mdi;
                var dx = 0;
                if (mdi + pdi !== 0) {
                    dx = (Math.abs(mdi - pdi) / (mdi + pdi)) * 100;
                }
                dxSum += dx;
                if (i >= params[0] * 2 - 2) {
                    if (i > params[0] * 2 - 2) {
                        adx = (adx * (params[0] - 1) + dx) / params[0];
                    }
                    else {
                        adx = dxSum / params[0];
                    }
                    dmi.adx = adx;
                    if (i >= params[0] * 2 + params[1] - 3) {
                        dmi.adxr = (((_b = result[i - (params[1] - 1)].adx) !== null && _b !== void 0 ? _b : 0) + adx) / 2;
                    }
                }
            }
            result.push(dmi);
        });
        return result;
    }
};

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
/**
 *
 * EMV 简易波动指标
 * 公式：
 * A=（今日最高+今日最低）/2
 * B=（前日最高+前日最低）/2
 * C=今日最高-今日最低
 * EM=（A-B）*C/今日成交额
 * EMV=N日内EM的累和
 * MAEMV=EMV的M日的简单移动平均
 *
 */
var easeOfMovementValue = {
    name: 'EMV',
    shortName: 'EMV',
    calcParams: [14, 9],
    figures: [
        { key: 'emv', title: 'EMV: ', type: 'line' },
        { key: 'maEmv', title: 'MAEMV: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var emvValueSum = 0;
        var emvValueList = [];
        return dataList.map(function (kLineData, i) {
            var _a;
            var emv = {};
            if (i > 0) {
                var prevKLineData = dataList[i - 1];
                var high = kLineData.high;
                var low = kLineData.low;
                var volume = (_a = kLineData.volume) !== null && _a !== void 0 ? _a : 0;
                var distanceMoved = (high + low) / 2 - (prevKLineData.high + prevKLineData.low) / 2;
                if (volume === 0 || high - low === 0) {
                    emv.emv = 0;
                }
                else {
                    var ratio = volume / 100000000 / (high - low);
                    emv.emv = distanceMoved / ratio;
                }
                emvValueSum += emv.emv;
                emvValueList.push(emv.emv);
                if (i >= params[0]) {
                    emv.maEmv = emvValueSum / params[0];
                    emvValueSum -= emvValueList[i - params[0]];
                }
            }
            return emv;
        });
    }
};

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
/**
 * EMA 指数移动平均
 */
var exponentialMovingAverage = {
    name: 'EMA',
    shortName: 'EMA',
    series: 'price',
    calcParams: [6, 12, 20],
    precision: 2,
    shouldOhlc: true,
    figures: [
        { key: 'ema1', title: 'EMA6: ', type: 'line' },
        { key: 'ema2', title: 'EMA12: ', type: 'line' },
        { key: 'ema3', title: 'EMA20: ', type: 'line' }
    ],
    regenerateFigures: function (params) { return params.map(function (p, i) { return ({ key: "ema".concat(i + 1), title: "EMA".concat(p, ": "), type: 'line' }); }); },
    calc: function (dataList, indicator) {
        var params = indicator.calcParams, figures = indicator.figures;
        var closeSum = 0;
        var emaValues = [];
        return dataList.map(function (kLineData, i) {
            var ema = {};
            var close = kLineData.close;
            closeSum += close;
            params.forEach(function (p, index) {
                if (i >= p - 1) {
                    if (i > p - 1) {
                        emaValues[index] = (2 * close + (p - 1) * emaValues[index]) / (p + 1);
                    }
                    else {
                        emaValues[index] = closeSum / p;
                    }
                    ema[figures[index].key] = emaValues[index];
                }
            });
            return ema;
        });
    }
};

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
/**
 * mtm
 * 公式 MTM（N日）=C－CN
 */
var momentum = {
    name: 'MTM',
    shortName: 'MTM',
    calcParams: [12, 6],
    figures: [
        { key: 'mtm', title: 'MTM: ', type: 'line' },
        { key: 'maMtm', title: 'MAMTM: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var mtmSum = 0;
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a;
            var mtm = {};
            if (i >= params[0]) {
                var close_1 = kLineData.close;
                var agoClose = dataList[i - params[0]].close;
                mtm.mtm = close_1 - agoClose;
                mtmSum += mtm.mtm;
                if (i >= params[0] + params[1] - 1) {
                    mtm.maMtm = mtmSum / params[1];
                    mtmSum -= (_a = result[i - (params[1] - 1)].mtm) !== null && _a !== void 0 ? _a : 0;
                }
            }
            result.push(mtm);
        });
        return result;
    }
};

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
/**
 * MA 移动平均
 */
var movingAverage = {
    name: 'MA',
    shortName: 'MA',
    series: 'price',
    calcParams: [5, 10, 30, 60],
    precision: 2,
    shouldOhlc: true,
    figures: [
        { key: 'ma1', title: 'MA5: ', type: 'line' },
        { key: 'ma2', title: 'MA10: ', type: 'line' },
        { key: 'ma3', title: 'MA30: ', type: 'line' },
        { key: 'ma4', title: 'MA60: ', type: 'line' }
    ],
    regenerateFigures: function (params) { return params.map(function (p, i) { return ({ key: "ma".concat(i + 1), title: "MA".concat(p, ": "), type: 'line' }); }); },
    calc: function (dataList, indicator) {
        var params = indicator.calcParams, figures = indicator.figures;
        var closeSums = [];
        return dataList.map(function (kLineData, i) {
            var ma = {};
            var close = kLineData.close;
            params.forEach(function (p, index) {
                var _a;
                closeSums[index] = ((_a = closeSums[index]) !== null && _a !== void 0 ? _a : 0) + close;
                if (i >= p - 1) {
                    ma[figures[index].key] = closeSums[index] / p;
                    closeSums[index] -= dataList[i - (p - 1)].close;
                }
            });
            return ma;
        });
    }
};

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
/**
 * MACD：参数快线移动平均、慢线移动平均、移动平均，
 * 默认参数值12、26、9。
 * 公式：⒈首先分别计算出收盘价12日指数平滑移动平均线与26日指数平滑移动平均线，分别记为EMA(12）与EMA(26）。
 * ⒉求这两条指数平滑移动平均线的差，即：DIFF = EMA(SHORT) － EMA(LONG)。
 * ⒊再计算DIFF的M日的平均的指数平滑移动平均线，记为DEA。
 * ⒋最后用DIFF减DEA，得MACD。MACD通常绘制成围绕零轴线波动的柱形图。MACD柱状大于0涨颜色，小于0跌颜色。
 */
var movingAverageConvergenceDivergence = {
    name: 'MACD',
    shortName: 'MACD',
    calcParams: [12, 26, 9],
    figures: [
        { key: 'dif', title: 'DIF: ', type: 'line' },
        { key: 'dea', title: 'DEA: ', type: 'line' },
        {
            key: 'macd',
            title: 'MACD: ',
            type: 'bar',
            baseValue: 0,
            styles: function (_a) {
                var _b, _c;
                var data = _a.data, indicator = _a.indicator, defaultStyles = _a.defaultStyles;
                var prev = data.prev, current = data.current;
                var prevMacd = (_b = prev === null || prev === void 0 ? void 0 : prev.macd) !== null && _b !== void 0 ? _b : Number.MIN_SAFE_INTEGER;
                var currentMacd = (_c = current === null || current === void 0 ? void 0 : current.macd) !== null && _c !== void 0 ? _c : Number.MIN_SAFE_INTEGER;
                var color = '';
                if (currentMacd > 0) {
                    color = formatValue(indicator.styles, 'bars[0].upColor', defaultStyles.bars[0].upColor);
                }
                else if (currentMacd < 0) {
                    color = formatValue(indicator.styles, 'bars[0].downColor', defaultStyles.bars[0].downColor);
                }
                else {
                    color = formatValue(indicator.styles, 'bars[0].noChangeColor', defaultStyles.bars[0].noChangeColor);
                }
                var style = prevMacd < currentMacd ? 'stroke' : 'fill';
                return { style: style, color: color, borderColor: color };
            }
        }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var closeSum = 0;
        var emaShort = 0;
        var emaLong = 0;
        var dif = 0;
        var difSum = 0;
        var dea = 0;
        var maxPeriod = Math.max(params[0], params[1]);
        return dataList.map(function (kLineData, i) {
            var macd = {};
            var close = kLineData.close;
            closeSum += close;
            if (i >= params[0] - 1) {
                if (i > params[0] - 1) {
                    emaShort = (2 * close + (params[0] - 1) * emaShort) / (params[0] + 1);
                }
                else {
                    emaShort = closeSum / params[0];
                }
            }
            if (i >= params[1] - 1) {
                if (i > params[1] - 1) {
                    emaLong = (2 * close + (params[1] - 1) * emaLong) / (params[1] + 1);
                }
                else {
                    emaLong = closeSum / params[1];
                }
            }
            if (i >= maxPeriod - 1) {
                dif = emaShort - emaLong;
                macd.dif = dif;
                difSum += dif;
                if (i >= maxPeriod + params[2] - 2) {
                    if (i > maxPeriod + params[2] - 2) {
                        dea = (dif * 2 + dea * (params[2] - 1)) / (params[2] + 1);
                    }
                    else {
                        dea = difSum / params[2];
                    }
                    macd.macd = (dif - dea) * 2;
                    macd.dea = dea;
                }
            }
            return macd;
        });
    }
};

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
/**
 * OBV
 * OBV = REF(OBV) + sign * V
 */
var onBalanceVolume = {
    name: 'OBV',
    shortName: 'OBV',
    calcParams: [30],
    figures: [
        { key: 'obv', title: 'OBV: ', type: 'line' },
        { key: 'maObv', title: 'MAOBV: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var obvSum = 0;
        var oldObv = 0;
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a, _b, _c, _d;
            var prevKLineData = (_a = dataList[i - 1]) !== null && _a !== void 0 ? _a : kLineData;
            if (kLineData.close < prevKLineData.close) {
                oldObv -= (_b = kLineData.volume) !== null && _b !== void 0 ? _b : 0;
            }
            else if (kLineData.close > prevKLineData.close) {
                oldObv += (_c = kLineData.volume) !== null && _c !== void 0 ? _c : 0;
            }
            var obv = { obv: oldObv };
            obvSum += oldObv;
            if (i >= params[0] - 1) {
                obv.maObv = obvSum / params[0];
                obvSum -= (_d = result[i - (params[0] - 1)].obv) !== null && _d !== void 0 ? _d : 0;
            }
            result.push(obv);
        });
        return result;
    }
};

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
/**
 * 价量趋势指标
 * 公式:
 * X = (CLOSE - REF(CLOSE, 1)) / REF(CLOSE, 1) * VOLUME
 * PVT = SUM(X)
 *
 */
var priceAndVolumeTrend = {
    name: 'PVT',
    shortName: 'PVT',
    figures: [{ key: 'pvt', title: 'PVT: ', type: 'line' }],
    calc: function (dataList) {
        var sum = 0;
        return dataList.map(function (kLineData, i) {
            var _a, _b;
            var pvt = {};
            var close = kLineData.close;
            var volume = (_a = kLineData.volume) !== null && _a !== void 0 ? _a : 1;
            var prevClose = ((_b = dataList[i - 1]) !== null && _b !== void 0 ? _b : kLineData).close;
            var x = 0;
            if (prevClose !== 0) {
                x = ((close - prevClose) / prevClose) * volume;
            }
            sum += x;
            pvt.pvt = sum;
            return pvt;
        });
    }
};

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
/**
 * PSY
 * 公式：PSY=N日内的上涨天数/N×100%。
 */
var psychologicalLine = {
    name: 'PSY',
    shortName: 'PSY',
    calcParams: [12, 6],
    figures: [
        { key: 'psy', title: 'PSY: ', type: 'line' },
        { key: 'maPsy', title: 'MAPSY: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var upCount = 0;
        var psySum = 0;
        var upList = [];
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a, _b;
            var psy = {};
            var prevClose = ((_a = dataList[i - 1]) !== null && _a !== void 0 ? _a : kLineData).close;
            var upFlag = kLineData.close - prevClose > 0 ? 1 : 0;
            upList.push(upFlag);
            upCount += upFlag;
            if (i >= params[0] - 1) {
                psy.psy = (upCount / params[0]) * 100;
                psySum += psy.psy;
                if (i >= params[0] + params[1] - 2) {
                    psy.maPsy = psySum / params[1];
                    psySum -= (_b = result[i - (params[1] - 1)].psy) !== null && _b !== void 0 ? _b : 0;
                }
                upCount -= upList[i - (params[0] - 1)];
            }
            result.push(psy);
        });
        return result;
    }
};

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
/**
 * 变动率指标
 * 公式：ROC = (CLOSE - REF(CLOSE, N)) / REF(CLOSE, N)
 */
var rateOfChange = {
    name: 'ROC',
    shortName: 'ROC',
    calcParams: [12, 6],
    figures: [
        { key: 'roc', title: 'ROC: ', type: 'line' },
        { key: 'maRoc', title: 'MAROC: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var result = [];
        var rocSum = 0;
        dataList.forEach(function (kLineData, i) {
            var _a;
            var roc = {};
            if (i >= params[0]) {
                var close_1 = kLineData.close;
                var agoClose = dataList[i - params[0]].close;
                if (agoClose !== 0) {
                    roc.roc = ((close_1 - agoClose) / agoClose) * 100;
                }
                else {
                    roc.roc = 0;
                }
                rocSum += roc.roc;
                if (i >= params[0] + params[1] - 1) {
                    roc.maRoc = rocSum / params[1];
                    rocSum -= (_a = result[i - (params[1] - 1)].roc) !== null && _a !== void 0 ? _a : 0;
                }
            }
            result.push(roc);
        });
        return result;
    }
};

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
/**
 * RSI
 * RSI = 100 - 100 / (1 + RMA(MAX(CHANGE(CLOSE), 0), N) / RMA(MAX(-CHANGE(CLOSE), 0), N))
 */
var relativeStrengthIndex = {
    name: 'RSI',
    shortName: 'RSI',
    calcParams: [6, 12, 24],
    figures: [
        { key: 'rsi1', title: 'RSI1: ', type: 'line' },
        { key: 'rsi2', title: 'RSI2: ', type: 'line' },
        { key: 'rsi3', title: 'RSI3: ', type: 'line' }
    ],
    regenerateFigures: function (params) {
        return params.map(function (_, index) {
            var num = index + 1;
            return { key: "rsi".concat(num), title: "RSI".concat(num, ": "), type: 'line' };
        });
    },
    calc: function (dataList, indicator) {
        var params = indicator.calcParams, figures = indicator.figures;
        var gainSums = [];
        var lossSums = [];
        var avgGains = [];
        var avgLosses = [];
        return dataList.map(function (kLineData, i) {
            var rsi = {};
            var change = i === 0 ? 0 : kLineData.close - dataList[i - 1].close;
            var gain = Math.max(change, 0);
            var loss = Math.max(-change, 0);
            params.forEach(function (p, index) {
                var _a, _b;
                gainSums[index] = ((_a = gainSums[index]) !== null && _a !== void 0 ? _a : 0) + gain;
                lossSums[index] = ((_b = lossSums[index]) !== null && _b !== void 0 ? _b : 0) + loss;
                if (i < p) {
                    return;
                }
                if (avgGains[index] === undefined || avgLosses[index] === undefined) {
                    avgGains[index] = gainSums[index] / p;
                    avgLosses[index] = lossSums[index] / p;
                }
                else {
                    avgGains[index] = (avgGains[index] * (p - 1) + gain) / p;
                    avgLosses[index] = (avgLosses[index] * (p - 1) + loss) / p;
                }
                if (avgLosses[index] === 0) {
                    rsi[figures[index].key] = 100;
                }
                else if (avgGains[index] === 0) {
                    rsi[figures[index].key] = 0;
                }
                else {
                    rsi[figures[index].key] = 100 - 100 / (1 + avgGains[index] / avgLosses[index]);
                }
            });
            return rsi;
        });
    }
};

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
/**
 * sma
 */
var simpleMovingAverage = {
    name: 'SMA',
    shortName: 'SMA',
    series: 'price',
    calcParams: [12, 2],
    precision: 2,
    figures: [{ key: 'sma', title: 'SMA: ', type: 'line' }],
    shouldOhlc: true,
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var closeSum = 0;
        var smaValue = 0;
        return dataList.map(function (kLineData, i) {
            var sma = {};
            var close = kLineData.close;
            closeSum += close;
            if (i >= params[0] - 1) {
                if (i > params[0] - 1) {
                    smaValue = (close * params[1] + smaValue * (params[0] - params[1] + 1)) / (params[0] + 1);
                }
                else {
                    smaValue = closeSum / params[0];
                }
                sma.sma = smaValue;
            }
            return sma;
        });
    }
};

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
/**
 * KDJ
 *
 * 当日K值=2/3×前一日K值+1/3×当日RSV
 * 当日D值=2/3×前一日D值+1/3×当日K值
 * 若无前一日K 值与D值，则可分别用50来代替。
 * J值=3*当日K值-2*当日D值
 */
var stoch = {
    name: 'KDJ',
    shortName: 'KDJ',
    calcParams: [9, 3, 3],
    figures: [
        { key: 'k', title: 'K: ', type: 'line' },
        { key: 'd', title: 'D: ', type: 'line' },
        { key: 'j', title: 'J: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a, _b, _c, _d;
            var kdj = {};
            var close = kLineData.close;
            if (i >= params[0] - 1) {
                var lhn = getMaxMin(dataList.slice(i - (params[0] - 1), i + 1), 'high', 'low');
                var hn = lhn[0];
                var ln = lhn[1];
                var hnSubLn = hn - ln;
                var rsv = ((close - ln) / (hnSubLn === 0 ? 1 : hnSubLn)) * 100;
                kdj.k = ((params[1] - 1) * ((_b = (_a = result[i - 1]) === null || _a === void 0 ? void 0 : _a.k) !== null && _b !== void 0 ? _b : 50) + rsv) / params[1];
                kdj.d = ((params[2] - 1) * ((_d = (_c = result[i - 1]) === null || _c === void 0 ? void 0 : _c.d) !== null && _d !== void 0 ? _d : 50) + kdj.k) / params[2];
                kdj.j = 3.0 * kdj.k - 2.0 * kdj.d;
            }
            result.push(kdj);
        });
        return result;
    }
};

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
var stopAndReverse = {
    name: 'SAR',
    shortName: 'SAR',
    series: 'price',
    calcParams: [2, 2, 20],
    precision: 2,
    shouldOhlc: true,
    figures: [
        {
            key: 'sar',
            title: 'SAR: ',
            type: 'circle',
            styles: function (_a) {
                var _b, _c, _d;
                var data = _a.data, indicator = _a.indicator, defaultStyles = _a.defaultStyles;
                var current = data.current;
                var sar = (_b = current === null || current === void 0 ? void 0 : current.sar) !== null && _b !== void 0 ? _b : Number.MIN_SAFE_INTEGER;
                var halfHL = (((_c = current === null || current === void 0 ? void 0 : current.high) !== null && _c !== void 0 ? _c : 0) + ((_d = current === null || current === void 0 ? void 0 : current.low) !== null && _d !== void 0 ? _d : 0)) / 2;
                var color = sar < halfHL ? formatValue(indicator.styles, 'circles[0].upColor', defaultStyles.circles[0].upColor) : formatValue(indicator.styles, 'circles[0].downColor', defaultStyles.circles[0].downColor);
                return { color: color };
            }
        }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var startAf = params[0] / 100;
        var step = params[1] / 100;
        var maxAf = params[2] / 100;
        // 加速因子
        var af = startAf;
        // 极值
        var ep = -100;
        // 判断是上涨还是下跌  false：下跌
        var isIncreasing = false;
        var sar = 0;
        return dataList.map(function (kLineData, i) {
            // 上一个周期的sar
            var preSar = sar;
            var high = kLineData.high;
            var low = kLineData.low;
            if (isIncreasing) {
                // 上涨
                if (ep === -100 || ep < high) {
                    // 重新初始化值
                    ep = high;
                    af = Math.min(af + step, maxAf);
                }
                sar = preSar + af * (ep - preSar);
                var lowMin = Math.min(dataList[Math.max(1, i) - 1].low, low);
                if (sar > kLineData.low) {
                    sar = ep;
                    // 重新初始化值
                    af = startAf;
                    ep = -100;
                    isIncreasing = !isIncreasing;
                }
                else if (sar > lowMin) {
                    sar = lowMin;
                }
            }
            else {
                if (ep === -100 || ep > low) {
                    // 重新初始化值
                    ep = low;
                    af = Math.min(af + step, maxAf);
                }
                sar = preSar + af * (ep - preSar);
                var highMax = Math.max(dataList[Math.max(1, i) - 1].high, high);
                if (sar < kLineData.high) {
                    sar = ep;
                    // 重新初始化值
                    af = startAf;
                    ep = -100;
                    isIncreasing = !isIncreasing;
                }
                else if (sar < highMax) {
                    sar = highMax;
                }
            }
            return { high: high, low: low, sar: sar };
        });
    }
};

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http:*www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * trix
 *
 * TR=收盘价的N日指数移动平均的N日指数移动平均的N日指数移动平均；
 * TRIX=(TR-昨日TR)/昨日TR*100；
 * MATRIX=TRIX的M日简单移动平均；
 * 默认参数N设为12，默认参数M设为9；
 * 默认参数12、9
 * 公式：MTR:=EMA(EMA(EMA(CLOSE,N),N),N)
 * TRIX:(MTR-REF(MTR,1))/REF(MTR,1)*100;
 * TRMA:MA(TRIX,M)
 *
 */
var tripleExponentiallySmoothedAverage = {
    name: 'TRIX',
    shortName: 'TRIX',
    calcParams: [12, 9],
    figures: [
        { key: 'trix', title: 'TRIX: ', type: 'line' },
        { key: 'maTrix', title: 'MATRIX: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var closeSum = 0;
        var ema1 = 0;
        var ema2 = 0;
        var oldTr = 0;
        var ema1Sum = 0;
        var ema2Sum = 0;
        var trixSum = 0;
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a;
            var trix = {};
            var close = kLineData.close;
            closeSum += close;
            if (i >= params[0] - 1) {
                if (i > params[0] - 1) {
                    ema1 = (2 * close + (params[0] - 1) * ema1) / (params[0] + 1);
                }
                else {
                    ema1 = closeSum / params[0];
                }
                ema1Sum += ema1;
                if (i >= params[0] * 2 - 2) {
                    if (i > params[0] * 2 - 2) {
                        ema2 = (2 * ema1 + (params[0] - 1) * ema2) / (params[0] + 1);
                    }
                    else {
                        ema2 = ema1Sum / params[0];
                    }
                    ema2Sum += ema2;
                    if (i >= params[0] * 3 - 3) {
                        var tr = 0;
                        var trixValue = 0;
                        if (i > params[0] * 3 - 3) {
                            tr = (2 * ema2 + (params[0] - 1) * oldTr) / (params[0] + 1);
                            trixValue = ((tr - oldTr) / oldTr) * 100;
                        }
                        else {
                            tr = ema2Sum / params[0];
                        }
                        oldTr = tr;
                        trix.trix = trixValue;
                        trixSum += trixValue;
                        if (i >= params[0] * 3 + params[1] - 4) {
                            trix.maTrix = trixSum / params[1];
                            trixSum -= (_a = result[i - (params[1] - 1)].trix) !== null && _a !== void 0 ? _a : 0;
                        }
                    }
                }
            }
            result.push(trix);
        });
        return result;
    }
};

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
function getVolumeFigure() {
    return {
        key: 'volume',
        title: 'VOLUME: ',
        type: 'bar',
        baseValue: 0,
        styles: function (_a) {
            var data = _a.data, indicator = _a.indicator, defaultStyles = _a.defaultStyles;
            var current = data.current;
            var color = formatValue(indicator.styles, 'bars[0].noChangeColor', defaultStyles.bars[0].noChangeColor);
            if (isValid(current)) {
                if (current.close > current.open) {
                    color = formatValue(indicator.styles, 'bars[0].upColor', defaultStyles.bars[0].upColor);
                }
                else if (current.close < current.open) {
                    color = formatValue(indicator.styles, 'bars[0].downColor', defaultStyles.bars[0].downColor);
                }
            }
            return { color: color };
        }
    };
}
var volume = {
    name: 'VOL',
    shortName: 'VOL',
    series: 'volume',
    calcParams: [5, 10, 20],
    shouldFormatBigNumber: true,
    precision: 0,
    minValue: 0,
    figures: [{ key: 'ma1', title: 'MA5: ', type: 'line' }, { key: 'ma2', title: 'MA10: ', type: 'line' }, { key: 'ma3', title: 'MA20: ', type: 'line' }, getVolumeFigure()],
    regenerateFigures: function (params) {
        var figures = params.map(function (p, i) { return ({ key: "ma".concat(i + 1), title: "MA".concat(p, ": "), type: 'line' }); });
        figures.push(getVolumeFigure());
        return figures;
    },
    calc: function (dataList, indicator) {
        var params = indicator.calcParams, figures = indicator.figures;
        var volSums = [];
        return dataList.map(function (kLineData, i) {
            var _a;
            var volume = (_a = kLineData.volume) !== null && _a !== void 0 ? _a : 0;
            var vol = { volume: volume, open: kLineData.open, close: kLineData.close };
            params.forEach(function (p, index) {
                var _a, _b;
                volSums[index] = ((_a = volSums[index]) !== null && _a !== void 0 ? _a : 0) + volume;
                if (i >= p - 1) {
                    vol[figures[index].key] = volSums[index] / p;
                    volSums[index] -= (_b = dataList[i - (p - 1)].volume) !== null && _b !== void 0 ? _b : 0;
                }
            });
            return vol;
        });
    }
};

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
/**
 * VR
 * VR=（UVS+1/2PVS）/（DVS+1/2PVS）
 * 24天以来凡是股价上涨那一天的成交量都称为AV，将24天内的AV总和相加后称为UVS
 * 24天以来凡是股价下跌那一天的成交量都称为BV，将24天内的BV总和相加后称为DVS
 * 24天以来凡是股价不涨不跌，则那一天的成交量都称为CV，将24天内的CV总和相加后称为PVS
 *
 */
var volumeRatio = {
    name: 'VR',
    shortName: 'VR',
    calcParams: [26, 6],
    figures: [
        { key: 'vr', title: 'VR: ', type: 'line' },
        { key: 'maVr', title: 'MAVR: ', type: 'line' }
    ],
    calc: function (dataList, indicator) {
        var params = indicator.calcParams;
        var uvs = 0;
        var dvs = 0;
        var pvs = 0;
        var vrSum = 0;
        var result = [];
        dataList.forEach(function (kLineData, i) {
            var _a, _b, _c, _d, _e;
            var vr = {};
            var close = kLineData.close;
            var preClose = ((_a = dataList[i - 1]) !== null && _a !== void 0 ? _a : kLineData).close;
            var volume = (_b = kLineData.volume) !== null && _b !== void 0 ? _b : 0;
            if (close > preClose) {
                uvs += volume;
            }
            else if (close < preClose) {
                dvs += volume;
            }
            else {
                pvs += volume;
            }
            if (i >= params[0] - 1) {
                var halfPvs = pvs / 2;
                if (dvs + halfPvs === 0) {
                    vr.vr = 0;
                }
                else {
                    vr.vr = ((uvs + halfPvs) / (dvs + halfPvs)) * 100;
                }
                vrSum += vr.vr;
                if (i >= params[0] + params[1] - 2) {
                    vr.maVr = vrSum / params[1];
                    vrSum -= (_c = result[i - (params[1] - 1)].vr) !== null && _c !== void 0 ? _c : 0;
                }
                var agoData = dataList[i - (params[0] - 1)];
                var agoPreData = (_d = dataList[i - params[0]]) !== null && _d !== void 0 ? _d : agoData;
                var agoClose = agoData.close;
                var agoVolume = (_e = agoData.volume) !== null && _e !== void 0 ? _e : 0;
                if (agoClose > agoPreData.close) {
                    uvs -= agoVolume;
                }
                else if (agoClose < agoPreData.close) {
                    dvs -= agoVolume;
                }
                else {
                    pvs -= agoVolume;
                }
            }
            result.push(vr);
        });
        return result;
    }
};

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
/**
 * WR
 * 公式 WR(N) = 100 * [ C - HIGH(N) ] / [ HIGH(N)-LOW(N) ]
 */
var williamsR = {
    name: 'WR',
    shortName: 'WR',
    calcParams: [6, 10, 14],
    figures: [
        { key: 'wr1', title: 'WR1: ', type: 'line' },
        { key: 'wr2', title: 'WR2: ', type: 'line' },
        { key: 'wr3', title: 'WR3: ', type: 'line' }
    ],
    regenerateFigures: function (params) { return params.map(function (_, i) { return ({ key: "wr".concat(i + 1), title: "WR".concat(i + 1, ": "), type: 'line' }); }); },
    calc: function (dataList, indicator) {
        var params = indicator.calcParams, figures = indicator.figures;
        return dataList.map(function (kLineData, i) {
            var wr = {};
            var close = kLineData.close;
            params.forEach(function (param, index) {
                var p = param - 1;
                if (i >= p) {
                    var hln = getMaxMin(dataList.slice(i - p, i + 1), 'high', 'low');
                    var hn = hln[0];
                    var ln = hln[1];
                    var hnSubLn = hn - ln;
                    wr[figures[index].key] = hnSubLn === 0 ? 0 : ((close - hn) / hnSubLn) * 100;
                }
            });
            return wr;
        });
    }
};

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
var indicators = {};
var extensions$2 = [
    averagePrice,
    awesomeOscillator,
    bias,
    bollingerBands,
    brar,
    bullAndBearIndex,
    commodityChannelIndex,
    currentRatio,
    differentOfMovingAverage,
    directionalMovementIndex,
    easeOfMovementValue,
    exponentialMovingAverage,
    momentum,
    movingAverage,
    movingAverageConvergenceDivergence,
    onBalanceVolume,
    priceAndVolumeTrend,
    psychologicalLine,
    rateOfChange,
    relativeStrengthIndex,
    simpleMovingAverage,
    stoch,
    stopAndReverse,
    tripleExponentiallySmoothedAverage,
    volume,
    volumeRatio,
    williamsR
];
// @ts-ignore
extensions$2.forEach(function (indicator) {
    indicators[indicator.name] = IndicatorImp.extend(indicator);
});
function registerIndicator(indicator) {
    // @ts-ignore
    indicators[indicator.name] = IndicatorImp.extend(indicator);
}
function getIndicatorClass(name) {
    var _a;
    return (_a = indicators[name]) !== null && _a !== void 0 ? _a : null;
}
function getSupportedIndicators() {
    return Object.keys(indicators);
}

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
var Eventful = /** @class */ (function () {
    function Eventful() {
        this._children = [];
        this._callbacks = new Map();
    }
    Eventful.prototype.registerEvent = function (name, callback) {
        this._callbacks.set(name, callback);
        return this;
    };
    Eventful.prototype.onEvent = function (name, event) {
        var callback = this._callbacks.get(name);
        if (isValid(callback) && this.checkEventOn(event)) {
            return callback(event);
        }
        return false;
    };
    Eventful.prototype.dispatchEventToChildren = function (name, event) {
        var start = this._children.length - 1;
        if (start > -1) {
            for (var i = start; i > -1; i--) {
                if (this._children[i].dispatchEvent(name, event)) {
                    return true;
                }
            }
        }
        return false;
    };
    Eventful.prototype.dispatchEvent = function (name, event) {
        if (this.dispatchEventToChildren(name, event)) {
            return true;
        }
        return this.onEvent(name, event);
    };
    Eventful.prototype.addChild = function (eventful) {
        this._children.push(eventful);
        return this;
    };
    Eventful.prototype.clear = function () {
        this._children = [];
    };
    return Eventful;
}());

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
var DEVIATION = 2;
var FigureImp = /** @class */ (function (_super) {
    __extends(FigureImp, _super);
    function FigureImp(figure) {
        var _this = _super.call(this) || this;
        _this.attrs = figure.attrs;
        _this.styles = figure.styles;
        return _this;
    }
    FigureImp.prototype.checkEventOn = function (event) {
        return this.checkEventOnImp(event, this.attrs, this.styles);
    };
    FigureImp.prototype.setAttrs = function (attrs) {
        this.attrs = attrs;
        return this;
    };
    FigureImp.prototype.setStyles = function (styles) {
        this.styles = styles;
        return this;
    };
    FigureImp.prototype.draw = function (ctx) {
        this.drawImp(ctx, this.attrs, this.styles);
    };
    FigureImp.extend = function (figure) {
        var Custom = /** @class */ (function (_super) {
            __extends(Custom, _super);
            function Custom() {
                return _super !== null && _super.apply(this, arguments) || this;
            }
            Custom.prototype.checkEventOnImp = function (coordinate, attrs, styles) {
                return figure.checkEventOn(coordinate, attrs, styles);
            };
            Custom.prototype.drawImp = function (ctx, attrs, styles) {
                figure.draw(ctx, attrs, styles);
            };
            return Custom;
        }(FigureImp));
        return Custom;
    };
    return FigureImp;
}(Eventful));

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
function checkCoordinateOnLine(coordinate, attrs) {
    var e_1, _a;
    var lines = [];
    lines = lines.concat(attrs);
    try {
        for (var lines_1 = __values(lines), lines_1_1 = lines_1.next(); !lines_1_1.done; lines_1_1 = lines_1.next()) {
            var line_1 = lines_1_1.value;
            var coordinates = line_1.coordinates;
            if (coordinates.length > 1) {
                for (var i = 1; i < coordinates.length; i++) {
                    var prevCoordinate = coordinates[i - 1];
                    var currentCoordinate = coordinates[i];
                    if (prevCoordinate.x === currentCoordinate.x) {
                        if (Math.abs(prevCoordinate.y - coordinate.y) + Math.abs(currentCoordinate.y - coordinate.y) - Math.abs(prevCoordinate.y - currentCoordinate.y) < DEVIATION + DEVIATION && Math.abs(coordinate.x - prevCoordinate.x) < DEVIATION) {
                            return true;
                        }
                    }
                    else {
                        var kb = getLinearSlopeIntercept(prevCoordinate, currentCoordinate);
                        var y = getLinearYFromSlopeIntercept(kb, coordinate);
                        var yDif = Math.abs(y - coordinate.y);
                        if (Math.abs(prevCoordinate.x - coordinate.x) + Math.abs(currentCoordinate.x - coordinate.x) - Math.abs(prevCoordinate.x - currentCoordinate.x) < DEVIATION + DEVIATION && (yDif * yDif) / (kb[0] * kb[0] + 1) < DEVIATION * DEVIATION) {
                            return true;
                        }
                    }
                }
            }
        }
    }
    catch (e_1_1) { e_1 = { error: e_1_1 }; }
    finally {
        try {
            if (lines_1_1 && !lines_1_1.done && (_a = lines_1.return)) _a.call(lines_1);
        }
        finally { if (e_1) throw e_1.error; }
    }
    return false;
}
function getLinearYFromSlopeIntercept(kb, coordinate) {
    if (kb !== null) {
        return coordinate.x * kb[0] + kb[1];
    }
    return coordinate.y;
}
/**
 * 获取点在两点决定的一次函数上的y值
 * @param coordinate1
 * @param coordinate2
 * @param targetCoordinate
 */
function getLinearYFromCoordinates(coordinate1, coordinate2, targetCoordinate) {
    var kb = getLinearSlopeIntercept(coordinate1, coordinate2);
    return getLinearYFromSlopeIntercept(kb, targetCoordinate);
}
function getLinearSlopeIntercept(coordinate1, coordinate2) {
    var difX = coordinate1.x - coordinate2.x;
    if (difX !== 0) {
        var k = (coordinate1.y - coordinate2.y) / difX;
        var b = coordinate1.y - k * coordinate1.x;
        return [k, b];
    }
    return null;
}
function lineTo(ctx, coordinates, smooth) {
    var length = coordinates.length;
    var smoothParam = isNumber(smooth) ? (smooth > 0 && smooth < 1 ? smooth : 0) : smooth ? 0.5 : 0;
    if (smoothParam > 0 && length > 2) {
        var cpx0 = coordinates[0].x;
        var cpy0 = coordinates[0].y;
        for (var i = 1; i < length - 1; i++) {
            var prevCoordinate = coordinates[i - 1];
            var coordinate = coordinates[i];
            var nextCoordinate = coordinates[i + 1];
            var dx01 = coordinate.x - prevCoordinate.x;
            var dy01 = coordinate.y - prevCoordinate.y;
            var dx12 = nextCoordinate.x - coordinate.x;
            var dy12 = nextCoordinate.y - coordinate.y;
            var dx02 = nextCoordinate.x - prevCoordinate.x;
            var dy02 = nextCoordinate.y - prevCoordinate.y;
            var prevSegmentLength = Math.sqrt(dx01 * dx01 + dy01 * dy01);
            var nextSegmentLength = Math.sqrt(dx12 * dx12 + dy12 * dy12);
            var segmentLengthRatio = nextSegmentLength / (nextSegmentLength + prevSegmentLength);
            var nextCpx = coordinate.x + dx02 * smoothParam * segmentLengthRatio;
            var nextCpy = coordinate.y + dy02 * smoothParam * segmentLengthRatio;
            nextCpx = Math.min(nextCpx, Math.max(nextCoordinate.x, coordinate.x));
            nextCpy = Math.min(nextCpy, Math.max(nextCoordinate.y, coordinate.y));
            nextCpx = Math.max(nextCpx, Math.min(nextCoordinate.x, coordinate.x));
            nextCpy = Math.max(nextCpy, Math.min(nextCoordinate.y, coordinate.y));
            dx02 = nextCpx - coordinate.x;
            dy02 = nextCpy - coordinate.y;
            var cpx1 = coordinate.x - (dx02 * prevSegmentLength) / nextSegmentLength;
            var cpy1 = coordinate.y - (dy02 * prevSegmentLength) / nextSegmentLength;
            cpx1 = Math.min(cpx1, Math.max(prevCoordinate.x, coordinate.x));
            cpy1 = Math.min(cpy1, Math.max(prevCoordinate.y, coordinate.y));
            cpx1 = Math.max(cpx1, Math.min(prevCoordinate.x, coordinate.x));
            cpy1 = Math.max(cpy1, Math.min(prevCoordinate.y, coordinate.y));
            dx02 = coordinate.x - cpx1;
            dy02 = coordinate.y - cpy1;
            nextCpx = coordinate.x + (dx02 * nextSegmentLength) / prevSegmentLength;
            nextCpy = coordinate.y + (dy02 * nextSegmentLength) / prevSegmentLength;
            ctx.bezierCurveTo(cpx0, cpy0, cpx1, cpy1, coordinate.x, coordinate.y);
            cpx0 = nextCpx;
            cpy0 = nextCpy;
        }
        var lastCoordinate = coordinates[length - 1];
        ctx.bezierCurveTo(cpx0, cpy0, lastCoordinate.x, lastCoordinate.y, lastCoordinate.x, lastCoordinate.y);
    }
    else {
        for (var i = 1; i < length; i++) {
            ctx.lineTo(coordinates[i].x, coordinates[i].y);
        }
    }
}
function drawLine(ctx, attrs, styles) {
    var lines = [];
    lines = lines.concat(attrs);
    var _a = styles.style, style = _a === void 0 ? 'solid' : _a, _b = styles.smooth, smooth = _b === void 0 ? false : _b, _c = styles.size, size = _c === void 0 ? 1 : _c, _d = styles.color, color = _d === void 0 ? 'currentColor' : _d, _e = styles.dashedValue, dashedValue = _e === void 0 ? [2, 2] : _e, lineCap = styles.lineCap, lineJoin = styles.lineJoin;
    var isSmooth = isNumber(smooth) ? smooth > 0 : smooth;
    ctx.lineWidth = size;
    ctx.strokeStyle = color;
    // Use explicit lineCap/lineJoin if provided, otherwise default based on smooth
    if (isString(lineCap)) {
        ctx.lineCap = lineCap;
    }
    else if (isSmooth) {
        ctx.lineCap = 'round';
    }
    else {
        ctx.lineCap = 'butt';
    }
    if (isString(lineJoin)) {
        ctx.lineJoin = lineJoin;
    }
    else if (isSmooth) {
        ctx.lineJoin = 'round';
    }
    else {
        ctx.lineJoin = 'miter';
    }
    if (style === 'dashed') {
        ctx.setLineDash(dashedValue);
    }
    else {
        ctx.setLineDash([]);
    }
    var correction = size % 2 === 1 ? 0.5 : 0;
    lines.forEach(function (_a) {
        var coordinates = _a.coordinates;
        if (coordinates.length > 1) {
            if (coordinates.length === 2 && (coordinates[0].x === coordinates[1].x || coordinates[0].y === coordinates[1].y)) {
                ctx.beginPath();
                if (coordinates[0].x === coordinates[1].x) {
                    ctx.moveTo(coordinates[0].x + correction, coordinates[0].y);
                    ctx.lineTo(coordinates[1].x + correction, coordinates[1].y);
                }
                else {
                    ctx.moveTo(coordinates[0].x, coordinates[0].y + correction);
                    ctx.lineTo(coordinates[1].x, coordinates[1].y + correction);
                }
                ctx.stroke();
                ctx.closePath();
            }
            else {
                ctx.save();
                if (size % 2 === 1) {
                    ctx.translate(0.5, 0.5);
                }
                ctx.beginPath();
                ctx.moveTo(coordinates[0].x, coordinates[0].y);
                lineTo(ctx, coordinates, smooth);
                ctx.stroke();
                ctx.closePath();
                ctx.restore();
            }
        }
    });
}
var line = {
    name: 'line',
    checkEventOn: checkCoordinateOnLine,
    draw: function (ctx, attrs, styles) {
        drawLine(ctx, attrs, styles);
    }
};

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
function getDistance(coordinate1, coordinate2) {
    var xDif = coordinate1.x - coordinate2.x;
    var yDif = coordinate1.y - coordinate2.y;
    return Math.sqrt(xDif * xDif + yDif * yDif);
}

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
function checkCoordinateOnArc(coordinate, attrs) {
    var e_1, _a;
    var arcs = [];
    arcs = arcs.concat(attrs);
    try {
        for (var arcs_1 = __values(arcs), arcs_1_1 = arcs_1.next(); !arcs_1_1.done; arcs_1_1 = arcs_1.next()) {
            var arc_1 = arcs_1_1.value;
            if (Math.abs(getDistance(coordinate, arc_1) - arc_1.r) < DEVIATION) {
                var r = arc_1.r, startAngle = arc_1.startAngle, endAngle = arc_1.endAngle;
                var startCoordinateX = r * Math.cos(startAngle) + arc_1.x;
                var startCoordinateY = r * Math.sin(startAngle) + arc_1.y;
                var endCoordinateX = r * Math.cos(endAngle) + arc_1.x;
                var endCoordinateY = r * Math.sin(endAngle) + arc_1.y;
                if (coordinate.x <= Math.max(startCoordinateX, endCoordinateX) + DEVIATION && coordinate.x >= Math.min(startCoordinateX, endCoordinateX) - DEVIATION && coordinate.y <= Math.max(startCoordinateY, endCoordinateY) + DEVIATION && coordinate.y >= Math.min(startCoordinateY, endCoordinateY) - DEVIATION) {
                    return true;
                }
            }
        }
    }
    catch (e_1_1) { e_1 = { error: e_1_1 }; }
    finally {
        try {
            if (arcs_1_1 && !arcs_1_1.done && (_a = arcs_1.return)) _a.call(arcs_1);
        }
        finally { if (e_1) throw e_1.error; }
    }
    return false;
}
function drawArc(ctx, attrs, styles) {
    var arcs = [];
    arcs = arcs.concat(attrs);
    var _a = styles.style, style = _a === void 0 ? 'solid' : _a, _b = styles.size, size = _b === void 0 ? 1 : _b, _c = styles.color, color = _c === void 0 ? 'currentColor' : _c, _d = styles.dashedValue, dashedValue = _d === void 0 ? [2, 2] : _d;
    ctx.lineWidth = size;
    ctx.strokeStyle = color;
    if (style === 'dashed') {
        ctx.setLineDash(dashedValue);
    }
    else {
        ctx.setLineDash([]);
    }
    arcs.forEach(function (_a) {
        var x = _a.x, y = _a.y, r = _a.r, startAngle = _a.startAngle, endAngle = _a.endAngle;
        ctx.beginPath();
        ctx.arc(x, y, r, startAngle, endAngle);
        ctx.stroke();
        ctx.closePath();
    });
}
var arc = {
    name: 'arc',
    checkEventOn: checkCoordinateOnArc,
    draw: function (ctx, attrs, styles) {
        drawArc(ctx, attrs, styles);
    }
};

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
function isTransparent(color) {
    return color === 'transparent' || color === 'none' || /^[rR][gG][Bb][Aa]\(([\s]*(2[0-4][0-9]|25[0-5]|[01]?[0-9][0-9]?)[\s]*,){3}[\s]*0[\s]*\)$/.test(color) || /^[hH][Ss][Ll][Aa]\(([\s]*(360|3[0-5][0-9]|[012]?[0-9][0-9]?)[\s]*,)([\s]*((100|[0-9][0-9]?)%|0)[\s]*,){2}([\s]*0[\s]*)\)$/.test(color);
}
function hexToRgb(hex, alpha) {
    var h = hex.replace(/^#/, '');
    var i = parseInt(h, 16);
    var r = (i >> 16) & 255;
    var g = (i >> 8) & 255;
    var b = i & 255;
    return "rgba(".concat(r, ", ").concat(g, ", ").concat(b, ", ").concat(alpha !== null && alpha !== void 0 ? alpha : 1, ")");
}

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
function checkCoordinateOnCircle(coordinate, attrs) {
    var e_1, _a;
    var circles = [];
    circles = circles.concat(attrs);
    try {
        for (var circles_1 = __values(circles), circles_1_1 = circles_1.next(); !circles_1_1.done; circles_1_1 = circles_1.next()) {
            var circle_1 = circles_1_1.value;
            var x = circle_1.x, y = circle_1.y, r = circle_1.r;
            var difX = coordinate.x - x;
            var difY = coordinate.y - y;
            if (!(difX * difX + difY * difY > r * r)) {
                return true;
            }
        }
    }
    catch (e_1_1) { e_1 = { error: e_1_1 }; }
    finally {
        try {
            if (circles_1_1 && !circles_1_1.done && (_a = circles_1.return)) _a.call(circles_1);
        }
        finally { if (e_1) throw e_1.error; }
    }
    return false;
}
function drawCircle(ctx, attrs, styles) {
    var circles = [];
    circles = circles.concat(attrs);
    var _a = styles.style, style = _a === void 0 ? 'fill' : _a, _b = styles.color, color = _b === void 0 ? 'currentColor' : _b, _c = styles.borderSize, borderSize = _c === void 0 ? 1 : _c, _d = styles.borderColor, borderColor = _d === void 0 ? 'currentColor' : _d, _e = styles.borderStyle, borderStyle = _e === void 0 ? 'solid' : _e, _f = styles.borderDashedValue, borderDashedValue = _f === void 0 ? [2, 2] : _f;
    var solid = (style === 'fill' || styles.style === 'stroke_fill') && (!isString(color) || !isTransparent(color));
    if (solid) {
        ctx.fillStyle = color;
        circles.forEach(function (_a) {
            var x = _a.x, y = _a.y, r = _a.r;
            ctx.beginPath();
            ctx.arc(x, y, r, 0, Math.PI * 2);
            ctx.closePath();
            ctx.fill();
        });
    }
    if ((style === 'stroke' || styles.style === 'stroke_fill') && borderSize > 0 && !isTransparent(borderColor)) {
        ctx.strokeStyle = borderColor;
        ctx.lineWidth = borderSize;
        if (borderStyle === 'dashed') {
            ctx.setLineDash(borderDashedValue);
        }
        else {
            ctx.setLineDash([]);
        }
        circles.forEach(function (_a) {
            var x = _a.x, y = _a.y, r = _a.r;
            if (!solid || r > borderSize) {
                ctx.beginPath();
                ctx.arc(x, y, r, 0, Math.PI * 2);
                ctx.closePath();
                ctx.stroke();
            }
        });
    }
}
var circle = {
    name: 'circle',
    checkEventOn: checkCoordinateOnCircle,
    draw: function (ctx, attrs, styles) {
        drawCircle(ctx, attrs, styles);
    }
};

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
function checkCoordinateOnRect(coordinate, attrs) {
    var e_1, _a;
    var rects = [];
    rects = rects.concat(attrs);
    try {
        for (var rects_1 = __values(rects), rects_1_1 = rects_1.next(); !rects_1_1.done; rects_1_1 = rects_1.next()) {
            var rect_1 = rects_1_1.value;
            var x = rect_1.x;
            var width = rect_1.width;
            if (width < DEVIATION * 2) {
                x -= DEVIATION;
                width = DEVIATION * 2;
            }
            var y = rect_1.y;
            var height = rect_1.height;
            if (height < DEVIATION * 2) {
                y -= DEVIATION;
                height = DEVIATION * 2;
            }
            if (coordinate.x >= x && coordinate.x <= x + width && coordinate.y >= y && coordinate.y <= y + height) {
                return true;
            }
        }
    }
    catch (e_1_1) { e_1 = { error: e_1_1 }; }
    finally {
        try {
            if (rects_1_1 && !rects_1_1.done && (_a = rects_1.return)) _a.call(rects_1);
        }
        finally { if (e_1) throw e_1.error; }
    }
    return false;
}
function drawRect(ctx, attrs, styles) {
    var _a;
    var rects = [];
    rects = rects.concat(attrs);
    var _b = styles.style, style = _b === void 0 ? 'fill' : _b, _c = styles.color, color = _c === void 0 ? 'transparent' : _c, _d = styles.borderSize, borderSize = _d === void 0 ? 1 : _d, _e = styles.borderColor, borderColor = _e === void 0 ? 'transparent' : _e, _f = styles.borderStyle, borderStyle = _f === void 0 ? 'solid' : _f, _g = styles.borderRadius, r = _g === void 0 ? 0 : _g, _h = styles.borderDashedValue, borderDashedValue = _h === void 0 ? [2, 2] : _h;
    var draw = (_a = ctx.roundRect) !== null && _a !== void 0 ? _a : ctx.rect;
    var solid = (style === 'fill' || styles.style === 'stroke_fill') && (!isString(color) || !isTransparent(color));
    if (solid) {
        ctx.fillStyle = color;
        rects.forEach(function (_a) {
            var x = _a.x, y = _a.y, w = _a.width, h = _a.height;
            ctx.beginPath();
            draw.call(ctx, x, y, w, h, r);
            ctx.closePath();
            ctx.fill();
        });
    }
    if ((style === 'stroke' || styles.style === 'stroke_fill') && borderSize > 0 && !isTransparent(borderColor)) {
        ctx.strokeStyle = borderColor;
        ctx.fillStyle = borderColor;
        ctx.lineWidth = borderSize;
        if (borderStyle === 'dashed') {
            ctx.setLineDash(borderDashedValue);
        }
        else {
            ctx.setLineDash([]);
        }
        var correction_1 = borderSize % 2 === 1 ? 0.5 : 0;
        var doubleCorrection_1 = Math.round(correction_1 * 2);
        rects.forEach(function (_a) {
            var x = _a.x, y = _a.y, w = _a.width, h = _a.height;
            if (w > borderSize * 2 && h > borderSize * 2) {
                ctx.beginPath();
                draw.call(ctx, x + correction_1, y + correction_1, w - doubleCorrection_1, h - doubleCorrection_1, r);
                ctx.closePath();
                ctx.stroke();
            }
            else {
                if (!solid) {
                    ctx.fillRect(x, y, w, h);
                }
            }
        });
    }
}
var rect = {
    name: 'rect',
    checkEventOn: checkCoordinateOnRect,
    draw: function (ctx, attrs, styles) {
        drawRect(ctx, attrs, styles);
    }
};

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
var COMMAND_ARGUMENT_COUNTS = {
    A: 7,
    C: 6,
    H: 1,
    L: 2,
    M: 2,
    Q: 4,
    S: 4,
    T: 2,
    V: 1,
    Z: 0
};
var PATH_CACHE_LIMIT = 256;
var EMPTY_PATH = [];
var pathCache = new Map();
var tokenRegex = /[a-zA-Z]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?/g;
function tokenizePath(path) {
    var tokens = [];
    var lastIndex = 0;
    tokenRegex.lastIndex = 0;
    var match = tokenRegex.exec(path);
    while (match !== null) {
        if (!/^[\s,]*$/.test(path.slice(lastIndex, match.index))) {
            return null;
        }
        var token = match[0];
        if (/^[a-zA-Z]$/.test(token)) {
            tokens.push(token);
        }
        else {
            var value = Number(token);
            if (!isFinite(value)) {
                return null;
            }
            tokens.push(value);
        }
        lastIndex = tokenRegex.lastIndex;
        match = tokenRegex.exec(path);
    }
    return /^[\s,]*$/.test(path.slice(lastIndex)) ? tokens : null;
}
function resolveCoordinate(value, current, isRelative) {
    return value + (isRelative ? current : 0);
}
function ellipticalArcToBeziers(x1, y1, radiusX, radiusY, rotation, largeArcFlag, sweepFlag, x2, y2) {
    var rx = Math.abs(radiusX);
    var ry = Math.abs(radiusY);
    if (x1 === x2 && y1 === y2) {
        return [];
    }
    if (rx === 0 || ry === 0) {
        return [];
    }
    var phi = ((rotation % 360) * Math.PI) / 180;
    var cosPhi = Math.cos(phi);
    var sinPhi = Math.sin(phi);
    var dx = (x1 - x2) / 2;
    var dy = (y1 - y2) / 2;
    var x1p = cosPhi * dx + sinPhi * dy;
    var y1p = -sinPhi * dx + cosPhi * dy;
    var lambda = Math.pow(x1p, 2) / Math.pow(rx, 2) + Math.pow(y1p, 2) / Math.pow(ry, 2);
    if (lambda > 1) {
        var scale = Math.sqrt(lambda);
        rx *= scale;
        ry *= scale;
    }
    var rxSquared = Math.pow(rx, 2);
    var rySquared = Math.pow(ry, 2);
    var x1pSquared = Math.pow(x1p, 2);
    var y1pSquared = Math.pow(y1p, 2);
    var denominator = rxSquared * y1pSquared + rySquared * x1pSquared;
    var numerator = Math.max(0, rxSquared * rySquared - denominator);
    var sign = largeArcFlag === sweepFlag ? -1 : 1;
    var factor = denominator === 0 ? 0 : sign * Math.sqrt(numerator / denominator);
    var cxp = factor * ((rx * y1p) / ry);
    var cyp = factor * ((-ry * x1p) / rx);
    var cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2;
    var cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2;
    var startAngle = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
    var deltaAngle = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - startAngle;
    if (deltaAngle < 0 && sweepFlag === 1) {
        deltaAngle += 2 * Math.PI;
    }
    else if (deltaAngle > 0 && sweepFlag === 0) {
        deltaAngle -= 2 * Math.PI;
    }
    var segmentCount = Math.ceil(Math.abs(deltaAngle) / (Math.PI / 2));
    var segmentAngle = deltaAngle / segmentCount;
    var commands = [];
    for (var i = 0; i < segmentCount; i++) {
        var start = startAngle + i * segmentAngle;
        var end = start + segmentAngle;
        var alpha = (4 / 3) * Math.tan((end - start) / 4);
        var startCos = Math.cos(start);
        var startSin = Math.sin(start);
        var endCos = Math.cos(end);
        var endSin = Math.sin(end);
        var startX = cx + rx * startCos * cosPhi - ry * startSin * sinPhi;
        var startY = cy + rx * startCos * sinPhi + ry * startSin * cosPhi;
        var endX = cx + rx * endCos * cosPhi - ry * endSin * sinPhi;
        var endY = cy + rx * endCos * sinPhi + ry * endSin * cosPhi;
        var startDx = -rx * startSin * cosPhi - ry * startCos * sinPhi;
        var startDy = -rx * startSin * sinPhi + ry * startCos * cosPhi;
        var endDx = -rx * endSin * cosPhi - ry * endCos * sinPhi;
        var endDy = -rx * endSin * sinPhi + ry * endCos * cosPhi;
        commands.push({
            type: 'C',
            cp1X: startX + alpha * startDx,
            cp1Y: startY + alpha * startDy,
            cp2X: endX - alpha * endDx,
            cp2Y: endY - alpha * endDy,
            x: endX,
            y: endY
        });
    }
    return commands;
}
function compilePath(path) {
    var tokens = tokenizePath(path);
    if (tokens === null) {
        return EMPTY_PATH;
    }
    var compiled = [];
    var index = 0;
    var command = '';
    var currentX = 0;
    var currentY = 0;
    var startX = 0;
    var startY = 0;
    var cubicControlX = 0;
    var cubicControlY = 0;
    var quadraticControlX = 0;
    var quadraticControlY = 0;
    var previousCommand = '';
    var isFirstMoveGroup = false;
    while (index < tokens.length) {
        if (typeof tokens[index] === 'string') {
            command = tokens[index++];
            var upperCommand_1 = command.toUpperCase();
            if (COMMAND_ARGUMENT_COUNTS[upperCommand_1] === undefined) {
                return EMPTY_PATH;
            }
            if (upperCommand_1 === 'Z') {
                compiled.push({ type: 'Z' });
                currentX = startX;
                currentY = startY;
                previousCommand = 'Z';
                command = '';
                continue;
            }
            isFirstMoveGroup = upperCommand_1 === 'M';
        }
        else if (command === '') {
            return EMPTY_PATH;
        }
        var upperCommand = command.toUpperCase();
        var argumentCount = COMMAND_ARGUMENT_COUNTS[upperCommand];
        if (index + argumentCount > tokens.length) {
            return EMPTY_PATH;
        }
        var args = [];
        for (var i = 0; i < argumentCount; i++) {
            var token = tokens[index + i];
            if (typeof token !== 'number') {
                return EMPTY_PATH;
            }
            args.push(token);
        }
        index += argumentCount;
        var isRelative = command === command.toLowerCase();
        switch (upperCommand) {
            case 'M': {
                var x = resolveCoordinate(args[0], currentX, isRelative);
                var y = resolveCoordinate(args[1], currentY, isRelative);
                if (isFirstMoveGroup) {
                    compiled.push({ type: 'M', x: x, y: y });
                    startX = x;
                    startY = y;
                    isFirstMoveGroup = false;
                }
                else {
                    compiled.push({ type: 'L', x: x, y: y });
                }
                currentX = x;
                currentY = y;
                break;
            }
            case 'L': {
                currentX = resolveCoordinate(args[0], currentX, isRelative);
                currentY = resolveCoordinate(args[1], currentY, isRelative);
                compiled.push({ type: 'L', x: currentX, y: currentY });
                break;
            }
            case 'H':
                currentX = resolveCoordinate(args[0], currentX, isRelative);
                compiled.push({ type: 'L', x: currentX, y: currentY });
                break;
            case 'V':
                currentY = resolveCoordinate(args[0], currentY, isRelative);
                compiled.push({ type: 'L', x: currentX, y: currentY });
                break;
            case 'C': {
                var cp1X = resolveCoordinate(args[0], currentX, isRelative);
                var cp1Y = resolveCoordinate(args[1], currentY, isRelative);
                var cp2X = resolveCoordinate(args[2], currentX, isRelative);
                var cp2Y = resolveCoordinate(args[3], currentY, isRelative);
                var x = resolveCoordinate(args[4], currentX, isRelative);
                var y = resolveCoordinate(args[5], currentY, isRelative);
                compiled.push({ type: 'C', cp1X: cp1X, cp1Y: cp1Y, cp2X: cp2X, cp2Y: cp2Y, x: x, y: y });
                cubicControlX = cp2X;
                cubicControlY = cp2Y;
                currentX = x;
                currentY = y;
                break;
            }
            case 'S': {
                var cp1X = previousCommand === 'C' || previousCommand === 'S' ? 2 * currentX - cubicControlX : currentX;
                var cp1Y = previousCommand === 'C' || previousCommand === 'S' ? 2 * currentY - cubicControlY : currentY;
                var cp2X = resolveCoordinate(args[0], currentX, isRelative);
                var cp2Y = resolveCoordinate(args[1], currentY, isRelative);
                var x = resolveCoordinate(args[2], currentX, isRelative);
                var y = resolveCoordinate(args[3], currentY, isRelative);
                compiled.push({ type: 'C', cp1X: cp1X, cp1Y: cp1Y, cp2X: cp2X, cp2Y: cp2Y, x: x, y: y });
                cubicControlX = cp2X;
                cubicControlY = cp2Y;
                currentX = x;
                currentY = y;
                break;
            }
            case 'Q': {
                var cpX = resolveCoordinate(args[0], currentX, isRelative);
                var cpY = resolveCoordinate(args[1], currentY, isRelative);
                var x = resolveCoordinate(args[2], currentX, isRelative);
                var y = resolveCoordinate(args[3], currentY, isRelative);
                compiled.push({ type: 'Q', cpX: cpX, cpY: cpY, x: x, y: y });
                quadraticControlX = cpX;
                quadraticControlY = cpY;
                currentX = x;
                currentY = y;
                break;
            }
            case 'T': {
                var cpX = previousCommand === 'Q' || previousCommand === 'T' ? 2 * currentX - quadraticControlX : currentX;
                var cpY = previousCommand === 'Q' || previousCommand === 'T' ? 2 * currentY - quadraticControlY : currentY;
                var x = resolveCoordinate(args[0], currentX, isRelative);
                var y = resolveCoordinate(args[1], currentY, isRelative);
                compiled.push({ type: 'Q', cpX: cpX, cpY: cpY, x: x, y: y });
                quadraticControlX = cpX;
                quadraticControlY = cpY;
                currentX = x;
                currentY = y;
                break;
            }
            case 'A': {
                if ((args[3] !== 0 && args[3] !== 1) || (args[4] !== 0 && args[4] !== 1)) {
                    return EMPTY_PATH;
                }
                var x = resolveCoordinate(args[5], currentX, isRelative);
                var y = resolveCoordinate(args[6], currentY, isRelative);
                if (args[0] === 0 || args[1] === 0) {
                    compiled.push({ type: 'L', x: x, y: y });
                }
                else {
                    compiled.push.apply(compiled, __spreadArray([], __read(ellipticalArcToBeziers(currentX, currentY, args[0], args[1], args[2], args[3], args[4], x, y)), false));
                }
                currentX = x;
                currentY = y;
                break;
            }
            default:
                return EMPTY_PATH;
        }
        previousCommand = upperCommand;
    }
    return compiled;
}
function getCompiledPath(path) {
    var cached = pathCache.get(path);
    if (cached !== undefined) {
        return cached;
    }
    var compiled = compilePath(path);
    if (pathCache.size >= PATH_CACHE_LIMIT) {
        var oldestKey = pathCache.keys().next().value;
        if (oldestKey !== undefined) {
            pathCache.delete(oldestKey);
        }
    }
    pathCache.set(path, compiled);
    return compiled;
}
function replayPath(ctx, commands, offsetX, offsetY) {
    ctx.beginPath();
    commands.forEach(function (command) {
        switch (command.type) {
            case 'M':
                ctx.moveTo(command.x + offsetX, command.y + offsetY);
                break;
            case 'L':
                ctx.lineTo(command.x + offsetX, command.y + offsetY);
                break;
            case 'C':
                ctx.bezierCurveTo(command.cp1X + offsetX, command.cp1Y + offsetY, command.cp2X + offsetX, command.cp2Y + offsetY, command.x + offsetX, command.y + offsetY);
                break;
            case 'Q':
                ctx.quadraticCurveTo(command.cpX + offsetX, command.cpY + offsetY, command.x + offsetX, command.y + offsetY);
                break;
            case 'Z':
                ctx.closePath();
                break;
        }
    });
}
function drawPath(ctx, attrs, styles) {
    var paths = Array.isArray(attrs) ? attrs : [attrs];
    var _a = styles.style, style = _a === void 0 ? 'stroke' : _a, _b = styles.lineWidth, lineWidth = _b === void 0 ? 1 : _b, _c = styles.color, color = _c === void 0 ? 'currentColor' : _c;
    ctx.lineWidth = lineWidth;
    ctx.setLineDash([]);
    if (style === 'fill') {
        ctx.fillStyle = color;
    }
    else {
        ctx.strokeStyle = color;
    }
    paths.forEach(function (_a) {
        var x = _a.x, y = _a.y, path = _a.path;
        var commands = getCompiledPath(path);
        if (commands.length > 0) {
            replayPath(ctx, commands, x, y);
            if (style === 'fill') {
                ctx.fill();
            }
            else {
                ctx.stroke();
            }
        }
    });
}
var path = {
    name: 'path',
    checkEventOn: checkCoordinateOnRect,
    draw: function (ctx, attrs, styles) {
        drawPath(ctx, attrs, styles);
    }
};

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
function checkCoordinateOnPolygon(coordinate, attrs) {
    var e_1, _a;
    var polygons = [];
    polygons = polygons.concat(attrs);
    try {
        for (var polygons_1 = __values(polygons), polygons_1_1 = polygons_1.next(); !polygons_1_1.done; polygons_1_1 = polygons_1.next()) {
            var polygon_1 = polygons_1_1.value;
            var on = false;
            var coordinates = polygon_1.coordinates;
            for (var i = 0, j = coordinates.length - 1; i < coordinates.length; j = i++) {
                if (coordinates[i].y > coordinate.y !== coordinates[j].y > coordinate.y && coordinate.x < ((coordinates[j].x - coordinates[i].x) * (coordinate.y - coordinates[i].y)) / (coordinates[j].y - coordinates[i].y) + coordinates[i].x) {
                    on = !on;
                }
            }
            if (on) {
                return true;
            }
        }
    }
    catch (e_1_1) { e_1 = { error: e_1_1 }; }
    finally {
        try {
            if (polygons_1_1 && !polygons_1_1.done && (_a = polygons_1.return)) _a.call(polygons_1);
        }
        finally { if (e_1) throw e_1.error; }
    }
    return false;
}
function drawPolygon(ctx, attrs, styles) {
    var polygons = [];
    polygons = polygons.concat(attrs);
    var _a = styles.style, style = _a === void 0 ? 'fill' : _a, _b = styles.color, color = _b === void 0 ? 'currentColor' : _b, _c = styles.borderSize, borderSize = _c === void 0 ? 1 : _c, _d = styles.borderColor, borderColor = _d === void 0 ? 'currentColor' : _d, _e = styles.borderStyle, borderStyle = _e === void 0 ? 'solid' : _e, _f = styles.borderDashedValue, borderDashedValue = _f === void 0 ? [2, 2] : _f;
    if ((style === 'fill' || styles.style === 'stroke_fill') && (!isString(color) || !isTransparent(color))) {
        ctx.fillStyle = color;
        polygons.forEach(function (_a) {
            var coordinates = _a.coordinates;
            ctx.beginPath();
            ctx.moveTo(coordinates[0].x, coordinates[0].y);
            for (var i = 1; i < coordinates.length; i++) {
                ctx.lineTo(coordinates[i].x, coordinates[i].y);
            }
            ctx.closePath();
            ctx.fill();
        });
    }
    if ((style === 'stroke' || styles.style === 'stroke_fill') && borderSize > 0 && !isTransparent(borderColor)) {
        ctx.strokeStyle = borderColor;
        ctx.lineWidth = borderSize;
        if (borderStyle === 'dashed') {
            ctx.setLineDash(borderDashedValue);
        }
        else {
            ctx.setLineDash([]);
        }
        polygons.forEach(function (_a) {
            var coordinates = _a.coordinates;
            ctx.beginPath();
            ctx.moveTo(coordinates[0].x, coordinates[0].y);
            for (var i = 1; i < coordinates.length; i++) {
                ctx.lineTo(coordinates[i].x, coordinates[i].y);
            }
            ctx.closePath();
            ctx.stroke();
        });
    }
}
var polygon = {
    name: 'polygon',
    checkEventOn: checkCoordinateOnPolygon,
    draw: function (ctx, attrs, styles) {
        drawPolygon(ctx, attrs, styles);
    }
};

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
function getTextRect(attrs, styles) {
    var _a = styles.size, size = _a === void 0 ? 12 : _a, _b = styles.paddingLeft, paddingLeft = _b === void 0 ? 0 : _b, _c = styles.paddingTop, paddingTop = _c === void 0 ? 0 : _c, _d = styles.paddingRight, paddingRight = _d === void 0 ? 0 : _d, _e = styles.paddingBottom, paddingBottom = _e === void 0 ? 0 : _e, _f = styles.weight, weight = _f === void 0 ? 'normal' : _f, family = styles.family;
    var x = attrs.x, y = attrs.y, text = attrs.text, _g = attrs.align, align = _g === void 0 ? 'left' : _g, _h = attrs.baseline, baseline = _h === void 0 ? 'top' : _h, w = attrs.width, h = attrs.height;
    var width = w !== null && w !== void 0 ? w : paddingLeft + calcTextWidth(text, size, weight, family) + paddingRight;
    var height = h !== null && h !== void 0 ? h : paddingTop + size + paddingBottom;
    var startX = 0;
    switch (align) {
        case 'left':
        case 'start': {
            startX = x;
            break;
        }
        case 'right':
        case 'end': {
            startX = x - width;
            break;
        }
        default: {
            startX = x - width / 2;
            break;
        }
    }
    var startY = 0;
    switch (baseline) {
        case 'top':
        case 'hanging': {
            startY = y;
            break;
        }
        case 'bottom':
        case 'ideographic':
        case 'alphabetic': {
            startY = y - height;
            break;
        }
        default: {
            startY = y - height / 2;
            break;
        }
    }
    return { x: startX, y: startY, width: width, height: height };
}
function checkCoordinateOnText(coordinate, attrs, styles) {
    var e_1, _a;
    var texts = [];
    texts = texts.concat(attrs);
    try {
        for (var texts_1 = __values(texts), texts_1_1 = texts_1.next(); !texts_1_1.done; texts_1_1 = texts_1.next()) {
            var text_1 = texts_1_1.value;
            var _b = getTextRect(text_1, styles), x = _b.x, y = _b.y, width = _b.width, height = _b.height;
            if (coordinate.x >= x && coordinate.x <= x + width && coordinate.y >= y && coordinate.y <= y + height) {
                return true;
            }
        }
    }
    catch (e_1_1) { e_1 = { error: e_1_1 }; }
    finally {
        try {
            if (texts_1_1 && !texts_1_1.done && (_a = texts_1.return)) _a.call(texts_1);
        }
        finally { if (e_1) throw e_1.error; }
    }
    return false;
}
function drawText(ctx, attrs, styles) {
    var texts = [];
    texts = texts.concat(attrs);
    var _a = styles.color, color = _a === void 0 ? 'currentColor' : _a, _b = styles.size, size = _b === void 0 ? 12 : _b, family = styles.family, weight = styles.weight, _c = styles.paddingLeft, paddingLeft = _c === void 0 ? 0 : _c, _d = styles.paddingTop, paddingTop = _d === void 0 ? 0 : _d, _e = styles.paddingRight, paddingRight = _e === void 0 ? 0 : _e;
    var rects = texts.map(function (text) { return getTextRect(text, styles); });
    drawRect(ctx, rects, __assign(__assign({}, styles), { color: styles.backgroundColor }));
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.font = createFont(size, weight, family);
    ctx.fillStyle = color;
    texts.forEach(function (text, index) {
        var rect = rects[index];
        ctx.fillText(text.text, rect.x + paddingLeft, rect.y + paddingTop, rect.width - paddingLeft - paddingRight);
    });
}
var text = {
    name: 'text',
    checkEventOn: checkCoordinateOnText,
    draw: function (ctx, attrs, styles) {
        drawText(ctx, attrs, styles);
    }
};

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
var figures = {};
var extensions$1 = [circle, line, polygon, rect, text, arc, path];
// @ts-ignore
extensions$1.forEach(function (figure) {
    figures[figure.name] = FigureImp.extend(figure);
});
function getSupportedFigures() {
    return Object.keys(figures);
}
function registerFigure(figure) {
    // @ts-ignore
    figures[figure.name] = FigureImp.extend(figure);
}
function getInnerFigureClass(name) {
    var _a;
    return (_a = figures[name]) !== null && _a !== void 0 ? _a : null;
}
function getFigureClass(name) {
    var _a;
    return (_a = figures[name]) !== null && _a !== void 0 ? _a : null;
}

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
var View = /** @class */ (function (_super) {
    __extends(View, _super);
    function View(widget) {
        var _this = _super.call(this) || this;
        _this._widget = widget;
        return _this;
    }
    View.prototype.getWidget = function () {
        return this._widget;
    };
    View.prototype.createFigure = function (create, eventHandler) {
        var FigureClazz = getInnerFigureClass(create.name);
        if (FigureClazz !== null) {
            var figure = new FigureClazz(create);
            if (isValid(eventHandler)) {
                for (var key in eventHandler) {
                    // biome-ignore lint/suspicious/noPrototypeBuiltins: Event handlers may inherit their own implementation.
                    if (eventHandler.hasOwnProperty(key)) {
                        figure.registerEvent(key, eventHandler[key]);
                    }
                }
                this.addChild(figure);
            }
            return figure;
        }
        return null;
    };
    View.prototype.draw = function (ctx) {
        this.clear();
        this.drawImp(ctx);
    };
    View.prototype.checkEventOn = function (_) {
        return true;
    };
    return View;
}(Eventful));

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
var ChildrenView = /** @class */ (function (_super) {
    __extends(ChildrenView, _super);
    function ChildrenView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    ChildrenView.prototype.eachChildren = function (childCallback) {
        var pane = this.getWidget().getPane();
        var chartStore = pane.getChart().getChartStore();
        var visibleRangeDataList = chartStore.getVisibleRangeDataList();
        var barSpace = chartStore.getBarSpace();
        var dataLength = visibleRangeDataList.length;
        var index = 0;
        while (index < dataLength) {
            childCallback(visibleRangeDataList[index], barSpace, index);
            ++index;
        }
    };
    return ChildrenView;
}(View));

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
var CandleAreaView = /** @class */ (function (_super) {
    __extends(CandleAreaView, _super);
    function CandleAreaView() {
        var _this = _super.apply(this, __spreadArray([], __read(arguments), false)) || this;
        _this._ripplePoint = _this.createFigure({
            name: 'circle',
            attrs: {
                x: 0,
                y: 0,
                r: 0
            },
            styles: {
                style: 'fill'
            }
        });
        _this._animationFrameTime = 0;
        _this._animation = new Animation({ iterationCount: Infinity }).doFrame(function (time) {
            _this._animationFrameTime = time;
            var pane = _this.getWidget().getPane();
            pane.getChart().updatePane(0 /* UpdateLevel.Main */, pane.getId());
        });
        return _this;
    }
    CandleAreaView.prototype.drawImp = function (ctx) {
        var _a, _b, _c;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chart = pane.getChart();
        var dataList = chart.getDataList();
        var lastDataIndex = dataList.length - 1;
        var bounding = widget.getBounding();
        var yAxis = pane.getYAxisComponentById();
        var styles = chart.getStyles().candle.area;
        var coordinates = [];
        var minY = Number.MAX_SAFE_INTEGER;
        var areaStartX = Number.MIN_SAFE_INTEGER;
        var ripplePointCoordinate = null;
        this.eachChildren(function (data) {
            var x = data.x;
            var kLineData = data.data.current;
            var value = kLineData === null || kLineData === void 0 ? void 0 : kLineData[styles.value];
            if (isNumber(value)) {
                var y = yAxis.convertToPixel(value);
                if (areaStartX === Number.MIN_SAFE_INTEGER) {
                    areaStartX = x;
                }
                coordinates.push({ x: x, y: y });
                minY = Math.min(minY, y);
                if (data.dataIndex === lastDataIndex) {
                    ripplePointCoordinate = { x: x, y: y };
                }
            }
        });
        if (coordinates.length > 0) {
            (_a = this.createFigure({
                name: 'line',
                attrs: { coordinates: coordinates },
                styles: {
                    color: styles.lineColor,
                    size: styles.lineSize,
                    smooth: styles.smooth
                }
            })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
            // render area
            var backgroundColor = styles.backgroundColor;
            var color = '';
            if (isArray(backgroundColor)) {
                var gradient_1 = ctx.createLinearGradient(0, bounding.height, 0, minY);
                try {
                    backgroundColor.forEach(function (_a) {
                        var offset = _a.offset, color = _a.color;
                        gradient_1.addColorStop(offset, color);
                    });
                }
                catch (_d) {
                    // Ignore invalid gradient stops.
                }
                color = gradient_1;
            }
            else {
                color = backgroundColor;
            }
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.moveTo(areaStartX, bounding.height);
            ctx.lineTo(coordinates[0].x, coordinates[0].y);
            lineTo(ctx, coordinates, styles.smooth);
            ctx.lineTo(coordinates[coordinates.length - 1].x, bounding.height);
            ctx.closePath();
            ctx.fill();
        }
        var pointStyles = styles.point;
        if (pointStyles.show && isValid(ripplePointCoordinate)) {
            (_b = this.createFigure({
                name: 'circle',
                attrs: {
                    x: ripplePointCoordinate.x,
                    y: ripplePointCoordinate.y,
                    r: pointStyles.radius
                },
                styles: {
                    style: 'fill',
                    color: pointStyles.color
                }
            })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
            var rippleRadius = pointStyles.rippleRadius;
            if (pointStyles.animation && isNumber(pointStyles.animationDuration) && pointStyles.animationDuration > 0) {
                var progress = Math.min(this._animationFrameTime / pointStyles.animationDuration, 1);
                rippleRadius = pointStyles.radius + progress * (pointStyles.rippleRadius - pointStyles.radius);
                this._animation.setDuration(pointStyles.animationDuration).start();
            }
            else {
                this.stopAnimation();
            }
            (_c = this._ripplePoint) === null || _c === void 0 ? void 0 : _c.setAttrs({
                x: ripplePointCoordinate.x,
                y: ripplePointCoordinate.y,
                r: rippleRadius
            }).setStyles({ style: 'fill', color: pointStyles.rippleColor }).draw(ctx);
        }
        else {
            this.stopAnimation();
        }
    };
    CandleAreaView.prototype.stopAnimation = function () {
        this._animationFrameTime = 0;
        this._animation.cancel();
    };
    return CandleAreaView;
}(ChildrenView));

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
var CandleBarView = /** @class */ (function (_super) {
    __extends(CandleBarView, _super);
    function CandleBarView() {
        var _this = _super.apply(this, __spreadArray([], __read(arguments), false)) || this;
        _this._boundCandleBarClickEvent = function (data) { return function () {
            _this.getWidget().getPane().getChart().getChartStore().executeAction('onCandleBarClick', data);
            return false;
        }; };
        return _this;
    }
    CandleBarView.prototype.drawImp = function (ctx) {
        var _this = this;
        var pane = this.getWidget().getPane();
        var isMain = pane.getId() === PaneIdConstants.CANDLE;
        var chartStore = pane.getChart().getChartStore();
        var candleBarOptions = this.getCandleBarOptions();
        if (candleBarOptions !== null) {
            var type_1 = candleBarOptions.type, styles_1 = candleBarOptions.styles;
            var ohlcSize_1 = 0;
            var halfOhlcSize_1 = 0;
            if (candleBarOptions.type === 'ohlc') {
                var gapBar = chartStore.getBarSpace().gapBar;
                ohlcSize_1 = Math.min(Math.max(Math.round(gapBar * 0.2), 1), 8);
                if (ohlcSize_1 > 2 && ohlcSize_1 % 2 === 1) {
                    ohlcSize_1--;
                }
                halfOhlcSize_1 = Math.floor(ohlcSize_1 / 2);
            }
            var yAxis_1 = pane.getYAxisComponentById(candleBarOptions.yAxisId);
            this.eachChildren(function (visibleData, barSpace) {
                var _a;
                var x = visibleData.x, _b = visibleData.data, current = _b.current, prev = _b.prev;
                if (isValid(current)) {
                    var open_1 = current.open, high = current.high, low = current.low, close_1 = current.close;
                    var comparePrice = styles_1.compareRule === 'current_open' ? open_1 : ((_a = prev === null || prev === void 0 ? void 0 : prev.close) !== null && _a !== void 0 ? _a : close_1);
                    var colors = [];
                    if (close_1 > comparePrice) {
                        colors[0] = styles_1.upColor;
                        colors[1] = styles_1.upBorderColor;
                        colors[2] = styles_1.upWickColor;
                    }
                    else if (close_1 < comparePrice) {
                        colors[0] = styles_1.downColor;
                        colors[1] = styles_1.downBorderColor;
                        colors[2] = styles_1.downWickColor;
                    }
                    else {
                        colors[0] = styles_1.noChangeColor;
                        colors[1] = styles_1.noChangeBorderColor;
                        colors[2] = styles_1.noChangeWickColor;
                    }
                    var openY = yAxis_1.convertToPixel(open_1);
                    var closeY = yAxis_1.convertToPixel(close_1);
                    var priceY = [openY, closeY, yAxis_1.convertToPixel(high), yAxis_1.convertToPixel(low)];
                    priceY.sort(function (a, b) { return a - b; });
                    var correction = barSpace.gapBar % 2 === 0 ? 1 : 0;
                    var rects = [];
                    switch (type_1) {
                        case 'candle_solid': {
                            rects = _this._createSolidBar(x, priceY, barSpace, colors, correction);
                            break;
                        }
                        case 'candle_stroke': {
                            rects = _this._createStrokeBar(x, priceY, barSpace, colors, correction);
                            break;
                        }
                        case 'candle_up_stroke': {
                            if (close_1 > open_1) {
                                rects = _this._createStrokeBar(x, priceY, barSpace, colors, correction);
                            }
                            else {
                                rects = _this._createSolidBar(x, priceY, barSpace, colors, correction);
                            }
                            break;
                        }
                        case 'candle_down_stroke': {
                            if (open_1 > close_1) {
                                rects = _this._createStrokeBar(x, priceY, barSpace, colors, correction);
                            }
                            else {
                                rects = _this._createSolidBar(x, priceY, barSpace, colors, correction);
                            }
                            break;
                        }
                        case 'ohlc': {
                            rects = [
                                {
                                    name: 'rect',
                                    attrs: [
                                        {
                                            x: x - halfOhlcSize_1,
                                            y: priceY[0],
                                            width: ohlcSize_1,
                                            height: priceY[3] - priceY[0]
                                        },
                                        {
                                            x: x - barSpace.halfGapBar,
                                            y: openY + ohlcSize_1 > priceY[3] ? priceY[3] - ohlcSize_1 : openY,
                                            width: barSpace.halfGapBar - halfOhlcSize_1,
                                            height: ohlcSize_1
                                        },
                                        {
                                            x: x + halfOhlcSize_1,
                                            y: closeY + ohlcSize_1 > priceY[3] ? priceY[3] - ohlcSize_1 : closeY,
                                            width: barSpace.halfGapBar - halfOhlcSize_1,
                                            height: ohlcSize_1
                                        }
                                    ],
                                    styles: { color: colors[0] }
                                }
                            ];
                            break;
                        }
                    }
                    rects.forEach(function (rect) {
                        var _a;
                        var handler = null;
                        if (isMain) {
                            handler = {
                                mouseClickEvent: _this._boundCandleBarClickEvent(visibleData)
                            };
                        }
                        (_a = _this.createFigure(rect, handler !== null && handler !== void 0 ? handler : undefined)) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                    });
                }
            });
        }
    };
    CandleBarView.prototype.getCandleBarOptions = function () {
        var pane = this.getWidget().getPane();
        var yAxisId = pane.getDefaultYAxisId();
        if (!isValid(yAxisId)) {
            return null;
        }
        var candleStyles = pane.getChart().getStyles().candle;
        return {
            yAxisId: yAxisId,
            type: candleStyles.type,
            styles: candleStyles.bar
        };
    };
    CandleBarView.prototype._createSolidBar = function (x, priceY, barSpace, colors, correction) {
        return [
            {
                name: 'rect',
                attrs: {
                    x: x,
                    y: priceY[0],
                    width: 1,
                    height: priceY[3] - priceY[0]
                },
                styles: { color: colors[2] }
            },
            {
                name: 'rect',
                attrs: {
                    x: x - barSpace.halfGapBar,
                    y: priceY[1],
                    width: barSpace.gapBar + correction,
                    height: Math.max(1, priceY[2] - priceY[1])
                },
                styles: {
                    style: 'stroke_fill',
                    color: colors[0],
                    borderColor: colors[1]
                }
            }
        ];
    };
    CandleBarView.prototype._createStrokeBar = function (x, priceY, barSpace, colors, correction) {
        return [
            {
                name: 'rect',
                attrs: [
                    {
                        x: x,
                        y: priceY[0],
                        width: 1,
                        height: priceY[1] - priceY[0]
                    },
                    {
                        x: x,
                        y: priceY[2],
                        width: 1,
                        height: priceY[3] - priceY[2]
                    }
                ],
                styles: { color: colors[2] }
            },
            {
                name: 'rect',
                attrs: {
                    x: x - barSpace.halfGapBar,
                    y: priceY[1],
                    width: barSpace.gapBar + correction,
                    height: Math.max(1, priceY[2] - priceY[1])
                },
                styles: {
                    style: 'stroke',
                    borderColor: colors[1]
                }
            }
        ];
    };
    return CandleBarView;
}(ChildrenView));

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
var CandleHighLowPriceView = /** @class */ (function (_super) {
    __extends(CandleHighLowPriceView, _super);
    function CandleHighLowPriceView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CandleHighLowPriceView.prototype.drawImp = function (ctx) {
        var _a, _b;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chartStore = pane.getChart().getChartStore();
        var priceMarkStyles = chartStore.getStyles().candle.priceMark;
        var highPriceMarkStyles = priceMarkStyles.high;
        var lowPriceMarkStyles = priceMarkStyles.low;
        if (priceMarkStyles.show && (highPriceMarkStyles.show || lowPriceMarkStyles.show)) {
            var highestLowestPrice = chartStore.getVisibleRangeHighLowPrice();
            var precision = (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE;
            var yAxis = pane.getYAxisComponentById();
            var _c = highestLowestPrice[0], high = _c.price, highX = _c.x;
            var _d = highestLowestPrice[1], low = _d.price, lowX = _d.x;
            var highY = yAxis.convertToPixel(high);
            var lowY = yAxis.convertToPixel(low);
            var decimalFold = chartStore.getDecimalFold();
            var thousandsSeparator = chartStore.getThousandsSeparator();
            if (highPriceMarkStyles.show && high !== Number.MIN_SAFE_INTEGER) {
                this._drawMark(ctx, decimalFold.format(thousandsSeparator.format(formatPrecision(high, precision))), { x: highX, y: highY }, highY < lowY ? [-2, -5] : [2, 5], highPriceMarkStyles);
            }
            if (lowPriceMarkStyles.show && low !== Number.MAX_SAFE_INTEGER) {
                this._drawMark(ctx, decimalFold.format(thousandsSeparator.format(formatPrecision(low, precision))), { x: lowX, y: lowY }, highY < lowY ? [2, 5] : [-2, -5], lowPriceMarkStyles);
            }
        }
    };
    CandleHighLowPriceView.prototype._drawMark = function (ctx, text, coordinate, offsets, styles) {
        var _a, _b, _c;
        var startX = coordinate.x;
        var startY = coordinate.y + offsets[0];
        (_a = this.createFigure({
            name: 'line',
            attrs: {
                coordinates: [
                    { x: startX - 2, y: startY + offsets[0] },
                    { x: startX, y: startY },
                    { x: startX + 2, y: startY + offsets[0] }
                ]
            },
            styles: { color: styles.color }
        })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
        var lineEndX = 0;
        var textStartX = 0;
        var textAlign = 'left';
        var width = this.getWidget().getBounding().width;
        if (startX > width / 2) {
            lineEndX = startX - 5;
            textStartX = lineEndX - styles.textOffset;
            textAlign = 'right';
        }
        else {
            lineEndX = startX + 5;
            textAlign = 'left';
            textStartX = lineEndX + styles.textOffset;
        }
        var y = startY + offsets[1];
        (_b = this.createFigure({
            name: 'line',
            attrs: {
                coordinates: [
                    { x: startX, y: startY },
                    { x: startX, y: y },
                    { x: lineEndX, y: y }
                ]
            },
            styles: { color: styles.color }
        })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
        (_c = this.createFigure({
            name: 'text',
            attrs: {
                x: textStartX,
                y: y,
                text: text,
                align: textAlign,
                baseline: 'middle'
            },
            styles: {
                color: styles.color,
                size: styles.textSize,
                family: styles.textFamily,
                weight: styles.textWeight
            }
        })) === null || _c === void 0 ? void 0 : _c.draw(ctx);
    };
    return CandleHighLowPriceView;
}(View));

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
var CandleLastPriceView = /** @class */ (function (_super) {
    __extends(CandleLastPriceView, _super);
    function CandleLastPriceView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CandleLastPriceView.prototype.drawImp = function (ctx) {
        var _a, _b, _c;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var bounding = widget.getBounding();
        var chartStore = pane.getChart().getChartStore();
        var priceMarkStyles = chartStore.getStyles().candle.priceMark;
        var lastPriceMarkStyles = priceMarkStyles.last;
        var lastPriceMarkLineStyles = lastPriceMarkStyles.line;
        if (priceMarkStyles.show && lastPriceMarkStyles.show && lastPriceMarkLineStyles.show) {
            var yAxis = pane.getYAxisComponentById();
            var dataList = chartStore.getDataList();
            var data = dataList[dataList.length - 1];
            if (isValid(data)) {
                var close_1 = data.close, open_1 = data.open;
                var comparePrice = lastPriceMarkStyles.compareRule === 'current_open' ? open_1 : ((_b = (_a = dataList[dataList.length - 2]) === null || _a === void 0 ? void 0 : _a.close) !== null && _b !== void 0 ? _b : close_1);
                var priceY = yAxis.convertToNicePixel(close_1);
                var color = '';
                if (close_1 > comparePrice) {
                    color = lastPriceMarkStyles.upColor;
                }
                else if (close_1 < comparePrice) {
                    color = lastPriceMarkStyles.downColor;
                }
                else {
                    color = lastPriceMarkStyles.noChangeColor;
                }
                (_c = this.createFigure({
                    name: 'line',
                    attrs: {
                        coordinates: [
                            { x: 0, y: priceY },
                            { x: bounding.width, y: priceY }
                        ]
                    },
                    styles: {
                        style: lastPriceMarkLineStyles.style,
                        color: color,
                        size: lastPriceMarkLineStyles.size,
                        dashedValue: lastPriceMarkLineStyles.dashedValue
                    }
                })) === null || _c === void 0 ? void 0 : _c.draw(ctx);
            }
        }
    };
    return CandleLastPriceView;
}(View));

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
var PeriodTypeXAxisFormat = {
    second: 'HH:mm:ss',
    minute: 'HH:mm',
    hour: 'MM-DD HH:mm',
    day: 'YYYY-MM-DD',
    week: 'YYYY-MM-DD',
    month: 'YYYY-MM',
    year: 'YYYY'
};
var PeriodTypeCrosshairTooltipFormat = {
    second: 'HH:mm:ss',
    minute: 'YYYY-MM-DD HH:mm',
    hour: 'YYYY-MM-DD HH:mm',
    day: 'YYYY-MM-DD',
    week: 'YYYY-MM-DD',
    month: 'YYYY-MM',
    year: 'YYYY'
};

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
var enUS = {
    time: 'Time: ',
    open: 'Open: ',
    high: 'High: ',
    low: 'Low: ',
    close: 'Close: ',
    volume: 'Volume: ',
    turnover: 'Turnover: ',
    change: 'Change: ',
    second: 'S',
    minute: '',
    hour: 'H',
    day: 'D',
    week: 'W',
    month: 'M',
    year: 'Y'
};

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
var zhCN = {
    time: '时间：',
    open: '开：',
    high: '高：',
    low: '低：',
    close: '收：',
    volume: '成交量：',
    turnover: '成交额：',
    change: '涨幅：',
    second: '秒',
    minute: '',
    hour: '小时',
    day: '天',
    week: '周',
    month: '月',
    year: '年'
};

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
var locales = {
    'zh-CN': zhCN,
    'en-US': enUS
};
function registerLocale(locale, ls) {
    locales[locale] = __assign(__assign({}, locales[locale]), ls);
}
function getSupportedLocales() {
    return Object.keys(locales);
}
function i18n(key, locale) {
    var _a;
    return (_a = locales[locale][key]) !== null && _a !== void 0 ? _a : key;
}

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
var IndicatorTooltipView = /** @class */ (function (_super) {
    __extends(IndicatorTooltipView, _super);
    function IndicatorTooltipView(widget) {
        var _this = _super.call(this, widget) || this;
        _this._activeFeatureInfo = null;
        _this._featureClickEvent = function (type, featureInfo) { return function () {
            var pane = _this.getWidget().getPane();
            pane.getChart().getChartStore().executeAction(type, featureInfo);
            return true;
        }; };
        _this._featureMouseMoveEvent = function (featureInfo) { return function () {
            _this._activeFeatureInfo = featureInfo;
            return true;
        }; };
        _this.registerEvent('mouseMoveEvent', function (_) {
            _this._activeFeatureInfo = null;
            return false;
        });
        return _this;
    }
    IndicatorTooltipView.prototype.drawImp = function (ctx) {
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chartStore = pane.getChart().getChartStore();
        var crosshair = chartStore.getCrosshair();
        if (isValid(crosshair.kLineData)) {
            var bounding = widget.getBounding();
            var _a = chartStore.getStyles().indicator.tooltip, offsetLeft = _a.offsetLeft, offsetTop = _a.offsetTop, offsetRight = _a.offsetRight;
            this.drawIndicatorTooltip(ctx, offsetLeft, offsetTop, bounding.width - offsetRight);
        }
    };
    IndicatorTooltipView.prototype.drawIndicatorTooltip = function (ctx, left, top, maxWidth) {
        var _this = this;
        var pane = this.getWidget().getPane();
        var chartStore = pane.getChart().getChartStore();
        var styles = chartStore.getStyles().indicator;
        var tooltipStyles = styles.tooltip;
        if (this.isDrawTooltip(chartStore.getCrosshair(), tooltipStyles)) {
            var indicators = chartStore.getIndicatorsByPaneId(pane.getId());
            var tooltipTitleStyles_1 = tooltipStyles.title;
            var tooltipLegendStyles_1 = tooltipStyles.legend;
            indicators.forEach(function (indicator) {
                var prevRowHeight = 0;
                var coordinate = { x: left, y: top };
                var _a = _this.getIndicatorTooltipData(indicator), name = _a.name, calcParamsText = _a.calcParamsText, legends = _a.legends, featuresStyles = _a.features;
                var nameValid = name.length > 0;
                var legendValid = legends.length > 0;
                if (nameValid || legendValid) {
                    var features = _this.classifyTooltipFeatures(featuresStyles);
                    prevRowHeight = _this.drawStandardTooltipFeatures(ctx, features[0], coordinate, indicator, left, prevRowHeight, maxWidth);
                    if (nameValid) {
                        var text = name;
                        if (calcParamsText.length > 0) {
                            text = "".concat(text).concat(calcParamsText);
                        }
                        var color = tooltipTitleStyles_1.color;
                        prevRowHeight = _this.drawStandardTooltipLegends(ctx, [
                            {
                                title: { text: '', color: color },
                                value: { text: text, color: color }
                            }
                        ], coordinate, left, prevRowHeight, maxWidth, tooltipTitleStyles_1);
                    }
                    prevRowHeight = _this.drawStandardTooltipFeatures(ctx, features[1], coordinate, indicator, left, prevRowHeight, maxWidth);
                    if (legendValid) {
                        prevRowHeight = _this.drawStandardTooltipLegends(ctx, legends, coordinate, left, prevRowHeight, maxWidth, tooltipLegendStyles_1);
                    }
                    // draw right features
                    prevRowHeight = _this.drawStandardTooltipFeatures(ctx, features[2], coordinate, indicator, left, prevRowHeight, maxWidth);
                    top = coordinate.y + prevRowHeight;
                }
            });
        }
        return top;
    };
    IndicatorTooltipView.prototype.drawStandardTooltipFeatures = function (ctx, features, coordinate, indicator, left, prevRowHeight, maxWidth) {
        var _this = this;
        if (features.length > 0) {
            var width_1 = 0;
            var height_1 = 0;
            features.forEach(function (feature) {
                var _a = feature.marginLeft, marginLeft = _a === void 0 ? 0 : _a, _b = feature.marginTop, marginTop = _b === void 0 ? 0 : _b, _c = feature.marginRight, marginRight = _c === void 0 ? 0 : _c, _d = feature.marginBottom, marginBottom = _d === void 0 ? 0 : _d, _e = feature.paddingLeft, paddingLeft = _e === void 0 ? 0 : _e, _f = feature.paddingTop, paddingTop = _f === void 0 ? 0 : _f, _g = feature.paddingRight, paddingRight = _g === void 0 ? 0 : _g, _h = feature.paddingBottom, paddingBottom = _h === void 0 ? 0 : _h, _j = feature.size, size = _j === void 0 ? 0 : _j, type = feature.type, content = feature.content;
                var contentWidth = 0;
                if (type === 'icon_font') {
                    var iconFont = content;
                    ctx.font = createFont(size, 'normal', iconFont.family);
                    contentWidth = ctx.measureText(iconFont.code).width;
                }
                else {
                    contentWidth = size;
                }
                width_1 += marginLeft + paddingLeft + contentWidth + paddingRight + marginRight;
                height_1 = Math.max(height_1, marginTop + paddingTop + size + paddingBottom + marginBottom);
            });
            if (coordinate.x + width_1 > maxWidth) {
                coordinate.x = left;
                coordinate.y += prevRowHeight;
                prevRowHeight = height_1;
            }
            else {
                prevRowHeight = Math.max(prevRowHeight, height_1);
            }
            var pane = this.getWidget().getPane();
            var paneId_1 = pane.getId();
            features.forEach(function (feature) {
                var _a, _b, _c, _d, _e;
                var _f = feature.marginLeft, marginLeft = _f === void 0 ? 0 : _f, _g = feature.marginTop, marginTop = _g === void 0 ? 0 : _g, _h = feature.marginRight, marginRight = _h === void 0 ? 0 : _h, _j = feature.paddingLeft, paddingLeft = _j === void 0 ? 0 : _j, _k = feature.paddingTop, paddingTop = _k === void 0 ? 0 : _k, _l = feature.paddingRight, paddingRight = _l === void 0 ? 0 : _l, _m = feature.paddingBottom, paddingBottom = _m === void 0 ? 0 : _m, backgroundColor = feature.backgroundColor, activeBackgroundColor = feature.activeBackgroundColor, borderRadius = feature.borderRadius, _o = feature.size, size = _o === void 0 ? 0 : _o, color = feature.color, activeColor = feature.activeColor, type = feature.type, content = feature.content;
                var finalColor = color;
                var finalBackgroundColor = backgroundColor;
                if (((_a = _this._activeFeatureInfo) === null || _a === void 0 ? void 0 : _a.paneId) === paneId_1 && ((_b = _this._activeFeatureInfo.indicator) === null || _b === void 0 ? void 0 : _b.id) === (indicator === null || indicator === void 0 ? void 0 : indicator.id) && _this._activeFeatureInfo.feature.id === feature.id) {
                    finalColor = activeColor !== null && activeColor !== void 0 ? activeColor : color;
                    finalBackgroundColor = activeBackgroundColor !== null && activeBackgroundColor !== void 0 ? activeBackgroundColor : backgroundColor;
                }
                var actionType = 'onCandleTooltipFeatureClick';
                var featureInfo = {
                    paneId: paneId_1,
                    feature: feature
                };
                if (isValid(indicator)) {
                    actionType = 'onIndicatorTooltipFeatureClick';
                    featureInfo.indicator = indicator;
                }
                var eventHandler = {
                    mouseDownEvent: _this._featureClickEvent(actionType, featureInfo),
                    mouseMoveEvent: _this._featureMouseMoveEvent(featureInfo)
                };
                var contentWidth = 0;
                if (type === 'icon_font') {
                    var iconFont = content;
                    (_c = _this.createFigure({
                        name: 'text',
                        attrs: { text: iconFont.code, x: coordinate.x + marginLeft, y: coordinate.y + marginTop },
                        styles: {
                            paddingLeft: paddingLeft,
                            paddingTop: paddingTop,
                            paddingRight: paddingRight,
                            paddingBottom: paddingBottom,
                            borderRadius: borderRadius,
                            size: size,
                            family: iconFont.family,
                            color: finalColor,
                            backgroundColor: finalBackgroundColor
                        }
                    }, eventHandler)) === null || _c === void 0 ? void 0 : _c.draw(ctx);
                    contentWidth = ctx.measureText(iconFont.code).width;
                }
                else {
                    (_d = _this.createFigure({
                        name: 'rect',
                        attrs: { x: coordinate.x + marginLeft, y: coordinate.y + marginTop, width: size, height: size },
                        styles: {
                            paddingLeft: paddingLeft,
                            paddingTop: paddingTop,
                            paddingRight: paddingRight,
                            paddingBottom: paddingBottom,
                            color: finalBackgroundColor
                        }
                    }, eventHandler)) === null || _d === void 0 ? void 0 : _d.draw(ctx);
                    var path = content;
                    (_e = _this.createFigure({
                        name: 'path',
                        attrs: { path: path.path, x: coordinate.x + marginLeft + paddingLeft, y: coordinate.y + marginTop + paddingTop, width: size, height: size },
                        styles: {
                            style: path.style,
                            lineWidth: path.lineWidth,
                            color: finalColor
                        }
                    })) === null || _e === void 0 ? void 0 : _e.draw(ctx);
                    contentWidth = size;
                }
                coordinate.x += marginLeft + paddingLeft + contentWidth + paddingRight + marginRight;
            });
        }
        return prevRowHeight;
    };
    IndicatorTooltipView.prototype.drawStandardTooltipLegends = function (ctx, legends, coordinate, left, prevRowHeight, maxWidth, styles) {
        var _this = this;
        if (legends.length > 0) {
            var marginLeft_1 = styles.marginLeft, marginTop_1 = styles.marginTop, marginRight_1 = styles.marginRight, marginBottom_1 = styles.marginBottom, size_1 = styles.size, family_1 = styles.family, weight_1 = styles.weight;
            ctx.font = createFont(size_1, weight_1, family_1);
            legends.forEach(function (data) {
                var _a, _b;
                var title = data.title;
                var value = data.value;
                var titleTextWidth = ctx.measureText(title.text).width;
                var valueTextWidth = ctx.measureText(value.text).width;
                var totalTextWidth = titleTextWidth + valueTextWidth;
                var h = marginTop_1 + size_1 + marginBottom_1;
                if (coordinate.x + marginLeft_1 + totalTextWidth + marginRight_1 > maxWidth) {
                    coordinate.x = left;
                    coordinate.y += prevRowHeight;
                    prevRowHeight = h;
                }
                else {
                    prevRowHeight = Math.max(prevRowHeight, h);
                }
                if (title.text.length > 0) {
                    (_a = _this.createFigure({
                        name: 'text',
                        attrs: { x: coordinate.x + marginLeft_1, y: coordinate.y + marginTop_1, text: title.text },
                        styles: { color: title.color, size: size_1, family: family_1, weight: weight_1 }
                    })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                }
                (_b = _this.createFigure({
                    name: 'text',
                    attrs: { x: coordinate.x + marginLeft_1 + titleTextWidth, y: coordinate.y + marginTop_1, text: value.text },
                    styles: { color: value.color, size: size_1, family: family_1, weight: weight_1 }
                })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
                coordinate.x += marginLeft_1 + totalTextWidth + marginRight_1;
            });
        }
        return prevRowHeight;
    };
    IndicatorTooltipView.prototype.isDrawTooltip = function (crosshair, styles) {
        var showRule = styles.showRule;
        return showRule === 'always' || (showRule === 'follow_cross' && isString(crosshair.paneId));
    };
    IndicatorTooltipView.prototype.getIndicatorTooltipData = function (indicator) {
        var _a;
        var chartStore = this.getWidget().getPane().getChart().getChartStore();
        var styles = chartStore.getStyles().indicator;
        var tooltipStyles = styles.tooltip;
        var tooltipTitleStyles = tooltipStyles.title;
        var name = '';
        var calcParamsText = '';
        if (tooltipTitleStyles.show) {
            if (tooltipTitleStyles.showName) {
                name = indicator.shortName;
            }
            if (tooltipTitleStyles.showParams) {
                var calcParams = indicator.calcParams;
                if (calcParams.length > 0) {
                    calcParamsText = "(".concat(calcParams.join(','), ")");
                }
            }
        }
        var tooltipData = { name: name, calcParamsText: calcParamsText, legends: [], features: tooltipStyles.features };
        var dataIndex = chartStore.getCrosshair().dataIndex;
        var result = indicator.result;
        var formatter = chartStore.getInnerFormatter();
        var decimalFold = chartStore.getDecimalFold();
        var thousandsSeparator = chartStore.getThousandsSeparator();
        var legends = [];
        if (indicator.visible) {
            var barSpace = chartStore.getBarSpace();
            var data_1 = (_a = result[dataIndex]) !== null && _a !== void 0 ? _a : {};
            var defaultValue_1 = tooltipStyles.legend.defaultValue;
            // @ts-ignore
            eachFigures(indicator, dataIndex, barSpace, styles, function (figure, figureStyles) {
                if (isString(figure.title)) {
                    var color = figureStyles.color;
                    var value = data_1[figure.key];
                    if (isNumber(value)) {
                        value = formatPrecision(value, indicator.precision);
                        if (indicator.shouldFormatBigNumber) {
                            value = formatter.formatBigNumber(value);
                        }
                        value = decimalFold.format(thousandsSeparator.format(value));
                    }
                    legends.push({ title: { text: figure.title, color: color }, value: { text: (value !== null && value !== void 0 ? value : defaultValue_1), color: color } });
                }
            });
            tooltipData.legends = legends;
        }
        if (isFunction(indicator.createTooltipDataSource)) {
            var widget = this.getWidget();
            var pane = widget.getPane();
            var chart = pane.getChart();
            var _b = indicator.createTooltipDataSource({
                chart: chart,
                indicator: indicator,
                crosshair: chartStore.getCrosshair(),
                bounding: widget.getBounding(),
                xAxis: pane.getChart().getXAxisPane().getXAxisComponent(),
                yAxis: pane.getYAxisComponentById(indicator.yAxisId)
            }), customName = _b.name, customCalcParamsText = _b.calcParamsText, customLegends = _b.legends, customFeatures = _b.features;
            if (tooltipTitleStyles.show) {
                if (isString(customName) && tooltipTitleStyles.showName) {
                    tooltipData.name = customName;
                }
                if (isString(customCalcParamsText) && tooltipTitleStyles.showParams) {
                    tooltipData.calcParamsText = customCalcParamsText;
                }
            }
            if (isValid(customFeatures)) {
                tooltipData.features = customFeatures;
            }
            if (isValid(customLegends) && indicator.visible) {
                var optimizedLegends_1 = [];
                var color_1 = styles.tooltip.legend.color;
                customLegends.forEach(function (data) {
                    var title = { text: '', color: color_1 };
                    if (isObject(data.title)) {
                        title = data.title;
                    }
                    else {
                        title.text = data.title;
                    }
                    var value = { text: '', color: color_1 };
                    if (isObject(data.value)) {
                        value = data.value;
                    }
                    else {
                        value.text = data.value;
                    }
                    if (isNumber(Number(value.text))) {
                        value.text = decimalFold.format(thousandsSeparator.format(value.text));
                    }
                    optimizedLegends_1.push({ title: title, value: value });
                });
                tooltipData.legends = optimizedLegends_1;
            }
        }
        return tooltipData;
    };
    IndicatorTooltipView.prototype.classifyTooltipFeatures = function (features) {
        var leftFeatures = [];
        var middleFeatures = [];
        var rightFeatures = [];
        features.forEach(function (feature) {
            switch (feature.position) {
                case 'left': {
                    leftFeatures.push(feature);
                    break;
                }
                case 'middle': {
                    middleFeatures.push(feature);
                    break;
                }
                case 'right': {
                    rightFeatures.push(feature);
                    break;
                }
            }
        });
        return [leftFeatures, middleFeatures, rightFeatures];
    };
    return IndicatorTooltipView;
}(View));

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
var CandleTooltipView = /** @class */ (function (_super) {
    __extends(CandleTooltipView, _super);
    function CandleTooltipView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CandleTooltipView.prototype.drawImp = function (ctx) {
        var widget = this.getWidget();
        var chartStore = widget.getPane().getChart().getChartStore();
        var crosshair = chartStore.getCrosshair();
        if (isValid(crosshair.kLineData)) {
            var bounding = widget.getBounding();
            var styles = chartStore.getStyles();
            var candleStyles = styles.candle;
            var indicatorStyles = styles.indicator;
            if (candleStyles.tooltip.showType === 'rect' && indicatorStyles.tooltip.showType === 'rect') {
                var isDrawCandleTooltip = this.isDrawTooltip(crosshair, candleStyles.tooltip);
                var isDrawIndicatorTooltip = this.isDrawTooltip(crosshair, indicatorStyles.tooltip);
                this._drawRectTooltip(ctx, isDrawCandleTooltip, isDrawIndicatorTooltip, candleStyles.tooltip.offsetTop);
            }
            else if (candleStyles.tooltip.showType === 'standard' && indicatorStyles.tooltip.showType === 'standard') {
                var _a = candleStyles.tooltip, offsetLeft = _a.offsetLeft, offsetTop = _a.offsetTop, offsetRight = _a.offsetRight;
                var maxWidth = bounding.width - offsetRight;
                var top_1 = this._drawCandleStandardTooltip(ctx, offsetLeft, offsetTop, maxWidth);
                this.drawIndicatorTooltip(ctx, offsetLeft, top_1, maxWidth);
            }
            else if (candleStyles.tooltip.showType === 'rect' && indicatorStyles.tooltip.showType === 'standard') {
                var _b = candleStyles.tooltip, offsetLeft = _b.offsetLeft, offsetTop = _b.offsetTop, offsetRight = _b.offsetRight;
                var maxWidth = bounding.width - offsetRight;
                var top_2 = this.drawIndicatorTooltip(ctx, offsetLeft, offsetTop, maxWidth);
                var isDrawCandleTooltip = this.isDrawTooltip(crosshair, candleStyles.tooltip);
                this._drawRectTooltip(ctx, isDrawCandleTooltip, false, top_2);
            }
            else {
                var _c = candleStyles.tooltip, offsetLeft = _c.offsetLeft, offsetTop = _c.offsetTop, offsetRight = _c.offsetRight;
                var maxWidth = bounding.width - offsetRight;
                var top_3 = this._drawCandleStandardTooltip(ctx, offsetLeft, offsetTop, maxWidth);
                var isDrawIndicatorTooltip = this.isDrawTooltip(crosshair, indicatorStyles.tooltip);
                this._drawRectTooltip(ctx, false, isDrawIndicatorTooltip, top_3);
            }
        }
    };
    CandleTooltipView.prototype._drawCandleStandardTooltip = function (ctx, left, top, maxWidth) {
        var _a;
        var chartStore = this.getWidget().getPane().getChart().getChartStore();
        var styles = chartStore.getStyles().candle;
        var tooltipStyles = styles.tooltip;
        var tooltipLegendStyles = tooltipStyles.legend;
        var prevRowHeight = 0;
        var coordinate = { x: left, y: top };
        var crosshair = chartStore.getCrosshair();
        if (this.isDrawTooltip(crosshair, tooltipStyles)) {
            var tooltipTitleStyles = tooltipStyles.title;
            if (tooltipTitleStyles.show) {
                var _b = (_a = chartStore.getPeriod()) !== null && _a !== void 0 ? _a : {}, _c = _b.type, type = _c === void 0 ? '' : _c, _d = _b.span, span = _d === void 0 ? '' : _d;
                var text = formatTemplateString(tooltipTitleStyles.template, __assign(__assign({}, chartStore.getSymbol()), { period: "".concat(span).concat(i18n(type, chartStore.getLocale())) }));
                var color = tooltipTitleStyles.color;
                var height = this.drawStandardTooltipLegends(ctx, [
                    {
                        title: { text: '', color: color },
                        value: { text: text, color: color }
                    }
                ], { x: left, y: top }, left, 0, maxWidth, tooltipTitleStyles);
                coordinate.y = coordinate.y + height;
            }
            var legends = this._getCandleTooltipLegends();
            var features = this.classifyTooltipFeatures(tooltipStyles.features);
            prevRowHeight = this.drawStandardTooltipFeatures(ctx, features[0], coordinate, null, left, prevRowHeight, maxWidth);
            prevRowHeight = this.drawStandardTooltipFeatures(ctx, features[1], coordinate, null, left, prevRowHeight, maxWidth);
            if (legends.length > 0) {
                prevRowHeight = this.drawStandardTooltipLegends(ctx, legends, coordinate, left, prevRowHeight, maxWidth, tooltipLegendStyles);
            }
            prevRowHeight = this.drawStandardTooltipFeatures(ctx, features[2], coordinate, null, left, prevRowHeight, maxWidth);
        }
        return coordinate.y + prevRowHeight;
    };
    CandleTooltipView.prototype._drawRectTooltip = function (ctx, isDrawCandleTooltip, isDrawIndicatorTooltip, top) {
        var _this = this;
        var _a, _b;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chartStore = pane.getChart().getChartStore();
        var styles = chartStore.getStyles();
        var candleStyles = styles.candle;
        var indicatorStyles = styles.indicator;
        var candleTooltipStyles = candleStyles.tooltip;
        var indicatorTooltipStyles = indicatorStyles.tooltip;
        if (isDrawCandleTooltip || isDrawIndicatorTooltip) {
            var candleLegends = this._getCandleTooltipLegends();
            var offsetLeft = candleTooltipStyles.offsetLeft, offsetTop = candleTooltipStyles.offsetTop, offsetRight = candleTooltipStyles.offsetRight, offsetBottom = candleTooltipStyles.offsetBottom;
            var _c = candleTooltipStyles.legend, baseLegendMarginLeft_1 = _c.marginLeft, baseLegendMarginRight_1 = _c.marginRight, baseLegendMarginTop_1 = _c.marginTop, baseLegendMarginBottom_1 = _c.marginBottom, baseLegendSize_1 = _c.size, baseLegendWeight_1 = _c.weight, baseLegendFamily_1 = _c.family;
            var _d = candleTooltipStyles.rect, rectPosition = _d.position, rectPaddingLeft = _d.paddingLeft, rectPaddingRight_1 = _d.paddingRight, rectPaddingTop = _d.paddingTop, rectPaddingBottom = _d.paddingBottom, rectOffsetLeft = _d.offsetLeft, rectOffsetRight = _d.offsetRight, rectOffsetTop = _d.offsetTop, rectOffsetBottom = _d.offsetBottom, rectBorderSize_1 = _d.borderSize, rectBorderRadius = _d.borderRadius, rectBorderColor = _d.borderColor, rectBackgroundColor = _d.color;
            var maxTextWidth_1 = 0;
            var rectWidth_1 = 0;
            var rectHeight_1 = 0;
            if (isDrawCandleTooltip) {
                ctx.font = createFont(baseLegendSize_1, baseLegendWeight_1, baseLegendFamily_1);
                candleLegends.forEach(function (data) {
                    var title = data.title;
                    var value = data.value;
                    var text = "".concat(title.text).concat(value.text);
                    var labelWidth = ctx.measureText(text).width + baseLegendMarginLeft_1 + baseLegendMarginRight_1;
                    maxTextWidth_1 = Math.max(maxTextWidth_1, labelWidth);
                });
                rectHeight_1 += (baseLegendMarginBottom_1 + baseLegendMarginTop_1 + baseLegendSize_1) * candleLegends.length;
            }
            var _e = indicatorTooltipStyles.legend, indicatorLegendMarginLeft_1 = _e.marginLeft, indicatorLegendMarginRight_1 = _e.marginRight, indicatorLegendMarginTop_1 = _e.marginTop, indicatorLegendMarginBottom_1 = _e.marginBottom, indicatorLegendSize_1 = _e.size, indicatorLegendWeight_1 = _e.weight, indicatorLegendFamily_1 = _e.family;
            var indicatorLegendsArray_1 = [];
            if (isDrawIndicatorTooltip) {
                var indicators = chartStore.getIndicatorsByPaneId(pane.getId());
                ctx.font = createFont(indicatorLegendSize_1, indicatorLegendWeight_1, indicatorLegendFamily_1);
                indicators.forEach(function (indicator) {
                    var tooltipDataLegends = _this.getIndicatorTooltipData(indicator).legends;
                    indicatorLegendsArray_1.push(tooltipDataLegends);
                    tooltipDataLegends.forEach(function (data) {
                        var title = data.title;
                        var value = data.value;
                        var text = "".concat(title.text).concat(value.text);
                        var textWidth = ctx.measureText(text).width + indicatorLegendMarginLeft_1 + indicatorLegendMarginRight_1;
                        maxTextWidth_1 = Math.max(maxTextWidth_1, textWidth);
                        rectHeight_1 += indicatorLegendMarginTop_1 + indicatorLegendMarginBottom_1 + indicatorLegendSize_1;
                    });
                });
            }
            rectWidth_1 += maxTextWidth_1;
            if (rectWidth_1 !== 0 && rectHeight_1 !== 0) {
                var crosshair = chartStore.getCrosshair();
                var bounding = widget.getBounding();
                var yAxisBounding = pane.getYAxisWidget().getBounding();
                rectWidth_1 += rectBorderSize_1 * 2 + rectPaddingLeft + rectPaddingRight_1;
                rectHeight_1 += rectBorderSize_1 * 2 + rectPaddingTop + rectPaddingBottom;
                var centerX = bounding.width / 2;
                var isPointer = rectPosition === 'pointer' && crosshair.paneId === PaneIdConstants.CANDLE;
                var isLeft = ((_a = crosshair.realX) !== null && _a !== void 0 ? _a : 0) > centerX;
                var rectX_1 = 0;
                if (isPointer) {
                    var realX = crosshair.realX;
                    if (isLeft) {
                        rectX_1 = realX - rectOffsetRight - rectWidth_1;
                    }
                    else {
                        rectX_1 = realX + rectOffsetLeft;
                    }
                }
                else {
                    var yAxis = this.getWidget().getPane().getYAxisComponentById();
                    if (isLeft) {
                        rectX_1 = rectOffsetLeft + offsetLeft;
                        if (yAxis.inside && yAxis.position === 'left') {
                            rectX_1 += yAxisBounding.width;
                        }
                    }
                    else {
                        rectX_1 = bounding.width - rectOffsetRight - rectWidth_1 - offsetRight;
                        if (yAxis.inside && yAxis.position === 'right') {
                            rectX_1 -= yAxisBounding.width;
                        }
                    }
                }
                var rectY = top + rectOffsetTop;
                if (isPointer) {
                    var y = crosshair.y;
                    rectY = y - rectHeight_1 / 2;
                    if (rectY + rectHeight_1 > bounding.height - rectOffsetBottom - offsetBottom) {
                        rectY = bounding.height - rectOffsetBottom - rectHeight_1 - offsetBottom;
                    }
                    if (rectY < top + rectOffsetTop) {
                        rectY = top + rectOffsetTop + offsetTop;
                    }
                }
                (_b = this.createFigure({
                    name: 'rect',
                    attrs: {
                        x: rectX_1,
                        y: rectY,
                        width: rectWidth_1,
                        height: rectHeight_1
                    },
                    styles: {
                        style: 'stroke_fill',
                        color: rectBackgroundColor,
                        borderColor: rectBorderColor,
                        borderSize: rectBorderSize_1,
                        borderRadius: rectBorderRadius
                    }
                })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
                var candleTextX_1 = rectX_1 + rectBorderSize_1 + rectPaddingLeft + baseLegendMarginLeft_1;
                var textY_1 = rectY + rectBorderSize_1 + rectPaddingTop;
                if (isDrawCandleTooltip) {
                    // render candle texts
                    candleLegends.forEach(function (data) {
                        var _a, _b;
                        textY_1 += baseLegendMarginTop_1;
                        var title = data.title;
                        (_a = _this.createFigure({
                            name: 'text',
                            attrs: {
                                x: candleTextX_1,
                                y: textY_1,
                                text: title.text
                            },
                            styles: {
                                color: title.color,
                                size: baseLegendSize_1,
                                family: baseLegendFamily_1,
                                weight: baseLegendWeight_1
                            }
                        })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                        var value = data.value;
                        (_b = _this.createFigure({
                            name: 'text',
                            attrs: {
                                x: rectX_1 + rectWidth_1 - rectBorderSize_1 - baseLegendMarginRight_1 - rectPaddingRight_1,
                                y: textY_1,
                                text: value.text,
                                align: 'right'
                            },
                            styles: {
                                color: value.color,
                                size: baseLegendSize_1,
                                family: baseLegendFamily_1,
                                weight: baseLegendWeight_1
                            }
                        })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
                        textY_1 += baseLegendSize_1 + baseLegendMarginBottom_1;
                    });
                }
                if (isDrawIndicatorTooltip) {
                    // render indicator legends
                    var indicatorTextX_1 = rectX_1 + rectBorderSize_1 + rectPaddingLeft + indicatorLegendMarginLeft_1;
                    indicatorLegendsArray_1.forEach(function (legends) {
                        legends.forEach(function (data) {
                            var _a, _b;
                            textY_1 += indicatorLegendMarginTop_1;
                            var title = data.title;
                            var value = data.value;
                            (_a = _this.createFigure({
                                name: 'text',
                                attrs: {
                                    x: indicatorTextX_1,
                                    y: textY_1,
                                    text: title.text
                                },
                                styles: {
                                    color: title.color,
                                    size: indicatorLegendSize_1,
                                    family: indicatorLegendFamily_1,
                                    weight: indicatorLegendWeight_1
                                }
                            })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                            (_b = _this.createFigure({
                                name: 'text',
                                attrs: {
                                    x: rectX_1 + rectWidth_1 - rectBorderSize_1 - indicatorLegendMarginRight_1 - rectPaddingRight_1,
                                    y: textY_1,
                                    text: value.text,
                                    align: 'right'
                                },
                                styles: {
                                    color: value.color,
                                    size: indicatorLegendSize_1,
                                    family: indicatorLegendFamily_1,
                                    weight: indicatorLegendWeight_1
                                }
                            })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
                            textY_1 += indicatorLegendSize_1 + indicatorLegendMarginBottom_1;
                        });
                    });
                }
            }
        }
    };
    CandleTooltipView.prototype._getCandleTooltipLegends = function () {
        var _a, _b, _c, _d, _e, _f, _g, _h;
        var chartStore = this.getWidget().getPane().getChart().getChartStore();
        var styles = chartStore.getStyles().candle;
        var dataList = chartStore.getDataList();
        var formatter = chartStore.getInnerFormatter();
        var decimalFold = chartStore.getDecimalFold();
        var thousandsSeparator = chartStore.getThousandsSeparator();
        var locale = chartStore.getLocale();
        var _j = (_a = chartStore.getSymbol()) !== null && _a !== void 0 ? _a : {}, _k = _j.pricePrecision, pricePrecision = _k === void 0 ? SymbolDefaultPrecisionConstants.PRICE : _k, _l = _j.volumePrecision, volumePrecision = _l === void 0 ? SymbolDefaultPrecisionConstants.VOLUME : _l;
        var period = chartStore.getPeriod();
        var dataIndex = (_b = chartStore.getCrosshair().dataIndex) !== null && _b !== void 0 ? _b : 0;
        var tooltipStyles = styles.tooltip;
        var _m = tooltipStyles.legend, textColor = _m.color, defaultValue = _m.defaultValue, template = _m.template;
        var prev = (_c = dataList[dataIndex - 1]) !== null && _c !== void 0 ? _c : null;
        var current = dataList[dataIndex];
        var prevClose = (_d = prev === null || prev === void 0 ? void 0 : prev.close) !== null && _d !== void 0 ? _d : current.close;
        var changeValue = current.close - prevClose;
        var mapping = __assign(__assign({}, current), { time: formatter.formatDate(current.timestamp, PeriodTypeCrosshairTooltipFormat[(_e = period === null || period === void 0 ? void 0 : period.type) !== null && _e !== void 0 ? _e : 'day'], 'tooltip'), open: decimalFold.format(thousandsSeparator.format(formatPrecision(current.open, pricePrecision))), high: decimalFold.format(thousandsSeparator.format(formatPrecision(current.high, pricePrecision))), low: decimalFold.format(thousandsSeparator.format(formatPrecision(current.low, pricePrecision))), close: decimalFold.format(thousandsSeparator.format(formatPrecision(current.close, pricePrecision))), volume: decimalFold.format(thousandsSeparator.format(formatter.formatBigNumber(formatPrecision((_f = current.volume) !== null && _f !== void 0 ? _f : defaultValue, volumePrecision)))), turnover: decimalFold.format(thousandsSeparator.format(formatPrecision((_g = current.turnover) !== null && _g !== void 0 ? _g : defaultValue, pricePrecision))), change: prevClose === 0 ? defaultValue : "".concat(thousandsSeparator.format(formatPrecision((changeValue / prevClose) * 100)), "%") });
        // @ts-ignore
        var legends = isFunction(template) ? template({ prev: prev, current: current, next: (_h = dataList[dataIndex + 1]) !== null && _h !== void 0 ? _h : null }, styles) : template;
        return legends.map(function (_a) {
            var title = _a.title, value = _a.value;
            var t = { text: '', color: textColor };
            if (isObject(title)) {
                t = __assign({}, title);
            }
            else {
                t.text = title;
            }
            t.text = i18n(t.text, locale);
            var v = { text: defaultValue, color: textColor };
            if (isObject(value)) {
                v = __assign({}, value);
            }
            else {
                v.text = value;
            }
            if (isValid(/{change}/.exec(v.text))) {
                v.color = changeValue === 0 ? styles.priceMark.last.noChangeColor : changeValue > 0 ? styles.priceMark.last.upColor : styles.priceMark.last.downColor;
            }
            v.text = formatTemplateString(v.text, mapping);
            return { title: t, value: v };
        });
    };
    return CandleTooltipView;
}(IndicatorTooltipView));

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
var CrosshairFeatureView = /** @class */ (function (_super) {
    __extends(CrosshairFeatureView, _super);
    function CrosshairFeatureView(widget) {
        var _this = _super.call(this, widget) || this;
        _this._activeFeatureInfo = null;
        _this._featureClickEvent = function (featureInfo) { return function () {
            var pane = _this.getWidget().getPane();
            pane.getChart().getChartStore().executeAction('onCrosshairFeatureClick', featureInfo);
            return true;
        }; };
        _this._featureMouseMoveEvent = function (featureInfo) { return function () {
            _this._activeFeatureInfo = featureInfo;
            _this.getWidget().setForceCursor('pointer');
            return true;
        }; };
        _this.registerEvent('mouseMoveEvent', function (_) {
            _this._activeFeatureInfo = null;
            _this.getWidget().setForceCursor(null);
            return false;
        });
        return _this;
    }
    CrosshairFeatureView.prototype.drawImp = function (ctx) {
        var _this = this;
        var _a, _b;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chartStore = widget.getPane().getChart().getChartStore();
        var crosshair = chartStore.getCrosshair();
        var weight = this.getWidget();
        var yAxis = weight.getPane().getYAxisComponentById();
        if (isString(crosshair.paneId) && crosshair.paneId === pane.getId() && yAxis.isInCandle()) {
            var styles = chartStore.getStyles().crosshair;
            var features = styles.horizontal.features;
            if (styles.show && styles.horizontal.show && features.length > 0) {
                var isRight_1 = yAxis.position === 'right';
                var bounding = weight.getBounding();
                var yAxisTextWidth = 0;
                var horizontalTextStyles = styles.horizontal.text;
                if (yAxis.inside && horizontalTextStyles.show) {
                    var value = yAxis.convertFromPixel(crosshair.y);
                    var range = yAxis.getRange();
                    var text = yAxis.displayValueToText(yAxis.realValueToDisplayValue(yAxis.valueToRealValue(value, { range: range }), { range: range }), (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE);
                    text = chartStore.getDecimalFold().format(chartStore.getThousandsSeparator().format(text));
                    yAxisTextWidth = horizontalTextStyles.paddingLeft + calcTextWidth(text, horizontalTextStyles.size, horizontalTextStyles.weight, horizontalTextStyles.family) + horizontalTextStyles.paddingRight;
                }
                var x_1 = yAxisTextWidth;
                if (isRight_1) {
                    x_1 = bounding.width - yAxisTextWidth;
                }
                var y_1 = crosshair.y;
                features.forEach(function (feature) {
                    var _a, _b, _c, _d;
                    var _e = feature.marginLeft, marginLeft = _e === void 0 ? 0 : _e, _f = feature.marginTop, marginTop = _f === void 0 ? 0 : _f, _g = feature.marginRight, marginRight = _g === void 0 ? 0 : _g, _h = feature.paddingLeft, paddingLeft = _h === void 0 ? 0 : _h, _j = feature.paddingTop, paddingTop = _j === void 0 ? 0 : _j, _k = feature.paddingRight, paddingRight = _k === void 0 ? 0 : _k, _l = feature.paddingBottom, paddingBottom = _l === void 0 ? 0 : _l, color = feature.color, activeColor = feature.activeColor, backgroundColor = feature.backgroundColor, activeBackgroundColor = feature.activeBackgroundColor, borderRadius = feature.borderRadius, _m = feature.size, size = _m === void 0 ? 0 : _m, type = feature.type, content = feature.content;
                    var width = size;
                    if (type === 'icon_font') {
                        var iconFont = content;
                        width = paddingLeft + calcTextWidth(iconFont.code, size, 'normal', iconFont.family) + paddingRight;
                    }
                    if (isRight_1) {
                        x_1 -= width + marginRight;
                    }
                    else {
                        x_1 += marginLeft;
                    }
                    var finalColor = color;
                    var finalBackgroundColor = backgroundColor;
                    if (((_a = _this._activeFeatureInfo) === null || _a === void 0 ? void 0 : _a.feature.id) === feature.id) {
                        finalColor = activeColor !== null && activeColor !== void 0 ? activeColor : color;
                        finalBackgroundColor = activeBackgroundColor !== null && activeBackgroundColor !== void 0 ? activeBackgroundColor : backgroundColor;
                    }
                    var eventHandler = {
                        mouseDownEvent: _this._featureClickEvent({ crosshair: crosshair, feature: feature }),
                        mouseMoveEvent: _this._featureMouseMoveEvent({ crosshair: crosshair, feature: feature })
                    };
                    if (type === 'icon_font') {
                        var iconFont = content;
                        (_b = _this.createFigure({
                            name: 'text',
                            attrs: {
                                text: iconFont.code,
                                x: x_1,
                                y: y_1 + marginTop,
                                baseline: 'middle'
                            },
                            styles: {
                                paddingLeft: paddingLeft,
                                paddingTop: paddingTop,
                                paddingRight: paddingRight,
                                paddingBottom: paddingBottom,
                                borderRadius: borderRadius,
                                size: size,
                                family: iconFont.family,
                                color: finalColor,
                                backgroundColor: finalBackgroundColor
                            }
                        }, eventHandler)) === null || _b === void 0 ? void 0 : _b.draw(ctx);
                    }
                    else {
                        (_c = _this.createFigure({
                            name: 'rect',
                            attrs: { x: x_1, y: y_1 + marginTop - size / 2, width: size, height: size },
                            styles: {
                                paddingLeft: paddingLeft,
                                paddingTop: paddingTop,
                                paddingRight: paddingRight,
                                paddingBottom: paddingBottom,
                                color: finalBackgroundColor
                            }
                        }, eventHandler)) === null || _c === void 0 ? void 0 : _c.draw(ctx);
                        var path = content;
                        (_d = _this.createFigure({
                            name: 'path',
                            attrs: { path: path.path, x: x_1 + paddingLeft, y: y_1 + marginTop + paddingTop - size / 2, width: size, height: size },
                            styles: {
                                style: path.style,
                                lineWidth: path.lineWidth,
                                color: finalColor
                            }
                        })) === null || _d === void 0 ? void 0 : _d.draw(ctx);
                    }
                    if (isRight_1) {
                        x_1 -= marginLeft;
                    }
                    else {
                        x_1 += width + marginRight;
                    }
                });
            }
        }
    };
    return CrosshairFeatureView;
}(View));

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
var CrosshairLineView = /** @class */ (function (_super) {
    __extends(CrosshairLineView, _super);
    function CrosshairLineView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CrosshairLineView.prototype.drawImp = function (ctx) {
        var widget = this.getWidget();
        var pane = widget.getPane();
        var bounding = widget.getBounding();
        var chartStore = widget.getPane().getChart().getChartStore();
        var crosshair = chartStore.getCrosshair();
        var styles = chartStore.getStyles().crosshair;
        if (isString(crosshair.paneId) && styles.show) {
            if (crosshair.paneId === pane.getId()) {
                var y = crosshair.y;
                this._drawLine(ctx, [
                    { x: 0, y: y },
                    { x: bounding.width, y: y }
                ], styles.horizontal);
            }
            var x = crosshair.realX;
            this._drawLine(ctx, [
                { x: x, y: 0 },
                { x: x, y: bounding.height }
            ], styles.vertical);
        }
    };
    CrosshairLineView.prototype._drawLine = function (ctx, coordinates, styles) {
        var _a;
        if (styles.show) {
            var lineStyles = styles.line;
            if (lineStyles.show) {
                (_a = this.createFigure({
                    name: 'line',
                    attrs: { coordinates: coordinates },
                    styles: lineStyles
                })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
            }
        }
    };
    return CrosshairLineView;
}(View));

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
var GridView = /** @class */ (function (_super) {
    __extends(GridView, _super);
    function GridView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    GridView.prototype.drawImp = function (ctx) {
        var _a, _b;
        var widget = this.getWidget();
        var pane = this.getWidget().getPane();
        var chart = pane.getChart();
        var bounding = widget.getBounding();
        var styles = chart.getStyles().grid;
        var show = styles.show;
        if (show) {
            ctx.save();
            ctx.globalCompositeOperation = 'destination-over';
            var horizontalStyles = styles.horizontal;
            var horizontalShow = horizontalStyles.show;
            if (horizontalShow) {
                var yAxis = pane.getYAxisComponentById();
                var attrs = yAxis.getTicks().map(function (tick) { return ({
                    coordinates: [
                        { x: 0, y: tick.coord },
                        { x: bounding.width, y: tick.coord }
                    ]
                }); });
                (_a = this.createFigure({
                    name: 'line',
                    attrs: attrs,
                    styles: horizontalStyles
                })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
            }
            var verticalStyles = styles.vertical;
            var verticalShow = verticalStyles.show;
            if (verticalShow) {
                var xAxis = chart.getXAxisPane().getXAxisComponent();
                var attrs = xAxis.getTicks().map(function (tick) { return ({
                    coordinates: [
                        { x: tick.coord, y: 0 },
                        { x: tick.coord, y: bounding.height }
                    ]
                }); });
                (_b = this.createFigure({
                    name: 'line',
                    attrs: attrs,
                    styles: verticalStyles
                })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
            }
            ctx.restore();
        }
    };
    return GridView;
}(View));

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
var IndicatorView = /** @class */ (function (_super) {
    __extends(IndicatorView, _super);
    function IndicatorView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    IndicatorView.prototype.getCandleBarOptions = function () {
        var e_1, _a;
        var pane = this.getWidget().getPane();
        var chartStore = pane.getChart().getChartStore();
        var indicators = chartStore.getIndicatorsByPaneId(pane.getId());
        try {
            for (var indicators_1 = __values(indicators), indicators_1_1 = indicators_1.next(); !indicators_1_1.done; indicators_1_1 = indicators_1.next()) {
                var indicator = indicators_1_1.value;
                var yAxis = pane.getYAxisComponentById(indicator.yAxisId);
                if (indicator.shouldOhlc && indicator.visible && !yAxis.isInCandle()) {
                    var indicatorStyles = indicator.styles;
                    var defaultStyles = chartStore.getStyles().indicator;
                    var compareRule = formatValue(indicatorStyles, 'ohlc.compareRule', defaultStyles.ohlc.compareRule);
                    var upColor = formatValue(indicatorStyles, 'ohlc.upColor', defaultStyles.ohlc.upColor);
                    var downColor = formatValue(indicatorStyles, 'ohlc.downColor', defaultStyles.ohlc.downColor);
                    var noChangeColor = formatValue(indicatorStyles, 'ohlc.noChangeColor', defaultStyles.ohlc.noChangeColor);
                    return {
                        yAxisId: indicator.yAxisId,
                        type: 'ohlc',
                        styles: {
                            compareRule: compareRule,
                            upColor: upColor,
                            downColor: downColor,
                            noChangeColor: noChangeColor,
                            upBorderColor: upColor,
                            downBorderColor: downColor,
                            noChangeBorderColor: noChangeColor,
                            upWickColor: upColor,
                            downWickColor: downColor,
                            noChangeWickColor: noChangeColor
                        }
                    };
                }
            }
        }
        catch (e_1_1) { e_1 = { error: e_1_1 }; }
        finally {
            try {
                if (indicators_1_1 && !indicators_1_1.done && (_a = indicators_1.return)) _a.call(indicators_1);
            }
            finally { if (e_1) throw e_1.error; }
        }
        return null;
    };
    IndicatorView.prototype.drawImp = function (ctx) {
        var _this = this;
        _super.prototype.drawImp.call(this, ctx);
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chart = pane.getChart();
        var bounding = widget.getBounding();
        var xAxis = chart.getXAxisPane().getXAxisComponent();
        var chartStore = chart.getChartStore();
        var indicators = chartStore.getIndicatorsByPaneId(pane.getId());
        var defaultStyles = chartStore.getStyles().indicator;
        ctx.save();
        indicators.forEach(function (indicator) {
            var yAxis = pane.getYAxisComponentById(indicator.yAxisId);
            if (indicator.visible) {
                if (indicator.zLevel < 0) {
                    ctx.globalCompositeOperation = 'destination-over';
                }
                else {
                    ctx.globalCompositeOperation = 'source-over';
                }
                var isCover = false;
                if (indicator.draw !== null) {
                    ctx.save();
                    isCover = indicator.draw({
                        ctx: ctx,
                        chart: chart,
                        indicator: indicator,
                        bounding: bounding,
                        xAxis: xAxis,
                        yAxis: yAxis
                    });
                    ctx.restore();
                }
                if (!isCover) {
                    var result_1 = indicator.result;
                    var lines_1 = [];
                    _this.eachChildren(function (data, barSpace) {
                        var _a, _b, _c;
                        var halfGapBar = barSpace.halfGapBar;
                        var dataIndex = data.dataIndex, x = data.x;
                        var prevX = xAxis.convertToPixel(dataIndex - 1);
                        var nextX = xAxis.convertToPixel(dataIndex + 1);
                        var prevData = (_a = result_1[dataIndex - 1]) !== null && _a !== void 0 ? _a : null;
                        var currentData = (_b = result_1[dataIndex]) !== null && _b !== void 0 ? _b : null;
                        var nextData = (_c = result_1[dataIndex + 1]) !== null && _c !== void 0 ? _c : null;
                        var prevCoordinate = { x: prevX };
                        var currentCoordinate = { x: x };
                        var nextCoordinate = { x: nextX };
                        indicator.figures.forEach(function (_a) {
                            var key = _a.key;
                            var prevValue = prevData === null || prevData === void 0 ? void 0 : prevData[key];
                            if (isNumber(prevValue)) {
                                prevCoordinate[key] = yAxis.convertToPixel(prevValue);
                            }
                            var currentValue = currentData === null || currentData === void 0 ? void 0 : currentData[key];
                            if (isNumber(currentValue)) {
                                currentCoordinate[key] = yAxis.convertToPixel(currentValue);
                            }
                            var nextValue = nextData === null || nextData === void 0 ? void 0 : nextData[key];
                            if (isNumber(nextValue)) {
                                nextCoordinate[key] = yAxis.convertToPixel(nextValue);
                            }
                        });
                        eachFigures(indicator, dataIndex, barSpace, defaultStyles, function (figure, figureStyles, figureIndex) {
                            var _a, _b, _c, _d, _e;
                            if (isValid(currentData === null || currentData === void 0 ? void 0 : currentData[figure.key])) {
                                var valueY = currentCoordinate[figure.key];
                                var attrs = (_a = figure.attrs) === null || _a === void 0 ? void 0 : _a.call(figure, {
                                    data: { prev: prevData, current: currentData, next: nextData },
                                    coordinate: { prev: prevCoordinate, current: currentCoordinate, next: nextCoordinate },
                                    bounding: bounding,
                                    barSpace: barSpace,
                                    xAxis: xAxis,
                                    yAxis: yAxis
                                });
                                switch (figure.type) {
                                    case 'text': {
                                        attrs = __assign({ x: x, y: valueY, text: currentData === null || currentData === void 0 ? void 0 : currentData[figure.key], align: 'center', baseline: 'middle' }, attrs);
                                        break;
                                    }
                                    case 'circle': {
                                        attrs = __assign({ x: x, y: valueY, r: Math.max(1, halfGapBar) }, attrs);
                                        break;
                                    }
                                    case 'rect':
                                    case 'bar': {
                                        var baseValue = (_b = figure.baseValue) !== null && _b !== void 0 ? _b : yAxis.getRange().from;
                                        var baseValueY = yAxis.convertToPixel(baseValue);
                                        var height = Math.abs(baseValueY - valueY);
                                        if (baseValue !== (currentData === null || currentData === void 0 ? void 0 : currentData[figure.key])) {
                                            height = Math.max(1, height);
                                        }
                                        var y = 0;
                                        if (valueY > baseValueY) {
                                            y = baseValueY;
                                        }
                                        else {
                                            y = valueY;
                                        }
                                        var barWidth = (_c = attrs === null || attrs === void 0 ? void 0 : attrs.width) !== null && _c !== void 0 ? _c : halfGapBar * 2;
                                        attrs = __assign({ x: x - barWidth / 2, y: y, width: Math.max(1, barWidth), height: height }, attrs);
                                        break;
                                    }
                                    case 'line': {
                                        if (!isValid(lines_1[figureIndex])) {
                                            lines_1[figureIndex] = [];
                                        }
                                        if (isNumber(currentCoordinate[figure.key]) && isNumber(nextCoordinate[figure.key])) {
                                            lines_1[figureIndex].push({
                                                coordinates: (_d = attrs === null || attrs === void 0 ? void 0 : attrs.coordinates) !== null && _d !== void 0 ? _d : [
                                                    { x: currentCoordinate.x, y: currentCoordinate[figure.key] },
                                                    { x: nextCoordinate.x, y: nextCoordinate[figure.key] }
                                                ],
                                                styles: figureStyles
                                            });
                                        }
                                        break;
                                    }
                                }
                                var type = figure.type;
                                if (isValid(attrs) && type !== 'line') {
                                    (_e = _this.createFigure({
                                        name: type === 'bar' ? 'rect' : type,
                                        attrs: attrs,
                                        styles: figureStyles
                                    })) === null || _e === void 0 ? void 0 : _e.draw(ctx);
                                }
                            }
                        });
                    });
                    // merge line and render
                    lines_1.forEach(function (items) {
                        var _a, _b, _c, _d;
                        if (items.length > 1) {
                            var mergeLines = [
                                {
                                    coordinates: [items[0].coordinates[0], items[0].coordinates[1]],
                                    styles: items[0].styles
                                }
                            ];
                            for (var i = 1; i < items.length; i++) {
                                var lastMergeLine = mergeLines[mergeLines.length - 1];
                                var current = items[i];
                                var lastMergeLineLastCoordinate = lastMergeLine.coordinates[lastMergeLine.coordinates.length - 1];
                                if (lastMergeLineLastCoordinate.x === current.coordinates[0].x &&
                                    lastMergeLineLastCoordinate.y === current.coordinates[0].y &&
                                    lastMergeLine.styles.style === current.styles.style &&
                                    lastMergeLine.styles.color === current.styles.color &&
                                    lastMergeLine.styles.size === current.styles.size &&
                                    lastMergeLine.styles.smooth === current.styles.smooth &&
                                    ((_a = lastMergeLine.styles.dashedValue) === null || _a === void 0 ? void 0 : _a[0]) === ((_b = current.styles.dashedValue) === null || _b === void 0 ? void 0 : _b[0]) &&
                                    ((_c = lastMergeLine.styles.dashedValue) === null || _c === void 0 ? void 0 : _c[1]) === ((_d = current.styles.dashedValue) === null || _d === void 0 ? void 0 : _d[1])) {
                                    lastMergeLine.coordinates.push(current.coordinates[1]);
                                }
                                else {
                                    mergeLines.push({
                                        coordinates: [current.coordinates[0], current.coordinates[1]],
                                        styles: current.styles
                                    });
                                }
                            }
                            mergeLines.forEach(function (_a) {
                                var _b;
                                var coordinates = _a.coordinates, styles = _a.styles;
                                (_b = _this.createFigure({
                                    name: 'line',
                                    attrs: { coordinates: coordinates },
                                    styles: styles
                                })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
                            });
                        }
                    });
                }
            }
        });
        ctx.restore();
    };
    return IndicatorView;
}(CandleBarView));

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
function checkOverlayFigureEvent(targetEventType, figure) {
    var _a;
    var ignoreEvent = (_a = figure === null || figure === void 0 ? void 0 : figure.ignoreEvent) !== null && _a !== void 0 ? _a : false;
    if (isBoolean(ignoreEvent)) {
        return !ignoreEvent;
    }
    return !ignoreEvent.includes(targetEventType);
}
var OVERLAY_DRAW_STEP_START = 1;
var OVERLAY_DRAW_STEP_FINISHED = -1;
var OVERLAY_ID_PREFIX = 'overlay_';
var OVERLAY_FIGURE_KEY_PREFIX = 'overlay_figure_';
var OverlayImp = /** @class */ (function () {
    function OverlayImp(overlay) {
        this.groupId = '';
        this.totalStep = 1;
        this.currentStep = OVERLAY_DRAW_STEP_START;
        this.drawingMode = 'step';
        this.lock = false;
        this.visible = true;
        this.zLevel = 0;
        this.needDefaultPointFigure = false;
        this.needDefaultXAxisFigure = false;
        this.needDefaultYAxisFigure = false;
        this.mode = 'normal';
        this.modeSensitivity = 8;
        this.points = [];
        this.styles = null;
        this.createPointFigures = null;
        this.createXAxisFigures = null;
        this.createYAxisFigures = null;
        this.performEventPressedMove = null;
        this.performEventMoveForDrawing = null;
        this.onDrawStart = null;
        this.onDrawing = null;
        this.onDrawEnd = null;
        this.onClick = null;
        this.onDoubleClick = null;
        this.onRightClick = null;
        this.onPressedMoveStart = null;
        this.onPressedMoving = null;
        this.onPressedMoveEnd = null;
        this.onMouseMove = null;
        this.onMouseEnter = null;
        this.onMouseLeave = null;
        this.onRemoved = null;
        this.onSelected = null;
        this.onDeselected = null;
        this._prevZLevel = 0;
        this._prevPressedPoint = null;
        this._prevPressedPoints = [];
        this.override(overlay);
    }
    OverlayImp.prototype.override = function (overlay) {
        var _a, _b;
        this._prevOverlay = clone(__assign(__assign({}, this), { _prevOverlay: null }));
        var id = overlay.id, name = overlay.name; overlay.currentStep; var points = overlay.points, styles = overlay.styles, others = __rest(overlay, ["id", "name", "currentStep", "points", "styles"]);
        merge(this, others);
        if (!isString(this.name)) {
            this.name = name !== null && name !== void 0 ? name : '';
        }
        if (!isString(this.id) && isString(id)) {
            this.id = id;
        }
        if (isValid(styles)) {
            (_a = this.styles) !== null && _a !== void 0 ? _a : (this.styles = {});
            merge(this.styles, styles);
        }
        if (isArray(points) && points.length > 0) {
            this.points = __spreadArray([], __read(points), false);
            this.currentStep = OVERLAY_DRAW_STEP_FINISHED;
            var lastIndex = this.points.length - 1;
            var lastPoint = this.points[lastIndex];
            if (lastIndex > 0 && isValid(lastPoint)) {
                (_b = this.performEventPressedMove) === null || _b === void 0 ? void 0 : _b.call(this, {
                    currentStep: this.currentStep,
                    mode: this.mode,
                    points: this.points,
                    performPointIndex: lastIndex,
                    performPoint: lastPoint
                });
            }
        }
    };
    OverlayImp.prototype.getPrevZLevel = function () {
        return this._prevZLevel;
    };
    OverlayImp.prototype.setPrevZLevel = function (zLevel) {
        this._prevZLevel = zLevel;
    };
    OverlayImp.prototype.shouldUpdate = function () {
        var sort = this._prevOverlay.zLevel !== this.zLevel;
        var draw = sort || JSON.stringify(this._prevOverlay.points) !== JSON.stringify(this.points) || this._prevOverlay.visible !== this.visible || this._prevOverlay.extendData !== this.extendData || this._prevOverlay.styles !== this.styles;
        return { sort: sort, draw: draw };
    };
    OverlayImp.prototype.nextStep = function () {
        if (this.currentStep === this.totalStep - 1) {
            this.currentStep = OVERLAY_DRAW_STEP_FINISHED;
        }
        else {
            this.currentStep++;
        }
    };
    OverlayImp.prototype.forceComplete = function () {
        this.currentStep = OVERLAY_DRAW_STEP_FINISHED;
    };
    OverlayImp.prototype.isDrawing = function () {
        return this.currentStep !== OVERLAY_DRAW_STEP_FINISHED;
    };
    OverlayImp.prototype.isStart = function () {
        return this.currentStep === OVERLAY_DRAW_STEP_START;
    };
    OverlayImp.prototype.isContinuousDrawingMode = function () {
        return this.drawingMode === 'continuous';
    };
    /**
     * Start continuous drawing - set first point
     */
    OverlayImp.prototype.startContinuousDrawing = function (point) {
        this.points = [];
        this.continuousDrawingModeEventMoveForDrawing(point);
        this.currentStep = 2; // Mark as actively drawing
    };
    /**
     * Add a point during continuous drawing mode
     */
    OverlayImp.prototype.continuousDrawingModeEventMoveForDrawing = function (point) {
        var newPoint = {};
        if (isNumber(point.timestamp)) {
            newPoint.timestamp = point.timestamp;
        }
        if (isNumber(point.dataIndex)) {
            newPoint.dataIndex = point.dataIndex;
        }
        if (isNumber(point.value)) {
            newPoint.value = point.value;
        }
        this.points.push(newPoint);
        return true;
    };
    OverlayImp.prototype.stepDrawingModeEventMoveForDrawing = function (point) {
        var _a;
        var pointIndex = this.currentStep - 1;
        var newPoint = {};
        if (isNumber(point.timestamp)) {
            newPoint.timestamp = point.timestamp;
        }
        if (isNumber(point.dataIndex)) {
            newPoint.dataIndex = point.dataIndex;
        }
        if (isNumber(point.value)) {
            newPoint.value = point.value;
        }
        this.points[pointIndex] = newPoint;
        (_a = this.performEventMoveForDrawing) === null || _a === void 0 ? void 0 : _a.call(this, {
            currentStep: this.currentStep,
            mode: this.mode,
            points: this.points,
            performPointIndex: pointIndex,
            performPoint: newPoint
        });
    };
    OverlayImp.prototype.eventPressedPointMove = function (point, pointIndex) {
        var _a;
        if (isNumber(point.timestamp)) {
            this.points[pointIndex].timestamp = point.timestamp;
        }
        if (isNumber(point.dataIndex)) {
            this.points[pointIndex].dataIndex = point.dataIndex;
        }
        if (isNumber(point.value)) {
            this.points[pointIndex].value = point.value;
        }
        (_a = this.performEventPressedMove) === null || _a === void 0 ? void 0 : _a.call(this, {
            currentStep: this.currentStep,
            points: this.points,
            mode: this.mode,
            performPointIndex: pointIndex,
            performPoint: this.points[pointIndex]
        });
    };
    OverlayImp.prototype.startPressedMove = function (point) {
        this._prevPressedPoint = __assign({}, point);
        this._prevPressedPoints = clone(this.points);
    };
    OverlayImp.prototype.eventPressedOtherMove = function (point, chartStore) {
        var _this = this;
        if (this._prevPressedPoint !== null) {
            var difDataIndex_1 = null;
            if (isNumber(point.dataIndex) && isNumber(this._prevPressedPoint.dataIndex)) {
                difDataIndex_1 = point.dataIndex - this._prevPressedPoint.dataIndex;
            }
            var difValue_1 = null;
            if (isNumber(point.value) && isNumber(this._prevPressedPoint.value)) {
                difValue_1 = point.value - this._prevPressedPoint.value;
            }
            this.points = this._prevPressedPoints.map(function (p) {
                var _a, _b;
                var newPoint = __assign({}, p);
                if (isNumber(difDataIndex_1) && (isNumber(p.dataIndex) || isNumber(p.timestamp))) {
                    var dataIndex = isNumber(p.timestamp) ? (_this.isContinuousDrawingMode() ? chartStore.timestampToFloatIndex(p.timestamp) : chartStore.timestampToDataIndex(p.timestamp)) : p.dataIndex;
                    newPoint.dataIndex = dataIndex + difDataIndex_1;
                    newPoint.timestamp = _this.isContinuousDrawingMode() ? ((_a = chartStore.floatIndexToTimestamp(newPoint.dataIndex)) !== null && _a !== void 0 ? _a : undefined) : ((_b = chartStore.dataIndexToTimestamp(newPoint.dataIndex)) !== null && _b !== void 0 ? _b : undefined);
                }
                if (isNumber(difValue_1) && isNumber(p.value)) {
                    newPoint.value = p.value + difValue_1;
                }
                return newPoint;
            });
        }
    };
    OverlayImp.extend = function (template) {
        var Custom = /** @class */ (function (_super) {
            __extends(Custom, _super);
            function Custom() {
                return _super.call(this, template) || this;
            }
            return Custom;
        }(OverlayImp));
        return Custom;
    };
    return OverlayImp;
}());

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
var OverlayView = /** @class */ (function (_super) {
    __extends(OverlayView, _super);
    function OverlayView(widget) {
        var _this = _super.call(this, widget) || this;
        _this._initEvent();
        return _this;
    }
    OverlayView.prototype._initEvent = function () {
        var _this = this;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var paneId = pane.getId();
        var chart = pane.getChart();
        var chartStore = chart.getChartStore();
        this.registerEvent('mouseMoveEvent', function (event) {
            var _a;
            var progressOverlayInfo = chartStore.getProgressOverlayInfo();
            if (progressOverlayInfo !== null) {
                var overlay = progressOverlayInfo.overlay;
                var progressOverlayPaneId = progressOverlayInfo.paneId;
                if (overlay.isStart()) {
                    chartStore.updateProgressOverlayInfo(paneId);
                    progressOverlayPaneId = paneId;
                }
                var index = overlay.points.length - 1;
                if (overlay.isDrawing() && progressOverlayPaneId === paneId) {
                    overlay.stepDrawingModeEventMoveForDrawing(_this._coordinateToPoint(overlay, event));
                    (_a = overlay.onDrawing) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: chart, overlay: overlay }, event));
                }
                return _this._figureMouseMoveEvent(overlay, 'point', index, { key: "".concat(OVERLAY_FIGURE_KEY_PREFIX, "point_").concat(index), type: 'circle', attrs: {} })(event);
            }
            chartStore.setHoverOverlayInfo({
                paneId: paneId,
                overlay: null,
                figureType: 'none',
                figureIndex: -1,
                figure: null
            }, function (o, f) { return _this._processOverlayMouseEnterEvent(o, f, event); }, function (o, f) { return _this._processOverlayMouseLeaveEvent(o, f, event); });
            widget.setForceCursor(null);
            return false;
        })
            .registerEvent('mouseClickEvent', function (event) {
            var _a, _b;
            var progressOverlayInfo = chartStore.getProgressOverlayInfo();
            if (progressOverlayInfo !== null) {
                var overlay = progressOverlayInfo.overlay;
                var progressOverlayPaneId = progressOverlayInfo.paneId;
                if (overlay.isStart()) {
                    chartStore.updateProgressOverlayInfo(paneId, true);
                    progressOverlayPaneId = paneId;
                }
                var index = overlay.points.length - 1;
                if (overlay.isDrawing() && progressOverlayPaneId === paneId) {
                    overlay.stepDrawingModeEventMoveForDrawing(_this._coordinateToPoint(overlay, event));
                    (_a = overlay.onDrawing) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: chart, overlay: overlay }, event));
                    overlay.nextStep();
                    if (!overlay.isDrawing()) {
                        chartStore.progressOverlayComplete();
                        (_b = overlay.onDrawEnd) === null || _b === void 0 ? void 0 : _b.call(overlay, __assign({ chart: chart, overlay: overlay }, event));
                    }
                }
                return _this._figureMouseClickEvent(overlay, 'point', index, {
                    key: "".concat(OVERLAY_FIGURE_KEY_PREFIX, "point_").concat(index),
                    type: 'circle',
                    attrs: {}
                })(event);
            }
            chartStore.setClickOverlayInfo({
                paneId: paneId,
                overlay: null,
                figureType: 'none',
                figureIndex: -1,
                figure: null
            }, function (o, f) { return _this._processOverlaySelectedEvent(o, f, event); }, function (o, f) { return _this._processOverlayDeselectedEvent(o, f, event); });
            return false;
        })
            .registerEvent('mouseDoubleClickEvent', function (event) {
            var _a;
            var progressOverlayInfo = chartStore.getProgressOverlayInfo();
            if (progressOverlayInfo !== null) {
                var overlay = progressOverlayInfo.overlay;
                var progressOverlayPaneId = progressOverlayInfo.paneId;
                if (overlay.isDrawing() && progressOverlayPaneId === paneId) {
                    overlay.forceComplete();
                    if (!overlay.isDrawing()) {
                        chartStore.progressOverlayComplete();
                        (_a = overlay.onDrawEnd) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: chart, overlay: overlay }, event));
                    }
                }
                var index = overlay.points.length - 1;
                return _this._figureMouseClickEvent(overlay, 'point', index, {
                    key: "".concat(OVERLAY_FIGURE_KEY_PREFIX, "point_").concat(index),
                    type: 'circle',
                    attrs: {}
                })(event);
            }
            return false;
        })
            .registerEvent('mouseRightClickEvent', function (event) {
            var progressOverlayInfo = chartStore.getProgressOverlayInfo();
            if (progressOverlayInfo !== null) {
                var overlay = progressOverlayInfo.overlay;
                if (overlay.isDrawing()) {
                    var index = overlay.points.length - 1;
                    return _this._figureMouseRightClickEvent(overlay, 'point', index, {
                        key: "".concat(OVERLAY_FIGURE_KEY_PREFIX, "point_").concat(index),
                        type: 'circle',
                        attrs: {}
                    })(event);
                }
            }
            return false;
        })
            .registerEvent('mouseDownEvent', function (event) {
            var _a;
            // Handle continuous drawing mode - start drawing on mouse down
            var progressOverlayInfo = chartStore.getProgressOverlayInfo();
            if (progressOverlayInfo !== null) {
                var overlay = progressOverlayInfo.overlay;
                if (overlay.isContinuousDrawingMode() && overlay.isStart()) {
                    chartStore.updateProgressOverlayInfo(paneId, true);
                    var point = _this._coordinateToPoint(overlay, event);
                    overlay.startContinuousDrawing(point);
                    (_a = overlay.onDrawStart) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: chart, overlay: overlay }, event));
                    return true;
                }
            }
            return false;
        })
            .registerEvent('mouseUpEvent', function (event) {
            var _a, _b;
            // Handle continuous drawing mode - complete on mouse up
            var progressOverlayInfo = chartStore.getProgressOverlayInfo();
            if (progressOverlayInfo !== null) {
                var overlay_1 = progressOverlayInfo.overlay;
                if (overlay_1.isContinuousDrawingMode() && overlay_1.isDrawing() && !overlay_1.isStart()) {
                    overlay_1.forceComplete();
                    chartStore.progressOverlayComplete();
                    (_a = overlay_1.onDrawEnd) === null || _a === void 0 ? void 0 : _a.call(overlay_1, __assign({ chart: chart, overlay: overlay_1 }, event));
                    return true;
                }
            }
            var _c = chartStore.getPressedOverlayInfo(), overlay = _c.overlay, figure = _c.figure;
            if (overlay !== null) {
                if (checkOverlayFigureEvent('onPressedMoveEnd', figure)) {
                    (_b = overlay.onPressedMoveEnd) === null || _b === void 0 ? void 0 : _b.call(overlay, __assign({ chart: chart, overlay: overlay, figure: figure !== null && figure !== void 0 ? figure : undefined }, event));
                }
            }
            chartStore.setPressedOverlayInfo({
                paneId: paneId,
                overlay: null,
                figureType: 'none',
                figureIndex: -1,
                figure: null
            });
            return false;
        })
            .registerEvent('pressedMouseMoveEvent', function (event) {
            var _a, _b;
            // Handle continuous drawing mode - accumulate points while mouse is pressed
            var progressOverlayInfo = chartStore.getProgressOverlayInfo();
            if (progressOverlayInfo !== null) {
                var overlay_2 = progressOverlayInfo.overlay;
                if (overlay_2.isContinuousDrawingMode() && overlay_2.isDrawing() && !overlay_2.isStart()) {
                    var point = _this._coordinateToPoint(overlay_2, event);
                    overlay_2.continuousDrawingModeEventMoveForDrawing(point);
                    (_a = overlay_2.onDrawing) === null || _a === void 0 ? void 0 : _a.call(overlay_2, __assign({ chart: chart, overlay: overlay_2 }, event));
                    _this.getWidget().setForceCursor('pointer');
                    return true;
                }
            }
            var _c = chartStore.getPressedOverlayInfo(), overlay = _c.overlay, figureType = _c.figureType, figureIndex = _c.figureIndex, figure = _c.figure;
            if (overlay !== null) {
                if (checkOverlayFigureEvent('onPressedMoving', figure)) {
                    if (!overlay.lock) {
                        var point = _this._coordinateToPoint(overlay, event);
                        if (figureType === 'point') {
                            overlay.eventPressedPointMove(point, figureIndex);
                        }
                        else {
                            overlay.eventPressedOtherMove(point, _this.getWidget().getPane().getChart().getChartStore());
                        }
                        var prevented_1 = false;
                        (_b = overlay.onPressedMoving) === null || _b === void 0 ? void 0 : _b.call(overlay, __assign(__assign({ chart: chart, overlay: overlay, figure: figure !== null && figure !== void 0 ? figure : undefined }, event), { preventDefault: function () {
                                prevented_1 = true;
                            } }));
                        if (prevented_1) {
                            _this.getWidget().setForceCursor(null);
                        }
                        else {
                            _this.getWidget().setForceCursor('pointer');
                        }
                        return true;
                    }
                }
            }
            _this.getWidget().setForceCursor(null);
            return false;
        });
    };
    OverlayView.prototype._createFigureEvents = function (overlay, figureType, figureIndex, figure) {
        if (overlay.isDrawing()) {
            return null;
        }
        return {
            mouseMoveEvent: this._figureMouseMoveEvent(overlay, figureType, figureIndex, figure),
            mouseDownEvent: this._figureMouseDownEvent(overlay, figureType, figureIndex, figure),
            mouseClickEvent: this._figureMouseClickEvent(overlay, figureType, figureIndex, figure),
            mouseRightClickEvent: this._figureMouseRightClickEvent(overlay, figureType, figureIndex, figure),
            mouseDoubleClickEvent: this._figureMouseDoubleClickEvent(overlay, figureType, figureIndex, figure)
        };
    };
    OverlayView.prototype._processOverlayMouseEnterEvent = function (overlay, figure, event) {
        if (isFunction(overlay.onMouseEnter) && checkOverlayFigureEvent('onMouseEnter', figure)) {
            overlay.onMouseEnter(__assign({ chart: this.getWidget().getPane().getChart(), overlay: overlay, figure: figure !== null && figure !== void 0 ? figure : undefined }, event));
            return true;
        }
        return false;
    };
    OverlayView.prototype._processOverlayMouseLeaveEvent = function (overlay, figure, event) {
        if (isFunction(overlay.onMouseLeave) && checkOverlayFigureEvent('onMouseLeave', figure)) {
            overlay.onMouseLeave(__assign({ chart: this.getWidget().getPane().getChart(), overlay: overlay, figure: figure !== null && figure !== void 0 ? figure : undefined }, event));
            return true;
        }
        return false;
    };
    OverlayView.prototype._processOverlaySelectedEvent = function (overlay, figure, event) {
        var _a;
        if (checkOverlayFigureEvent('onSelected', figure)) {
            (_a = overlay.onSelected) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: this.getWidget().getPane().getChart(), overlay: overlay, figure: figure !== null && figure !== void 0 ? figure : undefined }, event));
            return true;
        }
        return false;
    };
    OverlayView.prototype._processOverlayDeselectedEvent = function (overlay, figure, event) {
        var _a;
        if (checkOverlayFigureEvent('onDeselected', figure)) {
            (_a = overlay.onDeselected) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: this.getWidget().getPane().getChart(), overlay: overlay, figure: figure !== null && figure !== void 0 ? figure : undefined }, event));
            return true;
        }
        return false;
    };
    OverlayView.prototype._figureMouseMoveEvent = function (overlay, figureType, figureIndex, figure) {
        var _this = this;
        return function (event) {
            var _a;
            var pane = _this.getWidget().getPane();
            var check = !overlay.isDrawing() && checkOverlayFigureEvent('onMouseMove', figure);
            if (check) {
                var prevented_2 = false;
                (_a = overlay.onMouseMove) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign(__assign({ chart: pane.getChart(), overlay: overlay, figure: figure }, event), { preventDefault: function () {
                        prevented_2 = true;
                    } }));
                if (prevented_2) {
                    _this.getWidget().setForceCursor(null);
                }
                else {
                    _this.getWidget().setForceCursor('pointer');
                }
            }
            pane
                .getChart()
                .getChartStore()
                .setHoverOverlayInfo({ paneId: pane.getId(), overlay: overlay, figureType: figureType, figure: figure, figureIndex: figureIndex }, function (o, f) { return _this._processOverlayMouseEnterEvent(o, f, event); }, function (o, f) { return _this._processOverlayMouseLeaveEvent(o, f, event); });
            return check;
        };
    };
    OverlayView.prototype._figureMouseDownEvent = function (overlay, figureType, figureIndex, figure) {
        var _this = this;
        return function (event) {
            var _a;
            if (overlay.lock) {
                return false;
            }
            var pane = _this.getWidget().getPane();
            var paneId = pane.getId();
            overlay.startPressedMove(_this._coordinateToPoint(overlay, event));
            if (checkOverlayFigureEvent('onPressedMoveStart', figure)) {
                (_a = overlay.onPressedMoveStart) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: pane.getChart(), overlay: overlay, figure: figure }, event));
                pane.getChart().getChartStore().setPressedOverlayInfo({ paneId: paneId, overlay: overlay, figureType: figureType, figureIndex: figureIndex, figure: figure });
                return !overlay.isDrawing();
            }
            return false;
        };
    };
    OverlayView.prototype._figureMouseClickEvent = function (overlay, figureType, figureIndex, figure) {
        var _this = this;
        return function (event) {
            var _a;
            var pane = _this.getWidget().getPane();
            var paneId = pane.getId();
            var check = !overlay.isDrawing() && checkOverlayFigureEvent('onClick', figure);
            if (check) {
                (_a = overlay.onClick) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign({ chart: _this.getWidget().getPane().getChart(), overlay: overlay, figure: figure }, event));
            }
            pane
                .getChart()
                .getChartStore()
                .setClickOverlayInfo({ paneId: paneId, overlay: overlay, figureType: figureType, figureIndex: figureIndex, figure: figure }, function (o, f) { return _this._processOverlaySelectedEvent(o, f, event); }, function (o, f) { return _this._processOverlayDeselectedEvent(o, f, event); });
            return check;
        };
    };
    OverlayView.prototype._figureMouseDoubleClickEvent = function (overlay, _figureType, _figureIndex, figure) {
        var _this = this;
        return function (event) {
            var _a;
            if (checkOverlayFigureEvent('onDoubleClick', figure)) {
                (_a = overlay.onDoubleClick) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign(__assign({}, event), { chart: _this.getWidget().getPane().getChart(), figure: figure, overlay: overlay }));
                return !overlay.isDrawing();
            }
            return false;
        };
    };
    OverlayView.prototype._figureMouseRightClickEvent = function (overlay, _figureType, _figureIndex, figure) {
        var _this = this;
        return function (event) {
            var _a;
            if (checkOverlayFigureEvent('onRightClick', figure)) {
                var prevented_3 = false;
                (_a = overlay.onRightClick) === null || _a === void 0 ? void 0 : _a.call(overlay, __assign(__assign({ chart: _this.getWidget().getPane().getChart(), overlay: overlay, figure: figure }, event), { preventDefault: function () {
                        prevented_3 = true;
                    } }));
                if (!prevented_3) {
                    _this.getWidget().getPane().getChart().getChartStore().removeOverlay(overlay);
                }
                return !overlay.isDrawing();
            }
            return false;
        };
    };
    OverlayView.prototype._coordinateToPoint = function (o, coordinate) {
        var _a, _b;
        var point = {};
        var pane = this.getWidget().getPane();
        var chart = pane.getChart();
        var paneId = pane.getId();
        var chartStore = chart.getChartStore();
        if (this.coordinateToPointTimestampDataIndexFlag()) {
            var overlayImp = o;
            if (overlayImp.isContinuousDrawingMode()) {
                // For continuous drawing, store precise timestamp for sub-bar positioning
                var floatIndex = chartStore.coordinateToFloatIndex(coordinate.x);
                point.dataIndex = floatIndex;
                point.timestamp = (_a = chartStore.floatIndexToTimestamp(floatIndex)) !== null && _a !== void 0 ? _a : undefined;
            }
            else {
                // For step-based drawing, snap to candle boundaries
                var xAxis = chart.getXAxisPane().getXAxisComponent();
                var dataIndex = xAxis.convertFromPixel(coordinate.x);
                point.dataIndex = dataIndex;
                point.timestamp = (_b = chartStore.dataIndexToTimestamp(dataIndex)) !== null && _b !== void 0 ? _b : undefined;
            }
        }
        if (this.coordinateToPointValueFlag()) {
            var yAxis = pane.getYAxisComponentById();
            var value = yAxis.convertFromPixel(coordinate.y);
            if (o.mode !== 'normal' && paneId === PaneIdConstants.CANDLE && isNumber(point.dataIndex)) {
                var kLineData = chartStore.getDataByDataIndex(point.dataIndex);
                if (kLineData !== null) {
                    var modeSensitivity = o.modeSensitivity;
                    if (value > kLineData.high) {
                        if (o.mode === 'weak_magnet') {
                            var highY = yAxis.convertToPixel(kLineData.high);
                            var buffValueY = yAxis.reverse ? highY + modeSensitivity : highY - modeSensitivity;
                            var buffValue = yAxis.convertFromPixel(buffValueY);
                            if (value < buffValue) {
                                value = kLineData.high;
                            }
                        }
                        else {
                            value = kLineData.high;
                        }
                    }
                    else if (value < kLineData.low) {
                        if (o.mode === 'weak_magnet') {
                            var lowY = yAxis.convertToPixel(kLineData.low);
                            var buffValueY = yAxis.reverse ? lowY - modeSensitivity : lowY + modeSensitivity;
                            var buffValue = yAxis.convertFromPixel(buffValueY);
                            if (value > buffValue) {
                                value = kLineData.low;
                            }
                        }
                        else {
                            value = kLineData.low;
                        }
                    }
                    else {
                        var max = Math.max(kLineData.open, kLineData.close);
                        var min = Math.min(kLineData.open, kLineData.close);
                        if (value > max) {
                            if (value - max < kLineData.high - value) {
                                value = max;
                            }
                            else {
                                value = kLineData.high;
                            }
                        }
                        else if (value < min) {
                            if (value - kLineData.low < min - value) {
                                value = kLineData.low;
                            }
                            else {
                                value = min;
                            }
                        }
                        else if (max - value < value - min) {
                            value = max;
                        }
                        else {
                            value = min;
                        }
                    }
                }
            }
            point.value = value;
        }
        return point;
    };
    OverlayView.prototype.coordinateToPointValueFlag = function () {
        return true;
    };
    OverlayView.prototype.coordinateToPointTimestampDataIndexFlag = function () {
        return true;
    };
    OverlayView.prototype.dispatchEvent = function (name, event) {
        var isDrawing = this.getWidget().getPane().getChart().getChartStore().isOverlayDrawing();
        if (isDrawing) {
            return this.onEvent(name, event);
        }
        return _super.prototype.dispatchEvent.call(this, name, event);
    };
    OverlayView.prototype.drawImp = function (ctx) {
        var _this = this;
        var overlays = this.getCompleteOverlays();
        overlays.forEach(function (overlay) {
            if (overlay.visible) {
                _this._drawOverlay(ctx, overlay);
            }
        });
        var progressOverlay = this.getProgressOverlay();
        if (isValid(progressOverlay) && progressOverlay.visible) {
            this._drawOverlay(ctx, progressOverlay);
        }
    };
    OverlayView.prototype._drawOverlay = function (ctx, overlay) {
        var points = overlay.points;
        var pane = this.getWidget().getPane();
        var chart = pane.getChart();
        var chartStore = chart.getChartStore();
        var yAxis = pane.getYAxisComponentById();
        // For continuous drawing overlays, use float indices for smooth rendering
        var isContinuous = overlay.isContinuousDrawingMode();
        var coordinates = points.map(function (point) {
            var _a;
            var dataIndex = null;
            if (isContinuous && isNumber(point.timestamp)) {
                // Use timestampToFloatIndex for sub-bar precision
                dataIndex = chartStore.timestampToFloatIndex(point.timestamp);
            }
            else if (isNumber(point.timestamp)) {
                // For regular overlays, use integer timestamp lookup
                dataIndex = chartStore.timestampToDataIndex(point.timestamp);
            }
            else if (isNumber(point.dataIndex)) {
                // Fallback to dataIndex if no timestamp
                dataIndex = point.dataIndex;
            }
            var coordinate = { x: 0, y: 0 };
            if (isNumber(dataIndex)) {
                coordinate.x = chartStore.dataIndexToCoordinate(dataIndex);
            }
            if (isNumber(point.value)) {
                coordinate.y = (_a = yAxis === null || yAxis === void 0 ? void 0 : yAxis.convertToPixel(point.value)) !== null && _a !== void 0 ? _a : 0;
            }
            return coordinate;
        });
        if (coordinates.length > 0) {
            var figures = [];
            figures = figures.concat(this.getFigures(overlay, coordinates));
            this.drawFigures(ctx, overlay, figures);
        }
        this.drawDefaultFigures(ctx, overlay, coordinates);
    };
    OverlayView.prototype.drawFigures = function (ctx, overlay, figures) {
        var _this = this;
        var defaultStyles = this.getWidget().getPane().getChart().getStyles().overlay;
        figures.forEach(function (figure, figureIndex) {
            var type = figure.type, styles = figure.styles, attrs = figure.attrs;
            // @ts-ignore
            var attrsArray = [].concat(attrs);
            attrsArray.forEach(function (ats) {
                var _a, _b;
                var events = _this._createFigureEvents(overlay, 'other', figureIndex, figure);
                // @ts-ignore
                var ss = __assign(__assign(__assign({}, defaultStyles[type]), (_a = overlay.styles) === null || _a === void 0 ? void 0 : _a[type]), styles);
                (_b = _this.createFigure({
                    name: type,
                    attrs: ats,
                    styles: ss
                }, events !== null && events !== void 0 ? events : undefined)) === null || _b === void 0 ? void 0 : _b.draw(ctx);
            });
        });
    };
    OverlayView.prototype.getCompleteOverlays = function () {
        var pane = this.getWidget().getPane();
        return pane.getChart().getChartStore().getOverlaysByPaneId(pane.getId());
    };
    OverlayView.prototype.getProgressOverlay = function () {
        var pane = this.getWidget().getPane();
        var info = pane.getChart().getChartStore().getProgressOverlayInfo();
        if (isValid(info) && info.paneId === pane.getId()) {
            return info.overlay;
        }
        return null;
    };
    OverlayView.prototype.getFigures = function (o, coordinates) {
        var _a, _b;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chart = pane.getChart();
        var yAxis = pane.getYAxisComponentById();
        var xAxis = chart.getXAxisPane().getXAxisComponent();
        var bounding = widget.getBounding();
        return (_b = (_a = o.createPointFigures) === null || _a === void 0 ? void 0 : _a.call(o, { chart: chart, overlay: o, coordinates: coordinates, bounding: bounding, xAxis: xAxis, yAxis: yAxis })) !== null && _b !== void 0 ? _b : [];
    };
    OverlayView.prototype.drawDefaultFigures = function (ctx, overlay, coordinates) {
        var _this = this;
        var _a, _b;
        if (overlay.needDefaultPointFigure) {
            var chartStore = this.getWidget().getPane().getChart().getChartStore();
            var hoverOverlayInfo_1 = chartStore.getHoverOverlayInfo();
            var clickOverlayInfo = chartStore.getClickOverlayInfo();
            if ((((_a = hoverOverlayInfo_1.overlay) === null || _a === void 0 ? void 0 : _a.id) === overlay.id && hoverOverlayInfo_1.figureType !== 'none') || (((_b = clickOverlayInfo.overlay) === null || _b === void 0 ? void 0 : _b.id) === overlay.id && clickOverlayInfo.figureType !== 'none')) {
                var defaultStyles = chartStore.getStyles().overlay;
                var styles = overlay.styles;
                var pointStyles_1 = __assign(__assign({}, defaultStyles.point), styles === null || styles === void 0 ? void 0 : styles.point);
                coordinates.forEach(function (_a, index) {
                    var _b, _c, _d, _e, _f;
                    var x = _a.x, y = _a.y;
                    var radius = pointStyles_1.radius;
                    var color = pointStyles_1.color;
                    var borderColor = pointStyles_1.borderColor;
                    var borderSize = pointStyles_1.borderSize;
                    if (((_b = hoverOverlayInfo_1.overlay) === null || _b === void 0 ? void 0 : _b.id) === overlay.id && hoverOverlayInfo_1.figureType === 'point' && ((_c = hoverOverlayInfo_1.figure) === null || _c === void 0 ? void 0 : _c.key) === "".concat(OVERLAY_FIGURE_KEY_PREFIX, "point_").concat(index)) {
                        radius = pointStyles_1.activeRadius;
                        color = pointStyles_1.activeColor;
                        borderColor = pointStyles_1.activeBorderColor;
                        borderSize = pointStyles_1.activeBorderSize;
                    }
                    (_e = _this.createFigure({
                        name: 'circle',
                        attrs: { x: x, y: y, r: radius + borderSize },
                        styles: { color: borderColor }
                    }, (_d = _this._createFigureEvents(overlay, 'point', index, {
                        key: "".concat(OVERLAY_FIGURE_KEY_PREFIX, "point_").concat(index),
                        type: 'circle',
                        attrs: { x: x, y: y, r: radius + borderSize },
                        styles: { color: borderColor }
                    })) !== null && _d !== void 0 ? _d : undefined)) === null || _e === void 0 ? void 0 : _e.draw(ctx);
                    (_f = _this.createFigure({
                        name: 'circle',
                        attrs: { x: x, y: y, r: radius },
                        styles: { color: color }
                    })) === null || _f === void 0 ? void 0 : _f.draw(ctx);
                });
            }
        }
    };
    return OverlayView;
}(View));

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
var supportedDevicePixelContentBox = null;
function isSupportedDevicePixelContentBox() {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            switch (_a.label) {
                case 0:
                    supportedDevicePixelContentBox !== null && supportedDevicePixelContentBox !== void 0 ? supportedDevicePixelContentBox : (supportedDevicePixelContentBox = new Promise(function (resolve) {
                        var ro = new ResizeObserver(function (entries) {
                            resolve(entries.every(function (entry) { var _a; return isValid((_a = entry.devicePixelContentBoxSize) === null || _a === void 0 ? void 0 : _a[0]); }));
                            ro.disconnect();
                        });
                        ro.observe(document.body, { box: 'device-pixel-content-box' });
                    }).catch(function () { return false; }));
                    return [4 /*yield*/, supportedDevicePixelContentBox];
                case 1: return [2 /*return*/, _a.sent()];
            }
        });
    });
}
var Canvas = /** @class */ (function () {
    function Canvas(style, listener) {
        var _this = this;
        this._resizeObserver = null;
        this._mediaQueryList = null;
        this._destroyed = false;
        this._resizePending = false;
        this._sizeInitialized = false;
        this._width = 0;
        this._height = 0;
        this._pixelWidth = 0;
        this._pixelHeight = 0;
        this._nextPixelWidth = 0;
        this._nextPixelHeight = 0;
        this._requestAnimationId = DEFAULT_REQUEST_ID;
        this._mediaQueryListener = function () {
            if (!_this._destroyed) {
                _this._observePixelRatio();
                _this._resize(getPixelRatio(_this._element));
            }
        };
        this._listener = listener;
        this._element = createDom('canvas', style);
        this._ctx = this._element.getContext('2d');
        isSupportedDevicePixelContentBox()
            .then(function (result) {
            if (_this._destroyed) {
                return;
            }
            if (result) {
                _this._resizeObserver = new ResizeObserver(function (entries) {
                    var entry = entries.find(function (entry) { return entry.target === _this._element; });
                    var size = entry === null || entry === void 0 ? void 0 : entry.devicePixelContentBoxSize[0];
                    if (_this._sizeInitialized && isValid(size)) {
                        _this._nextPixelWidth = size.inlineSize;
                        _this._nextPixelHeight = size.blockSize;
                        if (_this._pixelWidth !== _this._nextPixelWidth || _this._pixelHeight !== _this._nextPixelHeight) {
                            _this._scheduleDraw(true);
                        }
                    }
                });
                _this._resizeObserver.observe(_this._element, { box: 'device-pixel-content-box' });
            }
            else {
                _this._observePixelRatio();
            }
        })
            .catch(function (_) { return false; });
    }
    Canvas.prototype._observePixelRatio = function () {
        var _a, _b;
        if (isValid(this._mediaQueryList)) {
            this._mediaQueryList.removeListener(this._mediaQueryListener);
        }
        var view = this._element.ownerDocument.defaultView;
        this._mediaQueryList = (_a = view === null || view === void 0 ? void 0 : view.matchMedia("(resolution: ".concat(getPixelRatio(this._element), "dppx)"))) !== null && _a !== void 0 ? _a : null;
        (_b = this._mediaQueryList) === null || _b === void 0 ? void 0 : _b.addListener(this._mediaQueryListener);
    };
    Canvas.prototype._resize = function (pixelRatio) {
        this._nextPixelWidth = Math.round(this._width * pixelRatio);
        this._nextPixelHeight = Math.round(this._height * pixelRatio);
        this._scheduleDraw(true);
    };
    Canvas.prototype._scheduleDraw = function (resize) {
        var _this = this;
        if (resize === void 0) { resize = false; }
        this._resizePending || (this._resizePending = resize);
        if (this._requestAnimationId === DEFAULT_REQUEST_ID) {
            this._requestAnimationId = requestAnimationFrame(function () {
                _this._requestAnimationId = DEFAULT_REQUEST_ID;
                if (_this._destroyed) {
                    return;
                }
                if (_this._resizePending) {
                    _this._resizePending = false;
                    _this._pixelWidth = _this._nextPixelWidth;
                    _this._pixelHeight = _this._nextPixelHeight;
                    _this._element.width = _this._pixelWidth;
                    _this._element.height = _this._pixelHeight;
                }
                _this._ctx.setTransform(1, 0, 0, 1, 0, 0);
                _this._ctx.clearRect(0, 0, _this._pixelWidth, _this._pixelHeight);
                _this._ctx.save();
                if (_this._width > 0 && _this._height > 0) {
                    _this._ctx.scale(_this._pixelWidth / _this._width, _this._pixelHeight / _this._height);
                }
                try {
                    _this._listener();
                }
                finally {
                    _this._ctx.restore();
                }
            });
        }
    };
    Canvas.prototype.update = function (w, h) {
        if (!this._sizeInitialized || this._width !== w || this._height !== h) {
            this._sizeInitialized = true;
            this._width = w;
            this._height = h;
            this._element.style.width = "".concat(w, "px");
            this._element.style.height = "".concat(h, "px");
            this._resize(getPixelRatio(this._element));
        }
        else {
            this._scheduleDraw();
        }
    };
    Canvas.prototype.getElement = function () {
        return this._element;
    };
    Canvas.prototype.getContext = function () {
        return this._ctx;
    };
    Canvas.prototype.destroy = function () {
        this._destroyed = true;
        if (this._requestAnimationId !== DEFAULT_REQUEST_ID) {
            cancelAnimationFrame(this._requestAnimationId);
            this._requestAnimationId = DEFAULT_REQUEST_ID;
        }
        if (isValid(this._resizeObserver)) {
            this._resizeObserver.disconnect();
            this._resizeObserver = null;
        }
        if (isValid(this._mediaQueryList)) {
            this._mediaQueryList.removeListener(this._mediaQueryListener);
            this._mediaQueryList = null;
        }
    };
    return Canvas;
}());

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
var Widget = /** @class */ (function (_super) {
    __extends(Widget, _super);
    function Widget(rootContainer, pane) {
        var _this = _super.call(this) || this;
        _this._bounding = createDefaultBounding();
        _this._cursor = 'crosshair';
        _this._forceCursor = null;
        _this._pane = pane;
        _this._rootContainer = rootContainer;
        _this._container = _this.createContainer();
        rootContainer.appendChild(_this._container);
        return _this;
    }
    Widget.prototype.setBounding = function (bounding) {
        merge(this._bounding, bounding);
        return this;
    };
    Widget.prototype.getContainer = function () {
        return this._container;
    };
    Widget.prototype.getBounding = function () {
        return this._bounding;
    };
    Widget.prototype.getPane = function () {
        return this._pane;
    };
    Widget.prototype.checkEventOn = function (_) {
        return true;
    };
    Widget.prototype.setCursor = function (cursor) {
        if (!isString(this._forceCursor)) {
            if (cursor !== this._cursor) {
                this._cursor = cursor;
                this._container.style.cursor = this._cursor;
            }
        }
    };
    Widget.prototype.setForceCursor = function (cursor) {
        var _a;
        if (cursor !== this._forceCursor) {
            this._forceCursor = cursor;
            this._container.style.cursor = (_a = this._forceCursor) !== null && _a !== void 0 ? _a : this._cursor;
        }
    };
    Widget.prototype.getForceCursor = function () {
        return this._forceCursor;
    };
    Widget.prototype.update = function (level) {
        this.updateImp(this._container, this._bounding, level !== null && level !== void 0 ? level : 3 /* UpdateLevel.Drawer */);
    };
    Widget.prototype.destroy = function () {
        this._rootContainer.removeChild(this._container);
    };
    return Widget;
}(Eventful));

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
var DrawWidget = /** @class */ (function (_super) {
    __extends(DrawWidget, _super);
    function DrawWidget(rootContainer, pane) {
        var _this = _super.call(this, rootContainer, pane) || this;
        _this._mainCanvas = new Canvas({
            position: 'absolute',
            top: '0',
            left: '0',
            zIndex: '2',
            boxSizing: 'border-box'
        }, function () {
            _this.updateMain(_this._mainCanvas.getContext());
        });
        _this._overlayCanvas = new Canvas({
            position: 'absolute',
            top: '0',
            left: '0',
            zIndex: '2',
            boxSizing: 'border-box'
        }, function () {
            _this.updateOverlay(_this._overlayCanvas.getContext());
        });
        var container = _this.getContainer();
        container.appendChild(_this._mainCanvas.getElement());
        container.appendChild(_this._overlayCanvas.getElement());
        return _this;
    }
    DrawWidget.prototype.createContainer = function () {
        return createDom('div', {
            margin: '0',
            padding: '0',
            position: 'absolute',
            top: '0',
            overflow: 'hidden',
            boxSizing: 'border-box',
            zIndex: '1'
        });
    };
    DrawWidget.prototype.updateImp = function (container, bounding, level) {
        var width = bounding.width, height = bounding.height, left = bounding.left;
        container.style.left = "".concat(left, "px");
        var l = level;
        var w = container.clientWidth;
        var h = container.clientHeight;
        if (width !== w || height !== h) {
            container.style.width = "".concat(width, "px");
            container.style.height = "".concat(height, "px");
            l = 3 /* UpdateLevel.Drawer */;
        }
        switch (l) {
            case 0 /* UpdateLevel.Main */: {
                this._mainCanvas.update(width, height);
                break;
            }
            case 1 /* UpdateLevel.Overlay */: {
                this._overlayCanvas.update(width, height);
                break;
            }
            case 3 /* UpdateLevel.Drawer */:
            case 4 /* UpdateLevel.All */: {
                this._mainCanvas.update(width, height);
                this._overlayCanvas.update(width, height);
                break;
            }
        }
    };
    DrawWidget.prototype.destroy = function () {
        this._mainCanvas.destroy();
        this._overlayCanvas.destroy();
        _super.prototype.destroy.call(this);
    };
    DrawWidget.prototype.getImage = function (includeOverlay) {
        var _a = this.getBounding(), width = _a.width, height = _a.height;
        var canvas = createDom('canvas', {
            width: "".concat(width, "px"),
            height: "".concat(height, "px"),
            boxSizing: 'border-box'
        });
        var ctx = canvas.getContext('2d');
        var pixelRatio = getPixelRatio(canvas);
        canvas.width = width * pixelRatio;
        canvas.height = height * pixelRatio;
        ctx.scale(pixelRatio, pixelRatio);
        ctx.drawImage(this._mainCanvas.getElement(), 0, 0, width, height);
        if (includeOverlay) {
            ctx.drawImage(this._overlayCanvas.getElement(), 0, 0, width, height);
        }
        return canvas;
    };
    return DrawWidget;
}(Widget));

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
var IndicatorWidget = /** @class */ (function (_super) {
    __extends(IndicatorWidget, _super);
    function IndicatorWidget(rootContainer, pane) {
        var _this = _super.call(this, rootContainer, pane) || this;
        _this._gridView = new GridView(_this);
        _this._indicatorView = new IndicatorView(_this);
        _this._crosshairLineView = new CrosshairLineView(_this);
        _this._tooltipView = _this.createTooltipView();
        _this._overlayView = new OverlayView(_this);
        _this.addChild(_this._tooltipView);
        _this.addChild(_this._overlayView);
        return _this;
    }
    IndicatorWidget.prototype.getName = function () {
        return WidgetNameConstants.MAIN;
    };
    IndicatorWidget.prototype.updateMain = function (ctx) {
        this.updateMainContent(ctx);
        this._indicatorView.draw(ctx);
        this._gridView.draw(ctx);
    };
    IndicatorWidget.prototype.createTooltipView = function () {
        return new IndicatorTooltipView(this);
    };
    IndicatorWidget.prototype.updateMainContent = function (_ctx) {
        // to do it
    };
    IndicatorWidget.prototype.updateOverlayContent = function (_ctx) {
        // to do it
    };
    IndicatorWidget.prototype.updateOverlay = function (ctx) {
        this._overlayView.draw(ctx);
        this._crosshairLineView.draw(ctx);
        this.updateOverlayContent(ctx);
        this._tooltipView.draw(ctx);
    };
    return IndicatorWidget;
}(DrawWidget));

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
var CandleWidget = /** @class */ (function (_super) {
    __extends(CandleWidget, _super);
    function CandleWidget(rootContainer, pane) {
        var _this = _super.call(this, rootContainer, pane) || this;
        _this._candleBarView = new CandleBarView(_this);
        _this._candleAreaView = new CandleAreaView(_this);
        _this._candleHighLowPriceView = new CandleHighLowPriceView(_this);
        _this._candleLastPriceLineView = new CandleLastPriceView(_this);
        _this._crosshairFeatureView = new CrosshairFeatureView(_this);
        _this.addChild(_this._candleBarView);
        _this.addChild(_this._crosshairFeatureView);
        return _this;
    }
    CandleWidget.prototype.updateMainContent = function (ctx) {
        var candleStyles = this.getPane().getChart().getStyles().candle;
        if (candleStyles.type !== 'area') {
            this._candleBarView.draw(ctx);
            this._candleHighLowPriceView.draw(ctx);
            this._candleAreaView.stopAnimation();
        }
        else {
            this._candleAreaView.draw(ctx);
        }
        this._candleLastPriceLineView.draw(ctx);
    };
    CandleWidget.prototype.updateOverlayContent = function (ctx) {
        this._crosshairFeatureView.draw(ctx);
    };
    CandleWidget.prototype.createTooltipView = function () {
        return new CandleTooltipView(this);
    };
    CandleWidget.prototype.destroy = function () {
        this._candleAreaView.stopAnimation();
        _super.prototype.destroy.call(this);
    };
    return CandleWidget;
}(IndicatorWidget));

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
var logarithm = {
    name: 'logarithm',
    minSpan: function (precision) { return 0.05 * index10(-precision); },
    valueToRealValue: function (value) { return (value < 0 ? -log10(Math.abs(value)) : log10(value)); },
    realValueToDisplayValue: function (value) { return (value < 0 ? -index10(Math.abs(value)) : index10(value)); },
    displayValueToRealValue: function (value) { return (value < 0 ? -log10(Math.abs(value)) : log10(value)); },
    realValueToValue: function (value) { return (value < 0 ? -index10(Math.abs(value)) : index10(value)); },
    createRange: function (_a) {
        var defaultRange = _a.defaultRange;
        var from = defaultRange.from, to = defaultRange.to, range = defaultRange.range;
        var realFrom = from < 0 ? -log10(Math.abs(from)) : log10(from);
        var realTo = to < 0 ? -log10(Math.abs(to)) : log10(to);
        return {
            from: from,
            to: to,
            range: range,
            realFrom: realFrom,
            realTo: realTo,
            realRange: realTo - realFrom,
            displayFrom: from,
            displayTo: to,
            displayRange: range
        };
    }
};

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
var normal$1 = {
    name: 'normal'
};

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
var percentage = {
    name: 'percentage',
    minSpan: function () { return Math.pow(10, -2); },
    displayValueToText: function (value) { return "".concat(formatPrecision(value, 2), "%"); },
    valueToRealValue: function (value, _a) {
        var range = _a.range;
        return (range.range === 0 ? range.realFrom : ((value - range.from) / range.range) * range.realRange + range.realFrom);
    },
    realValueToValue: function (value, _a) {
        var range = _a.range;
        return (range.realRange === 0 ? range.from : ((value - range.realFrom) / range.realRange) * range.range + range.from);
    },
    createRange: function (_a) {
        var chart = _a.chart, defaultRange = _a.defaultRange;
        var kLineDataList = chart.getDataList();
        var visibleRange = chart.getVisibleRange();
        var kLineData = kLineDataList[visibleRange.from];
        if (isValid(kLineData)) {
            var from = defaultRange.from, to = defaultRange.to, range = defaultRange.range;
            var base = kLineData.close;
            var realFrom = base === 0 ? 0 : ((defaultRange.from - base) / base) * 100;
            var realTo = base === 0 ? 0 : ((defaultRange.to - base) / base) * 100;
            var realRange = realTo - realFrom;
            return {
                from: from,
                to: to,
                range: range,
                realFrom: realFrom,
                realTo: realTo,
                realRange: realRange,
                displayFrom: realFrom,
                displayTo: realTo,
                displayRange: realRange
            };
        }
        return defaultRange;
    }
};

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
var yAxises = {
    normal: YAxisImp.extend(normal$1),
    percentage: YAxisImp.extend(percentage),
    logarithm: YAxisImp.extend(logarithm)
};
function registerYAxis(axis) {
    yAxises[axis.name] = YAxisImp.extend(axis);
}
function getYAxisClass(name) {
    var _a;
    return (_a = yAxises[name]) !== null && _a !== void 0 ? _a : yAxises.normal;
}

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
var CandleLastPriceLabelView = /** @class */ (function (_super) {
    __extends(CandleLastPriceLabelView, _super);
    function CandleLastPriceLabelView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CandleLastPriceLabelView.prototype.drawImp = function (ctx) {
        var _this = this;
        var _a, _b, _c, _d;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var bounding = widget.getBounding();
        var chartStore = pane.getChart().getChartStore();
        var priceMarkStyles = chartStore.getStyles().candle.priceMark;
        var lastPriceMarkStyles = priceMarkStyles.last;
        var lastPriceMarkTextStyles = lastPriceMarkStyles.text;
        if (priceMarkStyles.show && lastPriceMarkStyles.show && lastPriceMarkTextStyles.show) {
            var precision = (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE;
            var yAxis = widget.getAxisComponent();
            var dataList = chartStore.getDataList();
            var data_1 = dataList[dataList.length - 1];
            if (isValid(data_1)) {
                var close_1 = data_1.close, open_1 = data_1.open;
                var comparePrice = lastPriceMarkStyles.compareRule === 'current_open' ? open_1 : ((_d = (_c = dataList[dataList.length - 2]) === null || _c === void 0 ? void 0 : _c.close) !== null && _d !== void 0 ? _d : close_1);
                var priceY = yAxis.convertToNicePixel(close_1);
                var backgroundColor_1 = '';
                if (close_1 > comparePrice) {
                    backgroundColor_1 = lastPriceMarkStyles.upColor;
                }
                else if (close_1 < comparePrice) {
                    backgroundColor_1 = lastPriceMarkStyles.downColor;
                }
                else {
                    backgroundColor_1 = lastPriceMarkStyles.noChangeColor;
                }
                var x_1 = 0;
                var textAlgin_1 = 'left';
                if (yAxis.isFromZero()) {
                    x_1 = 0;
                    textAlgin_1 = 'left';
                }
                else {
                    x_1 = bounding.width;
                    textAlgin_1 = 'right';
                }
                var textFigures_1 = [];
                var yAxisRange = yAxis.getRange();
                var priceText = yAxis.displayValueToText(yAxis.realValueToDisplayValue(yAxis.valueToRealValue(close_1, { range: yAxisRange }), { range: yAxisRange }), precision);
                priceText = chartStore.getDecimalFold().format(chartStore.getThousandsSeparator().format(priceText));
                var paddingLeft = lastPriceMarkTextStyles.paddingLeft, paddingRight = lastPriceMarkTextStyles.paddingRight, paddingTop = lastPriceMarkTextStyles.paddingTop, paddingBottom = lastPriceMarkTextStyles.paddingBottom, size = lastPriceMarkTextStyles.size, family = lastPriceMarkTextStyles.family, weight = lastPriceMarkTextStyles.weight;
                var textWidth_1 = paddingLeft + calcTextWidth(priceText, size, weight, family) + paddingRight;
                var priceTextHeight = paddingTop + size + paddingBottom;
                textFigures_1.push({
                    name: 'text',
                    attrs: {
                        x: x_1,
                        y: priceY,
                        width: textWidth_1,
                        height: priceTextHeight,
                        text: priceText,
                        align: textAlgin_1,
                        baseline: 'middle'
                    },
                    styles: __assign(__assign({}, lastPriceMarkTextStyles), { backgroundColor: backgroundColor_1 })
                });
                var formatExtendText_1 = chartStore.getInnerFormatter().formatExtendText;
                var priceTextHalfHeight = size / 2;
                var aboveY_1 = priceY - priceTextHalfHeight - paddingTop;
                var belowY_1 = priceY + priceTextHalfHeight + paddingBottom;
                lastPriceMarkStyles.extendTexts.forEach(function (item, index) {
                    var text = formatExtendText_1({ type: 'last_price', data: data_1, index: index });
                    if (text.length > 0 && item.show) {
                        var textHalfHeight = item.size / 2;
                        var textY = 0;
                        if (item.position === 'above_price') {
                            aboveY_1 -= item.paddingBottom + textHalfHeight;
                            textY = aboveY_1;
                            aboveY_1 -= textHalfHeight + item.paddingTop;
                        }
                        else {
                            belowY_1 += item.paddingTop + textHalfHeight;
                            textY = belowY_1;
                            belowY_1 += textHalfHeight + item.paddingBottom;
                        }
                        textWidth_1 = Math.max(textWidth_1, item.paddingLeft + calcTextWidth(text, item.size, item.weight, item.family) + item.paddingRight);
                        textFigures_1.push({
                            name: 'text',
                            attrs: {
                                x: x_1,
                                y: textY,
                                width: textWidth_1,
                                height: item.paddingTop + item.size + item.paddingBottom,
                                text: text,
                                align: textAlgin_1,
                                baseline: 'middle'
                            },
                            styles: __assign(__assign({}, item), { backgroundColor: backgroundColor_1 })
                        });
                    }
                });
                textFigures_1.forEach(function (figure) {
                    var _a;
                    figure.attrs.width = textWidth_1;
                    (_a = _this.createFigure(figure)) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                });
            }
        }
    };
    return CandleLastPriceLabelView;
}(View));

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
var CrosshairHorizontalLabelView = /** @class */ (function (_super) {
    __extends(CrosshairHorizontalLabelView, _super);
    function CrosshairHorizontalLabelView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CrosshairHorizontalLabelView.prototype.drawImp = function (ctx) {
        var _a;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chartStore = widget.getPane().getChart().getChartStore();
        var crosshair = chartStore.getCrosshair();
        if (isString(crosshair.paneId) && this.compare(crosshair, pane.getId())) {
            var styles = chartStore.getStyles().crosshair;
            if (styles.show) {
                var directionStyles = this.getDirectionStyles(styles);
                var textStyles = directionStyles.text;
                if (directionStyles.show && textStyles.show) {
                    var bounding = widget.getBounding();
                    var axis = 'getAxisComponent' in widget ? widget.getAxisComponent() : pane.getYAxisComponentById();
                    var text = this.getText(crosshair, chartStore, axis);
                    ctx.font = createFont(textStyles.size, textStyles.weight, textStyles.family);
                    (_a = this.createFigure({
                        name: 'text',
                        attrs: this.getTextAttrs(text, ctx.measureText(text).width, crosshair, bounding, axis, textStyles),
                        styles: textStyles
                    })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                }
            }
        }
    };
    CrosshairHorizontalLabelView.prototype.compare = function (crosshair, paneId) {
        return crosshair.paneId === paneId;
    };
    CrosshairHorizontalLabelView.prototype.getDirectionStyles = function (styles) {
        return styles.horizontal;
    };
    CrosshairHorizontalLabelView.prototype.getText = function (crosshair, chartStore, axis) {
        var _a, _b, _c;
        var yAxis = axis;
        var value = axis.convertFromPixel(crosshair.y);
        var precision = 0;
        var shouldFormatBigNumber = false;
        if (yAxis.isInCandle()) {
            precision = (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE;
        }
        else {
            var yAxisId_1 = yAxis.id;
            var pane = this.getWidget().getPane();
            if (pane.isManualYAxis(yAxisId_1)) {
                yAxisId_1 = (_c = pane.getDefaultYAxisId()) !== null && _c !== void 0 ? _c : yAxisId_1;
            }
            var indicators = chartStore.getIndicatorsByPaneId(crosshair.paneId).filter(function (indicator) { return indicator.yAxisId === yAxisId_1; });
            indicators.forEach(function (indicator) {
                precision = Math.max(indicator.precision, precision);
                shouldFormatBigNumber || (shouldFormatBigNumber = indicator.shouldFormatBigNumber);
            });
        }
        var yAxisRange = yAxis.getRange();
        var text = yAxis.displayValueToText(yAxis.realValueToDisplayValue(yAxis.valueToRealValue(value, { range: yAxisRange }), { range: yAxisRange }), precision);
        if (shouldFormatBigNumber) {
            text = chartStore.getInnerFormatter().formatBigNumber(text);
        }
        return chartStore.getDecimalFold().format(chartStore.getThousandsSeparator().format(text));
    };
    CrosshairHorizontalLabelView.prototype.getTextAttrs = function (text, _textWidth, crosshair, bounding, axis, _styles) {
        var yAxis = axis;
        var x = 0;
        var textAlign = 'left';
        if (yAxis.isFromZero()) {
            x = 0;
            textAlign = 'left';
        }
        else {
            x = bounding.width;
            textAlign = 'right';
        }
        return { x: x, y: crosshair.y, text: text, align: textAlign, baseline: 'middle' };
    };
    return CrosshairHorizontalLabelView;
}(View));

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
var IndicatorLastValueView = /** @class */ (function (_super) {
    __extends(IndicatorLastValueView, _super);
    function IndicatorLastValueView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    IndicatorLastValueView.prototype.drawImp = function (ctx) {
        var _this = this;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var bounding = widget.getBounding();
        var chartStore = pane.getChart().getChartStore();
        var defaultStyles = chartStore.getStyles().indicator;
        var lastValueMarkStyles = defaultStyles.lastValueMark;
        var lastValueMarkTextStyles = lastValueMarkStyles.text;
        if (lastValueMarkStyles.show) {
            var yAxisWidget = widget;
            var yAxis_1 = yAxisWidget.getAxisComponent();
            var yAxisRange_1 = yAxis_1.getRange();
            var dataList = chartStore.getDataList();
            var barSpace_1 = chartStore.getBarSpace();
            var dataIndex_1 = dataList.length - 1;
            var yAxisIds_1 = new Set([yAxis_1.id]);
            var defaultYAxisId = pane.getDefaultYAxisId();
            if (pane.isManualYAxis(yAxis_1.id) && isValid(defaultYAxisId)) {
                yAxisIds_1.add(defaultYAxisId);
            }
            var indicators = chartStore.getIndicatorsByPaneId(pane.getId()).filter(function (indicator) { return yAxisIds_1.has(indicator.yAxisId); });
            var formatter_1 = chartStore.getInnerFormatter();
            var decimalFold_1 = chartStore.getDecimalFold();
            var thousandsSeparator_1 = chartStore.getThousandsSeparator();
            indicators.forEach(function (indicator) {
                var _a;
                var result = indicator.result;
                var data = (_a = result[dataIndex_1]) !== null && _a !== void 0 ? _a : {};
                if (isValid(data) && indicator.visible) {
                    var precision_1 = indicator.precision;
                    // @ts-ignore
                    eachFigures(indicator, dataIndex_1, barSpace_1, defaultStyles, function (figure, figureStyles) {
                        var _a;
                        var value = data[figure.key];
                        if (isNumber(value)) {
                            var y = yAxis_1.convertToNicePixel(value);
                            var text = yAxis_1.displayValueToText(yAxis_1.realValueToDisplayValue(yAxis_1.valueToRealValue(value, { range: yAxisRange_1 }), { range: yAxisRange_1 }), precision_1);
                            if (indicator.shouldFormatBigNumber) {
                                text = formatter_1.formatBigNumber(text);
                            }
                            text = decimalFold_1.format(thousandsSeparator_1.format(text));
                            var x = 0;
                            var textAlign = 'left';
                            if (yAxis_1.isFromZero()) {
                                x = 0;
                                textAlign = 'left';
                            }
                            else {
                                x = bounding.width;
                                textAlign = 'right';
                            }
                            (_a = _this.createFigure({
                                name: 'text',
                                attrs: {
                                    x: x,
                                    y: y,
                                    text: text,
                                    align: textAlign,
                                    baseline: 'middle'
                                },
                                styles: __assign(__assign({}, lastValueMarkTextStyles), { backgroundColor: figureStyles.color })
                            })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                        }
                    });
                }
            });
        }
    };
    return IndicatorLastValueView;
}(View));

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
var OverlayYAxisView = /** @class */ (function (_super) {
    __extends(OverlayYAxisView, _super);
    function OverlayYAxisView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    OverlayYAxisView.prototype.coordinateToPointTimestampDataIndexFlag = function () {
        return false;
    };
    OverlayYAxisView.prototype.drawDefaultFigures = function (ctx, overlay, coordinates) {
        this.drawFigures(ctx, overlay, this.getDefaultFigures(overlay, coordinates));
    };
    OverlayYAxisView.prototype.getDefaultFigures = function (overlay, coordinates) {
        var _a;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chartStore = pane.getChart().getChartStore();
        var clickOverlayInfo = chartStore.getClickOverlayInfo();
        var figures = [];
        if (overlay.needDefaultYAxisFigure && overlay.id === ((_a = clickOverlayInfo.overlay) === null || _a === void 0 ? void 0 : _a.id) && clickOverlayInfo.paneId === pane.getId()) {
            var yAxis = pane.getYAxisComponentById();
            var bounding = widget.getBounding();
            var topY_1 = Number.MAX_SAFE_INTEGER;
            var bottomY_1 = Number.MIN_SAFE_INTEGER;
            var isFromZero = yAxis.isFromZero();
            var textAlign_1 = 'left';
            var x_1 = 0;
            if (isFromZero) {
                textAlign_1 = 'left';
                x_1 = 0;
            }
            else {
                textAlign_1 = 'right';
                x_1 = bounding.width;
            }
            var decimalFold_1 = chartStore.getDecimalFold();
            var thousandsSeparator_1 = chartStore.getThousandsSeparator();
            coordinates.forEach(function (coordinate, index) {
                var _a, _b;
                var point = overlay.points[index];
                if (isNumber(point.value)) {
                    topY_1 = Math.min(topY_1, coordinate.y);
                    bottomY_1 = Math.max(bottomY_1, coordinate.y);
                    var text = decimalFold_1.format(thousandsSeparator_1.format(formatPrecision(point.value, (_b = (_a = chartStore.getSymbol()) === null || _a === void 0 ? void 0 : _a.pricePrecision) !== null && _b !== void 0 ? _b : SymbolDefaultPrecisionConstants.PRICE)));
                    figures.push({ type: 'text', attrs: { x: x_1, y: coordinate.y, text: text, align: textAlign_1, baseline: 'middle' }, ignoreEvent: true });
                }
            });
            if (coordinates.length > 1) {
                figures.unshift({ type: 'rect', attrs: { x: 0, y: topY_1, width: bounding.width, height: bottomY_1 - topY_1 }, ignoreEvent: true });
            }
        }
        return figures;
    };
    OverlayYAxisView.prototype.getFigures = function (overlay, coordinates) {
        var _a, _b;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chart = pane.getChart();
        var yAxis = pane.getYAxisComponentById();
        var xAxis = chart.getXAxisPane().getXAxisComponent();
        var bounding = widget.getBounding();
        return (_b = (_a = overlay.createYAxisFigures) === null || _a === void 0 ? void 0 : _a.call(overlay, { chart: chart, overlay: overlay, coordinates: coordinates, bounding: bounding, xAxis: xAxis, yAxis: yAxis })) !== null && _b !== void 0 ? _b : [];
    };
    return OverlayYAxisView;
}(OverlayView));

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
var AxisView = /** @class */ (function (_super) {
    __extends(AxisView, _super);
    function AxisView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    AxisView.prototype.drawImp = function (ctx) {
        var _this = this;
        var _a, _b;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var bounding = widget.getBounding();
        var axis = this.getAxis();
        var styles = this.getAxisStyles(pane.getChart().getStyles());
        if (styles.show) {
            if (styles.axisLine.show) {
                (_a = this.createFigure({
                    name: 'line',
                    attrs: this.createAxisLine(bounding, styles),
                    styles: styles.axisLine
                })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
            }
            var ticks = axis.getTicks();
            if (styles.tickLine.show) {
                var lines = this.createTickLines(ticks, bounding, styles);
                lines.forEach(function (line) {
                    var _a;
                    (_a = _this.createFigure({
                        name: 'line',
                        attrs: line,
                        styles: styles.tickLine
                    })) === null || _a === void 0 ? void 0 : _a.draw(ctx);
                });
            }
            if (styles.tickText.show) {
                var texts = this.createTickTexts(ticks, bounding, styles);
                (_b = this.createFigure({
                    name: 'text',
                    attrs: texts,
                    styles: styles.tickText
                })) === null || _b === void 0 ? void 0 : _b.draw(ctx);
            }
        }
    };
    return AxisView;
}(View));

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
var YAxisView = /** @class */ (function (_super) {
    __extends(YAxisView, _super);
    function YAxisView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    YAxisView.prototype.getAxis = function () {
        return this.getWidget().getAxisComponent();
    };
    YAxisView.prototype.getAxisStyles = function (styles) {
        return styles.yAxis;
    };
    YAxisView.prototype.createAxisLine = function (bounding, styles) {
        var yAxis = this.getAxis();
        var size = styles.axisLine.size;
        var x = 0;
        if (yAxis.isFromZero()) {
            x = 0;
        }
        else {
            x = bounding.width - size;
        }
        return {
            coordinates: [
                { x: x, y: 0 },
                { x: x, y: bounding.height }
            ]
        };
    };
    YAxisView.prototype.createTickLines = function (ticks, bounding, styles) {
        var yAxis = this.getAxis();
        var axisLineStyles = styles.axisLine;
        var tickLineStyles = styles.tickLine;
        var startX = 0;
        var endX = 0;
        if (yAxis.isFromZero()) {
            startX = 0;
            if (axisLineStyles.show) {
                startX += axisLineStyles.size;
            }
            endX = startX + tickLineStyles.length;
        }
        else {
            startX = bounding.width;
            if (axisLineStyles.show) {
                startX -= axisLineStyles.size;
            }
            endX = startX - tickLineStyles.length;
        }
        return ticks.map(function (tick) { return ({
            coordinates: [
                { x: startX, y: tick.coord },
                { x: endX, y: tick.coord }
            ]
        }); });
    };
    YAxisView.prototype.createTickTexts = function (ticks, bounding, styles) {
        var yAxis = this.getAxis();
        var axisLineStyles = styles.axisLine;
        var tickLineStyles = styles.tickLine;
        var tickTextStyles = styles.tickText;
        var x = 0;
        if (yAxis.isFromZero()) {
            x = tickTextStyles.marginStart;
            if (axisLineStyles.show) {
                x += axisLineStyles.size;
            }
            if (tickLineStyles.show) {
                x += tickLineStyles.length;
            }
        }
        else {
            x = bounding.width - tickTextStyles.marginEnd;
            if (axisLineStyles.show) {
                x -= axisLineStyles.size;
            }
            if (tickLineStyles.show) {
                x -= tickLineStyles.length;
            }
        }
        var textAlign = this.getAxis().isFromZero() ? 'left' : 'right';
        return ticks.map(function (tick) { return ({
            x: x,
            y: tick.coord,
            text: tick.text,
            align: textAlign,
            baseline: 'middle'
        }); });
    };
    return YAxisView;
}(AxisView));

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
var YAxisWidget = /** @class */ (function (_super) {
    __extends(YAxisWidget, _super);
    function YAxisWidget(rootContainer, pane, yAxis) {
        var _this = _super.call(this, rootContainer, pane) || this;
        _this._yAxisView = new YAxisView(_this);
        _this._candleLastPriceLabelView = new CandleLastPriceLabelView(_this);
        _this._indicatorLastValueView = new IndicatorLastValueView(_this);
        _this._overlayYAxisView = new OverlayYAxisView(_this);
        _this._crosshairHorizontalLabelView = new CrosshairHorizontalLabelView(_this);
        _this._yAxis = yAxis;
        _this.setCursor('ns-resize');
        _this.addChild(_this._overlayYAxisView);
        return _this;
    }
    YAxisWidget.prototype.getAxisComponent = function () {
        return this._yAxis;
    };
    YAxisWidget.prototype.getName = function () {
        return WidgetNameConstants.Y_AXIS;
    };
    YAxisWidget.prototype.updateMain = function (ctx) {
        this._yAxisView.draw(ctx);
        var pane = this.getPane();
        var isCandleLastPriceLabelVisibleYAxis = pane.isDefaultYAxis(this._yAxis.id) || pane.isManualYAxis(this._yAxis.id);
        if (isCandleLastPriceLabelVisibleYAxis && this.getAxisComponent().isInCandle()) {
            this._candleLastPriceLabelView.draw(ctx);
        }
        this._indicatorLastValueView.draw(ctx);
    };
    YAxisWidget.prototype.updateOverlay = function (ctx) {
        this._overlayYAxisView.draw(ctx);
        this._crosshairHorizontalLabelView.draw(ctx);
    };
    return YAxisWidget;
}(DrawWidget));

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
var Pane = /** @class */ (function () {
    function Pane(chart, id) {
        this._bounding = createDefaultBounding();
        this._chart = chart;
        this._id = id;
        this._container = createDom('div', {
            width: '100%',
            margin: '0',
            padding: '0',
            position: 'relative',
            overflow: 'hidden',
            boxSizing: 'border-box'
        });
    }
    Pane.prototype.getContainer = function () {
        return this._container;
    };
    Pane.prototype.getId = function () {
        return this._id;
    };
    Pane.prototype.getChart = function () {
        return this._chart;
    };
    Pane.prototype.getBounding = function () {
        return this._bounding;
    };
    Pane.prototype.update = function (level) {
        if (this._bounding.height !== this._container.clientHeight) {
            this._container.style.height = "".concat(this._bounding.height, "px");
        }
        this.updateImp(level !== null && level !== void 0 ? level : 3 /* UpdateLevel.Drawer */, this._container, this._bounding);
    };
    return Pane;
}());

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
var DrawPane = /** @class */ (function (_super) {
    __extends(DrawPane, _super);
    function DrawPane(chart, options) {
        var _this = _super.call(this, chart, options.id) || this;
        _this._yAxisWidgets = new Map();
        _this._yAxisComponents = new Map();
        _this._manualYAxisIds = new Set();
        _this._defaultYAxisId = null;
        _this._yAxesBounding = {};
        var container = _this.getContainer();
        _this._mainWidget = _this.createMainWidget(container);
        _this._options = options;
        return _this;
    }
    DrawPane.prototype.setOptions = function (options) {
        merge(this._options, options);
        if (isNumber(options.height) && options.height > 0) {
            this.setBounding({ height: this._options.height });
        }
        return this;
    };
    DrawPane.prototype.setAxisCursor = function (scrollZoomEnabled, yAxisId) {
        var _a, _b;
        var container = null;
        var cursor = 'default';
        if (this.getId() === PaneIdConstants.X_AXIS) {
            container = this.getMainWidget().getContainer();
            cursor = 'ew-resize';
        }
        else {
            container = (_b = (_a = this.getYAxisWidgetById(yAxisId)) === null || _a === void 0 ? void 0 : _a.getContainer()) !== null && _b !== void 0 ? _b : null;
            cursor = 'ns-resize';
        }
        if (!isValid(container) || !isBoolean(scrollZoomEnabled)) {
            return;
        }
        if (scrollZoomEnabled) {
            container.style.cursor = cursor;
        }
        else {
            container.style.cursor = 'default';
        }
    };
    DrawPane.prototype.createOrOverrideYAxis = function (override) {
        var _a, _b, _c, _d, _e;
        var axis = __assign(__assign({}, override), { paneId: this.getId() });
        var yAxisId = axis.id;
        var yAxisName = (_a = axis.name) !== null && _a !== void 0 ? _a : 'normal';
        var needWidget = (_b = axis.needWidget) !== null && _b !== void 0 ? _b : true;
        var yAxis = this._yAxisComponents.get(yAxisId);
        var shouldCreateYAxis = !isValid(yAxis) || (isValid(axis.name) && yAxis.name !== axis.name);
        if (shouldCreateYAxis) {
            (_c = this._yAxisWidgets.get(yAxisId)) === null || _c === void 0 ? void 0 : _c.destroy();
            this._yAxisWidgets.delete(yAxisId);
            yAxis = this.createYAxisComponent(yAxisName);
            yAxis.id = yAxisId;
            yAxis.paneId = this.getId();
            this._yAxisComponents.set(yAxisId, yAxis);
            (_d = this._defaultYAxisId) !== null && _d !== void 0 ? _d : (this._defaultYAxisId = yAxisId);
            if (needWidget) {
                var yAxisWidget = this.createYAxisWidget(this.getContainer(), yAxis);
                if (isValid(yAxisWidget)) {
                    this._yAxisWidgets.set(yAxisId, yAxisWidget);
                }
            }
        }
        else if (isBoolean(axis.needWidget) && isValid(yAxis)) {
            var yAxisWidget = this._yAxisWidgets.get(yAxisId);
            if (axis.needWidget && !isValid(yAxisWidget)) {
                var newYAxisWidget = this.createYAxisWidget(this.getContainer(), yAxis);
                if (isValid(newYAxisWidget)) {
                    this._yAxisWidgets.set(yAxisId, newYAxisWidget);
                }
            }
            else if (!axis.needWidget && isValid(yAxisWidget)) {
                yAxisWidget.destroy();
                this._yAxisWidgets.delete(yAxisId);
            }
        }
        if (!isValid(yAxis)) {
            throw new Error('create yAxis failed.');
        }
        yAxis.setAutoCalcTickFlag(true);
        yAxis.override(__assign(__assign({}, axis), { name: yAxisName }));
        this.setAxisCursor(yAxis.scrollZoomEnabled, yAxisId);
        // Inherit the pane's current geometry. Recreating the axis (e.g. via
        // `overrideYAxis({ name })`) destroys the old widget, and follow-up
        // layouts only measure width, so without this the new widget keeps
        // height 0 and the axis collapses (squashed bars, no ticks).
        var bounding = this.getBounding();
        (_e = this._yAxisWidgets.get(yAxisId)) === null || _e === void 0 ? void 0 : _e.setBounding({ height: bounding.height, top: bounding.top });
        return yAxis;
    };
    DrawPane.prototype.getOptions = function () {
        return this._options;
    };
    DrawPane.prototype.getYAxisComponents = function () {
        return Array.from(this._yAxisComponents.values());
    };
    DrawPane.prototype.getWidgetYAxisComponents = function () {
        var _this = this;
        return Array.from(this._yAxisWidgets.keys()).map(function (id) { return _this._yAxisComponents.get(id); });
    };
    DrawPane.prototype.hasYAxisComponent = function (yAxisId) {
        return this._yAxisComponents.has(yAxisId);
    };
    DrawPane.prototype.setManualYAxis = function (yAxisId, manual) {
        if (manual) {
            this._manualYAxisIds.add(yAxisId);
        }
        else {
            this._manualYAxisIds.delete(yAxisId);
        }
    };
    DrawPane.prototype.isManualYAxis = function (yAxisId) {
        return this._manualYAxisIds.has(yAxisId);
    };
    DrawPane.prototype.removeYAxis = function (yAxisId) {
        var _this = this;
        var _a;
        var yAxis = this._yAxisComponents.get(yAxisId);
        if (!isValid(yAxis)) {
            return false;
        }
        this._yAxisComponents.delete(yAxisId);
        this._manualYAxisIds.delete(yAxisId);
        if (this._defaultYAxisId === yAxisId) {
            this._defaultYAxisId = (_a = this._yAxisComponents.keys().next().value) !== null && _a !== void 0 ? _a : null;
        }
        var yAxisWidget = this._yAxisWidgets.get(yAxisId);
        if (isValid(yAxisWidget)) {
            yAxisWidget.destroy();
            this._yAxisWidgets.delete(yAxisId);
        }
        this._yAxesBounding = Object.keys(this._yAxesBounding).reduce(function (bounding, id) {
            if (id !== yAxisId) {
                bounding[id] = _this._yAxesBounding[id];
            }
            return bounding;
        }, {});
        return true;
    };
    DrawPane.prototype.getDefaultYAxisId = function () {
        return this._defaultYAxisId;
    };
    DrawPane.prototype.isDefaultYAxis = function (yAxisId) {
        return this._defaultYAxisId === yAxisId;
    };
    DrawPane.prototype.getYAxisComponentById = function (yAxisId) {
        var id = yAxisId !== null && yAxisId !== void 0 ? yAxisId : this.getDefaultYAxisId();
        return this._yAxisComponents.get(id);
    };
    DrawPane.prototype.getYAxisWidgetById = function (yAxisId) {
        var _a;
        var id = yAxisId !== null && yAxisId !== void 0 ? yAxisId : this.getDefaultYAxisId();
        return isValid(id) ? ((_a = this._yAxisWidgets.get(id)) !== null && _a !== void 0 ? _a : null) : null;
    };
    DrawPane.prototype.setYAxesBounding = function (bounding) {
        this._yAxesBounding = bounding;
    };
    DrawPane.prototype.setBounding = function (rootBounding, mainBounding, leftYAxisBounding, rightYAxisBounding) {
        var _this = this;
        merge(this.getBounding(), rootBounding);
        var contentBounding = {};
        if (isValid(rootBounding.height)) {
            contentBounding.height = rootBounding.height;
        }
        if (isValid(rootBounding.top)) {
            contentBounding.top = rootBounding.top;
        }
        this._mainWidget.setBounding(contentBounding);
        var mainBoundingValid = isValid(mainBounding);
        if (mainBoundingValid) {
            this._mainWidget.setBounding(mainBounding);
        }
        if (this._yAxisWidgets.size > 0) {
            this._yAxisWidgets.forEach(function (yAxisWidget, yAxisId) {
                var _a, _b, _c, _d;
                yAxisWidget.setBounding(contentBounding);
                if (isValid(_this._yAxesBounding[yAxisId])) {
                    yAxisWidget.setBounding(_this._yAxesBounding[yAxisId]);
                    return;
                }
                var yAxis = _this.getYAxisComponentById(yAxisId);
                if (yAxis.position === 'left') {
                    if (isValid(leftYAxisBounding)) {
                        yAxisWidget.setBounding(__assign(__assign({}, leftYAxisBounding), { left: 0 }));
                    }
                }
                else {
                    if (isValid(rightYAxisBounding)) {
                        yAxisWidget.setBounding(rightYAxisBounding);
                        if (mainBoundingValid) {
                            yAxisWidget.setBounding({
                                left: ((_a = mainBounding.left) !== null && _a !== void 0 ? _a : 0) + ((_b = mainBounding.width) !== null && _b !== void 0 ? _b : 0) + ((_c = mainBounding.right) !== null && _c !== void 0 ? _c : 0) - ((_d = rightYAxisBounding.width) !== null && _d !== void 0 ? _d : 0)
                            });
                        }
                    }
                }
            });
        }
        return this;
    };
    DrawPane.prototype.getMainWidget = function () {
        return this._mainWidget;
    };
    DrawPane.prototype.getYAxisWidget = function () {
        return this.getYAxisWidgetById();
    };
    DrawPane.prototype.getYAxisWidgets = function () {
        return Array.from(this._yAxisWidgets.values());
    };
    DrawPane.prototype.updateImp = function (level) {
        this._mainWidget.update(level);
        this._yAxisWidgets.forEach(function (widget) {
            widget.update(level);
        });
    };
    DrawPane.prototype.destroy = function () {
        this._mainWidget.destroy();
        this._yAxisWidgets.forEach(function (widget) {
            widget.destroy();
        });
    };
    DrawPane.prototype.getImage = function (includeOverlay) {
        var _a = this.getBounding(), width = _a.width, height = _a.height;
        var canvas = createDom('canvas', {
            width: "".concat(width, "px"),
            height: "".concat(height, "px"),
            boxSizing: 'border-box'
        });
        var ctx = canvas.getContext('2d');
        var pixelRatio = getPixelRatio(canvas);
        canvas.width = width * pixelRatio;
        canvas.height = height * pixelRatio;
        ctx.scale(pixelRatio, pixelRatio);
        var mainBounding = this._mainWidget.getBounding();
        ctx.drawImage(this._mainWidget.getImage(includeOverlay), mainBounding.left, 0, mainBounding.width, mainBounding.height);
        this._yAxisWidgets.forEach(function (yAxisWidget) {
            var yAxisBounding = yAxisWidget.getBounding();
            ctx.drawImage(yAxisWidget.getImage(includeOverlay), yAxisBounding.left, 0, yAxisBounding.width, yAxisBounding.height);
        });
        return canvas;
    };
    DrawPane.prototype.createYAxisComponent = function (_name) {
        throw new Error('createYAxisComponent is not implemented.');
    };
    DrawPane.prototype.createYAxisWidget = function (_container, _yAxis) {
        return null;
    };
    return DrawPane;
}(Pane));

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
var IndicatorPane = /** @class */ (function (_super) {
    __extends(IndicatorPane, _super);
    function IndicatorPane() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    IndicatorPane.prototype.createYAxisComponent = function (name) {
        var YAxisClass = getYAxisClass(name !== null && name !== void 0 ? name : 'default');
        return new YAxisClass(this);
    };
    IndicatorPane.prototype.createMainWidget = function (container) {
        return new IndicatorWidget(container, this);
    };
    IndicatorPane.prototype.createYAxisWidget = function (container, yAxis) {
        return new YAxisWidget(container, this, yAxis);
    };
    return IndicatorPane;
}(DrawPane));

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
var CandlePane = /** @class */ (function (_super) {
    __extends(CandlePane, _super);
    function CandlePane() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CandlePane.prototype.createMainWidget = function (container) {
        return new CandleWidget(container, this);
    };
    return CandlePane;
}(IndicatorPane));

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
function throttle(func, wait) {
    var previous = 0;
    return function () {
        var args = [];
        for (var _i = 0; _i < arguments.length; _i++) {
            args[_i] = arguments[_i];
        }
        var now = Date.now();
        if (now - previous > (wait )) {
            func.apply(this, args);
            previous = now;
        }
    };
}
// export function memoize<R1 = any, R2 = any> (func: (...args: any[]) => R1, resolver?: (...args: any[]) => R2): (...args: any[]) => R1 {
//   if (!isFunction(func) || (isValid(resolver) && !isFunction(resolver))) {
//     throw new TypeError('Expected a function')
//   }
//   const memoized = function (...args: any[]): any {
//     const key = isFunction(resolver) ? resolver.apply(this, args) : args[0]
//     const cache = memoized.cache
//     if (cache.has(key)) {
//       return cache.get(key)
//     }
//     const result = func.apply(this, args)
//     memoized.cache = cache.set(key, result) || cache
//     return result
//   }
//   memoized.cache = new (memoize.Cache || Map)()
//   return memoized
// }
// memoize.Cache = Map

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
var SeparatorWidget = /** @class */ (function (_super) {
    __extends(SeparatorWidget, _super);
    function SeparatorWidget(rootContainer, pane) {
        var _this = _super.call(this, rootContainer, pane) || this;
        _this._dragFlag = false;
        _this._dragStartY = 0;
        _this._topPaneHeight = 0;
        _this._bottomPaneHeight = 0;
        _this._topPane = null;
        _this._bottomPane = null;
        _this._pressedMouseMoveEvent = throttle(_this._pressedTouchMouseMoveEvent, 20);
        _this.registerEvent('touchStartEvent', _this._mouseDownEvent.bind(_this))
            // @ts-ignore
            .registerEvent('touchMoveEvent', _this._pressedMouseMoveEvent.bind(_this))
            .registerEvent('touchEndEvent', _this._mouseUpEvent.bind(_this))
            .registerEvent('mouseDownEvent', _this._mouseDownEvent.bind(_this))
            .registerEvent('mouseUpEvent', _this._mouseUpEvent.bind(_this))
            // @ts-ignore
            .registerEvent('pressedMouseMoveEvent', _this._pressedMouseMoveEvent.bind(_this))
            .registerEvent('mouseEnterEvent', _this._mouseEnterEvent.bind(_this))
            .registerEvent('mouseLeaveEvent', _this._mouseLeaveEvent.bind(_this));
        return _this;
    }
    SeparatorWidget.prototype.getName = function () {
        return WidgetNameConstants.SEPARATOR;
    };
    SeparatorWidget.prototype._dragEnabled = function (topPane, bottomPane) {
        return topPane.getOptions().state === 'normal' && bottomPane.getOptions().state === 'normal' && bottomPane.getOptions().dragEnabled;
    };
    SeparatorWidget.prototype._findAdjustablePane = function (startIndex, step) {
        var drawPanes = this.getPane().getChart().getDrawPanes();
        for (var i = startIndex; i >= 0 && i < drawPanes.length; i += step) {
            var pane = drawPanes[i];
            if (pane.getId() !== PaneIdConstants.X_AXIS && pane.getOptions().state === 'normal') {
                return pane;
            }
        }
        return null;
    };
    SeparatorWidget.prototype._findDragPanes = function () {
        var currentPane = this.getPane();
        var drawPanes = currentPane.getChart().getDrawPanes();
        var topPaneIndex = drawPanes.indexOf(currentPane.getTopPane());
        var bottomPaneIndex = drawPanes.indexOf(currentPane.getBottomPane());
        if (topPaneIndex === -1 || bottomPaneIndex === -1) {
            return null;
        }
        var topPane = this._findAdjustablePane(topPaneIndex, -1);
        var bottomPane = this._findAdjustablePane(bottomPaneIndex, 1);
        if (isValid(topPane) && isValid(bottomPane) && this._dragEnabled(topPane, bottomPane)) {
            return { topPane: topPane, bottomPane: bottomPane };
        }
        return null;
    };
    SeparatorWidget.prototype._mouseDownEvent = function (event) {
        var dragPanes = this._findDragPanes();
        if (!isValid(dragPanes)) {
            this._topPane = null;
            this._bottomPane = null;
            return false;
        }
        this._topPane = dragPanes.topPane;
        this._bottomPane = dragPanes.bottomPane;
        this._dragFlag = true;
        this._dragStartY = event.pageY;
        this._topPaneHeight = this._topPane.getBounding().height;
        this._bottomPaneHeight = this._bottomPane.getBounding().height;
        return true;
    };
    SeparatorWidget.prototype._mouseUpEvent = function () {
        this._dragFlag = false;
        this._topPane = null;
        this._bottomPane = null;
        this._topPaneHeight = 0;
        this._bottomPaneHeight = 0;
        return this._mouseLeaveEvent();
    };
    SeparatorWidget.prototype._pressedTouchMouseMoveEvent = function (event) {
        var dragDistance = event.pageY - this._dragStartY;
        var isUpDrag = dragDistance < 0;
        if (isValid(this._topPane) && isValid(this._bottomPane)) {
            if (this._dragEnabled(this._topPane, this._bottomPane)) {
                var reducedPane = null;
                var increasedPane = null;
                var startDragReducedPaneHeight = 0;
                var startDragIncreasedPaneHeight = 0;
                if (isUpDrag) {
                    reducedPane = this._topPane;
                    increasedPane = this._bottomPane;
                    startDragReducedPaneHeight = this._topPaneHeight;
                    startDragIncreasedPaneHeight = this._bottomPaneHeight;
                }
                else {
                    reducedPane = this._bottomPane;
                    increasedPane = this._topPane;
                    startDragReducedPaneHeight = this._bottomPaneHeight;
                    startDragIncreasedPaneHeight = this._topPaneHeight;
                }
                var reducedPaneMinHeight = reducedPane.getOptions().minHeight;
                if (startDragReducedPaneHeight > reducedPaneMinHeight) {
                    var reducedPaneHeight = Math.max(startDragReducedPaneHeight - Math.abs(dragDistance), reducedPaneMinHeight);
                    var diffHeight = startDragReducedPaneHeight - reducedPaneHeight;
                    reducedPane.setBounding({ height: reducedPaneHeight });
                    var increasedPaneHeight = startDragIncreasedPaneHeight + diffHeight;
                    increasedPane.setBounding({ height: increasedPaneHeight });
                    reducedPane.setOptions({ height: reducedPaneHeight });
                    increasedPane.setOptions({ height: increasedPaneHeight });
                    var currentPane = this.getPane();
                    var chart = currentPane.getChart();
                    chart.getChartStore().executeAction('onPaneDrag', { paneId: currentPane.getId() });
                    chart.layout({
                        measureHeight: true,
                        measureWidth: true,
                        update: true,
                        buildYAxisTick: true,
                        forceBuildYAxisTick: true
                    });
                }
            }
        }
        return true;
    };
    SeparatorWidget.prototype._mouseEnterEvent = function () {
        var dragPanes = this._findDragPanes();
        if (isValid(dragPanes)) {
            var chart = this.getPane().getChart();
            var styles = chart.getStyles().separator;
            this.getContainer().style.background = styles.activeBackgroundColor;
            return true;
        }
        return false;
    };
    SeparatorWidget.prototype._mouseLeaveEvent = function () {
        if (!this._dragFlag) {
            this.getContainer().style.background = 'transparent';
            return true;
        }
        return false;
    };
    SeparatorWidget.prototype.createContainer = function () {
        return createDom('div', {
            width: '100%',
            height: "".concat(REAL_SEPARATOR_HEIGHT, "px"),
            margin: '0',
            padding: '0',
            position: 'absolute',
            top: '-3px',
            zIndex: '20',
            boxSizing: 'border-box',
            cursor: 'ns-resize'
        });
    };
    SeparatorWidget.prototype.updateImp = function (container, _bounding, level) {
        if (level === 4 /* UpdateLevel.All */ || level === 2 /* UpdateLevel.Separator */) {
            var styles = this.getPane().getChart().getStyles().separator;
            container.style.top = "".concat(-Math.floor((REAL_SEPARATOR_HEIGHT - styles.size) / 2), "px");
            container.style.height = "".concat(REAL_SEPARATOR_HEIGHT, "px");
        }
    };
    return SeparatorWidget;
}(Widget));

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
var SeparatorPane = /** @class */ (function (_super) {
    __extends(SeparatorPane, _super);
    function SeparatorPane(chart, id, topPane, bottomPane) {
        var _this = _super.call(this, chart, id) || this;
        _this.getContainer().style.overflow = '';
        _this._topPane = topPane;
        _this._bottomPane = bottomPane;
        _this._separatorWidget = new SeparatorWidget(_this.getContainer(), _this);
        return _this;
    }
    SeparatorPane.prototype.setBounding = function (rootBounding) {
        merge(this.getBounding(), rootBounding);
        return this;
    };
    SeparatorPane.prototype.getTopPane = function () {
        return this._topPane;
    };
    SeparatorPane.prototype.setTopPane = function (pane) {
        this._topPane = pane;
        return this;
    };
    SeparatorPane.prototype.getBottomPane = function () {
        return this._bottomPane;
    };
    SeparatorPane.prototype.setBottomPane = function (pane) {
        this._bottomPane = pane;
        return this;
    };
    SeparatorPane.prototype.getWidget = function () {
        return this._separatorWidget;
    };
    SeparatorPane.prototype.getImage = function (_includeOverlay) {
        var _a = this.getBounding(), width = _a.width, height = _a.height;
        var styles = this.getChart().getStyles().separator;
        var canvas = createDom('canvas', {
            width: "".concat(width, "px"),
            height: "".concat(height, "px"),
            boxSizing: 'border-box'
        });
        var ctx = canvas.getContext('2d');
        var pixelRatio = getPixelRatio(canvas);
        canvas.width = width * pixelRatio;
        canvas.height = height * pixelRatio;
        ctx.scale(pixelRatio, pixelRatio);
        ctx.fillStyle = styles.color;
        ctx.fillRect(0, 0, width, height);
        return canvas;
    };
    SeparatorPane.prototype.updateImp = function (level, container, bounding) {
        if (level === 4 /* UpdateLevel.All */ || level === 2 /* UpdateLevel.Separator */) {
            var styles = this.getChart().getStyles().separator;
            container.style.backgroundColor = styles.color;
            container.style.height = "".concat(bounding.height, "px");
            container.style.marginLeft = "".concat(bounding.left, "px");
            container.style.width = "".concat(bounding.width, "px");
            this._separatorWidget.update(level);
        }
    };
    return SeparatorPane;
}(Pane));

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
var XAxisImp = /** @class */ (function (_super) {
    __extends(XAxisImp, _super);
    function XAxisImp(parent, xAxis) {
        var _this = _super.call(this, parent) || this;
        _this.override(xAxis);
        return _this;
    }
    XAxisImp.prototype.override = function (xAxis) {
        var name = xAxis.name, scrollZoomEnabled = xAxis.scrollZoomEnabled, createTicks = xAxis.createTicks;
        if (!isString(this.name) && isString(name)) {
            this.name = name;
        }
        this.scrollZoomEnabled = scrollZoomEnabled !== null && scrollZoomEnabled !== void 0 ? scrollZoomEnabled : this.scrollZoomEnabled;
        this.createTicks = createTicks !== null && createTicks !== void 0 ? createTicks : this.createTicks;
    };
    XAxisImp.prototype.createRangeImp = function () {
        var chartStore = this.getParent().getChart().getChartStore();
        var visibleDataRange = chartStore.getVisibleRange();
        var realFrom = visibleDataRange.realFrom, realTo = visibleDataRange.realTo;
        var af = realFrom;
        var at = realTo;
        var diff = realTo - realFrom + 1;
        var range = {
            from: af,
            to: at,
            range: diff,
            realFrom: af,
            realTo: at,
            realRange: diff,
            displayFrom: af,
            displayTo: at,
            displayRange: diff
        };
        return range;
    };
    XAxisImp.prototype.createTicksImp = function () {
        var _a;
        var _b = this.getRange(), realFrom = _b.realFrom, realTo = _b.realTo, from = _b.from;
        var chartStore = this.getParent().getChart().getChartStore();
        var formatDate = chartStore.getInnerFormatter().formatDate;
        var period = chartStore.getPeriod();
        var ticks = [];
        var barSpace = chartStore.getBarSpace().bar;
        var textStyles = chartStore.getStyles().xAxis.tickText;
        var tickTextWidth = Math.max(calcTextWidth('YYYY-MM-DD HH:mm:ss', textStyles.size, textStyles.weight, textStyles.family), this.getBounding().width / TICK_COUNT);
        var tickBetweenBarCount = Math.ceil(tickTextWidth / barSpace);
        if (tickBetweenBarCount % 2 !== 0) {
            tickBetweenBarCount += 1;
        }
        var startDataIndex = Math.max(0, Math.floor(realFrom / tickBetweenBarCount) * tickBetweenBarCount);
        for (var i = startDataIndex; i < realTo; i += tickBetweenBarCount) {
            if (i >= from) {
                var timestamp = chartStore.dataIndexToTimestamp(i);
                if (isNumber(timestamp)) {
                    ticks.push({
                        coord: this.convertToPixel(i),
                        value: timestamp,
                        text: formatDate(timestamp, PeriodTypeXAxisFormat[(_a = period === null || period === void 0 ? void 0 : period.type) !== null && _a !== void 0 ? _a : 'day'], 'xAxis')
                    });
                }
            }
        }
        if (isFunction(this.createTicks)) {
            return this.createTicks({
                range: this.getRange(),
                bounding: this.getBounding(),
                defaultTicks: ticks
            });
        }
        return ticks;
    };
    XAxisImp.prototype.getAutoSize = function () {
        var styles = this.getParent().getChart().getStyles();
        var xAxisStyles = styles.xAxis;
        var height = xAxisStyles.size;
        if (height !== 'auto') {
            return height;
        }
        var crosshairStyles = styles.crosshair;
        var xAxisHeight = 0;
        if (xAxisStyles.show) {
            if (xAxisStyles.axisLine.show) {
                xAxisHeight += xAxisStyles.axisLine.size;
            }
            if (xAxisStyles.tickLine.show) {
                xAxisHeight += xAxisStyles.tickLine.length;
            }
            if (xAxisStyles.tickText.show) {
                xAxisHeight += xAxisStyles.tickText.marginStart + xAxisStyles.tickText.marginEnd + xAxisStyles.tickText.size;
            }
        }
        var crosshairVerticalTextHeight = 0;
        if (crosshairStyles.show && crosshairStyles.vertical.show && crosshairStyles.vertical.text.show) {
            crosshairVerticalTextHeight += crosshairStyles.vertical.text.paddingTop + crosshairStyles.vertical.text.paddingBottom + crosshairStyles.vertical.text.borderSize * 2 + crosshairStyles.vertical.text.size;
        }
        return Math.max(xAxisHeight, crosshairVerticalTextHeight);
    };
    XAxisImp.prototype.getBounding = function () {
        return this.getParent().getMainWidget().getBounding();
    };
    XAxisImp.prototype.convertTimestampFromPixel = function (pixel) {
        var chartStore = this.getParent().getChart().getChartStore();
        var dataIndex = chartStore.coordinateToDataIndex(pixel);
        return chartStore.dataIndexToTimestamp(dataIndex);
    };
    XAxisImp.prototype.convertTimestampToPixel = function (timestamp) {
        var chartStore = this.getParent().getChart().getChartStore();
        var dataIndex = chartStore.timestampToDataIndex(timestamp);
        return chartStore.dataIndexToCoordinate(dataIndex);
    };
    XAxisImp.prototype.convertFromPixel = function (pixel) {
        return this.getParent().getChart().getChartStore().coordinateToDataIndex(pixel);
    };
    XAxisImp.prototype.convertToPixel = function (value) {
        return this.getParent().getChart().getChartStore().dataIndexToCoordinate(value);
    };
    XAxisImp.extend = function (template) {
        var Custom = /** @class */ (function (_super) {
            __extends(Custom, _super);
            function Custom(parent) {
                return _super.call(this, parent, template) || this;
            }
            return Custom;
        }(XAxisImp));
        return Custom;
    };
    return XAxisImp;
}(AxisImp));

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
var normal = {
    name: 'normal'
};

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
var xAxises = {
    normal: XAxisImp.extend(normal)
};
function registerXAxis(axis) {
    xAxises[axis.name] = XAxisImp.extend(axis);
}
function getXAxisClass(name) {
    var _a;
    return (_a = xAxises[name]) !== null && _a !== void 0 ? _a : xAxises.normal;
}

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
var CrosshairVerticalLabelView = /** @class */ (function (_super) {
    __extends(CrosshairVerticalLabelView, _super);
    function CrosshairVerticalLabelView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    CrosshairVerticalLabelView.prototype.compare = function (crosshair) {
        return isValid(crosshair.timestamp);
    };
    CrosshairVerticalLabelView.prototype.getDirectionStyles = function (styles) {
        return styles.vertical;
    };
    CrosshairVerticalLabelView.prototype.getText = function (crosshair, chartStore) {
        var _a, _b;
        var timestamp = crosshair.timestamp;
        return chartStore.getInnerFormatter().formatDate(timestamp, PeriodTypeCrosshairTooltipFormat[(_b = (_a = chartStore.getPeriod()) === null || _a === void 0 ? void 0 : _a.type) !== null && _b !== void 0 ? _b : 'day'], 'crosshair');
    };
    CrosshairVerticalLabelView.prototype.getTextAttrs = function (text, textWidth, crosshair, bounding, _axis, styles) {
        var x = crosshair.realX;
        var optimalX = 0;
        var align = 'center';
        if (x - textWidth / 2 - styles.paddingLeft < 0) {
            optimalX = 0;
            align = 'left';
        }
        else if (x + textWidth / 2 + styles.paddingRight > bounding.width) {
            optimalX = bounding.width;
            align = 'right';
        }
        else {
            optimalX = x;
        }
        return { x: optimalX, y: 0, text: text, align: align, baseline: 'top' };
    };
    return CrosshairVerticalLabelView;
}(CrosshairHorizontalLabelView));

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
var OverlayXAxisView = /** @class */ (function (_super) {
    __extends(OverlayXAxisView, _super);
    function OverlayXAxisView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    OverlayXAxisView.prototype.coordinateToPointTimestampDataIndexFlag = function () {
        return true;
    };
    OverlayXAxisView.prototype.coordinateToPointValueFlag = function () {
        return false;
    };
    OverlayXAxisView.prototype.getCompleteOverlays = function () {
        return this.getWidget().getPane().getChart().getChartStore().getOverlaysByPaneId();
    };
    OverlayXAxisView.prototype.getProgressOverlay = function () {
        var _a, _b;
        return (_b = (_a = this.getWidget().getPane().getChart().getChartStore().getProgressOverlayInfo()) === null || _a === void 0 ? void 0 : _a.overlay) !== null && _b !== void 0 ? _b : null;
    };
    OverlayXAxisView.prototype.getDefaultFigures = function (overlay, coordinates) {
        var _a;
        var figures = [];
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chartStore = pane.getChart().getChartStore();
        var clickOverlayInfo = chartStore.getClickOverlayInfo();
        if (overlay.needDefaultXAxisFigure && overlay.id === ((_a = clickOverlayInfo.overlay) === null || _a === void 0 ? void 0 : _a.id)) {
            var leftX_1 = Number.MAX_SAFE_INTEGER;
            var rightX_1 = Number.MIN_SAFE_INTEGER;
            coordinates.forEach(function (coordinate, index) {
                leftX_1 = Math.min(leftX_1, coordinate.x);
                rightX_1 = Math.max(rightX_1, coordinate.x);
                var point = overlay.points[index];
                if (isNumber(point.timestamp)) {
                    var text = chartStore.getInnerFormatter().formatDate(point.timestamp, 'YYYY-MM-DD HH:mm', 'crosshair');
                    figures.push({ type: 'text', attrs: { x: coordinate.x, y: 0, text: text, align: 'center' }, ignoreEvent: true });
                }
            });
            if (coordinates.length > 1) {
                figures.unshift({ type: 'rect', attrs: { x: leftX_1, y: 0, width: rightX_1 - leftX_1, height: widget.getBounding().height }, ignoreEvent: true });
            }
        }
        return figures;
    };
    OverlayXAxisView.prototype.getFigures = function (o, coordinates) {
        var _a, _b;
        var widget = this.getWidget();
        var pane = widget.getPane();
        var chart = pane.getChart();
        var yAxis = pane.getYAxisComponentById();
        var xAxis = chart.getXAxisPane().getXAxisComponent();
        var bounding = widget.getBounding();
        return (_b = (_a = o.createXAxisFigures) === null || _a === void 0 ? void 0 : _a.call(o, { chart: chart, overlay: o, coordinates: coordinates, bounding: bounding, xAxis: xAxis, yAxis: yAxis })) !== null && _b !== void 0 ? _b : [];
    };
    return OverlayXAxisView;
}(OverlayYAxisView));

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
var XAxisView = /** @class */ (function (_super) {
    __extends(XAxisView, _super);
    function XAxisView() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    XAxisView.prototype.getAxis = function () {
        return this.getWidget().getPane().getXAxisComponent();
    };
    XAxisView.prototype.getAxisStyles = function (styles) {
        return styles.xAxis;
    };
    XAxisView.prototype.createAxisLine = function (bounding) {
        return {
            coordinates: [
                { x: 0, y: 0 },
                { x: bounding.width, y: 0 }
            ]
        };
    };
    XAxisView.prototype.createTickLines = function (ticks, _bounding, styles) {
        var tickLineStyles = styles.tickLine;
        var axisLineSize = styles.axisLine.size;
        return ticks.map(function (tick) { return ({
            coordinates: [
                { x: tick.coord, y: 0 },
                { x: tick.coord, y: axisLineSize + tickLineStyles.length }
            ]
        }); });
    };
    XAxisView.prototype.createTickTexts = function (ticks, _bounding, styles) {
        var tickTickStyles = styles.tickText;
        var axisLineSize = styles.axisLine.size;
        var tickLineLength = styles.tickLine.length;
        return ticks.map(function (tick) { return ({
            x: tick.coord,
            y: axisLineSize + tickLineLength + tickTickStyles.marginStart,
            text: tick.text,
            align: 'center',
            baseline: 'top'
        }); });
    };
    return XAxisView;
}(AxisView));

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
var XAxisWidget = /** @class */ (function (_super) {
    __extends(XAxisWidget, _super);
    function XAxisWidget(rootContainer, pane) {
        var _this = _super.call(this, rootContainer, pane) || this;
        _this._xAxisView = new XAxisView(_this);
        _this._overlayXAxisView = new OverlayXAxisView(_this);
        _this._crosshairVerticalLabelView = new CrosshairVerticalLabelView(_this);
        _this.setCursor('ew-resize');
        _this.addChild(_this._overlayXAxisView);
        return _this;
    }
    XAxisWidget.prototype.getName = function () {
        return WidgetNameConstants.X_AXIS;
    };
    XAxisWidget.prototype.updateMain = function (ctx) {
        this._xAxisView.draw(ctx);
    };
    XAxisWidget.prototype.updateOverlay = function (ctx) {
        this._overlayXAxisView.draw(ctx);
        this._crosshairVerticalLabelView.draw(ctx);
    };
    return XAxisWidget;
}(DrawWidget));

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
var XAxisPane = /** @class */ (function (_super) {
    __extends(XAxisPane, _super);
    function XAxisPane(chart, options) {
        var _this = _super.call(this, chart, options) || this;
        _this.overrideXAxis({ name: 'normal', scrollZoomEnabled: true });
        return _this;
    }
    XAxisPane.prototype.setOptions = function (options) {
        return _super.prototype.setOptions.call(this, options);
    };
    XAxisPane.prototype.overrideXAxis = function (xAxis) {
        var axisName = xAxis.name;
        if (!isValid(this._xAxis) || (isValid(axisName) && this._xAxis.name !== axisName)) {
            this._xAxis = this.createXAxisComponent(axisName !== null && axisName !== void 0 ? axisName : 'normal');
        }
        this._xAxis.override(xAxis);
        this.setAxisCursor(this._xAxis.scrollZoomEnabled);
        return this;
    };
    XAxisPane.prototype.getXAxisComponent = function () {
        return this._xAxis;
    };
    XAxisPane.prototype.createXAxisComponent = function (name) {
        var XAxisClass = getXAxisClass(name);
        return new XAxisClass(this);
    };
    XAxisPane.prototype.createMainWidget = function (container) {
        return new XAxisWidget(container, this);
    };
    return XAxisPane;
}(DrawPane));

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
var Action = /** @class */ (function () {
    function Action() {
        this._callbacks = [];
    }
    Action.prototype.subscribe = function (callback) {
        var index = this._callbacks.indexOf(callback);
        if (index < 0) {
            this._callbacks.push(callback);
        }
    };
    Action.prototype.unsubscribe = function (callback) {
        if (isFunction(callback)) {
            var index = this._callbacks.indexOf(callback);
            if (index > -1) {
                this._callbacks.splice(index, 1);
            }
        }
        else {
            this._callbacks = [];
        }
    };
    Action.prototype.execute = function (data) {
        this._callbacks.forEach(function (callback) {
            callback(data);
        });
    };
    Action.prototype.isEmpty = function () {
        return this._callbacks.length === 0;
    };
    return Action;
}());

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
var Color = {
    RED: '#F92855',
    GREEN: '#2DC08E',
    WHITE: '#FFFFFF',
    GREY: '#76808F',
    BLUE: '#1677FF'
};
function getDefaultGridStyle() {
    return {
        show: true,
        horizontal: {
            show: true,
            size: 1,
            color: '#EDEDED',
            style: 'dashed',
            dashedValue: [2, 2]
        },
        vertical: {
            show: true,
            size: 1,
            color: '#EDEDED',
            style: 'dashed',
            dashedValue: [2, 2]
        }
    };
}
/**
 * Get default candle style
 * @type {{area: {backgroundColor: [{offset: number, color: string}, {offset: number, color: string}], lineColor: string, lineSize: number, value: string}, bar: {noChangeColor: string, upColor: string, downColor: string}, tooltip: {rect: {offsetTop: number, fillColor: string, borderColor: string, paddingBottom: number, borderRadius: number, paddingRight: number, borderSize: number, offsetLeft: number, paddingTop: number, paddingLeft: number, offsetRight: number}, showRule: string, values: null, showType: string, text: {marginRight: number, size: number, color: string, weight: string, marginBottom: number, family: string, marginTop: number, marginLeft: number}, labels: string[]}, type: string, priceMark: {high: {textMargin: number, textSize: number, color: string, textFamily: string, show: boolean, textWeight: string}, last: {noChangeColor: string, upColor: string, line: {dashValue: number[], size: number, show: boolean, style: string}, show: boolean, text: {paddingBottom: number, size: number, color: string, paddingRight: number, show: boolean, weight: string, paddingTop: number, family: string, paddingLeft: number}, downColor: string}, low: {textMargin: number, textSize: number, color: string, textFamily: string, show: boolean, textWeight: string}, show: boolean}}}
 */
function getDefaultCandleStyle() {
    var highLow = {
        show: true,
        color: Color.GREY,
        textOffset: 5,
        textSize: 10,
        textFamily: 'Helvetica Neue',
        textWeight: 'normal'
    };
    return {
        type: 'candle_solid',
        bar: {
            compareRule: 'current_open',
            upColor: Color.GREEN,
            downColor: Color.RED,
            noChangeColor: Color.GREY,
            upBorderColor: Color.GREEN,
            downBorderColor: Color.RED,
            noChangeBorderColor: Color.GREY,
            upWickColor: Color.GREEN,
            downWickColor: Color.RED,
            noChangeWickColor: Color.GREY
        },
        area: {
            lineSize: 2,
            lineColor: Color.BLUE,
            smooth: false,
            value: 'close',
            backgroundColor: [
                {
                    offset: 0,
                    color: hexToRgb(Color.BLUE, 0.01)
                },
                {
                    offset: 1,
                    color: hexToRgb(Color.BLUE, 0.2)
                }
            ],
            point: {
                show: true,
                color: Color.BLUE,
                radius: 4,
                rippleColor: hexToRgb(Color.BLUE, 0.3),
                rippleRadius: 8,
                animation: true,
                animationDuration: 1000
            }
        },
        priceMark: {
            show: true,
            high: __assign({}, highLow),
            low: __assign({}, highLow),
            last: {
                show: true,
                compareRule: 'current_open',
                upColor: Color.GREEN,
                downColor: Color.RED,
                noChangeColor: Color.GREY,
                line: {
                    show: true,
                    style: 'dashed',
                    dashedValue: [4, 4],
                    size: 1
                },
                text: {
                    show: true,
                    style: 'fill',
                    size: 12,
                    paddingLeft: 4,
                    paddingTop: 4,
                    paddingRight: 4,
                    paddingBottom: 4,
                    borderColor: 'transparent',
                    borderStyle: 'solid',
                    borderSize: 0,
                    borderDashedValue: [2, 2],
                    color: Color.WHITE,
                    family: 'Helvetica Neue',
                    weight: 'normal',
                    borderRadius: 2
                },
                extendTexts: []
            }
        },
        tooltip: {
            offsetLeft: 4,
            offsetTop: 6,
            offsetRight: 4,
            offsetBottom: 6,
            showRule: 'always',
            showType: 'standard',
            rect: {
                position: 'fixed',
                paddingLeft: 4,
                paddingRight: 4,
                paddingTop: 4,
                paddingBottom: 4,
                offsetLeft: 4,
                offsetTop: 4,
                offsetRight: 4,
                offsetBottom: 4,
                borderRadius: 4,
                borderSize: 1,
                borderColor: '#F2F3F5',
                color: '#FEFEFE'
            },
            title: {
                show: true,
                size: 14,
                family: 'Helvetica Neue',
                weight: 'normal',
                color: Color.GREY,
                marginLeft: 8,
                marginTop: 4,
                marginRight: 8,
                marginBottom: 4,
                template: '{ticker} · {period}'
            },
            legend: {
                size: 12,
                family: 'Helvetica Neue',
                weight: 'normal',
                color: Color.GREY,
                marginLeft: 8,
                marginTop: 4,
                marginRight: 8,
                marginBottom: 4,
                defaultValue: 'n/a',
                template: [
                    { title: 'time', value: '{time}' },
                    { title: 'open', value: '{open}' },
                    { title: 'high', value: '{high}' },
                    { title: 'low', value: '{low}' },
                    { title: 'close', value: '{close}' },
                    { title: 'volume', value: '{volume}' }
                ]
            },
            features: []
        }
    };
}
/**
 * Get default indicator style
 */
function getDefaultIndicatorStyle() {
    var alphaGreen = hexToRgb(Color.GREEN, 0.7);
    var alphaRed = hexToRgb(Color.RED, 0.7);
    return {
        ohlc: {
            compareRule: 'current_open',
            upColor: alphaGreen,
            downColor: alphaRed,
            noChangeColor: Color.GREY
        },
        bars: [
            {
                style: 'fill',
                borderStyle: 'solid',
                borderSize: 1,
                borderDashedValue: [2, 2],
                upColor: alphaGreen,
                downColor: alphaRed,
                noChangeColor: Color.GREY
            }
        ],
        lines: ['#FF9600', '#935EBD', Color.BLUE, '#E11D74', '#01C5C4'].map(function (color) { return ({
            style: 'solid',
            smooth: false,
            size: 1,
            dashedValue: [2, 2],
            color: color
        }); }),
        circles: [
            {
                style: 'fill',
                borderStyle: 'solid',
                borderSize: 1,
                borderDashedValue: [2, 2],
                upColor: alphaGreen,
                downColor: alphaRed,
                noChangeColor: Color.GREY
            }
        ],
        texts: [
            {
                paddingLeft: 0,
                paddingTop: 0,
                paddingRight: 0,
                paddingBottom: 0,
                style: 'fill',
                size: 12,
                color: Color.BLUE,
                family: 'Helvetica Neue',
                weight: 'normal',
                borderStyle: 'solid',
                borderDashedValue: [2, 2],
                borderSize: 0,
                borderColor: 'transparent',
                borderRadius: 0,
                backgroundColor: 'transparent'
            }
        ],
        lastValueMark: {
            show: false,
            text: {
                show: false,
                style: 'fill',
                color: Color.WHITE,
                size: 12,
                family: 'Helvetica Neue',
                weight: 'normal',
                borderStyle: 'solid',
                borderColor: 'transparent',
                borderSize: 0,
                borderDashedValue: [2, 2],
                paddingLeft: 4,
                paddingTop: 4,
                paddingRight: 4,
                paddingBottom: 4,
                borderRadius: 2
            }
        },
        tooltip: {
            offsetLeft: 4,
            offsetTop: 6,
            offsetRight: 4,
            offsetBottom: 6,
            showRule: 'always',
            showType: 'standard',
            title: {
                show: true,
                showName: true,
                showParams: true,
                size: 12,
                family: 'Helvetica Neue',
                weight: 'normal',
                color: Color.GREY,
                marginLeft: 8,
                marginTop: 4,
                marginRight: 8,
                marginBottom: 4
            },
            legend: {
                size: 12,
                family: 'Helvetica Neue',
                weight: 'normal',
                color: Color.GREY,
                marginLeft: 8,
                marginTop: 4,
                marginRight: 8,
                marginBottom: 4,
                defaultValue: 'n/a'
            },
            features: []
        }
    };
}
function getDefaultAxisStyle() {
    return {
        show: true,
        size: 'auto',
        axisLine: {
            show: true,
            color: '#DDDDDD',
            size: 1
        },
        tickText: {
            show: true,
            color: Color.GREY,
            size: 12,
            family: 'Helvetica Neue',
            weight: 'normal',
            marginStart: 4,
            marginEnd: 6
        },
        tickLine: {
            show: true,
            size: 1,
            length: 3,
            color: '#DDDDDD'
        }
    };
}
function getDefaultCrosshairStyle() {
    return {
        show: true,
        horizontal: {
            show: true,
            line: {
                show: true,
                style: 'dashed',
                dashedValue: [4, 2],
                size: 1,
                color: Color.GREY
            },
            text: {
                show: true,
                style: 'fill',
                color: Color.WHITE,
                size: 12,
                family: 'Helvetica Neue',
                weight: 'normal',
                borderStyle: 'solid',
                borderDashedValue: [2, 2],
                borderSize: 1,
                borderColor: Color.GREY,
                borderRadius: 2,
                paddingLeft: 4,
                paddingRight: 4,
                paddingTop: 4,
                paddingBottom: 4,
                backgroundColor: Color.GREY
            },
            features: []
        },
        vertical: {
            show: true,
            line: {
                show: true,
                style: 'dashed',
                dashedValue: [4, 2],
                size: 1,
                color: Color.GREY
            },
            text: {
                show: true,
                style: 'fill',
                color: Color.WHITE,
                size: 12,
                family: 'Helvetica Neue',
                weight: 'normal',
                borderStyle: 'solid',
                borderDashedValue: [2, 2],
                borderSize: 1,
                borderColor: Color.GREY,
                borderRadius: 2,
                paddingLeft: 4,
                paddingRight: 4,
                paddingTop: 4,
                paddingBottom: 4,
                backgroundColor: Color.GREY
            }
        }
    };
}
function getDefaultOverlayStyle() {
    var pointBorderColor = hexToRgb(Color.BLUE, 0.35);
    var alphaBg = hexToRgb(Color.BLUE, 0.25);
    function text() {
        return {
            style: 'fill',
            color: Color.WHITE,
            size: 12,
            family: 'Helvetica Neue',
            weight: 'normal',
            borderStyle: 'solid',
            borderDashedValue: [2, 2],
            borderSize: 1,
            borderRadius: 2,
            borderColor: Color.BLUE,
            paddingLeft: 4,
            paddingRight: 4,
            paddingTop: 4,
            paddingBottom: 4,
            backgroundColor: Color.BLUE
        };
    }
    return {
        point: {
            color: Color.BLUE,
            borderColor: pointBorderColor,
            borderSize: 1,
            radius: 5,
            activeColor: Color.BLUE,
            activeBorderColor: pointBorderColor,
            activeBorderSize: 3,
            activeRadius: 5
        },
        line: {
            style: 'solid',
            smooth: false,
            color: Color.BLUE,
            size: 1,
            dashedValue: [2, 2]
        },
        rect: {
            style: 'fill',
            color: alphaBg,
            borderColor: Color.BLUE,
            borderSize: 1,
            borderRadius: 0,
            borderStyle: 'solid',
            borderDashedValue: [2, 2]
        },
        polygon: {
            style: 'fill',
            color: Color.BLUE,
            borderColor: Color.BLUE,
            borderSize: 1,
            borderStyle: 'solid',
            borderDashedValue: [2, 2]
        },
        circle: {
            style: 'fill',
            color: alphaBg,
            borderColor: Color.BLUE,
            borderSize: 1,
            borderStyle: 'solid',
            borderDashedValue: [2, 2]
        },
        arc: {
            style: 'solid',
            color: Color.BLUE,
            size: 1,
            dashedValue: [2, 2]
        },
        text: text()
    };
}
function getDefaultSeparatorStyle() {
    return {
        size: 1,
        color: '#DDDDDD',
        fill: true,
        activeBackgroundColor: hexToRgb(Color.BLUE, 0.08)
    };
}
function getDefaultStyles() {
    return {
        grid: getDefaultGridStyle(),
        candle: getDefaultCandleStyle(),
        indicator: getDefaultIndicatorStyle(),
        xAxis: getDefaultAxisStyle(),
        yAxis: getDefaultAxisStyle(),
        separator: getDefaultSeparatorStyle(),
        crosshair: getDefaultCrosshairStyle(),
        overlay: getDefaultOverlayStyle()
    };
}

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
var TaskScheduler = /** @class */ (function () {
    function TaskScheduler(callback) {
        this._holdingTasks = null;
        this._running = false;
        this._callback = callback;
    }
    TaskScheduler.prototype.add = function (tasks) {
        if (!this._running) {
            void this._runTask(tasks);
        }
        else {
            if (isValid(this._holdingTasks)) {
                this._holdingTasks = __assign(__assign({}, this._holdingTasks), tasks);
            }
            else {
                this._holdingTasks = tasks;
            }
        }
    };
    TaskScheduler.prototype._runTask = function (tasks) {
        return __awaiter(this, void 0, void 0, function () {
            var next;
            var _a;
            return __generator(this, function (_b) {
                switch (_b.label) {
                    case 0:
                        this._running = true;
                        _b.label = 1;
                    case 1:
                        _b.trys.push([1, , 3, 4]);
                        return [4 /*yield*/, Promise.all(Object.values(tasks))];
                    case 2:
                        _b.sent();
                        return [3 /*break*/, 4];
                    case 3:
                        this._running = false;
                        (_a = this._callback) === null || _a === void 0 ? void 0 : _a.call(this);
                        if (isValid(this._holdingTasks)) {
                            next = this._holdingTasks;
                            void this._runTask(next);
                            this._holdingTasks = null;
                        }
                        return [7 /*endfinally*/];
                    case 4: return [2 /*return*/];
                }
            });
        });
    };
    TaskScheduler.prototype.clear = function () {
        this._holdingTasks = null;
    };
    return TaskScheduler;
}());

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
function getDefaultVisibleRange() {
    return { from: 0, to: 0, realFrom: 0, realTo: 0 };
}

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
/**
 * brush overlay - freehand drawing with click and drag
 * Uses continuous drawing mode for smooth path creation
 */
var brush = {
    name: 'brush',
    totalStep: 2,
    drawingMode: 'continuous',
    needDefaultPointFigure: false,
    needDefaultXAxisFigure: false,
    needDefaultYAxisFigure: false,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates;
        if (coordinates.length < 2) {
            return [];
        }
        return [
            {
                type: 'line',
                attrs: { coordinates: coordinates },
                styles: {
                    smooth: false,
                    lineCap: 'round',
                    lineJoin: 'round'
                }
            }
        ];
    }
};

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
var fibonacciLine = {
    name: 'fibonacciLine',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var _b, _c, _d;
        var chart = _a.chart, coordinates = _a.coordinates, bounding = _a.bounding, overlay = _a.overlay, yAxis = _a.yAxis;
        var points = overlay.points;
        if (coordinates.length > 0) {
            var precision_1 = 0;
            if ((_b = yAxis === null || yAxis === void 0 ? void 0 : yAxis.isInCandle()) !== null && _b !== void 0 ? _b : true) {
                precision_1 = (_d = (_c = chart.getSymbol()) === null || _c === void 0 ? void 0 : _c.pricePrecision) !== null && _d !== void 0 ? _d : SymbolDefaultPrecisionConstants.PRICE;
            }
            else {
                var indicators = chart.getIndicators({ paneId: overlay.paneId });
                indicators.forEach(function (indicator) {
                    precision_1 = Math.max(precision_1, indicator.precision);
                });
            }
            var lines_1 = [];
            var texts_1 = [];
            var startX_1 = 0;
            var endX_1 = bounding.width;
            if (coordinates.length > 1 && isNumber(points[0].value) && isNumber(points[1].value)) {
                var percents = [1, 0.786, 0.618, 0.5, 0.382, 0.236, 0];
                var yDif_1 = coordinates[0].y - coordinates[1].y;
                var valueDif_1 = points[0].value - points[1].value;
                percents.forEach(function (percent) {
                    var _a;
                    var y = coordinates[1].y + yDif_1 * percent;
                    var value = chart.getDecimalFold().format(chart.getThousandsSeparator().format((((_a = points[1].value) !== null && _a !== void 0 ? _a : 0) + valueDif_1 * percent).toFixed(precision_1)));
                    lines_1.push({
                        coordinates: [
                            { x: startX_1, y: y },
                            { x: endX_1, y: y }
                        ]
                    });
                    texts_1.push({
                        x: startX_1,
                        y: y,
                        text: "".concat(value, " (").concat((percent * 100).toFixed(1), "%)"),
                        baseline: 'bottom'
                    });
                });
            }
            return [
                {
                    type: 'line',
                    attrs: lines_1
                },
                {
                    type: 'text',
                    isCheckEvent: false,
                    attrs: texts_1
                }
            ];
        }
        return [];
    }
};

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
var horizontalRayLine = {
    name: 'horizontalRayLine',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        var coordinate = { x: 0, y: coordinates[0].y };
        if (isValid(coordinates[1]) && coordinates[0].x < coordinates[1].x) {
            coordinate.x = bounding.width;
        }
        return [
            {
                type: 'line',
                attrs: { coordinates: [coordinates[0], coordinate] }
            }
        ];
    },
    performEventPressedMove: function (_a) {
        var points = _a.points, performPoint = _a.performPoint;
        points[0].value = performPoint.value;
        points[1].value = performPoint.value;
    },
    performEventMoveForDrawing: function (_a) {
        var currentStep = _a.currentStep, points = _a.points, performPoint = _a.performPoint;
        if (currentStep === 2) {
            points[0].value = performPoint.value;
        }
    }
};

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
var horizontalSegment = {
    name: 'horizontalSegment',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates;
        var lines = [];
        if (coordinates.length === 2) {
            lines.push({ coordinates: coordinates });
        }
        return [
            {
                type: 'line',
                attrs: lines
            }
        ];
    },
    performEventPressedMove: function (_a) {
        var points = _a.points, performPoint = _a.performPoint;
        points[0].value = performPoint.value;
        points[1].value = performPoint.value;
    },
    performEventMoveForDrawing: function (_a) {
        var currentStep = _a.currentStep, points = _a.points, performPoint = _a.performPoint;
        if (currentStep === 2) {
            points[0].value = performPoint.value;
        }
    }
};

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
var horizontalStraightLine = {
    name: 'horizontalStraightLine',
    totalStep: 2,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        return [
            {
                type: 'line',
                attrs: {
                    coordinates: [
                        {
                            x: 0,
                            y: coordinates[0].y
                        },
                        {
                            x: bounding.width,
                            y: coordinates[0].y
                        }
                    ]
                }
            }
        ];
    }
};

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
/**
 * 获取平行线
 * @param coordinates
 * @param bounding
 * @param extendParallelLineCount
 * @returns {Array}
 */
function getParallelLines(coordinates, bounding, extendParallelLineCount) {
    var count = extendParallelLineCount !== null && extendParallelLineCount !== void 0 ? extendParallelLineCount : 0;
    var lines = [];
    if (coordinates.length > 1) {
        if (coordinates[0].x === coordinates[1].x) {
            var startY = 0;
            var endY = bounding.height;
            lines.push({
                coordinates: [
                    { x: coordinates[0].x, y: startY },
                    { x: coordinates[0].x, y: endY }
                ]
            });
            if (coordinates.length > 2) {
                lines.push({
                    coordinates: [
                        { x: coordinates[2].x, y: startY },
                        { x: coordinates[2].x, y: endY }
                    ]
                });
                var distance = coordinates[0].x - coordinates[2].x;
                for (var i = 0; i < count; i++) {
                    var d = distance * (i + 1);
                    lines.push({
                        coordinates: [
                            { x: coordinates[0].x + d, y: startY },
                            { x: coordinates[0].x + d, y: endY }
                        ]
                    });
                }
            }
        }
        else {
            var startX = 0;
            var endX = bounding.width;
            var kb = getLinearSlopeIntercept(coordinates[0], coordinates[1]);
            var k = kb[0];
            var b = kb[1];
            lines.push({
                coordinates: [
                    { x: startX, y: startX * k + b },
                    { x: endX, y: endX * k + b }
                ]
            });
            if (coordinates.length > 2) {
                var b1 = coordinates[2].y - k * coordinates[2].x;
                lines.push({
                    coordinates: [
                        { x: startX, y: startX * k + b1 },
                        { x: endX, y: endX * k + b1 }
                    ]
                });
                var distance = b - b1;
                for (var i = 0; i < count; i++) {
                    var b2 = b + distance * (i + 1);
                    lines.push({
                        coordinates: [
                            { x: startX, y: startX * k + b2 },
                            { x: endX, y: endX * k + b2 }
                        ]
                    });
                }
            }
        }
    }
    return lines;
}
var parallelStraightLine = {
    name: 'parallelStraightLine',
    totalStep: 4,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        return [
            {
                type: 'line',
                attrs: getParallelLines(coordinates, bounding)
            }
        ];
    }
};

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
var priceChannelLine = {
    name: 'priceChannelLine',
    totalStep: 4,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        return [
            {
                type: 'line',
                attrs: getParallelLines(coordinates, bounding, 1)
            }
        ];
    }
};

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
var priceLine = {
    name: 'priceLine',
    totalStep: 2,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var _b, _c, _d;
        var chart = _a.chart, coordinates = _a.coordinates, bounding = _a.bounding, overlay = _a.overlay, yAxis = _a.yAxis;
        var precision = 0;
        if ((_b = yAxis === null || yAxis === void 0 ? void 0 : yAxis.isInCandle()) !== null && _b !== void 0 ? _b : true) {
            precision = (_d = (_c = chart.getSymbol()) === null || _c === void 0 ? void 0 : _c.pricePrecision) !== null && _d !== void 0 ? _d : SymbolDefaultPrecisionConstants.PRICE;
        }
        else {
            var indicators = chart.getIndicators({ paneId: overlay.paneId });
            indicators.forEach(function (indicator) {
                precision = Math.max(precision, indicator.precision);
            });
        }
        var _e = overlay.points[0].value, value = _e === void 0 ? 0 : _e;
        return [
            {
                type: 'line',
                attrs: { coordinates: [coordinates[0], { x: bounding.width, y: coordinates[0].y }] }
            },
            {
                type: 'text',
                ignoreEvent: true,
                attrs: {
                    x: coordinates[0].x,
                    y: coordinates[0].y,
                    text: chart.getDecimalFold().format(chart.getThousandsSeparator().format(value.toFixed(precision))),
                    baseline: 'bottom'
                }
            }
        ];
    }
};

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
function getRayLine(coordinates, bounding) {
    if (coordinates.length > 1) {
        var coordinate = { x: 0, y: 0 };
        if (coordinates[0].x === coordinates[1].x && coordinates[0].y !== coordinates[1].y) {
            if (coordinates[0].y < coordinates[1].y) {
                coordinate = {
                    x: coordinates[0].x,
                    y: bounding.height
                };
            }
            else {
                coordinate = {
                    x: coordinates[0].x,
                    y: 0
                };
            }
        }
        else if (coordinates[0].x > coordinates[1].x) {
            coordinate = {
                x: 0,
                y: getLinearYFromCoordinates(coordinates[0], coordinates[1], { x: 0, y: coordinates[0].y })
            };
        }
        else {
            coordinate = {
                x: bounding.width,
                y: getLinearYFromCoordinates(coordinates[0], coordinates[1], { x: bounding.width, y: coordinates[0].y })
            };
        }
        return { coordinates: [coordinates[0], coordinate] };
    }
    return [];
}
var rayLine = {
    name: 'rayLine',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        return [
            {
                type: 'line',
                attrs: getRayLine(coordinates, bounding)
            }
        ];
    }
};

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
var segment = {
    name: 'segment',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates;
        if (coordinates.length === 2) {
            return [
                {
                    type: 'line',
                    attrs: { coordinates: coordinates }
                }
            ];
        }
        return [];
    }
};

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
var simpleAnnotation = {
    name: 'simpleAnnotation',
    totalStep: 2,
    styles: {
        line: { style: 'dashed' }
    },
    createPointFigures: function (_a) {
        var _b;
        var overlay = _a.overlay, coordinates = _a.coordinates;
        var text = '';
        if (isValid(overlay.extendData)) {
            if (!isFunction(overlay.extendData)) {
                text = ((_b = overlay.extendData) !== null && _b !== void 0 ? _b : '');
            }
            else {
                text = overlay.extendData(overlay);
            }
        }
        var startX = coordinates[0].x;
        var startY = coordinates[0].y - 6;
        var lineEndY = startY - 50;
        var arrowEndY = lineEndY - 5;
        return [
            {
                type: 'line',
                attrs: {
                    coordinates: [
                        { x: startX, y: startY },
                        { x: startX, y: lineEndY }
                    ]
                },
                ignoreEvent: true
            },
            {
                type: 'polygon',
                attrs: {
                    coordinates: [
                        { x: startX, y: lineEndY },
                        { x: startX - 4, y: arrowEndY },
                        { x: startX + 4, y: arrowEndY }
                    ]
                },
                ignoreEvent: true
            },
            {
                type: 'text',
                attrs: { x: startX, y: arrowEndY, text: text, align: 'center', baseline: 'bottom' },
                ignoreEvent: true
            }
        ];
    }
};

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
var simpleTag = {
    name: 'simpleTag',
    totalStep: 2,
    styles: {
        line: { style: 'dashed' }
    },
    createPointFigures: function (_a) {
        var bounding = _a.bounding, coordinates = _a.coordinates;
        return ({
            type: 'line',
            attrs: {
                coordinates: [
                    { x: 0, y: coordinates[0].y },
                    { x: bounding.width, y: coordinates[0].y }
                ]
            },
            ignoreEvent: true
        });
    },
    createYAxisFigures: function (_a) {
        var _b, _c, _d, _e;
        var chart = _a.chart, overlay = _a.overlay, coordinates = _a.coordinates, bounding = _a.bounding, yAxis = _a.yAxis;
        var isFromZero = (_b = yAxis === null || yAxis === void 0 ? void 0 : yAxis.isFromZero()) !== null && _b !== void 0 ? _b : false;
        var textAlign = 'left';
        var x = 0;
        if (isFromZero) {
            textAlign = 'left';
            x = 0;
        }
        else {
            textAlign = 'right';
            x = bounding.width;
        }
        var text = '';
        if (isValid(overlay.extendData)) {
            if (!isFunction(overlay.extendData)) {
                text = ((_c = overlay.extendData) !== null && _c !== void 0 ? _c : '');
            }
            else {
                text = overlay.extendData(overlay);
            }
        }
        if (!isValid(text) && isNumber(overlay.points[0].value)) {
            text = formatPrecision(overlay.points[0].value, (_e = (_d = chart.getSymbol()) === null || _d === void 0 ? void 0 : _d.pricePrecision) !== null && _e !== void 0 ? _e : SymbolDefaultPrecisionConstants.PRICE);
        }
        return { type: 'text', attrs: { x: x, y: coordinates[0].y, text: text, align: textAlign, baseline: 'middle' } };
    }
};

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
var straightLine = {
    name: 'straightLine',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        if (coordinates.length === 2) {
            if (coordinates[0].x === coordinates[1].x) {
                return [
                    {
                        type: 'line',
                        attrs: {
                            coordinates: [
                                {
                                    x: coordinates[0].x,
                                    y: 0
                                },
                                {
                                    x: coordinates[0].x,
                                    y: bounding.height
                                }
                            ]
                        }
                    }
                ];
            }
            return [
                {
                    type: 'line',
                    attrs: {
                        coordinates: [
                            {
                                x: 0,
                                y: getLinearYFromCoordinates(coordinates[0], coordinates[1], { x: 0, y: coordinates[0].y })
                            },
                            {
                                x: bounding.width,
                                y: getLinearYFromCoordinates(coordinates[0], coordinates[1], { x: bounding.width, y: coordinates[0].y })
                            }
                        ]
                    }
                }
            ];
        }
        return [];
    }
};

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
var verticalRayLine = {
    name: 'verticalRayLine',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        if (coordinates.length === 2) {
            var coordinate = { x: coordinates[0].x, y: 0 };
            if (coordinates[0].y < coordinates[1].y) {
                coordinate.y = bounding.height;
            }
            return [
                {
                    type: 'line',
                    attrs: { coordinates: [coordinates[0], coordinate] }
                }
            ];
        }
        return [];
    },
    performEventPressedMove: function (_a) {
        var points = _a.points, performPoint = _a.performPoint;
        points[0].timestamp = performPoint.timestamp;
        points[0].dataIndex = performPoint.dataIndex;
        points[1].timestamp = performPoint.timestamp;
        points[1].dataIndex = performPoint.dataIndex;
    },
    performEventMoveForDrawing: function (_a) {
        var currentStep = _a.currentStep, points = _a.points, performPoint = _a.performPoint;
        if (currentStep === 2) {
            points[0].timestamp = performPoint.timestamp;
            points[0].dataIndex = performPoint.dataIndex;
        }
    }
};

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
var verticalSegment = {
    name: 'verticalSegment',
    totalStep: 3,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates;
        if (coordinates.length === 2) {
            return [
                {
                    type: 'line',
                    attrs: { coordinates: coordinates }
                }
            ];
        }
        return [];
    },
    performEventPressedMove: function (_a) {
        var points = _a.points, performPoint = _a.performPoint;
        points[0].timestamp = performPoint.timestamp;
        points[0].dataIndex = performPoint.dataIndex;
        points[1].timestamp = performPoint.timestamp;
        points[1].dataIndex = performPoint.dataIndex;
    },
    performEventMoveForDrawing: function (_a) {
        var currentStep = _a.currentStep, points = _a.points, performPoint = _a.performPoint;
        if (currentStep === 2) {
            points[0].timestamp = performPoint.timestamp;
            points[0].dataIndex = performPoint.dataIndex;
        }
    }
};

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
var verticalStraightLine = {
    name: 'verticalStraightLine',
    totalStep: 2,
    needDefaultPointFigure: true,
    needDefaultXAxisFigure: true,
    needDefaultYAxisFigure: true,
    createPointFigures: function (_a) {
        var coordinates = _a.coordinates, bounding = _a.bounding;
        return [
            {
                type: 'line',
                attrs: {
                    coordinates: [
                        {
                            x: coordinates[0].x,
                            y: 0
                        },
                        {
                            x: coordinates[0].x,
                            y: bounding.height
                        }
                    ]
                }
            }
        ];
    }
};

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
var overlays = {};
var extensions = [fibonacciLine, horizontalRayLine, horizontalSegment, horizontalStraightLine, parallelStraightLine, priceChannelLine, priceLine, rayLine, segment, straightLine, verticalRayLine, verticalSegment, verticalStraightLine, simpleAnnotation, simpleTag, brush];
extensions.forEach(function (template) {
    overlays[template.name] = OverlayImp.extend(template);
});
function registerOverlay(template) {
    // @ts-ignore
    overlays[template.name] = OverlayImp.extend(template);
}
function getOverlayInnerClass(name) {
    var _a;
    return (_a = overlays[name]) !== null && _a !== void 0 ? _a : null;
}
function getOverlayClass(name) {
    var _a;
    return (_a = overlays[name]) !== null && _a !== void 0 ? _a : null;
}
function getSupportedOverlays() {
    return Object.keys(overlays);
}

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
var dark = {
    grid: {
        horizontal: {
            color: '#292929'
        },
        vertical: {
            color: '#292929'
        }
    },
    candle: {
        priceMark: {
            high: {
                color: '#929AA5'
            },
            low: {
                color: '#929AA5'
            }
        },
        tooltip: {
            rect: {
                color: 'rgba(10, 10, 10, .6)',
                borderColor: 'rgba(10, 10, 10, .6)'
            },
            title: {
                color: '#929AA5'
            },
            legend: {
                color: '#929AA5'
            }
        }
    },
    indicator: {
        tooltip: {
            title: {
                color: '#929AA5'
            },
            legend: {
                color: '#929AA5'
            }
        }
    },
    xAxis: {
        axisLine: {
            color: '#333333'
        },
        tickText: {
            color: '#929AA5'
        },
        tickLine: {
            color: '#333333'
        }
    },
    yAxis: {
        axisLine: {
            color: '#333333'
        },
        tickText: {
            color: '#929AA5'
        },
        tickLine: {
            color: '#333333'
        }
    },
    separator: {
        color: '#333333'
    },
    crosshair: {
        horizontal: {
            line: {
                color: '#929AA5'
            },
            text: {
                borderColor: '#373a40',
                backgroundColor: '#373a40'
            }
        },
        vertical: {
            line: {
                color: '#929AA5'
            },
            text: {
                borderColor: '#373a40',
                backgroundColor: '#373a40'
            }
        }
    }
};

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
var light = {
    grid: {
        horizontal: {
            color: '#EDEDED'
        },
        vertical: {
            color: '#EDEDED'
        }
    },
    candle: {
        priceMark: {
            high: {
                color: '#76808F'
            },
            low: {
                color: '#76808F'
            }
        },
        tooltip: {
            rect: {
                color: '#FEFEFE',
                borderColor: '#F2F3F5'
            },
            title: {
                color: '#76808F'
            },
            legend: {
                color: '#76808F'
            }
        }
    },
    indicator: {
        tooltip: {
            title: {
                color: '#76808F'
            },
            legend: {
                color: '#76808F'
            }
        }
    },
    xAxis: {
        axisLine: {
            color: '#DDDDDD'
        },
        tickText: {
            color: '#76808F'
        },
        tickLine: {
            color: '#DDDDDD'
        }
    },
    yAxis: {
        axisLine: {
            color: '#DDDDDD'
        },
        tickText: {
            color: '#76808F'
        },
        tickLine: {
            color: '#DDDDDD'
        }
    },
    separator: {
        color: '#DDDDDD'
    },
    crosshair: {
        horizontal: {
            line: {
                color: '#76808F'
            },
            text: {
                borderColor: '#686D76',
                backgroundColor: '#686D76'
            }
        },
        vertical: {
            line: {
                color: '#76808F'
            },
            text: {
                borderColor: '#686D76',
                backgroundColor: '#686D76'
            }
        }
    }
};

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
var styles = {
    light: light,
    dark: dark
};
function registerStyles(name, ss) {
    styles[name] = ss;
}
function getStyles(name) {
    var _a;
    return (_a = styles[name]) !== null && _a !== void 0 ? _a : null;
}

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
var DEFAULT_BAR_SPACE = 10;
var DEFAULT_OFFSET_RIGHT_DISTANCE = 80;
var BAR_GAP_RATIO = 0.2;
var SCALE_MULTIPLIER = 10;
var StoreImp = /** @class */ (function () {
    function StoreImp(chart, options) {
        var _this = this;
        /**
         * Styles
         */
        this._styles = getDefaultStyles();
        /**
         * Custom api
         */
        this._formatter = {
            formatDate: function (_a) {
                var dateTimeFormat = _a.dateTimeFormat, timestamp = _a.timestamp, template = _a.template;
                return formatTimestampByTemplate(dateTimeFormat, timestamp, template);
            },
            formatBigNumber: formatBigNumber,
            formatExtendText: function (_) { return ''; }
        };
        /**
         * Inner formatter
         * @description Internal use only
         */
        this._innerFormatter = {
            formatDate: function (timestamp, template, type) { return _this._formatter.formatDate({ dateTimeFormat: _this._dateTimeFormat, timestamp: timestamp, template: template, type: type }); },
            formatBigNumber: function (value) { return _this._formatter.formatBigNumber(value); },
            formatExtendText: function (params) { return _this._formatter.formatExtendText(params); }
        };
        /**
         * Locale
         */
        this._locale = 'en-US';
        /**
         * Thousands separator
         */
        this._thousandsSeparator = {
            sign: ',',
            format: function (value) { return formatThousands(value, _this._thousandsSeparator.sign); }
        };
        /**
         * Decimal fold
         */
        this._decimalFold = {
            threshold: 3,
            format: function (value) { return formatFoldDecimal(value, _this._decimalFold.threshold); }
        };
        /**
         * Hotkey
         */
        this._hotKey = {
            enabled: true,
            exclude: []
        };
        /**
         * Symbol
         */
        this._symbol = null;
        /**
         * Period
         */
        this._period = null;
        /**
         * Data source
         */
        this._dataList = [];
        /**
         * Load more data callback
         */
        this._dataLoader = null;
        /**
         * Is loading data flag
         */
        this._loading = false;
        /**
         * Whether there are forward and backward more flag
         */
        this._dataLoadMore = { forward: false, backward: false };
        /**
         * Scale enabled flag
         */
        this._zoomEnabled = true;
        /**
         * Zoom anchor point flag
         */
        this._zoomAnchor = {
            main: 'cursor',
            xAxis: 'cursor'
        };
        /**
         * Scroll enabled flag
         */
        this._scrollEnabled = true;
        /**
         * Total space of drawing area
         */
        this._totalBarSpace = 0;
        /**
         * Space occupied by a single piece of data
         */
        this._barSpace = DEFAULT_BAR_SPACE;
        /**
         * Distance from the last data to the right of the drawing area
         */
        this._offsetRightDistance = DEFAULT_OFFSET_RIGHT_DISTANCE;
        /**
         * The number of bar to the right of the drawing area from the last data when scrolling starts
         */
        this._startLastBarRightSideDiffBarCount = 0;
        /**
         * Scroll limit role
         */
        this._scrollLimitRole = 'bar_count';
        /**
         * Scroll to the leftmost and rightmost visible bar
         */
        this._minVisibleBarCount = { left: 2, right: 2 };
        /**
         * Scroll to the leftmost and rightmost distance
         */
        this._maxOffsetDistance = { left: 50, right: 50 };
        /**
         * Start and end points of visible area data index
         */
        this._visibleRange = getDefaultVisibleRange();
        /**
         * Visible data array
         */
        this._visibleRangeDataList = [];
        /**
         * Visible highest lowest price data
         */
        this._visibleRangeHighLowPrice = [
            { x: 0, price: Number.MIN_SAFE_INTEGER },
            { x: 0, price: Number.MAX_SAFE_INTEGER }
        ];
        /**
         * Crosshair info
         */
        this._crosshair = {};
        /**
         * Actions
         */
        this._actions = new Map();
        /**
         * Indicator
         */
        this._indicators = new Map();
        /**
         * Overlay
         */
        this._overlays = new Map();
        /**
         * Overlay information in painting
         */
        this._progressOverlayInfo = null;
        this._lastPriceMarkExtendTextUpdateTimers = [];
        /**
         * Overlay information by the mouse pressed
         */
        this._pressedOverlayInfo = {
            paneId: '',
            overlay: null,
            figureType: 'none',
            figureIndex: -1,
            figure: null
        };
        /**
         * Overlay information by hover
         */
        this._hoverOverlayInfo = {
            paneId: '',
            overlay: null,
            figureType: 'none',
            figureIndex: -1,
            figure: null
        };
        /**
         * Overlay information by the mouse click
         */
        this._clickOverlayInfo = {
            paneId: '',
            overlay: null,
            figureType: 'none',
            figureIndex: -1,
            figure: null
        };
        /**
         * Default layout params
         */
        this._layoutOptions = {
            barSpaceLimit: {
                min: 1,
                max: 50
            },
            pane: {
                minHeight: 30,
                dragEnabled: true,
                order: 0,
                height: 100,
                state: 'normal'
            },
            yAxis: {
                reverse: false,
                inside: false,
                position: 'right',
                scrollZoomEnabled: true,
                needWidget: true,
                gap: {
                    top: 0.2,
                    bottom: 0.1
                }
            }
        };
        this._chart = chart;
        var _a = options !== null && options !== void 0 ? options : {}, styles = _a.styles, locale = _a.locale, timezone = _a.timezone, formatter = _a.formatter, thousandsSeparator = _a.thousandsSeparator, decimalFold = _a.decimalFold, zoomAnchor = _a.zoomAnchor, hotkey = _a.hotkey, layout = _a.layout;
        if (isValid(layout)) {
            merge(this._layoutOptions, layout);
        }
        this._calcOptimalBarSpace();
        this._lastBarRightSideDiffBarCount = this._offsetRightDistance / this._barSpace;
        if (isValid(styles)) {
            this.setStyles(styles);
        }
        if (isString(locale)) {
            this.setLocale(locale);
        }
        this.setTimezone(timezone !== null && timezone !== void 0 ? timezone : '');
        if (isValid(formatter)) {
            this.setFormatter(formatter);
        }
        if (isValid(thousandsSeparator)) {
            this.setThousandsSeparator(thousandsSeparator);
        }
        if (isValid(decimalFold)) {
            this.setDecimalFold(decimalFold);
        }
        if (isValid(zoomAnchor)) {
            this.setZoomAnchor(zoomAnchor);
        }
        if (isValid(hotkey)) {
            this.setHotkey(hotkey);
        }
        this._taskScheduler = new TaskScheduler(function () {
            _this._chart.layout({
                measureWidth: true,
                update: true,
                buildYAxisTick: true
            });
        });
    }
    StoreImp.prototype.setStyles = function (value) {
        var _this = this;
        var _a, _b, _c, _d, _e, _f;
        var styles = null;
        if (isString(value)) {
            styles = getStyles(value);
        }
        else {
            styles = value;
        }
        merge(this._styles, styles);
        // `candle.tooltip.custom` should override
        if (isArray((_c = (_b = (_a = styles === null || styles === void 0 ? void 0 : styles.candle) === null || _a === void 0 ? void 0 : _a.tooltip) === null || _b === void 0 ? void 0 : _b.legend) === null || _c === void 0 ? void 0 : _c.template)) {
            this._styles.candle.tooltip.legend.template = styles.candle.tooltip.legend.template;
        }
        if (isValid((_f = (_e = (_d = styles === null || styles === void 0 ? void 0 : styles.candle) === null || _d === void 0 ? void 0 : _d.priceMark) === null || _e === void 0 ? void 0 : _e.last) === null || _f === void 0 ? void 0 : _f.extendTexts)) {
            this._clearLastPriceMarkExtendTextUpdateTimer();
            var intervals_1 = [];
            this._styles.candle.priceMark.last.extendTexts.forEach(function (item) {
                var updateInterval = item.updateInterval;
                if (item.show && updateInterval > 0 && !intervals_1.includes(updateInterval)) {
                    intervals_1.push(updateInterval);
                    var timer = setInterval(function () {
                        _this._chart.updatePane(0 /* UpdateLevel.Main */, PaneIdConstants.CANDLE);
                    }, updateInterval);
                    _this._lastPriceMarkExtendTextUpdateTimers.push(timer);
                }
            });
        }
    };
    StoreImp.prototype.getStyles = function () {
        return this._styles;
    };
    StoreImp.prototype.setFormatter = function (formatter) {
        merge(this._formatter, formatter);
    };
    StoreImp.prototype.getFormatter = function () {
        return this._formatter;
    };
    StoreImp.prototype.getInnerFormatter = function () {
        return this._innerFormatter;
    };
    StoreImp.prototype.setLocale = function (locale) {
        this._locale = locale;
    };
    StoreImp.prototype.getLocale = function () {
        return this._locale;
    };
    StoreImp.prototype.setTimezone = function (timezone) {
        if (!isValid(this._dateTimeFormat) || this.getTimezone() !== timezone) {
            var options = {
                hour12: false,
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            };
            if (timezone.length > 0) {
                options.timeZone = timezone;
            }
            var dateTimeFormat = null;
            try {
                dateTimeFormat = new Intl.DateTimeFormat('en', options);
            }
            catch (_a) {
                logWarn('', '', 'Timezone is error!!!');
            }
            if (dateTimeFormat !== null) {
                this._dateTimeFormat = dateTimeFormat;
            }
        }
    };
    StoreImp.prototype.getTimezone = function () {
        return this._dateTimeFormat.resolvedOptions().timeZone;
    };
    StoreImp.prototype.getDateTimeFormat = function () {
        return this._dateTimeFormat;
    };
    StoreImp.prototype.setThousandsSeparator = function (thousandsSeparator) {
        merge(this._thousandsSeparator, thousandsSeparator);
    };
    StoreImp.prototype.getThousandsSeparator = function () {
        return this._thousandsSeparator;
    };
    StoreImp.prototype.setDecimalFold = function (decimalFold) {
        merge(this._decimalFold, decimalFold);
    };
    StoreImp.prototype.getDecimalFold = function () {
        return this._decimalFold;
    };
    StoreImp.prototype.setHotkey = function (hotkey) {
        merge(this._hotKey, hotkey);
    };
    StoreImp.prototype.getHotkey = function () {
        return this._hotKey;
    };
    StoreImp.prototype.getHotKey = function () {
        return this._hotKey;
    };
    StoreImp.prototype.setSymbol = function (symbol) {
        var _this = this;
        this.resetData(function () {
            // @ts-ignore
            _this._symbol = __assign(__assign({ pricePrecision: SymbolDefaultPrecisionConstants.PRICE, volumePrecision: SymbolDefaultPrecisionConstants.VOLUME }, _this._symbol), symbol);
            _this._synchronizeIndicatorSeriesPrecision();
        });
    };
    StoreImp.prototype.getSymbol = function () {
        return this._symbol;
    };
    StoreImp.prototype.setPeriod = function (period) {
        var _this = this;
        this.resetData(function () {
            _this._period = period;
        });
    };
    StoreImp.prototype.getPeriod = function () {
        return this._period;
    };
    StoreImp.prototype.getDataList = function () {
        return this._dataList;
    };
    StoreImp.prototype.getVisibleRangeDataList = function () {
        return this._visibleRangeDataList;
    };
    StoreImp.prototype.getVisibleRangeHighLowPrice = function () {
        return this._visibleRangeHighLowPrice;
    };
    StoreImp.prototype._addData = function (data, type, more) {
        var _a, _b;
        var success = false;
        var adjustFlag = false;
        if (isArray(data)) {
            var realMore = { backward: false, forward: false };
            if (isBoolean(more)) {
                realMore.backward = more;
                realMore.forward = more;
            }
            else {
                realMore.backward = (_a = more === null || more === void 0 ? void 0 : more.backward) !== null && _a !== void 0 ? _a : false;
                realMore.forward = (_b = more === null || more === void 0 ? void 0 : more.forward) !== null && _b !== void 0 ? _b : false;
            }
            switch (type) {
                case 'init': {
                    this._clearData();
                    this._dataList = data;
                    this._dataLoadMore.backward = realMore.backward;
                    this._dataLoadMore.forward = realMore.forward;
                    this.setOffsetRightDistance(this._offsetRightDistance);
                    adjustFlag = true;
                    break;
                }
                case 'backward': {
                    this._dataList = this._dataList.concat(data);
                    this._dataLoadMore.backward = realMore.backward;
                    this._lastBarRightSideDiffBarCount -= data.length;
                    // scroll start LastBarRightSideDiffBarCount should sub data length
                    this._startLastBarRightSideDiffBarCount -= data.length;
                    adjustFlag = data.length > 0;
                    break;
                }
                case 'forward': {
                    this._dataList = data.concat(this._dataList);
                    this._dataLoadMore.forward = realMore.forward;
                    adjustFlag = data.length > 0;
                    break;
                }
            }
            success = true;
        }
        else {
            var dataCount = this._dataList.length;
            // Determine where individual data should be added
            var timestamp = data.timestamp;
            var lastDataTimestamp = formatValue(this._dataList[dataCount - 1], 'timestamp', 0);
            if (timestamp > lastDataTimestamp) {
                this._dataList.push(data);
                var lastBarRightSideDiffBarCount = this.getLastBarRightSideDiffBarCount();
                if (lastBarRightSideDiffBarCount < 0) {
                    this.setLastBarRightSideDiffBarCount(--lastBarRightSideDiffBarCount);
                }
                success = true;
                adjustFlag = true;
            }
            else if (timestamp === lastDataTimestamp) {
                this._dataList[dataCount - 1] = data;
                success = true;
                adjustFlag = true;
            }
        }
        if (success && adjustFlag) {
            this._adjustVisibleRange();
            this.setCrosshair(this._crosshair, { notInvalidate: true });
            var filterIndicators = this.getIndicatorsByFilter({});
            if (filterIndicators.length > 0) {
                this._calcIndicator(filterIndicators);
            }
            else {
                this._chart.layout({
                    measureWidth: true,
                    update: true,
                    buildYAxisTick: true,
                    cacheYAxisWidth: type !== 'init'
                });
            }
        }
    };
    StoreImp.prototype.setDataLoader = function (dataLoader) {
        var _this = this;
        this.resetData(function () {
            _this._dataLoader = dataLoader;
        });
    };
    StoreImp.prototype._calcOptimalBarSpace = function () {
        var specialBarSpace = 4;
        var ratio = 1 - (BAR_GAP_RATIO * Math.atan(Math.max(specialBarSpace, this._barSpace) - specialBarSpace)) / (Math.PI * 0.5);
        var gapBarSpace = Math.min(Math.floor(this._barSpace * ratio), Math.floor(this._barSpace));
        if (gapBarSpace % 2 === 0 && gapBarSpace + 2 >= this._barSpace) {
            --gapBarSpace;
        }
        this._gapBarSpace = Math.max(1, gapBarSpace);
    };
    StoreImp.prototype._adjustVisibleRange = function () {
        var _a, _b;
        var totalBarCount = this._dataList.length;
        var visibleBarCount = this._totalBarSpace / this._barSpace;
        var leftMinVisibleBarCount = 0;
        var rightMinVisibleBarCount = 0;
        if (this._scrollLimitRole === 'distance') {
            leftMinVisibleBarCount = (this._totalBarSpace - this._maxOffsetDistance.right) / this._barSpace;
            rightMinVisibleBarCount = (this._totalBarSpace - this._maxOffsetDistance.left) / this._barSpace;
        }
        else {
            leftMinVisibleBarCount = this._minVisibleBarCount.left;
            rightMinVisibleBarCount = this._minVisibleBarCount.right;
        }
        leftMinVisibleBarCount = Math.max(0, leftMinVisibleBarCount);
        rightMinVisibleBarCount = Math.max(0, rightMinVisibleBarCount);
        var maxRightOffsetBarCount = visibleBarCount - Math.min(leftMinVisibleBarCount, totalBarCount);
        if (this._lastBarRightSideDiffBarCount > maxRightOffsetBarCount) {
            this._lastBarRightSideDiffBarCount = maxRightOffsetBarCount;
        }
        var minRightOffsetBarCount = -totalBarCount + Math.min(rightMinVisibleBarCount, totalBarCount);
        if (this._lastBarRightSideDiffBarCount < minRightOffsetBarCount) {
            this._lastBarRightSideDiffBarCount = minRightOffsetBarCount;
        }
        var to = Math.round(this._lastBarRightSideDiffBarCount + totalBarCount + 0.5);
        var realTo = to;
        if (to > totalBarCount) {
            to = totalBarCount;
        }
        var from = Math.round(to - visibleBarCount) - 1;
        if (from < 0) {
            from = 0;
        }
        var realFrom = this._lastBarRightSideDiffBarCount > 0 ? Math.round(totalBarCount + this._lastBarRightSideDiffBarCount - visibleBarCount) - 1 : from;
        this._visibleRange = { from: from, to: to, realFrom: realFrom, realTo: realTo };
        this.executeAction('onVisibleRangeChange', this._visibleRange);
        this._visibleRangeDataList = [];
        this._visibleRangeHighLowPrice = [
            { x: 0, price: Number.MIN_SAFE_INTEGER },
            { x: 0, price: Number.MAX_SAFE_INTEGER }
        ];
        for (var i = realFrom; i < realTo; i++) {
            var kLineData = this._dataList[i];
            var x = this.dataIndexToCoordinate(i);
            this._visibleRangeDataList.push({
                dataIndex: i,
                x: x,
                data: {
                    prev: (_a = this._dataList[i - 1]) !== null && _a !== void 0 ? _a : kLineData,
                    current: kLineData,
                    next: (_b = this._dataList[i + 1]) !== null && _b !== void 0 ? _b : kLineData
                }
            });
            if (isValid(kLineData)) {
                if (this._visibleRangeHighLowPrice[0].price < kLineData.high) {
                    this._visibleRangeHighLowPrice[0].price = kLineData.high;
                    this._visibleRangeHighLowPrice[0].x = x;
                }
                if (this._visibleRangeHighLowPrice[1].price > kLineData.low) {
                    this._visibleRangeHighLowPrice[1].price = kLineData.low;
                    this._visibleRangeHighLowPrice[1].x = x;
                }
            }
        }
        // More processing and loading, more loading if there are callback methods and no data is being loaded
        if (from === 0) {
            if (this._dataLoadMore.forward) {
                this._processDataLoad('forward');
            }
        }
        else if (to === totalBarCount) {
            if (this._dataLoadMore.backward) {
                this._processDataLoad('backward');
            }
        }
    };
    StoreImp.prototype._processDataLoad = function (type) {
        var _this = this;
        var _a, _b, _c, _d;
        if (!this._loading && isValid(this._dataLoader) && isValid(this._symbol) && isValid(this._period)) {
            this._loading = true;
            var params = {
                type: type,
                symbol: this._symbol,
                period: this._period,
                timestamp: null,
                callback: function (data, more) {
                    var _a, _b;
                    _this._loading = false;
                    _this._addData(data, type, more);
                    if (type === 'init') {
                        (_b = (_a = _this._dataLoader) === null || _a === void 0 ? void 0 : _a.subscribeBar) === null || _b === void 0 ? void 0 : _b.call(_a, {
                            symbol: _this._symbol,
                            period: _this._period,
                            callback: function (data) {
                                _this._addData(data, 'update');
                            }
                        });
                    }
                }
            };
            switch (type) {
                case 'backward': {
                    params.timestamp = (_b = (_a = this._dataList[this._dataList.length - 1]) === null || _a === void 0 ? void 0 : _a.timestamp) !== null && _b !== void 0 ? _b : null;
                    break;
                }
                case 'forward': {
                    params.timestamp = (_d = (_c = this._dataList[0]) === null || _c === void 0 ? void 0 : _c.timestamp) !== null && _d !== void 0 ? _d : null;
                    break;
                }
            }
            void this._dataLoader.getBars(params);
        }
    };
    StoreImp.prototype._processDataUnsubscribe = function () {
        var _a, _b;
        if (isValid(this._dataLoader) && isValid(this._symbol) && isValid(this._period)) {
            (_b = (_a = this._dataLoader).unsubscribeBar) === null || _b === void 0 ? void 0 : _b.call(_a, {
                symbol: this._symbol,
                period: this._period
            });
        }
    };
    StoreImp.prototype.resetData = function (fn) {
        this._processDataUnsubscribe();
        fn === null || fn === void 0 ? void 0 : fn();
        this._loading = false;
        this._processDataLoad('init');
    };
    StoreImp.prototype.getBarSpace = function () {
        return {
            bar: this._barSpace,
            halfBar: this._barSpace / 2,
            gapBar: this._gapBarSpace,
            halfGapBar: Math.floor(this._gapBarSpace / 2)
        };
    };
    StoreImp.prototype.setBarSpace = function (barSpace, adjustBeforeFunc) {
        if (barSpace < this._layoutOptions.barSpaceLimit.min || barSpace > this._layoutOptions.barSpaceLimit.max || this._barSpace === barSpace) {
            return;
        }
        this._barSpace = barSpace;
        this._calcOptimalBarSpace();
        adjustBeforeFunc === null || adjustBeforeFunc === void 0 ? void 0 : adjustBeforeFunc();
        this._adjustVisibleRange();
        this.setCrosshair(this._crosshair, { notInvalidate: true });
        this._chart.layout({
            measureWidth: true,
            update: true,
            buildYAxisTick: true,
            cacheYAxisWidth: true
        });
    };
    StoreImp.prototype.getLayoutOptions = function () {
        return this._layoutOptions;
    };
    StoreImp.prototype.setTotalBarSpace = function (totalSpace) {
        if (this._totalBarSpace !== totalSpace) {
            this._totalBarSpace = totalSpace;
            this._adjustVisibleRange();
            this.setCrosshair(this._crosshair, { notInvalidate: true });
        }
    };
    StoreImp.prototype.setOffsetRightDistance = function (distance, isUpdate) {
        this._offsetRightDistance = this._scrollLimitRole === 'distance' ? Math.min(this._maxOffsetDistance.right, distance) : distance;
        this._lastBarRightSideDiffBarCount = this._offsetRightDistance / this._barSpace;
        if (isUpdate !== null && isUpdate !== void 0 ? isUpdate : false) {
            this._adjustVisibleRange();
            this.setCrosshair(this._crosshair, { notInvalidate: true });
            this._chart.layout({
                measureWidth: true,
                update: true,
                buildYAxisTick: true,
                cacheYAxisWidth: true
            });
        }
        return this;
    };
    StoreImp.prototype.getInitialOffsetRightDistance = function () {
        return this._offsetRightDistance;
    };
    StoreImp.prototype.getOffsetRightDistance = function () {
        return Math.max(0, this._lastBarRightSideDiffBarCount * this._barSpace);
    };
    StoreImp.prototype.getLastBarRightSideDiffBarCount = function () {
        return this._lastBarRightSideDiffBarCount;
    };
    StoreImp.prototype.setLastBarRightSideDiffBarCount = function (barCount) {
        this._lastBarRightSideDiffBarCount = barCount;
    };
    StoreImp.prototype.setMaxOffsetLeftDistance = function (distance) {
        this._scrollLimitRole = 'distance';
        this._maxOffsetDistance.left = distance;
    };
    StoreImp.prototype.setMaxOffsetRightDistance = function (distance) {
        this._scrollLimitRole = 'distance';
        this._maxOffsetDistance.right = distance;
    };
    StoreImp.prototype.setLeftMinVisibleBarCount = function (barCount) {
        this._scrollLimitRole = 'bar_count';
        this._minVisibleBarCount.left = barCount;
    };
    StoreImp.prototype.setRightMinVisibleBarCount = function (barCount) {
        this._scrollLimitRole = 'bar_count';
        this._minVisibleBarCount.right = barCount;
    };
    StoreImp.prototype.getVisibleRange = function () {
        return this._visibleRange;
    };
    StoreImp.prototype.startScroll = function () {
        this._startLastBarRightSideDiffBarCount = this._lastBarRightSideDiffBarCount;
    };
    StoreImp.prototype.scroll = function (distance) {
        if (!this._scrollEnabled) {
            return;
        }
        var distanceBarCount = distance / this._barSpace;
        var prevLastBarRightSideDistance = this._lastBarRightSideDiffBarCount * this._barSpace;
        this._lastBarRightSideDiffBarCount = this._startLastBarRightSideDiffBarCount - distanceBarCount;
        this._adjustVisibleRange();
        this.setCrosshair(this._crosshair, { notInvalidate: true });
        this._chart.layout({
            measureWidth: true,
            update: true,
            buildYAxisTick: true,
            cacheYAxisWidth: true
        });
        var realDistance = Math.round(prevLastBarRightSideDistance - this._lastBarRightSideDiffBarCount * this._barSpace);
        if (realDistance !== 0) {
            this.executeAction('onScroll', { distance: realDistance });
        }
    };
    StoreImp.prototype.getDataByDataIndex = function (dataIndex) {
        var _a;
        return (_a = this._dataList[dataIndex]) !== null && _a !== void 0 ? _a : null;
    };
    StoreImp.prototype.coordinateToFloatIndex = function (x) {
        var dataCount = this._dataList.length;
        var deltaFromRight = (this._totalBarSpace - x) / this._barSpace;
        var index = dataCount + this._lastBarRightSideDiffBarCount - deltaFromRight;
        return Math.round(index * 1000000) / 1000000;
    };
    StoreImp.prototype.dataIndexToTimestamp = function (dataIndex) {
        var length = this._dataList.length;
        if (length === 0) {
            return null;
        }
        var data = this.getDataByDataIndex(dataIndex);
        if (isValid(data)) {
            return data.timestamp;
        }
        if (isValid(this._period)) {
            var lastIndex = length - 1;
            var referenceTimestamp = null;
            var diff = 0;
            if (dataIndex > lastIndex) {
                referenceTimestamp = this._dataList[lastIndex].timestamp;
                diff = dataIndex - lastIndex;
            }
            else if (dataIndex < 0) {
                referenceTimestamp = this._dataList[0].timestamp;
                diff = dataIndex;
            }
            if (isNumber(referenceTimestamp)) {
                var _a = this._period, type = _a.type, span = _a.span;
                switch (type) {
                    case 'second': {
                        return referenceTimestamp + span * 1000 * diff;
                    }
                    case 'minute': {
                        return referenceTimestamp + span * 60 * 1000 * diff;
                    }
                    case 'hour': {
                        return referenceTimestamp + span * 60 * 60 * 1000 * diff;
                    }
                    case 'day': {
                        return referenceTimestamp + span * 24 * 60 * 60 * 1000 * diff;
                    }
                    case 'week': {
                        return referenceTimestamp + span * 7 * 24 * 60 * 60 * 1000 * diff;
                    }
                    case 'month': {
                        var date = new Date(referenceTimestamp);
                        var referenceDay = date.getDate();
                        date.setDate(1);
                        date.setMonth(date.getMonth() + span * diff);
                        var lastDayOfTargetMonth = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
                        date.setDate(Math.min(referenceDay, lastDayOfTargetMonth));
                        return date.getTime();
                    }
                    case 'year': {
                        var date = new Date(referenceTimestamp);
                        date.setFullYear(date.getFullYear() + span * diff);
                        return date.getTime();
                    }
                }
            }
        }
        return null;
    };
    StoreImp.prototype.timestampToDataIndex = function (timestamp) {
        var length = this._dataList.length;
        if (length === 0) {
            return 0;
        }
        if (isValid(this._period)) {
            var referenceTimestamp = null;
            var baseDataIndex = 0;
            var lastIndex = length - 1;
            var lastTimestamp = this._dataList[lastIndex].timestamp;
            if (timestamp > lastTimestamp) {
                referenceTimestamp = lastTimestamp;
                baseDataIndex = lastIndex;
            }
            var firstTimestamp = this._dataList[0].timestamp;
            if (timestamp < firstTimestamp) {
                referenceTimestamp = firstTimestamp;
                baseDataIndex = 0;
            }
            if (isNumber(referenceTimestamp)) {
                var _a = this._period, type = _a.type, span = _a.span;
                switch (type) {
                    case 'second': {
                        return baseDataIndex + Math.floor((timestamp - referenceTimestamp) / (span * 1000));
                    }
                    case 'minute': {
                        return baseDataIndex + Math.floor((timestamp - referenceTimestamp) / (span * 60 * 1000));
                    }
                    case 'hour': {
                        return baseDataIndex + Math.floor((timestamp - referenceTimestamp) / (span * 60 * 60 * 1000));
                    }
                    case 'day': {
                        return baseDataIndex + Math.floor((timestamp - referenceTimestamp) / (span * 24 * 60 * 60 * 1000));
                    }
                    case 'week': {
                        return baseDataIndex + Math.floor((timestamp - referenceTimestamp) / (span * 7 * 24 * 60 * 60 * 1000));
                    }
                    case 'month': {
                        var referenceDate = new Date(referenceTimestamp);
                        var currentDate = new Date(timestamp);
                        var referenceYear = referenceDate.getFullYear();
                        var currentYear = currentDate.getFullYear();
                        var referenceMonth = referenceDate.getMonth();
                        var currentMonth = currentDate.getMonth();
                        return baseDataIndex + Math.floor(((currentYear - referenceYear) * 12 + (currentMonth - referenceMonth)) / span);
                    }
                    case 'year': {
                        var referenceYear = new Date(referenceTimestamp).getFullYear();
                        var currentYear = new Date(timestamp).getFullYear();
                        return baseDataIndex + Math.floor((currentYear - referenceYear) / span);
                    }
                }
            }
        }
        return binarySearchNearest(this._dataList, 'timestamp', timestamp);
    };
    StoreImp.prototype.dataIndexToCoordinate = function (dataIndex) {
        var dataCount = this._dataList.length;
        var deltaFromRight = dataCount + this._lastBarRightSideDiffBarCount - dataIndex;
        return Math.floor(this._totalBarSpace - (deltaFromRight - 0.5) * this._barSpace + 0.5);
    };
    StoreImp.prototype.coordinateToDataIndex = function (x) {
        return Math.ceil(this.coordinateToFloatIndex(x)) - 1;
    };
    /**
     * Converts a float data index to an interpolated timestamp.
     * This allows sub-bar precision for smooth freehand drawings.
     * Supports extrapolation beyond the data range (drawing in the "future").
     * @param floatIndex - A floating point index (e.g., 42.75)
     * @returns An interpolated timestamp between two bars
     */
    StoreImp.prototype.floatIndexToTimestamp = function (floatIndex) {
        var length = this._dataList.length;
        if (length === 0) {
            return null;
        }
        var lastIndex = length - 1;
        // Handle float index beyond the last bar (extrapolate into the future)
        if (floatIndex > lastIndex && length >= 2) {
            var lastTimestamp = this._dataList[lastIndex].timestamp;
            var secondLastTimestamp = this._dataList[lastIndex - 1].timestamp;
            var barDuration = lastTimestamp - secondLastTimestamp;
            if (barDuration > 0) {
                var barsBeyondLast = floatIndex - lastIndex;
                return Math.round(lastTimestamp + barsBeyondLast * barDuration);
            }
        }
        // Handle float index before the first bar (extrapolate into the past)
        if (floatIndex < 0 && length >= 2) {
            var firstTimestamp = this._dataList[0].timestamp;
            var secondTimestamp = this._dataList[1].timestamp;
            var barDuration = secondTimestamp - firstTimestamp;
            if (barDuration > 0) {
                return Math.round(firstTimestamp + floatIndex * barDuration);
            }
        }
        // Normal case: interpolate between two bars within the data range
        var intIndex = Math.floor(floatIndex);
        var fraction = floatIndex - intIndex;
        // Get timestamp at the integer index
        var timestampAtInt = this.dataIndexToTimestamp(intIndex);
        // If no fractional part, return the integer timestamp
        if (fraction === 0 || !isNumber(timestampAtInt)) {
            return timestampAtInt;
        }
        // Get timestamp at the next index for interpolation
        var timestampAtNext = this.dataIndexToTimestamp(intIndex + 1);
        if (isNumber(timestampAtNext)) {
            // Linear interpolation between the two timestamps
            return Math.round(timestampAtInt + (timestampAtNext - timestampAtInt) * fraction);
        }
        return timestampAtInt;
    };
    /**
     * Converts a precise timestamp to a float data index.
     * This preserves sub-bar precision for smooth freehand drawings across timeframe changes.
     * Supports extrapolation beyond the data range (drawing in the "future").
     * @param timestamp - A precise timestamp (possibly between or beyond bars)
     * @returns A floating point index representing the exact position
     */
    StoreImp.prototype.timestampToFloatIndex = function (timestamp) {
        var length = this._dataList.length;
        if (length === 0) {
            return 0;
        }
        var firstTimestamp = this._dataList[0].timestamp;
        var lastTimestamp = this._dataList[length - 1].timestamp;
        // Handle timestamp beyond the last bar (drawing in the "future")
        if (timestamp > lastTimestamp && length >= 2) {
            // Calculate average bar duration from the last two bars
            var secondLastTimestamp = this._dataList[length - 2].timestamp;
            var barDuration = lastTimestamp - secondLastTimestamp;
            if (barDuration > 0) {
                var timeBeyondLast = timestamp - lastTimestamp;
                var barsBeyond = timeBeyondLast / barDuration;
                return length - 1 + barsBeyond;
            }
        }
        // Handle timestamp before the first bar
        if (timestamp < firstTimestamp && length >= 2) {
            var secondTimestamp = this._dataList[1].timestamp;
            var barDuration = secondTimestamp - firstTimestamp;
            if (barDuration > 0) {
                var timeBeforeFirst = firstTimestamp - timestamp;
                var barsBefore = timeBeforeFirst / barDuration;
                return -barsBefore;
            }
        }
        // Find the floor bar index using binary search
        // We need the bar where barTimestamp <= timestamp < nextBarTimestamp
        var left = 0;
        var right = length - 1;
        var floorIndex = 0;
        while (left <= right) {
            var mid = Math.floor((left + right) / 2);
            var midTimestamp = this._dataList[mid].timestamp;
            if (midTimestamp <= timestamp) {
                floorIndex = mid;
                left = mid + 1;
            }
            else {
                right = mid - 1;
            }
        }
        // Get the floor bar and the next bar for interpolation
        var dataAtFloor = this._dataList[floorIndex];
        var dataAtNext = floorIndex + 1 < length ? this._dataList[floorIndex + 1] : null;
        if (isValid(dataAtFloor) && isValid(dataAtNext)) {
            var timestampAtFloor = dataAtFloor.timestamp;
            var timestampAtNext = dataAtNext.timestamp;
            // Calculate fractional position between the two bars
            if (timestamp >= timestampAtFloor && timestampAtNext > timestampAtFloor) {
                var fraction = (timestamp - timestampAtFloor) / (timestampAtNext - timestampAtFloor);
                return floorIndex + Math.min(fraction, 1); // Clamp to max 1
            }
        }
        return floorIndex;
    };
    StoreImp.prototype.zoom = function (scale, coordinate, position) {
        var _this = this;
        var _a;
        if (!this._zoomEnabled) {
            return;
        }
        var zoomCoordinate = coordinate !== null && coordinate !== void 0 ? coordinate : { x: (_a = this._crosshair.x) !== null && _a !== void 0 ? _a : this._totalBarSpace / 2 };
        if (position === 'xAxis') {
            if (this._zoomAnchor.xAxis === 'last_bar') {
                zoomCoordinate.x = this.dataIndexToCoordinate(this._dataList.length - 1);
            }
        }
        else {
            if (this._zoomAnchor.main === 'last_bar') {
                zoomCoordinate.x = this.dataIndexToCoordinate(this._dataList.length - 1);
            }
        }
        var x = zoomCoordinate.x;
        var floatIndex = this.coordinateToFloatIndex(x);
        var prevBarSpace = this._barSpace;
        var barSpace = this._barSpace + scale * (this._barSpace / SCALE_MULTIPLIER);
        this.setBarSpace(barSpace, function () {
            _this._lastBarRightSideDiffBarCount += floatIndex - _this.coordinateToFloatIndex(x);
        });
        var realScale = this._barSpace / prevBarSpace;
        if (realScale !== 1) {
            this.executeAction('onZoom', { scale: realScale });
        }
    };
    StoreImp.prototype.setZoomEnabled = function (enabled) {
        this._zoomEnabled = enabled;
    };
    StoreImp.prototype.isZoomEnabled = function () {
        return this._zoomEnabled;
    };
    StoreImp.prototype.setZoomAnchor = function (anchor) {
        if (isString(anchor)) {
            this._zoomAnchor.main = anchor;
            this._zoomAnchor.xAxis = anchor;
        }
        else {
            if (isString(anchor.main)) {
                this._zoomAnchor.main = anchor.main;
            }
            if (isString(anchor.xAxis)) {
                this._zoomAnchor.xAxis = anchor.xAxis;
            }
        }
    };
    StoreImp.prototype.getZoomAnchor = function () {
        return __assign({}, this._zoomAnchor);
    };
    StoreImp.prototype.setScrollEnabled = function (enabled) {
        this._scrollEnabled = enabled;
    };
    StoreImp.prototype.isScrollEnabled = function () {
        return this._scrollEnabled;
    };
    StoreImp.prototype.setCrosshair = function (crosshair, options) {
        var _a;
        var _b = options !== null && options !== void 0 ? options : {}, notInvalidate = _b.notInvalidate, notExecuteAction = _b.notExecuteAction, forceInvalidate = _b.forceInvalidate;
        var cr = crosshair !== null && crosshair !== void 0 ? crosshair : {};
        var realDataIndex = 0;
        var dataIndex = 0;
        if (isNumber(cr.x)) {
            realDataIndex = this.coordinateToDataIndex(cr.x);
            if (realDataIndex < 0) {
                dataIndex = 0;
            }
            else if (realDataIndex > this._dataList.length - 1) {
                dataIndex = this._dataList.length - 1;
            }
            else {
                dataIndex = realDataIndex;
            }
        }
        else {
            realDataIndex = this._dataList.length - 1;
            dataIndex = realDataIndex;
        }
        var kLineData = this._dataList[dataIndex];
        var realX = this.dataIndexToCoordinate(realDataIndex);
        var prevCrosshair = { x: this._crosshair.x, y: this._crosshair.y, paneId: this._crosshair.paneId };
        this._crosshair = __assign(__assign({}, cr), { realX: realX, kLineData: kLineData, realDataIndex: realDataIndex, dataIndex: dataIndex, timestamp: (_a = this.dataIndexToTimestamp(realDataIndex)) !== null && _a !== void 0 ? _a : undefined });
        if (prevCrosshair.x !== cr.x || prevCrosshair.y !== cr.y || prevCrosshair.paneId !== cr.paneId || (forceInvalidate !== null && forceInvalidate !== void 0 ? forceInvalidate : false)) {
            if (isValid(kLineData) && !(notExecuteAction !== null && notExecuteAction !== void 0 ? notExecuteAction : false) && this.hasAction('onCrosshairChange') && isString(this._crosshair.paneId)) {
                this.executeAction('onCrosshairChange', crosshair);
            }
            if (!(notInvalidate !== null && notInvalidate !== void 0 ? notInvalidate : false)) {
                this._chart.updatePane(1 /* UpdateLevel.Overlay */);
            }
        }
    };
    StoreImp.prototype.getCrosshair = function () {
        return this._crosshair;
    };
    StoreImp.prototype.executeAction = function (type, data) {
        var _a;
        (_a = this._actions.get(type)) === null || _a === void 0 ? void 0 : _a.execute(data);
    };
    StoreImp.prototype.subscribeAction = function (type, callback) {
        var _a;
        if (!this._actions.has(type)) {
            this._actions.set(type, new Action());
        }
        (_a = this._actions.get(type)) === null || _a === void 0 ? void 0 : _a.subscribe(callback);
    };
    StoreImp.prototype.unsubscribeAction = function (type, callback) {
        var action = this._actions.get(type);
        if (isValid(action)) {
            action.unsubscribe(callback);
            if (action.isEmpty()) {
                this._actions.delete(type);
            }
        }
    };
    StoreImp.prototype.hasAction = function (type) {
        var action = this._actions.get(type);
        return isValid(action) && !action.isEmpty();
    };
    StoreImp.prototype._sortIndicators = function (paneId) {
        var _a;
        if (isString(paneId)) {
            (_a = this._indicators.get(paneId)) === null || _a === void 0 ? void 0 : _a.sort(function (i1, i2) { return i1.zLevel - i2.zLevel; });
        }
        else {
            this._indicators.forEach(function (paneIndicators) {
                paneIndicators.sort(function (i1, i2) { return i1.zLevel - i2.zLevel; });
            });
        }
    };
    StoreImp.prototype._calcIndicator = function (data) {
        var _this = this;
        var indicators = [];
        indicators = indicators.concat(data);
        if (indicators.length > 0) {
            var tasks_1 = {};
            indicators.forEach(function (indicator) {
                tasks_1[indicator.id] = indicator.calcImp(_this._dataList);
            });
            this._taskScheduler.add(tasks_1);
        }
    };
    StoreImp.prototype.addIndicator = function (create, isStack) {
        var name = create.name;
        var filterIndicators = this.getIndicatorsByFilter(create);
        if (filterIndicators.length > 0) {
            return false;
        }
        var paneId = create.paneId;
        var paneIndicators = this.getIndicatorsByPaneId(paneId);
        var IndicatorClazz = getIndicatorClass(name);
        var indicator = new IndicatorClazz();
        this._synchronizeIndicatorSeriesPrecision(indicator);
        indicator.override(create);
        if (!isStack) {
            this.removeIndicator({ paneId: paneId });
            paneIndicators = [];
        }
        paneIndicators.push(indicator);
        this._indicators.set(paneId, paneIndicators);
        this._sortIndicators(paneId);
        this._calcIndicator(indicator);
        return true;
    };
    StoreImp.prototype.getIndicatorsByPaneId = function (paneId) {
        var _a;
        return (_a = this._indicators.get(paneId)) !== null && _a !== void 0 ? _a : [];
    };
    StoreImp.prototype.getIndicatorsByFilter = function (filter) {
        var paneId = filter.paneId, name = filter.name, id = filter.id;
        var match = function (indicator) {
            if (isValid(id)) {
                return indicator.id === id;
            }
            return !isValid(name) || indicator.name === name;
        };
        var indicators = [];
        if (isValid(paneId)) {
            indicators = indicators.concat(this.getIndicatorsByPaneId(paneId).filter(match));
        }
        else {
            this._indicators.forEach(function (paneIndicators) {
                indicators = indicators.concat(paneIndicators.filter(match));
            });
        }
        return indicators;
    };
    StoreImp.prototype.removeIndicator = function (filter) {
        var _this = this;
        var removed = false;
        var filterIndicators = this.getIndicatorsByFilter(filter !== null && filter !== void 0 ? filter : {});
        filterIndicators.forEach(function (indicator) {
            var paneIndicators = _this.getIndicatorsByPaneId(indicator.paneId);
            var index = paneIndicators.findIndex(function (ins) { return ins.id === indicator.id; });
            if (index > -1) {
                paneIndicators.splice(index, 1);
                removed = true;
            }
            if (paneIndicators.length === 0) {
                _this._indicators.delete(indicator.paneId);
            }
        });
        return removed;
    };
    StoreImp.prototype.hasIndicators = function (paneId) {
        return this._indicators.has(paneId);
    };
    StoreImp.prototype._synchronizeIndicatorSeriesPrecision = function (indicator) {
        if (isValid(this._symbol)) {
            var _a = this._symbol, _b = _a.pricePrecision, pricePrecision_1 = _b === void 0 ? SymbolDefaultPrecisionConstants.PRICE : _b, _c = _a.volumePrecision, volumePrecision_1 = _c === void 0 ? SymbolDefaultPrecisionConstants.VOLUME : _c;
            var synchronize_1 = function (indicator) {
                switch (indicator.series) {
                    case 'price': {
                        indicator.setSeriesPrecision(pricePrecision_1);
                        break;
                    }
                    case 'volume': {
                        indicator.setSeriesPrecision(volumePrecision_1);
                        break;
                    }
                }
            };
            if (isValid(indicator)) {
                synchronize_1(indicator);
            }
            else {
                this._indicators.forEach(function (paneIndicators) {
                    paneIndicators.forEach(function (indicator) {
                        synchronize_1(indicator);
                    });
                });
            }
        }
    };
    StoreImp.prototype.overrideIndicator = function (override) {
        var _this = this;
        var updateFlag = false;
        var sortFlag = false;
        var filterIndicators = this.getIndicatorsByFilter(override);
        filterIndicators.forEach(function (indicator) {
            var prevPaneId = indicator.paneId;
            indicator.override(override);
            var currentPaneId = indicator.paneId;
            if (prevPaneId !== currentPaneId) {
                var prevPaneIndicators = _this.getIndicatorsByPaneId(prevPaneId);
                var index = prevPaneIndicators.findIndex(function (ins) { return ins.id === indicator.id; });
                if (index > -1) {
                    prevPaneIndicators.splice(index, 1);
                }
                if (prevPaneIndicators.length === 0) {
                    _this._indicators.delete(prevPaneId);
                }
                var currentPaneIndicators = _this.getIndicatorsByPaneId(currentPaneId);
                if (!currentPaneIndicators.some(function (ins) { return ins.id === indicator.id; })) {
                    currentPaneIndicators.push(indicator);
                    _this._indicators.set(currentPaneId, currentPaneIndicators);
                }
                sortFlag = true;
            }
            var _a = indicator.shouldUpdateImp(), calc = _a.calc, draw = _a.draw, sort = _a.sort;
            if (sort) {
                sortFlag = true;
            }
            if (calc) {
                _this._calcIndicator(indicator);
            }
            else {
                if (draw) {
                    updateFlag = true;
                }
            }
        });
        if (sortFlag) {
            this._sortIndicators();
        }
        return updateFlag || sortFlag;
    };
    StoreImp.prototype.getOverlaysByFilter = function (filter) {
        var _a;
        var id = filter.id, groupId = filter.groupId, paneId = filter.paneId, name = filter.name;
        var match = function (overlay) {
            if (isValid(id)) {
                return overlay.id === id;
            }
            else {
                if (isValid(groupId)) {
                    return overlay.groupId === groupId && (!isValid(name) || overlay.name === name);
                }
            }
            return !isValid(name) || overlay.name === name;
        };
        var overlays = [];
        if (isValid(paneId)) {
            overlays = overlays.concat(this.getOverlaysByPaneId(paneId).filter(match));
        }
        else {
            this._overlays.forEach(function (paneOverlays) {
                overlays = overlays.concat(paneOverlays.filter(match));
            });
        }
        var progressOverlay = (_a = this._progressOverlayInfo) === null || _a === void 0 ? void 0 : _a.overlay;
        if (isValid(progressOverlay) && match(progressOverlay)) {
            overlays.push(progressOverlay);
        }
        return overlays;
    };
    StoreImp.prototype.getOverlaysByPaneId = function (paneId) {
        var _a;
        if (!isString(paneId)) {
            var overlays_1 = [];
            this._overlays.forEach(function (paneOverlays) {
                overlays_1 = overlays_1.concat(paneOverlays);
            });
            return overlays_1;
        }
        return (_a = this._overlays.get(paneId)) !== null && _a !== void 0 ? _a : [];
    };
    StoreImp.prototype._sortOverlays = function (paneId) {
        var _a;
        if (isString(paneId)) {
            (_a = this._overlays.get(paneId)) === null || _a === void 0 ? void 0 : _a.sort(function (o1, o2) { return o1.zLevel - o2.zLevel; });
        }
        else {
            this._overlays.forEach(function (paneOverlays) {
                paneOverlays.sort(function (o1, o2) { return o1.zLevel - o2.zLevel; });
            });
        }
    };
    StoreImp.prototype.addOverlays = function (os, appointPaneFlags) {
        var _this = this;
        var updatePaneIds = [];
        var ids = os.map(function (create, index) {
            var e_1, _a;
            var _b, _c, _d, _e, _f, _g;
            if (isValid(create.id)) {
                var findOverlay = null;
                try {
                    for (var _h = __values(_this._overlays), _j = _h.next(); !_j.done; _j = _h.next()) {
                        var item = _j.value;
                        var overlays = item[1];
                        var overlay = overlays.find(function (o) { return o.id === create.id; });
                        if (isValid(overlay)) {
                            findOverlay = overlay;
                            break;
                        }
                    }
                }
                catch (e_1_1) { e_1 = { error: e_1_1 }; }
                finally {
                    try {
                        if (_j && !_j.done && (_a = _h.return)) _a.call(_h);
                    }
                    finally { if (e_1) throw e_1.error; }
                }
                if (isValid(findOverlay)) {
                    return create.id;
                }
            }
            var OverlayClazz = getOverlayInnerClass(create.name);
            if (isValid(OverlayClazz)) {
                var id = (_b = create.id) !== null && _b !== void 0 ? _b : createId(OVERLAY_ID_PREFIX);
                var overlay = new OverlayClazz();
                var paneId = (_c = create.paneId) !== null && _c !== void 0 ? _c : PaneIdConstants.CANDLE;
                create.id = id;
                (_d = create.groupId) !== null && _d !== void 0 ? _d : (create.groupId = id);
                var zLevel = _this.getOverlaysByPaneId(paneId).length;
                (_e = create.zLevel) !== null && _e !== void 0 ? _e : (create.zLevel = zLevel);
                overlay.override(create);
                if (!updatePaneIds.includes(paneId)) {
                    updatePaneIds.push(paneId);
                }
                if (overlay.isDrawing()) {
                    _this._progressOverlayInfo = { paneId: paneId, overlay: overlay, appointPaneFlag: appointPaneFlags[index] };
                }
                else {
                    if (!_this._overlays.has(paneId)) {
                        _this._overlays.set(paneId, []);
                    }
                    (_f = _this._overlays.get(paneId)) === null || _f === void 0 ? void 0 : _f.push(overlay);
                }
                if (overlay.isStart()) {
                    (_g = overlay.onDrawStart) === null || _g === void 0 ? void 0 : _g.call(overlay, { overlay: overlay, chart: _this._chart });
                }
                return id;
            }
            return null;
        });
        if (updatePaneIds.length > 0) {
            this._sortOverlays();
            updatePaneIds.forEach(function (paneId) {
                _this._chart.updatePane(1 /* UpdateLevel.Overlay */, paneId);
            });
            this._chart.updatePane(1 /* UpdateLevel.Overlay */, PaneIdConstants.X_AXIS);
        }
        return ids;
    };
    StoreImp.prototype.getProgressOverlayInfo = function () {
        return this._progressOverlayInfo;
    };
    StoreImp.prototype.progressOverlayComplete = function () {
        var _a;
        if (this._progressOverlayInfo !== null) {
            var _b = this._progressOverlayInfo, overlay = _b.overlay, paneId = _b.paneId;
            if (!overlay.isDrawing()) {
                if (!this._overlays.has(paneId)) {
                    this._overlays.set(paneId, []);
                }
                (_a = this._overlays.get(paneId)) === null || _a === void 0 ? void 0 : _a.push(overlay);
                this._sortOverlays(paneId);
                this._progressOverlayInfo = null;
            }
        }
    };
    StoreImp.prototype.updateProgressOverlayInfo = function (paneId, appointPaneFlag) {
        if (this._progressOverlayInfo !== null) {
            if (isBoolean(appointPaneFlag) && appointPaneFlag) {
                this._progressOverlayInfo.appointPaneFlag = appointPaneFlag;
            }
            this._progressOverlayInfo.paneId = paneId;
            this._progressOverlayInfo.overlay.override({ paneId: paneId });
        }
    };
    StoreImp.prototype.overrideOverlay = function (override) {
        var _this = this;
        var sortFlag = false;
        var updatePaneIds = [];
        var filterOverlays = this.getOverlaysByFilter(override);
        filterOverlays.forEach(function (overlay) {
            overlay.override(override);
            var _a = overlay.shouldUpdate(), sort = _a.sort, draw = _a.draw;
            if (sort) {
                sortFlag = true;
            }
            if (sort || draw) {
                if (!updatePaneIds.includes(overlay.paneId)) {
                    updatePaneIds.push(overlay.paneId);
                }
            }
        });
        if (sortFlag) {
            this._sortOverlays();
        }
        if (updatePaneIds.length > 0) {
            updatePaneIds.forEach(function (paneId) {
                _this._chart.updatePane(1 /* UpdateLevel.Overlay */, paneId);
            });
            this._chart.updatePane(1 /* UpdateLevel.Overlay */, PaneIdConstants.X_AXIS);
            return true;
        }
        return false;
    };
    StoreImp.prototype.removeOverlay = function (filter) {
        var _this = this;
        var updatePaneIds = [];
        var filterOverlays = this.getOverlaysByFilter(filter !== null && filter !== void 0 ? filter : {});
        filterOverlays.forEach(function (overlay) {
            var _a;
            var paneId = overlay.paneId;
            var paneOverlays = _this.getOverlaysByPaneId(overlay.paneId);
            (_a = overlay.onRemoved) === null || _a === void 0 ? void 0 : _a.call(overlay, { overlay: overlay, chart: _this._chart });
            if (!updatePaneIds.includes(paneId)) {
                updatePaneIds.push(paneId);
            }
            if (overlay.isDrawing()) {
                _this._progressOverlayInfo = null;
            }
            else {
                var index = paneOverlays.findIndex(function (o) { return o.id === overlay.id; });
                if (index > -1) {
                    paneOverlays.splice(index, 1);
                }
            }
            if (paneOverlays.length === 0) {
                _this._overlays.delete(paneId);
            }
        });
        if (updatePaneIds.length > 0) {
            updatePaneIds.forEach(function (paneId) {
                _this._chart.updatePane(1 /* UpdateLevel.Overlay */, paneId);
            });
            this._chart.updatePane(1 /* UpdateLevel.Overlay */, PaneIdConstants.X_AXIS);
            return true;
        }
        return false;
    };
    StoreImp.prototype.setPressedOverlayInfo = function (info) {
        this._pressedOverlayInfo = info;
    };
    StoreImp.prototype.getPressedOverlayInfo = function () {
        return this._pressedOverlayInfo;
    };
    StoreImp.prototype.setHoverOverlayInfo = function (info, processOnMouseEnterEvent, processOnMouseLeaveEvent) {
        var _a = this._hoverOverlayInfo, overlay = _a.overlay, figureType = _a.figureType, figureIndex = _a.figureIndex, figure = _a.figure;
        var infoOverlay = info.overlay;
        if ((overlay === null || overlay === void 0 ? void 0 : overlay.id) !== (infoOverlay === null || infoOverlay === void 0 ? void 0 : infoOverlay.id) || figureType !== info.figureType || figureIndex !== info.figureIndex) {
            this._hoverOverlayInfo = info;
            if ((overlay === null || overlay === void 0 ? void 0 : overlay.id) !== (infoOverlay === null || infoOverlay === void 0 ? void 0 : infoOverlay.id)) {
                var ignoreUpdateFlag = false;
                var sortFlag = false;
                if (overlay !== null) {
                    overlay.override({ zLevel: overlay.getPrevZLevel() });
                    sortFlag = true;
                    if (processOnMouseLeaveEvent(overlay, figure)) {
                        ignoreUpdateFlag = true;
                    }
                }
                if (infoOverlay !== null) {
                    infoOverlay.setPrevZLevel(infoOverlay.zLevel);
                    infoOverlay.override({ zLevel: Number.MAX_SAFE_INTEGER });
                    sortFlag = true;
                    if (processOnMouseEnterEvent(infoOverlay, info.figure)) {
                        ignoreUpdateFlag = true;
                    }
                }
                if (sortFlag) {
                    this._sortOverlays();
                }
                if (!ignoreUpdateFlag) {
                    this._chart.updatePane(1 /* UpdateLevel.Overlay */);
                }
            }
        }
    };
    StoreImp.prototype.getHoverOverlayInfo = function () {
        return this._hoverOverlayInfo;
    };
    StoreImp.prototype.setClickOverlayInfo = function (info, processOnSelectedEvent, processOnDeselectedEvent) {
        var _a = this._clickOverlayInfo, paneId = _a.paneId, overlay = _a.overlay, figureType = _a.figureType, figure = _a.figure, figureIndex = _a.figureIndex;
        var infoOverlay = info.overlay;
        if ((overlay === null || overlay === void 0 ? void 0 : overlay.id) !== (infoOverlay === null || infoOverlay === void 0 ? void 0 : infoOverlay.id) || figureType !== info.figureType || figureIndex !== info.figureIndex) {
            this._clickOverlayInfo = info;
            if ((overlay === null || overlay === void 0 ? void 0 : overlay.id) !== (infoOverlay === null || infoOverlay === void 0 ? void 0 : infoOverlay.id)) {
                if (isValid(overlay)) {
                    processOnDeselectedEvent(overlay, figure);
                }
                if (isValid(infoOverlay)) {
                    processOnSelectedEvent(infoOverlay, info.figure);
                }
                this._chart.updatePane(1 /* UpdateLevel.Overlay */, info.paneId);
                if (paneId !== info.paneId) {
                    this._chart.updatePane(1 /* UpdateLevel.Overlay */, paneId);
                }
                this._chart.updatePane(1 /* UpdateLevel.Overlay */, PaneIdConstants.X_AXIS);
            }
        }
    };
    StoreImp.prototype.getClickOverlayInfo = function () {
        return this._clickOverlayInfo;
    };
    StoreImp.prototype.isOverlayEmpty = function () {
        return this._overlays.size === 0 && this._progressOverlayInfo === null;
    };
    StoreImp.prototype.isOverlayDrawing = function () {
        var _a, _b;
        return (_b = (_a = this._progressOverlayInfo) === null || _a === void 0 ? void 0 : _a.overlay.isDrawing()) !== null && _b !== void 0 ? _b : false;
    };
    StoreImp.prototype._clearLastPriceMarkExtendTextUpdateTimer = function () {
        this._lastPriceMarkExtendTextUpdateTimers.forEach(function (timer) {
            clearInterval(timer);
        });
        this._lastPriceMarkExtendTextUpdateTimers = [];
    };
    StoreImp.prototype._clearData = function () {
        this._dataLoadMore.backward = false;
        this._dataLoadMore.forward = false;
        this._loading = false;
        this._dataList = [];
        this._visibleRangeDataList = [];
        this._visibleRangeHighLowPrice = [
            { x: 0, price: Number.MIN_SAFE_INTEGER },
            { x: 0, price: Number.MAX_SAFE_INTEGER }
        ];
        this._visibleRange = getDefaultVisibleRange();
        this._crosshair = {};
    };
    StoreImp.prototype.getChart = function () {
        return this._chart;
    };
    StoreImp.prototype.destroy = function () {
        this._clearData();
        this._clearLastPriceMarkExtendTextUpdateTimer();
        this._taskScheduler.clear();
        this._overlays.clear();
        this._indicators.clear();
        this._actions.clear();
    };
    return StoreImp;
}());

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
var ChartImp = /** @class */ (function () {
    function ChartImp(container, options) {
        var _this = this;
        this._chartBounding = createDefaultBounding();
        this._drawPanes = [];
        this._separatorPanes = new Map();
        this._layoutUpdateOptions = {
            sort: true,
            measureHeight: true,
            measureWidth: true,
            secondMeasureWidth: false,
            update: true,
            buildYAxisTick: false,
            cacheYAxisWidth: false,
            forceBuildYAxisTick: false
        };
        this._layoutPending = false;
        this._resizeObserver = null;
        this._resizeRequestAnimationId = DEFAULT_REQUEST_ID;
        this._scheduleResize = function () {
            if (_this._resizeRequestAnimationId === DEFAULT_REQUEST_ID) {
                _this._resizeRequestAnimationId = requestAnimationFrame(function () {
                    _this._resizeRequestAnimationId = DEFAULT_REQUEST_ID;
                    if (_this._chartBounding.width !== Math.floor(_this._chartContainer.clientWidth) || _this._chartBounding.height !== Math.floor(_this._chartContainer.clientHeight)) {
                        _this.resize();
                    }
                });
            }
        };
        this._cacheYAxisWidth = { left: 0, right: 0 };
        this._initContainer(container);
        this._chartEvent = new Event(this._chartContainer, this);
        this._chartStore = new StoreImp(this, options);
        var layoutOptions = this._chartStore.getLayoutOptions();
        var paneOptions = layoutOptions.pane;
        this._candlePane = this._createPane(CandlePane, __assign(__assign({}, paneOptions), { id: PaneIdConstants.CANDLE }));
        this._candlePane.createOrOverrideYAxis(__assign(__assign({}, layoutOptions.yAxis), { id: createId(Y_AXIS_ID_PREFIX) }));
        this._xAxisPane = this._createPane(XAxisPane, __assign(__assign({}, paneOptions), { id: PaneIdConstants.X_AXIS, order: Number.MAX_SAFE_INTEGER }));
        this._layout();
        this._initResizeListener();
    }
    ChartImp.prototype._initContainer = function (container) {
        this._container = container;
        this._chartContainer = createDom('div', {
            position: 'relative',
            width: '100%',
            height: '100%',
            outline: 'none',
            borderStyle: 'none',
            cursor: 'crosshair',
            boxSizing: 'border-box',
            userSelect: 'none',
            webkitUserSelect: 'none',
            overflow: 'hidden',
            touchAction: 'none',
            overscrollBehavior: 'none',
            // @ts-ignore
            msUserSelect: 'none',
            MozUserSelect: 'none',
            webkitTapHighlightColor: 'transparent'
        });
        this._chartContainer.tabIndex = 1;
        container.appendChild(this._chartContainer);
        this._cacheChartBounding();
    };
    ChartImp.prototype._cacheChartBounding = function () {
        this._chartBounding.width = Math.floor(this._chartContainer.clientWidth);
        this._chartBounding.height = Math.floor(this._chartContainer.clientHeight);
    };
    ChartImp.prototype._initResizeListener = function () {
        var _this = this;
        if (isValid(ResizeObserver)) {
            this._resizeObserver = new ResizeObserver(function () {
                _this._scheduleResize();
            });
            this._resizeObserver.observe(this._chartContainer);
        }
        else {
            window.addEventListener('resize', this._scheduleResize);
        }
    };
    ChartImp.prototype._createPane = function (DrawPaneClass, options) {
        var pane = new DrawPaneClass(this, options);
        this._drawPanes.push(pane);
        return pane;
    };
    ChartImp.prototype.getDrawPaneById = function (paneId) {
        if (paneId === PaneIdConstants.CANDLE) {
            return this._candlePane;
        }
        if (paneId === PaneIdConstants.X_AXIS) {
            return this._xAxisPane;
        }
        var pane = this._drawPanes.find(function (p) { return p.getId() === paneId; });
        return pane !== null && pane !== void 0 ? pane : null;
    };
    ChartImp.prototype.getContainer = function () {
        return this._container;
    };
    ChartImp.prototype.getChartStore = function () {
        return this._chartStore;
    };
    ChartImp.prototype.getXAxisPane = function () {
        return this._xAxisPane;
    };
    ChartImp.prototype.getDrawPanes = function () {
        return this._drawPanes;
    };
    ChartImp.prototype.getSeparatorPanes = function () {
        return this._separatorPanes;
    };
    ChartImp.prototype.layout = function (options) {
        var _this = this;
        var _a, _b, _c, _d, _e, _f, _g, _h;
        if ((_a = options.sort) !== null && _a !== void 0 ? _a : false) {
            this._layoutUpdateOptions.sort = options.sort;
        }
        if ((_b = options.measureHeight) !== null && _b !== void 0 ? _b : false) {
            this._layoutUpdateOptions.measureHeight = options.measureHeight;
        }
        if ((_c = options.measureWidth) !== null && _c !== void 0 ? _c : false) {
            this._layoutUpdateOptions.measureWidth = options.measureWidth;
        }
        if ((_d = options.secondMeasureWidth) !== null && _d !== void 0 ? _d : false) {
            this._layoutUpdateOptions.secondMeasureWidth = options.secondMeasureWidth;
        }
        if ((_e = options.update) !== null && _e !== void 0 ? _e : false) {
            this._layoutUpdateOptions.update = options.update;
        }
        if ((_f = options.buildYAxisTick) !== null && _f !== void 0 ? _f : false) {
            this._layoutUpdateOptions.buildYAxisTick = options.buildYAxisTick;
        }
        if ((_g = options.cacheYAxisWidth) !== null && _g !== void 0 ? _g : false) {
            this._layoutUpdateOptions.cacheYAxisWidth = options.cacheYAxisWidth;
        }
        if ((_h = options.forceBuildYAxisTick) !== null && _h !== void 0 ? _h : false) {
            this._layoutUpdateOptions.forceBuildYAxisTick = options.forceBuildYAxisTick;
        }
        if (!this._layoutPending) {
            this._layoutPending = true;
            Promise.resolve()
                .then(function (_) {
                _this._layout();
                _this._layoutPending = false;
            })
                .catch(function (_) {
                // todo
            });
        }
    };
    ChartImp.prototype._layout = function () {
        var _this = this;
        var _a;
        var _b = this._layoutUpdateOptions, sort = _b.sort, measureHeight = _b.measureHeight, measureWidth = _b.measureWidth, secondMeasureWidth = _b.secondMeasureWidth, update = _b.update, buildYAxisTick = _b.buildYAxisTick, cacheYAxisWidth = _b.cacheYAxisWidth, forceBuildYAxisTick = _b.forceBuildYAxisTick;
        if (sort) {
            while (isValid(this._chartContainer.firstChild)) {
                this._chartContainer.removeChild(this._chartContainer.firstChild);
            }
            this._separatorPanes.clear();
            this._drawPanes.sort(function (a, b) { return a.getOptions().order - b.getOptions().order; });
            var prevPane_1 = null;
            this._drawPanes.forEach(function (pane) {
                if (pane.getId() !== PaneIdConstants.X_AXIS) {
                    if (isValid(prevPane_1)) {
                        var separatorPane = new SeparatorPane(_this, '', prevPane_1, pane);
                        _this._chartContainer.appendChild(separatorPane.getContainer());
                        _this._separatorPanes.set(pane, separatorPane);
                    }
                    prevPane_1 = pane;
                }
                _this._chartContainer.appendChild(pane.getContainer());
            });
        }
        if (measureHeight) {
            var totalHeight = this._chartBounding.height;
            var separatorSize = this.getStyles().separator.size;
            var xAxisHeight = this._xAxisPane.getXAxisComponent().getAutoSize();
            var contentPanes = this._drawPanes.filter(function (pane) { return pane.getId() !== PaneIdConstants.X_AXIS; });
            var maximizedPane_1 = contentPanes.find(function (pane) { return pane.getOptions().state === 'maximize'; });
            var remainingHeight_1 = Math.max(totalHeight - xAxisHeight, 0);
            var paneHeights_1 = new Map();
            var actualSeparatorSize_1 = separatorSize;
            if (isValid(maximizedPane_1)) {
                actualSeparatorSize_1 = 0;
                contentPanes.forEach(function (pane) {
                    paneHeights_1.set(pane, pane === maximizedPane_1 ? remainingHeight_1 : 0);
                });
            }
            else {
                remainingHeight_1 = Math.max(remainingHeight_1 - this._separatorPanes.size * separatorSize, 0);
                var flexiblePane_1 = (_a = contentPanes.find(function (pane) { return pane.getId() === PaneIdConstants.CANDLE && pane.getOptions().state === 'normal'; })) !== null && _a !== void 0 ? _a : contentPanes.find(function (pane) { return pane.getOptions().state === 'normal'; });
                contentPanes.forEach(function (pane) {
                    if (pane === flexiblePane_1) {
                        return;
                    }
                    var options = pane.getOptions();
                    var paneHeight = options.minHeight;
                    if (options.state === 'normal') {
                        paneHeight = Math.max(options.minHeight, options.height);
                        var availableHeight = Math.max(remainingHeight_1, 0);
                        if (paneHeight > availableHeight) {
                            paneHeight = availableHeight;
                        }
                    }
                    remainingHeight_1 -= paneHeight;
                    paneHeights_1.set(pane, paneHeight);
                });
                if (isValid(flexiblePane_1)) {
                    paneHeights_1.set(flexiblePane_1, Math.max(remainingHeight_1, 0));
                }
            }
            this._drawPanes.forEach(function (pane) {
                var _a;
                if (pane.getId() !== PaneIdConstants.X_AXIS) {
                    pane.setBounding({ height: (_a = paneHeights_1.get(pane)) !== null && _a !== void 0 ? _a : 0 });
                }
            });
            this._xAxisPane.setBounding({ height: xAxisHeight });
            var top_1 = 0;
            this._drawPanes.forEach(function (pane) {
                var separatorPane = _this._separatorPanes.get(pane);
                if (isValid(separatorPane)) {
                    separatorPane.setBounding({ height: actualSeparatorSize_1, top: top_1 });
                    top_1 += actualSeparatorSize_1;
                }
                pane.setBounding({ top: top_1 });
                top_1 += pane.getBounding().height;
            });
        }
        var buildYAxisTickAndMeasureWidth = function () {
            var forceMeasureWidth = measureWidth;
            if (buildYAxisTick || forceBuildYAxisTick) {
                _this._drawPanes.forEach(function (pane) {
                    pane.getYAxisComponents().forEach(function (axis) {
                        var success = axis.buildTicks(forceBuildYAxisTick);
                        forceMeasureWidth || (forceMeasureWidth = success);
                    });
                });
            }
            if (forceMeasureWidth) {
                var totalWidth = _this._chartBounding.width;
                var styles = _this.getStyles();
                var leftOutsideYAxisWidths_1 = [];
                var leftInsideYAxisWidths_1 = [];
                var rightInsideYAxisWidths_1 = [];
                var rightOutsideYAxisWidths_1 = [];
                var updateColumnWidth_1 = function (widths, index, width) {
                    var _a;
                    widths[index] = Math.max((_a = widths[index]) !== null && _a !== void 0 ? _a : 0, width);
                };
                _this._drawPanes.forEach(function (pane) {
                    var leftOutsideAxes = [];
                    var leftInsideAxes = [];
                    var rightInsideAxes = [];
                    var rightOutsideAxes = [];
                    if (pane.getId() !== PaneIdConstants.X_AXIS) {
                        pane.getWidgetYAxisComponents().forEach(function (axis) {
                            var yAxis = axis;
                            if (yAxis.position === 'left') {
                                if (yAxis.inside) {
                                    leftInsideAxes.push(yAxis);
                                }
                                else {
                                    leftOutsideAxes.push(yAxis);
                                }
                            }
                            else {
                                if (yAxis.inside) {
                                    rightInsideAxes.push(yAxis);
                                }
                                else {
                                    rightOutsideAxes.push(yAxis);
                                }
                            }
                        });
                    }
                    leftOutsideAxes.forEach(function (yAxis, index) {
                        updateColumnWidth_1(leftOutsideYAxisWidths_1, index, yAxis.getAutoSize());
                    });
                    leftInsideAxes.forEach(function (yAxis, index) {
                        updateColumnWidth_1(leftInsideYAxisWidths_1, index, yAxis.getAutoSize());
                    });
                    rightInsideAxes.forEach(function (yAxis, index) {
                        updateColumnWidth_1(rightInsideYAxisWidths_1, index, yAxis.getAutoSize());
                    });
                    rightOutsideAxes.forEach(function (yAxis, index) {
                        updateColumnWidth_1(rightOutsideYAxisWidths_1, index, yAxis.getAutoSize());
                    });
                });
                var leftYAxisWidth_1 = leftOutsideYAxisWidths_1.reduce(function (total, width) { return total + width; }, 0);
                var rightYAxisWidth = rightOutsideYAxisWidths_1.reduce(function (total, width) { return total + width; }, 0);
                if (cacheYAxisWidth) {
                    leftYAxisWidth_1 = Math.max(_this._cacheYAxisWidth.left, leftYAxisWidth_1);
                    rightYAxisWidth = Math.max(_this._cacheYAxisWidth.right, rightYAxisWidth);
                }
                _this._cacheYAxisWidth.left = leftYAxisWidth_1;
                _this._cacheYAxisWidth.right = rightYAxisWidth;
                var mainWidth_1 = totalWidth;
                var mainLeft_1 = 0;
                var mainRight = 0;
                mainWidth_1 -= leftYAxisWidth_1;
                mainLeft_1 = leftYAxisWidth_1;
                mainWidth_1 -= rightYAxisWidth;
                mainRight = rightYAxisWidth;
                _this._chartStore.setTotalBarSpace(mainWidth_1);
                var paneBounding_1 = { width: totalWidth };
                var mainBounding_1 = { width: mainWidth_1, left: mainLeft_1, right: mainRight };
                var leftYAxisBounding_1 = { width: leftYAxisWidth_1 };
                var rightYAxisBounding_1 = { width: rightYAxisWidth };
                var separatorFill = styles.separator.fill;
                var separatorBounding_1 = {};
                if (!separatorFill) {
                    separatorBounding_1 = mainBounding_1;
                }
                else {
                    separatorBounding_1 = paneBounding_1;
                }
                _this._drawPanes.forEach(function (pane) {
                    var _a, _b;
                    (_a = _this._separatorPanes.get(pane)) === null || _a === void 0 ? void 0 : _a.setBounding(separatorBounding_1);
                    var yAxisBounding = {};
                    var leftOutsideOffset = 0;
                    var leftInsideOffset = 0;
                    var rightInsideOffset = 0;
                    var rightOutsideOffset = 0;
                    var leftOutsideAxes = [];
                    var leftInsideAxes = [];
                    var rightInsideAxes = [];
                    var rightOutsideAxes = [];
                    if (pane.getId() !== PaneIdConstants.X_AXIS) {
                        pane.getWidgetYAxisComponents().forEach(function (axis) {
                            var yAxis = axis;
                            if (yAxis.position === 'left') {
                                if (yAxis.inside) {
                                    leftInsideAxes.push(yAxis);
                                }
                                else {
                                    leftOutsideAxes.push(yAxis);
                                }
                            }
                            else {
                                if (yAxis.inside) {
                                    rightInsideAxes.push(yAxis);
                                }
                                else {
                                    rightOutsideAxes.push(yAxis);
                                }
                            }
                        });
                    }
                    var paneLeftOutsideYAxisWidth = leftOutsideAxes.reduce(function (total, _yAxis, index) { var _a; return total + ((_a = leftOutsideYAxisWidths_1[index]) !== null && _a !== void 0 ? _a : 0); }, 0);
                    leftOutsideOffset = leftYAxisWidth_1 - paneLeftOutsideYAxisWidth;
                    for (var index = leftOutsideAxes.length - 1; index >= 0; index--) {
                        var yAxis = leftOutsideAxes[index];
                        var width = (_b = leftOutsideYAxisWidths_1[index]) !== null && _b !== void 0 ? _b : 0;
                        yAxisBounding[yAxis.id] = { width: width, left: leftOutsideOffset };
                        leftOutsideOffset += width;
                    }
                    leftInsideAxes.forEach(function (yAxis, index) {
                        var _a;
                        var width = (_a = leftInsideYAxisWidths_1[index]) !== null && _a !== void 0 ? _a : 0;
                        yAxisBounding[yAxis.id] = { width: width, left: mainLeft_1 + leftInsideOffset };
                        leftInsideOffset += width;
                    });
                    rightInsideAxes.forEach(function (yAxis, index) {
                        var _a;
                        var width = (_a = rightInsideYAxisWidths_1[index]) !== null && _a !== void 0 ? _a : 0;
                        rightInsideOffset += width;
                        yAxisBounding[yAxis.id] = { width: width, left: mainLeft_1 + mainWidth_1 - rightInsideOffset };
                    });
                    rightOutsideAxes.forEach(function (yAxis, index) {
                        var _a;
                        var width = (_a = rightOutsideYAxisWidths_1[index]) !== null && _a !== void 0 ? _a : 0;
                        yAxisBounding[yAxis.id] = { width: width, left: mainLeft_1 + mainWidth_1 + rightOutsideOffset };
                        rightOutsideOffset += width;
                    });
                    pane.setYAxesBounding(yAxisBounding);
                    pane.setBounding(paneBounding_1, mainBounding_1, leftYAxisBounding_1, rightYAxisBounding_1);
                });
            }
        };
        buildYAxisTickAndMeasureWidth();
        if (secondMeasureWidth) {
            buildYAxisTickAndMeasureWidth();
        }
        if (update) {
            this._xAxisPane.getXAxisComponent().buildTicks(true);
            this.updatePane(4 /* UpdateLevel.All */);
        }
        this._layoutUpdateOptions = {
            sort: false,
            measureHeight: false,
            measureWidth: false,
            secondMeasureWidth: false,
            update: false,
            buildYAxisTick: false,
            cacheYAxisWidth: false,
            forceBuildYAxisTick: false
        };
    };
    ChartImp.prototype.updatePane = function (level, paneId) {
        var _this = this;
        if (isValid(paneId)) {
            var pane = this.getDrawPaneById(paneId);
            pane === null || pane === void 0 ? void 0 : pane.update(level);
        }
        else {
            this._drawPanes.forEach(function (pane) {
                var _a;
                pane.update(level);
                (_a = _this._separatorPanes.get(pane)) === null || _a === void 0 ? void 0 : _a.update(level);
            });
        }
    };
    ChartImp.prototype.getDom = function (paneId, position) {
        var _a, _b;
        if (isValid(paneId)) {
            var pane = this.getDrawPaneById(paneId);
            if (isValid(pane)) {
                var pos = position !== null && position !== void 0 ? position : 'root';
                switch (pos) {
                    case 'root': {
                        return pane.getContainer();
                    }
                    case 'main': {
                        return pane.getMainWidget().getContainer();
                    }
                    case 'yAxis': {
                        return (_b = (_a = pane.getYAxisWidget()) === null || _a === void 0 ? void 0 : _a.getContainer()) !== null && _b !== void 0 ? _b : null;
                    }
                }
            }
        }
        else {
            return this._chartContainer;
        }
        return null;
    };
    ChartImp.prototype.getSize = function (paneId, position) {
        var _a, _b;
        if (isValid(paneId)) {
            var pane = this.getDrawPaneById(paneId);
            if (isValid(pane)) {
                var pos = position !== null && position !== void 0 ? position : 'root';
                switch (pos) {
                    case 'root': {
                        return pane.getBounding();
                    }
                    case 'main': {
                        return pane.getMainWidget().getBounding();
                    }
                    case 'yAxis': {
                        return (_b = (_a = pane.getYAxisWidget()) === null || _a === void 0 ? void 0 : _a.getBounding()) !== null && _b !== void 0 ? _b : null;
                    }
                }
            }
        }
        else {
            return this._chartBounding;
        }
        return null;
    };
    ChartImp.prototype._resetYAxisAutoCalcTickFlag = function () {
        this._drawPanes.forEach(function (pane) {
            pane.getYAxisComponents().forEach(function (axis) {
                axis.setAutoCalcTickFlag(true);
            });
        });
    };
    ChartImp.prototype.setSymbol = function (symbol) {
        if (symbol !== this.getSymbol()) {
            this._resetYAxisAutoCalcTickFlag();
            this._chartStore.setSymbol(symbol);
        }
    };
    ChartImp.prototype.getSymbol = function () {
        return this._chartStore.getSymbol();
    };
    ChartImp.prototype.setPeriod = function (period) {
        if (period !== this.getPeriod()) {
            this._resetYAxisAutoCalcTickFlag();
            this._chartStore.setPeriod(period);
        }
    };
    ChartImp.prototype.getPeriod = function () {
        return this._chartStore.getPeriod();
    };
    ChartImp.prototype.setStyles = function (value) {
        var _this = this;
        this._setOptions(function () {
            _this._chartStore.setStyles(value);
        });
    };
    ChartImp.prototype.getStyles = function () {
        return this._chartStore.getStyles();
    };
    ChartImp.prototype.setFormatter = function (formatter) {
        var _this = this;
        this._setOptions(function () {
            _this._chartStore.setFormatter(formatter);
        });
    };
    ChartImp.prototype.getFormatter = function () {
        return this._chartStore.getFormatter();
    };
    ChartImp.prototype.setLocale = function (locale) {
        var _this = this;
        this._setOptions(function () {
            _this._chartStore.setLocale(locale);
        });
    };
    ChartImp.prototype.getLocale = function () {
        return this._chartStore.getLocale();
    };
    ChartImp.prototype.setTimezone = function (timezone) {
        var _this = this;
        this._setOptions(function () {
            _this._chartStore.setTimezone(timezone);
        });
    };
    ChartImp.prototype.getTimezone = function () {
        return this._chartStore.getTimezone();
    };
    ChartImp.prototype.setThousandsSeparator = function (thousandsSeparator) {
        var _this = this;
        this._setOptions(function () {
            _this._chartStore.setThousandsSeparator(thousandsSeparator);
        });
    };
    ChartImp.prototype.getThousandsSeparator = function () {
        return this._chartStore.getThousandsSeparator();
    };
    ChartImp.prototype.setDecimalFold = function (decimalFold) {
        var _this = this;
        this._setOptions(function () {
            _this._chartStore.setDecimalFold(decimalFold);
        });
    };
    ChartImp.prototype.getDecimalFold = function () {
        return this._chartStore.getDecimalFold();
    };
    ChartImp.prototype.setHotkey = function (hotkey) {
        this._chartStore.setHotkey(hotkey);
    };
    ChartImp.prototype.getHotkey = function () {
        return this._chartStore.getHotkey();
    };
    ChartImp.prototype.getHotKey = function () {
        return this._chartStore.getHotKey();
    };
    ChartImp.prototype._setOptions = function (fuc) {
        fuc();
        this.layout({
            measureHeight: true,
            measureWidth: true,
            update: true,
            buildYAxisTick: true,
            forceBuildYAxisTick: true
        });
    };
    ChartImp.prototype.setOffsetRightDistance = function (distance) {
        this._chartStore.setOffsetRightDistance(distance, true);
    };
    ChartImp.prototype.getOffsetRightDistance = function () {
        return this._chartStore.getOffsetRightDistance();
    };
    ChartImp.prototype.setMaxOffsetLeftDistance = function (distance) {
        if (distance < 0) {
            logWarn('setMaxOffsetLeftDistance', 'distance', 'distance must greater than zero!!!');
            return;
        }
        this._chartStore.setMaxOffsetLeftDistance(distance);
    };
    ChartImp.prototype.setMaxOffsetRightDistance = function (distance) {
        if (distance < 0) {
            logWarn('setMaxOffsetRightDistance', 'distance', 'distance must greater than zero!!!');
            return;
        }
        this._chartStore.setMaxOffsetRightDistance(distance);
    };
    ChartImp.prototype.setLeftMinVisibleBarCount = function (barCount) {
        if (barCount < 0) {
            logWarn('setLeftMinVisibleBarCount', 'barCount', 'barCount must greater than zero!!!');
            return;
        }
        this._chartStore.setLeftMinVisibleBarCount(Math.ceil(barCount));
    };
    ChartImp.prototype.setRightMinVisibleBarCount = function (barCount) {
        if (barCount < 0) {
            logWarn('setRightMinVisibleBarCount', 'barCount', 'barCount must greater than zero!!!');
            return;
        }
        this._chartStore.setRightMinVisibleBarCount(Math.ceil(barCount));
    };
    ChartImp.prototype.setBarSpace = function (space) {
        this._chartStore.setBarSpace(space);
    };
    ChartImp.prototype.getBarSpace = function () {
        return this._chartStore.getBarSpace();
    };
    ChartImp.prototype.getVisibleRange = function () {
        return this._chartStore.getVisibleRange();
    };
    ChartImp.prototype._removeOrphanYAxes = function () {
        var _this = this;
        var changed = false;
        this._drawPanes.forEach(function (pane) {
            var paneId = pane.getId();
            if (paneId === PaneIdConstants.X_AXIS) {
                return;
            }
            var usedYAxisIds = new Set();
            var defaultYAxisId = pane.getDefaultYAxisId();
            if (isValid(defaultYAxisId)) {
                usedYAxisIds.add(defaultYAxisId);
            }
            _this._chartStore.getIndicatorsByPaneId(paneId).forEach(function (indicator) {
                usedYAxisIds.add(indicator.yAxisId);
            });
            pane.getYAxisComponents().forEach(function (yAxis) {
                if (!usedYAxisIds.has(yAxis.id) && !pane.isManualYAxis(yAxis.id)) {
                    changed = pane.removeYAxis(yAxis.id) || changed;
                }
            });
        });
        return changed;
    };
    ChartImp.prototype._createOrUseIndicatorYAxis = function (pane, yAxisId) {
        var changed = false;
        if (!pane.hasYAxisComponent(yAxisId)) {
            pane.createOrOverrideYAxis(__assign(__assign({}, this._chartStore.getLayoutOptions().yAxis), { id: yAxisId }));
            changed = true;
        }
        if (pane.isManualYAxis(yAxisId)) {
            pane.setManualYAxis(yAxisId, false);
            changed = true;
        }
        return changed;
    };
    ChartImp.prototype.resetData = function () {
        this._chartStore.resetData();
    };
    ChartImp.prototype.getDataList = function () {
        return this._chartStore.getDataList();
    };
    ChartImp.prototype.setDataLoader = function (dataLoader) {
        this._resetYAxisAutoCalcTickFlag();
        this._chartStore.setDataLoader(dataLoader);
    };
    ChartImp.prototype.createIndicator = function (value, isStack) {
        var _a, _b, _c, _d;
        var indicator = isString(value) ? { name: value } : value;
        if (getIndicatorClass(indicator.name) === null) {
            logWarn('createIndicator', 'value', 'indicator not supported, you may need to use registerIndicator to add one!!!');
            return null;
        }
        (_a = indicator.id) !== null && _a !== void 0 ? _a : (indicator.id = createId("".concat(indicator.name, "_")));
        (_b = indicator.paneId) !== null && _b !== void 0 ? _b : (indicator.paneId = createId(PaneIdConstants.INDICATOR));
        var indicatorPane = this.getDrawPaneById(indicator.paneId);
        (_c = indicator.yAxisId) !== null && _c !== void 0 ? _c : (indicator.yAxisId = (_d = indicatorPane === null || indicatorPane === void 0 ? void 0 : indicatorPane.getDefaultYAxisId()) !== null && _d !== void 0 ? _d : createId(Y_AXIS_ID_PREFIX));
        var result = this._chartStore.addIndicator(indicator, isStack !== null && isStack !== void 0 ? isStack : false);
        if (result) {
            var shouldSort = false;
            var pane = this.getDrawPaneById(indicator.paneId);
            if (!isValid(pane)) {
                pane = this._createPane(IndicatorPane, __assign(__assign({}, this._chartStore.getLayoutOptions().pane), { id: indicator.paneId }));
                shouldSort = true;
            }
            this._createOrUseIndicatorYAxis(pane, indicator.yAxisId);
            this._removeOrphanYAxes();
            this.layout({
                sort: shouldSort,
                measureHeight: true,
                measureWidth: true,
                update: true,
                buildYAxisTick: true,
                forceBuildYAxisTick: true
            });
            return indicator.id;
        }
        return null;
    };
    ChartImp.prototype.overrideIndicator = function (override) {
        var _this = this;
        var filterIndicators = this._chartStore.getIndicatorsByFilter(override);
        if (filterIndicators.length === 0) {
            return false;
        }
        var updated = this._chartStore.overrideIndicator(override);
        filterIndicators.forEach(function (indicator) {
            var pane = _this.getDrawPaneById(indicator.paneId);
            if (isValid(pane)) {
                updated = _this._createOrUseIndicatorYAxis(pane, indicator.yAxisId) || updated;
            }
        });
        if (updated) {
            this._removeOrphanYAxes();
            this.layout({
                measureWidth: true,
                update: true,
                buildYAxisTick: true,
                forceBuildYAxisTick: true
            });
        }
        return updated;
    };
    ChartImp.prototype.getIndicators = function (filter) {
        return this._chartStore.getIndicatorsByFilter(filter !== null && filter !== void 0 ? filter : {});
    };
    ChartImp.prototype.removeIndicator = function (filter) {
        var _this = this;
        var removed = this._chartStore.removeIndicator(filter !== null && filter !== void 0 ? filter : {});
        if (removed) {
            this._removeOrphanYAxes();
            var panesChanged_1 = false;
            var removePaneIds_1 = [];
            this._drawPanes.forEach(function (pane) {
                var paneId = pane.getId();
                if (paneId !== PaneIdConstants.X_AXIS && paneId !== PaneIdConstants.CANDLE) {
                    var paneIndicators = _this._chartStore.getIndicatorsByPaneId(paneId);
                    if (paneIndicators.length === 0) {
                        removePaneIds_1.push(paneId);
                    }
                }
            });
            removePaneIds_1.forEach(function (paneId) {
                var index = _this._drawPanes.findIndex(function (pane) { return pane.getId() === paneId; });
                var pane = _this._drawPanes[index];
                if (isValid(pane)) {
                    _this._drawPanes.splice(index, 1);
                    pane.destroy();
                    panesChanged_1 = true;
                }
            });
            this.layout({
                sort: panesChanged_1,
                measureHeight: panesChanged_1,
                measureWidth: true,
                update: true,
                buildYAxisTick: true,
                forceBuildYAxisTick: true
            });
        }
        return removed;
    };
    ChartImp.prototype.createOverlay = function (value) {
        var _this = this;
        var overlays = [];
        var appointPaneFlags = [];
        var build = function (overlay) {
            if (!isValid(overlay.paneId) || _this.getDrawPaneById(overlay.paneId) === null) {
                overlay.paneId = PaneIdConstants.CANDLE;
                appointPaneFlags.push(false);
            }
            else {
                appointPaneFlags.push(true);
            }
            overlays.push(overlay);
        };
        if (isString(value)) {
            build({ name: value });
        }
        else if (isArray(value)) {
            value.forEach(function (v) {
                var overlay = null;
                if (isString(v)) {
                    overlay = { name: v };
                }
                else {
                    overlay = v;
                }
                build(overlay);
            });
        }
        else {
            build(value);
        }
        var ids = this._chartStore.addOverlays(overlays, appointPaneFlags);
        if (isArray(value)) {
            return ids;
        }
        return ids[0];
    };
    ChartImp.prototype.getOverlays = function (filter) {
        return this._chartStore.getOverlaysByFilter(filter !== null && filter !== void 0 ? filter : {});
    };
    ChartImp.prototype.overrideOverlay = function (override) {
        return this._chartStore.overrideOverlay(override);
    };
    ChartImp.prototype.removeOverlay = function (filter) {
        return this._chartStore.removeOverlay(filter !== null && filter !== void 0 ? filter : {});
    };
    ChartImp.prototype.setPaneOptions = function (options) {
        var e_1, _a;
        var _b;
        var shouldMeasureHeight = false;
        var shouldLayout = false;
        var shouldSort = false;
        var validId = isValid(options.id);
        try {
            for (var _c = __values(this._drawPanes), _d = _c.next(); !_d.done; _d = _c.next()) {
                var currentPane = _d.value;
                var currentPaneId = currentPane.getId();
                if ((validId && options.id === currentPaneId) || !validId) {
                    if (currentPaneId !== PaneIdConstants.X_AXIS) {
                        var currentPaneOptions = currentPane.getOptions();
                        var currentState = currentPaneOptions.state;
                        if (isNumber(options.height) && options.height > 0) {
                            var minHeight = Math.max((_b = options.minHeight) !== null && _b !== void 0 ? _b : currentPaneOptions.minHeight, 0);
                            var height = Math.max(minHeight, options.height);
                            shouldLayout = true;
                            shouldMeasureHeight = true;
                            currentPane.setBounding({ height: height });
                        }
                        if (isValid(options.state)) {
                            shouldLayout = true;
                            shouldMeasureHeight = true;
                            if (currentState === 'normal' && options.state !== 'normal') {
                                currentPane.setOptions({ height: currentPane.getBounding().height });
                            }
                            else if (currentState !== 'normal' && options.state === 'normal' && !isNumber(options.height)) {
                                currentPane.setBounding({
                                    height: Math.max(currentPaneOptions.minHeight, currentPaneOptions.height)
                                });
                            }
                        }
                    }
                    if (isNumber(options.order)) {
                        shouldLayout = true;
                        shouldSort = true;
                    }
                    currentPane.setOptions(options);
                    if (currentPaneId === options.id) {
                        break;
                    }
                }
            }
        }
        catch (e_1_1) { e_1 = { error: e_1_1 }; }
        finally {
            try {
                if (_d && !_d.done && (_a = _c.return)) _a.call(_c);
            }
            finally { if (e_1) throw e_1.error; }
        }
        if (shouldLayout) {
            this.layout({
                sort: shouldSort,
                measureHeight: shouldMeasureHeight,
                measureWidth: true,
                update: true,
                buildYAxisTick: true,
                forceBuildYAxisTick: true
            });
        }
    };
    ChartImp.prototype.createYAxis = function (yAxis) {
        var _a, _b;
        var paneId = (_a = yAxis.paneId) !== null && _a !== void 0 ? _a : PaneIdConstants.CANDLE;
        var pane = this.getDrawPaneById(paneId);
        if (!isValid(pane) || paneId === PaneIdConstants.X_AXIS) {
            logWarn('createYAxis', 'paneId', 'pane does not exist or does not support yAxis!!!');
            return null;
        }
        var id = (_b = yAxis.id) !== null && _b !== void 0 ? _b : createId(Y_AXIS_ID_PREFIX);
        if (pane.hasYAxisComponent(id)) {
            return id;
        }
        pane.createOrOverrideYAxis(__assign(__assign(__assign({}, this._chartStore.getLayoutOptions().yAxis), yAxis), { id: id, paneId: paneId }));
        pane.setManualYAxis(id, true);
        this.layout({
            measureWidth: true,
            update: true,
            buildYAxisTick: true,
            forceBuildYAxisTick: true
        });
        return id;
    };
    ChartImp.prototype.removeYAxis = function (filter) {
        var e_2, _a;
        var id = filter.id, name = filter.name;
        if (!isValid(id) && !isValid(name)) {
            logWarn('removeYAxis', 'filter', 'id or name should not be empty!!!');
            return false;
        }
        var removed = false;
        var _loop_1 = function (yAxis) {
            var pane = this_1.getDrawPaneById(yAxis.paneId);
            if (!isValid(pane)) {
                return "continue";
            }
            if (pane.isDefaultYAxis(yAxis.id) && yAxis.paneId === PaneIdConstants.CANDLE) {
                return "continue";
            }
            var indicators = this_1._chartStore.getIndicatorsByPaneId(yAxis.paneId);
            if (indicators.some(function (indicator) { return indicator.yAxisId === yAxis.id; })) {
                return "continue";
            }
            removed = pane.removeYAxis(yAxis.id) || removed;
        };
        var this_1 = this;
        try {
            for (var _b = __values(this.getYAxes(filter)), _c = _b.next(); !_c.done; _c = _b.next()) {
                var yAxis = _c.value;
                _loop_1(yAxis);
            }
        }
        catch (e_2_1) { e_2 = { error: e_2_1 }; }
        finally {
            try {
                if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
            }
            finally { if (e_2) throw e_2.error; }
        }
        if (removed) {
            this.layout({
                measureWidth: true,
                update: true,
                buildYAxisTick: true,
                forceBuildYAxisTick: true
            });
        }
        return removed;
    };
    ChartImp.prototype.getYAxes = function (filter) {
        var _a, _b;
        var paneId = filter.paneId, id = filter.id;
        var name = filter.name;
        var match = function (yAxis) {
            if (isValid(id)) {
                return yAxis.id === id;
            }
            return !isValid(name) || yAxis.name === name;
        };
        var yAxes = [];
        if (isValid(paneId)) {
            yAxes = yAxes.concat((_b = (_a = this.getDrawPaneById(paneId)) === null || _a === void 0 ? void 0 : _a.getYAxisComponents().filter(match)) !== null && _b !== void 0 ? _b : []);
        }
        else {
            this._drawPanes.forEach(function (pane) {
                if (pane.getId() !== PaneIdConstants.X_AXIS) {
                    yAxes = yAxes.concat(pane.getYAxisComponents().filter(match));
                }
            });
        }
        return yAxes;
    };
    ChartImp.prototype.overrideYAxis = function (yAxis) {
        var _this = this;
        var filterYAxes = this.getYAxes({ paneId: yAxis.paneId, id: yAxis.id });
        if (filterYAxes.length === 0) {
            return;
        }
        filterYAxes.forEach(function (axis) {
            var _a;
            (_a = _this.getDrawPaneById(axis.paneId)) === null || _a === void 0 ? void 0 : _a.createOrOverrideYAxis(__assign(__assign({}, yAxis), { id: axis.id }));
        });
        this.layout({
            measureWidth: true,
            update: true,
            buildYAxisTick: true,
            forceBuildYAxisTick: true
        });
    };
    ChartImp.prototype.overrideXAxis = function (xAxis) {
        this._xAxisPane.overrideXAxis(xAxis);
        this.layout({
            measureHeight: true,
            update: true,
            buildYAxisTick: true,
            forceBuildYAxisTick: true
        });
    };
    ChartImp.prototype.getPaneOptions = function (id) {
        var _a;
        if (isValid(id)) {
            var pane = this.getDrawPaneById(id);
            return (_a = pane === null || pane === void 0 ? void 0 : pane.getOptions()) !== null && _a !== void 0 ? _a : null;
        }
        return this._drawPanes.map(function (pane) { return pane.getOptions(); });
    };
    ChartImp.prototype.setZoomEnabled = function (enabled) {
        this._chartStore.setZoomEnabled(enabled);
    };
    ChartImp.prototype.isZoomEnabled = function () {
        return this._chartStore.isZoomEnabled();
    };
    ChartImp.prototype.setZoomAnchor = function (anchor) {
        this._chartStore.setZoomAnchor(anchor);
    };
    ChartImp.prototype.getZoomAnchor = function () {
        return this._chartStore.getZoomAnchor();
    };
    ChartImp.prototype.setScrollEnabled = function (enabled) {
        this._chartStore.setScrollEnabled(enabled);
    };
    ChartImp.prototype.isScrollEnabled = function () {
        return this._chartStore.isScrollEnabled();
    };
    ChartImp.prototype.scrollByDistance = function (distance, animationDuration) {
        var _this = this;
        var duration = isNumber(animationDuration) && animationDuration > 0 ? animationDuration : 0;
        this._chartStore.startScroll();
        if (duration > 0) {
            var animation = new Animation({ duration: duration });
            animation.doFrame(function (frameTime) {
                var progressDistance = distance * (frameTime / duration);
                _this._chartStore.scroll(progressDistance);
            });
            animation.start();
        }
        else {
            this._chartStore.scroll(distance);
        }
    };
    ChartImp.prototype.scrollToRealTime = function (animationDuration) {
        var barSpace = this._chartStore.getBarSpace().bar;
        var difBarCount = this._chartStore.getLastBarRightSideDiffBarCount() - this._chartStore.getInitialOffsetRightDistance() / barSpace;
        var distance = difBarCount * barSpace;
        this.scrollByDistance(distance, animationDuration);
    };
    ChartImp.prototype.scrollToDataIndex = function (dataIndex, animationDuration) {
        var distance = (this._chartStore.getLastBarRightSideDiffBarCount() + (this.getDataList().length - 1 - dataIndex)) * this._chartStore.getBarSpace().bar;
        this.scrollByDistance(distance, animationDuration);
    };
    ChartImp.prototype.scrollToTimestamp = function (timestamp, animationDuration) {
        var dataIndex = binarySearchNearest(this.getDataList(), 'timestamp', timestamp);
        this.scrollToDataIndex(dataIndex, animationDuration);
    };
    ChartImp.prototype.zoomAtCoordinate = function (scale, coordinate, animationDuration) {
        var _this = this;
        var duration = isNumber(animationDuration) && animationDuration > 0 ? animationDuration : 0;
        var barSpace = this._chartStore.getBarSpace().bar;
        var scaleBarSpace = barSpace * scale;
        var difSpace = scaleBarSpace - barSpace;
        if (duration > 0) {
            var prevProgressBarSpace_1 = 0;
            var animation = new Animation({ duration: duration });
            animation.doFrame(function (frameTime) {
                var progressBarSpace = difSpace * (frameTime / duration);
                var scale = ((progressBarSpace - prevProgressBarSpace_1) / _this._chartStore.getBarSpace().bar) * SCALE_MULTIPLIER;
                _this._chartStore.zoom(scale, coordinate !== null && coordinate !== void 0 ? coordinate : null, 'main');
                prevProgressBarSpace_1 = progressBarSpace;
            });
            animation.start();
        }
        else {
            this._chartStore.zoom((difSpace / barSpace) * SCALE_MULTIPLIER, coordinate !== null && coordinate !== void 0 ? coordinate : null, 'main');
        }
    };
    ChartImp.prototype.zoomAtDataIndex = function (scale, dataIndex, animationDuration) {
        var x = this._chartStore.dataIndexToCoordinate(dataIndex);
        this.zoomAtCoordinate(scale, { x: x, y: 0 }, animationDuration);
    };
    ChartImp.prototype.zoomAtTimestamp = function (scale, timestamp, animationDuration) {
        var dataIndex = binarySearchNearest(this.getDataList(), 'timestamp', timestamp);
        this.zoomAtDataIndex(scale, dataIndex, animationDuration);
    };
    ChartImp.prototype.convertToPixel = function (points, filter) {
        var _this = this;
        var _a;
        var _b = filter !== null && filter !== void 0 ? filter : {}, _c = _b.paneId, paneId = _c === void 0 ? PaneIdConstants.CANDLE : _c, yAxisId = _b.yAxisId, _d = _b.absolute, absolute = _d === void 0 ? false : _d;
        var coordinates = [];
        if (paneId !== PaneIdConstants.X_AXIS) {
            var pane = this.getDrawPaneById(paneId);
            if (pane !== null) {
                var bounding_1 = pane.getBounding();
                var ps = [];
                ps = ps.concat(points);
                var xAxis_1 = this._xAxisPane.getXAxisComponent();
                var yAxis_1 = pane.getYAxisComponentById(yAxisId);
                coordinates = ps.map(function (point) {
                    var coordinate = {};
                    var dataIndex = point.dataIndex;
                    if (isNumber(point.timestamp)) {
                        dataIndex = _this._chartStore.timestampToDataIndex(point.timestamp);
                    }
                    if (isNumber(dataIndex)) {
                        coordinate.x = xAxis_1.convertToPixel(dataIndex);
                    }
                    if (isNumber(point.value)) {
                        var y = yAxis_1.convertToPixel(point.value);
                        coordinate.y = absolute ? bounding_1.top + y : y;
                    }
                    return coordinate;
                });
            }
        }
        return isArray(points) ? coordinates : ((_a = coordinates[0]) !== null && _a !== void 0 ? _a : {});
    };
    ChartImp.prototype.convertFromPixel = function (coordinates, filter) {
        var _this = this;
        var _a;
        var _b = filter !== null && filter !== void 0 ? filter : {}, _c = _b.paneId, paneId = _c === void 0 ? PaneIdConstants.CANDLE : _c, yAxisId = _b.yAxisId, _d = _b.absolute, absolute = _d === void 0 ? false : _d;
        var points = [];
        if (paneId !== PaneIdConstants.X_AXIS) {
            var pane = this.getDrawPaneById(paneId);
            if (pane !== null) {
                var bounding_2 = pane.getBounding();
                var cs = [];
                cs = cs.concat(coordinates);
                var xAxis_2 = this._xAxisPane.getXAxisComponent();
                var yAxis_2 = pane.getYAxisComponentById(yAxisId);
                points = cs.map(function (coordinate) {
                    var _a;
                    var point = {};
                    if (isNumber(coordinate.x)) {
                        var dataIndex = xAxis_2.convertFromPixel(coordinate.x);
                        point.dataIndex = dataIndex;
                        point.timestamp = (_a = _this._chartStore.dataIndexToTimestamp(dataIndex)) !== null && _a !== void 0 ? _a : undefined;
                    }
                    if (isNumber(coordinate.y)) {
                        var y = absolute ? coordinate.y - bounding_2.top : coordinate.y;
                        point.value = yAxis_2.convertFromPixel(y);
                    }
                    return point;
                });
            }
        }
        return isArray(coordinates) ? points : ((_a = points[0]) !== null && _a !== void 0 ? _a : {});
    };
    ChartImp.prototype.executeAction = function (type, data) {
        var _a;
        switch (type) {
            case 'onCrosshairChange': {
                var crosshair = null;
                if (isValid(data)) {
                    crosshair = __assign({}, data);
                    (_a = crosshair.paneId) !== null && _a !== void 0 ? _a : (crosshair.paneId = PaneIdConstants.CANDLE);
                }
                this._chartStore.setCrosshair(crosshair, { notExecuteAction: true });
                break;
            }
        }
    };
    ChartImp.prototype.subscribeAction = function (type, callback) {
        this._chartStore.subscribeAction(type, callback);
    };
    ChartImp.prototype.unsubscribeAction = function (type, callback) {
        this._chartStore.unsubscribeAction(type, callback);
    };
    ChartImp.prototype.getConvertPictureUrl = function (includeOverlay, type, backgroundColor) {
        var _this = this;
        var _a = this._chartBounding, width = _a.width, height = _a.height;
        var canvas = createDom('canvas', {
            width: "".concat(width, "px"),
            height: "".concat(height, "px"),
            boxSizing: 'border-box'
        });
        var ctx = canvas.getContext('2d');
        var pixelRatio = getPixelRatio(canvas);
        canvas.width = width * pixelRatio;
        canvas.height = height * pixelRatio;
        ctx.scale(pixelRatio, pixelRatio);
        ctx.fillStyle = backgroundColor !== null && backgroundColor !== void 0 ? backgroundColor : '#FFFFFF';
        ctx.fillRect(0, 0, width, height);
        var overlayFlag = includeOverlay !== null && includeOverlay !== void 0 ? includeOverlay : false;
        this._drawPanes.forEach(function (pane) {
            var separatorPane = _this._separatorPanes.get(pane);
            if (isValid(separatorPane)) {
                var separatorBounding = separatorPane.getBounding();
                ctx.drawImage(separatorPane.getImage(overlayFlag), separatorBounding.left, separatorBounding.top, separatorBounding.width, separatorBounding.height);
            }
            var bounding = pane.getBounding();
            ctx.drawImage(pane.getImage(overlayFlag), 0, bounding.top, width, bounding.height);
        });
        return canvas.toDataURL("image/".concat(type !== null && type !== void 0 ? type : 'jpeg'));
    };
    ChartImp.prototype.resize = function () {
        this._cacheChartBounding();
        this.layout({
            measureHeight: true,
            measureWidth: true,
            secondMeasureWidth: true,
            update: true,
            buildYAxisTick: true,
            forceBuildYAxisTick: true
        });
    };
    ChartImp.prototype.destroy = function () {
        if (this._resizeRequestAnimationId !== DEFAULT_REQUEST_ID) {
            cancelAnimationFrame(this._resizeRequestAnimationId);
            this._resizeRequestAnimationId = DEFAULT_REQUEST_ID;
        }
        if (isValid(this._resizeObserver)) {
            this._resizeObserver.disconnect();
            this._resizeObserver = null;
        }
        else {
            window.removeEventListener('resize', this._scheduleResize);
        }
        this._chartEvent.destroy();
        this._drawPanes.forEach(function (pane) {
            pane.destroy();
        });
        this._drawPanes = [];
        this._separatorPanes.clear();
        this._chartStore.destroy();
        this._container.removeChild(this._chartContainer);
    };
    return ChartImp;
}());

/**
 *       ___           ___                   ___           ___           ___           ___           ___           ___           ___
 *      /\__\         /\__\      ___        /\__\         /\  \         /\  \         /\__\         /\  \         /\  \         /\  \
 *     /:/  /        /:/  /     /\  \      /::|  |       /::\  \       /::\  \       /:/  /        /::\  \       /::\  \        \:\  \
 *    /:/__/        /:/  /      \:\  \    /:|:|  |      /:/\:\  \     /:/\:\  \     /:/__/        /:/\:\  \     /:/\:\  \        \:\  \
 *   /::\__\____   /:/  /       /::\__\  /:/|:|  |__   /::\~\:\  \   /:/  \:\  \   /::\  \ ___   /::\~\:\  \   /::\~\:\  \       /::\  \
 *  /:/\:::::\__\ /:/__/     __/:/\/__/ /:/ |:| /\__\ /:/\:\ \:\__\ /:/__/ \:\__\ /:/\:\  /\__\ /:/\:\ \:\__\ /:/\:\ \:\__\     /:/\:\__\
 *  \/_|:|~~|~    \:\  \    /\/:/  /    \/__|:|/:/  / \:\~\:\ \/__/ \:\  \  \/__/ \/__\:\/:/  / \/__\:\/:/  / \/_|::\/:/  /    /:/  \/__/
 *     |:|  |      \:\  \   \::/__/         |:/:/  /   \:\ \:\__\    \:\  \            \::/  /       \::/  /     |:|::/  /    /:/  /
 *     |:|  |       \:\  \   \:\__\         |::/  /     \:\ \/__/     \:\  \           /:/  /        /:/  /      |:|\/__/     \/__/
 *     |:|  |        \:\__\   \/__/         /:/  /       \:\__\        \:\__\         /:/  /        /:/  /       |:|  |
 *      \|__|         \/__/                 \/__/         \/__/         \/__/         \/__/         \/__/         \|__|
 *
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
var charts = new Map();
var chartBaseId = 1;
/**
 * Chart version
 * @return {string}
 */
function version() {
    return '10.0.2';
}
/**
 * Init chart instance
 * @param ds
 * @param options
 * @returns {Chart}
 */
function init(ds, options) {
    var _a;
    logTag();
    var dom = null;
    if (isString(ds)) {
        dom = document.getElementById(ds);
    }
    else {
        dom = ds;
    }
    if (dom === null) {
        logError('', '', 'The chart cannot be initialized correctly. Please check the parameters. The chart container cannot be null and child elements need to be added!!!');
        return null;
    }
    var chart = charts.get((_a = dom.getAttribute('k-line-chart-id')) !== null && _a !== void 0 ? _a : '');
    if (isValid(chart)) {
        logWarn('', '', 'The chart has been initialized on the dom！！！');
        return chart;
    }
    var id = "k_line_chart_".concat(chartBaseId++);
    chart = new ChartImp(dom, options);
    chart.id = id;
    dom.setAttribute('k-line-chart-id', id);
    charts.set(id, chart);
    return chart;
}
/**
 * Destroy chart instance
 * @param dcs
 */
function dispose(dcs) {
    var _a, _b;
    var id = null;
    if (dcs instanceof ChartImp) {
        id = dcs.id;
    }
    else {
        var dom = null;
        if (isString(dcs)) {
            dom = document.getElementById(dcs);
        }
        else {
            dom = dcs;
        }
        id = (_a = dom === null || dom === void 0 ? void 0 : dom.getAttribute('k-line-chart-id')) !== null && _a !== void 0 ? _a : null;
    }
    if (id !== null) {
        (_b = charts.get(id)) === null || _b === void 0 ? void 0 : _b.destroy();
        charts.delete(id);
    }
}
var utils = {
    clone: clone,
    merge: merge,
    isString: isString,
    isNumber: isNumber,
    isValid: isValid,
    isObject: isObject,
    isArray: isArray,
    isFunction: isFunction,
    isBoolean: isBoolean,
    formatValue: formatValue,
    formatPrecision: formatPrecision,
    formatBigNumber: formatBigNumber,
    formatDate: formatTimestampByTemplate,
    formatThousands: formatThousands,
    formatFoldDecimal: formatFoldDecimal,
    calcTextWidth: calcTextWidth,
    getLinearSlopeIntercept: getLinearSlopeIntercept,
    getLinearYFromSlopeIntercept: getLinearYFromSlopeIntercept,
    getLinearYFromCoordinates: getLinearYFromCoordinates,
    checkCoordinateOnArc: checkCoordinateOnArc,
    checkCoordinateOnCircle: checkCoordinateOnCircle,
    checkCoordinateOnLine: checkCoordinateOnLine,
    checkCoordinateOnPolygon: checkCoordinateOnPolygon,
    checkCoordinateOnRect: checkCoordinateOnRect,
    checkCoordinateOnText: checkCoordinateOnText
};

exports.dispose = dispose;
exports.getFigureClass = getFigureClass;
exports.getHotkey = getHotkey;
exports.getOverlayClass = getOverlayClass;
exports.getSupportedFigures = getSupportedFigures;
exports.getSupportedHotkeys = getSupportedHotkeys;
exports.getSupportedIndicators = getSupportedIndicators;
exports.getSupportedLocales = getSupportedLocales;
exports.getSupportedOverlays = getSupportedOverlays;
exports.init = init;
exports.registerFigure = registerFigure;
exports.registerHotkey = registerHotkey;
exports.registerIndicator = registerIndicator;
exports.registerLocale = registerLocale;
exports.registerOverlay = registerOverlay;
exports.registerStyles = registerStyles;
exports.registerXAxis = registerXAxis;
exports.registerYAxis = registerYAxis;
exports.utils = utils;
exports.version = version;

}));
//# sourceMappingURL=klinecharts.js.map
