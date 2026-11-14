# Changelog

All notable changes to Lumi are written here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Start-page tiles show a site's real icon more often, the way Safari finds it: also icons the site keeps at its
  usual address without naming them, vector (SVG) icons, and icons that only appear once you open the site.
- A site that offers only a tiny icon gets a letter tile in the site's own colour on the start page, instead of a
  blurry little icon. Bookmark and History rows keep the small icon.
- A fresh Lumi starts with ten popular sites in Favorites (Google, YouTube, Wikipedia, Gmail,
  Amazon, Reddit, X, Instagram, GitHub and Netflix), so the start page is not empty. Delete the
  ones you do not want; they do not come back.
- Sites now ask before they look for or connect to devices on your home network or on your phone.
  You can allow or block each site.
- Lumi asks Android for the local network permission when a site needs it.
- A clear page when local network access is blocked.

### Changed
- Favorites icons are larger (64 dp) and sharper (192 px), and use the site's touch icon or web app
  manifest icon. Icons saved before refresh once.
- Site icons and search suggestions no longer reach devices on your own network.

### Fixed
- Site icons and search suggestions now load with a VPN or proxy that gives fake addresses.
- Sites you blocked from your local network before can ask again.

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
- A new app icon, whose themed version shows the flash alone, a Russian translation, and an About
  Lumi screen with what's new and the licenses of the libraries Lumi uses.
- On a page that sets a theme colour, or has a header stuck to the top of the screen, the status bar takes that
  colour, and its icons turn light or dark so they stay readable. This also works in private tabs.
- On a site with its own solid bar stuck to the bottom of the screen, the Compact bar gets a solid background, so
  that bar does not show through between Lumi's buttons; a see-through bar leaves the Compact bar see-through, as
  on any other page. The space under the Compact bar takes that bar's colour when Lumi can read a plain colour from
  it, and the system navigation buttons turn light or dark to stay readable on it; a solid bar whose colour Lumi
  cannot read (an image or a gradient) leaves that space in the theme colour.
- A page that ends in a colour such as yellow or red continues in that colour under the collapsed bar.
- Pages that follow the system light or dark theme switch with it at once, without restarting Lumi.
