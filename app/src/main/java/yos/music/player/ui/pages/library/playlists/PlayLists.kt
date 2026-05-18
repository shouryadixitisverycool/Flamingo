package yos.music.player.ui.pages.library.playlists

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
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

@Composable
fun PlayLists(navController: NavController) {
    // PRD §5.4: pinned playlists float to the top in pinOrder asc;
    // unpinned tail keeps the existing alpha-by-name order.
    val playLists = playList
    val pinned = playLists.filter { it.isPinned }
        .sortedBy { it.pinOrder ?: Int.MAX_VALUE }
    val unpinned = playLists.filter { !it.isPinned }.sortedBy { it.name }
    val orderedAll = pinned + unpinned

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // PRD FR-P-08: long-press → drag-reorder pins. Held at the page
    // scope so a Done tap (or back press) can commit / cancel.
    val reorder = remember { PinReorderState() }
    val rowHeightPx = with(LocalDensity.current) { 80.dp.toPx() }

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

    // Effective rendering order: while reorder mode is active, the
    // pinned section follows the live working order from
    // [PinReorderState] so the user sees their drag take effect
    // immediately. Otherwise it follows the persisted pinOrder.
    val effectiveOrder = remember(pinned, unpinned, reorder.active, reorder.workingOrder.toList()) {
        if (reorder.active) {
            val pinById = pinned.associateBy { it.listID }
            val orderedPinned = reorder.workingOrder.mapNotNull { pinById[it] }
            orderedPinned + unpinned
        } else {
            orderedAll
        }
    }

    Title(
            title = stringResource(id = R.string.page_library_playlists),
            onBack = {
                if (reorder.active) reorder.cancel() else navController.popBackStack()
            },
            rightBarIcon = if (reorder.active) {
                {
                    TitleBarIcon(
                        icon = Icons.Default.Check,
                        onBack = {
                            PlayListLibrary.reorderPins(reorder.snapshot())
                            reorder.cancel()
                        },
                    )
                }
            } else null,
            content = {
                item("AddList") {
                    val targetTitle = context.getString(R.string.page_library_playlists_add_title)
                    PlayListItem(playListType = PlayListType.Add, title = targetTitle) {
                        if (!reorder.active) createPickerOpen.value = true
                    }
                    PlayListDivider()
                }

                item("FavList") {
                    val targetTitle = context.getString(R.string.page_library_playlists_fav_title)
                    PlayListItem(playListType = PlayListType.Favorite, title = targetTitle) {
                        if (reorder.active) return@PlayListItem
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
                    effectiveOrder,
                    key = { _, p -> p.listID }
                ) { index, playList ->
                    val isPinned = playList.isPinned
                    val dimmed = reorder.active && !isPinned
                    val draggingThis = reorder.draggingId == playList.listID

                    PinnedAwarePlayListItem(
                        playList = playList,
                        isPinned = isPinned,
                        reorderActive = reorder.active,
                        draggingThisRow = draggingThis,
                        dragOffsetPx = if (draggingThis) reorder.dragOffset else 0f,
                        dimmed = dimmed,
                        onClick = {
                            if (reorder.active) return@PinnedAwarePlayListItem
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
                        },
                        onLongPress = if (isPinned) {
                            {
                                if (!reorder.active) {
                                    Vibrator.longClick(context)
                                    reorder.enter(pinned.map { it.listID })
                                }
                            }
                        } else null,
                        onDragStart = if (isPinned) {
                            { reorder.startDrag(playList.listID) }
                        } else null,
                        onDrag = if (isPinned) {
                            { delta -> reorder.updateDrag(delta, rowHeightPx) }
                        } else null,
                        onDragEnd = if (isPinned) {
                            { reorder.endDrag() }
                        } else null,
                    )

                    key(index) {
                        val needDivider = index < effectiveOrder.size - 1
                        if (needDivider) {
                            PlayListDivider()
                        }
                    }
                }
            }
        )
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
        println("重组：播放列表 ${playList.name}")

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
        println("重组：播放列表 ${playListType.name}")

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
 * (PRD §5.4). Adds:
 *   - a small pin badge over the cover when [isPinned],
 *   - a long-press detector on pinned rows that enters reorder
 *     mode (via the caller-supplied [onLongPress]),
 *   - a trailing drag handle and translation Y while in reorder mode.
 *
 * Non-pinned rows behave exactly like the legacy [PlayListItem]; the
 * external [dimmed] flag drives a visual "this row isn't interactive
 * right now" cue while reorder mode is active.
 */
@Composable
private fun LazyItemScope.PinnedAwarePlayListItem(
    playList: PlayList,
    isPinned: Boolean,
    reorderActive: Boolean,
    draggingThisRow: Boolean,
    dragOffsetPx: Float,
    dimmed: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    onDragStart: (() -> Unit)?,
    onDrag: ((Float) -> Unit)?,
    onDragEnd: (() -> Unit)?,
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
                translationY = dragOffsetPx
                if (draggingThisRow) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                    shadowElevation = 16f
                }
            }
            .clickable(enabled = !reorderActive) { onClick() }
            .then(
                if (onLongPress != null) {
                    Modifier.pointerInput(playList.listID, reorderActive) {
                        if (reorderActive) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart?.invoke() },
                                onDragEnd = { onDragEnd?.invoke() },
                                onDragCancel = { onDragEnd?.invoke() },
                                onDrag = { _, dragAmount ->
                                    onDrag?.invoke(dragAmount.y)
                                },
                            )
                        } else {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onLongPress() },
                                onDragEnd = {},
                                onDragCancel = {},
                                onDrag = { _, _ -> },
                            )
                        }
                    }
                } else Modifier
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

        if (reorderActive && isPinned) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_drag_handle),
                contentDescription = stringResource(R.string.playlist_edit_drag_handle_cd),
                modifier = Modifier
                    .size(28.dp)
                    .alpha(0.55f),
                tint = MaterialTheme.colorScheme.onBackground,
            )
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

