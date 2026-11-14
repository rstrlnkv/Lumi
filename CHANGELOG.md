# Changelog

All notable changes to Lumi are written here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- The app icon has a smaller disk and a larger flash; the themed icon shows the flash alone.
- Lumi's native libraries are no longer unpacked when the app is installed.

### Fixed
- On a site with its own bar stuck to the bottom of the screen, the Compact bar now has a solid background,
  so that bar no longer shows through between Lumi's buttons.

## [0.1.0-dev.1] - 2026-11-14

### Added
- A bottom address bar in two styles, Compact and Classic, that slides away as you scroll and comes
  back when you scroll up or tap it.
- Tabs with a grid of large cards: swipe the bar to switch to the next tab, swipe up for the grid,
  swipe a card away to close it. Your tabs come back after a restart.
- Tab groups, with group chips in the grid and the bar's swipes kept inside the current group.
- A long-press menu for links and images: open, open in a new or background tab, copy, share and
  save the image.
- Tracker blocking, with a shield that counts blocked trackers and a per-site switch.
- Private tabs that leave no history, are locked behind your phone's screen lock and show a blank
  card in recent apps.
- History, bookmarks with folders, and a Library screen for both.
- A start page on every new tab with a grid of your favorite sites and their real icons.
- A choice of search engine (Google, DuckDuckGo, Yandex, Bing, Startpage, Ecosia), search
  suggestions as you type, and completion of the address you start typing.
- Downloads with a confirmation sheet, progress notifications and a Downloads list in the Library.
- Lumi can be your default browser: links and web searches from other apps open in Lumi, and Back
  returns to the app you came from.
- Pull down to refresh the page; the end of every page stays reachable above the bar.
- A new app icon, a Russian translation, and an About Lumi screen with what's new and the licenses
  of the libraries Lumi uses.
