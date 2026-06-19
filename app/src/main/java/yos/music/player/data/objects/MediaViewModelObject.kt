package yos.music.player.data.objects

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

@Stable
object MediaViewModelObject {
    val lrcEntries: MutableState<List<List<Pair<Float, String>>>> = mutableStateOf(listOf())
    val otherSideForLines = mutableStateListOf<Boolean>()
    val lyricLineEndTimes = mutableStateListOf<Float>()
    val lyricLineTransliterations = mutableStateListOf<String?>()
    val lyricLineSubtitles = mutableStateListOf<String?>()
    val isTtmlLyrics = mutableStateOf(false)

    // var mainLyricLines = mutableStateListOf<AnnotatedString>()

    val bitmap: MutableState<Uri?> = mutableStateOf(null)

    val isPlaying: MutableState<Boolean> = mutableStateOf(false)

    val bitrate = mutableIntStateOf(0)
    val samplingRate = mutableIntStateOf(0)
    val isDolby = mutableStateOf(false)

    fun updateLyricMetadata(
        endTimes: List<Float>,
        transliterations: List<String?>,
        subtitles: List<String?>,
        ttmlLyrics: Boolean
    ) {
        lyricLineEndTimes.clear()
        lyricLineEndTimes.addAll(endTimes)
        lyricLineTransliterations.clear()
        lyricLineTransliterations.addAll(transliterations)
        lyricLineSubtitles.clear()
        lyricLineSubtitles.addAll(subtitles)
        isTtmlLyrics.value = ttmlLyrics
    }

    // val songSort = mutableStateOf(SettingData.getString("yos_player_song_sort", "MUSIC_TITLE"))
    // val enableDescending = mutableStateOf(SettingData.get("yos_player_enable_descending", false))
}
