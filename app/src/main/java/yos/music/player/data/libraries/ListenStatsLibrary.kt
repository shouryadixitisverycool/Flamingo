package yos.music.player.data.libraries

import androidx.compose.runtime.Stable
import java.util.Calendar

@Stable
data class ListenStatsEvent(
    val uri: String? = null,
    val title: String? = null,
    val artists: String? = null,
    val album: String? = null,
    val albumArtists: String? = null,
    val thumb: String? = null,
    val dayStartMs: Long = 0L,
    val timestampMs: Long = 0L,
    val listenedMs: Long = 0L,
    val countsAsPlay: Boolean = false
)

@Stable
enum class StatsPeriod
{
    Today, ThisWeek, ThisMonth, ThisYear, AllTime
}

@Stable
data class StatsSummary(
    val totalListenedMs: Long,
    val playCount: Int,
    val uniqueAlbumCount: Int
)

@Stable
data class StatsArtistEntry(
    val artistName: String,
    val listenedMs: Long,
    val thumb: String?,
    val inLibrary: Boolean
)

@Stable
data class StatsAlbumEntry(
    val albumName: String,
    val artistName: String,
    val listenedMs: Long,
    val thumb: String?,
    val inLibrary: Boolean
)

@Stable
data class StatsTrackEntry(
    val trackKey: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val listenedMs: Long,
    val thumb: String?,
    val libraryItem: YosMediaItem?
)

@Stable
data class StatsPeriodSnapshot(
    val summary: StatsSummary,
    val artistEntries: List<StatsArtistEntry>,
    val albumEntries: List<StatsAlbumEntry>,
    val trackEntries: List<StatsTrackEntry>,
    val uniqueAlbumKeys: Set<String>,
    val hasEvents: Boolean
)

@Stable
data class StatsLibraryIndex(
    val firstSongByArtist: Map<String, YosMediaItem>,
    val firstSongByAlbum: Map<String, YosMediaItem>,
    val songByUri: Map<String, YosMediaItem>
)

@Stable
object ListenStatsLibrary
{
    val emptySnapshot = StatsPeriodSnapshot(
        summary = StatsSummary(0L, 0, 0),
        artistEntries = emptyList(),
        albumEntries = emptyList(),
        trackEntries = emptyList(),
        uniqueAlbumKeys = emptySet(),
        hasEvents = false
    )

    fun buildLibraryIndex(): StatsLibraryIndex
    {
        val firstSongByArtist = LinkedHashMap<String, YosMediaItem>()
        val firstSongByAlbum = LinkedHashMap<String, YosMediaItem>()
        val songByUri = LinkedHashMap<String, YosMediaItem>()

        for (song in MusicLibrary.songs)
        {
            song.uri?.toString()?.let { songByUri[it] = song }
            firstSongByAlbum.putIfAbsent(song.album ?: defaultAlbum, song)

            val artistNames = song.artistsList ?: defaultArtists
            for (artistName in artistNames)
            {
                firstSongByArtist.putIfAbsent(artistName, song)
            }
        }

        return StatsLibraryIndex(firstSongByArtist, firstSongByAlbum, songByUri)
    }

