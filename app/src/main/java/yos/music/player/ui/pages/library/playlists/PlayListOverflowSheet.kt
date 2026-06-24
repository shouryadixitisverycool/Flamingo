package yos.music.player.ui.pages.library.playlists

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.ActionItem
import yos.music.player.ui.widgets.basic.ActionSheetBody
import yos.music.player.ui.widgets.basic.YosBottomSheetDialog
import yos.music.player.ui.widgets.playlist.PlayListPickerContent

/**
 * Internal screens hosted by [PlayListOverflowSheet]. Pattern mirrors
 * the NowPlaying overflow menu (single sheet, swapped body) so
 * sub-screens transition in place without the close + reopen
 * animation that two separate sheets would cause. PRD §5.2 FR-M-01.
 */
private enum class OverflowScreen { Menu, Sort, AddToPlaylist }

/**
 * PRD §5.2: full-width bottom sheet matching the NowPlaying overflow
 * menu visually. Hosts Sort By, Edit Playlist, Pin/Unpin Playlist,
 * Add to a Playlist…, Play Next, and Delete Playlist.
 *
 * Live state: pin label and icon read fresh from [playList] each
 * recomposition so a pin toggle from a parallel surface (e.g. another
 * device, or — once implemented — a long-press menu in the Library)
 * is reflected here without dismissing the sheet.
 *
 * @param isOpen visibility; setting to false dismisses.
 * @param playList snapshot of the playlist whose actions are
 *   surfaced. Mutating actions re-read the live record by [listID]
 *   so a stale snapshot doesn't roll back parallel edits.
 * @param onEdit invoked when the user picks "Edit Playlist". The
 *   host page is responsible for opening its Edit modal (PRD §5.3).
 * @param onPlayNext invoked when the user picks "Play Next" — the
 *   host enqueues the playlist's current song list after the now-
 *   playing track (PRD FR-M-09).
 * @param onDelete invoked when the user picks "Delete Playlist".
 *   The host removes the playlist and surfaces the undo snackbar
 *   (PRD FR-M-10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListOverflowSheet(
    isOpen: MutableState<Boolean>,
    playList: PlayList,
    onEdit: () -> Unit,
    onPlayNext: () -> Unit,
    onDelete: () -> Unit,
) {
    if (!isOpen.value) return

    var screen by remember { mutableStateOf(OverflowScreen.Menu) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onDismiss: () -> Unit = {
        isOpen.value = false
        screen = OverflowScreen.Menu
    }

    YosBottomSheetDialog(
        bottomSheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        when (screen) {
            OverflowScreen.Menu -> OverflowMenuBody(
                snapshot = playList,
                onPickSort = { screen = OverflowScreen.Sort },
                onEdit = {
                    onDismiss()
                    onEdit()
                },
                onPlayNext = {
                    onDismiss()
                    onPlayNext()
                },
                onAddToPlaylist = { screen = OverflowScreen.AddToPlaylist },
                onDelete = {
                    onDismiss()
                    onDelete()
                },
            )

            OverflowScreen.Sort -> PlayListSortBody(
                onBack = { screen = OverflowScreen.Menu },
            )

            OverflowScreen.AddToPlaylist -> BulkAddToPlaylistBody(
                source = playList,
                onDone = onDismiss,
                onBack = { screen = OverflowScreen.Menu },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListSortSheet(isOpen: MutableState<Boolean>) {
    if (!isOpen.value) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    YosBottomSheetDialog(
        bottomSheetState = sheetState,
        onDismissRequest = { isOpen.value = false },
    ) {
        PlayListSortBody(onBack = { isOpen.value = false })
    }
}

@Composable
private fun OverflowMenuBody(
    snapshot: PlayList,
    onPickSort: () -> Unit,
    onEdit: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    val sortLabel = stringResource(R.string.playlist_overflow_sort_by)
    val editLabel = stringResource(R.string.playlist_overflow_edit)
    val addToLabel = stringResource(R.string.playlist_overflow_add_to)
    val playNextLabel = stringResource(R.string.playlist_overflow_play_next)
    val deleteLabel = stringResource(R.string.playlist_overflow_delete)

    // Resolve to the live record so the pin label/icon reflect any
    // concurrent state changes. Falls back to the snapshot if the
    // playlist no longer exists (it's been deleted while the sheet
    // was open — extremely unlikely but worth covering).
    val live = playList.firstOrNull { it.listID == snapshot.listID } ?: snapshot
    val destructive = MaterialTheme.colorScheme.error

    val items = listOf(
        ActionItem(
            iconRes = R.drawable.ic_action_sort,
            label = sortLabel,
            onClick = onPickSort,
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_edit,
            label = editLabel,
            showChevron = false,
            onClick = onEdit,
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_add,
            label = addToLabel,
            onClick = onAddToPlaylist,
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_play_next,
            label = playNextLabel,
            showChevron = false,
            onClick = onPlayNext,
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_delete,
            label = deleteLabel,
            tint = destructive,
            showChevron = false,
            onClick = onDelete,
        ),
    )

    ActionSheetBody(
        header = { PlayListOverflowHeader(playList = live) },
        items = items,
    )
}

/**
 * Header row matching the NowPlaying overflow sheet conventions: 64dp
 * rounded cover + name + (optional) description as subtitle. PRD FR-M-02.
 *
 * Cover resolution mirrors the detail page header: custom photo →
 * auto-collage → default star placeholder.
 */
