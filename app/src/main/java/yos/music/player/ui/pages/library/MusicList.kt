package yos.music.player.ui.pages.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yos.music.player.ui.UI
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.FavPlayListLibrary
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.addMusic
import yos.music.player.data.libraries.PlayListLibrary.playList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultAlbum
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache

private val SongContextMenuEstimatedHeight = 278.dp

@Composable
fun /*LazyItemScope.*/MusicList(
    music: YosMediaItem,
    modifier: Modifier = Modifier,
    titleText: String = music.title ?: defaultTitle,
    subtitleText: String? = music.artistsName ?: defaultArtistsName,
    showArtwork: Boolean = true,
    artworkSize: Dp = 52.dp,
    leadingWidth: Dp = 24.dp,
    horizontalPadding: Dp = 22.dp,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable (RowScope.() -> Unit))? = null,
    onQueueSwipe: (suspend () -> Boolean)? = null,
    navController: NavController? = null,
    itemClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val addedToQueueToast = stringResource(id = R.string.queue_added_toast)
    var rowWidthPx by remember(music.uri) {
        mutableFloatStateOf(0f)
    }
    var rowHeightPx by remember(music.uri) {
        mutableFloatStateOf(0f)
    }
    var swipeOffsetPx by remember(music.uri) {
        mutableFloatStateOf(0f)
    }
    var rowRootPosition by remember(music.uri) {
        mutableStateOf(Offset.Zero)
    }
    var resetAnimationJob by remember(music.uri) {
        mutableStateOf<Job?>(null)
    }
    val contextMenuOpen = remember(music.uri) {
        mutableStateOf(false)
    }

    val swipeEnabled = onQueueSwipe != null
    val triggerOffsetPx = rowWidthPx * 0.20f
    val maxSwipeOffsetPx = rowWidthPx
    val swipeProgress = if (maxSwipeOffsetPx > 0f) {
        (swipeOffsetPx / maxSwipeOffsetPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rowHeight = with(density) {
        rowHeightPx.toDp()
    }
    val swipeRevealWidth = with(density) {
        swipeOffsetPx.toDp()
    }

    val dragModifier = if (swipeEnabled) {
        val queueSwipeAction = checkNotNull(onQueueSwipe)

        Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                if (rowWidthPx <= 0f) {
                    return@rememberDraggableState
                }

                resetAnimationJob?.cancel()

                val wasPastThreshold = swipeOffsetPx >= triggerOffsetPx
                swipeOffsetPx = (swipeOffsetPx + delta).coerceIn(0f, maxSwipeOffsetPx)
                val isPastThreshold = swipeOffsetPx >= triggerOffsetPx

                if (wasPastThreshold != isPastThreshold) {
                    if (isPastThreshold) {
                        Vibrator.longClick(context)
                    } else {
                        Vibrator.click(context)
                    }
                }
            },
            onDragStopped = {
                val shouldAddToQueue = triggerOffsetPx > 0f && swipeOffsetPx >= triggerOffsetPx

                resetAnimationJob?.cancel()

                if (shouldAddToQueue) {
                    Toast.makeText(context, addedToQueueToast, Toast.LENGTH_SHORT).show()
                    coroutineScope.launch(Dispatchers.IO) {
                        queueSwipeAction()
                    }
                }

                resetAnimationJob = coroutineScope.launch {
                    val animationStart = swipeOffsetPx

                    animate(
                        initialValue = animationStart,
                        targetValue = 0f,
                        animationSpec = SpringSpec(
                            dampingRatio = 0.72f,
                            stiffness = 420f,
                            visibilityThreshold = 0.5f,
                        ),
                    ) { value, _ ->
                        swipeOffsetPx = value
                    }
                }
            },
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onGloballyPositioned {
                rowWidthPx = it.size.width.toFloat()
                rowHeightPx = it.size.height.toFloat()
                rowRootPosition = it.localToRoot(Offset.Zero)
            }
    ) {
        if (swipeEnabled) {
            Box(
                modifier = Modifier
                    .width(swipeRevealWidth)
                    .height(rowHeight)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_swipe_queue),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            val iconScale = 0.82f + (swipeProgress * 0.18f)
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }

        MusicListRow(
            music = music,
            modifier = Modifier
                .graphicsLayer {
                    translationX = swipeOffsetPx
                }
                .background(MaterialTheme.colorScheme.background)
                .then(dragModifier),
            titleText = titleText,
            subtitleText = subtitleText,
            showArtwork = showArtwork,
            artworkSize = artworkSize,
            leadingWidth = leadingWidth,
            horizontalPadding = horizontalPadding,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            itemLongClick = if (navController != null) {
                {
                    contextMenuOpen.value = true
                }
            } else {
                null
            },
            itemClick = itemClick,
        )
    }

    if (navController != null) {
        SongContextMenu(
            music = music,
            visible = contextMenuOpen.value,
            anchorPosition = rowRootPosition,
            visibleState = contextMenuOpen,
            navController = navController,
        )
    }
}

