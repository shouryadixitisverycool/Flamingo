package yos.music.player.ui.pages.library.playlists

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Lightweight state holder for the long-press pin-reorder gesture
 * (PRD FR-P-08).
 *
 * Drives:
 *   - whether the list is in reorder mode (drag handles + dimmed
 *     unpinned rows),
 *   - the current candidate order of pinned playlist IDs while a
 *     drag is in flight,
 *   - vertical drag offset for the picked-up row so the UI can
 *     translate it.
 *
 * Reorder mode is exited via `commit()` (writes the new order back
 * to [yos.music.player.data.libraries.PlayListLibrary]) or
 * `cancel()` (drops the working order).
 *
 * Why a hand-rolled state machine and not a library: the Library
 * playlist list mixes pinned and unpinned rows in the same
 * LazyColumn. Off-the-shelf reorderable libraries (sh.calvin /
 * burnoutcrown) assume a homogeneous list — splitting the pinned
 * sub-section out into its own scrolling container would change the
 * Library page's visual rhythm, so this rolls the minimum needed.
 */
@Stable
class PinReorderState {
    private val activeState = mutableStateOf(false)
    private val draggingIdState = mutableStateOf<String?>(null)
    private val dragOffsetState = mutableStateOf(0f)
    /** Working order of pinned IDs, top to bottom. */
    val workingOrder: SnapshotStateList<String> = mutableStateListOf()

    val active: Boolean get() = activeState.value
    val draggingId: String? get() = draggingIdState.value
    val dragOffset: Float get() = dragOffsetState.value

    fun enter(initialOrder: List<String>) {
        workingOrder.clear()
        workingOrder.addAll(initialOrder)
        activeState.value = true
        draggingIdState.value = null
        dragOffsetState.value = 0f
    }

    fun startDrag(id: String) {
        draggingIdState.value = id
        dragOffsetState.value = 0f
    }

    fun updateDrag(delta: Float, rowHeightPx: Float) {
        val id = draggingIdState.value ?: return
        val idx = workingOrder.indexOf(id)
        if (idx < 0) return
        val newOffset = dragOffsetState.value + delta
        // Threshold: half a row's height triggers a swap with the
        // adjacent index.
        val threshold = rowHeightPx / 2f
        when {
            newOffset > threshold && idx < workingOrder.lastIndex -> {
                val moved = workingOrder.removeAt(idx)
                workingOrder.add(idx + 1, moved)
                dragOffsetState.value = newOffset - rowHeightPx
            }
            newOffset < -threshold && idx > 0 -> {
                val moved = workingOrder.removeAt(idx)
                workingOrder.add(idx - 1, moved)
                dragOffsetState.value = newOffset + rowHeightPx
            }
            else -> dragOffsetState.value = newOffset
        }
    }

    fun endDrag() {
        draggingIdState.value = null
        dragOffsetState.value = 0f
    }

    fun cancel() {
        activeState.value = false
        workingOrder.clear()
        endDrag()
    }

    /** Snapshot of the working order; caller should write back. */
    fun snapshot(): List<String> = workingOrder.toList()
}
