package app.poing

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6 (item 5) — PlaytimeStore: accumulated seconds per
 * game, plus the "2h 5m" display format.
 */
class PlaytimeStoreTest {
    private lateinit var dir: java.io.File
    private lateinit var store: PlaytimeStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("playtime").toFile()
        store = PlaytimeStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `unknown game has zero playtime`() {
        assertEquals(0L, store.get("abc123"))
    }

    @Test
    fun `add accumulates across calls`() {
        store.add("abc123", 90)
        store.add("abc123", 30)
        assertEquals(120L, store.get("abc123"))
    }

    @Test
    fun `add ignores zero and negative seconds`() {
        store.add("abc123", 60)
        store.add("abc123", 0)
        store.add("abc123", -30)
        assertEquals(60L, store.get("abc123"))
    }

    @Test
    fun `games do not share totals`() {
        store.add("abc123", 60)
        assertEquals(0L, store.get("def456"))
    }

    @Test
    fun `remove drops the entry`() {
        store.add("abc123", 60)
        store.remove("abc123")
        assertEquals(0L, store.get("abc123"))
    }

    @Test
    fun `totals survive a fresh store on the same dir`() {
        store.add("abc123", 3600)
        assertEquals(3600L, PlaytimeStore(dir).get("abc123"))
    }

    @Test
    fun `corrupt value reads as zero`() {
        dir.resolve("playtime.properties").writeText("abc123=banana\n")
        assertEquals(0L, store.get("abc123"))
    }

    @Test
    fun `formatPlaytime cases`() {
        assertEquals("0s", formatPlaytime(0))
        assertEquals("45s", formatPlaytime(45))
        assertEquals("1m", formatPlaytime(60))
        assertEquals("12m", formatPlaytime(750))
        assertEquals("1h 0m", formatPlaytime(3600))
        assertEquals("2h 5m", formatPlaytime(7500))
        assertEquals("0s", formatPlaytime(-10))
    }
}
