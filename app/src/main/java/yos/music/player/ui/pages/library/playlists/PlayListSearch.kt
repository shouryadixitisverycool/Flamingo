package yos.music.player.ui.pages.library.playlists

import androidx.compose.runtime.Stable
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy search for the in-playlist search bar (PRD §5.1).
 *
 * **Scope (FR-S-05):** every human-readable text metadata field
 * present on [YosMediaItem] is matched against the query — title,
 * artists, album, album artist, genre, composer, writer, author,
 * year. Non-text metadata (bitrate, sample rate, IDs, dates) is
 * intentionally excluded.
 *
 * **Ranking (FR-S-06/07):** matches are ranked by a hand-rolled
 * scoring function that prefers, in order:
 *   1. exact title match,
 *   2. title prefix match,
 *   3. title substring,
 *   4. substring in artist/album,
 *   5. substring in any other field,
 *   6. fuzzy (edit-distance-bounded) match within any field.
 *
 * The fuzzy threshold is proportional to query length so short
 * queries don't accept absurd typo counts:
 *   - 1–4 char query → ≤1 typo
 *   - 5–8 char query → ≤2 typos
 *   - 9+ char query  → ≤3 typos
 *
 * The implementation is **synchronous and in-memory** but callers
 * should debounce keystrokes by 150ms and dispatch onto
 * Dispatchers.Default to keep typing latency low on huge libraries
 * (FR-S-08).
 */
@Stable
object PlayListSearch {

    fun matchAndRank(items: List<YosMediaItem>, query: String): List<YosMediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        val threshold = fuzzyThreshold(q.length)
        // Score each item; drop non-matches; sort by score asc
        // (lower score = better, like rank position).
        return items
            .mapNotNull { item ->
                val score = score(item, q, threshold) ?: return@mapNotNull null
                item to score
            }
            .sortedBy { it.second }
            .map { it.first }
    }

    /** Public for tests / sort comparators. */
    fun fuzzyThreshold(queryLength: Int): Int = when {
        queryLength <= 4 -> 1
        queryLength <= 8 -> 2
        else -> 3
    }

    /**
     * Lower is better. Null means "no match within the threshold".
     */
    private fun score(item: YosMediaItem, q: String, threshold: Int): Int? {
        val title = item.title?.lowercase()
        val artists = item.artistsName?.lowercase()
        val album = item.album?.lowercase()
        val albumArtists = item.albumArtists?.lowercase()
        val genre = item.genre?.lowercase()
        val composer = item.composer?.lowercase()
        val writer = item.writer?.lowercase()
        val author = item.author?.lowercase()
        val year = (item.recordingYear ?: item.releaseYear)?.toString()

        // Tier 1: exact title.
        if (title == q) return 0
        // Tier 2: title prefix.
        if (title != null && title.startsWith(q)) return 10
        // Tier 3: title substring.
        if (title != null && title.contains(q)) return 20 + title.indexOf(q)
        // Tier 4: substring in artist / album.
        if (artists?.contains(q) == true) return 100 + artists.indexOf(q)
        if (album?.contains(q) == true) return 110 + album.indexOf(q)
        if (albumArtists?.contains(q) == true) return 120
        // Tier 5: substring in other fields.
        if (genre?.contains(q) == true) return 200
        if (composer?.contains(q) == true) return 210
        if (writer?.contains(q) == true) return 220
        if (author?.contains(q) == true) return 230
        if (year != null && year.contains(q)) return 240
        // Tier 6: fuzzy match. Try title first, then artists, then
        // album — these are by far the most common search targets.
        listOfNotNull(title, artists, album, albumArtists, genre, composer, writer, author)
            .forEachIndexed { idx, field ->
                val d = bestEditDistance(field, q, threshold)
                if (d != null) return 1000 + idx * 100 + d * 10
            }
        return null
    }

    /**
     * Return the minimum Levenshtein distance between [query] and
     * any substring of [haystack] of similar length, or null if it
     * exceeds [threshold].
     *
     * Sliding-window over haystack of length [query.length ±
     * threshold]. Cheap enough for ≤5000 songs × small queries; we
     * bail out early once `threshold + 1` rows of the DP have all
     * exceeded the budget.
     */
    private fun bestEditDistance(haystack: String?, query: String, threshold: Int): Int? {
        if (haystack == null) return null
        if (haystack.length + threshold < query.length) return null
        val minLen = max(1, query.length - threshold)
        val maxLen = query.length + threshold

        var best: Int? = null
        // Inspect overlapping substrings — but only the "anchor"
        // start positions: every start offset 0..haystack.length.
        for (start in 0..haystack.length) {
            for (len in minLen..maxLen) {
                val end = start + len
                if (end > haystack.length) break
                val sub = haystack.substring(start, end)
                val d = boundedLevenshtein(query, sub, threshold)
                if (d != null && (best == null || d < best)) {
                    best = d
                    if (best == 0) return 0
                }
            }
        }
        return best
    }

    /**
     * Levenshtein distance with an early-out: returns null if the
     * distance exceeds [threshold]. O(query.length * threshold)
     * space, single-row DP.
     */
    private fun boundedLevenshtein(a: String, b: String, threshold: Int): Int? {
        if (kotlin.math.abs(a.length - b.length) > threshold) return null
        val n = a.length
        val m = b.length
        if (n == 0) return if (m <= threshold) m else null
        if (m == 0) return if (n <= threshold) n else null

        var previous = IntArray(m + 1) { it }
        var current = IntArray(m + 1)

        for (i in 1..n) {
            current[0] = i
            var rowMin = current[0]
            val jStart = max(1, i - threshold)
            val jEnd = min(m, i + threshold)
            // Fill cells outside the band with sentinels so they
            // don't pollute later min() calls.
            for (j in 1..m) {
                current[j] = if (j in jStart..jEnd) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    minOf(
                        previous[j] + 1,        // deletion
                        current[j - 1] + 1,      // insertion
                        previous[j - 1] + cost,  // substitution
                    )
                } else {
                    threshold + 1
                }
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > threshold) return null
            // Swap rows.
            val tmp = previous
            previous = current
            current = tmp
        }
        val result = previous[m]
        return if (result <= threshold) result else null
    }
}
