@file:Suppress("SameParameterValue")

package yos.music.player.data.libraries

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastMap
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.funny.data_saver.core.mutableDataSaverListStateOf
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import uk.akane.libphonograph.constructor.ItemConstructor
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.reader.ReaderConfiguration
import uk.akane.libphonograph.reader.ReaderResult
import yos.music.player.UriTypeAdapter
import yos.music.player.code.MediaController
import yos.music.player.data.NormalSaver
import yos.music.player.data.SongListSaver

/*@Parcelize
@Stable
data class Music(
    val title: String,
    val artist: List<String>,
    val album: String,
    val path: String,
    val date: Long,
    val id: Long,
    var thumb: String?,
    val duration: Long = 0,
    val bitrate: Int,
    val samplingRate: Int
) : Parcelable {
    fun artistsToString(): String {
        return artist.joinToString("、")
    }
}*/

@Stable
data class Time(
    val min: String,
    val sec: String
)
@Parcelize
@Stable
data class PlayListV1(
    val mainMusicList: List<YosMediaItem>? = null,
    val playingMusicList: List<YosMediaItem>? = null,
    val nextInQueueMusicList: List<YosMediaItem>? = null,
    val historyMusicList: List<YosMediaItem>? = null,
    val musicPlaying: YosMediaItem? = null,
    val shuffleModeEnabled: Boolean = false,
    val playingMusicUris: List<String>? = null,
    val nextInQueueMusicUris: List<String>? = null,
    val historyMusicUris: List<String>? = null,
    val musicPlayingUri: String? = null,
) : Parcelable

@Parcelize
@Stable
data class PlayStatus(
    val music: YosMediaItem?,
    val position: Long,
    val shuffleModeEnabled: Boolean,
    val repeatMode: Int
) : Parcelable

@Parcelize
@Stable
data class Folder(val name: String, val path: String, val songs: List<YosMediaItem>) : Parcelable

@Parcelize
@Stable
data class YosMediaItem(
    val uri: Uri?,
    val mediaId: String?,
    val mimeType: String?,
    val title: String?,
    val writer: String?,
    val compilation: String?,
    val composer: String?,
    val artists: String?,
    val album: String?,
    val albumArtists: String?,
    val thumb: Uri?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val genre: String?,
    val recordingDay: Int?,
    val recordingMonth: Int?,
    val recordingYear: Int?,
    val releaseYear: Int?,
    val artistId: Long?,
    val albumId: Long?,
    val genreId: Long?,
    val author: String?,
    val addDate: Long?,
    val duration: Long,
    val modifiedDate: Long?,
    val cdTrackNumber: Int?
    //val samplingRate: Int,
    //val bitrate: Int
) : Parcelable

@Stable
@Parcelize
data class YosStringWrapper(val value: String) : Parcelable

@Stable
object MusicLibrary {
    // yos_player_core 负责歌曲列表 V1、播放状态记录

    private const val mmkvID = "yos_player_core"
    private const val playListKey = "yos_play_list_v1"
    private const val playStatusKey = "yos_player_play_status"

    private val gson by lazy { GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create() }
    private val mmkv by lazy { MMKV.mmkvWithID(mmkvID) }

    private var cachedSongSource: List<YosMediaItem>? = null
    private var cachedHiddenSongs: List<YosMediaItem>? = null
    private var cachedFolders: List<Folder>? = null
    private var cachedHiddenFolders: List<YosStringWrapper>? = null
    private var cachedVisibleSongs: List<YosMediaItem>? = null

