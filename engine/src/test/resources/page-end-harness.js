// Runs the page-end content script against a small fake page. Usage: node page-end-harness.js <content.js> '<steps json>'
// Each step patches the fake page, optionally delivers a storage change, and the script prints the value of
// --lumi-page-end on <html> (or the property a step names in "prop") after every step. The block feeds back into
// scrollHeight like in a browser. "delayGet" holds the initial storage read until a step sets "resolveGet".
// A change to <html style> reaches the MutationObserver after the frame unless the script took the records first;
// frames that keep scheduling frames make the harness fail; "loopFrames" adds such a frame.
// "nav" is an element stuck at the bottom ({position, width, height, bottom, bg, bgImage, visibility, opacity} and an
// optional "childBg": the hit is then its static child with that background). "advance" moves the fake clock by ms and
// runs the due timers. "prop":"observed" prints the MutationObserver targets (<html>, <body>) and option names. "noHit" makes elementFromPoint return null. "prop":"sent" prints the bottom-bar reports sent since the last such step, joined by commas;
// with "colors" each report also shows " b#rrggbb" (bottomColor) and " s#rrggbb" (statusColor). A report with any other
// key or value prints "bad". Site colours: "header" is an element stuck at the top ({position, width, height, top, bg,
// bgImage, visibility, opacity}); "bodyBg"/"htmlBg" are the backgrounds the colour walk reaches; "metas" are
// [{name, content, media}] for document.querySelectorAll; "dark" drives matchMedia("(prefers-color-scheme: dark|light)").
// "htmlScheme" is the computed color-scheme of <html>.
// "headMutation" notifies observers of <head>, "mediaChange" fires the matchMedia change listeners.
// "prop":"observedHead" prints the options of the <head> observers, "prop":"styles" the getComputedStyle calls and
// "prop":"topHits" the elementFromPoint calls in the upper half since the last such step.
// Icon links: "links" are [{rel, href, sizes, type}] for document.querySelectorAll("link[rel]"), "origin" is location.origin;
// "prop":"icons" prints the icon messages sent since the last such step as JSON with ' for ", joined by "|".
"use strict";
const fs = require("fs");
const vm = require("vm");
const [file, stepsJson] = process.argv.slice(2);
const steps = JSON.parse(stepsJson);
const S = { base: 2000, innerHeight: 800, clientWidth: 360, overflowRoot: "visible", overflowBody: "visible",
  top: true, hasBody: true, initialStrip: null, delayGet: false,
  bodyBg: "rgba(0, 0, 0, 0)", endBg: null, endReaches: true, emptyLast: false,
  nav: null, rejectSend: false, hidden: false, now: 0,
  header: null, htmlBg: "rgba(0, 0, 0, 0)", htmlScheme: "normal", metas: [], dark: false, colors: false, links: [], origin: "https://a.com" };
