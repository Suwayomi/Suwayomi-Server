/* Browser primitives missing from GraalJS UNTRUSTED isolates. */
(() => {
  const bytesOf = value => value == null ? new Uint8Array() : value instanceof ArrayBuffer ? new Uint8Array(value) : ArrayBuffer.isView(value) ? new Uint8Array(value.buffer, value.byteOffset, value.byteLength) : Uint8Array.from(value);
  const encodeUtf8 = input => {
    const output = [], text = String(input);
    for (let index = 0; index < text.length; index += 1) {
      let code = text.charCodeAt(index);
      if (code >= 0xd800 && code <= 0xdbff && index + 1 < text.length && (text.charCodeAt(index + 1) & 0xfc00) === 0xdc00) code = 0x10000 + ((code & 0x3ff) << 10) + (text.charCodeAt(++index) & 0x3ff);
      else if (code >= 0xd800 && code <= 0xdfff) code = 0xfffd;
      if (code < 0x80) output.push(code);
      else if (code < 0x800) output.push(0xc0 | code >> 6, 0x80 | code & 0x3f);
      else if (code < 0x10000) output.push(0xe0 | code >> 12, 0x80 | code >> 6 & 0x3f, 0x80 | code & 0x3f);
      else output.push(0xf0 | code >> 18, 0x80 | code >> 12 & 0x3f, 0x80 | code >> 6 & 0x3f, 0x80 | code & 0x3f);
    }
    return Uint8Array.from(output);
  };
  const decodeUtf8 = (input, fatal) => {
    const bytes = bytesOf(input); let output = '';
    const invalid = () => { if (fatal) throw new TypeError('Invalid UTF-8'); return '\ufffd'; };
    for (let index = 0; index < bytes.length;) {
      const first = bytes[index++];
      if (first < 0x80) { output += String.fromCharCode(first); continue; }
      const extra = first >= 0xc2 && first <= 0xdf ? 1 : first >= 0xe0 && first <= 0xef ? 2 : first >= 0xf0 && first <= 0xf4 ? 3 : -1;
      if (extra < 0 || index + extra > bytes.length) { output += invalid(); continue; }
      let code = first & (extra === 1 ? 0x1f : extra === 2 ? 0x0f : 0x07), valid = true;
      for (let offset = 0; offset < extra; offset += 1) { const next = bytes[index++]; valid &&= (next & 0xc0) === 0x80; code = code << 6 | next & 0x3f; }
      if (!valid || code < (extra === 1 ? 0x80 : extra === 2 ? 0x800 : 0x10000) || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff)) output += invalid();
      else output += String.fromCodePoint(code);
    }
    return output;
  };
  if (!globalThis.TextEncoder) globalThis.TextEncoder = class { get encoding() { return 'utf-8'; } encode(input = '') { return encodeUtf8(input); } };
  if (!globalThis.TextDecoder) globalThis.TextDecoder = class { constructor(label = 'utf-8', options = {}) { this.latin1 = /^(latin1|iso-8859-1|windows-1252)$/i.test(label); this.utf16 = /^utf-16(?:le|be)?$/i.test(label); this.littleEndian = !/be$/i.test(label); if (!this.latin1 && !this.utf16 && !/^utf-?8$/i.test(label)) throw new RangeError(`Unsupported encoding: ${label}`); this.fatal = Boolean(options.fatal); } get encoding() { return this.latin1 ? 'windows-1252' : this.utf16 ? (this.littleEndian ? 'utf-16le' : 'utf-16be') : 'utf-8'; } decode(input) { const bytes = bytesOf(input); if (this.latin1) return Array.from(bytes, byte => String.fromCharCode(byte)).join(''); if (this.utf16) { let output = ''; for (let index = 0; index + 1 < bytes.length; index += 2) output += String.fromCharCode(this.littleEndian ? bytes[index] | bytes[index + 1] << 8 : bytes[index] << 8 | bytes[index + 1]); if (bytes.length % 2 && this.fatal) throw new TypeError('Invalid UTF-16'); return output; } return decodeUtf8(bytes, this.fatal); } };
  if (!globalThis.setTimeout) globalThis.setTimeout = (cb, ms, ...args) => { if (typeof cb === 'function') cb(...args); return 0; };
  if (!globalThis.clearTimeout) globalThis.clearTimeout = () => {};
  if (!globalThis.setInterval) globalThis.setInterval = () => { throw new Error('setInterval is not permitted in LNReader plugins'); };
  if (!globalThis.clearInterval) globalThis.clearInterval = () => {};
})();