@Composable
private fun PlayListOverflowHeader(playList: PlayList) {
    val context = LocalContext.current
    val shape = YosRoundedCornerShape(8.dp)
    val songsInPlaylist = androidx.compose.runtime.remember(playList.songDataList) {
        playList.songDataList.mapNotNull { uri ->
            yos.music.player.data.libraries.MusicLibrary.songs.firstOrNull { it.uri == uri }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(64.dp)
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
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = playList.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!playList.description.isNullOrBlank()) {
                Text(
                    text = playList.description,
                    fontSize = 13.5.sp,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .alpha(0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Sort-options sub-sheet. PRD FR-M-05: view-only sort — does NOT
 * persist a new playlist order. The chosen sort is held by
 * [PlayListSortPreference] and applied client-side by NormalMusic.
 */
@Composable
private fun PlayListSortBody(onBack: () -> Unit) {
    val currentSort = PlayListSortPreference.sort
    val descending = PlayListSortPreference.descending

    val title = stringResource(R.string.playlist_sort_title)
    val labelManual = stringResource(R.string.playlist_sort_manual)
    val labelTitle = stringResource(R.string.playlist_sort_title_name)
    val labelArtist = stringResource(R.string.playlist_sort_artist)
    val labelDate = stringResource(R.string.playlist_sort_recently_added)
    val labelAsc = stringResource(R.string.playlist_sort_ascending)
    val labelDesc = stringResource(R.string.playlist_sort_descending)
    val accent = MaterialTheme.colorScheme.primary

    SubSheetHeader(title = title, onBack = onBack)

    Spacer(modifier = Modifier.height(12.dp))

    val items = listOf(
        ActionItem(
            iconRes = R.drawable.ic_action_drag_handle,
            label = labelManual,
            tint = if (currentSort == PlayListSort.Manual) accent else null,
            showChevron = false,
            onClick = { PlayListSortPreference.sort = PlayListSort.Manual },
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_sort,
            label = labelTitle,
            tint = if (currentSort == PlayListSort.Title) accent else null,
            showChevron = false,
            onClick = { PlayListSortPreference.sort = PlayListSort.Title },
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_sort,
            label = labelArtist,
            tint = if (currentSort == PlayListSort.Artist) accent else null,
            showChevron = false,
            onClick = { PlayListSortPreference.sort = PlayListSort.Artist },
        ),
        ActionItem(
            iconRes = R.drawable.ic_action_sort,
            label = labelDate,
            tint = if (currentSort == PlayListSort.RecentlyAdded) accent else null,
            showChevron = false,
            onClick = { PlayListSortPreference.sort = PlayListSort.RecentlyAdded },
        ),
    )

    ActionSheetBody(items = items)

    Spacer(modifier = Modifier.height(16.dp))
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White),
    )
    Spacer(modifier = Modifier.height(12.dp))

    ActionSheetBody(
        items = listOf(
            ActionItem(
                iconRes = R.drawable.ic_action_sort,
                label = labelAsc,
                tint = if (!descending) accent else null,
                showChevron = false,
                onClick = { PlayListSortPreference.descending = false },
            ),
            ActionItem(
                iconRes = R.drawable.ic_action_sort,
                label = labelDesc,
                tint = if (descending) accent else null,
                showChevron = false,
                onClick = { PlayListSortPreference.descending = true },
            ),
        ),
    )
}

/**
 * PRD FR-M-08: "Add to a Playlist…" on a playlist hands off to the
 * shared picker in **bulk mode**. The list view shows existing
 * playlists (minus the source itself) plus a "Create New Playlist"
 * row at the top. Selecting any target bulk-copies every song from
 * the source playlist into it; creating a new one does the same
 * once the playlist exists.
 *
 * Why one helper instead of two flows: re-using the same picker
 * surface keeps the look identical to the NowPlaying per-song
 * picker. The PRD called the per-song picker "the picker we've
 * made we'll reuse it" — this completes that reuse for bulk.
 */
@Composable
private fun BulkAddToPlaylistBody(
    source: PlayList,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Single helper used for both "tap existing" and "tap created"
    // paths so the bulk-insert behaviour is identical regardless of
    // where the target came from.
    val performBulkAdd: (PlayList) -> Unit = { target ->
        val urisToAdd = source.songDataList
        MainScope().launch(Dispatchers.IO) {
            urisToAdd.forEach { uri ->
                // Re-read the live record on every iteration so the
                // copy + replace cycle inside addMusic doesn't
                // overwrite earlier inserts (each addMusic builds a
                // copy and writes back to playList, so subsequent
                // calls need to see the latest snapshot).
                val live = playList.firstOrNull { it.listID == target.listID }
                    ?: return@forEach
                val stub = uriStubMedia(uri)
                PlayListLibrary.run { live.addMusic(stub) }
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

    PlayListPickerContent(
        songToAdd = null,
        onDone = onDone,
        onBack = onBack,
        bulkAddSource = source,
        onBulkAdd = performBulkAdd,
        onCreated = { created -> performBulkAdd(created) },
    )
}

/**
 * Build a minimal [YosMediaItem] holding only the URI. [PlayList]
 * stores raw URIs so this stub is sufficient for the data layer.
 * Metadata fields are nullable on the model so we leave them null.
 */
private fun uriStubMedia(uri: android.net.Uri) = YosMediaItem(
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

/**
 * Shared header for sub-sheets — back arrow on the left, bold title.
 * Visually mirrors the picker's [PlayListPickerContent] header so
 * users navigating between sub-screens see a consistent affordance.
 */
@Composable
private fun SubSheetHeader(title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onBack()
                },
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
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