@Composable
private fun SongContextMenu(
    music: YosMediaItem,
    visible: Boolean,
    anchorPosition: Offset,
    visibleState: MutableState<Boolean>,
    navController: NavController,
)
{
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val addedToQueueToast = stringResource(id = R.string.queue_added_toast)
    val artistNames = remember(music) {
        (music.artistsList ?: defaultArtists)
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(defaultArtistsName) }
    }
    val albumName = remember(music) {
        music.album?.takeIf { it.isNotBlank() } ?: defaultAlbum
    }
    val isFavorite by remember(music, FavPlayListLibrary.favPlayList) {
        derivedStateOf {
            FavPlayListLibrary.isFavorite(music)
        }
    }

    SongContextMenuPopup(
        music = music,
        visible = visible,
        anchorPosition = anchorPosition,
        artistNames = artistNames,
        isFavorite = isFavorite,
        onDismiss = {
            visibleState.value = false
        },
        onAddToQueue = {
            visibleState.value = false
            Toast.makeText(context, addedToQueueToast, Toast.LENGTH_SHORT).show()
            coroutineScope.launch(Dispatchers.IO) {
                MediaController.addToQueue(music)
            }
        },
        onAddToPlaylist = { playlist ->
            visibleState.value = false
            playlist.addMusic(music)
            Toast.makeText(
                context,
                context.getString(R.string.playlist_picker_added_toast, playlist.name),
                Toast.LENGTH_SHORT,
            ).show()
        },
        onCreatePlaylist = { playlistName ->
            visibleState.value = false
            PlayListLibrary.create(playlistName)
            val createdPlaylist = playList.firstOrNull { it.name == playlistName }
            if (createdPlaylist != null) {
                createdPlaylist.addMusic(music)
                Toast.makeText(
                    context,
                    context.getString(R.string.playlist_picker_added_toast, playlistName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onToggleFavorite = {
            visibleState.value = false
            if (isFavorite) {
                FavPlayListLibrary.removeMusic(music)
            } else {
                FavPlayListLibrary.addMusic(music)
            }
        },
        onGoToArtist = { artistName ->
            visibleState.value = false
            LibraryObject.setTargetArtistName(artistName)
            LibraryObject.setArtistSongsSearchOnOpen(false)
            navController.toUI(UI.ArtistInfo)
        },
        onGoToAlbum = {
            visibleState.value = false
            LibraryObject.setTargetAlbumName(albumName)
            navController.toUI(UI.AlbumInfo)
        },
    )
}

@Composable
private fun SongContextMenuPopup(
    music: YosMediaItem,
    visible: Boolean,
    anchorPosition: Offset,
    artistNames: List<String>,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (PlayList) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToAlbum: () -> Unit,
)
{
    val keepPopup = remember(music.uri, music.mediaId) {
        mutableStateOf(false)
    }
    val showPopup = remember(music.uri, music.mediaId) {
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
            val maxTop = maxHeight - SongContextMenuEstimatedHeight
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
                SongContextMenuCard(
                    music = music,
                    maxHeight = menuMaxHeight,
                    artistNames = artistNames,
                    isFavorite = isFavorite,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onCreatePlaylist = onCreatePlaylist,
                    onToggleFavorite = onToggleFavorite,
                    onGoToArtist = onGoToArtist,
                    onGoToAlbum = onGoToAlbum,
                )
            }
        }
    }
}

@Composable
private fun SongContextMenuCard(
    music: YosMediaItem,
    maxHeight: Dp,
    artistNames: List<String>,
    isFavorite: Boolean,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (PlayList) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToAlbum: () -> Unit,
)
{
    var playlistOptionsExpanded by remember(music.uri, music.mediaId) {
        mutableStateOf(false)
    }
    var artistOptionsExpanded by remember(music.uri, music.mediaId) {
        mutableStateOf(false)
    }
    val shape = RoundedCornerShape(18.dp)
    val cardColor = Color(0xF4F5F5F5) withNight Color(0xFA181818)
    val dividerColor = Color.Black withNight Color.White
    val favoriteLabel = stringResource(
        id = if (isFavorite) {
            R.string.song_context_remove_from_favourites
        } else {
            R.string.song_context_add_to_favourites
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
        SongContextMenuHeader(
            music = music,
            artistNames = artistNames,
            onArtistClick = onGoToArtist,
            onAlbumClick = onGoToAlbum,
        )
        SongContextMenuDivider(color = dividerColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            SongContextMenuItem(
                label = stringResource(id = R.string.song_context_play_next),
                iconRes = R.drawable.ic_swipe_queue,
                onClick = onAddToQueue,
            )
            SongContextMenuDivider(color = dividerColor)
            SongContextMenuItem(
                label = stringResource(id = R.string.song_context_add_to_playlist),
                iconRes = R.drawable.ic_song_context_playlist,
                labelTrailingIconRes = R.drawable.ic_action_next,
                labelTrailingIconRotation = if (playlistOptionsExpanded) {
                    90f
                } else {
                    0f
                },
                onClick = {
                    playlistOptionsExpanded = !playlistOptionsExpanded
                },
            )
            AnimatedVisibility(visible = playlistOptionsExpanded) {
                SongContextPlaylistDropDown(
                    music = music,
                    onAddToPlaylist = onAddToPlaylist,
                    onCreatePlaylist = onCreatePlaylist,
                )
            }
            SongContextMenuDivider(color = dividerColor)
            SongContextMenuItem(
                label = favoriteLabel,
                iconRes = if (isFavorite) {
                    R.drawable.ic_nowplaying_favorited
                } else {
                    R.drawable.ic_nowplaying_favorite
                },
                onClick = onToggleFavorite,
            )
            SongContextMenuDivider(color = dividerColor)
            SongContextMenuItem(
                label = stringResource(id = R.string.song_context_go_to_artist),
                iconRes = R.drawable.ic_song_context_artist,
                labelTrailingIconRes = if (artistNames.size > 1) {
                    R.drawable.ic_action_next
                } else {
                    null
                },
                labelTrailingIconRotation = if (artistOptionsExpanded) {
                    90f
                } else {
                    0f
                },
                onClick = {
                    if (artistNames.size == 1) {
                        onGoToArtist(artistNames.first())
                    } else {
                        artistOptionsExpanded = !artistOptionsExpanded
                    }
                },
            )
            AnimatedVisibility(visible = artistOptionsExpanded && artistNames.size > 1) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    artistNames.forEach { artistName ->
                        SongContextArtistOptionItem(
                            artistName = artistName,
                            onClick = {
                                onGoToArtist(artistName)
                            },
                        )
                    }
                }
            }
            SongContextMenuDivider(color = dividerColor)
            SongContextMenuItem(
                label = stringResource(id = R.string.song_context_go_to_album),
                iconRes = R.drawable.ic_song_context_album,
                onClick = onGoToAlbum,
            )
        }
    }
}

