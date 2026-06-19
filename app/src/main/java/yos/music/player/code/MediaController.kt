package yos.music.player.code

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.ExtraData
import cn.lyric.getter.api.tools.Tools
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import com.blankj.utilcode.util.ResourceUtils.getDrawable
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.buildList
import yos.music.player.MainActivity
import yos.music.player.R
import yos.music.player.code.MediaController.mediaControl
import yos.music.player.code.MediaController.mediaSession
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.code.MediaController.onServiceRunning
import yos.music.player.code.utils.lrc.YosLrcFactory
import yos.music.player.code.utils.lrc.YosParsedLyricData
import yos.music.player.code.utils.lrc.YosTtmlFactory
import yos.music.player.code.utils.player.FadeExo
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.MusicLibrary.toYosMediaItem
import yos.music.player.data.libraries.PlayListV1
import yos.music.player.data.libraries.PlayStatus
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.thumb
import yos.music.player.data.libraries.uri
import yos.music.player.data.objects.MainViewModelObject
import yos.music.player.data.objects.MediaViewModelObject

@Stable
object MediaController {
    @Volatile
    private var controllerInitializationStarted = false

    @Stable
    val mainMusicList: List<YosMediaItem>
        get() = MusicLibrary.songs

    @Stable
    var playingMusicList = mutableStateOf<List<YosMediaItem>?>(null)

    @Stable
    var historyMusicList = mutableStateOf<List<YosMediaItem>>(emptyList())

    @Stable
    var orderedPlayingMusicList = mutableStateOf<List<YosMediaItem>>(emptyList())

    @Stable
    var queueShuffleEnabled = mutableStateOf(false)

    @Stable
    var mediaControl: MediaController? = null

    @Stable
    var musicPlaying = mutableStateOf<YosMediaItem?>(null)

    @Stable
    var mediaSession: MediaSession? = null

    private var statusBarLyricHandler: Handler? = null
    private var checkHookStatusRunnable: Runnable? = null
    private var updateLyricsRunnable: Runnable? = null

