package yos.music.player.ui.pages.library.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

/**
 * Activity-level host for the playlist delete-undo snackbar.
 * Rendered inline at the activity root so the undo window outlives
 * any single page's lifecycle — the user can tap Library → Songs
 * while the timer is running and the snackbar stays put. The 5s
 * auto-dismiss is owned by [PendingPlayListDeletion], not by a
 * composable's coroutine.
 *
 * Sits flush against the bottom of the screen offset by
 * [bottomOffset] (the mini-player's height) so it floats right
 * above the mini-player with a small visible gap.
 */
@Composable
fun UndoSnackbarHost(bottomOffset: Dp = 62.dp) {
    val pending = PendingPlayListDeletion.current
    val visible = pending != null

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        enter = fadeIn(animationSpec = tween(200)) +
                slideInVertically(
                    animationSpec = tween(240),
                    initialOffsetY = { fullHeight -> fullHeight },
                ),
        exit = fadeOut(animationSpec = tween(180)) +
                slideOutVertically(
                    animationSpec = tween(220),
                    targetOffsetY = { fullHeight -> fullHeight },
                ),
    ) {
        val playList = pending?.playList
        val message = if (playList != null) {
            stringResource(R.string.playlist_delete_undo_message, playList.name)
        } else ""
        val actionLabel = stringResource(R.string.playlist_delete_undo_action)
        UndoSnackbarSurface(
            bottomOffset = bottomOffset,
            message = message,
            actionLabel = actionLabel,
            onAction = {
                PendingPlayListDeletion.consume()?.let {
                    PlayListLibrary.restore(it.playList, it.originalIndex)
                }
            },
        )
    }
}

@Composable
private fun UndoSnackbarSurface(
    bottomOffset: Dp,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    val surfaceColor = Color.White withNight Color(0xFF1C1C1E)
    val textColor = Color.Black withNight Color.White

    val dragOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Sits flush with the bottom of the screen, offset only
            // by the mini-player so it floats right above it. An 8dp
            // gap keeps the two surfaces visually distinct.
            .padding(bottom = bottomOffset + 8.dp, start = 8.dp, end = 8.dp)
            .graphicsLayer { translationY = dragOffset.value }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, delta ->
                        coroutineScope.launch {
                            val next = (dragOffset.value + delta).coerceAtLeast(0f)
                            dragOffset.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        if (dragOffset.value >= dismissThresholdPx)
                        {
                            PendingPlayListDeletion.clear()
                            coroutineScope.launch { dragOffset.snapTo(0f) }
                        }
                        else
                        {
                            coroutineScope.launch { dragOffset.animateTo(0f) }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { dragOffset.animateTo(0f) }
                    },
                )
            }
            .background(
                color = surfaceColor,
                shape = YosRoundedCornerShape(14.dp),
            )
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            color = textColor,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onAction()
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = actionLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
            )
        }
    }
}
