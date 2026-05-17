package yos.music.player.data.libraries

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Stable
import com.funny.data_saver.core.mutableDataSaverListStateOf
import kotlinx.parcelize.Parcelize
import yos.music.player.data.PlayListSaver
import java.util.UUID

/**
 * Per PRD §5.5 the playlist model carries the metadata needed by the
 * Edit Playlist modal (description, custom cover) and the Library list
 * (pin state + manual order within the pinned block).
 *
 * Duplicates: [songDataList] already permits duplicate URIs at the data
 * layer; the picker used to enforce uniqueness at insert time but no
 * longer does (PRD FR-E-13). Edit-time row identity is by list index —
 * no schema change required.
 *
 * Backward compatibility: all new fields default to `null`/`false` so
 * Gson can deserialize JSON written by prior versions without migration.
 */
@Stable
@Parcelize
data class PlayList(
    val listID: String,
    val name: String,
    val songDataList: List<Uri>,
    val description: String? = null,
    /** Custom cover image URI (content:// or file://) chosen by the user.
     *  When null the playlist falls back to the auto-generated collage
     *  built from [songDataList] (PRD FR-E-05). */
    val coverUri: String? = null,
    /** Pin state — controls placement in the Library playlist list
     *  (PRD §5.4). */
    val isPinned: Boolean = false,
    /** Sort key within the pinned block. Lower = higher in the list.
     *  Only meaningful when [isPinned] is true. Gaps are tolerated;
     *  unpin clears the value (PRD FR-P-06/07). */
    val pinOrder: Int? = null,
) : Parcelable

@Stable
object PlayListLibrary {

    @Stable
    var playList by mutableDataSaverListStateOf(
        dataSaverInterface = PlayListSaver,
        key = "yos_play_list",
        initialValue = listOf<PlayList>()
    )
        private set

    /**
     * Replace [old] with [new] in-place. Preserves the list's overall
     * order (the original position of [old]) rather than appending the
     * replacement to the end, which is what the previous `+=` then `-=`
     * pattern did. Order preservation matters now that the playlist
     * list is rendered with pin/sort awareness — moving a freshly
     * edited playlist to the bottom of the source-of-truth list was
     * leaking through to the Library page.
     */
    private fun replace(old: PlayList, new: PlayList) {
        val idx = playList.indexOfFirst { it.listID == old.listID }
        if (idx < 0) {
            playList = playList + new
        } else {
            playList = playList.toMutableList().also { it[idx] = new }
        }
    }

    fun PlayList.addMusic(music: YosMediaItem) {
        // PRD FR-E-13: duplicates allowed at the data layer; callers
        // that want dedupe must check before invoking.
        val uri = music.uri ?: return
        replace(this, copy(songDataList = songDataList + uri))
    }

    /**
     * Remove the **first** occurrence of [music] by URI. Edit Playlist
     * does its own index-based bulk removal via [replaceSongs] — this
     * helper exists for single-song flows (e.g. NowPlaying overflow).
     */
    fun PlayList.removeMusic(music: YosMediaItem) {
        val idx = songDataList.indexOfFirst { it == music.uri }
        if (idx < 0) return
        replace(this, copy(songDataList = songDataList.toMutableList().also { it.removeAt(idx) }))
    }

    fun PlayList.rename(name: String) = replace(this, copy(name = name))

    /**
     * Atomic apply of edits made in the Edit Playlist modal (PRD
     * FR-E-11): name, description, cover, and the new song order
     * (with removals already filtered out) are committed in a single
     * list mutation.
     */
    fun PlayList.applyEdits(
        name: String,
        description: String?,
        coverUri: String?,
        songs: List<Uri>,
    ) = replace(
        this,
        copy(
            name = name,
            description = description,
            coverUri = coverUri,
            songDataList = songs,
        ),
    )

    /** PRD FR-P-06: pin places the playlist at the bottom of the
     *  pinned block (max pinOrder + 1). */
    fun PlayList.pin() {
        if (isPinned) return
        val nextOrder = (playList.mapNotNull { it.pinOrder }.maxOrNull() ?: -1) + 1
        replace(this, copy(isPinned = true, pinOrder = nextOrder))
    }

    /** PRD FR-P-07: unpin clears [pinOrder]; remaining pins keep theirs. */
    fun PlayList.unpin() {
        if (!isPinned) return
        replace(this, copy(isPinned = false, pinOrder = null))
    }

    /**
     * Bulk-set pin order for the long-press reorder gesture
     * (PRD FR-P-08). [ordering] is the desired top-to-bottom order of
     * pinned playlists' listIDs; pinOrder is reassigned to that index.
     * Non-pinned playlists are untouched.
     */
    fun reorderPins(ordering: List<String>) {
        val orderMap = ordering.withIndex().associate { (i, id) -> id to i }
        playList = playList.map { pl ->
            if (pl.isPinned && orderMap.containsKey(pl.listID)) {
                pl.copy(pinOrder = orderMap[pl.listID])
            } else pl
        }
    }

    fun create(name: String) {
        if (!playList.any { it.name == name }) {
            playList = playList + PlayList(UUID.randomUUID().toString(), name, listOf())
        }
    }

    fun remove(list: PlayList) {
        playList = playList.filterNot { it.listID == list.listID }
    }

    /** Restore a previously-removed playlist at its original position
     *  if known, otherwise append (PRD FR-M-10 undo support). */
    fun restore(list: PlayList, atIndex: Int) {
        if (playList.any { it.listID == list.listID }) return
        val safeIndex = atIndex.coerceIn(0, playList.size)
        playList = playList.toMutableList().also { it.add(safeIndex, list) }
    }
}

@Stable
object FavPlayListLibrary {
    @Stable
    var favPlayList by mutableDataSaverListStateOf(
        dataSaverInterface = PlayListSaver,
        key = "yos_fav_play_list",
        initialValue = listOf<YosMediaItem>()
    )
        private set

    fun addMusic(music: YosMediaItem) {
        if (!favPlayList.any { it.uri == music.uri }) {
            favPlayList += music
        }
    }

    fun removeMusic(music: YosMediaItem) {
        favPlayList -= music
    }

    fun isFavorite(music: YosMediaItem): Boolean = favPlayList.any { it.uri == music.uri }
}