    fun ensureInitialized(context: Context) {
        if (mediaControl != null || controllerInitializationStarted) {
            return
        }

        controllerInitializationStarted = true
        val applicationContext = context.applicationContext
        val sessionToken = SessionToken(applicationContext, ComponentName(applicationContext, YosPlaybackService::class.java))
        val controllerFuture = androidx.media3.session.MediaController.Builder(applicationContext, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
                runCatching {
                    mediaControl = controllerFuture.get()
                    CoroutineScope(Dispatchers.IO).launch {
                        restoreSavedQueueState(play = false)
                    }
                }.onFailure {
                    controllerInitializationStarted = false
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private suspend fun restoreSavedQueueState(play: Boolean) {
        val playListData = MusicLibrary.loadPlayList()
        val playStatusData = MusicLibrary.loadPlayStatus()
        val songsByUri = MusicLibrary.songs.mapNotNull { song ->
            song.uri?.toString()?.let { uri -> uri to song }
        }.toMap()

        val restoredMusic = playListData.musicPlayingUri
            ?.let { songsByUri[it] }
            ?: playListData.musicPlaying
            ?: playStatusData.music

        if (restoredMusic == null) {
            return
        }

        val restoredPlayingMusicList = playListData.playingMusicUris
            ?.mapNotNull { songsByUri[it] }
            ?: playListData.playingMusicList
            ?: emptyList()

        val restoredHistoryMusicList = playListData.historyMusicUris
            ?.mapNotNull { songsByUri[it] }
            ?: playListData.historyMusicList
            ?: emptyList()

        restoreQueueState(
            restoredMusic,
            restoredPlayingMusicList,
            restoredHistoryMusicList,
            playStatusData.position,
            playListData.shuffleModeEnabled || playStatusData.shuffleModeEnabled,
            playStatusData.repeatMode,
            play
        )
    }

    fun onServiceRunning() {
        val handler = Handler(Looper.getMainLooper())
        val lyricAPI by lazy { API() }
        var lastLyric = listOf<Pair<Float, String>>()
        val base64 = Tools.drawableToBase64(getDrawable(R.drawable.flamingo_icon_notification)!!)
        var statusBarLyricEnabled: Boolean
        var hooked = false

        statusBarLyricHandler?.let { existingHandler ->
            checkHookStatusRunnable?.let { existingHandler.removeCallbacks(it) }
            updateLyricsRunnable?.let { existingHandler.removeCallbacks(it) }
        }

        statusBarLyricHandler = handler

        checkHookStatusRunnable = object : Runnable {
            override fun run() {
                hooked = lyricAPI.hasEnable
                SettingsLibrary.StatusBarLyricHooked = hooked
                handler.postDelayed(this, 1500)
            }
        }

        updateLyricsRunnable = object : Runnable {
            override fun run() {
                var nextDelay = 500L
                runCatching {
                    var currentLyricIndex: Int
                    var isPlaying: Boolean?
                    var liveTime: Long

                    isPlaying = mediaControl?.isPlaying

                    runCatching {
                        currentLyricIndex = MainViewModelObject.syncLyricIndex.intValue

                        if (isPlaying == true) {
                            liveTime = mediaControl?.currentPosition ?: 0

                            val lrcEntries = MediaViewModelObject.lrcEntries.value

                            val nextIndex = lrcEntries.nextLyricIndex(liveTime)

                            val sendLyric = fun() {
                                try {
                                    MainViewModelObject.syncLyricIndex.intValue = currentLyricIndex
                                    statusBarLyricEnabled = SettingsLibrary.StatusBarLyricEnabled


                                    val line = lrcEntries[currentLyricIndex]
                                    if (line == lastLyric) {
                                        return
                                    }

                                    val lyric = StringBuffer("")
                                    line.forEachIndexed { charIndex, char ->
                                        if (charIndex >= line.size - 1) return@forEachIndexed
                                        lyric.append(char.second)
                                    }

                                    val lyricResult = lyric.toString()

                                    if (statusBarLyricEnabled && hooked) {
                                        lyricAPI.sendLyric(
                                            lyricResult,
                                            extra = ExtraData().apply {
                                                customIcon = true
                                                base64Icon = base64
                                            }
                                        )
                                    }

                                    // YosPlaybackService().sendLyricTicker(lyricResult)

                                    lastLyric = line
                                } catch (_: Exception) {
                                }
                            }

                            if (nextIndex != -1) {
                                if (nextIndex - 1 != currentLyricIndex) {
                                    currentLyricIndex = nextIndex - 1
                                }
                                if (currentLyricIndex != -1) {
                                    sendLyric()
                                }
                            } else if (currentLyricIndex != lrcEntries.size - 1) {
                                currentLyricIndex = lrcEntries.size - 1
                                if (currentLyricIndex != -1) {
                                    sendLyric()
                                }
                            }

                            val nextLyricTime = lrcEntries.getOrNull(currentLyricIndex + 1)?.firstOrNull()?.first?.toLong()
                            if (nextLyricTime != null) {
                                nextDelay = (nextLyricTime - liveTime).coerceIn(70L, 500L)
                            }
                        }
                    }
                }

                handler.postDelayed(this, nextDelay)
            }
        }

        checkHookStatusRunnable?.let { handler.post(it) }
        updateLyricsRunnable?.let { handler.post(it) }
    }

    private fun List<List<Pair<Float, String>>>.nextLyricIndex(liveTime: Long): Int {
        var low = 0
        var high = lastIndex
        var result = -1

        while (low <= high) {
            val middle = (low + high) ushr 1
            val lineTime = getOrNull(middle)?.firstOrNull()?.first ?: return -1
            if (lineTime >= liveTime) {
                result = middle
                high = middle - 1
            } else {
                low = middle + 1
            }
        }

        return result
    }

    fun stopStatusBarLyricUpdater() {
        statusBarLyricHandler?.let { handler ->
            checkHookStatusRunnable?.let { handler.removeCallbacks(it) }
            updateLyricsRunnable?.let { handler.removeCallbacks(it) }
        }
        statusBarLyricHandler = null
        checkHookStatusRunnable = null
        updateLyricsRunnable = null
    }



    suspend fun prepare(
        music: YosMediaItem,
        thisMusicList: List<YosMediaItem>,
        position: Long = 0L,
        shuffleModeEnabled: Boolean = false,
        repeatMode: Int? = null,
        play: Boolean = true
    ) {
        if (thisMusicList.isEmpty()) {
            return
        }

        val targetIndex = thisMusicList.indexOfFirst { it.uri == music.uri }.let {
            if (it == -1) {
                0
            } else {
                it
            }
        }
        val currentMusic = thisMusicList[targetIndex]
        val orderedQueue = if (shuffleModeEnabled) {
            buildList {
                add(currentMusic)
                addAll(
                    thisMusicList.filterIndexed { index, _ ->
                        index != targetIndex
                    }.shuffled()
                )
            }
        } else {
            thisMusicList
        }
        val currentQueueIndex = if (shuffleModeEnabled) {
            0
        } else {
            targetIndex
        }

        val resolvedRepeatMode = repeatMode ?: withContext(Dispatchers.Main) {
            mediaControl?.repeatMode ?: REPEAT_MODE_OFF
        }

        withContext(Dispatchers.Main) {
            mediaControl?.setMediaItems(
                orderedQueue.map { it.toMediaItem() },
                currentQueueIndex,
                position
            )
            mediaControl?.prepare()
            mediaControl?.repeatMode = resolvedRepeatMode
            mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
        }

        orderedPlayingMusicList.value = orderedQueue
        queueShuffleEnabled.value = shuffleModeEnabled
        syncQueueState(orderedQueue, currentQueueIndex, currentMusic)

        if (play) {
            withContext(Dispatchers.Main) {
                mediaControl?.fadePlay()
            }
        }

        saveQueueState()
    }

    suspend fun addToQueue(music: YosMediaItem): Boolean {
        return addToQueue(listOf(music))
    }

    suspend fun addToQueue(musicList: List<YosMediaItem>): Boolean {
        if (musicList.isEmpty()) {
            return false
        }

        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)

        if (currentQueue.isEmpty()) {
            prepare(
                musicList.first(),
                musicList,
                play = false,
                shuffleModeEnabled = queueShuffleEnabled.value
            )
            return true
        }

        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val insertAt = if (queueShuffleEnabled.value) {
            (currentIndex + 1).coerceAtMost(currentQueue.size)
        } else {
            currentQueue.size
        }

        val updatedQueue = currentQueue.toMutableList().also {
            it.addAll(insertAt, musicList)
        }

        withContext(Dispatchers.Main) {
            controller.addMediaItems(insertAt, musicList.map { it.toMediaItem() })
        }

        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex)
        saveQueueState()

        return true
    }

    suspend fun playNext(musicList: List<YosMediaItem>): Boolean {
        if (musicList.isEmpty()) {
            return false
        }

        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)

        if (currentQueue.isEmpty()) {
            prepare(
                musicList.first(),
                musicList,
                play = false,
                shuffleModeEnabled = queueShuffleEnabled.value
            )
            return true
        }

        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val insertAt = (currentIndex + 1).coerceAtMost(currentQueue.size)
        val updatedQueue = currentQueue.toMutableList().also {
            it.addAll(insertAt, musicList)
        }

        withContext(Dispatchers.Main) {
            controller.addMediaItems(insertAt, musicList.map { it.toMediaItem() })
        }

        orderedPlayingMusicList.value = updatedQueue
        syncQueueState(updatedQueue, currentIndex)
        saveQueueState()

        return true
    }

