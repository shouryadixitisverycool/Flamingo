package yos.music.player.code

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.data.libraries.SettingsLibrary

/**
 * Sleep timer for the Now Playing overflow menu. Singleton; in-memory only
 * (PRD §5.6.1 FR-ST-4 — no MMKV persistence, no AlarmManager). Process death
 * cancels the timer.
 *
 * Three timer modes (PRD §5.6.1 FR-ST-2):
 * - [SleepTimerOption.Duration] — fires after a wall-clock delay
 * - [SleepTimerOption.EndOfTrack] — fires on the next `onMediaItemTransition`
 * - [SleepTimerOption.EndOfQueue] — fires on the next `onMediaItemTransition`
 *   after which the player no longer has a successor item.
 *
 * The playback service calls [onMediaItemTransition] from its
 * `Player.Listener` (MediaController.kt). Compose UI reads [state] and
 * [remainingMs] directly — both are `MutableState`-backed.
 */
@Stable
object SleepTimer {

    /** Current state — `Inactive` or `Active`. Compose-observable. */
    val state = mutableStateOf<SleepTimerState>(SleepTimerState.Inactive)

    /**
     * Live remaining time in ms for [SleepTimerOption.Duration] timers.
     * Updated every 1s while the timer is active. Returns 0 when no timer
     * is running. UI should read this directly when displaying countdown.
     */
    val remainingMs = mutableLongStateOf(0L)

    // Internal scope owns the ticker coroutine. SupervisorJob so a thrown
    // child job doesn't take down the singleton.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    /**
     * Start (or replace) the sleep timer.
     *
     * Cancels any existing timer first (PRD NFR-8 — no leaked tickers).
     *
     * @param option the timer mode.
     */
    fun start(option: SleepTimerOption) {
        cancel()
        val now = SystemClock.elapsedRealtime()
        val expiresAt = when (option) {
            is SleepTimerOption.Duration -> now + option.durationMs
            SleepTimerOption.EndOfTrack -> null
            SleepTimerOption.EndOfQueue -> null
        }
        state.value = SleepTimerState.Active(option, now, expiresAt)
        if (option is SleepTimerOption.Duration) {
            remainingMs.longValue = option.durationMs
            tickerJob = scope.launch { runDurationTimer(option, now) }
        } else {
            // Track-relative timers don't tick; remainingMs is meaningless.
            remainingMs.longValue = 0L
        }
    }

    /** Cancel the active timer (if any). Safe to call when inactive. */
    fun cancel() {
        tickerJob?.cancel()
        tickerJob = null
        state.value = SleepTimerState.Inactive
        remainingMs.longValue = 0L
    }

    /**
     * Hook called from the playback service's `Player.Listener` on every
     * media item transition.
     *
     * @param hasNext true iff the player still has a queued item after the
     *   current one (i.e. the album/playlist hasn't finished).
     */
    fun onMediaItemTransition(hasNext: Boolean) {
        when (val current = state.value) {
            is SleepTimerState.Active -> when (current.option) {
                SleepTimerOption.EndOfTrack -> fireAndCleanUp()
                SleepTimerOption.EndOfQueue -> if (!hasNext) fireAndCleanUp()
                is SleepTimerOption.Duration -> Unit /* duration timers are ticker-driven */
            }
            SleepTimerState.Inactive -> Unit
        }
    }

    private suspend fun runDurationTimer(option: SleepTimerOption.Duration, startedAt: Long) {
        // Tick once per second; recompute remaining off the wall clock so
        // we drift gracefully if the dispatcher is delayed (NFR-2: at most
        // one recomposition per second).
        while (scope.isActive) {
            val now = SystemClock.elapsedRealtime()
            val remaining = (startedAt + option.durationMs) - now
            if (remaining <= 0L) {
                remainingMs.longValue = 0L
                fireAndCleanUp()
                return
            }
            remainingMs.longValue = remaining
            delay(1_000L)
        }
    }

    /**
     * Common expiry path. Fade-pauses playback using the user's configured
     * fade duration, then clears state.
     *
     * NFR-9: tolerates a null [MediaController.mediaControl] (e.g. service
     * disconnected) — no crash, no toast.
     */
    private fun fireAndCleanUp() {
        runCatching {
            MediaController.mediaControl?.fadePause(
                SettingsLibrary.SleepTimerFadeDurationMs,
            )
        }
        state.value = SleepTimerState.Inactive
        remainingMs.longValue = 0L
        tickerJob = null
    }
}

/** Discrete states of the sleep timer. Compose-stable. */
@Stable
sealed class SleepTimerState {
    @Stable
    data object Inactive : SleepTimerState()

    /**
     * @param option the timer mode that's running.
     * @param startedAtElapsedMs `SystemClock.elapsedRealtime()` value at start.
     * @param expiresAtElapsedMs absolute expiry time for [SleepTimerOption.Duration]
     *   timers; `null` for track-relative timers.
     */
    @Stable
    data class Active(
        val option: SleepTimerOption,
        val startedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long?,
    ) : SleepTimerState()
}

/** Sleep timer mode (PRD §5.6.1 FR-ST-2). */
@Stable
sealed class SleepTimerOption {
    @Stable
    data class Duration(val durationMs: Long) : SleepTimerOption()

    @Stable
    data object EndOfTrack : SleepTimerOption()

    @Stable
    data object EndOfQueue : SleepTimerOption()
}
