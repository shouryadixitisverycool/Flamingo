package yos.music.player.code

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import yos.music.player.data.libraries.MusicLibrary.toYosMediaItem

class ListenHistoryTracker(private val player: Player)
{
    private val handler = Handler(Looper.getMainLooper())
    private var currentMediaId: String? = null
    private var alreadyRecordedForCurrentTrack = false

    private val progressCheckRunnable = object : Runnable
    {
        override fun run()
        {
            checkProgress()
            handler.postDelayed(this, 2000)
        }
    }

    fun start()
    {
        handler.post(progressCheckRunnable)
    }

    fun stop()
    {
        handler.removeCallbacks(progressCheckRunnable)
    }

    fun onTrackChanged()
    {
        val newMediaId = player.currentMediaItem?.mediaId
        if (newMediaId != currentMediaId)
        {
            currentMediaId = newMediaId
            alreadyRecordedForCurrentTrack = false
        }
    }

    private fun checkProgress()
    {
        if (alreadyRecordedForCurrentTrack) {return}
        if (!player.isPlaying && !player.playWhenReady) {return}

        val currentMediaItem = player.currentMediaItem ?: return
        val totalDuration = player.duration
        val currentPosition = player.currentPosition

        if (totalDuration <= 0) {return}

        val progressFraction = currentPosition.toDouble() / totalDuration.toDouble()
        if (progressFraction >= 0.5)
        {
            alreadyRecordedForCurrentTrack = true
            ListenHistoryManager.recordPlay(currentMediaItem.toYosMediaItem())
        }
    }
}
