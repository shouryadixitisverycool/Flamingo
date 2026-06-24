package yos.music.player.ui.pages.library.playlists

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMapNotNull
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
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

    PlayListPickerSheet(
        isOpen = createPickerOpen,
        songToAdd = null,
    )

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
                                onLongPress = null,
                                onClick = playlistClick,
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
                            onLongPress = if (isPinned && !reorderActive.value && pinned.size > 1) {
                                {
                                    Vibrator.longClick(context)
                                    enterReorderMode()
                                }
                            } else null,
                            onClick = playlistClick,
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

@Stable
private enum class PlayListType {
    Add, Favorite
}

@Composable
private fun LazyItemScope.PlayListItem(playList: PlayList, itemClick: () -> Unit) {
    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .height(80.dp)
            .fillMaxWidth()
            .clickable {
                itemClick()
            }
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
    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .height(80.dp)
            .fillMaxWidth()
            .clickable {
                itemClick()
            }
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
@Composable
private fun LazyItemScope.PinnedAwarePlayListItem(
    playList: PlayList,
    isPinned: Boolean,
    reorderActive: Boolean,
    reorderEnabled: Boolean,
    draggingThisRow: Boolean,
    dimmed: Boolean,
    reorderHandleModifier: Modifier,
    onLongPress: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val shape = YosRoundedCornerShape(4.dp)
    val density = LocalDensity.current
    val rowAlpha = if (dimmed) 0.35f else 1f

    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .height(80.dp)
            .fillMaxWidth()
            .graphicsLayer {
                if (draggingThisRow) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 16f
                }
            }
            .then(
                if (onLongPress != null) {
                    Modifier.pointerInput(playList.listID) {
                        detectTapGestures(
                            onTap = {
                                if (!reorderActive) { onClick() }
                            },
                            onLongPress = { onLongPress() },
                        )
                    }
                } else {
                    Modifier.clickable(enabled = !reorderActive) { onClick() }
                }
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
