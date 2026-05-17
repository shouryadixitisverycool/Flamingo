package yos.music.player.ui.widgets.playlist

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
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
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.addMusic
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.YosBottomSheetDialog

/**
 * Reusable bottom sheet for picking an existing playlist to add a song to,
 * OR creating a new playlist.
 *
 * Modes:
 * - **Picker mode** (`songToAdd != null`): shows "Create New Playlist" at the
 *   top followed by the user's existing playlists. Tapping an existing
 *   playlist appends [songToAdd] to it (with de-duplication, per PRD §10 OQ-1
 *   — duplication guarded at the picker level, not the data layer).
 * - **Create-only mode** (`songToAdd == null`): shows ONLY the
 *   "Create New Playlist" row. Used by the Playlists page's "Add" button.
 *
 * Per PRD §5.5 FR-AP-1 through FR-AP-5.
 *
 * @param isOpen controls visibility. Set to false to dismiss.
 * @param songToAdd the song to be added to the selected playlist, or `null`
 *   for create-only mode.
 * @param onCreated optional callback invoked after a new playlist is created
 *   (whether or not [songToAdd] was attached to it). Receives the freshly
 *   created [PlayList].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayListPickerSheet(
    isOpen: MutableState<Boolean>,
    songToAdd: YosMediaItem?,
    onCreated: ((PlayList) -> Unit)? = null,
) {
    if (!isOpen.value) return

    YosBottomSheetDialog(onDismissRequest = { isOpen.value = false }) {
        PlayListPickerContent(
            songToAdd = songToAdd,
            onDone = { isOpen.value = false },
            onCreated = onCreated,
        )
    }
}

/**
 * Bare picker content — the body of [PlayListPickerSheet] without its
 * surrounding [YosBottomSheetDialog]. Use this when you want to render the
 * picker INSIDE another bottom sheet (e.g. the NowPlaying overflow menu
 * swaps its content to this composable when the user picks "Add to a
 * Playlist", so the sub-screen appears without a close + reopen animation).
 *
 * @param songToAdd song to attach to the selected playlist, or `null` for
 *   create-only mode.
 * @param onDone called when the picker should be torn down — either after
 *   a successful add/create, or when the host should close the containing
 *   sheet entirely. The host decides what "done" means (e.g. dismiss the
 *   sheet, or return to a previous internal screen).
 * @param onBack optional callback for hosts that embed the picker inside
 *   their own navigation stack (e.g. the NowPlaying overflow menu wants
 *   the picker's list view to offer a back arrow that returns to the
 *   overflow menu). The picker manages its own internal List ↔ Create
 *   navigation: a single back arrow is shown that either pops Create ➝
 *   List, or — when in List with [onBack] non-null — invokes [onBack] so
 *   the host can pop one level further. When [onBack] is null and the
 *   picker is in its top-level List view, no back arrow is shown.
 * @param onCreated optional callback fired after a new playlist is created.
 */