    var hideSongs by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver,
        key = "hide_songs",
        initialValue = listOf<YosMediaItem>()
    )
        private set

    var folders by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver,
        key = "folders",
        initialValue = listOf<Folder>()
    )
        private set

    private var hideFoldersSaver by mutableDataSaverListStateOf(
        dataSaverInterface = NormalSaver, key = "hide_folders", initialValue = listOf<YosStringWrapper>()
    )

    fun hasAudioPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val hideFolders: List<String>
        get() = hideFoldersSaver.map { it.value }

    private var songSaver by mutableDataSaverListStateOf(
        dataSaverInterface = SongListSaver, key = "songs", initialValue = listOf<YosMediaItem>()
    )

    val songs: List<YosMediaItem>
        get() {
            if (
                cachedSongSource === songSaver &&
                cachedHiddenSongs === hideSongs &&
                cachedFolders === folders &&
                cachedHiddenFolders === hideFoldersSaver
            ) {
                cachedVisibleSongs?.let { return it }
            }

            val hiddenSongUris = hideSongs.mapNotNull { it.uri }.toSet()
            val hiddenFolderPaths = hideFoldersSaver.map { it.value }.toSet()
            val hiddenFolderSongUris = folders
                .asSequence()
                .filter { it.path in hiddenFolderPaths }
                .flatMap { it.songs.asSequence() }
                .mapNotNull { it.uri }
                .toSet()

            val visibleSongs = songSaver.filter { song ->
                song.uri !in hiddenSongUris && song.uri !in hiddenFolderSongUris
            }

            cachedSongSource = songSaver
            cachedHiddenSongs = hideSongs
            cachedFolders = folders
            cachedHiddenFolders = hideFoldersSaver
            cachedVisibleSongs = visibleSongs
            return visibleSongs
        }

    val artists
        get() = songs/*.distinctBy { it.artist }.map { it.artist }*/.flatMap {
            it.artistsList ?: defaultArtists
        }
            .distinct()

    val albums
        get() = songs.distinctBy { it.album ?: defaultAlbum }.map { it.album ?: defaultAlbum }

    @Stable
    object Album {
        operator fun get(albumName: String) =
            songs.filter { (it.album ?: defaultAlbum) == albumName }
    }

    @Stable
    object Artist {
        operator fun get(artistName: String) =
            songs.filter { (it.artistsList ?: defaultArtists).contains(artistName) }
    }

    fun updatePlayList(playListV1: PlayListV1) {
        updateData(playListKey, playListV1)
    }

    fun loadPlayList(): PlayListV1 {
        val loadedData = loadData(playListKey) ?: PlayListV1(null, null)
        return loadedData
    }

    fun MediaItem.toYosMediaItem(): YosMediaItem {
        return YosMediaItem(
            uri = this.localConfiguration?.uri,
            mediaId = this.mediaId,
            mimeType = this.localConfiguration?.mimeType,
            title = this.title,
            writer = this.writer,
            compilation = this.compilation,
            composer = this.composer,
            artists = this.mediaMetadata.artist?.toString(),
            album = this.album,
            albumArtists = this.albumArtists,
            thumb = this.thumb,
            trackNumber = this.trackNumber,
            discNumber = this.discNumber,
            genre = this.genre,
            recordingDay = this.recordingDay,
            recordingMonth = this.recordingMonth,
            recordingYear = this.recordingYear,
            releaseYear = this.releaseYear,
            artistId = this.artistId,
            albumId = this.albumId,
            genreId = this.genreId,
            author = this.author,
            addDate = this.addDate,
            duration = this.duration,
            modifiedDate = this.modifiedDate,
            cdTrackNumber = this.cdTrackNumber
            //samplingRate = this.samplingRate,
            //bitrate = this.bitrate
        )
    }

    fun YosMediaItem.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setUri(this.uri)
            .setMediaId(this.mediaId!!)
            .setMimeType(this.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(this.title)
                    .setWriter(this.writer)
                    .setCompilation(this.compilation)
                    .setComposer(this.composer)
                    .setArtist(this.artists)
                    .setAlbumTitle(this.album)
                    .setAlbumArtist(this.albumArtists)
                    .setArtworkUri(this.thumb)
                    .setTrackNumber(this.trackNumber)
                    .setDiscNumber(this.discNumber)
                    .setGenre(this.genre)
                    .setRecordingDay(this.recordingDay)
                    .setRecordingMonth(this.recordingMonth)
                    .setRecordingYear(this.recordingYear)
                    .setReleaseYear(this.releaseYear)
                    .setExtras(Bundle().apply {
                        this@toMediaItem.artistId?.let { putLong("ArtistId", it) }
                        this@toMediaItem.albumId?.let { putLong("AlbumId", it) }
                        this@toMediaItem.genreId?.let { putLong("GenreId", it) }
                        putString("Author", this@toMediaItem.author)
                        this@toMediaItem.addDate?.let { putLong("AddDate", it) }
                        putLong("Duration", this@toMediaItem.duration)
                        this@toMediaItem.modifiedDate?.let { putLong("ModifiedDate", it) }
                        this@toMediaItem.cdTrackNumber?.let { putInt("CdTrackNumber", it) }
                        //this@toMediaItem.samplingRate?.let { putInt("SamplingRate", it) }
                        //this@toMediaItem.bitrate?.let { putInt("Bitrate", it) }
                    })
                    .build()
            )
            .build()
    }

    fun updatePlayStatus(playStatus: PlayStatus) {
        updateData(playStatusKey, playStatus)
    }

    fun loadPlayStatus(): PlayStatus {
        return loadData(playStatusKey) ?: PlayStatus(null, 0L, false, 0)
    }

    private inline fun <reified T> updateData(key: String, value: T) {
        val json = gson.toJson(value)
        mmkv.encode(key, json)
    }

    private inline fun <reified T> loadData(key: String): T? {
        val json = mmkv.decodeString(key)
        return json?.let {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(it, type)
        }
    }

    fun hideFolder(folder: Folder) {
        updateFolderVisibility(folder, hide = true)
    }

    fun unHideFolder(folder: Folder) {
        updateFolderVisibility(folder, hide = false)
    }

    fun hideSong(song: YosMediaItem) {
        hideSongs = hideSongs + song
    }

    fun unHideSong(song: YosMediaItem) {
        hideSongs = hideSongs - song
    }

    /*fun removeSong(song: YosMediaItem) {
        folders = folders.map {
            if (it.songs.contains(song)) {
                return@map it.copy(
                    name = it.name,
                    songs = it.songs.toMutableList().apply { remove(song) })
            }
            it
        }
        hideSongs = hideSongs - song
    }*/

    private fun updateFolderVisibility(folder: Folder, hide: Boolean) {
        if (hide) {
            if (folders.any { it.path == folder.path }) {
                hideFoldersSaver = hideFoldersSaver.plus(YosStringWrapper(folder.path))
            }
        } else {
            if (hideFolders.any { it == folder.path }) {
                hideFoldersSaver = hideFoldersSaver.minus(YosStringWrapper(folder.path))
            }
        }
    }

    private val readerConfiguration = ReaderConfiguration(
        ItemConstructor { uri, mediaId, mimeType, title, writer, compilation,
                          composer, artist, albumTitle, albumArtist, artworkUri,
                          cdTrackNumber, trackNumber, discNumber, genre,
                          recordingDay, recordingMonth, recordingYear, releaseYear,
                          artistId, albumId, genreId, author, addDate,
                          duration, modifiedDate ->
            //val audioProperties = getAudioProperties(uri.path!!)
            return@ItemConstructor MediaItem
                .Builder()
                .setUri(uri)
                .setMediaId(mediaId.toString())
                .setMimeType(mimeType)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setTitle(title)
                        .setWriter(writer)
                        .setCompilation(compilation)
                        .setComposer(composer)
                        .setArtist(artist)
                        .setAlbumTitle(albumTitle)
                        .setAlbumArtist(albumArtist)
                        .setArtworkUri(artworkUri)
                        .setTrackNumber(trackNumber)
                        .setDiscNumber(discNumber)
                        .setGenre(genre)
                        .setRecordingDay(recordingDay)
                        .setRecordingMonth(recordingMonth)
                        .setRecordingYear(recordingYear)
                        .setReleaseYear(releaseYear)
                        .setExtras(Bundle().apply {
                            if (artistId != null) {
                                putLong("ArtistId", artistId)
                            }
                            if (albumId != null) {
                                putLong("AlbumId", albumId)
                            }
                            if (genreId != null) {
                                putLong("GenreId", genreId)
                            }
                            putString("Author", author)
                            if (addDate != null) {
                                putLong("AddDate", addDate)
                            }
                            if (duration != null) {
                                putLong("Duration", duration)
                            }
                            if (modifiedDate != null) {
                                putLong("ModifiedDate", modifiedDate)
                            }
                            cdTrackNumber?.toIntOrNull()
                                ?.let { it1 -> putInt("CdTrackNumber", it1) }
                            /*audioProperties?.let {
                                putInt("SamplingRate", it.first)
                                putInt("Bitrate", it.second)
                            }*/
                        })
                        .build(),
                ).build()
        },
        shouldFetchPlaylist = true,
        shouldIncludeExtraFormat = true
    )

    suspend fun scanMedia(context: Context): ReaderResult<MediaItem>? {
        return withContext(Dispatchers.IO) {
            if (!hasAudioPermission(context)) {
                return@withContext null
            }

            val result = Reader.readFromMediaStore(
                context,
                readerConfiguration
            )

            // 有层级结构的result.folderStructure.folderList[""].folderList
            // val folderList = result.folderStructure.folderList

            val convertedSongs = result.songList.fastMap {
                it.toYosMediaItem()
            }
            val songsByUri = convertedSongs.associateBy { it.uri }

            songSaver = convertedSongs

            result.shallowFolder.folderList.map {
                val name = it.key
                val path = it.value.songList.first().uri?.path?.substringBeforeLast("/")?:""
                val songs = it.value.songList.fastMap { thisSong ->
                    songsByUri[thisSong.uri] ?: thisSong.toYosMediaItem()
                }
                Folder(name, path, songs)
            }.let {
                folders = it
            }

            /*folders = folderList.map { (path, fileNode) ->
                Folder(
                    path,
                    fileNode.songList.toList()
                )
            }.filter { folder ->
                folder !in hideFolders
            }*/

            updatePlayList(
                PlayListV1(
                    playingMusicUris = MediaController.playingMusicList.value.orEmpty().mapNotNull { it.uri?.toString() },
                    nextInQueueMusicUris = MediaController.nextInQueueMusicList.value.mapNotNull { it.uri?.toString() },
                    historyMusicUris = MediaController.historyMusicList.value.mapNotNull { it.uri?.toString() },
                    musicPlayingUri = MediaController.musicPlaying.value?.uri?.toString(),
                    shuffleModeEnabled = MediaController.queueShuffleEnabled.value,
                )
            )

            result
        }
    }
}
