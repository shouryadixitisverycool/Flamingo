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
object ListenStatsLibrary
{
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

    fun buildArtistEntries(periodEvents: List<ListenStatsEvent>): List<StatsArtistEntry>
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
                val librarySongsForArtist = MusicLibrary.Artist[artistEntry.key]
                StatsArtistEntry(
                    artistName = artistEntry.key,
                    listenedMs = artistEntry.value,
                    thumb = librarySongsForArtist.firstOrNull()?.thumb?.toString() ?: fallbackThumbByArtist[artistEntry.key],
                    inLibrary = librarySongsForArtist.isNotEmpty()
                )
            }
    }

    fun buildAlbumEntries(periodEvents: List<ListenStatsEvent>): List<StatsAlbumEntry>
    {
        val eventsByAlbum = periodEvents.groupBy { it.album ?: defaultAlbum }

        return eventsByAlbum.entries
            .map { albumGroup ->
                val firstEvent = albumGroup.value.first()
                val librarySongsForAlbum = MusicLibrary.Album[albumGroup.key]
                val displayArtistName = firstEvent.albumArtists
                    ?: firstEvent.artists?.toMultipleArtists()?.toArtistsString()
                    ?: defaultArtistsName
                StatsAlbumEntry(
                    albumName = albumGroup.key,
                    artistName = displayArtistName,
                    listenedMs = albumGroup.value.sumOf { it.listenedMs },
                    thumb = librarySongsForAlbum.firstOrNull()?.thumb?.toString() ?: firstEvent.thumb,
                    inLibrary = librarySongsForAlbum.isNotEmpty()
                )
            }
            .sortedByDescending { it.listenedMs }
    }

    fun buildTrackEntries(periodEvents: List<ListenStatsEvent>): List<StatsTrackEntry>
    {
        val librarySongsByUri = MusicLibrary.songs.associateBy { it.uri?.toString() }
        val eventsByTrack = periodEvents.groupBy { it.uri ?: "${it.title}|${it.album}" }

        return eventsByTrack.entries
            .map { trackGroup ->
                val firstEvent = trackGroup.value.first()
                val libraryItem = firstEvent.uri?.let { librarySongsByUri[it] }
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
}