    suspend fun toggleShuffleMode(): Boolean {
        val controller = mediaControl ?: return false
        val currentQueue = currentQueueSnapshot(controller)

        if (currentQueue.isEmpty()) {
            queueShuffleEnabled.value = !queueShuffleEnabled.value
            withContext(Dispatchers.Main) {
                controller.shuffleModeEnabled = false
                YosPlaybackService().setCustomButtons(controller)
            }
            saveQueueState()
            return true
        }

        val currentIndex = withContext(Dispatchers.Main) {
            controller.currentMediaItemIndex.coerceAtLeast(0)
        }
        val updatedShuffleEnabled = !queueShuffleEnabled.value
        val history = currentQueue.take(currentIndex)
        val currentMusic = currentQueue.getOrNull(currentIndex) ?: return false
        val upcoming = currentQueue.drop(currentIndex + 1)
        val updatedQueue = if (updatedShuffleEnabled) {
            buildList {
                addAll(history)
                add(currentMusic)
                addAll(upcoming.shuffled())
            }
        } else {
            currentQueue
        }

        withContext(Dispatchers.Main) {
            controller.shuffleModeEnabled = false
            if (upcoming.isNotEmpty()) {
                controller.replaceMediaItems(
                    currentIndex + 1,
                    controller.mediaItemCount,
                    updatedQueue.drop(currentIndex + 1).map { it.toMediaItem() }
                )
            }
            YosPlaybackService().setCustomButtons(controller)
        }

        orderedPlayingMusicList.value = updatedQueue
        queueShuffleEnabled.value = updatedShuffleEnabled
        syncQueueState(updatedQueue, history.size, currentMusic)
        saveQueueState()

        return true
    }

