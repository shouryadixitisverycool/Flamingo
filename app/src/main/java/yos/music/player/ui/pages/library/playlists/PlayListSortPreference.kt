package yos.music.player.ui.pages.library.playlists

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Sort options available on the playlist detail page. PRD FR-M-05.
 *
 * "Manual" reflects the playlist's saved drag-reorder (or insertion)
 * order — the canonical source of truth. Other entries are view-only
 * transforms applied client-side without persisting any new order.
 */
@Stable
enum class PlayListSort { Manual, Title, Artist, RecentlyAdded }

/**
 * In-memory sort preference shared between the overflow sort
 * sub-sheet and the playlist detail page's song list. Per PRD
 * FR-M-05 the sort is view-only; we deliberately do NOT persist it
 * to MMKV so each playlist visit starts with the playlist's saved
 * (manual) order.
 *
 * Singleton state: lifting to a ViewModel would require threading
 * an instance through the existing Compose nav graph, which is
 * inconsistent with how the rest of the app stores screen-scoped
 * state (see [yos.music.player.data.libraries.SettingsLibrary]).
 */
@Stable
object PlayListSortPreference {
    private val sortState = mutableStateOf(PlayListSort.Manual)
    private val descendingState = mutableStateOf(false)

    var sort: PlayListSort
        get() = sortState.value
        set(value) { sortState.value = value }

    var descending: Boolean
        get() = descendingState.value
        set(value) { descendingState.value = value }

    fun reset() {
        sortState.value = PlayListSort.Manual
        descendingState.value = false
    }
}
