package yos.music.player.ui.pages.library.playlists

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import yos.music.player.data.libraries.PlayList

/**
 * Cross-screen handoff for the optimistic "delete with undo"
 * pattern (PRD FR-M-10). The detail page deletes the playlist
 * immediately (so the playlist disappears from the UI right away)
 * and stashes the original record here; an activity-level snackbar
 * host renders the undo toast and either restores the record on tap
 * or drops the stash on the 5s timer.
 *
 * Kept as an in-memory singleton because the undo window is
 * short-lived (5s) and crossing the process boundary defeats the
 * "feels instant" UX the PRD calls for. The 5s auto-dismiss timer
 * lives here (not in a composable's LaunchedEffect) so leaving the
 * page mid-window doesn't cancel the timer — the snackbar should
 * survive navigation right up until the user taps Undo or 5s elapse.
 */
@Stable
object PendingPlayListDeletion {

    /** Snapshot of the deleted playlist + its original list index. */
    data class Pending(val playList: PlayList, val originalIndex: Int)

    private val state = mutableStateOf<Pending?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoDismissJob: Job? = null

    val current: Pending? get() = state.value

    /**
     * Stash a freshly-deleted playlist + start the 5s auto-dismiss
     * timer. If a previous deletion is still pending, the new one
     * replaces it and the old undo window is dropped (the previous
     * playlist remains deleted — matches Apple Music's behaviour
     * where rapid-fire deletes only let you undo the most recent).
     */
    fun stash(playList: PlayList, originalIndex: Int) {
        autoDismissJob?.cancel()
        state.value = Pending(playList, originalIndex)
        autoDismissJob = scope.launch {
            delay(5000)
            // Only clear if the stash hasn't been consumed (Undo)
            // or replaced (another delete) in the meantime.
            if (state.value?.playList?.listID == playList.listID) {
                state.value = null
            }
        }
    }

    fun consume(): Pending? {
        autoDismissJob?.cancel()
        autoDismissJob = null
        val taken = state.value
        state.value = null
        return taken
    }

    fun clear() {
        autoDismissJob?.cancel()
        autoDismissJob = null
        state.value = null
    }
}
