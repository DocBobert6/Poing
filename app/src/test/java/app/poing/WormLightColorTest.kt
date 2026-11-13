package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Worm Light realism (Robert, 2026-11-12): the lamp's
 * hardware colour is the player's choice — dark medium gray
 * by default — and the light itself is always warm white.
 * Pure Kotlin, so these run on the plain JVM.
 */
class WormLightColorTest {

    @Test
    fun defaultIsDarkMediumGray() {
        assertEquals(WormLightColor.DARK_GRAY, WormLightColor.DEFAULT)
        // The literal is a Long in Kotlin (above Int range),
        // so it needs the same .toInt() as the enum entries.
        assertEquals(0xFF555555.toInt(), WormLightColor.DEFAULT.argb)
    }

    @Test
    fun swatchesCoverTheRealAccessories() {
        val swatches = WormLightColor.SWATCHES
        assertEquals(5, swatches.size)
        assertTrue(swatches.contains(WormLightColor.DARK_GRAY))
        assertTrue(swatches.contains(WormLightColor.ATOMIC_PURPLE))
        assertTrue(swatches.contains(WormLightColor.CLEAR))
        assertTrue(swatches.contains(WormLightColor.GREEN))
        assertTrue(swatches.contains(WormLightColor.BLACK))
        // The Atomic Purple is the app's own purple.
        assertEquals(0xFF7E57C2.toInt(), WormLightColor.ATOMIC_PURPLE.argb)
    }

    @Test
    fun onlyClearIsTranslucent() {
        assertTrue(WormLightColor.CLEAR.translucent)
        for (color in WormLightColor.SWATCHES) {
            if (color != WormLightColor.CLEAR) {
                assertTrue("$color should be solid", !color.translucent)
            }
        }
    }

    @Test
    fun fromNameRoundTripsEveryColour() {
        for (color in WormLightColor.values()) {
            assertEquals(color, WormLightColor.fromName(color.name))
        }
    }

    @Test
    fun fromNameFallsBackToDefaultForAnythingUnknown() {
        assertEquals(WormLightColor.DEFAULT, WormLightColor.fromName("NotAColour"))
        assertEquals(WormLightColor.DEFAULT, WormLightColor.fromName(null))
        assertEquals(WormLightColor.DEFAULT, WormLightColor.fromName(""))
    }
}
