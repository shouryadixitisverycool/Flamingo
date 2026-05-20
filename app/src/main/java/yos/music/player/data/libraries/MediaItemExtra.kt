package yos.music.player.data.libraries

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem

const val defaultArtistsName = "Unknown Artist"
val defaultArtists = listOf(defaultArtistsName)
const val defaultTitle = "Unknown Work"
const val defaultAlbum = "Unknown Album"
private val artistSplitExceptions = listOf("Tyler, The Creator")

val MediaItem.uri: Uri?
    get() = this.localConfiguration?.uri

val MediaItem.title: String?
    get() = this.mediaMetadata.title?.toString()

val MediaItem.writer: String?
    get() = this.mediaMetadata.writer?.toString()

val MediaItem.compilation: String?
    get() = this.mediaMetadata.compilation?.toString()

val MediaItem.composer: String?
    get() = this.mediaMetadata.composer?.toString()

val MediaItem.artists: List<String>?
    get() = this.mediaMetadata.artist?.toString()?.toMultipleArtists()

val MediaItem.artistsName: String?
    get() = this.mediaMetadata.artist?.toString()?.toMultipleArtists()?.toArtistsString()

val MediaItem.album: String?
    get() = this.mediaMetadata.albumTitle?.toString()

val MediaItem.albumArtists: String?
    get() = this.mediaMetadata.albumArtist?.toString()

val MediaItem.thumb: Uri?
    get() = this.mediaMetadata.artworkUri

val MediaItem.trackNumber: Int?
    get() = this.mediaMetadata.trackNumber

val MediaItem.discNumber: Int?
    get() = this.mediaMetadata.discNumber

val MediaItem.genre: String?
    get() = this.mediaMetadata.genre?.toString()

val MediaItem.recordingDay: Int?
    get() = this.mediaMetadata.recordingDay

val MediaItem.recordingMonth: Int?
    get() = this.mediaMetadata.recordingMonth

val MediaItem.recordingYear: Int?
    get() = this.mediaMetadata.recordingYear

val MediaItem.releaseYear: Int?
    get() = this.mediaMetadata.releaseYear

val MediaItem.extras: Bundle?
    get() = this.mediaMetadata.extras

val MediaItem.artistId: Long?
    get() = this.mediaMetadata.extras?.getLong("ArtistId")

val MediaItem.albumId: Long?
    get() = this.mediaMetadata.extras?.getLong("AlbumId")

val MediaItem.genreId: Long?
    get() = this.mediaMetadata.extras?.getLong("GenreId")

val MediaItem.author: String?
    get() = this.mediaMetadata.extras?.getString("Author")

val MediaItem.addDate: Long?
    get() = this.mediaMetadata.extras?.getLong("AddDate")

val MediaItem.duration: Long
    get() = this.mediaMetadata.extras?.getLong("Duration") ?: 0

val MediaItem.modifiedDate: Long?
    get() = this.mediaMetadata.extras?.getLong("ModifiedDate")

val MediaItem.cdTrackNumber: Int?
    get() = this.mediaMetadata.extras?.getInt("CdTrackNumber")

/*val MediaItem.samplingRate: Int
    get() = this.mediaMetadata.extras?.getInt("SamplingRate")?:0*/

/*
val MediaItem.bitrate: Int
    get() = this.mediaMetadata.extras?.getInt("Bitrate")?:0
*/

fun String.toMultipleArtists(): List<String> {
    val delimiters = buildList {
        add("、")
        SettingsLibrary.ArtistSplitSeparators
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { add(it) }
    }.distinct()

    var protectedArtistsText = this
    val protectedArtistMap = mutableMapOf<String, String>()

    artistSplitExceptions.forEachIndexed { index, artistException ->
        if (delimiters.any { artistException.contains(it) } && protectedArtistsText.contains(artistException)) {
            val placeholder = "__ARTIST_EXCEPTION_${index}__"
            protectedArtistsText = protectedArtistsText.replace(artistException, placeholder)
            protectedArtistMap[placeholder] = artistException
        }
    }

    val splitArtists = delimiters.fold(listOf(protectedArtistsText)) { currentArtists, delimiter ->
        currentArtists.flatMap { artist ->
            artist.split(delimiter).map { it.trim() }
        }
    }.map { protectedArtistMap[it] ?: it }
        .filter { it.isNotEmpty() }

    return splitArtists.ifEmpty { listOf(this.trim()) }
}

fun List<String>.toArtistsString(): String {
    return this.joinToString("、")
}
