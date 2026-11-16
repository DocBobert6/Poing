## Poing! v0.1.0-alpha.26 — Tree strip

The twenty-sixth signed alpha of Poing!, the standalone Game Boy / Game Boy Color / Game Boy Advance emulator for Android (mGBA core, one app, arm64-v8a only).

**Install first — read this:** this alpha is signed with the same release key as alpha.1 through alpha.25, so it installs straight over alpha.25 — no uninstall needed, saves and settings carry over. It will **not** install over the debug builds (different key). Uninstall the debug build first. Uninstalling wipes local saves (battery saves + save states); RetroAchievements progress is server-side and safe.

## Download

The signed APK is attached to this release: `poing-v0.1.0-alpha.26.apk` (size + SHA-256 in the Build section below).

## What changed in alpha.26

- The repo no longer carries mGBA's test-suite ROMs: the `cinema/` directory is gone — 1,116 files, about 36 MB of blargg and acid test ROMs, baselines and configs that nothing in Poing ever ran.
- Upstream's own GitHub workflows are gone too (`.github/workflows/compilation.yml`, `crowdin_prep.yml`, `crowdin_translate.yml`). Poing's own CI workflow (`.github/workflows/android.yml`) stays — it is what builds and releases every alpha.
- The mGBA pin is unchanged at `7a12d6d4`; the tree is simply no longer verbatim. The core builds from the library sources only, exactly as before.
- Nothing else changed: no app code, no bridge code, no settings, no behaviour. The smaller repo is the whole change.
- What you should see: nothing different in the app — this one is under the hood.

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
