/* Trusted LNReader compatibility surface. Downloaded plugin code never gets host objects. */
(() => {
  'use strict';
  const vendor = globalThis.__suwayomiLnVendor;
  delete globalThis.__suwayomiLnVendor;
  if (!vendor) throw new Error('LNReader vendor bundle is unavailable');
  vendor.dayjs.extend((_option, Dayjs) => {
    const baseFormat = Dayjs.prototype.format;
    const formats = Object.freeze({
      LTS: 'h:mm:ss A',
      LT: 'h:mm A',
      L: 'MM/DD/YYYY',
      LL: 'MMMM D, YYYY',
      LLL: 'MMMM D, YYYY h:mm A',
      LLLL: 'dddd, MMMM D, YYYY h:mm A',
      l: 'M/D/YYYY',
      ll: 'MMM D, YYYY',
      lll: 'MMM D, YYYY h:mm A',
      llll: 'ddd, MMM D, YYYY h:mm A',
    });
    Dayjs.prototype.format = function (format) {
      const localeFormats = this.$locale().formats || {};
      const expanded = (format || 'YYYY-MM-DDTHH:mm:ssZ').replace(/\[[^\]]+\]|LTS|LT|LLLL|LLL|LL|L|llll|lll|ll|l/g, token => {
        if (token[0] === '[') return token;
        return localeFormats[token] || formats[token] || token;
      });
      return baseFormat.call(this, expanded);
    };
  });
  const statuses = Object.freeze({ Unknown: 'Unknown', Ongoing: 'Ongoing', Completed: 'Completed', Licensed: 'Licensed', PublishingFinished: 'Publishing Finished', Cancelled: 'Cancelled', OnHiatus: 'On Hiatus', STUB: 'STUB', Inactive: 'Inactive' });
  const filterTypes = Object.freeze({ TextInput: 'Text', Picker: 'Picker', CheckboxGroup: 'Checkbox', Switch: 'Switch', ExcludableCheckboxGroup: 'XCheckbox' });
  const defaultCover = 'https://github.com/LNReader/lnreader-plugins/blob/main/icons/src/coverNotAvailable.jpg?raw=true';
  const defaultHeaders = Object.freeze({ Connection: 'keep-alive', Accept: '*/*', 'Accept-Language': '*', 'Sec-Fetch-Mode': 'cors', 'Accept-Encoding': 'gzip, deflate', 'Cache-Control': 'max-age=0' });
  const b64chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const toBase64 = bytes => { let output = ''; for (let i = 0; i < bytes.length; i += 3) { const a = bytes[i]; const b = bytes[i + 1]; const c = bytes[i + 2]; output += b64chars[a >> 2] + b64chars[((a & 3) << 4) | (b === undefined ? 0 : b >> 4)] + (b === undefined ? '=' : b64chars[((b & 15) << 2) | (c === undefined ? 0 : c >> 6)]) + (c === undefined ? '=' : b64chars[c & 63]); } return output; };
  const fromBase64 = value => { const clean = String(value).replace(/\s/g, ''); if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(clean)) throw new Error('Invalid base64 response'); const bytes = []; for (let i = 0; i < clean.length; i += 4) { const a = b64chars.indexOf(clean[i]); const b = b64chars.indexOf(clean[i + 1]); const c = clean[i + 2] === '=' ? -1 : b64chars.indexOf(clean[i + 2]); const d = clean[i + 3] === '=' ? -1 : b64chars.indexOf(clean[i + 3]); bytes.push((a << 2) | (b >> 4)); if (c >= 0) bytes.push(((b & 15) << 4) | (c >> 2)); if (d >= 0) bytes.push(((c & 3) << 6) | d); } return Uint8Array.from(bytes); };
  if (!globalThis.btoa) globalThis.btoa = value => toBase64(Uint8Array.from(String(value), character => character.charCodeAt(0)));
  if (!globalThis.atob) globalThis.atob = value => { const bytes = fromBase64(value); const chunks = []; for (let i = 0; i < bytes.length; i += 0x4000) chunks.push(String.fromCharCode(...bytes.subarray(i, i + 0x4000))); return chunks.join(''); };
  if (!globalThis.URL) globalThis.URL = vendor.url.URL;
  if (!globalThis.URLSearchParams) globalThis.URLSearchParams = vendor.url.URLSearchParams;

  class LnHeaders {
    constructor(initial = {}) { this.map = new Map(); if (initial instanceof LnHeaders) initial.forEach((value, name) => this.set(name, value)); else if (initial != null && typeof initial[Symbol.iterator] === 'function') { for (const pair of initial) { const values = Array.from(pair); if (values.length !== 2) throw new TypeError('Each header pair must contain exactly two items'); this.set(values[0], values[1]); } } else Object.entries(initial).forEach(([name, value]) => this.set(name, value)); }
    get(name) { return this.map.get(String(name).toLowerCase())?.[1] ?? null; }
    has(name) { return this.map.has(String(name).toLowerCase()); }
    set(name, value) { const text = String(name); this.map.set(text.toLowerCase(), [text, String(value)]); }
    append(name, value) { this.set(name, this.has(name) ? `${this.get(name)}, ${value}` : value); }
    delete(name) { this.map.delete(String(name).toLowerCase()); }
    entries() { return this.map.values(); }
    forEach(callback) { this.map.forEach(([name, value]) => callback(value, name, this)); }
    [Symbol.iterator]() { return this.entries(); }
  }

  class LnFormData {
    constructor() { this.parts = []; }
    append(name, value) { this.parts.push({ name: String(name), value: String(value) }); }
    entries() { return this.parts.map(({ name, value }) => [name, value])[Symbol.iterator](); }
    [Symbol.iterator]() { return this.entries(); }
  }

  class LnResponse {
    constructor(data, decode) { this.status = data.status; this.statusText = data.statusText; this.url = data.url; this.headers = new LnHeaders(data.headers); this.ok = this.status >= 200 && this.status < 300; this.bodyBase64 = data.bodyBase64; this.bytes = fromBase64(data.bodyBase64); this.decode = decode; }
    async arrayBuffer() { return this.bytes.buffer.slice(this.bytes.byteOffset, this.bytes.byteOffset + this.bytes.byteLength); }
    async text(encoding) { return this.decode(this.bodyBase64, encoding || this.charset() || 'utf-8'); }
    async json() { return JSON.parse(await this.text()); }
    charset() {
      const match = this.headers.get('content-type')?.match(/(?:^|[;\s])charset\s*=\s*(?:["']\s*([^"';\s]+)\s*["']|([^"';\s]+))/i);
      return (match?.[1] ?? match?.[2])?.trim() || undefined;
    }
  }

  const absoluteUrl = value => { if (!value) return false; if (value.indexOf('//') === 0) return true; if (value.indexOf('://') === -1 || value.indexOf('.') === -1 || value.indexOf('/') === -1 || value.indexOf(':') > value.indexOf('/')) return false; return value.indexOf('://') < value.indexOf('.'); };
  const objectHeaders = value => value instanceof LnHeaders ? Object.fromEntries(value.entries()) : value && typeof value === 'object' ? Object.fromEntries(Object.entries(value).map(([name, item]) => [name, String(item)])) : {};
  const addDefaults = value => { const headers = objectHeaders(value); for (const [name, item] of Object.entries(defaultHeaders)) if (!Object.keys(headers).some(key => key.toLowerCase() === name.toLowerCase())) headers[name] = item; return headers; };
  const refererFor = (url, init) => {
    if (!init.referrer || init.referrer === 'about:client') return undefined;
    const source = new URL(String(init.referrer));
    const target = new URL(String(url));
    if (!['http:', 'https:'].includes(source.protocol)) return undefined;
    source.username = ''; source.password = ''; source.hash = '';
    const sameOrigin = source.origin === target.origin;
    const downgrade = source.protocol === 'https:' && target.protocol !== 'https:';
    const policy = ['no-referrer', 'no-referrer-when-downgrade', 'same-origin', 'origin', 'strict-origin', 'origin-when-cross-origin', 'strict-origin-when-cross-origin', 'unsafe-url'].includes(init.referrerPolicy) ? init.referrerPolicy : 'strict-origin-when-cross-origin';
    if (policy === 'no-referrer' || (policy === 'same-origin' && !sameOrigin) || (downgrade && ['no-referrer-when-downgrade', 'strict-origin', 'strict-origin-when-cross-origin'].includes(policy))) return undefined;
    const originOnly = policy === 'origin' || policy === 'strict-origin' || (!sameOrigin && ['origin-when-cross-origin', 'strict-origin-when-cross-origin'].includes(policy));
    return originOnly ? `${source.origin}/` : source.href;
  };
  const bodyForHost = body => {
    if (body === undefined || body === null) return undefined;
    if (body instanceof LnFormData) return { type: 'form', parts: body.parts };
    if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) return { type: 'text', value: body.toString(), contentType: 'application/x-www-form-urlencoded;charset=UTF-8' };
    if (typeof body === 'string') return { type: 'text', value: body };
    if (body instanceof Uint8Array) return { type: 'base64', value: toBase64(body) };
    if (body instanceof ArrayBuffer) return { type: 'base64', value: toBase64(new Uint8Array(body)) };
    throw new Error('Unsupported LNReader fetch body');
  };

  globalThis.Headers = LnHeaders;
  globalThis.FormData = LnFormData;
  globalThis.Response = LnResponse;
  globalThis.__suwayomiLnInitialize = (pluginCode, host) => {
    const hostJson = (operation, payload = {}) => JSON.parse(host.invoke(operation, JSON.stringify(payload)));
    let timerSequence = 0;
    const activeTimers = new Map();
    globalThis.setTimeout = (callback, ms = 0, ...args) => {
      const id = ++timerSequence;
      const delay = Math.max(0, Math.floor(Number(ms) || 0));
      activeTimers.set(id, { callback, args, delay });
      Promise.resolve().then(() => {
        const timer = activeTimers.get(id);
        if (!timer) return;
        activeTimers.delete(id);
        if (timer.delay > 0) hostJson('sleep', { ms: timer.delay });
        if (typeof timer.callback === 'function') {
          timer.callback(...timer.args);
        } else if (typeof timer.callback === 'string') {
          (0, eval)(timer.callback);
        }
      });
      return id;
    };
    globalThis.clearTimeout = id => {
      activeTimers.delete(id);
    };
    const request = (url, init = {}) => {
      const headers = addDefaults(init.headers);
      if (!Object.keys(headers).some(name => name.toLowerCase() === 'referer')) {
        const referer = refererFor(url, init);
        if (referer) headers.Referer = referer;
      }
      const body = bodyForHost(init.body);
      if (body?.contentType && !Object.keys(headers).some(name => name.toLowerCase() === 'content-type')) headers['Content-Type'] = body.contentType;
      return new LnResponse(hostJson('fetch', { url: String(url), method: init.method || 'GET', headers, body }), (bodyBase64, encoding) => hostJson('decode', { bodyBase64, encoding }).text);
    };
    const fetchApi = async (url, init) => request(url, init);
    const fetchFile = async (url, init) => { try { const response = request(url, init); return response.ok ? response.bodyBase64 : ''; } catch (_) { return ''; } };
    const fetchText = async (url, init, encoding) => { try { const response = request(url, init); return response.ok ? await response.text(encoding) : ''; } catch (_) { return ''; } };
    const fetchProto = async (protoInit, url, init) => {
      if (typeof protoInit?.proto !== 'string' || protoInit.proto.length > 262144) throw new Error('Invalid LNReader protobuf schema');
      const root = vendor.protobuf.parse(protoInit.proto).root;
      const requestType = root.lookupType(protoInit.requestType);
      if (requestType.verify(protoInit.requestData || {})) throw new Error('Invalid LNReader protobuf request');
      const encoded = requestType.encode(protoInit.requestData || {}).finish();
      if (encoded.length > 524283) throw new Error('LNReader protobuf request is too large');
      const body = new Uint8Array(encoded.length + 5); body[0] = 0; body[1] = encoded.length >>> 24; body[2] = encoded.length >>> 16; body[3] = encoded.length >>> 8; body[4] = encoded.length; body.set(encoded, 5);
      const response = request(url, { ...init, method: 'POST', body });
      const payload = new Uint8Array(await response.arrayBuffer());
      if (payload.length < 5) throw new Error('Invalid LNReader protobuf response');
      const length = ((payload[1] << 24) >>> 0) | (payload[2] << 16) | (payload[3] << 8) | payload[4];
      if (length < 0 || length > payload.length - 5) throw new Error('Invalid LNReader protobuf response length');
      return root.lookupType(protoInit.responseType).decode(payload.slice(5, 5 + length));
    };
    const storage = Object.freeze({
      set: (key, value, expires) => hostJson('storage.set', { key: String(key), value, ...(expires === undefined ? {} : { expires: Number(expires instanceof Date ? expires.getTime() : expires) }) }),
      get: (key, raw = false) => { const result = hostJson('storage.get', { key: String(key), raw: Boolean(raw) }); if (!result.present) return undefined; if (raw && result.value?.created !== undefined) result.value.created = new Date(result.value.created); return result.value; },
      delete: key => hostJson('storage.delete', { key: String(key) }),
      clearAll: () => hostJson('storage.clear', {}),
      getAllKeys: () => hostJson('storage.keys', {}),
    });
    const webStorage = operation => Object.freeze({ get: () => { const result = hostJson(operation, {}); return result === null ? undefined : Object.freeze({ ...result }); } });
    const packages = Object.freeze({
      cheerio: vendor.cheerio,
      htmlparser2: vendor.htmlparser2,
      dayjs: vendor.dayjs,
      urlencode: vendor.urlencode,
      '@libs/novelStatus': Object.freeze({ NovelStatus: statuses }),
      '@libs/fetch': Object.freeze({ fetchApi, fetchFile, fetchText, fetchProto }),
      '@libs/isAbsoluteUrl': Object.freeze({ isUrlAbsolute: absoluteUrl }),
      '@libs/filterInputs': Object.freeze({ FilterTypes: filterTypes }),
      '@libs/defaultCover': Object.freeze({ defaultCover }),
      '@libs/storage': Object.freeze({ storage, localStorage: webStorage('webStorage.local'), sessionStorage: webStorage('webStorage.session') }),
      '@libs/aes': vendor.aes,
      '@libs/utils': vendor.utils,
      '@/types/constants': Object.freeze({ NovelStatus: statuses, defaultCover }),
    });
    const require = name => { const module = packages[name]; if (!module) throw new Error(`Unsupported LNReader CommonJS package: ${name}`); return module; };
    const strings = value => Array.isArray(value) && value.every(item => typeof item === 'string');
    const options = (name, value) => { if (!Array.isArray(value) || value.some(item => !item || typeof item.label !== 'string' || typeof item.value !== 'string')) throw new Error(`LNReader ${name} has invalid options`); return value; };
    const filter = (name, value) => {
      if (!value || typeof value.label !== 'string') throw new Error(`LNReader filter ${name} is invalid`);
      const type = value.type;
      if ((type === 'Text' || type === 'TextInput') && typeof value.value === 'string') return;
      if (type === 'Switch' && (typeof value.value === 'boolean' || typeof value.value === 'string')) return;
      if (type === 'Picker' && typeof value.value === 'string') { options(`filter ${name}`, value.options); return; }
      if ((type === 'Checkbox' || type === 'CheckboxGroup') && (strings(value.value) || typeof value.value === 'string')) { options(`filter ${name}`, value.options); return; }
      if ((type === 'XCheckbox' || type === 'ExcludableCheckboxGroup') && value.value && typeof value.value === 'object' && (value.value.include === undefined || strings(value.value.include)) && (value.value.exclude === undefined || strings(value.value.exclude))) { options(`filter ${name}`, value.options); return; }
      throw new Error(`LNReader filter ${name} has an unsupported documented type`);
    };
    const setting = (name, value) => {
      if (!value || typeof value.label !== 'string') throw new Error(`LNReader setting ${name} is invalid`);
      const type = value.type || 'Text';
      if (type === 'Text' && (typeof value.value === 'string' || typeof value.value === 'boolean' || typeof value.value === 'number')) return;
      if (type === 'Switch' && (typeof value.value === 'boolean' || typeof value.value === 'string')) return;
      if (type === 'Select' && typeof value.value === 'string') { const choices = options(`setting ${name}`, value.options); if (!choices.some(item => item.value === value.value)) throw new Error(`LNReader setting ${name} has an invalid value`); return; }
      if (type === 'CheckboxGroup' && (strings(value.value) || typeof value.value === 'string')) { const choices = options(`setting ${name}`, value.options); return; }
      throw new Error(`LNReader setting ${name} has an unsupported documented type`);
    };
    const imageRequestInit = value => {
      if (!value || typeof value !== 'object' || Array.isArray(value) || (value.headers !== undefined && (typeof value.headers !== 'object' || Array.isArray(value.headers) || Object.values(value.headers).some(header => typeof header !== 'string'))) || (value.method !== undefined && typeof value.method !== 'string') || (value.body !== undefined && typeof value.body !== 'string')) throw new Error('LNReader plugin has invalid imageRequestInit');
    };
    globalThis.fetch = fetchApi;
    const module = { exports: {} };
    new Function('module', 'exports', 'require', pluginCode)(module, module.exports, require);
    const exported = module.exports.default ?? module.exports;
    const plugin = typeof exported === 'function' ? new exported() : exported;
    if (!plugin || typeof plugin !== 'object') throw new Error('LNReader plugin did not export an object');
    for (const name of ['id', 'name', 'icon', 'version']) if (typeof plugin[name] !== 'string') throw new Error(`LNReader plugin has invalid required metadata: ${name}`);
    if (plugin.site !== undefined && plugin.site !== null && typeof plugin.site !== 'string') throw new Error('LNReader plugin has invalid site');
    for (const name of ['popularNovels', 'parseNovel', 'parseChapter', 'searchNovels']) if (typeof plugin[name] !== 'function') throw new Error(`LNReader plugin is missing required method: ${name}`);
    if (plugin.imageRequestInit !== undefined) imageRequestInit(plugin.imageRequestInit);
    if (plugin.filters !== undefined) { if (!plugin.filters || typeof plugin.filters !== 'object' || Array.isArray(plugin.filters)) throw new Error('LNReader plugin has invalid filters'); Object.entries(plugin.filters).forEach(([name, value]) => filter(name, value)); }
    if (plugin.pluginSettings !== undefined) { if (!plugin.pluginSettings || typeof plugin.pluginSettings !== 'object' || Array.isArray(plugin.pluginSettings)) throw new Error('LNReader plugin has invalid pluginSettings'); Object.entries(plugin.pluginSettings).forEach(([name, value]) => setting(name, value)); }
    for (const name of ['customJS', 'customCSS']) if (plugin[name] !== undefined && typeof plugin[name] !== 'string') throw new Error(`LNReader plugin has invalid ${name}`);
    if (plugin.webStorageUtilized !== undefined && typeof plugin.webStorageUtilized !== 'boolean') throw new Error('LNReader plugin has invalid webStorageUtilized');
    for (const name of ['resolveUrl', 'parsePage']) if (plugin[name] !== undefined && typeof plugin[name] !== 'function') throw new Error(`LNReader plugin has invalid ${name}`);
    const cloneFilterValue = val => {
      if (val === undefined || val === null) return val;
      if (typeof val === 'object') {
        try {
          return JSON.parse(JSON.stringify(val));
        } catch (_) {
          return val;
        }
      }
      return val;
    };
    const buildOptions = rawOptions => {
      const opts = rawOptions ? { ...rawOptions } : {};
      if (plugin.filters && typeof plugin.filters === 'object' && !Array.isArray(plugin.filters)) {
        const defaultFilters = {};
        for (const [key, filterDef] of Object.entries(plugin.filters)) {
          if (filterDef && typeof filterDef === 'object' && !Array.isArray(filterDef)) {
            let defaultValue = filterDef.value;
            if (defaultValue === undefined || defaultValue === null) {
              if (filterDef.type === 'Picker') defaultValue = (filterDef.options && filterDef.options[0] && filterDef.options[0].value) || '';
              else if (filterDef.type === 'Text' || filterDef.type === 'TextInput') defaultValue = '';
              else if (filterDef.type === 'Switch') defaultValue = false;
              else if (filterDef.type === 'Checkbox' || filterDef.type === 'CheckboxGroup') defaultValue = [];
              else if (filterDef.type === 'XCheckbox' || filterDef.type === 'ExcludableCheckboxGroup') defaultValue = { include: [], exclude: [] };
              else defaultValue = '';
            }
            defaultFilters[key] = {
              type: filterDef.type,
              value: cloneFilterValue(defaultValue),
            };
          }
        }
        const userFilters = (opts.filters && typeof opts.filters === 'object' && !Array.isArray(opts.filters)) ? opts.filters : {};
        const mergedFilters = { ...defaultFilters };
        for (const [key, userFilter] of Object.entries(userFilters)) {
          if (userFilter && typeof userFilter === 'object' && !Array.isArray(userFilter)) {
            mergedFilters[key] = {
              type: userFilter.type || defaultFilters[key]?.type || 'Text',
              value: userFilter.value !== undefined ? cloneFilterValue(userFilter.value) : defaultFilters[key]?.value,
            };
          } else {
            mergedFilters[key] = userFilter;
          }
        }
        opts.filters = mergedFilters;
      } else {
        delete opts.filters;
      }
      return opts;
    };
    const methods = Object.freeze({
      popularNovels: args => plugin.popularNovels(args.page, buildOptions(args.options)),
      latestNovels: args => plugin.popularNovels(args.page, buildOptions({ ...(args.options || {}), showLatestNovels: true })),
      parseNovel: args => plugin.parseNovel(args.path),
      parseChapter: args => plugin.parseChapter(args.path),
      searchNovels: args => plugin.searchNovels(args.term, args.page),
      parsePage: args => { if (!plugin.parsePage) throw new Error('LNReader plugin does not implement parsePage'); return plugin.parsePage(args.path, args.page); },
      hasParsePage: () => typeof plugin.parsePage === 'function',
      resolveUrl: args => plugin.resolveUrl?.(args.path, args.isNovel) ?? null,
      filters: () => plugin.filters ?? null,
      pluginSettings: () => plugin.pluginSettings ?? null,
      imageRequestInit: () => {
        if (!plugin.imageRequestInit) return null;
        const headers = plugin.imageRequestInit.headers ? { ...plugin.imageRequestInit.headers } : {};
        return { ...plugin.imageRequestInit, headers };
      },
    });
    globalThis.__suwayomiLnValidate = () => JSON.stringify({ id: plugin.id, name: plugin.name, icon: plugin.icon, site: plugin.site ?? '', version: plugin.version, webStorageUtilized: plugin.webStorageUtilized ?? false, hasParsePage: typeof plugin.parsePage === 'function' });
    globalThis.__suwayomiLnDispatch = (operation, requestJson) => {
      if (operation === '__validate') return Promise.resolve(globalThis.__suwayomiLnValidate());
      const method = methods[operation]; if (!method) throw new Error(`Unsupported LNReader plugin operation: ${operation}`);
      return Promise.resolve(method(JSON.parse(requestJson))).then(value => JSON.stringify(value));
    };
  };
})();
