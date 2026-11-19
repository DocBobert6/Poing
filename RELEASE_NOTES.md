# Poing! v0.1.0-alpha.33 — Achievement Center build 2: HUD pin + rarity pop-up variants

The thirty-third signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.32, so it installs straight over alpha.32 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.33.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.33

Two Achievement Center pieces — the second of the three queued RA builds:

- **HUD pin — one achievement at a glance.** In Pause → Achievements, any locked achievement that has measured progress now has a **Pin** button next to Help. Pin one and a slim gold chip appears over the game showing that achievement's title and its live progress bar ("42/100"), updating as you play. One pin per game — pinning a second achievement replaces the first, and tapping the pinned row again unpins it. The chip is movable and resizable in the layout editor like every other control (it starts at the top-left, just below the top bar), and it is off by default: no pin, no chip. When the pinned achievement unlocks, the pin clears itself and the unlock popup carries the celebration.
- **Rarity pop-up variants.** An unlock popup now reflects how rare the achievement is — when the rarity is known. Under 10% of players gets a diamond marker (◆), its tier label ("Rare · 7.5%") and a gold border; under 5% gets all of that plus a larger badge. A common unlock looks exactly as before.
- **A different feel for rare unlocks.** Rare unlocks also feel different: one tick for a rare unlock, two ticks for an ultra-rare one, on top of whatever the pop itself does. Common unlocks play nothing extra. No new sounds — the distinction is in haptics, capped by your haptics level like everything else.
- **One thing to know:** the rarity variants need your web API key (Settings → RetroAchievements) — the same key the rarity lines in the achievements list already use. Without a key, popups stay plain gold. The rarity is now fetched when a game loads, so it is in hand before an unlock can pop.

## What is in it

- Library with game import (.gb / .gbc / .gba / .zip), BIOS card (BIOS optional, never gated), custom covers, and cover art from libretro-thumbnails with a visible fetch status line and Retry.
- Player: touch + gamepad, pause, save states with thumbnails + auto-save on quit, fast-forward / slow motion, rewind, cheats, per-game control layouts, playtime + Save Manager, input display, haptics, cartridge sensors, screenshot + video capture, Cartridge Surgery panel, themed fps counter (Dev stats, movable in the layout editor).
- RetroAchievements: login, Achievement Center (points, recent unlocks, per-game progress), badges, leaderboards, rich presence, in-game RA indicator, per-game library progress, rarity tiers and unlock history with a web API key, live progress bars, Search for Help, closest-first sort, HUD pin, rarity pop-up variants.
- Display: Jade (GB), Prism (GBC), Dusk (GBA) chains, each per handheld; edge smoothing per handheld as an alternative, never stacked. A shader-only reconstruction experiment hides behind seven taps on the Settings version footer, off by default.
- Settings → Updates: checks GitHub releases and, on your tap only, downloads and installs the next build.
- Licences screen lists every library in the app (mGBA MPL-2.0, rcheevos MIT, inih BSD-3-Clause, libretro header, fonts OFL, Kotlin/AndroidX Apache 2.0).

## Not in it yet

- Link cable (Phase 7 — deferred until after beta).
- No ROMs, BIOS files, or download links, ever. Poing! is not affiliated with or endorsed by Nintendo.