    fun dayStartMs(timeMs: Long): Long
    {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun periodStartMs(period: StatsPeriod, nowMs: Long = System.currentTimeMillis()): Long
    {
        if (period == StatsPeriod.AllTime) {return 0L}

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMs
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when (period)
        {
            StatsPeriod.Today -> {}
            StatsPeriod.ThisWeek -> calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            StatsPeriod.ThisMonth -> calendar.set(Calendar.DAY_OF_MONTH, 1)
            StatsPeriod.ThisYear -> calendar.set(Calendar.DAY_OF_YEAR, 1)
            StatsPeriod.AllTime -> {}
        }

        return calendar.timeInMillis
    }

    fun filterEventsForPeriod(allEvents: List<ListenStatsEvent>, period: StatsPeriod): List<ListenStatsEvent>
    {
        if (period == StatsPeriod.AllTime) {return allEvents}
        val startMs = periodStartMs(period)
        return allEvents.filter { it.dayStartMs >= startMs }
    }

    fun buildSummary(periodEvents: List<ListenStatsEvent>): StatsSummary
    {
        val totalListenedMs = periodEvents.sumOf { it.listenedMs }
        val playCount = periodEvents.count { it.countsAsPlay }
        val uniqueAlbumCount = periodEvents.map { it.album ?: defaultAlbum }.distinct().size
        return StatsSummary(totalListenedMs, playCount, uniqueAlbumCount)
    }

    fun buildSnapshot(periodEvents: List<ListenStatsEvent>, libraryIndex: StatsLibraryIndex = buildLibraryIndex()): StatsPeriodSnapshot
    {
        if (periodEvents.isEmpty()) {return emptySnapshot}

        val uniqueAlbumKeys = periodEvents.map { it.album ?: defaultAlbum }.toSet()
        val summary = StatsSummary(
            totalListenedMs = periodEvents.sumOf { it.listenedMs },
            playCount = periodEvents.count { it.countsAsPlay },
            uniqueAlbumCount = uniqueAlbumKeys.size
        )

        return StatsPeriodSnapshot(
            summary = summary,
            artistEntries = buildArtistEntries(periodEvents, libraryIndex),
            albumEntries = buildAlbumEntries(periodEvents, libraryIndex),
            trackEntries = buildTrackEntries(periodEvents, libraryIndex),
            uniqueAlbumKeys = uniqueAlbumKeys,
            hasEvents = true
        )
    }

    fun mergeSnapshots(cachedSnapshot: StatsPeriodSnapshot, liveSnapshot: StatsPeriodSnapshot): StatsPeriodSnapshot
    {
        if (!liveSnapshot.hasEvents) {return cachedSnapshot}
        if (!cachedSnapshot.hasEvents) {return liveSnapshot}

        val uniqueAlbumKeys = cachedSnapshot.uniqueAlbumKeys + liveSnapshot.uniqueAlbumKeys
        return StatsPeriodSnapshot(
            summary = StatsSummary(
                totalListenedMs = cachedSnapshot.summary.totalListenedMs + liveSnapshot.summary.totalListenedMs,
                playCount = cachedSnapshot.summary.playCount + liveSnapshot.summary.playCount,
                uniqueAlbumCount = uniqueAlbumKeys.size
            ),
            artistEntries = mergeArtistEntries(cachedSnapshot.artistEntries, liveSnapshot.artistEntries),
            albumEntries = mergeAlbumEntries(cachedSnapshot.albumEntries, liveSnapshot.albumEntries),
            trackEntries = mergeTrackEntries(cachedSnapshot.trackEntries, liveSnapshot.trackEntries),
            uniqueAlbumKeys = uniqueAlbumKeys,
            hasEvents = true
        )
    }

    fun buildArtistEntries(periodEvents: List<ListenStatsEvent>, libraryIndex: StatsLibraryIndex = buildLibraryIndex()): List<StatsArtistEntry>
    {
        val listenedMsByArtist = LinkedHashMap<String, Long>()
        val fallbackThumbByArtist = HashMap<String, String?>()

        for (singleEvent in periodEvents)
        {
            val splitArtistNames = singleEvent.artists?.toMultipleArtists() ?: defaultArtists
            for (artistName in splitArtistNames)
            {
                listenedMsByArtist[artistName] = (listenedMsByArtist[artistName] ?: 0L) + singleEvent.listenedMs
                if (!fallbackThumbByArtist.containsKey(artistName)) {fallbackThumbByArtist[artistName] = singleEvent.thumb}
            }
        }

        return listenedMsByArtist.entries
            .sortedByDescending { it.value }
            .map { artistEntry ->
                val librarySongForArtist = libraryIndex.firstSongByArtist[artistEntry.key]
                StatsArtistEntry(
                    artistName = artistEntry.key,
                    listenedMs = artistEntry.value,
                    thumb = librarySongForArtist?.thumb?.toString() ?: fallbackThumbByArtist[artistEntry.key],
                    inLibrary = librarySongForArtist != null
                )
            }
    }

    fun buildAlbumEntries(periodEvents: List<ListenStatsEvent>, libraryIndex: StatsLibraryIndex = buildLibraryIndex()): List<StatsAlbumEntry>
    {
        val eventsByAlbum = periodEvents.groupBy { it.album ?: defaultAlbum }

        return eventsByAlbum.entries
            .map { albumGroup ->
                val firstEvent = albumGroup.value.first()
                val librarySongForAlbum = libraryIndex.firstSongByAlbum[albumGroup.key]
                val displayArtistName = firstEvent.albumArtists
                    ?: firstEvent.artists?.toMultipleArtists()?.toArtistsString()
                    ?: defaultArtistsName
                StatsAlbumEntry(
                    albumName = albumGroup.key,
                    artistName = displayArtistName,
                    listenedMs = albumGroup.value.sumOf { it.listenedMs },
                    thumb = librarySongForAlbum?.thumb?.toString() ?: firstEvent.thumb,
                    inLibrary = librarySongForAlbum != null
                )
            }
            .sortedByDescending { it.listenedMs }
    }

    fun buildTrackEntries(periodEvents: List<ListenStatsEvent>, libraryIndex: StatsLibraryIndex = buildLibraryIndex()): List<StatsTrackEntry>
    {
        val eventsByTrack = periodEvents.groupBy { it.uri ?: "${it.title}|${it.album}" }

        return eventsByTrack.entries
            .map { trackGroup ->
                val firstEvent = trackGroup.value.first()
                val libraryItem = firstEvent.uri?.let { libraryIndex.songByUri[it] }
                StatsTrackEntry(
                    trackKey = trackGroup.key,
                    title = libraryItem?.title ?: firstEvent.title ?: defaultTitle,
                    artistName = firstEvent.artists?.toMultipleArtists()?.toArtistsString() ?: defaultArtistsName,
                    albumName = firstEvent.album ?: defaultAlbum,
                    listenedMs = trackGroup.value.sumOf { it.listenedMs },
                    thumb = libraryItem?.thumb?.toString() ?: firstEvent.thumb,
                    libraryItem = libraryItem
                )
            }
            .sortedByDescending { it.listenedMs }
    }

    private fun mergeArtistEntries(cachedEntries: List<StatsArtistEntry>, liveEntries: List<StatsArtistEntry>): List<StatsArtistEntry>
    {
        val mergedEntries = LinkedHashMap<String, StatsArtistEntry>()
        for (entry in cachedEntries) {mergedEntries[entry.artistName] = entry}
        for (entry in liveEntries)
        {
            val cachedEntry = mergedEntries[entry.artistName]
            mergedEntries[entry.artistName] = if (cachedEntry == null)
            {
                entry
            }
            else
            {
                cachedEntry.copy(listenedMs = cachedEntry.listenedMs + entry.listenedMs)
            }
        }
        return mergedEntries.values.sortedByDescending { it.listenedMs }
    }

    private fun mergeAlbumEntries(cachedEntries: List<StatsAlbumEntry>, liveEntries: List<StatsAlbumEntry>): List<StatsAlbumEntry>
    {
        val mergedEntries = LinkedHashMap<String, StatsAlbumEntry>()
        for (entry in cachedEntries) {mergedEntries[entry.albumName] = entry}
        for (entry in liveEntries)
        {
            val cachedEntry = mergedEntries[entry.albumName]
            mergedEntries[entry.albumName] = if (cachedEntry == null)
            {
                entry
            }
            else
            {
                cachedEntry.copy(listenedMs = cachedEntry.listenedMs + entry.listenedMs)
            }
        }
        return mergedEntries.values.sortedByDescending { it.listenedMs }
    }

    private fun mergeTrackEntries(cachedEntries: List<StatsTrackEntry>, liveEntries: List<StatsTrackEntry>): List<StatsTrackEntry>
    {
        val mergedEntries = LinkedHashMap<String, StatsTrackEntry>()
        for (entry in cachedEntries) {mergedEntries[entry.trackKey] = entry}
        for (entry in liveEntries)
        {
            val cachedEntry = mergedEntries[entry.trackKey]
            mergedEntries[entry.trackKey] = if (cachedEntry == null)
            {
                entry
            }
            else
            {
                cachedEntry.copy(listenedMs = cachedEntry.listenedMs + entry.listenedMs)
            }
        }
        return mergedEntries.values.sortedByDescending { it.listenedMs }
    }
}
