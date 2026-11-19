# Poing! v0.1.0-alpha.31 — Achievement Center: live progress bars, Search for Help, closest-first sort

The thirty-first signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.30, so it installs straight over alpha.30 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.31.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.31

The first of the three Achievement Center builds — three small additions to Pause → Achievements, all using data the app already had:

- **Live progress bars.** Achievements that track progress (like "42/100 collectibles") now show a thin gold bar under the row, with the numbers beside it. Only achievements authored with progress get a bar — the rest look exactly as before, and no percentage is ever invented for them. The bars stay live: the emulator now forwards the progress-indicator event, so the list is fresh whenever you open it.
- **Search for Help.** Every locked achievement has a Help button. It opens a web search for the game and the achievement on RetroAchievements — a web search, so it finds text guides and videos both. If RA itself supplies a guide link for the game, that opens instead. No key needed, nothing is sent but the search you asked for.
- **Closest first.** A sort toggle at the top of the list: locked achievements closest to done first, unmeasured ones after them, unlocked ones last. Current game only — the library-wide half is not in this build.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence, in-game RA indicator, per-game library progress, rarity tiers and unlock history with a web API key, live progress bars, Search for Help, closest-first sort.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.