    suspend fun restoreQueueState(
        music: YosMediaItem,
        upcomingMusicList: List<YosMediaItem>,
        historyQueue: List<YosMediaItem>,
        position: Long,
        shuffleModeEnabled: Boolean,
        repeatMode: Int,
        play: Boolean = false,
    ) {
        val restoredQueue = buildList {
            addAll(historyQueue)
            add(music)
            addAll(upcomingMusicList)
        }

        if (restoredQueue.isEmpty()) {
            return
        }

        val currentQueueIndex = historyQueue.size.coerceAtMost(restoredQueue.lastIndex)

        withContext(Dispatchers.Main) {
            mediaControl?.setMediaItems(
                restoredQueue.map { it.toMediaItem() },
                currentQueueIndex,
                position
            )
            mediaControl?.prepare()
            mediaControl?.repeatMode = repeatMode
            mediaControl?.shuffleModeEnabled = false
            mediaControl?.let { YosPlaybackService().setCustomButtons(it) }
        }

        orderedPlayingMusicList.value = restoredQueue
        queueShuffleEnabled.value = shuffleModeEnabled
        syncQueueState(restoredQueue, currentQueueIndex, music)

        if (play) {
            withContext(Dispatchers.Main) {
                mediaControl?.fadePlay()
            }
        }

        saveQueueState()
    }

    private suspend fun currentQueueSnapshot(controller: androidx.media3.session.MediaController): List<YosMediaItem> {
        orderedPlayingMusicList.value.takeIf { it.isNotEmpty() }?.let {
            return it
        }

        return withContext(Dispatchers.Main) {
            List(controller.mediaItemCount) { index ->
                controller.getMediaItemAt(index).toYosMediaItem()
            }
        }
    }

    private fun syncQueueState(
        orderedQueue: List<YosMediaItem>,
        currentQueueIndex: Int,
        currentMusic: YosMediaItem? = null,
    ) {
        orderedPlayingMusicList.value = orderedQueue

        if (orderedQueue.isEmpty()) {
            historyMusicList.value = emptyList()
            playingMusicList.value = emptyList()
            musicPlaying.value = null
            return
        }

        val boundedIndex = currentQueueIndex.coerceIn(0, orderedQueue.lastIndex)
        val resolvedMusic = currentMusic ?: orderedQueue[boundedIndex]

        historyMusicList.value = orderedQueue.take(boundedIndex)
        playingMusicList.value = orderedQueue.drop(boundedIndex + 1)
        musicPlaying.value = resolvedMusic
        MediaViewModelObject.bitmap.value = resolvedMusic.thumb
        MainViewModelObject.syncLyricIndex.intValue = -1
    }

    fun syncQueueStateFromController(controller: Player, currentMusic: YosMediaItem? = null) {
        val orderedQueue = if (
            orderedPlayingMusicList.value.isNotEmpty() &&
            orderedPlayingMusicList.value.size == controller.mediaItemCount
        ) {
            orderedPlayingMusicList.value
        } else {
            List(controller.mediaItemCount) { index ->
                controller.getMediaItemAt(index).toYosMediaItem()
            }
        }

        syncQueueState(orderedQueue, controller.currentMediaItemIndex.coerceAtLeast(0), currentMusic)
    }

    fun saveQueueState() {
        MusicLibrary.updatePlayList(
            PlayListV1(
                playingMusicUris = playingMusicList.value.orEmpty().mapNotNull { it.uri?.toString() },
                historyMusicUris = historyMusicList.value.mapNotNull { it.uri?.toString() },
                musicPlayingUri = musicPlaying.value?.uri?.toString(),
                shuffleModeEnabled = queueShuffleEnabled.value,
            )
        )
    }

    fun onCase(mediaItem: YosMediaItem) {
        CoroutineScope(Dispatchers.IO).launch {
            refresh(mediaItem)
        }
    }

    private var refreshJob: CompletableJob? = null

