package yos.music.player.code

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yos.music.player.UriTypeAdapter
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem

@Stable
object ListenHistoryManager
{
    private const val MMKV_ID = "yos_listen_history"
    private const val HISTORY_KEY = "recently_played_list"
    private const val MAX_HISTORY_SIZE = 50

    val recentlyPlayedSongs = mutableStateOf<List<YosMediaItem>>(emptyList())

    private val gson by lazy {
        GsonBuilder().registerTypeAdapter(Uri::class.java, UriTypeAdapter()).create()
    }

    private val mmkv by lazy { MMKV.mmkvWithID(MMKV_ID) }

    suspend fun loadHistory()
    {
        val loadedHistory = withContext(Dispatchers.IO) {
            val json = mmkv.decodeString(HISTORY_KEY) ?: return@withContext null
            val historyType = object : TypeToken<List<YosMediaItem>>() {}.type
            runCatching {
                gson.fromJson<List<YosMediaItem>>(json, historyType)
            }.getOrNull()
        }
        if (loadedHistory != null) {recentlyPlayedSongs.value = loadedHistory}
    }

    fun recordPlay(song: YosMediaItem)
    {
        if (!SettingsLibrary.ListenHistory) {return}

        val currentHistory = recentlyPlayedSongs.value.toMutableList()
        currentHistory.removeAll { it.uri == song.uri }
        currentHistory.add(0, song)
        if (currentHistory.size > MAX_HISTORY_SIZE) {currentHistory.subList(MAX_HISTORY_SIZE, currentHistory.size).clear()}
        recentlyPlayedSongs.value = currentHistory
        persistHistory(currentHistory)
    }

    private fun persistHistory(historyList: List<YosMediaItem>)
    {
        val json = gson.toJson(historyList)
        mmkv.encode(HISTORY_KEY, json)
    }
}
