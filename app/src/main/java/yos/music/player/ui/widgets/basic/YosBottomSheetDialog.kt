package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

/**
 * Visibility is `internal` so sibling widget packages (e.g. the playlist
 * picker, the sleep-timer sheet) can reuse the same styling internals —
 * corner radius from [SettingsLibrary.ScreenCorner], edge-to-edge insets,
 * scrim alpha, haptic-on-open — without duplicating them.
 *
 * For most use cases prefer the higher-level [OptionDialog] or [ActionSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YosBottomSheetDialog(
    bottomSheetState: SheetState = rememberModalBottomSheetState(),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties(),
    cornerRadius: () -> Dp = { SettingsLibrary.ScreenCorner.toInt().dp },
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val edgeToEdgeEnabled by remember("YosBottomSheetDialog_edgeToEdgeEnabled") {
        mutableStateOf(
            true
        )
    }
    val windowInsets = if (edgeToEdgeEnabled)
        WindowInsets(0) else BottomSheetDefaults.windowInsets

    val context = LocalContext.current

    YosWrapper {
        LaunchedEffect(Unit) {
            Vibrator.click(context)
        }
    }

    val shape = YosRoundedCornerShape(cornerRadius())

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = bottomSheetState,
        windowInsets = windowInsets,
        properties = properties,
        shape = RectangleShape,
        content = {
            Surface(modifier = Modifier
                .padding(vertical = 5.dp, horizontal = 5.dp)
                .graphicsLayer {
                    this.shape = if (cornerRadius() != 0.dp) {
                        shape
                    } else {
                        RectangleShape
                    }
                    shadowElevation = 10f
                    spotShadowColor = Color.Black
                }
                .graphicsLayer {
                    clip = true
                    this.shape = if (cornerRadius() != 0.dp) {
                         shape
                    } else {
                        RectangleShape
                    }
                }
                .clipToBounds()
                /*.navigationBarsPadding()*/,
                color = Color.White withNight Color.Black,
                contentColor = Color.Black withNight Color.White,
                content = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(26.dp)
                    ) {
                        content()
                    }
                }
            )
        },
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = MaterialTheme.colorScheme.onBackground.copy(0.13f)
    )
}

@Composable
private fun DialogTitle(icon: @Composable () -> Unit, title: String, subTitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        icon()
        Text(
            text = title,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            textAlign = TextAlign.Center
        )
        if (subTitle != null) {
            Text(
                text = subTitle, fontSize = 16.sp,
                modifier = Modifier
                    .alpha(0.5f).padding(horizontal = 14.dp),
                textAlign = TextAlign.Center,
                lineHeight = 20.5.sp
            )
        }
    }
}

@Composable
fun DialogContent(text: String, modifier: Modifier = Modifier) =
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .alpha(0.5f),
        lineHeight = 19.5.sp,
        fontSize = 14.5.sp
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDialog(
    icon: @Composable () -> Unit,
    title: String,
    subTitle: String? = null,
    content: (@Composable () -> Unit)?,
    positiveContent: String,
    negativeContent: String? = null,
    bottomSheetState: SheetState = rememberModalBottomSheetState(),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties(),
    cornerRadius: () -> Dp = { SettingsLibrary.ScreenCorner.toInt().dp },
    onPositive: () -> Unit,
    onNegative: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) =
    YosBottomSheetDialog(
        bottomSheetState = bottomSheetState,
        properties = properties,
        onDismissRequest = onDismissRequest,
        cornerRadius = cornerRadius
    ) {
        // println(cornerRadius())
        DialogTitle(icon = icon, title = title, subTitle = subTitle)
        Column(
            Modifier
                .fillMaxWidth()
        ) {
            if (content != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 19.5.dp)
                ) {
                    content()
                }
            }
            val height = 50.dp
            val shape = RoundedCornerShape(height.div(2))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = /*24.5.dp*/ 5.dp, bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .background(MaterialTheme.colorScheme.primary, shape = shape)
                        .clip(shape)
                        .clickable(onClick = onPositive),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = positiveContent,
                        color = Color.White,
                        fontSize = 16.5.sp
                    )
                }

                if (negativeContent != null && onNegative != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .clip(shape)
                            .background(
                                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                            )
                            .clickable(onClick = onNegative),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = negativeContent,
                            fontSize = 16.5.sp
                        )
                    }
                }
            }
        }
    }

/**
 * One row in an [ActionSheet]. Designed for menu-style sheets (e.g. the Now Playing
 * overflow menu).
 *
 * @param iconRes drawable resource for the left icon (24dp).
 * @param label primary label text.
 * @param subtitle optional secondary text shown below the label.
 * @param tint optional override for icon + label color. Used to highlight the
 *   "currently active" row (e.g. Sleep Timer when a timer is running).
 * @param enabled if false the row is dimmed and not clickable.
 * @param showChevron whether a right-side chevron is shown (indicates a sub-sheet).
 * @param onClick invoked on tap. Haptic feedback is fired automatically.
 */
@Stable
data class ActionItem(
    val iconRes: Int,
    val label: String,
    val subtitle: String? = null,
    val tint: Color? = null,
    val enabled: Boolean = true,
    val showChevron: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A menu-style bottom sheet. Sibling of [OptionDialog]; shares the same
 * [YosBottomSheetDialog] internals (corner radius from settings, edge-to-edge,
 * scrim, vibration on open).
 *
 * Use this for any "list of actions" sheet (e.g. NowPlaying overflow menu).
 *
 * @param isOpen controls visibility. Setting this to false dismisses the sheet.
 * @param header optional composable rendered above the action list (e.g. song
 *   preview). A 1px divider is drawn between the header and the first action.
 * @param items the action rows, in display order.
 * @param onDismiss invoked when the sheet is dismissed (scrim tap, back, drag).
 *   Defaults to setting [isOpen] to false.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    isOpen: MutableState<Boolean>,
    header: (@Composable () -> Unit)? = null,
    items: List<ActionItem>,
    bottomSheetState: SheetState = rememberModalBottomSheetState(),
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties(),
    cornerRadius: () -> Dp = { SettingsLibrary.ScreenCorner.toInt().dp },
    onDismiss: () -> Unit = { isOpen.value = false },
) {
    if (!isOpen.value) return

    YosBottomSheetDialog(
        bottomSheetState = bottomSheetState,
        properties = properties,
        onDismissRequest = onDismiss,
        cornerRadius = cornerRadius,
    ) {
        if (header != null) {
            header()
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .alpha(0.15f)
                    .height(0.5.dp)
                    .background(Color.Black withNight Color.White)
            )
        }
        items.forEachIndexed { index, item ->
            ActionSheetRow(item = item)
            if (index < items.lastIndex) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ActionSheetRow(item: ActionItem) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val rowAlpha = if (item.enabled) 1f else 0.4f
    val contentColor = item.tint ?: (Color.Black withNight Color.White)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                enabled = item.enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    Vibrator.click(context)
                    item.onClick()
                },
            )
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                fontSize = 16.5.sp,
                fontWeight = if (item.tint != null) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    fontSize = 13.sp,
                    color = contentColor,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.showChevron) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(20.dp)
                    .alpha(0.35f),
            )
        }
    }
}