    private fun refresh(music: YosMediaItem) {
        refreshJob?.cancel()
        refreshJob = Job()

        val scope = CoroutineScope(Dispatchers.IO + refreshJob!!)

        scope.launch {
            musicPlaying.value = music
        }

        scope.launch {
            // val bitmap: MutableState<String?> = MediaViewModelObject.bitmap
            // bitmap.value = music.thumb
            MediaViewModelObject.bitmap.value = music.thumb
        }

        scope.launch {
            MainViewModelObject.syncLyricIndex.intValue = -1
        }
    }
}

class YosPlaybackService : MediaSessionService() {
    private val notificationID = 1145
    private val channelID = "YosMediaControllerChannel"

    private val shuffleMode = "shuffle_mode"
    private val repeatMode = "repeat_mode"

    companion object {
        private const val FLAG_ALWAYS_SHOW_TICKER = 0x1000000
        private const val FLAG_ONLY_UPDATE_TICKER = 0x2000000
    }

    @OptIn(UnstableApi::class)
    private fun setCustomButtons(player: ForwardingPlayer) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (yos.music.player.code.MediaController.queueShuffleEnabled.value) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    fun setCustomButtons(player: MediaController) {
        if (SettingsLibrary.NotificationEnableIcon) {
            val useSmallerIcon = SettingsLibrary.NotificationSmallerIcon

            val shuffleButtonIcon =
                if (yos.music.player.code.MediaController.queueShuffleEnabled.value) {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle else R.drawable.ic_shuffle
                } else {
                    if (useSmallerIcon) R.drawable.ic_mini_shuffle_off else R.drawable.ic_shuffle_off
                }
            val shuffleButton = CommandButton.Builder()
                .setIconResId(shuffleButtonIcon)
                .setDisplayName(shuffleMode)
                .setSessionCommand(SessionCommand(shuffleMode, Bundle()))
                .build()

            val repeatButtonIcon =
                when (player.repeatMode) {
                    REPEAT_MODE_ONE -> if (useSmallerIcon) R.drawable.ic_mini_repeat_one else R.drawable.ic_repeat_one
                    REPEAT_MODE_ALL -> if (useSmallerIcon) R.drawable.ic_mini_repeat else R.drawable.ic_repeat
                    else -> if (useSmallerIcon) R.drawable.ic_mini_repeat_off else R.drawable.ic_repeat_off
                }
            val repeatButton = CommandButton.Builder()
                .setIconResId(repeatButtonIcon)
                .setDisplayName(repeatMode)
                .setSessionCommand(SessionCommand(repeatMode, Bundle()))
                .build()

            mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
        } else {
            mediaSession?.setCustomLayout(emptyList())
        }
    }

    /*fun sendLyricTicker(lyric: String) {
        val notification = NotificationCompat.Builder(this, channelID).apply {
            setTicker(lyric)
            setSmallIcon(R.drawable.flamingo_icon_notification)
        }.build().also {
            it.extras.putInt("ticker_icon", R.drawable.flamingo_icon_notification)
            it.extras.putBoolean("ticker_icon_switch", true)
            it.flags = it.flags.or(FLAG_ALWAYS_SHOW_TICKER).or(FLAG_ONLY_UPDATE_TICKER)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(notificationID, notification)
    }*/

    private var saveJob: Job? = null

    fun saveDataWithDelay() {
        saveJob?.cancel()
        saveJob = CoroutineScope(Dispatchers.IO).launch {
            delay(200)
            withContext(Dispatchers.Main) {
                saveData()
            }
        }
    }

    private fun saveData() {
        if (musicPlaying.value != null && mediaControl != null) {
            MusicLibrary.updatePlayStatus(
                PlayStatus(
                    musicPlaying.value,
                    mediaControl?.currentPosition ?: 0,
                    yos.music.player.code.MediaController.queueShuffleEnabled.value,
                    mediaControl?.repeatMode ?: REPEAT_MODE_ALL
                )
            )
        }
        yos.music.player.code.MediaController.saveQueueState()
    }

    private fun prefetchArtwork(artwork: Any?) {
        if (artwork == null) {
            return
        }

        imageLoader.enqueue(
            ImageRequest.Builder(this)
                .data(artwork)
                .memoryCacheKey(artwork.toString())
                .placeholderMemoryCacheKey(artwork.toString())
                .allowHardware(true)
                .size(128)
                .precision(Precision.INEXACT)
                .build()
        )
    }

