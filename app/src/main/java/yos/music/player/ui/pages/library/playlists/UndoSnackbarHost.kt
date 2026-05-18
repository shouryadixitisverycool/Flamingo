package yos.music.player.ui.pages.library.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

/**
 * Activity-level host for the playlist delete-undo snackbar
 * (PRD FR-M-10). Rendered via a [Popup] so the snackbar is a
 * window-level overlay independent of the current page in the
 * NavHost — the undo window must outlive any single page's
 * lifecycle (the user might tap Library → Songs while the timer
 * is running). The 5s auto-dismiss is owned by
 * [PendingPlayListDeletion], not by a composable's coroutine.
 *
 * Visually anchored just above the mini-player. The Popup fills
 * the screen and we position the snackbar with
 * [Alignment.BottomCenter] + [navigationBarsPadding] + a static
 * lift that matches MainActivity's mini-player height (62.dp) +
 * the bottom tab strip (~80.dp).
 */
@Composable
fun UndoSnackbarHost() {
    val pending = PendingPlayListDeletion.current
    val visible = pending != null

    Popup(
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
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
    }
}

@Composable
private fun UndoSnackbarSurface(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    // Surface colour matches the mini-player (light: white, dark:
    // #1C1C1E) so the two bars read as a single floating stack.
    val surfaceColor = Color.White withNight Color(0xFF1C1C1E)
    val textColor = Color.Black withNight Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Lift above the bottom tab strip (~78.dp) + mini-player
            // (62.dp); a 6.dp gap keeps the two surfaces visually
            // distinct without leaving a giant void between them.
            .padding(bottom = 146.dp, start = 8.dp, end = 8.dp)
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
