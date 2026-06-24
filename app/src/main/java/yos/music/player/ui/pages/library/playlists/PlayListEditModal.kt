package yos.music.player.ui.pages.library.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.MusicLibrary.songs
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.applyEdits
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

private const val DescriptionMaxChars = 200
private const val NameMaxChars = 100
private const val EditPlaylistSongListOffset = 5
private val EditPlaylistRowHeight = 56.dp
private val EditPlaylistDraggingItemShape = RoundedCornerShape(0.dp)

private fun playlistEditItemKey(
    songUri: Uri,
    index: Int,
    songUris: List<Uri>,
): String {
    val duplicateOrdinal = songUris
        .take(index + 1)
        .count { it == songUri }

    return "edit_playlist:$songUri:$duplicateOrdinal"
}

private fun resolvePlaylistReorderTarget(
    lazyListIndex: Int,
    songListSize: Int,
): Int? {
    val songIndex = lazyListIndex - EditPlaylistSongListOffset

    if (songIndex !in 0 until songListSize) {
        return null
    }

    return songIndex
}

private fun movePlaylistSongDuringDrag(
    workingSongs: SnapshotStateList<Uri>,
    staged: SnapshotStateList<Int>,
    fromIndex: Int,
    toIndex: Int,
) {
    if (fromIndex !in workingSongs.indices || toIndex !in workingSongs.indices || fromIndex == toIndex) {
        return
    }

    val movedSong = workingSongs.removeAt(fromIndex)
    workingSongs.add(toIndex, movedSong)

    val remapped = staged.map { stagedIndex ->
        when {
            stagedIndex == fromIndex -> toIndex
            fromIndex < toIndex && stagedIndex in (fromIndex + 1)..toIndex -> stagedIndex - 1
            toIndex < fromIndex && stagedIndex in toIndex until fromIndex -> stagedIndex + 1
            else -> stagedIndex
        }
    }
    staged.clear()
    staged.addAll(remapped)
}

