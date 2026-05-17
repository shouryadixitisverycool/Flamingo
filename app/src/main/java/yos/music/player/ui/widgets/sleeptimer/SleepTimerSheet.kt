package yos.music.player.ui.widgets.sleeptimer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import yos.music.player.R
import yos.music.player.code.SleepTimer
import yos.music.player.code.SleepTimerOption
import yos.music.player.code.SleepTimerState
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.YosBottomSheetDialog

/**
 * Sleep timer bottom sheet. Three internal screens:
 *
 * 1. **Preset list** (default) — radio-style preset choices, active status row
 *    on top if a timer is running, fade selector at the bottom.
 * 2. **Custom duration** — two hour/minute wheel pickers + Start.
 * 3. **Fade selector** — four fade duration options (Off / 5s / 10s / 30s).
 *
 * Per PRD §5.6.5 FR-ST-11 through FR-ST-13.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(isOpen: MutableState<Boolean>) {
    if (!isOpen.value) return

    YosBottomSheetDialog(onDismissRequest = { isOpen.value = false }) {
        SleepTimerContent(onDone = { isOpen.value = false })
    }
}

/**
 * Bare sleep-timer content — the body of [SleepTimerSheet] without its
 * surrounding [YosBottomSheetDialog]. Use this when you want to render the
 * sleep timer inside another bottom sheet (e.g. the NowPlaying overflow
 * menu swaps its content to this composable when the user picks
 * "Sleep Timer", so the sub-screen appears without a close + reopen
 * animation).
 *
 * @param onDone called when the timer flow should terminate — either after
 *   the user starts/cancels a timer, or when the host should dismiss its
 *   containing sheet.
 */
@Composable
fun SleepTimerContent(onDone: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.Presets) }

    when (screen) {
        Screen.Presets -> PresetScreen(
            onPicked = { onDone() },
            onOpenCustom = { screen = Screen.Custom },
            onOpenFade = { screen = Screen.Fade },
        )
        Screen.Custom -> CustomScreen(
            onCancel = { screen = Screen.Presets },
            onConfirm = { onDone() },
        )
        Screen.Fade -> FadeScreen(
            onBack = { screen = Screen.Presets },
        )
    }
}

private enum class Screen { Presets, Custom, Fade }

// ---------------------------------------------------------------------------
// Preset list screen
// ---------------------------------------------------------------------------

@Composable
private fun PresetScreen(
    onPicked: () -> Unit,
    onOpenCustom: () -> Unit,
    onOpenFade: () -> Unit,
) {
    val timerState by SleepTimer.state
    val activeOption = (timerState as? SleepTimerState.Active)?.option

    SheetTitle(text = stringResource(R.string.sleep_timer_title))

    // Active status row: only shown when a timer is running.
    if (timerState is SleepTimerState.Active) {
        ActiveStatusRow(onCancel = {
            SleepTimer.cancel()
            // Don't dismiss — let the user pick a new option if they want.
        })
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Preset options.
    PresetRow(
        label = stringResource(R.string.sleep_timer_end_of_track),
        selected = activeOption is SleepTimerOption.EndOfTrack,
        onClick = {
            SleepTimer.start(SleepTimerOption.EndOfTrack)
            onPicked()
        },
    )
    PresetRow(
        label = stringResource(R.string.sleep_timer_end_of_queue),
        selected = activeOption is SleepTimerOption.EndOfQueue,
        onClick = {
            SleepTimer.start(SleepTimerOption.EndOfQueue)
            onPicked()
        },
    )
    Spacer(modifier = Modifier.height(4.dp))
    Divider()
    Spacer(modifier = Modifier.height(4.dp))

    val durationPresetMinutes = remember { listOf(5, 15, 30, 60) }
    durationPresetMinutes.forEach { minutes ->
        val durationMs = minutes * 60_000L
        PresetRow(
            label = stringResource(R.string.sleep_timer_minutes, minutes),
            selected = activeOption is SleepTimerOption.Duration &&
                activeOption.durationMs == durationMs,
            onClick = {
                SleepTimer.start(SleepTimerOption.Duration(durationMs))
                onPicked()
            },
        )
    }

    PresetRow(
        label = stringResource(R.string.sleep_timer_custom),
        selected = false,
        showChevron = true,
        onClick = onOpenCustom,
    )

    Spacer(modifier = Modifier.height(8.dp))
    Divider()
    Spacer(modifier = Modifier.height(8.dp))

    FadeSelectorRow(onClick = onOpenFade)
}

@Composable
private fun ActiveStatusRow(onCancel: () -> Unit) {
    val context = LocalContext.current
    val timerState by SleepTimer.state
    val remaining by SleepTimer.remainingMs
    val active = timerState as? SleepTimerState.Active ?: return

    val label = when (active.option) {
        SleepTimerOption.EndOfTrack -> stringResource(R.string.sleep_timer_end_of_track)
        SleepTimerOption.EndOfQueue -> stringResource(R.string.sleep_timer_end_of_queue)
        is SleepTimerOption.Duration -> stringResource(R.string.sleep_timer_active_short)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_setting_moon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (active.option is SleepTimerOption.Duration) {
                Text(
                    text = formatRemaining(remaining),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alpha(0.85f),
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    Vibrator.click(context)
                    onCancel()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.sleep_timer_cancel),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PresetRow(
    label: String,
    selected: Boolean,
    showChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else (Color.Black withNight Color.White),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else if (showChevron) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(0.4f),
            )
        }
    }
}

@Composable
private fun FadeSelectorRow(onClick: () -> Unit) {
    val context = LocalContext.current
    // SettingsLibrary.SleepTimerFadeDurationMs is a delegated property backed
    // by `mutableDataSaverStateOf`, which is Compose-observable — reading it
    // here directly triggers recomposition when it changes.
    val fadeMs = SettingsLibrary.SleepTimerFadeDurationMs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Vibrator.click(context)
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sleep_timer_fade_label),
            fontSize = 14.5.sp,
            modifier = Modifier
                .weight(1f)
                .alpha(0.75f),
        )
        Text(
            text = fadeLabel(fadeMs),
            fontSize = 14.sp,
            modifier = Modifier
                .padding(end = 8.dp)
                .alpha(0.65f),
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_action_next),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .alpha(0.4f),
        )
    }
}