    private fun prefetchAdjacentArtwork(player: Player) {
        if (player.hasNextMediaItem()) {
            prefetchArtwork(player.getMediaItemAt(player.nextMediaItemIndex).thumb)
        }

        if (player.hasPreviousMediaItem()) {
            prefetchArtwork(player.getMediaItemAt(player.previousMediaItemIndex).thumb)
        }
    }

    private fun loadParsedLyrics(localAudioPath: String?): YosParsedLyricData {
        val embeddedLyrics = if (localAudioPath != null) {
            AudioMetadataUtils.loadEmbeddedLyrics(localAudioPath)
        } else {
            null
        }

        if (!embeddedLyrics.isNullOrBlank() && YosTtmlFactory.isTtmlText(embeddedLyrics)) {
            parseTtmlLyrics(embeddedLyrics, "embedded TTML")?.let { return it }
        }

        loadSidecarTtmlLyrics(localAudioPath)?.let { return it }

        val lrcContent = if (!embeddedLyrics.isNullOrBlank() && !YosTtmlFactory.isTtmlText(embeddedLyrics)) {
            embeddedLyrics
        } else {
            loadSidecarLrcLyrics(localAudioPath)
        }

        return if (lrcContent.isNullOrBlank()) {
            emptyParsedLyrics()
        } else {
            parseLrcLyrics(lrcContent)
        }
    }

    private fun loadSidecarTtmlLyrics(localAudioPath: String?): YosParsedLyricData? {
        val sidecarBasePath = localAudioPath?.toSidecarBasePath() ?: return null
        val sidecarPaths = listOf(
            "$sidecarBasePath.ttml",
            "$sidecarBasePath.xml"
        )

        sidecarPaths.forEach { sidecarPath ->
            val sidecarContent = AudioMetadataUtils.loadTtmlFile(this, sidecarPath) ?: return@forEach
            if (!YosTtmlFactory.isTtmlText(sidecarContent)) {
                Log.d("FlamingoLyrics", "Ignoring non-TTML sidecar lyric file: $sidecarPath")
                return@forEach
            }
            parseTtmlLyrics(sidecarContent, sidecarPath)?.let { return it }
        }

        return null
    }

    private fun loadSidecarLrcLyrics(localAudioPath: String?): String? {
        val sidecarBasePath = localAudioPath?.toSidecarBasePath() ?: return null
        val lrcPath = "$sidecarBasePath.lrc"
        Log.d("FlamingoLyrics", "Attempting sidecar LRC path: $lrcPath")
        return AudioMetadataUtils.loadLrcFile(this, lrcPath)
    }

    private fun parseTtmlLyrics(ttmlContent: String, sourceLabel: String): YosParsedLyricData? {
        val startTime = System.nanoTime()
        val parsedLyrics = YosTtmlFactory().formatTtmlEntries(ttmlContent)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000f
        if (parsedLyrics == null) {
            Log.d("FlamingoLyrics", "Failed parsing TTML lyrics from $sourceLabel")
        } else {
            Log.d(
                "FlamingoLyrics",
                "Parsed TTML lyrics from $sourceLabel entries=${parsedLyrics.entries.size} elapsedMs=$elapsedMs"
            )
        }
        return parsedLyrics
    }

    private fun parseLrcLyrics(lrcContent: String): YosParsedLyricData {
        val entries = YosLrcFactory().formatLrcEntries(lrcContent)
        val endTimes = entries.mapIndexed { index, line ->
            entries.getOrNull(index + 1)?.firstOrNull()?.first
                ?: line.lastOrNull()?.first
                ?: line.firstOrNull()?.first
                ?: 0f
        }
        val emptySecondaryText = List(entries.size) { null as String? }

        return YosParsedLyricData(
            entries = entries,
            endTimes = endTimes,
            otherSideForLines = MediaViewModelObject.otherSideForLines.toList(),
            transliterations = emptySecondaryText,
            subtitles = emptySecondaryText,
            isTtml = false
        )
    }

    private fun emptyParsedLyrics(): YosParsedLyricData {
        return YosParsedLyricData(
            entries = emptyList(),
            endTimes = emptyList(),
            otherSideForLines = emptyList(),
            transliterations = emptyList(),
            subtitles = emptyList(),
            isTtml = false
        )
    }

