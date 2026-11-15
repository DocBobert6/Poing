## Poing! v0.1.0-alpha.8 — Haptics rework + per-system settings

The eighth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1, alpha.2, alpha.3, alpha.4, alpha.5, alpha.6 and alpha.7, so it installs straight over alpha.7 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.8.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.8

- Every overlay button clicks now. L, R, Start and Select join the D-pad and A/B — they were silent before. The click is the system's own prebaked click (a scaled click primitive on newer phones), on press only, never on release.
- A Haptic strength slider in Settings, next to the haptics toggle: 0–100, 100 by default, global (strength is about your hand and phone, not the handheld). 0 silences the click, the same as the toggle off.
- Settings has a Handheld section — Game Boy, Game Boy Color, Game Boy Advance — each opening a small page holding exactly that handheld's settings: its display chain, edge smoothing (+ grid), Worm Light on/off, dimming on/off, and its default control layout. The old global edge / worm-light / dimming rows are gone from Display, which keeps only the Worm Light colour (one light, one colour).
- Nothing changes on install: each handheld's value starts from the current global value, so edge smoothing, Worm Light and dimming stay exactly as you had them until you change one handheld on its own.
- What you should see now: every button clicks, including L/R/Select/Start; the strength slider scales the click; Settings → Handheld → a page per system; your current settings carried over unchanged.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), and cover art from libretro-thumbnails with a visible fetch status line, Retry, and a fetch trace (Trace button in the status bar).
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — the fork decision for the core's link API is still open).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.

## Build
