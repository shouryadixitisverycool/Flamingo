package yos.music.player.ui.pages.library

import android.widget.Toast
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache

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
    var resetAnimationJob by remember(music.uri) {
        mutableStateOf<Job?>(null)
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
                resetAnimationJob = coroutineScope.launch {
                    if (shouldAddToQueue && onQueueSwipe?.invoke() == true) {
                        Toast.makeText(context, addedToQueueToast, Toast.LENGTH_SHORT).show()
                    }

                    val animationStart = swipeOffsetPx

                    animate(
                        initialValue = animationStart,
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 180),
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
            .onSizeChanged {
                rowWidthPx = it.width.toFloat()
                rowHeightPx = it.height.toFloat()
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
                .offset {
                    IntOffset(swipeOffsetPx.roundToInt(), 0)
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
            itemClick = itemClick,
        )
    }
}

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
    itemClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .fillMaxWidth()
            .clickable {
                itemClick()
            }
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