// ---------------------------------------------------------------------------
// Custom duration screen
// ---------------------------------------------------------------------------

@Composable
private fun CustomScreen(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(30) }

    SheetTitle(text = stringResource(R.string.sleep_timer_custom))

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn(
            label = stringResource(R.string.sleep_timer_custom_hours),
            range = 0..12,
            initial = hours,
            onValueChange = { hours = it },
        )
        Text(
            text = ":",
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 24.dp),
        )
        WheelColumn(
            label = stringResource(R.string.sleep_timer_custom_minutes),
            range = 0..59,
            initial = minutes,
            onValueChange = { minutes = it },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    val canStart = (hours > 0 || minutes > 0)
    val buttonHeight = 50.dp
    val buttonShape = RoundedCornerShape(buttonHeight.div(2))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .background(
                color = if (canStart) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                shape = buttonShape,
            )
            .clip(buttonShape)
            .clickable(enabled = canStart) {
                Vibrator.click(context)
                val total = hours * 3_600_000L + minutes * 60_000L
                SleepTimer.start(SleepTimerOption.Duration(total))
                onConfirm()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.sleep_timer_confirm),
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

/**
 * Simple LazyColumn-based wheel picker. The centered item is the "selected"
 * value. Three rows visible at a time (one above + selected + one below);
 * neighbors are dimmed.
 *
 * Hand-rolled per PRD §10 OQ-3 — adding a wheel-picker library would have
 * inflated the dep tree for a single use case. This is the "serviceable"
 * implementation noted in the PRD as a candidate for polish.
 */
@Composable
private fun WheelColumn(
    label: String,
    range: IntRange,
    initial: Int,
    onValueChange: (Int) -> Unit,
) {
    val items = remember(range) { range.toList() }
    val itemHeight = 40.dp
    val visibleCount = 3 // 1 above + centered + 1 below
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (initial - range.first).coerceAtLeast(0),
    )
    val flingBehavior = rememberSnapFlingBehavior(state)

    // Derived current value = whichever item is sitting at the center slot.
    val centered by remember {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            val itemPx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            val centerIndex = if (offset > itemPx / 2) first + 1 else first
            items.getOrNull(centerIndex) ?: range.first
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centered }.collectLatest { onValueChange(it) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .alpha(0.5f),
        )
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(itemHeight * visibleCount),
        ) {
            // Center selection band (subtle background).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .align(Alignment.Center)
                    .background(
                        color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                    ),
            )

            LazyColumn(
                state = state,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxWidth(),
                // Top/bottom padding so first and last items can sit at the
                // center band when fully scrolled to either extreme.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = itemHeight,
                ),
            ) {
                items(items) { value ->
                    val isCentered = value == centered
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value.toString().padStart(2, '0'),
                            fontSize = if (isCentered) 22.sp else 18.sp,
                            fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.alpha(if (isCentered) 1f else 0.35f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fade selector screen
// ---------------------------------------------------------------------------

@Composable
private fun FadeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var fadeMs by remember { mutableLongStateOf(SettingsLibrary.SleepTimerFadeDurationMs) }

    SheetTitle(text = stringResource(R.string.sleep_timer_fade_label))

    val options = remember {
        listOf(
            0L to R.string.sleep_timer_fade_off,
            5_000L to R.string.sleep_timer_fade_5s,
            10_000L to R.string.sleep_timer_fade_10s,
            30_000L to R.string.sleep_timer_fade_30s,
        )
    }
    options.forEach { (value, labelRes) ->
        PresetRow(
            label = stringResource(labelRes),
            selected = fadeMs == value,
            onClick = {
                fadeMs = value
                SettingsLibrary.SleepTimerFadeDurationMs = value
            },
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f))
            .clickable {
                Vibrator.click(context)
                onBack()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.playlist_picker_cancel),
            fontSize = 16.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp),
    )
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

private fun fadeLabel(ms: Long): String = when (ms) {
    0L -> "Off"
    5_000L -> "5s"
    10_000L -> "10s"
    30_000L -> "30s"
    else -> "${ms / 1000}s"
}

/** Format milliseconds as `H:MM:SS` (or `MM:SS` when under an hour). */
private fun formatRemaining(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
}
