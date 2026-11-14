// Sets --lumi-page-end on <html> so spacer.css ends a scrollable page with a block as tall as Lumi's strip.
// It also tells the app whether the page has a painted bar stuck to the screen bottom (so the app draws a solid band
// under its own bar) and, as "#rrggbb", that bar's colour and a colour for the status bar (the page's theme-color, else a header
// stuck to the top). It reads only layout, style and the theme-color meta, and sends nothing else anywhere.
"use strict";
(() => {
  if (window.top !== window) return;
  const root = document.documentElement;
  const NAME = "--lumi-page-end";
  const TOP = "--lumi-page-end-top";
  const COLOR = "--lumi-page-end-color";
  let strip = null;
  let stripChanged = false;
  let queued = false;
  let lastSent = null;
  let lastCheck = -Infinity;
  let trailing = null;
  let context = null;

  // The strip in CSS px: physical px scaled by the layout width (a desktop site lays out wider than the view).
  function spacerCssPx(stripPx, viewWidthPx, layoutWidthCss) {
    if (!(stripPx > 0 && viewWidthPx > 0 && layoutWidthCss > 0)) return 0;
    if (!isFinite(stripPx * layoutWidthCss / viewWidthPx)) return 0;
    return Math.ceil(stripPx * layoutWidthCss / viewWidthPx);
  }

  // Only a page taller than the screen gets the block; one-screen pages and pages that lock scrolling stay as they are.
  function shouldApply(base, viewport, scrollRootBlocked) {
    return !scrollRootBlocked && base > viewport + 1;
  }

  function scrollRootBlocked() {
    return [root, document.body].some(el => el && /^(hidden|clip)$/.test(getComputedStyle(el).overflowY));
  }

  // The colour the page ends in, so the block continues the page under the strip (Safari shows the page there too):
  // the deepest box that reaches the page end and paints a background. "" leaves the block see-through.
  function endColor() {
    let color = "";
    for (let el = document.body; el; el = lastBox(el)) {
      const bg = getComputedStyle(el).backgroundColor;
      if (colored(bg)) color = bg;
    }
    return color;
  }

  // A visible, painted fixed (or stuck sticky) box that spans most of the width and ends at the viewport bottom
  // (or, with [top], starts at its top), found by hit-testing three points just inside that edge and walking up from
  // the topmost element. Returns the first element on that path that paints (the source of the bar's colour) and the box.
  function stuck(top) {
    const h = innerHeight;
    for (const fx of [0.15, 0.5, 0.85]) {
      for (const dy of [1, 5]) {
        let paint = null;
        for (let el = document.elementFromPoint(innerWidth * fx, top ? dy : h - dy), i = 0; el && el !== root && i < 32; el = el.parentElement, i++) {
          const s = getComputedStyle(el);
          if (!paint && (colored(s.backgroundColor) || (!!s.backgroundImage && s.backgroundImage !== "none"))) paint = el;
          if (s.position !== "fixed" && s.position !== "sticky") continue;
          const r = el.getBoundingClientRect();
          const edge = top ? r.top >= -1 && r.top <= 4 : r.bottom >= h - 4 && r.bottom <= h + 1;
          if (paint && r.width >= 0.8 * root.clientWidth && r.height >= 16 && r.height <= 0.35 * h
            && edge && s.visibility === "visible" && parseFloat(s.opacity) >= 0.5) return { paint, box: el };
        }
      }
    }
    return null;
  }

  // A colour with alpha above 0, or one in a form parseRgb does not read ("rgb(255, 0, 0)" is red, not transparent).
  function colored(bg) {
    const c = parseRgb(bg);
    return c ? c.a > 0 : !!bg;
  }

  // A computed colour (rgb(), rgba(), the space syntax, #rrggbb) as {r, g, b, a}; null for any other form.
  function parseRgb(css) {
    if (typeof css !== "string") return null;
    let m = /^#([0-9a-f]{6})$/i.exec(css);
    if (m) { const n = parseInt(m[1], 16); return { r: n >> 16, g: (n >> 8) & 255, b: n & 255, a: 1 }; }
    m = /^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)\s*(?:[,/]\s*([\d.]+)(%?)\s*)?\)$/i.exec(css);
    if (!m) return null;
    const c = { r: +m[1], g: +m[2], b: +m[3], a: m[4] === undefined ? 1 : m[5] ? m[4] / 100 : +m[4] };
    return [c.r, c.g, c.b].every(v => v <= 255) && c.a <= 1 ? c : null;
  }

  // An opaque colour as "#rrggbb"; null for none or a translucent one.
  function toHex(c) {
    if (!c || c.a < 0.999) return null;
    return "#" + [c.r, c.g, c.b].map(v => Math.round(v).toString(16).padStart(2, "0")).join("");
  }

  // The colour [el] shows: its background blended over its ancestors' up to the first opaque one, and over the canvas
  // when none is: white, unless the page sets a color-scheme (the canvas may then be dark). Null when an image or
  // gradient is in the way or a colour cannot be read (painted, colour unknown).
  function surfaceColor(el) {
    const layers = [];
    for (let i = 0; el && i < 32; el = el.parentElement, i++) {
      const s = getComputedStyle(el);
      if (s.backgroundImage && s.backgroundImage !== "none") return null;
      const c = parseRgb(s.backgroundColor);
      if (!c) return null;
      if (c.a > 0) layers.push(c);
      if (c.a >= 0.999) break;
    }
    if (!layers.length || layers[layers.length - 1].a < 0.999) {
      if (!/^(normal)?$/.test(getComputedStyle(root).colorScheme || "")) return null;
      layers.push({ r: 255, g: 255, b: 255, a: 1 });
    }
    const color = layers.pop();
    for (let l; (l = layers.pop());) {
      for (const k of ["r", "g", "b"]) color[k] = l[k] * l.a + color[k] * (1 - l.a);
    }
    return toHex(color);
  }

  // Any CSS colour as "#rrggbb" through a detached canvas, which ignores a value it cannot parse: two different
  // previous values tell an ignored value from one equal to the previous. Null for invalid or translucent colours.
  function cssToHex(value) {
    if (typeof value !== "string" || !value.trim() || value.length > 100) return null;
    context = context || document.createElement("canvas").getContext("2d");
    if (!context) return null;
    for (const before of ["#010203", "#040506"]) {
      context.fillStyle = before;
      context.fillStyle = value;
      if (context.fillStyle !== before) return toHex(parseRgb(context.fillStyle));
    }
    return null;
  }

  // The first valid theme-color whose media query matches (the HTML rule); prefers-color-scheme follows the app theme.
  function themeColor() {
    for (const m of document.querySelectorAll('meta[name="theme-color" i]')) {
      const media = m.getAttribute("media");
      if (media && !matchMedia(media).matches) continue;
      const c = cssToHex(m.getAttribute("content"));
      if (c) return c;
    }
    return null;
  }

  function snapshot() {
    // A translucent bar shows the page through it: its colour is blended over the page behind it.
    const bar = stuck(false);
    const bottom = bar && surfaceColor(bar.paint);
    const msg = { bottomBar: !!bar };
    if (bottom) msg.bottomColor = bottom;
    let status = themeColor();
    if (!status) { const header = stuck(true); status = header && surfaceColor(header.paint); }
    if (status) msg.statusColor = status;
    return msg;
  }

  // Sends the report when it changed, or always when forced (a new or restored page: the app starts it empty).
  function report(force) {
    const msg = snapshot();
    const key = JSON.stringify(msg);
    if (!force && key === lastSent) return;
    lastSent = key;
    browser.runtime.sendNativeMessage("lumiPageEnd", msg).catch(() => { lastSent = null; });
  }

  // At most one check per 250 ms; a check that arrives sooner runs once at the end of that time.
  // A last check in the future means the wall clock stepped back; counting from now keeps the wait at most 250 ms.
  function check() {
    lastCheck = Math.min(lastCheck, Date.now());
    const wait = lastCheck + 250 - Date.now();
    if (wait > 0) {
      if (!trailing) trailing = setTimeout(() => { trailing = null; check(); }, wait);
      return;
    }
    lastCheck = Date.now();
    report(false);
  }

  // The last non-empty child in the flow, if it reaches the bottom of its parent.
  function lastBox(el) {
    const kids = el.children || [];
    for (let i = kids.length - 1; i >= 0; i--) {
      const s = getComputedStyle(kids[i]);
      if (s.display === "none" || s.position === "fixed" || s.position === "absolute") continue;
      const r = kids[i].getBoundingClientRect();
      if (r.height === 0) continue;
      return r.bottom >= el.getBoundingClientRect().bottom - 1 ? kids[i] : null;
    }
    return null;
  }

  function set(name, value) {
    if (root.style.getPropertyValue(name) === value) return;
    if (value) root.style.setProperty(name, value);
    else root.style.removeProperty(name);
  }

  function apply() {
    queued = false;
    // Where the content ends, measured without the block; taking it out and back within one task paints nothing.
    set(NAME, "");
    const base = root.scrollHeight;
    // innerHeight is the layout viewport: the bar sliding does not change it, a rotation does.
    const px = strip && shouldApply(base, innerHeight, scrollRootBlocked())
      ? spacerCssPx(strip.stripPx, strip.viewWidthPx, root.clientWidth) : 0;
    set(TOP, px > 0 ? base + "px" : "");
    set(NAME, px > 0 ? px + "px" : "");
    set(COLOR, px > 0 ? endColor() : "");
    attributes.takeRecords(); // our own changes to <html style> must not schedule another run
    check();
  }

  function schedule() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(apply);
  }

  function setStrip(value) {
    strip = value || null;
    schedule();
  }

  // A change that arrives first is newer than what the first read returns.
  browser.storage.local.get("lumiPageEnd").then(r => { if (!stripChanged) setStrip(r.lumiPageEnd); });
  browser.storage.onChanged.addListener((changes, area) => {
    if (area !== "local" || !changes.lumiPageEnd) return;
    stripChanged = true;
    setStrip(changes.lumiPageEnd.newValue);
  });
  addEventListener("DOMContentLoaded", schedule);
  addEventListener("load", schedule);
  for (const type of ["DOMContentLoaded", "load", "pageshow"]) addEventListener(type, () => report(true));
  // A nav mounted late by the page's own script.
  addEventListener("load", () => { setTimeout(check, 1000); setTimeout(check, 3000); });
  // A tab in the background gets no frames, so it reports again when it shows.
  addEventListener("visibilitychange", () => { if (!document.hidden) report(true); });
  addEventListener("popstate", check);
  // A page that changes its theme-color (an SPA route), and a switch between light and dark.
  addEventListener("DOMContentLoaded", () => {
    if (document.head) {
      new MutationObserver(check).observe(document.head,
        { childList: true, subtree: true, attributes: true, attributeFilter: ["content", "media", "name"] });
    }
  });
  matchMedia("(prefers-color-scheme: dark)").addEventListener("change", check);
  addEventListener("hashchange", check);
  addEventListener("resize", schedule);
  // Pages with `height: 100%` on <html> and <body> never resize as content grows; scrolling re-checks them.
  addEventListener("scroll", schedule, { passive: true });
  new ResizeObserver(schedule).observe(root);
  // A framework that rewrites <html style> drops the property; a scroll lock on <body> changes the rule.
  const attributes = new MutationObserver(schedule);
  attributes.observe(root, { attributes: true, attributeFilter: ["style", "class"] });
  addEventListener("DOMContentLoaded", () => {
    if (!document.body) return;
    attributes.observe(document.body, { attributes: true, attributeFilter: ["style", "class"], childList: true });
    new ResizeObserver(schedule).observe(document.body);
  });
})();
