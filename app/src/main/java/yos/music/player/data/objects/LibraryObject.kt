package yos.music.player.data.objects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.YosMediaItem

@Stable
object LibraryObject {
    @Stable
    private val targetAlbumName = mutableStateOf("")
    fun setTargetAlbumName(name: String) {
        targetAlbumName.value = name
    }

    fun getTargetAlbumName(): String {
        return targetAlbumName.value
    }

    @Stable
    private val targetArtistName = mutableStateOf("")

    fun setTargetArtistName(name: String) {
        targetArtistName.value = name
    }

    fun getTargetArtistName(): String {
        return targetArtistName.value
    }

    @Stable
    private val targetArtistSongsSearchOnOpen = mutableStateOf(false)

    fun setArtistSongsSearchOnOpen(searchOnOpen: Boolean) {
        targetArtistSongsSearchOnOpen.value = searchOnOpen
    }

    fun consumeArtistSongsSearchOnOpen(): Boolean {
        val shouldSearchOnOpen = targetArtistSongsSearchOnOpen.value
        targetArtistSongsSearchOnOpen.value = false
        return shouldSearchOnOpen
    }

    @Stable
    private val targetList: MutableState<List<YosMediaItem>> = mutableStateOf(emptyList())
    @Stable
    private val targetListTitle = mutableStateOf("")

    /**
     * Identifier of the playlist whose contents are being shown in
     * [yos.music.player.ui.pages.library.NormalMusic], or null if the
     * page is showing some other list (Songs, Album, Artist, Folder).
     *
     * Set this alongside [setTargetListWithTitle] when navigating from
     * a playlist so the detail page can light up playlist-only actions
     * (Edit, Pin, Add-this-to-playlist, Play Next, Delete) without
     * needing to rediscover which playlist it's rendering. Per PRD §5.2.
     */
    @Stable
    val targetPlayListId: MutableState<String?> = mutableStateOf(null)

    fun setTargetListWithTitle(title: String, list: List<YosMediaItem>, playListId: String? = null) {
        targetListTitle.value = title
        targetList.value = list
        targetPlayListId.value = playListId
    }
    fun getTargetListWithTitle(): Pair<String, List<YosMediaItem>> {
        return Pair(targetListTitle.value, targetList.value)
    }
}