/**
 * Full-screen modal exposing every edit operation defined by PRD
 * §5.3:
 *  - rename (FR-E-06)
 *  - description (FR-E-07)
 *  - cover carousel — custom photo pick + 2×2 auto-collage (FR-E-03,
 *    FR-E-05)
 *  - drag-to-reorder via long-press handle (FR-E-10)
 *  - multi-select removal via leading checkbox (FR-E-09)
 *  - atomic commit on close (FR-E-11), X and ✓ are functionally
 *    identical per the session's product decisions; both call save.
 *
 * Duplicates: the songs list may contain the same URI multiple
 * times; row identity is by list index (FR-E-12 simplified — no
 * schema change required as long as edits are atomic per session).
 *
 * @param isOpen visibility. Setting to false dismisses & commits.
 * @param source playlist to edit. The modal makes a local working
 *   copy on open and only writes back to [PlayListLibrary] on
 *   dismiss.
 * @param onAppliedNameChange optional hook fired with the post-edit
 *   name so the host can update its title bar without waiting for a
 *   recomposition cycle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListEditModal(
    isOpen: MutableState<Boolean>,
    source: PlayList,
    onAppliedNameChange: ((String) -> Unit)? = null,
) {
    if (!isOpen.value) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Local working copy. Saved on dismiss.
    var name by remember(source.listID) { mutableStateOf(source.name) }
    var description by remember(source.listID) { mutableStateOf(source.description.orEmpty()) }
    var coverUri by remember(source.listID) { mutableStateOf(source.coverUri) }
    val workingSongs: SnapshotStateList<Uri> =
        remember(source.listID) { mutableStateListOf<Uri>().apply { addAll(source.songDataList) } }
    val staged: SnapshotStateList<Int> = remember(source.listID) { mutableStateListOf() }

    // Photo picker — uses the modern Android Photo Picker on API
    // 30+, falls back to ACTION_OPEN_DOCUMENT under the hood
    // (handled by AndroidX). The returned URI is persisted in the
    // playlist; we don't copy the bytes locally to keep storage
    // overhead near zero.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) coverUri = uri.toString()
    }

    val commit: () -> Unit = {
        val finalSongs = workingSongs.toList().filterIndexed { idx, _ -> idx !in staged }
        val finalDescription = description.trim().take(DescriptionMaxChars).ifEmpty { null }
        val finalName = name.trim().take(NameMaxChars).ifEmpty { source.name }
        // PRD FR-E-11: atomic write — apply all edits in one
        // PlayListLibrary mutation.
        val live = playList.firstOrNull { it.listID == source.listID } ?: source
        live.applyEdits(
            name = finalName,
            description = finalDescription,
            coverUri = coverUri,
            songs = finalSongs,
        )
        if (finalName != source.name) onAppliedNameChange?.invoke(finalName)

        // Also update the LibraryObject so the host page reflects
        // the new title + (post-removal) song list without needing
        // an explicit nav restart.
        if (LibraryObject.targetPlayListId.value == source.listID) {
            val resolved = finalSongs.mapNotNull { uri ->
                songs.firstOrNull { it.uri == uri }
            }
            LibraryObject.setTargetListWithTitle(
                title = finalName,
                list = resolved,
                playListId = source.listID,
            )
        }
    }

    val dismiss: () -> Unit = {
        commit()
        isOpen.value = false
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        properties = ModalBottomSheetDefaults.properties(),
        containerColor = Color.White withNight Color.Black,
        contentColor = Color.Black withNight Color.White,
        dragHandle = null,
        scrimColor = MaterialTheme.colorScheme.onBackground.copy(0.13f),
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            EditPlaylistContent(
                name = name,
                onNameChange = { name = it.take(NameMaxChars) },
                description = description,
                onDescriptionChange = { description = it.take(DescriptionMaxChars) },
                coverUri = coverUri,
                onPickCustomCover = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onChooseAutoCover = { coverUri = null },
                workingSongs = workingSongs,
                staged = staged,
                onClose = {
                    scope.launch {
                        sheetState.hide()
                        dismiss()
                    }
                },
                onDone = {
                    scope.launch {
                        sheetState.hide()
                        dismiss()
                    }
                },
            )
        }
    }
}

@Composable
private fun EditPlaylistContent(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    coverUri: String?,
    onPickCustomCover: () -> Unit,
    onChooseAutoCover: () -> Unit,
    workingSongs: SnapshotStateList<Uri>,
    staged: SnapshotStateList<Int>,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var draggingPlaylistItemKey by remember {
        mutableStateOf<String?>(null)
    }

    // Resolve URIs → YosMediaItem for display. Falls back to a
    // synthetic placeholder if the song was removed from the library
    // since the playlist was saved (orphan URI).
    val resolvedSongs = remember(workingSongs.toList()) {
        workingSongs.map { uri ->
            songs.firstOrNull { it.uri == uri }
                ?: YosMediaItem(
                    uri = uri, mediaId = null, mimeType = null,
                    title = null, writer = null, compilation = null,
                    composer = null, artists = null, album = null,
                    albumArtists = null, thumb = null,
                    trackNumber = null, discNumber = null, genre = null,
                    recordingDay = null, recordingMonth = null,
                    recordingYear = null, releaseYear = null,
                    artistId = null, albumId = null, genreId = null,
                    author = null, addDate = null, duration = 0L,
                    modifiedDate = null, cdTrackNumber = null,
                )
        }
    }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val source = resolvePlaylistReorderTarget(
            from.index,
            workingSongs.size,
        ) ?: return@rememberReorderableLazyListState
        val destination = resolvePlaylistReorderTarget(
            to.index,
            workingSongs.size,
        ) ?: return@rememberReorderableLazyListState

        if (source == destination) {
            return@rememberReorderableLazyListState
        }

        Vibrator.click(context)
        movePlaylistSongDuringDrag(
            workingSongs = workingSongs,
            staged = staged,
            fromIndex = source,
            toIndex = destination,
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = 120.dp),
    ) {
        item("header") {
            EditHeader(onClose = onClose, onDone = onDone)
        }
        item("cover") {
            CoverCarousel(
                coverUri = coverUri,
                resolvedSongs = resolvedSongs,
                onPickCustom = onPickCustomCover,
                onChooseAuto = onChooseAutoCover,
            )
        }
        item("name") {
            NameField(name = name, onNameChange = onNameChange)
        }
        item("description") {
            DescriptionField(
                description = description,
                onDescriptionChange = onDescriptionChange,
            )
        }
        item("divider") {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp)
                    .alpha(0.15f)
                    .height(0.5.dp)
                    .background(Color.Black withNight Color.White),
            )
        }
        itemsIndexed(
            items = resolvedSongs,
            key = { index, _ -> playlistEditItemKey(workingSongs[index], index, workingSongs) },
        ) { index, song ->
            val itemKey = playlistEditItemKey(workingSongs[index], index, workingSongs)

            ReorderableItem(reorderableState, key = itemKey) { isDragging ->
                EditSongRow(
                    song = song,
                    reorderEnabled = workingSongs.size > 1,
                    isDragging = isDragging || draggingPlaylistItemKey == itemKey,
                    isStaged = index in staged,
                    reorderHandleModifier = Modifier.draggableHandle(
                        onDragStarted = {
                            draggingPlaylistItemKey = itemKey
                            Vibrator.longClick(context)
                        },
                        onDragStopped = {
                            draggingPlaylistItemKey = null
                            Vibrator.click(context)
                        },
                    ),
                    onToggleStaged = {
                        if (index in staged) staged.remove(index) else staged.add(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun EditHeader(onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onClose()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_close),
                contentDescription = stringResource(R.string.playlist_edit_close_cd),
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.playlist_edit_title),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onDone()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_check),
                contentDescription = stringResource(R.string.playlist_edit_done_cd),
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Cover carousel. Slot 1 is the custom-photo picker (or the current
 * selection if a custom URI is set). Slot 2 is the 2×2 auto-collage
 * built from the playlist's first 4 unique album arts. PRD FR-E-03,
 * FR-E-05.
 *
 * Selection is signalled by a 2dp accent-colored border around the
 * active slot; the other slot uses a faint dim ring to read as
 * "tappable but not selected."
 */