    private fun applyParsedLyrics(parsedLyrics: YosParsedLyricData) {
        MediaViewModelObject.lrcEntries.value = parsedLyrics.entries
        MediaViewModelObject.otherSideForLines.clear()
        MediaViewModelObject.otherSideForLines.addAll(parsedLyrics.otherSideForLines)
        MediaViewModelObject.updateLyricMetadata(
            endTimes = parsedLyrics.endTimes,
            transliterations = parsedLyrics.transliterations,
            subtitles = parsedLyrics.subtitles,
            ttmlLyrics = parsedLyrics.isTtml
        )
    }

    private fun String.toSidecarBasePath(): String {
        val extensionStartIndex = lastIndexOf('.')
        return if (extensionStartIndex > 0) {
            substring(0, extensionStartIndex)
        } else {
            this
        }
    }

    private var listenHistoryTracker: ListenHistoryTracker? = null
    private var listenStatsTracker: ListenStatsTracker? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(
            this,
            YosRenderFactory(this)
                .setEnableAudioFloatOutput(
                    SettingsLibrary.AudioFloatOutput
                )
                .setEnableDecoderFallback(true)
                .setEnableAudioTrackPlaybackParams(
                    SettingsLibrary.HardwareAudioTrackPlayBackParams
                )
                .setExtensionRendererMode(
                    when (SettingsLibrary.Codec) {
                        "System" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                        else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    }
                )
        )
            .setAudioAttributes(
                audioAttributes,
                SettingsLibrary.AudioAttributes
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                player.fadePlay()
            }

            override fun pause() {
                player.fadePause()
            }

