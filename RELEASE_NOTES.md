## Poing! v0.1.0-alpha.25 — Unlock popup slim + movable

The twenty-fifth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.24, so it installs straight over alpha.24 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.25.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.25

- The achievement unlock notify is slim now: one line only — badge + "Achievement Unlocked: \<title\> (+N pts)". The description line drops out of the popup entirely (it still lives in Pause → Achievements and the hub, where it belongs), and the chip is capped at 75% of the game width, so it never spans the full game and covers the HUD the way it did.
- The popup is movable now: it is an overlay item, so you can drag it and resize it in the layout editor like any other control — per-system default plus per-game override, like the rest. A sample popup shows while editing so the position is visible. The default stays top-centre.
- It floats over the game now, in the overlay layer, so while it is up it no longer pushes the game down or makes it smaller — the same rule as the alpha.24 chip fix.
- Nothing else changed: the 4-second auto-clear, the badge, the gold chip style, the scoreboard note, the RA chip and the tracker line are untouched.
- What you should see: unlock an achievement and the notify is a single slim chip, not a wide two-liner; move it in the editor and it stays where you put it.

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
