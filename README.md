# Lumi

A Material 3 web browser for Android with a bottom address bar, gestures, tab groups and private tabs, built on GeckoView.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B%20arm64-lightgrey) ![Release](https://img.shields.io/github/v/release/rstrlnkv/lumi?include_prereleases) ![Downloads](https://img.shields.io/github/downloads/rstrlnkv/lumi/total) ![Licence](https://img.shields.io/badge/licence-MPL--2.0-blue)

**English** · [Русский](README.ru.md)

## Install

Download `Lumi-<version>.apk` from the [Releases](https://github.com/rstrlnkv/lumi/releases) page and
open it on the phone; Android asks once to allow installs from the app you opened it with. Each release
lists the file's SHA-256 under **Assets**, so you can check the download first:

```bash
shasum -a 256 Lumi-0.1.0-dev.1.apk
```

To install from a computer, connect the phone with USB debugging on and run:

```bash
adb install Lumi-0.1.0-dev.1.apk
```

There is no store listing yet. Builds marked `-dev.N` are prereleases for daily testing.

## Features

| Feature | What it does |
| --- | --- |
| Bottom address bar | The address field sits at the bottom, in two styles, Compact and Classic. It slides away as you scroll down and comes back when you scroll up or tap it. |
| Bar gestures | Swipe the bar left or right to switch to the next tab; swipe it up to open the tab grid. |
| Tab grid | Tabs are large cards. Swipe a card away to close it. Your tabs come back after a restart. |
| Tab groups | Gather tabs into named groups, shown as chips in the grid; the bar's swipes stay inside the current group. |
| Long-press menu | On a link or an image: open, open in a new or background tab, copy, share, save the image. |
| Tracker blocking | Strict Enhanced Tracking Protection, a shield that counts blocked trackers, and a per-site switch. |
| Private tabs | Leave no history, lock behind the phone's screen lock, and show a blank card in recent apps. |
| Library | History, bookmarks with folders, and a Downloads list. |
| Start page | Every new tab opens a grid of your favourite sites with their real icons. |
| Search | Google, DuckDuckGo, Yandex, Bing, Startpage or Ecosia, with suggestions as you type and completion of the address you start typing. |
| Downloads | A confirmation sheet, progress notifications, files saved to the system Downloads folder. |
| Default browser | Links and web searches from other apps open in Lumi, and Back returns to the app you came from. |
| Site colours | The status bar and the space under the bar take the page's colours; icons stay readable on them. |
| Pull to refresh | Pull the page down to reload it. |

The interface is in English and Russian and follows the phone's language, light or dark theme and
wallpaper colours (Dynamic Color).

## Requirements

- Android 8.0 (API 26) or later, on a 64-bit ARM phone (`arm64-v8a`). The release APK carries no
  other architecture.
- Permissions, from `app/src/main/AndroidManifest.xml`: `INTERNET`; `POST_NOTIFICATIONS`, asked when a
  download starts, for download progress; `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`,
  so a download continues when Lumi is in the background; `WRITE_EXTERNAL_STORAGE` on Android 9 and
  older only, to save a download.
- Network hosts Lumi itself reaches, besides the pages you open:
  - the search engine you choose, for searches, and for suggestions while "Search engine
    suggestions" is on: `www.google.com` and `suggestqueries.google.com`, `duckduckgo.com`,
    `yandex.ru` and `suggest.yandex.ru`, `www.bing.com` and `api.bing.com`, `www.startpage.com`,
    `www.ecosia.org` and `ac.ecosia.org`;
  - the `https` root, the icon links and `/favicon.ico` of each site on the start page, for its icon.
  GeckoView, the engine, also downloads its tracking-protection lists and Safe Browsing updates from
  Mozilla's and Google's services with its default settings, which Lumi does not change.
- Changes outside Lumi's own folders: downloaded files go to the system Downloads folder, and Lumi
  becomes the default browser only when you confirm the system's prompt. Lumi's data is excluded from
  cloud backup and device transfer.

## Build from source

You need JDK 21, an Android SDK (a `local.properties` with `sdk.dir=<path>`, gitignored) and
Node.js on `PATH` (the unit tests run the page-end content script under it). Gradle comes with the
wrapper.

```bash
./gradlew test lint        # always together: lint catches missing Russian strings
./gradlew assembleDebug    # APK in app/build/outputs/apk/debug/
./gradlew installDebug     # with a phone or an emulator connected (adb devices)
./gradlew assembleRelease  # arm64 only; unsigned unless the LUMI_RELEASE_* Gradle properties are set
```

The version name is `lumi.version` in `gradle.properties`; the build number is the commit count, so
build from a full clone, not a shallow one. [`ARCHITECTURE.md`](ARCHITECTURE.md) describes how the
code is laid out.

## Licence

Mozilla Public License 2.0, see [`LICENSE`](LICENSE). The bundled icons come from Material Symbols
under the Apache License 2.0; see [`NOTICE.md`](NOTICE.md).
