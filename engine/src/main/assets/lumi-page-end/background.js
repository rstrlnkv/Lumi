// Relays the app's two layout numbers ({stripPx, viewWidthPx}) to every content script through storage.
const port = browser.runtime.connectNative("lumiPageEnd");
port.onMessage.addListener(m => browser.storage.local.set({ lumiPageEnd: m }));
