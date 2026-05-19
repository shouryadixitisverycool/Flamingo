package yos.music.player.data.libraries

import androidx.compose.runtime.Stable

data class ArtistRelease(
    val albumName: String,
    val songs: List<YosMediaItem>,
    val releaseYear: Int?,
)

data class ArtistSections(
    val songs: List<YosMediaItem>,
    val albums: List<ArtistRelease>,
    val singlesAndEps: List<ArtistRelease>,
    val featuredOn: List<ArtistRelease>,
)

@Stable
object ArtistLibrary
{
    fun sortedArtists(sourceArtists: List<String>): List<String>
    {
        val distinctArtists = sourceArtists.distinct()
        val followedArtists = SettingsLibrary.FollowedArtists.toSet()
        return distinctArtists.filter { followedArtists.contains(it) } +
            distinctArtists.filterNot { followedArtists.contains(it) }
    }

    fun sectionsForArtist(artistName: String): ArtistSections
    {
        val allArtistSongs = allSongsForArtist(artistName)
        val primaryArtistSongs = primarySongsForArtist(artistName).ifEmpty { allArtistSongs }
        val primaryReleases = buildReleases(primaryArtistSongs)
        val primaryReleaseNames = primaryReleases.map { it.albumName }.toSet()
        val featuredReleases = buildReleases(
            allArtistSongs.filter { (it.album ?: defaultAlbum) !in primaryReleaseNames }
        )
        val singleAndEpReleases = primaryReleases.filter { it.looksLikeSingleOrEp() }.sortedForDisplay()
        val albumReleases = primaryReleases.filterNot { it.looksLikeSingleOrEp() }.sortedForDisplay()

        return ArtistSections(
            songs = primaryArtistSongs.sortedForPlayback(),
            albums = albumReleases,
            singlesAndEps = singleAndEpReleases,
            featuredOn = featuredReleases.sortedForDisplay(),
        )
    }

    fun songsForArtist(artistName: String): List<YosMediaItem>
    {
        return sectionsForArtist(artistName).songs
    }

    private fun allSongsForArtist(artistName: String): List<YosMediaItem>
    {
        return MusicLibrary.Artist[artistName].distinctBy { it.uri }
    }

    private fun primarySongsForArtist(artistName: String): List<YosMediaItem>
    {
        return allSongsForArtist(artistName).filter { it.isPrimaryArtistMatch(artistName) }
    }

    private fun buildReleases(songs: List<YosMediaItem>): List<ArtistRelease>
    {
        return songs.groupBy { it.album ?: defaultAlbum }
            .map { (albumName, albumSongs) ->
                ArtistRelease(
                    albumName = albumName,
                    songs = albumSongs.sortedForPlayback(),
                    releaseYear = albumSongs.mapNotNull { it.releaseYear ?: it.recordingYear }.maxOrNull(),
                )
            }
    }
}

private fun YosMediaItem.isPrimaryArtistMatch(artistName: String): Boolean
{
    val albumArtistsList = this.albumArtists?.toMultipleArtists().orEmpty().filter { it.isNotBlank() }
    if (albumArtistsList.isNotEmpty()) {
        return albumArtistsList.contains(artistName)
    }

    return (this.artistsList ?: defaultArtists).firstOrNull() == artistName
}

private fun ArtistRelease.looksLikeSingleOrEp(): Boolean
{
    val normalizedAlbumName = albumName.lowercase()
    val markerMatches = listOf(
        " - single",
        " (single)",
        " [single]",
        " - ep",
        " (ep)",
        " [ep]",
    ).any { normalizedAlbumName.contains(it) }

    if (markerMatches) {
        return true
    }

    val totalMinutes = songs.sumOf { it.duration } / 60000
    return songs.size <= 6 && totalMinutes <= 35
}

private fun List<ArtistRelease>.sortedForDisplay(): List<ArtistRelease>
{
    return this.sortedWith(
        compareByDescending<ArtistRelease> { it.releaseYear != null }
            .thenByDescending { it.releaseYear ?: Int.MIN_VALUE }
            .thenBy { it.albumName.lowercase() }
    )
}

private fun List<YosMediaItem>.sortedForPlayback(): List<YosMediaItem>
{
    return this.distinctBy { it.uri }
        .sortedWith(
            compareByDescending<YosMediaItem> { it.releaseYear ?: it.recordingYear ?: Int.MIN_VALUE }
                .thenBy { it.album ?: defaultAlbum }
                .thenBy { it.discNumber ?: Int.MAX_VALUE }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title ?: defaultTitle }
        )
}
