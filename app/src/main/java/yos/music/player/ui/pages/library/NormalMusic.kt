package yos.music.player.ui.pages.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import android.widget.Toast
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import yos.music.player.data.libraries.PlayListLibrary.addMusic
import yos.music.player.data.libraries.PlayListLibrary.pin
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.PlayListLibrary.unpin
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.SettingsLibrary.EnableDescending
import yos.music.player.data.libraries.SettingsLibrary.SongSort
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.libraries.lazyListKey
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.pages.library.playlists.PendingPlayListDeletion
import yos.music.player.ui.pages.library.playlists.PlayListAutoCover
import yos.music.player.ui.pages.library.playlists.PlayListEditModal
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
@OptIn(ExperimentalMaterial3Api::class)
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
            val songsByUri = remember(songs) {
                songs.associateBy { it.uri }
            }
            remember(activePlayList.songDataList, songsByUri) {
                activePlayList.songDataList.mapNotNull { uri ->
                    songsByUri[uri]
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
                    val updatedList = withContext(Dispatchers.Default) {
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
                        if (activePlayList != null) {
                            // FR-S-07: relevance ranking overrides
                            // Sort By while a query is active.
                            if (useSearch.value) filteredList
                            else filteredList.sortForPlaylist()
                        } else {
                            filteredList.sortX()
                        }
                    }
                    list.value = updatedList
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
                    val sortExpanded = remember { mutableStateOf(false) }
                    val addToPlaylistExpanded = remember { mutableStateOf(false) }

                    FloatingMenu({ overflowSheetOpen.value }, {
                        overflowSheetOpen.value = it
                    }, buttonPosition.value) {
                        val accent = MaterialTheme.colorScheme.primary
                        FloatingMenuItem(
                            label = stringResource(R.string.playlist_overflow_sort_by),
                            icon = painterResource(id = R.drawable.ic_action_sort),
                            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            trailingIconRotated = sortExpanded.value,
                        ) {
                            sortExpanded.value = !sortExpanded.value
                            if (sortExpanded.value) { addToPlaylistExpanded.value = false }
                        }
                        AnimatedVisibility(visible = sortExpanded.value) {
                            Column {
                                FloatingMenuItemDivider()
                                FloatingMenuItem(
                                    label = stringResource(R.string.playlist_sort_manual),
                                    icon = painterResource(id = R.drawable.ic_action_drag_handle),
                                    tint = if (PlayListSortPreference.sort == PlayListSort.Manual) accent else MaterialTheme.colorScheme.onBackground,
                                ) { PlayListSortPreference.sort = PlayListSort.Manual }
                                FloatingMenuItemDivider()
                                FloatingMenuItem(
                                    label = stringResource(R.string.playlist_sort_title_name),
                                    icon = painterResource(id = R.drawable.ic_action_sort),
                                    tint = if (PlayListSortPreference.sort == PlayListSort.Title) accent else MaterialTheme.colorScheme.onBackground,
                                ) { PlayListSortPreference.sort = PlayListSort.Title }
                                FloatingMenuItemDivider()
                                FloatingMenuItem(
                                    label = stringResource(R.string.playlist_sort_artist),
                                    icon = painterResource(id = R.drawable.ic_action_sort),
                                    tint = if (PlayListSortPreference.sort == PlayListSort.Artist) accent else MaterialTheme.colorScheme.onBackground,
                                ) { PlayListSortPreference.sort = PlayListSort.Artist }
                                FloatingMenuItemDivider()
                                FloatingMenuItem(
                                    label = stringResource(R.string.playlist_sort_recently_added),
                                    icon = painterResource(id = R.drawable.ic_action_sort),
                                    tint = if (PlayListSortPreference.sort == PlayListSort.RecentlyAdded) accent else MaterialTheme.colorScheme.onBackground,
                                ) { PlayListSortPreference.sort = PlayListSort.RecentlyAdded }
                                FloatingMenuDivider()
                                FloatingMenuItem(
                                    label = stringResource(R.string.playlist_sort_ascending),
                                    icon = painterResource(id = R.drawable.ic_action_sort),
                                    tint = if (!PlayListSortPreference.descending) accent else MaterialTheme.colorScheme.onBackground,
                                ) { PlayListSortPreference.descending = false }
                                FloatingMenuItemDivider()
                                FloatingMenuItem(
                                    label = stringResource(R.string.playlist_sort_descending),
                                    icon = painterResource(id = R.drawable.ic_action_sort),
                                    tint = if (PlayListSortPreference.descending) accent else MaterialTheme.colorScheme.onBackground,
                                ) { PlayListSortPreference.descending = true }
                            }
                        }
                        FloatingMenuItemDivider()
                        FloatingMenuItem(
                            label = stringResource(R.string.playlist_overflow_edit),
                            icon = painterResource(id = R.drawable.ic_action_edit),
                        ) {
                            overflowSheetOpen.value = false
                            editModalOpen.value = true
                        }
                        FloatingMenuDivider()
                        FloatingMenuItem(
                            label = stringResource(R.string.playlist_overflow_add_to),
                            icon = painterResource(id = R.drawable.ic_action_add),
                            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            trailingIconRotated = addToPlaylistExpanded.value,
                        ) {
                            addToPlaylistExpanded.value = !addToPlaylistExpanded.value
                            if (addToPlaylistExpanded.value) { sortExpanded.value = false }
                        }
                        AnimatedVisibility(visible = addToPlaylistExpanded.value) {
                            Column {
                                FloatingMenuItemDivider()
                                FloatingMenuPlayListPickerContent(
                                    excludeListId = activePlayList.listID,
                                    showHeader = false,
                                    onBack = { addToPlaylistExpanded.value = false },
                                    onDone = { overflowSheetOpen.value = false },
                                    onPlaylistSelected = { target ->
                                        scope.launch(Dispatchers.IO) {
                                            activePlayList.songDataList.forEach { uri ->
                                                val live = playList.firstOrNull { it.listID == target.listID }
                                                    ?: return@forEach
                                                PlayListLibrary.run { live.addMusic(normalMusicUriStubMedia(uri)) }
                                            }

                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.playlist_picker_added_toast, target.name),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        FloatingMenuItemDivider()
                        FloatingMenuItem(
                            label = stringResource(R.string.playlist_overflow_play_next),
                            icon = painterResource(id = R.drawable.ic_action_play_next),
                        ) {
                            overflowSheetOpen.value = false
                            scope.launch(Dispatchers.IO) {
                                val songsInOrder = activePlayList.songDataList.mapNotNull { uri ->
                                    yos.music.player.data.libraries.MusicLibrary.songs
                                        .firstOrNull { it.uri == uri }
                                }
                                if (songsInOrder.isEmpty()) return@launch

                                val currentPlaying = MediaController.musicPlaying.value
                                if (currentPlaying == null) {
                                    MediaController.prepare(songsInOrder.first(), songsInOrder)
                                } else {
                                    MediaController.playNext(songsInOrder)
                                }

                                withContext(Dispatchers.Main) {
                                    val msg = if (songsInOrder.size == 1) playNextOneFmt
                                        else playNextManyFmt.format(songsInOrder.size)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        FloatingMenuDivider()
                        FloatingMenuItem(
                            label = stringResource(R.string.playlist_overflow_delete),
                            icon = painterResource(id = R.drawable.ic_action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        ) {
                            overflowSheetOpen.value = false
                            val toRestore = activePlayList
                            val originalIndex = playList.indexOfFirst { it.listID == toRestore.listID }
                            PlayListLibrary.remove(toRestore)
                            PendingPlayListDeletion.stash(toRestore, originalIndex)
                            navController.popBackStack()
                        }
                    }

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
                        showSortButton = false,
                        showSearchButton = false,
                        searchModeActive = playListSearchModeActive.value,
                        searchRequestFocusSignal = playListSearchFocusSignal.value,
                        searchTopPadding = 76.dp,
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
                        topBarFirstActionIconRes = if (playListSearchModeActive.value) {
                            null
                        } else if (activePlayList.isPinned) {
                            R.drawable.ic_action_unpin
                        } else {
                            R.drawable.ic_action_pin
                        },
                        topBarFirstActionContentDescription = stringResource(
                            id = if (activePlayList.isPinned) {
                                R.string.playlist_overflow_unpin
                            } else {
                                R.string.playlist_overflow_pin
                            },
                        ),
                        topBarFirstActionSelected = activePlayList.isPinned,
                        onTopBarFirstActionClick = {
                            if (activePlayList.isPinned) {
                                activePlayList.unpin()
                            } else {
                                activePlayList.pin()
                            }
                        },
                        topBarSecondActionIconRes = if (playListSearchModeActive.value) {
                            null
                        } else {
                            R.drawable.ic_action_more
                        },
                        topBarSecondActionContentDescription = stringResource(id = R.string.playlist_overflow_more_cd),
                        onTopBarSecondActionPositioned = {
                            buttonPosition.value = it
                        },
                        onTopBarSecondActionClick = {
                            sortExpanded.value = false
                            addToPlaylistExpanded.value = false
                            overflowSheetOpen.value = true
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
                                    painter = painterResource(id = R.drawable.button_icon_shuffle),
                                    contentDescription = stringResource(id = R.string.normal_button_shuffle),
                                    enabled = list.value.isNotEmpty(),
                                    showBackground = false,
                                    iconSize = 36.dp,
                                    onClick = {
                                        if (list.value.isEmpty()) return@MusicDetailCircleButton

                                        scope.launch(Dispatchers.IO) {
                                            MediaController.prepare(
                                                list.value.random(),
                                                list.value,
                                                shuffleModeEnabled = true
                                            )
                                        }
                                    },
                                )

                                MusicDetailCircleButton(
                                    painter = painterResource(id = R.drawable.button_icon_play),
                                    contentDescription = stringResource(id = R.string.normal_button_play),
                                    enabled = list.value.isNotEmpty(),
                                    showBackground = false,
                                    iconSize = 36.dp,
                                    onClick = {
                                        if (list.value.isEmpty()) return@MusicDetailCircleButton

                                        scope.launch(Dispatchers.IO) {
                                            MediaController.prepare(list.value.first(), list.value)
                                        }
                                    },
                                )

                                MusicDetailCircleButton(
                                    painter = painterResource(id = R.drawable.ic_action_search),
                                    contentDescription = stringResource(id = R.string.music_detail_search_cd, activePlayList.name),
                                    showBackground = false,
                                    iconSize = 36.dp,
                                    onClick = {
                                        scope.launch {
                                            playListSearchModeActive.value = true
                                            playListListState.scrollToItem(0)
                                            playListSearchFocusSignal.value += 1
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
                                key = { index, music -> music.lazyListKey(index) },
                                contentType = { _, _ -> "MusicList_item" },
                            ) { index, music ->
                                MusicList(
                                    music = music,
                                    onQueueSwipe = {
                                        MediaController.addToQueue(music)
                                    },
                                    navController = navController,
                                ) {
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

                        item("Options") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 12.dp, bottom = 15.dp)
                            ) {
                                NormalTopButton(
                                    icon = painterResource(id = R.drawable.button_icon_play),
                                    label = stringResource(id = R.string.normal_button_play),
                                    enabled = list.value.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (list.value.isEmpty()) {
                                        return@NormalTopButton
                                    }

                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            list.value.first(),
                                            list.value
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(15.dp))
                                NormalTopButton(
                                    icon = painterResource(id = R.drawable.button_icon_shuffle),
                                    label = stringResource(id = R.string.normal_button_shuffle),
                                    enabled = list.value.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (list.value.isEmpty()) {
                                        return@NormalTopButton
                                    }

                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            list.value.random(),
                                            list.value,
                                            shuffleModeEnabled = true
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            list.value,
                            key = { index, music -> music.lazyListKey(index) },
                            contentType = { _, _ -> "MusicList_item" },
                        ) { index, music ->
                            MusicList(
                                music = music,
                                onQueueSwipe = {
                                    MediaController.addToQueue(music)
                                },
                                navController = navController,
                            ) {
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

private fun normalMusicUriStubMedia(uri: android.net.Uri) = YosMediaItem(
    uri = uri,
    mediaId = null, mimeType = null,
    title = null, writer = null, compilation = null, composer = null,
    artists = null, album = null, albumArtists = null, thumb = null,
    trackNumber = null, discNumber = null, genre = null,
    recordingDay = null, recordingMonth = null, recordingYear = null,
    releaseYear = null, artistId = null, albumId = null, genreId = null,
    author = null, addDate = null, duration = 0L,
    modifiedDate = null, cdTrackNumber = null,
)

@Composable
private fun NormalTopButton(
    icon: Painter,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                shape = shape,
            )
            .clip(shape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
        )
    }
}

@Composable
fun FloatingMenu(
    expandedLambda: () -> Boolean,
    expandedOnChanged: (Boolean) -> Unit,
    buttonPosition: Offset = Offset.Zero,
    content: @Composable ColumnScope.() -> Unit = {
        FloatingMenuItem(
            label = stringResource(id = R.string.normal_button_sort_by_name),
            icon = Icons.AutoMirrored.Outlined.QueueMusic
        ) {
            SongSort = SettingsLibrary.SongSortEnum.MUSIC_TITLE.ordinal
        }
        FloatingMenuItemDivider()
        FloatingMenuItem(
            label = stringResource(id = R.string.normal_button_sort_by_artist),
            icon = Icons.Outlined.Person
        ) {
            SongSort = SettingsLibrary.SongSortEnum.ARTIST_NAME.ordinal
        }
        FloatingMenuDivider()
        FloatingMenuItem(
            label = stringResource(id = R.string.normal_button_sort_by_date),
            icon = Icons.Outlined.AccessTime
        ) {
            SongSort = SettingsLibrary.SongSortEnum.MODIFIED_DATE.ordinal
        }
        FloatingMenuDivider()
        FloatingMenuItem(
            label = stringResource(id = R.string.normal_button_sort_ascending),
            icon = Icons.Rounded.ArrowUpward
        ) {
            EnableDescending = false
        }
        FloatingMenuItemDivider()
        FloatingMenuItem(
            label = stringResource(id = R.string.normal_button_sort_descending),
            icon = Icons.Rounded.ArrowDownward
        ) {
            EnableDescending = true
        }
    },
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
            },
            properties = PopupProperties(focusable = true),
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
                            content()
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
fun FloatingMenuItem(
    label: String,
    icon: ImageVector,
    trailingIcon: ImageVector? = null,
    trailingIconRotated: Boolean = false,
    onClick: () -> Unit,
) {
    val trailingIconRotation = animateFloatAsState(
        targetValue = if (trailingIconRotated) { 90f } else { 0f },
        animationSpec = tween(160),
        label = "FloatingMenuTrailingIconRotation",
    )

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
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .graphicsLayer { rotationZ = trailingIconRotation.value },
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun FloatingMenuItem(
    label: String,
    icon: Painter,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    trailingIcon: ImageVector? = null,
    trailingIconRotated: Boolean = false,
    onClick: () -> Unit,
) {
    val trailingIconRotation = animateFloatAsState(
        targetValue = if (trailingIconRotated) { 90f } else { 0f },
        animationSpec = tween(160),
        label = "FloatingMenuTrailingIconRotation",
    )

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
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .graphicsLayer { rotationZ = trailingIconRotation.value },
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun <T> FloatingMenuScreenTransition(
    targetState: T,
    targetDepth: (T) -> Int = { 0 },
    content: @Composable (T) -> Unit,
)
{
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val direction = if (targetDepth(targetState) > targetDepth(initialState)) { 1 } else { -1 }
            (
                    fadeIn(animationSpec = tween(150)) + slideInHorizontally(animationSpec = tween(180)) { direction * it / 4 } togetherWith
                            fadeOut(animationSpec = tween(120)) + slideOutHorizontally(animationSpec = tween(160)) { -direction * it / 4 }
                    ).using(SizeTransform(clip = false))
        },
        label = "FloatingMenuScreenTransition",
    ) { screen ->
        Column {
            content(screen)
        }
    }
}

@Composable
fun FloatingMenuPlayListPickerContent(
    excludeListId: String?,
    showHeader: Boolean = true,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPlaylistSelected: (PlayList) -> Unit,
)
{
    val context = LocalContext.current
    val createMode = remember { mutableStateOf(false) }
    val newPlaylistName = remember { mutableStateOf("") }
    val nameError = remember { mutableStateOf<String?>(null) }
    val playlists = remember(playList, excludeListId) {
        playList.filter { it.listID != excludeListId }.sortedBy { it.name }
    }

    FloatingMenuScreenTransition(
        targetState = createMode.value,
        targetDepth = { if (it) { 1 } else { 0 } },
    ) { creating ->
        if (creating) {
            FloatingMenuCreatePlayListContent(
                name = newPlaylistName.value,
                errorMessage = nameError.value,
                onNameChange = {
                    newPlaylistName.value = it
                    nameError.value = null
                },
                onBack = {
                    createMode.value = false
                    newPlaylistName.value = ""
                    nameError.value = null
                },
                onCreate = {
                    val trimmedName = newPlaylistName.value.trim()
                    when {
                        trimmedName.isEmpty() -> Unit
                        playList.any { it.name == trimmedName } -> {
                            nameError.value = context.getString(R.string.playlist_picker_duplicate_name)
                        }

                        else -> {
                            PlayListLibrary.create(trimmedName)
                            val created = playList.firstOrNull { it.name == trimmedName }
                            if (created != null) {
                                onPlaylistSelected(created)
                            }
                            onDone()
                        }
                    }
                },
            )
        } else {
            if (showHeader) {
                FloatingMenuItem(
                    label = stringResource(R.string.playlist_picker_title),
                    icon = painterResource(id = R.drawable.ic_back),
                    onClick = onBack,
                )
                FloatingMenuDivider()
            }
            FloatingMenuItem(
                label = stringResource(R.string.playlist_picker_create_new),
                icon = painterResource(id = R.drawable.ic_action_add),
                tint = MaterialTheme.colorScheme.primary,
            ) {
                createMode.value = true
            }

            if (playlists.isEmpty()) {
                FloatingMenuDivider()
                FloatingMenuTextItem(label = stringResource(R.string.playlist_picker_empty))
            } else {
                FloatingMenuItemDivider()
                playlists.forEachIndexed { index, playlist ->
                    FloatingMenuPlaylistItem(playlist = playlist) {
                        onPlaylistSelected(playlist)
                        onDone()
                    }
                    if (index < playlists.lastIndex) {
                        FloatingMenuItemDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingMenuCreatePlayListContent(
    name: String,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit,
)
{
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canCreate = name.trim().isNotEmpty()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    FloatingMenuHeader(
        label = stringResource(R.string.playlist_picker_create_title),
        onBack = onBack,
    )
    FloatingMenuDivider()
    FloatingMenuTextField(
        value = name,
        placeholder = stringResource(R.string.playlist_picker_name_placeholder),
        focusRequester = focusRequester,
        onValueChange = onNameChange,
        onDone = {
            if (canCreate) { onCreate() }
        },
    )
    if (errorMessage != null) {
        FloatingMenuTextItem(label = errorMessage, color = MaterialTheme.colorScheme.error)
    }
    FloatingMenuDivider()
    FloatingMenuItem(
        label = stringResource(R.string.playlist_picker_create_confirm),
        icon = painterResource(id = R.drawable.ic_action_add),
        tint = if (canCreate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
    ) {
        if (canCreate) { onCreate() }
    }
}

@Composable
private fun FloatingMenuTextField(
    value: String,
    placeholder: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
)
{
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        Modifier
            .fillMaxWidth(0.618f)
            .heightIn(min = 48.dp)
            .background((Color.White withNight Color.Black).copy(alpha = 0.68f))
            .clickable {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 17.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(0.45f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black withNight Color.White,
                fontSize = 17.5.sp,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun FloatingMenuTextItem(
    label: String,
    color: Color = MaterialTheme.colorScheme.onBackground,
)
{
    Text(
        text = label,
        color = color,
        fontSize = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth(0.618f)
            .background((Color.White withNight Color.Black).copy(alpha = 0.68f))
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .alpha(0.8f),
    )
}

@Composable
private fun FloatingMenuHeader(label: String, onBack: () -> Unit)
{
    Row(
        Modifier
            .fillMaxWidth(0.618f)
            .height(48.dp)
            .background((Color.White withNight Color.Black).copy(alpha = 0.68f))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(0.6f),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 17.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(0.9f),
        )
    }
}

@Composable
private fun FloatingMenuPlaylistItem(playlist: PlayList, onClick: () -> Unit)
{
    val playlistSongs = remember(playlist.songDataList) {
        playlist.songDataList.mapNotNull { uri -> songs.firstOrNull { it.uri == uri } }
    }

    Row(
        Modifier
            .fillMaxWidth(0.618f)
            .height(48.dp)
            .background((Color.White withNight Color.Black).copy(alpha = 0.68f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = playlist.name,
            fontSize = 17.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alpha(0.9f)
                .padding(end = 18.dp),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            PlayListAutoCover(songs = playlistSongs)
        }
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
