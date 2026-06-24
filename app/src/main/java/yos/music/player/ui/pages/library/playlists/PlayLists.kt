package yos.music.player.ui.pages.library.playlists

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMapNotNull
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.addMusic
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.TitleBarIcon
import yos.music.player.ui.widgets.playlist.PlayListPickerSheet

private const val FirstPlayListLazyListIndex = 3
private val PlayListContextMenuEstimatedHeight = 342.dp

@Composable
fun PlayLists(navController: NavController) {
    // PRD §5.4: pinned playlists float to the top in pinOrder asc;
    // unpinned tail keeps the existing alpha-by-name order.
    val playLists = playList
    val pinned = playLists.filter { it.isPinned }
        .sortedBy { it.pinOrder ?: Int.MAX_VALUE }
    val unpinned = playLists.filter { !it.isPinned }.sortedBy { it.name }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val reorderActive = remember {
        mutableStateOf(false)
    }
    val reorderOrder = remember {
        mutableStateListOf<String>()
    }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (!reorderActive.value) { return@rememberReorderableLazyListState }

        val sourceIndex = resolvePinnedPlaylistReorderIndex(from.index, reorderOrder.size)
            ?: return@rememberReorderableLazyListState
        val destinationIndex = resolvePinnedPlaylistReorderIndex(to.index, reorderOrder.size)
            ?: return@rememberReorderableLazyListState

        if (sourceIndex == destinationIndex) { return@rememberReorderableLazyListState }

        val movedId = reorderOrder.removeAt(sourceIndex)
        reorderOrder.add(destinationIndex, movedId)
        Vibrator.click(context)
    }
    val draggingPlayListId = remember {
        mutableStateOf<String?>(null)
    }
    val visiblePinned = if (reorderActive.value) {
        val pinnedById = pinned.associateBy { it.listID }
        reorderOrder.mapNotNull { pinnedById[it] }
    } else {
        pinned
    }
    val visibleAll = visiblePinned + unpinned
    val enterReorderMode = {
        reorderOrder.clear()
        reorderOrder.addAll(pinned.map { it.listID })
        reorderActive.value = true
    }
    val exitReorderMode = {
        reorderActive.value = false
        reorderOrder.clear()
        draggingPlayListId.value = null
    }

    // FR-M-10: the delete-undo snackbar is hosted by MainActivity
    // ([UndoSnackbarHost]), not this page — see the rationale on
    // [PendingPlayListDeletion]. Nothing to do here other than
    // PlayListLibrary.remove() + stash from the detail page, which
    // happens in NormalMusic.onDelete.

    // PRD §5.5 FR-AP-5: this page's "Add" row opens the same reusable picker
    // used by the NowPlaying overflow menu, in create-only mode (no song to
    // attach). Hoisting visibility to this composable keeps a single picker
    // instance shared across the page.
    val createPickerOpen = remember { mutableStateOf(false) }
    val contextMenuOpen = remember { mutableStateOf(false) }
    val contextMenuPlayList = remember { mutableStateOf<PlayList?>(null) }
    val contextMenuAnchorPosition = remember { mutableStateOf(Offset.Zero) }
    val editModalOpen = remember { mutableStateOf(false) }
    val editPlayList = remember { mutableStateOf<PlayList?>(null) }

    PlayListPickerSheet(
        isOpen = createPickerOpen,
        songToAdd = null,
        centered = true,
    )

    contextMenuPlayList.value?.let { playListForMenu ->
        PlayListContextMenu(
            playList = playListForMenu,
            visible = contextMenuOpen.value,
            anchorPosition = contextMenuAnchorPosition.value,
            visibleState = contextMenuOpen,
            onEdit = {
                editPlayList.value = playList.firstOrNull { it.listID == playListForMenu.listID } ?: playListForMenu
                editModalOpen.value = true
            },
            onTogglePin = { target ->
                val live = playList.firstOrNull { it.listID == target.listID } ?: target
                if (live.isPinned) {
                    PlayListLibrary.run { live.unpin() }
                    Toast.makeText(context, context.getString(R.string.playlist_unpin_toast, live.name), Toast.LENGTH_SHORT).show()
                } else {
                    PlayListLibrary.run { live.pin() }
                    Toast.makeText(context, context.getString(R.string.playlist_pin_toast, live.name), Toast.LENGTH_SHORT).show()
                }
            },
            onReorder = if (pinned.isNotEmpty()) {
                {
                    enterReorderMode()
                }
            } else null,
            onBulkAddToPlaylist = { source, target ->
                scope.launch(Dispatchers.IO) {
                    source.songDataList.forEach { uri ->
                        val live = playList.firstOrNull { it.listID == target.listID }
                            ?: return@forEach
                        live.addMusic(uriStubMedia(uri))
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
            onCreateAndBulkAdd = { source, playlistName ->
                scope.launch(Dispatchers.IO) {
                    PlayListLibrary.create(playlistName)
                    val created = playList.firstOrNull { it.name == playlistName }
                        ?: return@launch
                    source.songDataList.forEach { uri ->
                        val live = playList.firstOrNull { it.listID == created.listID }
                            ?: return@forEach
                        live.addMusic(uriStubMedia(uri))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.playlist_picker_added_toast, playlistName),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onPlayNext = {
                scope.launch(Dispatchers.IO) {
                    val songsInOrder = convertToSongList(playListForMenu.songDataList, songs)
                    if (songsInOrder.isEmpty()) return@launch

                    val currentPlaying = MediaController.musicPlaying.value
                    if (currentPlaying == null) {
                        MediaController.prepare(songsInOrder.first(), songsInOrder)
                    } else {
                        MediaController.playNext(songsInOrder)
                    }

                    withContext(Dispatchers.Main) {
                        val message = if (songsInOrder.size == 1) {
                            context.getString(R.string.playlist_play_next_toast_one)
                        } else {
                            context.getString(R.string.playlist_play_next_toast_other, songsInOrder.size)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                val live = playList.firstOrNull { it.listID == playListForMenu.listID } ?: playListForMenu
                val originalIndex = playList.indexOfFirst { it.listID == live.listID }
                PlayListLibrary.remove(live)
                PendingPlayListDeletion.stash(live, originalIndex)
                contextMenuPlayList.value = null
            },
        )
    }

    editPlayList.value?.let { source ->
        PlayListEditModal(
            isOpen = editModalOpen,
            source = source,
        )
    }
    Title(
            title = stringResource(id = R.string.page_library_playlists),
            onBack = {
                if (reorderActive.value) {
                    exitReorderMode()
                } else {
                    navController.popBackStack()
                }
            },
            rightBarIcon = if (reorderActive.value) {
                {
                    TitleBarIcon(
                        icon = Icons.Default.Check,
                        onBack = {
                            PlayListLibrary.reorderPins(reorderOrder.toList())
                            exitReorderMode()
                        },
                    )
                }
            } else null,
            listState = listState,
            content = {
                item("AddList") {
                    val targetTitle = context.getString(R.string.page_library_playlists_add_title)
                    PlayListItem(playListType = PlayListType.Add, title = targetTitle) {
                        if (reorderActive.value) { return@PlayListItem }
                        createPickerOpen.value = true
                    }
                    PlayListDivider()
                }

                item("FavList") {
                    val targetTitle = context.getString(R.string.page_library_playlists_fav_title)
                    PlayListItem(playListType = PlayListType.Favorite, title = targetTitle) {
                        if (reorderActive.value) { return@PlayListItem }
                        scope.launch(Dispatchers.IO) {
                            val targetList = FavPlayListLibrary.favPlayList
                            LibraryObject.setTargetListWithTitle(targetTitle, targetList)
                            withContext(Dispatchers.Main) {
                                navController.toUI(UI.NormalMusic)
                            }
                        }
                    }
                    PlayListDivider()
                }

                itemsIndexed(
                    visibleAll,
                    key = { _, p -> p.listID }
                ) { index, playList ->
                    val isPinned = playList.isPinned
                    val rowRootPosition = remember(playList.listID) {
                        mutableStateOf(Offset.Zero)
                    }
                    val playlistClick: () -> Unit = playlistClick@{
                        if (reorderActive.value) { return@playlistClick }
                        scope.launch(Dispatchers.IO) {
                            val targetTitle = playList.name
                            val targetList = convertToSongList(playList.songDataList, songs)
                            LibraryObject.setTargetListWithTitle(
                                targetTitle,
                                targetList,
                                playListId = playList.listID,
                            )
                            withContext(Dispatchers.Main) {
                                navController.toUI(UI.NormalMusic)
                            }
                        }
                    }

                    if (isPinned && reorderActive.value) {
                        ReorderableItem(reorderableState, key = playList.listID) { isDragging ->
                            PinnedAwarePlayListItem(
                                playList = playList,
                                isPinned = true,
                                reorderActive = true,
                                reorderEnabled = reorderOrder.size > 1,
                                draggingThisRow = isDragging || draggingPlayListId.value == playList.listID,
                                dimmed = false,
                                reorderHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        draggingPlayListId.value = playList.listID
                                        Vibrator.longClick(context)
                                    },
                                    onDragStopped = {
                                        draggingPlayListId.value = null
                                        Vibrator.click(context)
                                    },
                                ),
                                onPositionChange = {
                                    rowRootPosition.value = it
                                },
                                onClick = playlistClick,
                                onLongClick = {},
                            )
                        }
                    } else {
                        PinnedAwarePlayListItem(
                            playList = playList,
                            isPinned = isPinned,
                            reorderActive = reorderActive.value,
                            reorderEnabled = false,
                            draggingThisRow = false,
                            dimmed = reorderActive.value && !isPinned,
                            reorderHandleModifier = Modifier,
                            onPositionChange = {
                                rowRootPosition.value = it
                            },
                            onClick = playlistClick,
                            onLongClick = {
                                if (reorderActive.value) { return@PinnedAwarePlayListItem }
                                contextMenuPlayList.value = playList
                                contextMenuAnchorPosition.value = rowRootPosition.value
                                contextMenuOpen.value = true
                            },
                        )
                    }

                    key(index) {
                        val needDivider = index < visibleAll.size - 1
                        if (needDivider) {
                            PlayListDivider()
                        }
                    }
                }
            }
        )
}

private fun resolvePinnedPlaylistReorderIndex(lazyListIndex: Int, pinnedCount: Int): Int? {
    val playlistIndex = lazyListIndex - FirstPlayListLazyListIndex
    if (playlistIndex !in 0 until pinnedCount) { return null }

    return playlistIndex
}

@Composable
private fun PlayListDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 102.dp)
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White),
    )
}

private fun convertToSongList(
    songDataList: List<Uri>,
    songs: List<YosMediaItem>
): List<YosMediaItem> {
    return songDataList.fastMapNotNull { uri ->
        songs.find { it.uri == uri }
    }
}

@Composable
private fun PlayListContextMenu(
    playList: PlayList,
    visible: Boolean,
    anchorPosition: Offset,
    visibleState: androidx.compose.runtime.MutableState<Boolean>,
    onEdit: () -> Unit,
    onTogglePin: (PlayList) -> Unit,
    onReorder: (() -> Unit)?,
    onBulkAddToPlaylist: (PlayList, PlayList) -> Unit,
    onCreateAndBulkAdd: (PlayList, String) -> Unit,
    onPlayNext: () -> Unit,
    onDelete: () -> Unit,
)
{
    PlayListContextMenuPopup(
        playList = playList,
        visible = visible,
        anchorPosition = anchorPosition,
        onDismiss = {
            visibleState.value = false
        },
        onEdit = {
            visibleState.value = false
            onEdit()
        },
        onTogglePin = {
            visibleState.value = false
            onTogglePin(playList)
        },
        onReorder = onReorder?.let {
            {
                visibleState.value = false
                it()
            }
        },
        onBulkAddToPlaylist = { target ->
            visibleState.value = false
            onBulkAddToPlaylist(playList, target)
        },
        onCreateAndBulkAdd = { playlistName ->
            visibleState.value = false
            onCreateAndBulkAdd(playList, playlistName)
        },
        onPlayNext = {
            visibleState.value = false
            onPlayNext()
        },
        onDelete = {
            visibleState.value = false
            onDelete()
        },
    )
}

@Composable
private fun PlayListContextMenuPopup(
    playList: PlayList,
    visible: Boolean,
    anchorPosition: Offset,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onReorder: (() -> Unit)?,
    onBulkAddToPlaylist: (PlayList) -> Unit,
    onCreateAndBulkAdd: (String) -> Unit,
    onPlayNext: () -> Unit,
    onDelete: () -> Unit,
)
{
    val keepPopup = remember(playList.listID) {
        mutableStateOf(false)
    }
    val showPopup = remember(playList.listID) {
        mutableStateOf(false)
    }

    LaunchedEffect(visible) {
        if (visible) {
            keepPopup.value = true
            delay(16)
            showPopup.value = true
        } else if (keepPopup.value) {
            showPopup.value = false
            delay(240)
            keepPopup.value = false
        }
    }

    if (!keepPopup.value) {
        return
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        ) {
            val anchorTop = with(density) {
                anchorPosition.y.toDp()
            }
            val maxTop = maxHeight - PlayListContextMenuEstimatedHeight
            val menuTop = if (maxTop > 16.dp) {
                anchorTop.coerceIn(16.dp, maxTop)
            } else {
                16.dp
            }
            val menuMaxHeight = (maxHeight - menuTop - 16.dp).coerceAtLeast(96.dp)

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.68f)
                    .padding(top = menuTop),
                visible = showPopup.value,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 360f,
                    ),
                ) + scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 360f,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 520f,
                    ),
                ) + scaleOut(
                    targetScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 0f),
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 520f,
                    ),
                ),
            ) {
                PlayListContextMenuCard(
                    playList = playList,
                    maxHeight = menuMaxHeight,
                    onEdit = onEdit,
                    onTogglePin = onTogglePin,
                    onReorder = onReorder,
                    onBulkAddToPlaylist = onBulkAddToPlaylist,
                    onCreateAndBulkAdd = onCreateAndBulkAdd,
                    onPlayNext = onPlayNext,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PlayListContextMenuCard(
    playList: PlayList,
    maxHeight: Dp,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onReorder: (() -> Unit)?,
    onBulkAddToPlaylist: (PlayList) -> Unit,
    onCreateAndBulkAdd: (String) -> Unit,
    onPlayNext: () -> Unit,
    onDelete: () -> Unit,
)
{
    var playlistOptionsExpanded by remember(playList.listID) {
        mutableStateOf(false)
    }
    val live = PlayListLibrary.playList.firstOrNull { it.listID == playList.listID } ?: playList
    val shape = RoundedCornerShape(18.dp)
    val cardColor = Color(0xF4F5F5F5) withNight Color(0xFA181818)
    val dividerColor = Color.Black withNight Color.White
    val pinLabel = stringResource(
        id = if (live.isPinned) {
            R.string.playlist_overflow_unpin
        } else {
            R.string.playlist_overflow_pin
        },
    )
    val bodyMaxHeight = (maxHeight - 64.dp - 0.5.dp).coerceAtLeast(0.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .graphicsLayer {
                this.shape = shape
                shadowElevation = 28f
                spotShadowColor = Color.Black
            }
            .clip(shape)
            .background(cardColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
    ) {
        PlayListContextMenuHeader(playList = live)
        PlayListContextMenuDivider(color = dividerColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            PlayListContextMenuItem(
                label = stringResource(id = R.string.playlist_overflow_play_next),
                iconRes = R.drawable.ic_action_play_next,
                onClick = onPlayNext,
            )
            PlayListContextMenuDivider(color = dividerColor)
            PlayListContextMenuItem(
                label = stringResource(id = R.string.playlist_overflow_add_to),
                iconRes = R.drawable.ic_song_context_playlist,
                labelTrailingIconRes = R.drawable.ic_action_next,
                labelTrailingIconRotation = if (playlistOptionsExpanded) 90f else 0f,
                onClick = {
                    playlistOptionsExpanded = !playlistOptionsExpanded
                },
            )
            AnimatedVisibility(visible = playlistOptionsExpanded) {
                PlayListContextPlaylistDropDown(
                    source = live,
                    onAddToPlaylist = onBulkAddToPlaylist,
                    onCreatePlaylist = onCreateAndBulkAdd,
                )
            }
            PlayListContextMenuDivider(color = dividerColor)
            PlayListContextMenuItem(
                label = stringResource(id = R.string.playlist_overflow_edit),
                iconRes = R.drawable.ic_action_edit,
                onClick = onEdit,
            )
            PlayListContextMenuDivider(color = dividerColor)
            PlayListContextMenuItem(
                label = pinLabel,
                iconRes = if (live.isPinned) R.drawable.ic_action_unpin else R.drawable.ic_action_pin,
                onClick = onTogglePin,
            )
            if (onReorder != null) {
                PlayListContextMenuDivider(color = dividerColor)
                PlayListContextMenuItem(
                    label = stringResource(id = R.string.playlist_overflow_reorder),
                    iconRes = R.drawable.ic_action_drag_handle,
                    onClick = onReorder,
                )
            }
            PlayListContextMenuDivider(color = dividerColor)
            PlayListContextMenuItem(
                label = stringResource(id = R.string.playlist_overflow_delete),
                iconRes = R.drawable.ic_action_delete,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun PlayListContextMenuHeader(playList: PlayList)
{
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(5.dp)
    val songsInPlaylist = remember(playList.songDataList) {
        playList.songDataList.mapNotNull { uri ->
            songs.firstOrNull { it.uri == uri }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
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
                    )
                }
                songsInPlaylist.isEmpty() -> {
                    Image(
                        painter = painterResource(id = R.drawable.placeholder_playlist_default),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    PlayListAutoCover(songs = songsInPlaylist)
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = playList.name,
                fontSize = 14.5.sp,
                lineHeight = 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playList.songDataList.size == 1) {
                    stringResource(id = R.string.playlist_picker_song_count_one)
                } else {
                    stringResource(id = R.string.playlist_picker_song_count_other, playList.songDataList.size)
                },
                fontSize = 11.sp,
                lineHeight = 13.sp,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .alpha(0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayListContextPlaylistDropDown(
    source: PlayList,
    onAddToPlaylist: (PlayList) -> Unit,
    onCreatePlaylist: (String) -> Unit,
)
{
    val duplicateNameError = stringResource(id = R.string.playlist_picker_duplicate_name)
    var createPlaylistMode by remember {
        mutableStateOf(false)
    }
    var newPlaylistName by remember {
        mutableStateOf("")
    }
    var nameError by remember {
        mutableStateOf<String?>(null)
    }
    val playlists = remember(playList, source.listID) {
        playList
            .filter { it.listID != source.listID }
            .sortedBy { it.name }
    }

    val confirmCreatePlaylist = {
        val trimmedPlaylistName = newPlaylistName.trim()
        when {
            trimmedPlaylistName.isEmpty() -> {}
            playList.any { it.name == trimmedPlaylistName } -> {
                nameError = duplicateNameError
            }
            else -> {
                onCreatePlaylist(trimmedPlaylistName)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (createPlaylistMode) {
            PlayListContextCreatePlaylistItem(
                name = newPlaylistName,
                errorMessage = nameError,
                onNameChange = {
                    newPlaylistName = it
                    nameError = null
                },
                onConfirm = confirmCreatePlaylist,
                onCancel = {
                    createPlaylistMode = false
                    newPlaylistName = ""
                    nameError = null
                },
            )
        } else {
            PlayListContextCreatePlaylistRow(
                onClick = {
                    createPlaylistMode = true
                },
            )
        }

        if (playlists.isEmpty()) {
            Text(
                text = stringResource(id = R.string.playlist_picker_empty),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp, vertical = 8.dp)
                    .alpha(0.48f),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 176.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                playlists.forEach { playlist ->
                    PlayListContextPlaylistOptionItem(
                        playlist = playlist,
                        onClick = {
                            onAddToPlaylist(playlist)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayListContextCreatePlaylistRow(onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onClick)
            .padding(start = 34.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.playlist_picker_create_new),
            fontSize = 13.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_action_add),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PlayListContextCreatePlaylistItem(
    name: String,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
)
{
    val canConfirm = name.trim().isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(start = 24.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.22f))
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.playlist_picker_name_placeholder),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(0.48f),
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.Black withNight Color.White,
                        fontSize = 12.sp,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canConfirm) {
                                onConfirm()
                            }
                        },
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
            PlayListContextSmallIconButton(
                iconRes = R.drawable.ic_action_close,
                enabled = true,
                onClick = onCancel,
            )
            PlayListContextSmallIconButton(
                iconRes = R.drawable.ic_action_check,
                enabled = canConfirm,
                onClick = onConfirm,
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 34.dp, end = 18.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun PlayListContextSmallIconButton(
    iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
)
{
    Box(
        modifier = Modifier
            .size(25.dp)
            .clip(RoundedCornerShape(12.5.dp))
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f),
        )
    }
}

@Composable
private fun PlayListContextPlaylistOptionItem(
    playlist: PlayList,
    onClick: () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onClick)
            .padding(start = 34.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            )
            Text(
                text = if (playlist.songDataList.size == 1) {
                    stringResource(id = R.string.playlist_picker_song_count_one)
                } else {
                    stringResource(id = R.string.playlist_picker_song_count_other, playlist.songDataList.size)
                },
                fontSize = 10.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(0.42f),
            )
        }
    }
}

@Composable
private fun PlayListContextMenuItem(
    label: String,
    iconRes: Int,
    labelTrailingIconRes: Int? = null,
    labelTrailingIconRotation: Float = 0f,
    onClick: () -> Unit,
)
{
    val animatedLabelTrailingIconRotation by animateFloatAsState(
        targetValue = labelTrailingIconRotation,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 420f,
        ),
        label = "PlayListContextMenuItemTrailingIconRotation",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(start = 19.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 14.5.sp,
                lineHeight = 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (labelTrailingIconRes != null) {
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    painter = painterResource(id = labelTrailingIconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            rotationZ = animatedLabelTrailingIconRotation
                        },
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                )
            }
        }
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.74f),
        )
    }
}

@Composable
private fun PlayListContextMenuDivider(color: Color)
{
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .alpha(0.08f)
            .background(color),
    )
}

private fun uriStubMedia(uri: Uri) = YosMediaItem(
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

@Stable
private enum class PlayListType {
    Add, Favorite
}

@Composable
private fun LazyItemScope.PlayListItem(playList: PlayList, itemClick: () -> Unit) {
    val itemInteractionSource = remember { MutableInteractionSource() }
    val itemPressed = itemInteractionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .height(80.dp)
            .fillMaxWidth()
            .background(
                if (itemPressed.value) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = itemInteractionSource,
                indication = null,
                onClick = itemClick,
            )
            .padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = YosRoundedCornerShape(4.dp)
        val density = LocalDensity.current

        Image(painter = painterResource(id = R.drawable.placeholder_playlist_default),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                    this.shape = shape
                }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val outline = shape.createOutline(
                            Size(size.width, size.height),
                            LayoutDirection.Ltr,
                            density
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.1f),
                            style = Stroke(width = 8f)
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.5f),
                            style = Stroke(width = 8f),
                            blendMode = BlendMode.Overlay
                        )
                    }
                })

        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = playList.name,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_action_next), contentDescription = null,
            modifier = Modifier
                .height(40.dp)
                .alpha(0.3f), tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun LazyItemScope.PlayListItem(playListType: PlayListType, title: String, itemClick: () -> Unit) {
    val itemInteractionSource = remember { MutableInteractionSource() }
    val itemPressed = itemInteractionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .height(80.dp)
            .fillMaxWidth()
            .background(
                if (itemPressed.value) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = itemInteractionSource,
                indication = null,
                onClick = itemClick,
            )
            .padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = YosRoundedCornerShape(4.dp)
        val density = LocalDensity.current

        val coverResId = when (playListType)
        {
            PlayListType.Add -> R.drawable.placeholder_playlist_new
            PlayListType.Favorite -> R.drawable.placeholder_playlist_fav
        }
        Image(painter = painterResource(id = coverResId),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                    this.shape = shape
                }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val outline = shape.createOutline(
                            Size(size.width, size.height),
                            LayoutDirection.Ltr,
                            density
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.1f),
                            style = Stroke(width = 8f)
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.5f),
                            style = Stroke(width = 8f),
                            blendMode = BlendMode.Overlay
                        )
                    }
                })

        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_action_next), contentDescription = null,
            modifier = Modifier
                .height(40.dp)
                .alpha(0.3f), tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Pin-aware variant of [PlayListItem] used for the Library list
 * (PRD §5.4). Adds a small pin badge over the cover and a queue-style
 * reorder handle for pinned rows while reorder mode is active.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.PinnedAwarePlayListItem(
    playList: PlayList,
    isPinned: Boolean,
    reorderActive: Boolean,
    reorderEnabled: Boolean,
    draggingThisRow: Boolean,
    dimmed: Boolean,
    reorderHandleModifier: Modifier,
    onPositionChange: (Offset) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = YosRoundedCornerShape(4.dp)
    val density = LocalDensity.current
    val rowAlpha = if (dimmed) 0.35f else 1f
    val context = LocalContext.current
    val itemInteractionSource = remember { MutableInteractionSource() }
    val itemPressed = itemInteractionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .height(80.dp)
            .fillMaxWidth()
            .onGloballyPositioned {
                onPositionChange(it.localToRoot(Offset.Zero))
            }
            .graphicsLayer {
                if (draggingThisRow) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 16f
                }
            }
            .background(
                if (itemPressed.value) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .combinedClickable(
                enabled = !reorderActive,
                interactionSource = itemInteractionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    Vibrator.longClick(context)
                    onLongClick()
                },
            )
            .alpha(rowAlpha)
            .padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Resolve the playlist's URIs to YosMediaItem for the
        // auto-collage fallback. Cheap — same lookup performed each
        // time the row recomposes; with a few hundred songs in the
        // library this is sub-millisecond.
        val songsInPlaylist = androidx.compose.runtime.remember(playList.songDataList) {
            playList.songDataList.mapNotNull { uri ->
                yos.music.player.data.libraries.MusicLibrary.songs.firstOrNull { it.uri == uri }
            }
        }
        val coverContext = LocalContext.current
        Box(modifier = Modifier.size(64.dp)) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        clip = true
                        this.shape = shape
                    }
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            val outline = shape.createOutline(
                                Size(size.width, size.height),
                                LayoutDirection.Ltr,
                                density,
                            )
                            drawOutline(
                                outline = outline,
                                color = Color.Gray.copy(alpha = 0.1f),
                                style = Stroke(width = 8f),
                            )
                            drawOutline(
                                outline = outline,
                                color = Color.Gray.copy(alpha = 0.5f),
                                style = Stroke(width = 8f),
                                blendMode = BlendMode.Overlay,
                            )
                        }
                    },
            ) {
                when {
                    !playList.coverUri.isNullOrBlank() -> {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(coverContext)
                                .data(playList.coverUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    songsInPlaylist.isEmpty() -> {
                        Image(
                            painter = painterResource(id = R.drawable.placeholder_playlist_default),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        PlayListAutoCover(songs = songsInPlaylist)
                    }
                }
            }
            if (isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = YosRoundedCornerShape(9.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_pin),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }

        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                text = playList.name,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
        }

        if (reorderEnabled) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .then(reorderHandleModifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_queue_reorder),
                    contentDescription = stringResource(R.string.playlist_edit_drag_handle_cd),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.34f),
                )
            }
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next),
                contentDescription = null,
                modifier = Modifier
                    .height(40.dp)
                    .alpha(0.3f),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
