// Runs the page-end content script against a small fake page. Usage: node page-end-harness.js <content.js> '<steps json>'
// Each step patches the fake page, optionally delivers a storage change, and the script prints the value of
// --lumi-page-end on <html> (or the property a step names in "prop") after every step. The block feeds back into
// scrollHeight like in a browser. "delayGet" holds the initial storage read until a step sets "resolveGet".
// A change to <html style> reaches the MutationObserver after the frame unless the script took the records first;
// frames that keep scheduling frames make the harness fail; "loopFrames" adds such a frame.
"use strict";
const fs = require("fs");
const vm = require("vm");
const [file, stepsJson] = process.argv.slice(2);
const steps = JSON.parse(stepsJson);
const S = { base: 2000, innerHeight: 800, clientWidth: 360, overflowRoot: "visible", overflowBody: "visible",
  top: true, hasBody: true, initialStrip: null, delayGet: false,
  bodyBg: "rgba(0, 0, 0, 0)", endBg: null, endReaches: true, emptyLast: false };
Object.assign(S, steps[0].init || {});
const props = {};
const raf = [];
const listeners = {};
let onChanged = null;
let resolveGet = null;
let mutations = 0;
let onMutation = null;
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
};
// The body may hold one box (the footer) with a background; it ends at the page end unless "endReaches" is false.
const end = { get children() { return []; }, getBoundingClientRect: () => ({ bottom: S.endReaches ? S.base : S.base - 20 }) };
// An empty box after it (Wikipedia's overlay container) has no height and must not count as the page end.
const empty = { get children() { return []; }, getBoundingClientRect: () => ({ bottom: S.base, height: 0 }) };
const body = {
  get children() { return (S.endBg ? [end] : []).concat(S.emptyLast ? [empty] : []); },
  getBoundingClientRect: () => ({ bottom: S.base }),
};
const ctx = vm.createContext({
  document: { documentElement: root, get body() { return S.hasBody ? body : null; } },
  get innerHeight() { return S.innerHeight; },
  getComputedStyle: (el, pseudo) => pseudo ? { height: applied() ? applied() + "px" : "auto" }
    : { overflowY: el === root ? S.overflowRoot : S.overflowBody, display: "block", position: "static",
        backgroundColor: el === end ? S.endBg : el === body ? S.bodyBg : "rgba(0, 0, 0, 0)" },
  requestAnimationFrame: f => { raf.push(f); },
  addEventListener: (t, f) => { (listeners[t] = listeners[t] || []).push(f); },
  ResizeObserver: class { observe() {} },
  MutationObserver: class { constructor(f) { onMutation = f; } observe() {} takeRecords() { mutations = 0; return []; } },
  browser: { storage: {
    local: { get: () => new Promise(r => {
      const settle = () => r(S.initialStrip ? { lumiPageEnd: S.initialStrip } : {});
      if (S.delayGet) resolveGet = settle; else settle();
    }) },
    onChanged: { addListener: f => { onChanged = f; } },
  } },
  parseFloat, Math, isFinite, getComputedStyleCalls: 0,
});
ctx.window = ctx;
ctx.top = S.top ? ctx : {};
vm.runInContext(fs.readFileSync(file, "utf8"), ctx);
const deliver = () => { if (mutations && onMutation) { mutations = 0; onMutation([]); } };
(async () => {
  const out = [];
  await new Promise(r => setImmediate(r)); // the initial storage read settles first
  for (const step of steps) {
    Object.assign(S, step.patch || {});
    if ("strip" in step && onChanged) onChanged({ lumiPageEnd: { newValue: step.strip } }, "local");
    if (step.event) (listeners[step.event] || []).forEach(f => f());
    if (step.resolveGet && resolveGet) resolveGet();
    if (step.loopFrames) ctx.requestAnimationFrame(function again() { ctx.requestAnimationFrame(again); });
    await new Promise(r => setImmediate(r));
    for (let i = 0; i < 5 && raf.length; i++) raf.splice(0).forEach(f => { f(); deliver(); });
    if (raf.length) { console.log("frames never settle: each run schedules another"); process.exit(1); }
    const prop = step.prop || "--lumi-page-end";
    out.push(props[prop] === undefined ? "" : props[prop]);
  }
  console.log(JSON.stringify(out));
})();
