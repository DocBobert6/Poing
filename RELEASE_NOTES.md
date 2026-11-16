## Poing! v0.1.0-alpha.27 — D-pad envelope split

The twenty-seventh signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.26, so it installs straight over alpha.26 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.27.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.27

- The D-pad's haptics are split by how you roll it. Pressing a direction from neutral — any direction — is the envelope click again, the softer one from alpha.15 (0.75× resonant sharpness, weaker than A/B). Rolling to a straight direction (up, down, left, right) is the envelope click too.
- Rolling to a diagonal is the plain tick at 0.6 — the regular haptic click, not the envelope.
- The release tick is unchanged: 0.5, and it only fires when you lift off completely, never on a roll.
- The joystick is untouched — it stays on the plain tick.
- Nothing else changed: the A/B click, the Start/Select envelope, the L/R envelope + thud, the strength slider, proportional steering, the dead zone, and all the wiring are untouched.
- What you should feel: pressing the D-pad or rolling it straight should feel like the envelope's smoother click again; rolling through a diagonal should feel like the normal tick.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel.
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence, in-game RA indicator, per-game library progress, rarity tiers and unlock history with a web API key.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.