Object.assign(S, steps[0].init || {});
const props = {};
const raf = [];
const listeners = {};
let onChanged = null;
let resolveGet = null;
let mutations = 0;
const observers = [];
const mediaListeners = [];
let styles = 0;
let topHits = 0;
const sent = [];
const iconSent = [];
const observed = [];
const observedHead = [];
// A 2d context's fillStyle as a browser normalises it: opaque colours read back as #rrggbb, translucent ones as
// rgba(r, g, b, a); a value it cannot parse leaves the previous one.
const NAMED = { red: "#ff0000", white: "#ffffff", black: "#000000", transparent: "rgba(0, 0, 0, 0)" };
function canvas2d() {
  let fill = "#000000";
  return {
    get fillStyle() { return fill; },
    set fillStyle(v) {
      const s = String(v).trim().toLowerCase();
      let m;
      if (NAMED[s]) fill = NAMED[s];
      else if ((m = /^#([0-9a-f]{6})$/.exec(s))) fill = "#" + m[1];
      else if ((m = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/.exec(s))) fill = "#" + m[1] + m[1] + m[2] + m[2] + m[3] + m[3];
      else if ((m = /^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)$/.exec(s))) {
        const a = m[4] === undefined ? 1 : +m[4];
        fill = a >= 1 ? "#" + [m[1], m[2], m[3]].map(x => (+x).toString(16).padStart(2, "0")).join("")
          : `rgba(${m[1]}, ${m[2]}, ${m[3]}, ${a})`;
      }
    },
  };
}
const timers = [];
let applied = () => parseFloat(props["--lumi-page-end"]) || 0;
const root = {
  style: {
    getPropertyValue: n => props[n] || "",
    setProperty: (n, v) => { props[n] = v; mutations++; },
    removeProperty: n => { delete props[n]; mutations++; },
  },
  // The block is placed at --lumi-page-end-top, so the scroll height is wherever it ends, or the content end.
  get scrollHeight() { return Math.max(S.base, applied() ? (parseFloat(props["--lumi-page-end-top"]) || 0) + applied() : 0); },
  get clientWidth() { return S.clientWidth; },
  parentElement: null,
};
// The body may hold one box (the footer) with a background; it ends at the page end unless "endReaches" is false.
const end = { get children() { return []; }, getBoundingClientRect: () => ({ bottom: S.endReaches ? S.base : S.base - 20 }) };
// An empty box after it (Wikipedia's overlay container) has no height and must not count as the page end.
const empty = { get children() { return []; }, getBoundingClientRect: () => ({ bottom: S.base, height: 0 }) };
const body = {
  get children() { return (S.endBg ? [end] : []).concat(S.emptyLast ? [empty] : []); },
  getBoundingClientRect: () => ({ bottom: S.base }),
  parentElement: root,
};
const nav = { parentElement: body,
  getBoundingClientRect: () => ({ width: S.nav.width, height: S.nav.height, top: S.nav.bottom - S.nav.height, bottom: S.nav.bottom }) };
const header = { parentElement: body,
  getBoundingClientRect: () => ({ width: S.header.width, height: S.header.height, top: S.header.top, bottom: S.header.top + S.header.height }) };
const head = {};
const navChild = { parentElement: nav };
// The topmost element at a point: the nav (or its child) where it is, else the body.
function elementFromPoint(x, y) {
  const n = S.nav;
  if (S.noHit) return null;
  if (y < S.innerHeight / 2) topHits++;
  const hd = S.header;
  const hl = hd ? (S.clientWidth - hd.width) / 2 : 0;
  if (hd && x >= hl && x <= hl + hd.width && y >= hd.top && y <= hd.top + hd.height) return header;
  // "cover": [from, to] as fractions of the width: another element (here the body) is topmost there, like a floating button.
  if (n && n.cover && x >= n.cover[0] * S.clientWidth && x <= n.cover[1] * S.clientWidth) return S.hasBody ? body : root;
  const left = (S.clientWidth - (n ? n.width : 0)) / 2;
  if (n && x >= left && x <= left + n.width && y >= n.bottom - n.height && y <= n.bottom) {
    return n.childBg === undefined ? nav : navChild;
  }
  return S.hasBody ? body : root;
}
function style(el) {
  if (el === nav) return { position: S.nav.position, backgroundColor: S.nav.bg, backgroundImage: S.nav.bgImage,
    visibility: S.nav.visibility, opacity: S.nav.opacity, display: "block", overflowY: "visible" };
  if (el === header) return { position: S.header.position, backgroundColor: S.header.bg, backgroundImage: S.header.bgImage,
    visibility: S.header.visibility, opacity: S.header.opacity, display: "block" };
  if (el === navChild) return { position: "static", backgroundColor: S.nav.childBg, backgroundImage: "none",
    visibility: "visible", opacity: "1", display: "block" };
  return null;
}
const ctx = vm.createContext({
  document: { documentElement: root, get body() { return S.hasBody ? body : null; }, elementFromPoint,
    get hidden() { return S.hidden; }, get head() { return head; },
    querySelectorAll: sel => sel === 'meta[name="theme-color" i]'
      ? S.metas.filter(m => String(m.name).toLowerCase() === "theme-color").map(m => ({ getAttribute: n => (n in m ? m[n] : null) }))
      : sel === "link[rel]" ? S.links.map(l => ({ href: l.href, type: l.type || "",
        relList: { contains: t => String(l.rel).toLowerCase().split(/\s+/).includes(t) },
        sizes: String(l.sizes || "").split(/\s+/).filter(Boolean) }))
      : [],
    createElement: tag => (tag === "canvas" ? { getContext: () => canvas2d() } : null) },
  matchMedia: q => ({
    get matches() { return /dark/.test(q) ? S.dark : /light/.test(q) ? !S.dark : true; },
    addEventListener: (t, f) => { if (t === "change") mediaListeners.push(f); },
  }),
  get innerHeight() { return S.innerHeight; },
  get innerWidth() { return S.clientWidth; },
  getComputedStyle: (el, pseudo) => (styles++, style(el)) || (pseudo ? { height: applied() ? applied() + "px" : "auto" }
    : { overflowY: el === root ? S.overflowRoot : S.overflowBody, display: "block", position: "static",
        backgroundColor: el === end ? S.endBg : el === body ? S.bodyBg : el === root ? S.htmlBg : "rgba(0, 0, 0, 0)",
        colorScheme: el === root ? S.htmlScheme : "normal" }),
  requestAnimationFrame: f => { raf.push(f); },
  addEventListener: (t, f) => { (listeners[t] = listeners[t] || []).push(f); },
  ResizeObserver: class { observe() {} },
  MutationObserver: class {
    constructor(f) { this.f = f; this.targets = []; observers.push(this); }
    observe(t, o) {
      this.targets.push(t);
      (t === head ? observedHead : observed).push((t === root ? "root" : t === head ? "head" : "body") + ":" + Object.keys(o).sort().join("+") + (o.attributeFilter && t === head ? "=" + o.attributeFilter.join("+") : ""));
    }
    takeRecords() { mutations = 0; return []; }
  },
  browser: { storage: {
    local: { get: () => new Promise(r => {
      const settle = () => r(S.initialStrip ? { lumiPageEnd: S.initialStrip } : {});
      if (S.delayGet) resolveGet = settle; else settle();
    }) },
    onChanged: { addListener: f => { onChanged = f; } },
  }, runtime: { sendNativeMessage: (app, m) => {
    if (app === "lumiPageEnd" && "page" in m) { iconSent.push(JSON.stringify(m).replace(/"/g, "'")); return Promise.resolve(undefined); }
    const keys = Object.keys(m);
    const hex = v => typeof v === "string" && /^#[0-9a-f]{6}$/.test(v);
    const ok = app === "lumiPageEnd" && typeof m.bottomBar === "boolean"
      && keys.every(k => k === "bottomBar" || (k === "bottomColor" && m.bottomBar && hex(m[k])) || (k === "statusColor" && hex(m[k])));
    sent.push(!ok ? "bad" : String(m.bottomBar) + (S.colors && m.bottomColor ? " b" + m.bottomColor : "") + (S.colors && m.statusColor ? " s" + m.statusColor : ""));
    return S.rejectSend ? Promise.reject(new Error("no delegate")) : Promise.resolve(undefined);
  } } },
  setTimeout: (f, ms) => { const t = { at: S.now + ms, f }; timers.push(t); return t; },
  clearTimeout: t => { const i = timers.indexOf(t); if (i >= 0) timers.splice(i, 1); },
  Date: { now: () => S.now },
  location: { get origin() { return S.origin; } },
  parseFloat, Math, isFinite, getComputedStyleCalls: 0,
});
ctx.window = ctx;
ctx.top = S.top ? ctx : {};
vm.runInContext(fs.readFileSync(file, "utf8"), ctx);
const notify = t => observers.filter(o => o.targets.includes(t)).forEach(o => o.f([]));
const deliver = () => { if (mutations) { mutations = 0; notify(root); } };
(async () => {
  const out = [];
  await new Promise(r => setImmediate(r)); // the initial storage read settles first
  for (const step of steps) {
    Object.assign(S, step.patch || {});
    if ("strip" in step && onChanged) onChanged({ lumiPageEnd: { newValue: step.strip } }, "local");
    if (step.event) (listeners[step.event] || []).forEach(f => f());
    if (step.headMutation) notify(head);
    if (step.mediaChange) mediaListeners.forEach(f => f());
    if (step.advance) {
      S.now += step.advance;
      for (let t; (t = timers.filter(x => x.at <= S.now).sort((a, b) => a.at - b.at)[0]);) {
        timers.splice(timers.indexOf(t), 1);
        t.f();
      }
    }
    if (step.resolveGet && resolveGet) resolveGet();
    if (step.loopFrames) ctx.requestAnimationFrame(function again() { ctx.requestAnimationFrame(again); });
    await new Promise(r => setImmediate(r));
    for (let i = 0; i < 5 && raf.length; i++) raf.splice(0).forEach(f => { f(); deliver(); });
    if (raf.length) { console.log("frames never settle: each run schedules another"); process.exit(1); }
    const prop = step.prop || "--lumi-page-end";
    if (prop === "sent") out.push(sent.splice(0).join());
    else if (prop === "icons") out.push(iconSent.splice(0).join("|"));
    else if (prop === "observed") out.push(observed.join());
    else if (prop === "observedHead") out.push(observedHead.join());
    else if (prop === "styles") { out.push(String(styles)); styles = 0; }
    else if (prop === "topHits") { out.push(String(topHits)); topHits = 0; }
    else out.push(props[prop] === undefined ? "" : props[prop]);
  }
  console.log(JSON.stringify(out));
})();
