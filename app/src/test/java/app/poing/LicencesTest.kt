package app.poing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 11 hardening (Step 3 of the alpha, 2026-11-15): the
 * Licences screen must list the ACTUAL licences in the app —
 * everything compiled into the APK, not just the headline ones.
 * Each entry below was established by direct evidence, not by
 * guessing at the dependency list:
 *
 * - mGBA core (MPL-2.0) — the pinned core tree, root LICENSE.
 * - inih (BSD-3-Clause) — compiled into the core .so: the top
 *   CMakeLists globs the inih C sources unconditionally, and
 *   `ini_parse_stream` is in the shipped .so's symbol table.
 * - libretro API header (MIT-style, RetroArch team) — the API
 *   the bridge talks through, src/platform/libretro/libretro.h.
 * - rcheevos (MIT) — the RetroAchievements client, compiled into
 *   the bridge from deps/rcheevos/.
 * - Fonts (SIL OFL 1.1) — DotGothic16, VT323, DSEG; full texts in
 *   assets/fonts/, pinned by PoingFontsTest too.
 * - Kotlin standard library + AndroidX / Jetpack Compose
 *   (Apache 2.0) — the app/build.gradle.kts implementation deps
 *   and the libraries they pull in.
 *
 * What is NOT in the app, so it is NOT listed here: mGBA's other
 * bundled third-party code (libpng, zlib, sqlite3, lzma,
 * discord-rpc) — none of their symbols are in the shipped core
 * .so and it links no system libz/libpng, so this build does not
 * compile them in. If a later build enables one, its licence
 * belongs here the same day.
 *
 * CI also diffs NOTICE against res/raw/notice.txt, so the screen
 * can never drift from the repo file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LicencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun notice(): String = loadNoticeText(context)

    @Test
    fun frontendIsGplAndNotAffiliatedWithNintendo() {
        val text = notice()
        assertTrue("frontend must be named GPL-3.0", "GNU General Public License v3.0" in text)
        assertTrue("the Nintendo disclaimer must be present",
            "not affiliated with or endorsed by Nintendo" in text)
    }

    @Test
    fun mgbaMplTextIsPresent() {
        val text = notice()
        assertTrue("mGBA must be named MPL-2.0", "Mozilla Public License Version 2.0" in text)
        assertTrue("the MPL full text must be present", "Exhibit A - Source Code Form License Notice" in text)
    }

    @Test
    fun rcheevosMitTextIsPresent() {
        val text = notice()
        assertTrue("rcheevos must be named", "rcheevos" in text)
        assertTrue("rcheevos MIT copyright must be present",
            "Copyright (c) 2018 RetroAchievements.org" in text)
    }

    @Test
    fun inihBsdTextIsPresent() {
        val text = notice()
        assertTrue("inih must be named", "inih" in text)
        assertTrue("inih BSD copyright must be present", "Copyright (c) 2009, Ben Hoyt" in text)
        assertTrue("inih BSD conditions must be present",
            "Neither the name of Ben Hoyt" in text)
    }

    @Test
    fun libretroHeaderMitTextIsPresent() {
        val text = notice()
        assertTrue("the libretro API header must be named", "libretro API" in text)
        assertTrue("the libretro copyright must be present",
            "Copyright (C) 2010-2024 The RetroArch team" in text)
    }

    @Test
    fun fontsAreNamedWithOfl() {
        val text = notice()
        assertTrue("the fonts must be named OFL", "SIL Open Font License" in text)
        for (font in listOf("DotGothic16", "VT323", "DSEG")) {
            assertTrue("NOTICE must name $font", font in text)
        }
    }

    @Test
    fun androidRuntimeApacheTextIsPresent() {
        val text = notice()
        assertTrue("the Android runtime libraries must be named",
            "Android runtime libraries" in text)
        assertTrue("the Apache licence must be named",
            "Apache License" in text && "Version 2.0, January 2004" in text)
        assertTrue("the Apache terms must be present",
            "END OF TERMS AND CONDITIONS" in text)
        for (lib in listOf("Kotlin", "AndroidX", "Jetpack Compose")) {
            assertTrue("NOTICE must name $lib", lib in text)
        }
    }
}