            override fun isPlaying(): Boolean {
                return FadeExo.targetStatus != 0
            }
        }

        listenHistoryTracker = ListenHistoryTracker(player)
        listenHistoryTracker?.start()

        listenStatsTracker = ListenStatsTracker(player)
        listenStatsTracker?.start()

        forwardingPlayer.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    runCatching {

                        if (tracks.isEmpty) return@runCatching

                        val path = player.currentMediaItem?.uri

                        var samplingRate = 0
                        var bitrate = 0
                        var haveJOC = false

                        for (i in tracks.groups) {
                            for (j in 0 until i.length) {
                                if (!i.isTrackSelected(j)) continue
                                val trackFormat = i.getTrackFormat(j)
                                samplingRate = trackFormat.sampleRate
                                bitrate = trackFormat.bitrate / 1000
                                haveJOC =
                                    trackFormat.sampleMimeType?.contains("-joc", ignoreCase = true)
                                        ?: false
                                break
                            }
                        }

                        val thisPath = AudioMetadataUtils.resolveLocalAudioFilePath(
                            this@YosPlaybackService,
                            path
                        ) ?: path?.path
                        Log.d(
                            "FlamingoLyrics",
                            "Track changed uri=$path resolvedPath=$thisPath mediaId=${player.currentMediaItem?.mediaId}"
                        )

                        val parsedLyrics = loadParsedLyrics(thisPath)
                        applyParsedLyrics(parsedLyrics)
                        Log.d(
                            "FlamingoLyrics",
                            "Loaded lyrics parsedEntries=${parsedLyrics.entries.size} isTtml=${parsedLyrics.isTtml} otherSideCount=${MediaViewModelObject.otherSideForLines.size}"
                        )

                        if (thisPath != null) {
                            // MediaViewModelObject.isDolby.value = thisPath.endsWith(".m4a")
                            // 改为 JOC 判断

                            if (samplingRate == 0 || bitrate == 0) {
                                val audioInfo = AudioMetadataUtils.getQualityInfos(thisPath)
                                if (samplingRate == 0) {
                                    samplingRate = audioInfo.second
                                } else {
                                    bitrate = audioInfo.first
                                }
                            }
                        }

                        MediaViewModelObject.isDolby.value = haveJOC
                        MediaViewModelObject.samplingRate.intValue = samplingRate
                        MediaViewModelObject.bitrate.intValue = bitrate

                    }.onFailure { throwable ->
                        Log.e("FlamingoLyrics", "onTracksChanged lyric processing failed", throwable)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    /*mediaSession?.let { MediaController.sendNotification(it,context) }*/
                    listenHistoryTracker?.onTrackChanged()
                    listenStatsTracker?.onTrackChanged()

                    mediaItem?.let {
                        yos.music.player.code.MediaController.syncQueueStateFromController(
                            player,
                            it.toYosMediaItem()
                        )
                        yos.music.player.code.MediaController.onCase(
                            it.toYosMediaItem()
                        )
                    }

                    runCatching {
                        prefetchAdjacentArtwork(player)
                    }

                    // Sleep timer hook (PRD §5.6.2 FR-ST-6).
                    // Fires EndOfTrack on every transition; fires EndOfQueue
                    // only when the player has no successor.
                    runCatching {
                        val hasNext = player.hasNextMediaItem()
                        SleepTimer.onMediaItemTransition(hasNext = hasNext)
                    }

                    super.onMediaItemTransition(mediaItem, reason)
                }

                /*override fun onIsPlayingChanged(isPlaying: Boolean) {
                    saveData()
                    super.onIsPlayingChanged(isPlaying)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    saveData()
                    super.onRepeatModeChanged(repeatMode)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    saveData()
                    super.onShuffleModeEnabledChanged(shuffleModeEnabled)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_BUFFERING) {
                        saveData()
                    }
                    super.onPlaybackStateChanged(playbackState)
                }*/

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    MediaViewModelObject.isPlaying.value = isPlaying
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    super.onEvents(player, events)

                    if (events.containsAny(
                            Player.EVENT_PLAY_WHEN_READY_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
                        )
                    ) {
                        saveDataWithDelay()
                    }
                }

            }
        )

        /*val repeatButton = CommandButton.Builder()
            .setIconResId(android.R.drawable.ic_media_rew)
            .setSessionCommand(SessionCommand(SAVE_TO_FAVORITES, Bundle()))
            .build()*/

        @Suppress("DEPRECATION")
        class YosMediaSessionCallback : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands =
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(shuffleMode, Bundle.EMPTY))
                        .add(SessionCommand(repeatMode, Bundle.EMPTY))
                        .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == shuffleMode) {
                    CoroutineScope(Dispatchers.IO).launch {
                        yos.music.player.code.MediaController.toggleShuffleMode()
                    }
                } else if (customCommand.customAction == repeatMode) {
                    when (player.repeatMode) {
                        REPEAT_MODE_OFF -> {
                            player.repeatMode = REPEAT_MODE_ALL
                        }

                        REPEAT_MODE_ALL -> {
                            player.repeatMode = REPEAT_MODE_ONE
                        }

                        else -> {
                            player.repeatMode = REPEAT_MODE_OFF
                        }
                    }
                    setCustomButtons(forwardingPlayer)
                }
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS)
                )
            }
            /*override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            player.fadePlay()
                        }

                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            player.fadePause()
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            player.seekToNextMediaItem()
                        }

                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            player.seekToPreviousMediaItem()
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }*/
        }

        mediaSession =
            MediaSession
                .Builder(this, forwardingPlayer)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .setShowPlayButtonIfPlaybackIsSuppressed(true)
                .setCallback(YosMediaSessionCallback())
                .build()
        /*
                val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
                val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                mediaButtonIntent.component = mediaButtonReceiver
                val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                mediaSession.setMediaButtonReceiver(pendingIntent)
        */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Flamingo Media Control"
            val descriptionText = "Flamingo Media Control Notification Channel"
            val importance = NotificationManager.IMPORTANCE_NONE
            val channel = NotificationChannel(channelID, name, importance).apply {
                description = descriptionText
                enableVibration(false)
                vibrationPattern = longArrayOf(0)
                setSound(null, null)
            }
            val notificationManager: NotificationManager =
                ContextCompat.getSystemService(
                    this,
                    NotificationManager::class.java
                ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationProvider =
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(notificationID)
                .setChannelId(channelID)
                .build()

        /*DefaultMediaNotificationProvider(
            this,
            {
                notificationID
            },
            channelID,
            notificationID
        )*/

        notificationProvider.setSmallIcon(R.drawable.flamingo_icon_notification)

        this.setMediaNotificationProvider(notificationProvider)

        setCustomButtons(forwardingPlayer)

        onServiceRunning()
    }

    override fun onDestroy() {
        yos.music.player.code.MediaController.stopStatusBarLyricUpdater()
        listenHistoryTracker?.stop()
        listenHistoryTracker = null
        listenStatsTracker?.stop()
        listenStatsTracker = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession
}
