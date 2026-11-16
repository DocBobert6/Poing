## Poing! v0.1.0-alpha.21 — The fetch-on-open diagnostic

The twenty-first signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.20, so it installs straight over alpha.20 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.21.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.21

- The library status line now names the game it is fetching cover art for, instead of only naming the system. When a fetch runs you will see "fetching art for …" with the game's name; if several are fetching at once, the first three are named.
- If a game is still missing its cover after a fetch finishes, the status bar stays visible and names it ("still missing: …") instead of hiding after four seconds — so the answer is still there to read after the pass, not only while it runs. The bar hides again once every game has reached a final state.
- This is a diagnostic build: it changes only what the status line reports, not the fetch itself. The investigation behind it found no code path that fetches on open with an unchanged library — so the fetch that still shows on open must be a game that never reaches a final state on the device, and this build identifies which one.
- Nothing else changed: the haptics (including the alpha.17 pair), the steering and Worm Light tilt tuning, the dead zone, the layout editor minimize, the cheat screen, cover fetching, and RA are untouched.
- What you should see: open the library and, if a fetch runs, the status line names the game it is fetching for. If a game is still missing its cover when the pass ends, the bar stays up with its name — tell me the name and that is the game to fix next.

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
