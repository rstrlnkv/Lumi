// Sets --lumi-page-end on <html> so spacer.css ends a scrollable page with a block as tall as Lumi's strip.
// It reads only layout sizes and sends nothing anywhere.
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
      if (bg && !/,\s*0\)$/.test(bg)) color = bg;
    }
    return color;
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
  addEventListener("resize", schedule);
  // Pages with `height: 100%` on <html> and <body> never resize as content grows; scrolling re-checks them.
  addEventListener("scroll", schedule, { passive: true });
  new ResizeObserver(schedule).observe(root);
  // A framework that rewrites <html style> drops the property; a scroll lock on <body> changes the rule.
  const attributes = new MutationObserver(schedule);
  attributes.observe(root, { attributes: true, attributeFilter: ["style", "class"] });
  addEventListener("DOMContentLoaded", () => {
    if (!document.body) return;
    attributes.observe(document.body, { attributes: true, attributeFilter: ["style", "class"] });
    new ResizeObserver(schedule).observe(document.body);
  });
})();
