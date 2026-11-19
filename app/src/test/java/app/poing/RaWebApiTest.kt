package app.poing

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alpha.23 — the RA web API: rarity tiers, response parsing, and URL building (pure Kotlin). */
class RaWebApiTest {

    @Test
    fun tierBoundaries() {
        assertEquals(RaRarityTier.Common, raRarityTier(100.0))
        assertEquals(RaRarityTier.Common, raRarityTier(50.0))
        assertEquals(RaRarityTier.Uncommon, raRarityTier(49.9))
        assertEquals(RaRarityTier.Uncommon, raRarityTier(25.0))
        assertEquals(RaRarityTier.Rare, raRarityTier(24.9))
        assertEquals(RaRarityTier.Rare, raRarityTier(10.0))
        assertEquals(RaRarityTier.VeryRare, raRarityTier(9.9))
        assertEquals(RaRarityTier.VeryRare, raRarityTier(5.0))
        assertEquals(RaRarityTier.UltraRare, raRarityTier(4.9))
        assertEquals(RaRarityTier.UltraRare, raRarityTier(2.0))
        assertEquals(RaRarityTier.Legendary, raRarityTier(1.9))
        assertEquals(RaRarityTier.Legendary, raRarityTier(0.0))
    }

    @Test
    fun percentFormat() {
        assertEquals("50%", formatRarityPercent(50.0))
        assertEquals("12%", formatRarityPercent(12.4))
        assertEquals("2.5%", formatRarityPercent(2.5))
        assertEquals("0.4%", formatRarityPercent(0.4))
    }

    @Test
    fun unlockRarityKindBoundaries() {
        // Alpha.33 — the popup variant (and the tier haptic):
        // under 10% is rare, under 5% ultra-rare, and no
        // rarity data at all is the plain gold popup.
        assertEquals(RaUnlockRarityKind.PLAIN, raUnlockRarityKind(null))
        assertEquals(RaUnlockRarityKind.PLAIN, raUnlockRarityKind(50.0))
        assertEquals(RaUnlockRarityKind.PLAIN, raUnlockRarityKind(10.0))
        assertEquals(RaUnlockRarityKind.RARE, raUnlockRarityKind(9.9))
        assertEquals(RaUnlockRarityKind.RARE, raUnlockRarityKind(5.0))
        assertEquals(RaUnlockRarityKind.ULTRA_RARE, raUnlockRarityKind(4.9))
        assertEquals(RaUnlockRarityKind.ULTRA_RARE, raUnlockRarityKind(0.0))
    }

    @Test
    fun gameRarityParsesAwardCounts() {
        val json = """
            {"ID":540,"Title":"Super Mario Land 2","NumDistinctPlayersCasual":200,
             "NumDistinctPlayersHardcore":50,
             "Achievements":{
               "1":{"ID":1,"NumAwarded":100,"NumAwardedHardcore":20},
               "2":{"ID":2,"NumAwarded":5,"NumAwardedHardcore":1},
               "3":{"ID":3,"NumAwarded":0,"NumAwardedHardcore":0},
               "4":"nope"}}
        """.trimIndent()
        val rarity = RaWebApi.parseGameRarity(json)
        assertEquals(3, rarity.size)
        val first = rarity[1]!!
        assertEquals(100, first.awarded)
        assertEquals(200, first.players)
        assertEquals(50.0, first.percent, 0.001)
        assertEquals(RaRarityTier.Common, first.tier)
        val second = rarity[2]!!
        assertEquals(2.5, second.percent, 0.001)
        assertEquals(RaRarityTier.UltraRare, second.tier)
        val third = rarity[3]!!
        assertEquals(0.0, third.percent, 0.001)
        assertEquals(RaRarityTier.Legendary, third.tier)
        // The malformed entry ("4": "nope") is skipped, not fatal.
        assertFalse(rarity.containsKey(4))
    }

    @Test
    fun gameRarityWithoutPlayerCountIsZero() {
        val json = """{"ID":540,"Achievements":{"1":{"ID":1,"NumAwarded":10}}}"""
        val rarity = RaWebApi.parseGameRarity(json)
        assertEquals(0.0, rarity[1]!!.percent, 0.001)
        assertEquals(RaRarityTier.Legendary, rarity[1]!!.tier)
    }

    @Test
    fun gameRarityMalformedJsonIsEmpty() {
        assertTrue(RaWebApi.parseGameRarity("not json").isEmpty())
        assertTrue(RaWebApi.parseGameRarity("{}").isEmpty())
    }

    @Test
    fun recentAchievementsSortNewestFirst() {
        val json = """
            [{"AchievementID":2,"Title":"Second Win","GameTitle":"Super Mario Land 2","Points":10,
              "Date":"2026-11-16 14:32:00","BadgeName":"abc123"},
             {"AchievementID":1,"Title":"First Win","GameTitle":"Super Mario Land 2","Points":5,
              "Date":"2026-11-15 09:00:00","BadgeName":"def456"},
             {"AchievementID":3,"Title":"Bad Date","GameTitle":"Super Mario Land 2","Points":5,
              "Date":"not-a-date","BadgeName":"ghi789"}]
        """.trimIndent()
        val history = RaWebApi.parseRecentAchievements(json)
        // The bad-date entry is skipped, not fatal; the rest sort newest first.
        assertEquals(listOf(2, 1), history.map { it.achievementId })
        val first = history[0]
        assertEquals("Second Win", first.title)
        assertEquals("Super Mario Land 2", first.gameTitle)
        assertEquals(10, first.points)
        assertEquals(
            Instant.parse("2026-11-16T14:32:00Z").toEpochMilli(),
            first.unlockedAt,
        )
        assertEquals("https://media.retroachievements.org/Badge/abc123.png", first.badgeUrl)
    }

    @Test
    fun recentAchievementsMalformedJsonIsEmpty() {
        assertTrue(RaWebApi.parseRecentAchievements("not json").isEmpty())
        assertTrue(RaWebApi.parseRecentAchievements("{}").isEmpty())
    }

    @Test
    fun urlsCarryCredentialsAndGame() {
        val gameUrl = RaWebApi.gameInfoUrl("DocBobert", "key+with/special", 540)
        assertTrue(gameUrl.startsWith("https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php?"))
        assertTrue(gameUrl.contains("z=DocBobert"))
        assertTrue(gameUrl.contains("y=key%2Bwith%2Fspecial"))
        assertTrue(gameUrl.contains("g=540"))
        val recentUrl = RaWebApi.recentAchievementsUrl("DocBobert", "key123")
        assertTrue(recentUrl.contains("API_GetUserRecentAchievements.php?"))
        assertTrue(recentUrl.contains("m=5256000"))
    }
}