@Composable
private fun CoverCarousel(
    coverUri: String?,
    resolvedSongs: List<YosMediaItem>,
    onPickCustom: () -> Unit,
    onChooseAuto: () -> Unit,
) {
    val context = LocalContext.current
    val customSelected = coverUri != null
    val autoSelected = coverUri == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: custom photo picker.
        CarouselSlot(
            selected = customSelected,
            onClick = onPickCustom,
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(coverUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.playlist_edit_cover_custom_cd),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_camera),
                        contentDescription = stringResource(R.string.playlist_edit_cover_custom_cd),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        // Slot 2: auto-collage.
        CarouselSlot(
            selected = autoSelected,
            onClick = onChooseAuto,
        ) {
            PlayListAutoCover(songs = resolvedSongs)
        }
    }
}

@Composable
private fun CarouselSlot(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(12.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
        else (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.35f)
    val borderWidth = if (selected) 3.dp else 1.dp

    Box(
        modifier = Modifier
            .size(160.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                    this.shape = shape
                },
        ) {
            content()
        }
        // Border drawn as an overlay so it sits ABOVE clipped content
        // without being clipped itself.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val outline = shape.createOutline(
                androidx.compose.ui.geometry.Size(size.width, size.height),
                androidx.compose.ui.unit.LayoutDirection.Ltr,
                this,
            )
            drawOutline(
                outline = outline,
                color = borderColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = borderWidth.toPx(),
                ),
            )
        }
    }
}

/**
 * Edit-modal alias for the shared playlist cover collage. See
 * [PlayListAutoCover] for the layout matrix and fallbacks.
 */
@Composable
private fun AutoCollage(resolvedSongs: List<YosMediaItem>) {
    PlayListAutoCover(songs = resolvedSongs)
}

@Composable
private fun NameField(name: String, onNameChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black withNight Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (name.isEmpty()) {
                        Text(
                            text = stringResource(R.string.playlist_edit_name_placeholder),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.alpha(0.35f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun DescriptionField(description: String, onDescriptionChange: (String) -> Unit) {
    BasicTextField(
        value = description,
        onValueChange = onDescriptionChange,
        textStyle = TextStyle(
            color = Color.Black withNight Color.White,
            fontSize = 15.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (description.isEmpty()) {
                    Text(
                        text = stringResource(R.string.playlist_edit_description_placeholder),
                        fontSize = 15.sp,
                        modifier = Modifier.alpha(0.45f),
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun EditSongRow(
    song: YosMediaItem,
    reorderEnabled: Boolean,
    isDragging: Boolean,
    isStaged: Boolean,
    reorderHandleModifier: Modifier,
    onToggleStaged: () -> Unit,
) {
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(4.dp)
    val destructive = MaterialTheme.colorScheme.error
    val rowAlpha = if (isStaged) 0.4f else 1f
    val draggedItemBackground by animateColorAsState(
        targetValue = Color.Transparent,
        label = "EditPlaylistDraggedItemBackground",
    )
    val draggedItemElevation by animateDpAsState(
        targetValue = if (isDragging) { 10.dp } else { 0.dp },
        label = "EditPlaylistDraggedItemElevation",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(EditPlaylistRowHeight),
        color = draggedItemBackground,
        contentColor = Color.Black withNight Color.White,
        shadowElevation = draggedItemElevation,
        shape = EditPlaylistDraggingItemShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .alpha(rowAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemovalCheckbox(
                checked = isStaged,
                destructive = destructive,
                onToggle = onToggleStaged,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(shape),
            ) {
                if (song.thumb != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(song.thumb).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.placeholder_playlist_default),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title ?: defaultTitle,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!song.artists.isNullOrBlank()) {
                    Text(
                        text = song.artists,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .alpha(0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                        tint = Color.Black.copy(alpha = 0.34f) withNight Color.White.copy(alpha = 0.34f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Square checkbox styled for "stage for removal" semantics. The
 * outline is drawn with [androidx.compose.foundation.Canvas] +
 * `drawRoundRect` so the corners are continuous; the previous
 * version composed `drawRect` (sharp corners) inside a parent with a
 * [RoundedCornerShape] clip, which produced the visible breaks the
 * user reported.
 */
@Composable
private fun RemovalCheckbox(
    checked: Boolean,
    destructive: Color,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val boxSize = 22.dp
    val cornerRadius = 5.dp
    val strokeWidth = 1.6.dp

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(boxSize)) {
            val rPx = cornerRadius.toPx()
            val swPx = strokeWidth.toPx()
            if (checked) {
                // Filled rounded square; check glyph is drawn over it
                // by the Icon below.
                drawRoundRect(
                    color = destructive,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx),
                )
            } else {
                // Outline only. Inset by half the stroke so the stroke
                // sits entirely inside the box bounds — otherwise the
                // outer edge gets clipped at 0,0 by the canvas bounds.
                val inset = swPx / 2f
                drawRoundRect(
                    color = destructive,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - swPx,
                        size.height - swPx,
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        rPx - inset, rPx - inset,
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = swPx),
                )
            }
        }
        if (checked) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_check),
                contentDescription = stringResource(R.string.playlist_edit_remove_cd),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