@Composable
fun PlayListPickerContent(
    songToAdd: YosMediaItem?,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
    onCreated: ((PlayList) -> Unit)? = null,
    /**
     * When non-null the picker enters **bulk mode**: the list of
     * existing playlists is shown (just like single-song add), but
     * tapping a row hands off to [onBulkAdd] instead of attaching
     * [songToAdd] to it. The "Create New Playlist" row at the top
     * still appears and routes through [onCreated]; the host is
     * expected to fold the bulk insert into the onCreated handler.
     *
     * Used by the playlist detail page's "Add to a Playlist…"
     * action (PRD FR-M-08) to copy every song from the source
     * playlist into the selected target.
     */
    bulkAddSource: PlayList? = null,
    onBulkAdd: ((PlayList) -> Unit)? = null,
) {
    val context = LocalContext.current
    val bulkMode = bulkAddSource != null
    // Local UI state: are we in "create new playlist" mode (text input
    // visible) or the default list view? Bulk mode lands in list view
    // by default; the user can still tap "Create New Playlist" to make
    // a fresh target.
    var createMode by remember { mutableStateOf(songToAdd == null && !bulkMode) }
    var newPlaylistName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    val resetTransient: () -> Unit = {
        createMode = songToAdd == null && !bulkMode
        newPlaylistName = ""
        nameError = null
    }

    val finish: () -> Unit = {
        resetTransient()
        onDone()
    }

    val confirmCreate: () -> Unit = {
        val trimmed = newPlaylistName.trim()
        when {
            trimmed.isEmpty() -> {
                // Confirm button is normally disabled; safety net only.
            }
            playList.any { it.name == trimmed } -> {
                nameError = context.getString(R.string.playlist_picker_duplicate_name)
            }
            else -> {
                PlayListLibrary.create(trimmed)
                val created = playList.firstOrNull { it.name == trimmed }
                if (created != null) {
                    if (songToAdd != null) {
                        created.addMusic(songToAdd)
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.playlist_picker_added_toast,
                                trimmed,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.playlist_picker_created_toast,
                                trimmed,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    onCreated?.invoke(created)
                }
                finish()
            }
        }
    }

    // Back-arrow logic:
    //   - In Create mode entered from the list view, back pops to List.
    //   - In List mode, back invokes the host-provided [onBack] when
    //     given so the host can pop one level further.
    //   - In create-only mode (no songToAdd, no bulk source), there is
    //     no internal List to return to, so back falls through to
    //     [onBack] — or is hidden if [onBack] is null.
    val headerBack: (() -> Unit)? = when {
        createMode && (songToAdd != null || bulkMode) -> {
            {
                createMode = false
                newPlaylistName = ""
                nameError = null
            }
        }
        else -> onBack
    }
    PickerHeader(
        title = if (createMode) {
            stringResource(R.string.playlist_picker_create_title)
        } else {
            stringResource(R.string.playlist_picker_title)
        },
        onBack = headerBack,
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (createMode) {
        CreatePlaylistBody(
            name = newPlaylistName,
            onNameChange = {
                newPlaylistName = it
                nameError = null
            },
            errorMessage = nameError,
            onConfirm = confirmCreate,
            onCancel = if (songToAdd == null && !bulkMode) finish else {
                {
                    createMode = false
                    newPlaylistName = ""
                    nameError = null
                }
            },
        )
    } else {
        // Picker mode: "Create New Playlist" entry + list of existing playlists.
        CreateNewRow(
            onClick = {
                Vibrator.click(context)
                createMode = true
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Divider()

        ExistingPlayListList(
            songToAdd = songToAdd,
            excludeId = bulkAddSource?.listID,
            onAdd = { playlist ->
                if (bulkMode) {
                    // PRD FR-M-08: hand off to the host. The host
                    // performs the bulk insert; the picker just
                    // closes once the tap is consumed.
                    onBulkAdd?.invoke(playlist)
                } else if (songToAdd != null) {
                    // PRD FR-E-13: duplicates are allowed across the
                    // app. The picker no longer dedupes — adding the
                    // same song twice produces two distinct entries.
                    playlist.addMusic(songToAdd)
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_picker_added_toast,
                            playlist.name,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                finish()
            },
        )
    }
}

@Composable
private fun PickerHeader(
    title: String,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val context = LocalContext.current
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
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CreatePlaylistBody(
    name: String,
    onNameChange: (String) -> Unit,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val canConfirm = name.trim().isNotEmpty()

    // Text field — borrows the styling used by SearchTextField in
    // widgets/basic/YosTextField.kt for visual consistency.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp),
            )
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (name.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_picker_name_placeholder),
                    fontSize = 16.sp,
                    modifier = Modifier.alpha(0.5f),
                )
            }
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.Black withNight Color.White,
                    fontSize = 16.sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (canConfirm) onConfirm() },
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (errorMessage != null) {
        Text(
            text = errorMessage,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Action buttons. Borrows the style of OptionDialog's positive/negative.
    val buttonHeight = 50.dp
    val buttonShape = RoundedCornerShape(buttonHeight.div(2))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .background(
                color = if (canConfirm) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = buttonShape,
            )
            .clip(buttonShape)
            .clickable(enabled = canConfirm) {
                Vibrator.click(context)
                onConfirm()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.playlist_picker_create_confirm),
            color = Color.White,
            fontSize = 16.5.sp,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .clip(buttonShape)
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
            )
            .clickable {
                Vibrator.click(context)
                onCancel()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.playlist_picker_cancel),
            fontSize = 16.5.sp,
        )
    }
}

@Composable
private fun CreateNewRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_action_add),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.playlist_picker_create_new),
            fontSize = 16.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ExistingPlayListList(
    songToAdd: YosMediaItem?,
    excludeId: String?,
    onAdd: (PlayList) -> Unit,
) {
    val playlists = remember(playList, excludeId) {
        playList.filter { it.listID != excludeId }.sortedBy { it.name }
    }

    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.playlist_picker_empty),
                fontSize = 14.sp,
                modifier = Modifier.alpha(0.5f),
            )
        }
        return
    }

    // Cap list height so the sheet doesn't take over the whole screen for
    // libraries with many playlists. ~5 rows visible before scrolling.
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = playlists,
            key = { _, p -> p.listID },
        ) { _, playlist ->
            ExistingPlayListRow(
                playlist = playlist,
                isAlreadyIn = songToAdd != null && playlist.songDataList.contains(songToAdd.uri),
                onClick = { onAdd(playlist) },
            )
        }
    }
}

@Composable
private fun ExistingPlayListRow(
    playlist: PlayList,
    isAlreadyIn: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick()
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.placeholder_playlist_default),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralSongCount(playlist.songDataList.size),
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isAlreadyIn) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_check),
                contentDescription = stringResource(R.string.playlist_picker_already_added_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.15f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White),
    )
}

@Composable
private fun pluralSongCount(count: Int): String {
    return if (count == 1) {
        stringResource(R.string.playlist_picker_song_count_one)
    } else {
        stringResource(R.string.playlist_picker_song_count_other, count)
    }
}
