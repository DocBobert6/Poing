package app.poing

/**
 * Cover art from the libretro-thumbnails collection, per system —
 * the same source HowlingDog uses (GitHub API index, cached; one
 * file per game, Wi-Fi only, cached on device), adapted for
 * GB/GBC/GBA.
 *
 * Two adaptations HowlingDog did not need:
 *
 * 1. The index is the git-trees listing, not the contents listing.
 *    The contents endpoint truncates a directory at 1,000 entries
 *    (verified 2026-11-14: GB stops at "PGA Tour 96", GBA at
 *    "Diadroids World"), and the three collections hold 1,647 /
 *    1,524 / 5,961 boxarts. The trees API returns the whole tree
 *    in one call (GBA measured 7.2 MB), so the index cap is 8 MB.
 *    The index is fetched once per system and cached on device.
 *
 * 2. GB/GBC/GBA have no catalogue number, so the matcher
 *    normalizes aggressively and tries BOTH the filename stem
 *    (usually the full No-Intro name) and the ROM header title
 *    (abbreviated to 11–16 characters by the hardware). The
 *    filename stem is tried first: it is the fuller name.
 *
 * The matcher was measured, not guessed: 50 real titles against
 * the real indexes, 50/50 the correct game
 * (~/workspace/poing/verify/library/MEASUREMENTS.md). The rules
 * that earned that number, each pinned by a test:
 * - Parenthetical groups are edition/region tags, not the title:
 *   "(USA, Europe)", "(Rev 1)", "(SGB Enhanced)" are all stripped
 *   before comparing.
 * - libretro writes ", The" ("Legend of Zelda, The - ..."): the
 *   article is dropped, never left mid-title.
 * - libretro writes "$" for "s" in one title ("Mega Microgame$!").
 * - Matches are RANKED, never first-alphabetical: stripping the
 *   parentheticals makes every variant normalize identically, so
 *   first-alphabetical returned Beta/Sample/Virtual Console files.
 *   The rank is (re-release variant?, normalized-title length,
 *   USA/World first, parenthetical-group count, filename length).
 *   Title length first is what keeps "Harvest Moon GB" (the game)
 *   ahead of "Harvest Moon 3 GBC" (the sequel).
 * - A word-boundary prefix ("Pokemon Red" -> "Pokemon Red Version")
 *   needs a real title (>= 2 words), or a bare series name
 *   ("WARIOWARE") lands on the wrong game ("Twisted!").
 * - A bare prefix (truncated headers: "POKEMON EMER" -> "Pokemon
 *   Emerald") needs a mid-word continuation, for the same reason.
 *
 * Pure Kotlin, no Android — unit-testable on the plain JVM.
 */
object Thumbnails {

    /** One art filename with its match key, computed once per index load. */
    data class IndexedArt(val fileName: String, val normalized: String)

    fun repoFor(system: GameSystem): String = when (system) {
        GameSystem.GB -> "Nintendo_-_Game_Boy"
        GameSystem.GBC -> "Nintendo_-_Game_Boy_Color"
        GameSystem.GBA -> "Nintendo_-_Game_Boy_Advance"
    }

    /**
     * The filename index: the git-trees listing of the whole repo.
     * One call per system, cached on device. No API key; the
     * unauthenticated limit is 60/hr, and the cache means the
     * call happens once per system, ever.
     */
    fun indexUrl(system: GameSystem): String =
        "https://api.github.com/repos/libretro-thumbnails/${repoFor(system)}/git/trees/master?recursive=1"

    /** The download URL for one box-art filename. */
    fun artUrl(system: GameSystem, fileName: String): String {
        val encoded = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "https://raw.githubusercontent.com/libretro-thumbnails/${repoFor(system)}/master/Named_Boxarts/$encoded"
    }

