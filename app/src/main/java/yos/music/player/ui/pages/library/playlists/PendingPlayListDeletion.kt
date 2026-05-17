package yos.music.player.ui.pages.library.playlists

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import yos.music.player.data.libraries.PlayList

/**
 * Cross-screen handoff for the optimistic "delete with undo"
 * pattern (PRD FR-M-10). The detail page deletes the playlist
 * immediately (so the playlist disappears from the UI right away)
 * and stashes the original record here; the Library/Playlists page
 * picks it up on next composition, surfaces an undo snackbar, and
 * either restores the record on tap or drops the stash on dismiss.
 *
 * Kept as an in-memory singleton because (a) the undo window is
 * short-lived (5s) and crossing the process boundary defeats the
 * "feels instant" UX the PRD calls for, and (b) every other piece
 * of cross-screen UI state in this app uses the same pattern
 * (see [yos.music.player.data.objects.LibraryObject]).
 */
@Stable
object PendingPlayListDeletion {

    /** Snapshot of the deleted playlist + its original list index. */
    data class Pending(val playList: PlayList, val originalIndex: Int)

    private val state = mutableStateOf<Pending?>(null)

    val current: Pending? get() = state.value

    fun stash(playList: PlayList, originalIndex: Int) {
        state.value = Pending(playList, originalIndex)
    }

    fun consume(): Pending? {
        val taken = state.value
        state.value = null
        return taken
    }

    fun clear() {
        state.value = null
    }
}
