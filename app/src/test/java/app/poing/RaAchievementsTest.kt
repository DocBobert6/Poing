package app.poing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the RetroAchievements list model: the JSON
 * the native layer builds from the rc_client list (see
 * ra_client.c) parses into achievements with the right fields,
 * malformed JSON never crashes the list screen, and the header
 * summary counts unlocked achievements.
 */
class RaAchievementsTest {

    // The native layer's shape: unlocked is an int (0/1), not a
    // JSON boolean, and measured_progress is "" when there is
    // none. Mirrors the mock game in the ra_client host test (two
    // achievements, 5 and 2 points).
    private val sampleJson = """[
        {"id":5501,"title":"Ach1","description":"First one","points":5,"state":1,"unlocked":0,"badge_url":"","rarity":0.0,"measured_progress":"","measured_percent":0.0},
        {"id":5502,"title":"Ach2","description":"Second one","points":2,"state":2,"unlocked":1,"badge_url":"","rarity":0.0,"measured_progress":"3/5","measured_percent":0.6}
    ]"""

    @Test
    fun parseSampleJson() {
        val list = RaAchievements.parse(sampleJson)
        assertEquals(2, list.size)
        val first = list[0]
        assertEquals(5501, first.id)
        assertEquals("Ach1", first.title)
        assertEquals("First one", first.description)
        assertEquals(5, first.points)
        assertEquals(RaAchievements.STATE_ACTIVE, first.state)
        assertFalse(first.unlocked)
        assertFalse(first.isUnlocked)
        assertNull(first.measuredProgress)
        val second = list[1]
        assertEquals(5502, second.id)
        assertEquals(2, second.points)
        assertEquals(RaAchievements.STATE_UNLOCKED, second.state)
        assertTrue(second.unlocked)
        assertTrue(second.isUnlocked)
        assertEquals("3/5", second.measuredProgress)
        assertEquals(0.6, second.measuredPercent!!, 0.001)
    }

    @Test
    fun parseMalformedJsonIsEmpty() {
        assertTrue(RaAchievements.parse("").isEmpty())
        assertTrue(RaAchievements.parse("not json").isEmpty())
        assertTrue(RaAchievements.parse("{}").isEmpty())
        // One bad entry is skipped, the good one still parses.
        val mixed = """[{"id":1,"title":"Good","description":"","points":1,"state":1,"unlocked":0}, "oops"]"""
        val list = RaAchievements.parse(mixed)
        assertEquals(1, list.size)
        assertEquals("Good", list[0].title)
    }

    @Test
    fun summaryCountsUnlocked() {
        assertEquals("0 / 0 unlocked", RaAchievements.summary(emptyList()))
        assertEquals("1 / 2 unlocked", RaAchievements.summary(RaAchievements.parse(sampleJson)))
    }

    @Test
    fun inactiveIsNotUnlocked() {
        val list = RaAchievements.parse(
            """[{"id":1,"title":"Never","description":"","points":1,"state":0,"unlocked":0}]""",
        )
        assertEquals(1, list.size)
        assertEquals(RaAchievements.STATE_INACTIVE, list[0].state)
        assertFalse(list[0].isUnlocked)
        assertEquals("0 / 1 unlocked", RaAchievements.summary(list))
    }

    // Hub + badges (2026-11-14): the badge URLs the native layer
    // carries (see ra_client.c) parse through, and the display
    // badge is the unlocked one once earned, the locked one
    // before that. An empty badge_url from the native layer (the
    // server gave no badge) is no badge, not an empty URL.
    @Test
    fun badgeUrlsParseAndDisplayPicksLockedUntilUnlocked() {
        val list = RaAchievements.parse(
            """[
                {"id":1,"title":"Locked","description":"","points":5,"state":1,"unlocked":0,"badge_url":"https://media.retroachievements.org/Badge/12345.png","badge_locked_url":"https://media.retroachievements.org/Badge/12345_lock.png"},
                {"id":2,"title":"Earned","description":"","points":10,"state":2,"unlocked":1,"badge_url":"https://media.retroachievements.org/Badge/23456.png","badge_locked_url":"https://media.retroachievements.org/Badge/23456_lock.png"},
                {"id":3,"title":"NoBadge","description":"","points":1,"state":1,"unlocked":0,"badge_url":"","badge_locked_url":""}
            ]""",
        )
        assertEquals(3, list.size)
        assertEquals(
            "https://media.retroachievements.org/Badge/12345_lock.png",
            list[0].badgeUrlForDisplay(),
        )
        assertEquals(
            "https://media.retroachievements.org/Badge/23456.png",
            list[1].badgeUrlForDisplay(),
        )
        assertNull(list[2].badgeUrl)
        assertNull(list[2].badgeLockedUrl)
        assertNull(list[2].badgeUrlForDisplay())
    }

    @Test
    fun badgeUrlMissingIsNull() {
        val list = RaAchievements.parse(
            """[{"id":1,"title":"Old","description":"","points":1,"state":1,"unlocked":0}]""",
        )
        assertEquals(1, list.size)
        assertNull(list[0].badgeUrl)
        assertNull(list[0].badgeUrlForDisplay())
    }
}
