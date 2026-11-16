## Poing! v0.1.0-alpha.9 — Handheld-page fix + custom covers + cover-art cleanup

The ninth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.8, so it installs straight over alpha.8 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.9.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.9

- Fixed: the per-system Handheld pages no longer render over the main Settings list. Both layers were translucent, so every page row overlapped a main-list row and the screen was unreadable. Opening a Handheld page now shows only that page; Back returns to the main list in the same place.
- Custom covers: every game row and grid cell has a Cover button. Pick any image from the photo picker and it becomes that game's cover — including games with no boxart to fetch (fan translations, hacks), which is the point of it. The image is downscaled to 512px and stored separately from fetched art, so a re-fetch can never replace it. Removing a custom cover falls back to the fetched cover, or the placeholder when the game has none.
- The cover-art fetch trace is gone — the Trace button, its dialog, and the trace plumbing behind it, removed entirely. (With it gone there is no on-device diagnostic for covers any more.)
- Covers now fetch only when new games or a new folder are added. Which games have already been attempted is remembered, so opening the app a second time with no new games makes no network fetch at all — and a game with no match is not re-attempted either. A failed fetch still retries on demand with Retry.
- The library status bar hides itself a few seconds after the fetch finishes, so the library is clean. While a fetch runs, or while a failure needs attention, the bar stays visible so Retry is always reachable.
- What you should see now: Handheld pages show only the page; a Cover button on every game, set a cover on a game with no boxart and remove it again to see the fallback; no Trace button; no fetch on app open; the status bar gone a few seconds after the fetch.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.

## Build
