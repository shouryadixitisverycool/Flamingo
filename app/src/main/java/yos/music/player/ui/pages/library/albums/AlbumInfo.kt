package yos.music.player.ui.pages.library.albums

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.github.promeg.pinyinhelper.Pinyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.addMusic
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.pages.library.MusicDetailActionPill
import yos.music.player.ui.pages.library.MusicDetailCircleButton
import yos.music.player.ui.pages.library.MusicDetailPage
import yos.music.player.ui.pages.library.MusicDetailPillButton
import yos.music.player.ui.pages.library.MusicDetailPillDivider
import yos.music.player.ui.pages.library.MusicList
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.ActionItem
import yos.music.player.ui.widgets.basic.ActionSheet
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosBottomSheetDialog
import yos.music.player.ui.widgets.playlist.PlayListPickerContent

private enum class AlbumSortOption {
    TrackNumber,
    Title,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumInfo(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
) {
    val albumName = rememberSaveable(key = "AlbumInfo_albumName") {
        mutableStateOf(LibraryObject.getTargetAlbumName())
    }
    val albumSongs = MusicLibrary.Album[albumName.value]

    val showEmptyState = remember(albumName.value, albumSongs) {
        derivedStateOf {
            albumName.value.isEmpty() || albumSongs.isEmpty()
        }
    }

    if (showEmptyState.value) {
        Title(
            title = stringResource(id = R.string.page_library_album_info_title),
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("AlbumInfo_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.tip_no_album_info),
                        fontSize = 18.sp,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searchText = rememberSaveable(key = "AlbumInfo_searchText") {
        mutableStateOf("")
    }
    val searchVisible = rememberSaveable(key = "AlbumInfo_searchVisible") {
        mutableStateOf(false)
    }
    val sortOption = rememberSaveable(key = "AlbumInfo_sortOption") {
        mutableStateOf(AlbumSortOption.TrackNumber)
    }
    val descending = rememberSaveable(key = "AlbumInfo_descending") {
        mutableStateOf(false)
    }
    val displayedSongs = remember("AlbumInfo_displayedSongs") {
        mutableStateOf(albumSongs)
    }
    val sortSheetOpen = remember("AlbumInfo_sortSheetOpen") {
        mutableStateOf(false)
    }
    val overflowSheetOpen = remember("AlbumInfo_overflowSheetOpen") {
        mutableStateOf(false)
    }
    val addToPlaylistOpen = remember("AlbumInfo_addToPlaylistOpen") {
        mutableStateOf(false)
    }

    val primaryArtists = remember(albumSongs) {
        albumSongs.firstOrNull()?.artistsList ?: defaultArtists
    }
    val primaryArtistsName = remember(albumSongs) {
        albumSongs.firstOrNull()?.albumArtists
            ?: albumSongs.firstOrNull()?.artistsName
            ?: defaultArtistsName
    }
    val songCount = remember(albumSongs) {
        albumSongs.size
    }
    val totalMinutes = remember(albumSongs) {
        albumSongs.sumOf { it.duration } / 60000
    }
    val detailLine = buildList {
        albumSongs.firstOrNull()?.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
        albumSongs.firstOrNull()?.releaseYear?.let { add(it.toString()) }
        add(stringResource(id = R.string.page_library_album_info_others, songCount, totalMinutes))
    }.joinToString(separator = " · ")

    val allSongsFavorited by remember(albumSongs, FavPlayListLibrary.favPlayList) {
        derivedStateOf {
            albumSongs.isNotEmpty() && albumSongs.all { FavPlayListLibrary.isFavorite(it) }
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        albumSongs,
        searchText.value,
        sortOption.value,
        descending.value,
    ) {
        withContext(Dispatchers.Default) {
            val baseList = albumSongs.sortForAlbum(sortOption.value, descending.value)
            displayedSongs.value = if (searchText.value.isBlank()) {
                baseList
            } else {
                baseList.filter { song ->
                    (song.title ?: defaultTitle).contains(searchText.value, ignoreCase = true) ||
                            (song.artistsList ?: defaultArtists).any { artist ->
                                artist.contains(searchText.value, ignoreCase = true)
                            }
                }
            }
        }
    }

    ActionSheet(
        isOpen = sortSheetOpen,
        items = listOf(
            ActionItem(
                iconRes = R.drawable.ic_action_sort,
                label = stringResource(id = R.string.album_sort_track_number),
                tint = if (sortOption.value == AlbumSortOption.TrackNumber) MaterialTheme.colorScheme.primary else null,
                showChevron = false,
                onClick = {
                    sortOption.value = AlbumSortOption.TrackNumber
                },
            ),
            ActionItem(
                iconRes = R.drawable.ic_action_sort,
                label = stringResource(id = R.string.normal_button_sort_by_name),
                tint = if (sortOption.value == AlbumSortOption.Title) MaterialTheme.colorScheme.primary else null,
                showChevron = false,
                onClick = {
                    sortOption.value = AlbumSortOption.Title
                },
            ),
            ActionItem(
                iconRes = R.drawable.ic_action_sort,
                label = stringResource(id = R.string.playlist_sort_ascending),
                tint = if (!descending.value) MaterialTheme.colorScheme.primary else null,
                showChevron = false,
                onClick = {
                    descending.value = false
                },
            ),
            ActionItem(
                iconRes = R.drawable.ic_action_sort,
                label = stringResource(id = R.string.playlist_sort_descending),
                tint = if (descending.value) MaterialTheme.colorScheme.primary else null,
                showChevron = false,
                onClick = {
                    descending.value = true
                },
            ),
        ),
    )

    ActionSheet(
        isOpen = overflowSheetOpen,
        items = listOf(
            ActionItem(
                iconRes = R.drawable.ic_action_add,
                label = stringResource(id = R.string.now_playing_overflow_add_to_playlist),
                showChevron = false,
                onClick = {
                    overflowSheetOpen.value = false
                    addToPlaylistOpen.value = true
                },
            ),
        ),
    )

    AlbumAddToPlaylistSheet(
        isOpen = addToPlaylistOpen,
        albumName = albumName.value,
        songs = albumSongs,
    )

    MusicDetailPage(
        title = albumName.value,
        listState = listState,
        searchText = searchText.value,
        searchPlaceholder = stringResource(id = R.string.page_library_search_album_tracks),
        showSearch = searchVisible.value || searchText.value.isNotEmpty(),
        onBack = {
            navController.popBackStack()
        },
        onSort = {
            sortSheetOpen.value = true
        },
        onSearchTextChange = {
            searchText.value = it
        },
        onSearchToggle = {
            val newVisibility = !(searchVisible.value || searchText.value.isNotEmpty())
            searchVisible.value = newVisibility
            if (newVisibility) {
                scope.launch {
                    listState.animateScrollToItem(1)
                }
            } else {
                searchText.value = ""
            }
        },
        artwork = {
            AlbumHeroArtwork(songs = albumSongs)
        },
        headerContent = {
            Text(
                text = albumName.value,
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = primaryArtistsName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = detailLine,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actionContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MusicDetailCircleButton(
                    painter = painterResource(id = R.drawable.button_icon_shuffle),
                    contentDescription = stringResource(id = R.string.normal_button_shuffle),
                    enabled = displayedSongs.value.isNotEmpty(),
                    onClick = {
                        val songsToPlay = displayedSongs.value
                        if (songsToPlay.isEmpty()) return@MusicDetailCircleButton

                        MediaController.mediaControl?.shuffleModeEnabled = true
                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(songsToPlay.random(), songsToPlay)
                        }
                    },
                )

                MusicDetailActionPill(modifier = Modifier.weight(1f)) {
                    MusicDetailPillButton(
                        painter = painterResource(
                            id = if (allSongsFavorited) {
                                R.drawable.ic_nowplaying_favorited
                            } else {
                                R.drawable.ic_nowplaying_favorite
                            },
                        ),
                        contentDescription = stringResource(
                            id = if (allSongsFavorited) {
                                R.string.album_action_unfavorite
                            } else {
                                R.string.album_action_favorite
                            },
                        ),
                        selected = allSongsFavorited,
                        onClick = {
                            if (allSongsFavorited) {
                                albumSongs.forEach { FavPlayListLibrary.removeMusic(it) }
                            } else {
                                albumSongs.forEach { song ->
                                    if (!FavPlayListLibrary.isFavorite(song)) {
                                        FavPlayListLibrary.addMusic(song)
                                    }
                                }
                            }
                        },
                    )

                    MusicDetailPillDivider()

                    MusicDetailPillButton(
                        painter = painterResource(id = R.drawable.ic_nowplaying_more),
                        contentDescription = stringResource(id = R.string.playlist_overflow_more_cd),
                        onClick = {
                            overflowSheetOpen.value = true
                        },
                    )
                }

                MusicDetailCircleButton(
                    painter = painterResource(id = R.drawable.button_icon_play),
                    contentDescription = stringResource(id = R.string.normal_button_play),
                    enabled = displayedSongs.value.isNotEmpty(),
                    accent = true,
                    onClick = {
                        val songsToPlay = displayedSongs.value
                        if (songsToPlay.isEmpty()) return@MusicDetailCircleButton

                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(songsToPlay.first(), songsToPlay)
                        }
                    },
                )
            }
        },
    ) {
        if (displayedSongs.value.isEmpty()) {
            item("AlbumInfo_emptyResults") {
                Text(
                    text = stringResource(id = R.string.tip_no_song),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .alpha(0.55f),
                )
            }
        } else {
            itemsIndexed(
                displayedSongs.value,
                key = { index, music -> "$index:${music.uri}" },
            ) { index, music ->
                val subtitle = remember(music, primaryArtists) {
                    if (primaryArtists.containsAll(music.artistsList ?: defaultArtists)) {
                        null
                    } else {
                        music.artistsName ?: defaultArtistsName
                    }
                }

                MusicList(
                    music = music,
                    titleText = music.title ?: defaultTitle,
                    subtitleText = subtitle,
                    showArtwork = false,
                    artworkSize = 44.dp,
                    leadingWidth = 24.dp,
                    horizontalPadding = 18.dp,
                    leadingContent = {
                        Text(
                            text = "${music.trackNumber ?: index + 1}",
                            fontSize = 15.sp,
                            modifier = Modifier.alpha(0.42f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    itemClick = {
                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(music, displayedSongs.value)
                        }
                    },
                )

                if (index < displayedSongs.value.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 54.dp, end = 18.dp)
                            .alpha(0.16f)
                            .height(0.5.dp)
                            .background(Color.Black withNight Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumHeroArtwork(songs: List<YosMediaItem>) {
    val context = LocalContext.current
    val heroArtwork = songs.firstOrNull()?.thumb

    if (heroArtwork == null) {
        Image(
            painter = painterResource(id = R.drawable.placeholder_music_default_artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(heroArtwork)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumAddToPlaylistSheet(
    isOpen: MutableState<Boolean>,
    albumName: String,
    songs: List<YosMediaItem>,
) {
    if (!isOpen.value) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceStub = remember(albumName) {
        PlayList(
            listID = "album-bulk:$albumName",
            name = albumName,
            songDataList = emptyList(),
        )
    }

    val performBulkAdd: (PlayList) -> Unit = { target ->
        scope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                val songUri = song.uri ?: return@forEach
                val live = playList.firstOrNull { it.listID == target.listID } ?: return@forEach
                PlayListLibrary.run {
                    live.addMusic(uriStubMedia(songUri))
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.playlist_picker_added_toast, target.name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    YosBottomSheetDialog(onDismissRequest = { isOpen.value = false }) {
        PlayListPickerContent(
            songToAdd = null,
            onDone = { isOpen.value = false },
            bulkAddSource = sourceStub,
            onBulkAdd = performBulkAdd,
            onCreated = { created ->
                performBulkAdd(created)
            },
        )
    }
}

private fun List<YosMediaItem>.sortForAlbum(
    sortOption: AlbumSortOption,
    descending: Boolean,
): List<YosMediaItem> {
    val sorted = when (sortOption) {
        AlbumSortOption.TrackNumber -> {
            sortedWith(
                compareBy<YosMediaItem> { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title ?: defaultTitle },
            )
        }

        AlbumSortOption.Title -> {
            sortedBy {
                Pinyin.toPinyin((it.title ?: defaultTitle).first())
            }
        }
    }

    return if (descending) {
        sorted.reversed()
    } else {
        sorted
    }
}

private fun uriStubMedia(uri: Uri) = YosMediaItem(
    uri = uri,
    mediaId = null,
    mimeType = null,
    title = null,
    writer = null,
    compilation = null,
    composer = null,
    artists = null,
    album = null,
    albumArtists = null,
    thumb = null,
    trackNumber = null,
    discNumber = null,
    genre = null,
    recordingDay = null,
    recordingMonth = null,
    recordingYear = null,
    releaseYear = null,
    artistId = null,
    albumId = null,
    genreId = null,
    author = null,
    addDate = null,
    duration = 0L,
    modifiedDate = null,
    cdTrackNumber = null,
)