    /** The filenames under Named_Boxarts/ in a git-trees response. */
    fun parseTreeJson(json: String): List<String> {
        val pattern = Regex(""""path"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.findAll(json).mapNotNull { m ->
            m.groupValues[1]
                .replace("\\\"", "\"").replace("\\\\", "\\")
                .takeIf { it.startsWith("Named_Boxarts/") && it.endsWith(".png", ignoreCase = true) }
                ?.removePrefix("Named_Boxarts/")
        }.toList()
    }

    /** The index with match keys, computed once per index load. */
    fun normalizeIndex(index: List<String>): List<IndexedArt> =
        index.mapNotNull { name ->
            val base = name.removeSuffix(".png")
            val norm = normalizeTitle(base)
            if (norm.isEmpty()) null else IndexedArt(name, norm)
        }

    /**
     * The box-art filename for a game, from the folder index, or
     * null when nothing in the index matches. The filename stem
     * is tried first (it is usually the full No-Intro name), then
     * the ROM header title (abbreviated by the hardware).
     */
    fun resolveArtFile(
        fileNameStem: String?,
        headerTitle: String?,
        index: List<IndexedArt>,
    ): String? {
        for (candidate in listOfNotNull(fileNameStem, headerTitle)) {
            matchArtFile(candidate, index)?.let { return it }
        }
        return null
    }

    /** The box-art filename for one title, from the folder index, or null. */
    fun matchArtFile(title: String, index: List<IndexedArt>): String? {
        val want = normalizeTitle(title)
        if (want.isEmpty()) return null
        // 1. Exact title.
        rank(index.filter { it.normalized == want })?.let { return it.fileName }
        // 2. Title prefix ("Pokemon Red" -> "Pokemon Red Version").
        // A real title only (>= 2 words): a bare series name must
        // never prefix-match a different game in the series.
        if (want.split(' ').size >= 2) {
            rank(index.filter { it.normalized.startsWith("$want ") })?.let { return it.fileName }
        }
        // 3. Bare prefix (truncated header titles: "POKEMON EMER" ->
        // "Pokemon Emerald"). The art must continue the SAME word:
        // a new word after the match is a different game.
        rank(index.filter {
            it.normalized.startsWith(want) &&
                it.normalized.length > want.length &&
                it.normalized[want.length] != ' '
        })?.let { return it.fileName }
        // 4. Reverse prefix: the title starts with the art title.
        index.filter { want.startsWith("${it.normalized} ") }
            .maxByOrNull { it.normalized.length }?.let { return it.fileName }
        // 5. Contiguous phrase (both >= 2 words, so a bare "zelda"
        // or "kirby" header title never matches the wrong game).
        if (want.split(' ').size >= 2) {
            val phrase = " $want "
            index.filter {
                it.normalized.split(' ').size >= 2 &&
                    (want == it.normalized || want.startsWith("${it.normalized} ") ||
                        want.endsWith(" ${it.normalized}") || phrase.contains(" ${it.normalized} "))
            }.maxByOrNull { it.normalized.length }?.let { return it.fileName }
        }
        return null
    }

    /**
     * Best of the candidates, or null. The order is the lesson of
     * the 50-game measurement: normalized-title length first (the
     * game ahead of its sequel), then USA/World first, then the
     * fewest parenthetical groups (the plain release ahead of a
     * re-release), then the shortest filename.
     */
    private fun rank(candidates: List<IndexedArt>): IndexedArt? =
        candidates.sortedWith(
            compareBy(
                { isVariantFile(it.fileName) },
                { it.normalized.length },
                { regionScore(it.fileName) },
                { groupCount(it.fileName) },
                { it.fileName.length },
            ),
        ).firstOrNull()

    /**
     * A title ready for art matching: parenthetical/bracket groups
     * stripped (they are edition/region tags, never the title),
     * ", The" dropped, "$" read as "s" (a libretro quirk in one
     * title), lower-cased on words, leading "the" dropped, trailing
     * region/language/edition tags stripped.
     */
    fun normalizeTitle(title: String): String {
        var s = title.replace('$', 's')
        s = s.replace(Regex(",\\s*the\\b", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("\\([^)]*\\)"), " ")
        s = s.replace(Regex("\\[[^\\]]*\\]"), " ")
        val words = s.lowercase()
            .map { c -> if (c.isLetterOrDigit()) c else ' ' }
            .joinToString("")
            .split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (words.size > 1 && words.first() == "the") words.removeAt(0)
        while (words.size > 1 && isRegionOrLangTag(words.last())) {
            words.removeAt(words.lastIndex)
        }
        return words.joinToString(" ")
    }

    private val REGION_WORDS = setOf(
        "japan", "usa", "us", "europe", "eu", "world", "asia",
        "korea", "brazil", "france", "germany", "spain", "italy",
        "uk", "australia", "canada", "netherlands", "china",
        "taiwan", "mexico", "argentina", "portugal",
    )

    private val TAG_CODES = setOf(
        "en", "ja", "fr", "de", "es", "it", "pt", "nl", "ko", "zh",
        "ru", "sv", "da", "fi", "no", "pl", "tr", "ar", "he", "cs",
        "hu", "el", "id", "ms", "th", "vi",
        "jp", "kr", "cn", "tw", "au", "ca", "mx", "br", "us", "uk", "eu",
    )

    private val EDITION_WORDS = setOf(
        "rev", "beta", "proto", "prototype", "demo", "kiosk", "sample",
    )

    /** A word that is a region tag, an edition tag, or a language/region-code word (2–6 letters, made of 2-letter codes). */
    private fun isRegionOrLangTag(word: String): Boolean {
        if (word in REGION_WORDS || word in EDITION_WORDS) return true
        return word.length in setOf(2, 4, 6) &&
            word.chunked(2).all { it in TAG_CODES }
    }

    /** A re-release / repack / demo file: same game, not the original box. Ranked last, never excluded. */
    private fun isVariantFile(fileName: String): Boolean {
        val low = fileName.lowercase()
        return "(beta)" in low || "(proto" in low || "(bootleg" in low ||
            "(demo" in low || "(kiosk" in low || "(sample" in low ||
            "(virtual console" in low || "collection" in low
    }

    private fun groupCount(fileName: String): Int = fileName.count { it == '(' }

    /** USA / World first, then Europe, then any other single region. */
    private fun regionScore(fileName: String): Int {
        val low = fileName.lowercase()
        return when {
            "(usa" in low || "(world" in low -> 0
            "(europe" in low -> 1
            else -> 2
        }
    }
}
