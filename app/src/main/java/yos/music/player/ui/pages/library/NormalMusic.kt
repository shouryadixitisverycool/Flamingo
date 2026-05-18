package yos.music.player.ui.pages.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.navigation.NavController
import com.github.promeg.pinyinhelper.Pinyin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.libraries.MusicLibrary.toMediaItem
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.SettingsLibrary.EnableDescending
import yos.music.player.data.libraries.SettingsLibrary.SongSort
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.pages.library.playlists.PendingPlayListDeletion
import yos.music.player.ui.pages.library.playlists.PlayListEditModal
import yos.music.player.ui.pages.library.playlists.PlayListOverflowSheet
import yos.music.player.ui.pages.library.playlists.PlayListSearch
import yos.music.player.ui.pages.library.playlists.PlayListSortSheet
import yos.music.player.ui.pages.library.playlists.PlayListSort
import yos.music.player.ui.pages.library.playlists.PlayListSortPreference
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.SearchTextField
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitleBarIcon
import yos.music.player.ui.widgets.basic.YosWrapper

@Composable
fun NormalMusic(navController: NavController) {
    Column(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        val pageInfo = LibraryObject.getTargetListWithTitle()

        // PRD §5.2: playlist-only actions surface only when we
        // arrived from the Playlists page (which sets this state).
        val playListId = LibraryObject.targetPlayListId.value
        val activePlayList: PlayList? = playListId?.let { id ->
            playList.firstOrNull { it.listID == id }
        }

        // PRD §5.3 follow-up: when viewing a playlist, the source of
        // truth for the song list is the live [PlayList.songDataList]
        // — *not* the snapshot captured into [LibraryObject.targetList]
        // at navigation time. Re-resolving on every recomposition is
        // what makes Edit Playlist edits (reorder, remove, cover, etc.)
        // reflect immediately without having to leave and re-enter
        // the detail page. For non-playlist views we keep the original
        // behaviour (read whatever was handed to us by the caller).
        val musicList = if (activePlayList != null) {
            remember(activePlayList.songDataList, songs) {
                activePlayList.songDataList.mapNotNull { uri ->
                    songs.firstOrNull { it.uri == uri }
                }
            }
        } else {
            pageInfo.second
        }
        val searchText = remember(activePlayList?.listID) {
            mutableStateOf("")
        }

        val showMusic = remember("NormalMusic_showMusic", activePlayList, musicList) {
            derivedStateOf {
                activePlayList == null && musicList.isEmpty()
            }
        }
        if (showMusic.value) {
            val message = stringResource(id = R.string.tip_no_song)
            Title(
                title = pageInfo.first, onBack = {
                    navController.popBackStack()
                }
            ) {
                item("tip_no_song") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(text = message, fontSize = 18.sp, modifier = Modifier.alpha(0.6f))
                    }
                }
            }
        } else {
            val useSearch = remember { derivedStateOf { searchText.value.isNotEmpty() } }
            val list: MutableState<List<YosMediaItem>> = remember(activePlayList?.listID) {
                mutableStateOf(if (activePlayList != null) musicList else musicList.sortX())
            }

            YosWrapper {
                // PRD FR-M-05 + FR-S-06/07/08: on a playlist, sort
                // is view-only and search is fuzzy with relevance
                // ranking overriding the chosen sort. Search runs on
                // Dispatchers.Default with a 150ms debounce — the
                // delay() inside the launched effect gives Compose
                // a chance to cancel + restart for in-flight typing.
                LaunchedEffect(
                    searchText.value,
                    SongSort,
                    EnableDescending,
                    activePlayList?.listID,
                    // PRD §5.3 follow-up: re-run when the playlist
                    // body itself changes (Edit modal reorder/remove,
                    // single-song removal from a row menu, …) so the
                    // rendered list isn't pinned to a stale snapshot.
                    activePlayList?.songDataList,
                    musicList,
                    PlayListSortPreference.sort,
                    PlayListSortPreference.descending,
                ) {
                    if (activePlayList != null && useSearch.value) {
                        // Debounce typing — FR-S-08.
                        kotlinx.coroutines.delay(150)
                    }
                    withContext(Dispatchers.Default) {
                        val filteredList = if (useSearch.value) {
                            if (activePlayList != null) {
                                PlayListSearch.matchAndRank(musicList, searchText.value)
                            } else {
                                songs.asSequence().filter { song ->
                                    (song.title ?: defaultTitle).contains(
                                        searchText.value,
                                        ignoreCase = true
                                    ) ||
                                            (song.artistsList ?: defaultArtists).any { artist ->
                                                artist.contains(
                                                    searchText.value,
                                                    ignoreCase = true
                                                )
                                            }
                                }.toList()
                            }
                        } else {
                            musicList
                        }
                        list.value = if (activePlayList != null) {
                            // FR-S-07: relevance ranking overrides
                            // Sort By while a query is active.
                            if (useSearch.value) filteredList
                            else filteredList.sortForPlaylist()
                        } else {
                            filteredList.sortX()
                        }
                    }
                }
            }

            val scope = rememberCoroutineScope()

            val expanded = remember { mutableStateOf(false) }
            val buttonPosition = remember { mutableStateOf(Offset.Zero) }

            // PRD §5.2: when viewing a playlist, the 3-dot icon opens
            // the new bottom-sheet menu instead of the FloatingMenu
            // (which remains the right surface for Songs / Album / etc.).
            val overflowSheetOpen = remember { mutableStateOf(false) }
            val editModalOpen = remember { mutableStateOf(false) }
            val sortSheetOpen = remember { mutableStateOf(false) }
            val playListListState = rememberLazyListState()
            val playListSearchModeActive = remember(activePlayList?.listID) {
                mutableStateOf(false)
            }
            val playListSearchFocusSignal = remember(activePlayList?.listID) {
                mutableStateOf(0)
            }

            // Playlist detail layout — search bar is always visible
            // at the top of the scroll content; no pull-to-reveal.

            Box(Modifier.fillMaxSize()) {
                if (activePlayList == null) {
                    YosWrapper {
                        FloatingMenu({ expanded.value }, {
                            expanded.value = it
                        }, buttonPosition.value)
                    }
                } else {
                    val context = LocalContext.current
                    val playNextOneFmt = stringResource(R.string.playlist_play_next_toast_one)
                    val playNextManyFmt = stringResource(R.string.playlist_play_next_toast_other)
                    val songCount = remember(musicList) {
                        musicList.size
                    }
                    val totalMinutes = remember(musicList) {
                        musicList.sumOf { it.duration } / 60000
                    }

                    PlayListOverflowSheet(
                        isOpen = overflowSheetOpen,
                        playList = activePlayList,
                        onEdit = {
                            // PRD §5.3: open the Edit Playlist modal.
                            editModalOpen.value = true
                        },
                        onPlayNext = {
                            // PRD FR-M-09: insert the playlist's
                            // current songs right after the now-
                            // playing track. Updates both the
                            // ExoPlayer queue and the app's
                            // [MediaController.playingMusicList]
                            // snapshot so the NowPlaying queue UI
                            // (which renders from playingMusicList,
                            // not the live player) reflects the
                            // insert immediately. Falls back to
                            // [MediaController.prepare] when nothing
                            // is currently playing.
                            scope.launch(Dispatchers.IO) {
                                // Resolve the playlist's URIs against
                                // the global song library — musicList
                                // here is whatever the page is showing
                                // (which may be filtered by an active
                                // search query), so we go straight to
                                // the canonical store.
                                val songsInOrder = activePlayList.songDataList.mapNotNull { uri ->
                                    yos.music.player.data.libraries.MusicLibrary.songs
                                        .firstOrNull { it.uri == uri }
                                }
                                if (songsInOrder.isEmpty()) return@launch

                                val ctrl = MediaController.mediaControl
                                val currentQueue = MediaController.playingMusicList.value
                                val currentPlaying = MediaController.musicPlaying.value

                                if (ctrl == null || currentQueue.isNullOrEmpty() || currentPlaying == null) {
                                    // Nothing is playing — start fresh
                                    // with the playlist as the new queue.
                                    MediaController.prepare(songsInOrder.first(), songsInOrder)
                                } else {
                                    val mediaItems = songsInOrder.map { it.toMediaItem() }
                                    val insertAt = currentQueue.indexOfFirst {
                                        it.uri == currentPlaying.uri
                                    }.let { if (it < 0) 0 else it + 1 }

                                    withContext(Dispatchers.Main) {
                                        // Insert into the live player.
                                        ctrl.addMediaItems(insertAt, mediaItems)
                                    }
                                    // Mirror the insert into the app's
                                    // queue snapshot so PlayingList in
                                    // NowPlaying re-renders. Done off
                                    // the main thread since list
                                    // assignment triggers Compose work.
                                    val updated = currentQueue.toMutableList().also {
                                        it.addAll(insertAt, songsInOrder)
                                    }
                                    MediaController.playingMusicList.value = updated
                                }

                                withContext(Dispatchers.Main) {
                                    val msg = if (songsInOrder.size == 1) playNextOneFmt
                                        else playNextManyFmt.format(songsInOrder.size)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = {
                            // PRD FR-M-10: optimistic delete + undo
                            // snackbar. Wired with a simple Toast
                            // here pending the snackbar host (next
                            // commit). The deletion itself happens
                            // immediately and the page pops back.
                            val toRestore = activePlayList
                            val originalIndex = playList.indexOfFirst { it.listID == toRestore.listID }
                            PlayListLibrary.remove(toRestore)
                            // Stash for the upcoming snackbar host;
                            // for now, we just pop back.
                            PendingPlayListDeletion.stash(toRestore, originalIndex)
                            navController.popBackStack()
                        },
                    )

                    PlayListSortSheet(isOpen = sortSheetOpen)

                    PlayListEditModal(
                        isOpen = editModalOpen,
                        source = activePlayList,
                    )

                    MusicDetailPage(
                        title = activePlayList.name,
                        listState = playListListState,
                        searchText = searchText.value,
                        searchPlaceholder = stringResource(id = R.string.playlist_search_placeholder),
                        enableSearch = true,
                        searchModeActive = playListSearchModeActive.value,
                        searchRequestFocusSignal = playListSearchFocusSignal.value,
                        onBack = {
                            navController.popBackStack()
                        },
                        onSort = {
                            sortSheetOpen.value = true
                        },
                        onSearchTextChange = {
                            searchText.value = it
                        },
                        onSearchClick = {
                            scope.launch {
                                playListSearchModeActive.value = true
                                playListListState.scrollToItem(0)
                                playListSearchFocusSignal.value += 1
                            }
                        },
                        onSearchDismiss = {
                            scope.launch {
                                searchText.value = ""
                                playListSearchModeActive.value = false
                                playListListState.scrollToItem(0)
                            }
                        },
                        artwork = {
                            PlayListHeroArtwork(playList = activePlayList)
                        },
                        headerContent = {
                            Text(
                                text = activePlayList.name,
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
                                text = stringResource(
                                    id = R.string.page_library_album_info_others,
                                    songCount,
                                    totalMinutes,
                                ),
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (!activePlayList.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = activePlayList.description,
                                    color = Color.White.copy(alpha = 0.68f),
                                    fontSize = 14.5.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        actionContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                                    14.dp,
                                    Alignment.CenterHorizontally,
                                ),
                            ) {
                                MusicDetailCircleButton(
                                    painter = painterResource(id = R.drawable.button_icon_play),
                                    contentDescription = stringResource(id = R.string.normal_button_play),
                                    enabled = list.value.isNotEmpty(),
                                    onClick = {
                                        if (list.value.isEmpty()) return@MusicDetailCircleButton

                                        scope.launch(Dispatchers.IO) {
                                            MediaController.prepare(list.value.first(), list.value)
                                        }
                                    },
                                )

                                MusicDetailCircleButton(
                                    painter = painterResource(id = R.drawable.ic_action_edit),
                                    contentDescription = stringResource(id = R.string.playlist_overflow_edit),
                                    onClick = {
                                        editModalOpen.value = true
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

                                MusicDetailCircleButton(
                                    painter = painterResource(id = R.drawable.button_icon_shuffle),
                                    contentDescription = stringResource(id = R.string.normal_button_shuffle),
                                    enabled = list.value.isNotEmpty(),
                                    onClick = {
                                        if (list.value.isEmpty()) return@MusicDetailCircleButton

                                        MediaController.mediaControl?.shuffleModeEnabled = true
                                        scope.launch(Dispatchers.IO) {
                                            MediaController.prepare(list.value.random(), list.value)
                                        }
                                    },
                                )
                            }
                        },
                    ) {
                        if (list.value.isEmpty()) {
                            item("PlayListEmpty") {
                                val emptyMessage = if (musicList.isEmpty()) {
                                    stringResource(id = R.string.playlist_unavailable_desc)
                                } else {
                                    stringResource(id = R.string.tip_no_song)
                                }

                                Text(
                                    text = emptyMessage,
                                    fontSize = 15.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 18.dp)
                                        .alpha(0.55f),
                                )
                            }
                        } else {
                            itemsIndexed(
                                list.value,
                                key = { index, music -> "$index:${music.uri}" },
                            ) { index, music ->
                                MusicList(music = music) {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(music, list.value)
                                    }
                                }

                                if (index < list.value.lastIndex) {
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
                        }
                    }
                }

                if (activePlayList == null) {
                    Title(
                        title = pageInfo.first,
                        onBack = {
                            navController.popBackStack()
                        },
                        rightBarIcon = {
                            TitleBarIcon(
                                modifier = Modifier.onGloballyPositioned {
                                    if (buttonPosition.value.y == 0f) {
                                        buttonPosition.value = it.localToRoot(Offset.Zero)
                                    }
                                },
                                icon = Icons.Rounded.MoreHoriz,
                                onBack = {
                                    expanded.value = true
                                },
                            )
                        },
                    ) {
                        item("SearchField") {
                            val keyboardController = LocalSoftwareKeyboardController.current

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SearchTextField(
                                    text = searchText.value,
                                    placeholder = stringResource(id = R.string.page_library_search_songs),
                                    onValueChange = { searchText.value = it },
                                    modifier = Modifier.weight(1f),
                                    onSearch = {
                                        if (searchText.value.isNotEmpty()) {
                                            keyboardController?.hide()
                                        }
                                    },
                                )
                            }
                        }

                        itemsIndexed(
                            list.value,
                            key = { index, music -> "$index:${music.uri}" },
                        ) { index, music ->
                            MusicList(music = music) {
                                scope.launch(Dispatchers.IO) {
                                    MediaController.prepare(music, list.value)
                                }
                            }

                            if (index < list.value.lastIndex) {
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
                    }
                }
            }
        }
    }
}

private fun List<YosMediaItem>.sortX() =
    this.sortedBy { song ->
        when (SongSort) {
            SettingsLibrary.SongSortEnum.MUSIC_TITLE.ordinal -> Pinyin.toPinyin(
                (song.title ?: defaultTitle)[0]
            )

            SettingsLibrary.SongSortEnum.MUSIC_DURATION.ordinal -> song.duration
            SettingsLibrary.SongSortEnum.ARTIST_NAME.ordinal -> Pinyin.toPinyin(
                (song.artistsList ?: defaultArtists).first()[0]
            )

            SettingsLibrary.SongSortEnum.MODIFIED_DATE.ordinal -> song.modifiedDate ?: 0
            else -> Pinyin.toPinyin((song.title ?: defaultTitle)[0])
        }.toString()
    }.let {
        if (EnableDescending) {
            it.reversed()
        } else {
            it
        }
    }

/**
 * Apply the playlist view's chosen sort (PRD FR-M-05). Manual leaves
 * the order as-is — the caller already constructed [this] in the
 * playlist's saved order (via [convertToSongList]). Other sort modes
 * project to a string key (Pinyin-aware for title/artist) and apply
 * the descending toggle.
 */
private fun List<YosMediaItem>.sortForPlaylist(): List<YosMediaItem> {
    val sorted = when (PlayListSortPreference.sort) {
        PlayListSort.Manual -> return if (PlayListSortPreference.descending) reversed() else this
        PlayListSort.Title -> sortedBy { Pinyin.toPinyin((it.title ?: defaultTitle)[0]) }
        PlayListSort.Artist -> sortedBy {
            Pinyin.toPinyin(((it.artistsList ?: defaultArtists).first())[0])
        }
        PlayListSort.RecentlyAdded -> sortedBy { it.modifiedDate ?: 0L }
    }
    return if (PlayListSortPreference.descending) sorted.reversed() else sorted
}

@Composable
fun FloatingMenu(
    expandedLambda: () -> Boolean,
    expandedOnChanged: (Boolean) -> Unit,
    buttonPosition: Offset = Offset.Zero
) {

    val keepPopup = remember("FloatingMenu_keepPopup") {
        mutableStateOf(false)
    }
    val showPopup = remember("FloatingMenu_showPopup") {
        mutableStateOf(false)
    }

    if (keepPopup.value) {
        Popup(
            //offset = IntOffset(0, buttonPosition.y.toInt()),
            onDismissRequest = {
                expandedOnChanged(false)
            }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {
                        expandedOnChanged(false)
                    }) {
                val animationSpec =
                    spring(dampingRatio = 0.7f, stiffness = 340f, visibilityThreshold = 0.0001f)
                val shadow = animateFloatAsState(
                    targetValue = if (showPopup.value) 225f else 0f,
                    animationSpec = if (showPopup.value) tween(
                        durationMillis = 300,
                        delayMillis = 430
                    ) else tween(durationMillis = 0)
                )
                AnimatedVisibility(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = with(LocalDensity.current) {
                            buttonPosition.y
                                .toDp()
                                .plus(10.dp)
                        }),
                    visible = showPopup.value,
                    enter = fadeIn(animationSpec = animationSpec) + scaleIn(
                        initialScale = 0.618f,
                        animationSpec = animationSpec,
                        transformOrigin = TransformOrigin(0.95f, 0f)
                    ),
                    exit = fadeOut(animationSpec = animationSpec) + scaleOut(
                        targetScale = 0.618f,
                        animationSpec = animationSpec,
                        transformOrigin = TransformOrigin(0.95f, 0f)
                    )
                ) {
                    val shape = RoundedCornerShape(10.dp)
                    val shadowColor = Color(0xB3000000)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Column(
                            Modifier
                                .padding(end = 12.dp)
                                /*.shadow(
                                    spotColor = shadowColor,
                                    shape = shape,
                                    elevation = shadow.value.dp,
                                    clip = false
                                )*/
                                .graphicsLayer {
                                    this.shape = shape
                                    this.spotShadowColor = shadowColor
                                    this.shadowElevation = shadow.value
                                }
                                .graphicsLayer {
                                    this.shape = shape
                                    this.clip = true
                                }
                                .background(Color(0xF2E9E9E9) withNight Color(0xFA161616), shape),
                        ) {
                            FloatingMenuItem(
                                label = stringResource(id = R.string.normal_button_sort_by_name),
                                icon = Icons.AutoMirrored.Outlined.QueueMusic
                            ) {
                                SongSort =
                                    SettingsLibrary.SongSortEnum.MUSIC_TITLE.ordinal
                                println("SongSort: $SongSort")
                            }
                            FloatingMenuItemDivider()
                            FloatingMenuItem(
                                label = stringResource(id = R.string.normal_button_sort_by_artist),
                                icon = Icons.Outlined.Person
                            ) {
                                SongSort =
                                    SettingsLibrary.SongSortEnum.ARTIST_NAME.ordinal
                                println("SongSort: $SongSort")
                            }
                            FloatingMenuDivider()
                            FloatingMenuItem(
                                label = stringResource(id = R.string.normal_button_sort_by_date),
                                icon = Icons.Outlined.AccessTime
                            ) {
                                SongSort =
                                    SettingsLibrary.SongSortEnum.MODIFIED_DATE.ordinal
                                println("SongSort: $SongSort")
                            }
                            FloatingMenuDivider()
                            FloatingMenuItem(
                                label = stringResource(id = R.string.normal_button_sort_ascending),
                                icon = Icons.Rounded.ArrowUpward
                            ) {
                                EnableDescending = false
                                println("SongSort: $EnableDescending")
                            }
                            FloatingMenuItemDivider()
                            FloatingMenuItem(
                                label = stringResource(id = R.string.normal_button_sort_descending),
                                icon = Icons.Rounded.ArrowDownward
                            ) {
                                EnableDescending = true
                                println("SongSort: $EnableDescending")
                            }
                        }
                    }
                }
            }
        }
        // println("Popup 显示")
    } else {
        // println("Popup 隐藏")
    }

    LaunchedEffect(key1 = expandedLambda()) {
        if (expandedLambda()) {
            keepPopup.value = true
            delay(100)
            showPopup.value = true
        } else {
            showPopup.value = false
            delay(300)
            keepPopup.value = false
        }
    }
}


@Composable
fun FloatingMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth(0.618f)
            .height(48.dp)
            .background((Color.White withNight Color.Black).copy(alpha = 0.68f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 17.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(0.9f)
                .padding(end = 18.dp)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun FloatingMenuItemDivider() =
    Spacer(
        modifier = Modifier
            .fillMaxWidth(0.618f)
            .alpha(0.1f)
            .height(0.65.dp)
            .background(Color.Black withNight Color.White)
    )

@Composable
fun FloatingMenuDivider() =
    Spacer(
        modifier = Modifier.height(8.dp)
    )

/**
 * Shared playlist hero artwork used by the new reusable detail page.
 * Custom cover wins, otherwise the auto-generated collage is used,
 * and empty playlists fall back to the default placeholder.
 */
@Composable
private fun PlayListHeroArtwork(playList: PlayList) {
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(18.dp)

    val songsInPlaylist = remember(playList.songDataList) {
        playList.songDataList.mapNotNull { uri ->
            yos.music.player.data.libraries.MusicLibrary.songs.firstOrNull { it.uri == uri }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
                this.shape = shape
            },
    ) {
        when {
            !playList.coverUri.isNullOrBlank() -> {
                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(playList.coverUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            songsInPlaylist.isEmpty() -> {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.placeholder_playlist_default),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            else -> {
                yos.music.player.ui.pages.library.playlists.PlayListAutoCover(
                    songs = songsInPlaylist,
                )
            }
        }
    }
}
