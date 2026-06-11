package yos.music.player.code

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import yos.music.player.data.libraries.ListenStatsEvent
import yos.music.player.data.libraries.ListenStatsLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.album
import yos.music.player.data.libraries.albumArtists
import yos.music.player.data.libraries.artists
import yos.music.player.data.libraries.thumb
import yos.music.player.data.libraries.title
import yos.music.player.data.libraries.uri

class ListenStatsTracker(private val player: Player)
{
    private val handler = Handler(Looper.getMainLooper())

    private var sessionMediaId: String? = null
    private var sessionMediaItem: MediaItem? = null
    private var sessionQualified = false
    private var sessionQualifyDayStartMs = 0L
    private var lastObservedPositionMs = -1L
    private var listenedMsByDayStart = LinkedHashMap<Long, Long>()
    private var ticksSinceLastPendingPersist = 0

    private val statsTickRunnable = object : Runnable
    {
        override fun run()
        {
            tick()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    fun start()
    {
        handler.post(statsTickRunnable)
    }

    fun stop()
    {
        handler.removeCallbacks(statsTickRunnable)
        finalizeSession()
    }

    fun onTrackChanged()
    {
        val newMediaId = player.currentMediaItem?.mediaId
        if (newMediaId == sessionMediaId) {return}

        finalizeSession()

        sessionMediaId = newMediaId
        sessionMediaItem = player.currentMediaItem
        sessionQualified = false
        sessionQualifyDayStartMs = 0L
        lastObservedPositionMs = -1L
        listenedMsByDayStart = LinkedHashMap()
        ticksSinceLastPendingPersist = 0
    }

    private fun tick()
    {
        if (!SettingsLibrary.ListenHistory) {return}

        val currentMediaItem = player.currentMediaItem ?: return
        if (currentMediaItem.mediaId != sessionMediaId) {onTrackChanged()}

        val isActuallyPlaying = player.isPlaying || player.playWhenReady
        val currentPositionMs = player.currentPosition
        val totalDurationMs = player.duration

        if (isActuallyPlaying && lastObservedPositionMs >= 0)
        {
            val positionDeltaMs = currentPositionMs - lastObservedPositionMs
            if (positionDeltaMs in 1..(TICK_INTERVAL_MS * 5 / 2))
            {
                val nowMs = System.currentTimeMillis()
                val dayStartMs = ListenStatsLibrary.dayStartMs(nowMs)
                listenedMsByDayStart[dayStartMs] = (listenedMsByDayStart[dayStartMs] ?: 0L) + positionDeltaMs
            }
        }

        lastObservedPositionMs = currentPositionMs

        if (!sessionQualified && totalDurationMs > 0)
        {
            val progressFraction = currentPositionMs.toDouble() / totalDurationMs.toDouble()
            if (progressFraction >= 0.5)
            {
                sessionQualified = true
                sessionQualifyDayStartMs = ListenStatsLibrary.dayStartMs(System.currentTimeMillis())
            }
        }

        if (sessionQualified)
        {
            ListenStatsManager.liveSessionEvents.value = buildSessionEvents()

            ticksSinceLastPendingPersist++
            if (ticksSinceLastPendingPersist >= PENDING_PERSIST_TICKS)
            {
                ticksSinceLastPendingPersist = 0
                ListenStatsManager.persistPendingEvents(ListenStatsManager.liveSessionEvents.value)
            }
        }
    }

    private fun finalizeSession()
    {
        if (sessionQualified && SettingsLibrary.ListenHistory)
        {
            ListenStatsManager.commitEvents(buildSessionEvents())
        }
        else
        {
            ListenStatsManager.liveSessionEvents.value = emptyList()
            ListenStatsManager.clearPendingEvents()
        }

        sessionMediaId = null
        sessionMediaItem = null
        sessionQualified = false
        sessionQualifyDayStartMs = 0L
        lastObservedPositionMs = -1L
        listenedMsByDayStart = LinkedHashMap()
        ticksSinceLastPendingPersist = 0
    }

    private fun buildSessionEvents(): List<ListenStatsEvent>
    {
        val mediaItem = sessionMediaItem ?: return emptyList()
        val nowMs = System.currentTimeMillis()

        return listenedMsByDayStart.entries
            .filter { it.value > 0 }
            .map { daySegment ->
                ListenStatsEvent(
                    uri = mediaItem.uri?.toString(),
                    title = mediaItem.title,
                    artists = mediaItem.artists?.joinToString("、"),
                    album = mediaItem.album,
                    albumArtists = mediaItem.albumArtists,
                    thumb = mediaItem.thumb?.toString(),
                    dayStartMs = daySegment.key,
                    timestampMs = nowMs,
                    listenedMs = daySegment.value,
                    countsAsPlay = daySegment.key == sessionQualifyDayStartMs
                )
            }
    }

    companion object
    {
        private const val TICK_INTERVAL_MS = 1000L
        private const val PENDING_PERSIST_TICKS = 10
    }
}
