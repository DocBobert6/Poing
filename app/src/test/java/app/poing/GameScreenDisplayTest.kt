package app.poing

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Alpha.8 — GameScreen resolves the display for the
 * loaded game's system, not the global: the chain
 * wanted for that system, that system's reconstruction
 * toggle, and that system's edge mode. Pure — no
 * native code, no Compose — so this runs on the plain
 * JVM (the drawPath token in the player is unreachable
 * under Robolectric: the onStats callback never fires
 * there, so the top-bar label cannot be the test path).
 */
class GameScreenDisplayTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = AppSettingsStore(temp.root)

    @Test
    fun gbDefaultsToItsChainWithEdgeOff() {
        val s = store()
        val resolution = displayResolutionFor(s, GameSystem.GB)
        assertEquals(DisplayChain.JADE, resolution.chain)
        assertEquals(EdgeMode.OFF, resolution.edge)
        assertEquals(ReconMode.OFF, resolution.recon)
    }

    @Test
    fun gbcDefaultsToItsChainWithEdgeOff() {
        val s = store()
        val resolution = displayResolutionFor(s, GameSystem.GBC)
        assertEquals(DisplayChain.PRISM, resolution.chain)
        assertEquals(EdgeMode.OFF, resolution.edge)
    }

    @Test
    fun gbaDefaultsToItsChainWithEdgeOff() {
        val s = store()
        val resolution = displayResolutionFor(s, GameSystem.GBA)
        assertEquals(DisplayChain.DUSK, resolution.chain)
        assertEquals(EdgeMode.OFF, resolution.edge)
    }

    @Test
    fun edgeOnForOneSystemLeavesTheOthersOnTheirChains() {
        val s = store()
        s.setEdgeSmoothingEnabled(GameSystem.GB, true)
        val gb = displayResolutionFor(s, GameSystem.GB)
        assertEquals(DisplayChain.CLEAN, gb.chain)
        assertEquals(EdgeMode.EDGE, gb.edge)
        val gbc = displayResolutionFor(s, GameSystem.GBC)
        assertEquals(DisplayChain.PRISM, gbc.chain)
        assertEquals(EdgeMode.OFF, gbc.edge)
        val gba = displayResolutionFor(s, GameSystem.GBA)
        assertEquals(DisplayChain.DUSK, gba.chain)
        assertEquals(EdgeMode.OFF, gba.edge)
    }

    @Test
    fun edgeGridForGbaResolvesToTheCombinedMode() {
        val s = store()
        s.setEdgeSmoothingEnabled(GameSystem.GBA, true)
        s.setEdgeGridEnabled(GameSystem.GBA, true)
        val resolution = displayResolutionFor(s, GameSystem.GBA)
        assertEquals(DisplayChain.CLEAN, resolution.chain)
        assertEquals(EdgeMode.EDGE_GRID, resolution.edge)
    }

    @Test
    fun chainOffWithEdgeOffResolvesToClean() {
        val s = store()
        s.setChainEnabled(GameSystem.GBC, false)
        val resolution = displayResolutionFor(s, GameSystem.GBC)
        assertEquals(DisplayChain.CLEAN, resolution.chain)
        assertEquals(EdgeMode.OFF, resolution.edge)
    }

    @Test
    fun reconstructionWinsOverChainAndEdgeForItsOwnSystem() {
        val s = store()
        s.setEdgeSmoothingEnabled(GameSystem.GB, true)
        s.setReconGbEnabled(true)
        val gb = displayResolutionFor(s, GameSystem.GB)
        assertEquals(DisplayChain.CLEAN, gb.chain)
        assertEquals(EdgeMode.OFF, gb.edge)
        assertEquals(ReconMode.GB, gb.recon)
        // The GBC reconstruction is a different toggle:
        // GB recon on does not affect a GBC game.
        val gbc = displayResolutionFor(s, GameSystem.GBC)
        assertEquals(DisplayChain.PRISM, gbc.chain)
        assertEquals(EdgeMode.OFF, gbc.edge)
        assertEquals(ReconMode.OFF, gbc.recon)
    }

    @Test
    fun edgeMigratedFromTheGlobalAppliesToEverySystem() {
        // The global edge on migrates every system until
        // one is set on its own — nothing changes for a
        // player who never touched the per-system page.
        val s = store()
        s.setEdgeSmoothingEnabled(true)
        assertEquals(EdgeMode.EDGE, displayResolutionFor(s, GameSystem.GB).edge)
        assertEquals(EdgeMode.EDGE, displayResolutionFor(s, GameSystem.GBC).edge)
        assertEquals(EdgeMode.EDGE, displayResolutionFor(s, GameSystem.GBA).edge)
    }
}