@Composable
private fun SongContextMenuHeader(
    music: YosMediaItem,
    artistNames: List<String>,
    onArtistClick: (String) -> Unit,
    onAlbumClick: () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadowImageWithCache(
            dataLambda = { music.thumb },
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    onClick = onAlbumClick,
                ),
            cornerRadius = 5.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.LOW,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = music.title ?: defaultTitle,
                fontSize = 14.5.sp,
                lineHeight = 16.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SongContextArtistsText(
                artistNames = artistNames,
                onArtistClick = onArtistClick,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun SongContextPlaylistDropDown(
    music: YosMediaItem,
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
    val playlists = remember(playList) {
        playList.sortedBy { it.name }
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
            SongContextCreatePlaylistItem(
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
            SongContextCreatePlaylistRow(
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
                    SongContextPlaylistOptionItem(
                        playlist = playlist,
                        isAlreadyIn = playlist.songDataList.contains(music.uri),
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
private fun SongContextCreatePlaylistRow(onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(
                onClick = onClick,
            )
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
private fun SongContextCreatePlaylistItem(
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
                    textStyle = androidx.compose.ui.text.TextStyle(
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
            SongContextSmallIconButton(
                iconRes = R.drawable.ic_action_close,
                enabled = true,
                onClick = onCancel,
            )
            SongContextSmallIconButton(
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
private fun SongContextSmallIconButton(
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
private fun SongContextPlaylistOptionItem(
    playlist: PlayList,
    isAlreadyIn: Boolean,
    onClick: () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(
                onClick = onClick,
            )
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
        if (isAlreadyIn) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_check),
                contentDescription = stringResource(id = R.string.playlist_picker_already_added_cd),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun SongContextArtistsText(
    artistNames: List<String>,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
)
{
    val contentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
    val artistsText = remember(artistNames) {
        buildAnnotatedString {
            artistNames.forEachIndexed { index, artistName ->
                pushStringAnnotation(tag = "artist", annotation = artistName)
                append(artistName)
                pop()
                if (index < artistNames.lastIndex) {
                    append("、")
                }
            }
        }
    }
    var artistsTextLayoutResult by remember(artistsText) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    Text(
        text = artistsText,
        color = contentColor,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        modifier = modifier.pointerInput(artistsText) {
            detectTapGestures { offset ->
                val textOffset = artistsTextLayoutResult?.getOffsetForPosition(offset)
                    ?: return@detectTapGestures
                artistsText
                    .getStringAnnotations(tag = "artist", start = textOffset, end = textOffset)
                    .firstOrNull()
                    ?.item
                    ?.let { onArtistClick(it) }
            }
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            artistsTextLayoutResult = it
        },
    )
}

@Composable
private fun SongContextArtistOptionItem(
    artistName: String,
    onClick: () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(
                onClick = onClick,
            )
            .padding(start = 34.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = artistName,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_song_context_artist),
            contentDescription = artistName,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun SongContextMenuItem(
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
        label = "SongContextMenuItemTrailingIconRotation",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(
                onClick = onClick,
            )
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
private fun SongContextMenuDivider(color: Color)
{
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .alpha(0.08f)
            .background(color),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicListRow(
    music: YosMediaItem,
    modifier: Modifier = Modifier,
    titleText: String,
    subtitleText: String?,
    showArtwork: Boolean,
    artworkSize: Dp,
    leadingWidth: Dp,
    horizontalPadding: Dp,
    leadingContent: (@Composable () -> Unit)?,
    trailingContent: (@Composable (RowScope.() -> Unit))?,
    itemLongClick: (() -> Unit)?,
    itemClick: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = itemClick,
                onLongClick = itemLongClick?.let {
                    {
                        Vibrator.longClick(context)
                        it()
                    }
                },
            )
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            showArtwork -> {
                ShadowImageWithCache(
                    dataLambda = { music.thumb },
                    contentDescription = null,
                    modifier = Modifier.size(artworkSize),
                    cornerRadius = 3.5.dp,
                    shadowAlpha = 0f,
                    imageQuality = ImageQuality.LOW
                )
            }

            leadingContent != null -> {
                Box(
                    modifier = Modifier.size(width = leadingWidth, height = artworkSize),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingContent()
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (showArtwork || leadingContent != null) 16.dp else 0.dp)
                .padding(end = if (trailingContent != null) 12.dp else 0.dp)
        ) {
            Text(
                text = titleText,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )

            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    modifier = Modifier.alpha(0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                )
            }
        }

        if (trailingContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailingContent,
            )
        }
    }
}
