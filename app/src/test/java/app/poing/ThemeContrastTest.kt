package app.poing

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the Phase 1 device bug (Robert, 2026-11-11):
 * the home screen showed only the Licences button and the Licences
 * screen only the Back button — all plain text was invisible,
 * because the app used a bare MaterialTheme (default light scheme,
 * near-black onBackground text) on a black window background, with
 * no Surface painting a background. Black text on black.
 *
 * `assertIsDisplayed` cannot catch this class of bug — the nodes
 * ARE displayed, just in a colour nobody can see. So this test
 * asserts the theme itself: the text colours must contrast strongly
 * against the backgrounds they sit on. If anyone reverts the app
 * to the light scheme (dark text on a black background), the
 * contrast ratios below collapse and this test goes red first.
 *
 * Pure JVM test — no Robolectric, no Compose runtime needed.
 */
class ThemeContrastTest {

    @Test
    fun backgroundIsDark() {
        assertTrue(
            "background must be dark (luminance < 0.1), was ${PoingDarkColors.background}",
            PoingDarkColors.background.luminance() < 0.1f,
        )
        assertTrue(
            "surface must be dark (luminance < 0.1), was ${PoingDarkColors.surface}",
            PoingDarkColors.surface.luminance() < 0.1f,
        )
    }

    @Test
    fun textOnBackgroundHasHighContrast() {
        val ratio = contrastRatio(PoingDarkColors.onBackground, PoingDarkColors.background)
        assertTrue("onBackground vs background contrast $ratio, want >= 7", ratio >= 7.0)
    }

    @Test
    fun textOnSurfaceHasHighContrast() {
        val ratio = contrastRatio(PoingDarkColors.onSurface, PoingDarkColors.surface)
        assertTrue("onSurface vs surface contrast $ratio, want >= 7", ratio >= 7.0)
    }

    @Test
    fun textOnPrimaryHasReadableContrast() {
        val ratio = contrastRatio(PoingDarkColors.onPrimary, PoingDarkColors.primary)
        assertTrue("onPrimary vs primary contrast $ratio, want >= 4.5", ratio >= 4.5)
    }

    @Test
    fun textColoursAreLight() {
        assertTrue(
            "onBackground must be light (luminance > 0.7)",
            PoingDarkColors.onBackground.luminance() > 0.7f,
        )
        assertTrue(
            "onSurface must be light (luminance > 0.7)",
            PoingDarkColors.onSurface.luminance() > 0.7f,
        )
    }

    // Phase 5 palette guards (colours sampled from the supplied
    // icon: deep purple face, gold trim, silver chrome). Gold and
    // silver are accents on black; the purple is a container
    // colour with white text, never text on black.

    @Test
    fun primaryIsTheIconsPurple() {
        assertTrue(
            "primary must be the icon's deep purple, not the old Material purple",
            PoingDarkColors.primary == PoingPurple,
        )
    }

    @Test
    fun goldOnBlackIsReadable() {
        val ratio = contrastRatio(PoingGold, PoingDarkColors.background)
        assertTrue("gold vs black contrast $ratio, want >= 4.5", ratio >= 4.5)
    }

    @Test
    fun silverOnBlackIsReadable() {
        val ratio = contrastRatio(PoingSilver, PoingDarkColors.background)
        assertTrue("silver vs black contrast $ratio, want >= 7", ratio >= 7.0)
    }

    @Test
    fun textOnPrimaryContainerIsReadable() {
        val ratio = contrastRatio(
            PoingDarkColors.onPrimaryContainer,
            PoingDarkColors.primaryContainer,
        )
        assertTrue("onPrimaryContainer vs primaryContainer contrast $ratio, want >= 4.5", ratio >= 4.5)
    }

    @Test
    fun blackOnGoldIsReadable() {
        val ratio = contrastRatio(PoingDarkColors.onTertiary, PoingDarkColors.tertiary)
        assertTrue("onTertiary vs tertiary contrast $ratio, want >= 4.5", ratio >= 4.5)
    }

    @Test
    fun purpleIsNeverTextOnBlack() {
        // Purple on black is 4.03:1 — below the AA floor. The
        // purple is for containers with white text only; if this
        // ever passes, someone made the purple light enough to be
        // text, and the guardrail above no longer describes it.
        val ratio = contrastRatio(PoingPurple, PoingDarkColors.background)
        assertTrue("purple vs black must stay below 4.5 (it is a container, not text)", ratio < 4.5)
    }

    @Test
    fun powerLightColoursAreDistinct() {
        // Green (playing), amber (paused) and dark (off) must be
        // three visibly different dots — a test that just asserts
        // the mapping, so a copy-paste slip shows up here.
        assertTrue(powerLightColor(PowerState.Playing) == PoingPowerGreen)
        assertTrue(powerLightColor(PowerState.Paused) == PoingPowerAmber)
        assertTrue(powerLightColor(PowerState.Playing) != powerLightColor(PowerState.Paused))
        assertTrue(powerLightColor(PowerState.Off) != powerLightColor(PowerState.Playing))
    }
}
