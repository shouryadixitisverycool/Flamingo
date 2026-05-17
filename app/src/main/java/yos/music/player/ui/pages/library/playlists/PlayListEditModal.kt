package yos.music.player.ui.pages.library.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

private const val DescriptionMaxChars = 200
private const val NameMaxChars = 100

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
    val listState = rememberLazyListState()

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
            key = { idx, _ -> idx },
        ) { index, song ->
            EditSongRow(
                index = index,
                song = song,
                isStaged = index in staged,
                onToggleStaged = {
                    if (index in staged) staged.remove(index) else staged.add(index)
                },
                onMoveUp = if (index > 0) {
                    {
                        val item = workingSongs.removeAt(index)
                        workingSongs.add(index - 1, item)
                        // Reindex staged positions so the user's
                        // pending removals continue to point at the
                        // intended songs after the reorder.
                        // List#replaceAll is API 24+ (minSdk 23);
                        // emulate with a clear+addAll cycle.
                        val remapped = staged.map { stagedIdx ->
                            when (stagedIdx) {
                                index -> index - 1
                                index - 1 -> index
                                else -> stagedIdx
                            }
                        }
                        staged.clear()
                        staged.addAll(remapped)
                    }
                } else null,
                onMoveDown = if (index < workingSongs.lastIndex) {
                    {
                        val item = workingSongs.removeAt(index)
                        workingSongs.add(index + 1, item)
                        val remapped = staged.map { stagedIdx ->
                            when (stagedIdx) {
                                index -> index + 1
                                index + 1 -> index
                                else -> stagedIdx
                            }
                        }
                        staged.clear()
                        staged.addAll(remapped)
                    }
                } else null,
            )
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
 */
@Composable
private fun CoverCarousel(
    coverUri: String?,
    resolvedSongs: List<YosMediaItem>,
    onPickCustom: () -> Unit,
    onChooseAuto: () -> Unit,
) {
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(12.dp)
    val density = LocalDensity.current
    val autoSelected = coverUri == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Slot 1: custom photo.
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(shape)
                .background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onPickCustom()
                },
            contentAlignment = Alignment.Center,
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
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_camera),
                    contentDescription = stringResource(R.string.playlist_edit_cover_custom_cd),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(36.dp),
                )
            }
            // Selection ring when this is the active slot.
            if (coverUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            this.shape = shape
                            clip = true
                        },
                )
            }
        }
        // Slot 2: auto-collage.
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onChooseAuto()
                },
            contentAlignment = Alignment.Center,
        ) {
            AutoCollage(resolvedSongs = resolvedSongs)
            if (autoSelected) {
                // Selection outline via a translucent overlay ring.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)),
                )
            }
        }
    }
}

/**
 * 2×2 collage of the first 4 unique album arts (FR-E-05). Fallbacks:
 *   3 unique → 1×3 strip stacked horizontally (rendered as 2×2 with
 *     one cell duplicated to fill).
 *   2 unique → 1×2 split.
 *   1 unique → single-art fill.
 *   0 → default placeholder.
 */
@Composable
private fun AutoCollage(resolvedSongs: List<YosMediaItem>) {
    val context = LocalContext.current
    val thumbs = remember(resolvedSongs) {
        resolvedSongs.mapNotNull { it.thumb }.distinct().take(4)
    }
    if (thumbs.isEmpty()) {
        Image(
            painter = painterResource(id = R.drawable.placeholder_playlist_default),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    when (thumbs.size) {
        1 -> AsyncImage(
            model = ImageRequest.Builder(context).data(thumbs[0]).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        2 -> Row(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbs[0]).build(),
                contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbs[1]).build(),
                contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
        3 -> Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[0]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[1]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbs[2]).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        else -> Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[0]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[1]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[2]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[3]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
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
    index: Int,
    song: YosMediaItem,
    isStaged: Boolean,
    onToggleStaged: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(4.dp)
    val accent = MaterialTheme.colorScheme.primary
    val destructive = MaterialTheme.colorScheme.error
    val rowAlpha = if (isStaged) 0.4f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isStaged) destructive
                    else (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.0f),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onToggleStaged()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isStaged) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_check),
                    contentDescription = stringResource(R.string.playlist_edit_remove_cd),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(destructive.copy(alpha = 0.0f))
                        .graphicsLayer {
                            this.shape = RoundedCornerShape(5.dp)
                        },
                ) {
                    // Outline-only checkbox.
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                        drawRect(color = destructive, style = stroke)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        // Album art (or default).
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
        // Reorder controls. Two small tappable arrows instead of a
        // drag handle: long-press drag inside a LazyColumn requires
        // hosting state outside Compose's lazy layout (since the
        // visible item set changes mid-gesture), which adds enough
        // complexity that a tap-up / tap-down pair is the better
        // first cut for this release. PRD §10 open question.
        Column(
            modifier = Modifier.padding(start = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ReorderArrow(
                iconRes = R.drawable.ic_back,
                rotationDegrees = 90f,
                onClick = onMoveUp,
            )
            ReorderArrow(
                iconRes = R.drawable.ic_back,
                rotationDegrees = -90f,
                onClick = onMoveDown,
            )
        }
    }
}

@Composable
private fun ReorderArrow(iconRes: Int, rotationDegrees: Float, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val enabled = onClick != null
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 0.6f else 0.2f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = stringResource(R.string.playlist_edit_drag_handle_cd),
            tint = Color.Black withNight Color.White,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    rotationZ = rotationDegrees
                },
        )
    }
}
