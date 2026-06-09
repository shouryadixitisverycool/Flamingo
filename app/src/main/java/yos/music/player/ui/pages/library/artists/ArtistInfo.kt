package yos.music.player.ui.pages.library.artists

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.ArtistLibrary
import yos.music.player.data.libraries.ArtistRelease
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.addMusic
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.consumeNowPlayingNavigationMarker
import yos.music.player.ui.returnToLibraryFromNowPlaying
import yos.music.player.ui.pages.library.MusicDetailCircleButton
import yos.music.player.ui.pages.library.MusicDetailPage
import yos.music.player.ui.pages.library.MusicList
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.ActionItem
import yos.music.player.ui.widgets.basic.ActionSheet
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosBottomSheetDialog
import yos.music.player.ui.widgets.playlist.PlayListPickerContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistInfo(
    navController: NavController,
)
{
    val openedFromNowPlaying = rememberSaveable(key = "ArtistInfo_openedFromNowPlaying") {
        mutableStateOf(navController.consumeNowPlayingNavigationMarker())
    }
    val handleBack: () -> Unit = {
        if (openedFromNowPlaying.value) {
            openedFromNowPlaying.value = false
            navController.returnToLibraryFromNowPlaying()
        } else {
            navController.popBackStack()
        }
    }

    BackHandler(onBack = handleBack)

    val artistName = rememberSaveable(key = "ArtistInfo_artistName") {
        mutableStateOf(LibraryObject.getTargetArtistName())
    }
    val artistSections = remember(artistName.value, MusicLibrary.songs) {
        ArtistLibrary.sectionsForArtist(artistName.value)
    }
    val showEmptyState = remember(artistName.value, artistSections) {
        derivedStateOf {
            artistName.value.isEmpty() || (
                artistSections.songs.isEmpty() &&
                    artistSections.albums.isEmpty() &&
                    artistSections.singlesAndEps.isEmpty() &&
                    artistSections.featuredOn.isEmpty()
                )
        }
    }

    if (showEmptyState.value) {
        Title(
            title = stringResource(id = R.string.page_library_artists),
            onBack = handleBack,
        ) {
            item("ArtistInfo_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.tip_no_song),
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
    val context = LocalContext.current
    val overflowSheetOpen = remember("ArtistInfo_overflowSheetOpen") {
        mutableStateOf(false)
    }
    val addToPlaylistOpen = remember("ArtistInfo_addToPlaylistOpen") {
        mutableStateOf(false)
    }
    val artistSongs = artistSections.songs
    val isFollowed by remember(artistName.value, SettingsLibrary.FollowedArtists) {
        derivedStateOf {
            SettingsLibrary.isArtistFollowed(artistName.value)
        }
    }

    ActionSheet(
        isOpen = overflowSheetOpen,
        header = {
            ArtistOverflowHeader(
                artistName = artistName.value,
                songs = artistSongs,
            )
        },
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
            ActionItem(
                iconRes = R.drawable.button_icon_shuffle,
                label = stringResource(id = R.string.normal_button_shuffle),
                showChevron = false,
                enabled = artistSongs.isNotEmpty(),
                onClick = {
                    overflowSheetOpen.value = false
                    if (artistSongs.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(
                                artistSongs.random(),
                                artistSongs,
                                shuffleModeEnabled = true,
                            )
                        }
                    }
                },
            ),
            ActionItem(
                iconRes = R.drawable.ic_action_play_next,
                label = stringResource(id = R.string.playlist_overflow_play_next),
                showChevron = false,
                enabled = artistSongs.isNotEmpty(),
                onClick = {
                    overflowSheetOpen.value = false
                    if (artistSongs.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            val queued = MediaController.playNext(artistSongs)
                            if (!queued) { return@launch }

                            withContext(Dispatchers.Main) {
                                val message = if (artistSongs.size == 1) {
                                    context.getString(R.string.playlist_play_next_toast_one)
                                } else {
                                    context.getString(
                                        R.string.playlist_play_next_toast_other,
                                        artistSongs.size,
                                    )
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
            ),
        ),
    )

    ArtistAddToPlaylistSheet(
        isOpen = addToPlaylistOpen,
        artistName = artistName.value,
        songs = artistSongs,
    )

    MusicDetailPage(
        title = artistName.value,
        listState = listState,
        searchText = "",
        searchPlaceholder = "",
        enableSearch = false,
        showSortButton = false,
        showSearchButton = true,
        searchModeActive = false,
        searchRequestFocusSignal = 0,
        onBack = handleBack,
        onSort = {},
        onSearchTextChange = {},
        onSearchClick = {
            LibraryObject.setTargetArtistName(artistName.value)
            LibraryObject.setArtistSongsSearchOnOpen(true)
            navController.toUI(UI.ArtistSongs)
        },
        onSearchDismiss = {},
        artwork = {
            ArtistHeroArtwork(songs = artistSongs)
        },
        headerContent = {
            Text(
                text = artistName.value,
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actionContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                MusicDetailCircleButton(
                    painter = painterResource(id = R.drawable.button_icon_play),
                    contentDescription = stringResource(id = R.string.normal_button_play),
                    enabled = artistSongs.isNotEmpty(),
                    onClick = {
                        if (artistSongs.isEmpty()) { return@MusicDetailCircleButton }

                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(artistSongs.first(), artistSongs)
                        }
                    },
                )

                MusicDetailCircleButton(
                    painter = painterResource(
                        id = if (isFollowed) {
                            R.drawable.ic_nowplaying_favorited
                        } else {
                            R.drawable.ic_nowplaying_favorite
                        },
                    ),
                    contentDescription = stringResource(
                        id = if (isFollowed) {
                            R.string.artist_action_unfollow
                        } else {
                            R.string.artist_action_follow
                        },
                    ),
                    selected = isFollowed,
                    iconSize = 26.dp,
                    onClick = {
                        SettingsLibrary.toggleArtistFollowed(artistName.value)
                    },
                )

                MusicDetailCircleButton(
                    painter = painterResource(id = R.drawable.ic_nowplaying_more),
                    contentDescription = stringResource(id = R.string.playlist_overflow_more_cd),
                    iconSize = 26.dp,
                    onClick = {
                        overflowSheetOpen.value = true
                    },
                )
            }
        },
    ) {
        item("ArtistInfo_songs_header") {
            ArtistSectionHeader(
                title = stringResource(id = R.string.page_library_songs),
                onMore = {
                    LibraryObject.setTargetArtistName(artistName.value)
                    LibraryObject.setArtistSongsSearchOnOpen(false)
                    navController.toUI(UI.ArtistSongs)
                },
            )
        }

        itemsIndexed(
            artistSongs.take(5),
            key = { index, music -> "artist-song-$index:${music.uri}" },
        ) { index, music ->
            MusicList(
                music = music,
                onQueueSwipe = {
                    MediaController.addToQueue(music)
                },
            ) {
                scope.launch(Dispatchers.IO) {
                    MediaController.prepare(music, artistSongs)
                }
            }

            if (index < minOf(artistSongs.lastIndex, 4)) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 88.dp)
                        .alpha(0.15f)
                        .height(0.5.dp)
                        .background(Color.Black withNight Color.White),
                )
            }
        }

        if (artistSections.albums.isNotEmpty()) {
            item("ArtistInfo_albums_header") {
                ArtistSectionHeader(title = stringResource(id = R.string.page_library_albums))
            }
            item("ArtistInfo_albums_row") {
                ArtistReleaseRow(
                    releases = artistSections.albums,
                    onAlbumClick = { release ->
                        LibraryObject.setTargetAlbumName(release.albumName)
                        navController.toUI(UI.AlbumInfo)
                    },
                )
            }
        }

        if (artistSections.singlesAndEps.isNotEmpty()) {
            item("ArtistInfo_singles_header") {
                ArtistSectionHeader(title = stringResource(id = R.string.page_library_artist_singles))
            }
            item("ArtistInfo_singles_row") {
                ArtistReleaseRow(
                    releases = artistSections.singlesAndEps,
                    onAlbumClick = { release ->
                        LibraryObject.setTargetAlbumName(release.albumName)
                        navController.toUI(UI.AlbumInfo)
                    },
                )
            }
        }

        if (artistSections.featuredOn.isNotEmpty()) {
            item("ArtistInfo_featured_header") {
                ArtistSectionHeader(title = stringResource(id = R.string.page_library_artist_featured_on))
            }
            item("ArtistInfo_featured_row") {
                ArtistReleaseRow(
                    releases = artistSections.featuredOn,
                    onAlbumClick = { release ->
                        LibraryObject.setTargetAlbumName(release.albumName)
                        navController.toUI(UI.AlbumInfo)
                    },
                )
            }
        }
    }
}

@Composable
private fun ArtistHeroArtwork(songs: List<YosMediaItem>)
{
    val context = LocalContext.current
    val heroArtwork = songs.firstOrNull()?.thumb

    if (heroArtwork == null) {
        Image(
            painter = painterResource(id = R.drawable.songcredits_monogram_person),
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

@Composable
private fun ArtistSectionHeader(title: String, onMore: (() -> Unit)? = null)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        if (onMore != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMore,
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(id = R.drawable.ic_action_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistReleaseRow(
    releases: List<ArtistRelease>,
    onAlbumClick: (ArtistRelease) -> Unit,
)
{
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
    ) {
        items(releases, key = { it.albumName }) { release ->
            ArtistReleaseCard(
                release = release,
                onClick = {
                    onAlbumClick(release)
                },
            )
        }
    }
}

@Composable
private fun ArtistReleaseCard(release: ArtistRelease, onClick: () -> Unit)
{
    Column(
        modifier = Modifier
            .width(142.dp)
            .clickable(onClick = onClick),
    ) {
        ShadowImageWithCache(
            dataLambda = { release.songs.firstOrNull()?.thumb },
            contentDescription = release.albumName,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 8.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.HIGH,
        )

        Text(
            text = release.albumName,
            fontSize = 15.sp,
            maxLines = 2,
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(0.94f),
        )

        release.releaseYear?.let {
            Text(
                text = it.toString(),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .alpha(0.56f),
            )
        }
    }
}

@Composable
private fun ArtistOverflowHeader(artistName: String, songs: List<YosMediaItem>)
{
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(songs.firstOrNull()?.thumb)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(YosRoundedCornerShape(10.dp)),
            error = painterResource(id = R.drawable.songcredits_monogram_person),
            fallback = painterResource(id = R.drawable.songcredits_monogram_person),
            placeholder = painterResource(id = R.drawable.songcredits_monogram_person),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artistName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(id = R.string.page_library_album_desc, songs.size),
                fontSize = 13.5.sp,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistAddToPlaylistSheet(
    isOpen: MutableState<Boolean>,
    artistName: String,
    songs: List<YosMediaItem>,
)
{
    if (!isOpen.value) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceStub = remember(artistName) {
        PlayList(
            listID = "artist-bulk:$artistName",
            name = artistName,
            songDataList = emptyList(),
        )
    }

    val performBulkAdd: (PlayList) -> Unit = { target ->
        scope.launch(Dispatchers.IO) {
            songs.forEach { song ->
                val live = playList.firstOrNull { it.listID == target.listID } ?: return@forEach
                PlayListLibrary.run {
                    live.addMusic(song)
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
