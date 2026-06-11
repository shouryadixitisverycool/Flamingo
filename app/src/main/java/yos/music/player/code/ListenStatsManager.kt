package yos.music.player.code

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import yos.music.player.data.libraries.ListenStatsEvent

@Stable
object ListenStatsManager
{
    private const val MMKV_ID = "yos_listen_stats"
    private const val EVENTS_KEY = "listen_stats_events"
    private const val PENDING_KEY = "listen_stats_pending_events"

    val statsEvents = mutableStateOf<List<ListenStatsEvent>>(emptyList())
    val liveSessionEvents = mutableStateOf<List<ListenStatsEvent>>(emptyList())

    private val gson by lazy { GsonBuilder().create() }

    private val mmkv by lazy { MMKV.mmkvWithID(MMKV_ID) }

    private val eventListType = object : TypeToken<List<ListenStatsEvent>>() {}.type

    fun loadEvents()
    {
        val loadedEvents: List<ListenStatsEvent> = decodeEventList(EVENTS_KEY)
        val pendingEvents: List<ListenStatsEvent> = decodeEventList(PENDING_KEY)

        if (pendingEvents.isNotEmpty())
        {
            val mergedEvents = loadedEvents + pendingEvents
            statsEvents.value = mergedEvents
            persistEvents(mergedEvents)
            mmkv.removeValueForKey(PENDING_KEY)
        }
        else
        {
            statsEvents.value = loadedEvents
        }
    }

    private fun decodeEventList(storageKey: String): List<ListenStatsEvent>
    {
        val json = mmkv.decodeString(storageKey) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<ListenStatsEvent>>(json, eventListType)
        }.getOrNull() ?: emptyList()
    }

    fun commitEvents(newEvents: List<ListenStatsEvent>)
    {
        if (newEvents.isEmpty()) {return}

        val updatedEvents = statsEvents.value + newEvents
        statsEvents.value = updatedEvents
        liveSessionEvents.value = emptyList()
        persistEvents(updatedEvents)
        mmkv.removeValueForKey(PENDING_KEY)
    }

    fun persistPendingEvents(pendingEvents: List<ListenStatsEvent>)
    {
        if (pendingEvents.isEmpty())
        {
            mmkv.removeValueForKey(PENDING_KEY)
            return
        }
        mmkv.encode(PENDING_KEY, gson.toJson(pendingEvents))
    }

    fun clearPendingEvents()
    {
        mmkv.removeValueForKey(PENDING_KEY)
    }

    fun clearAllEvents()
    {
        statsEvents.value = emptyList()
        liveSessionEvents.value = emptyList()
        mmkv.removeValueForKey(EVENTS_KEY)
        mmkv.removeValueForKey(PENDING_KEY)
    }

    fun exportEventsAsJson(): String
    {
        return gson.toJson(statsEvents.value)
    }

    fun importEventsFromJson(json: String, replaceExisting: Boolean): Int
    {
        val importedEvents: List<ListenStatsEvent> = runCatching {
            gson.fromJson<List<ListenStatsEvent>>(json, eventListType)
        }.getOrNull() ?: return -1

        val resultingEvents: List<ListenStatsEvent>
        if (replaceExisting)
        {
            resultingEvents = importedEvents
        }
        else
        {
            val existingEventKeys = statsEvents.value.map { "${it.uri}|${it.timestampMs}|${it.dayStartMs}" }.toHashSet()
            val deduplicatedImports = importedEvents.filter { "${it.uri}|${it.timestampMs}|${it.dayStartMs}" !in existingEventKeys }
            resultingEvents = statsEvents.value + deduplicatedImports
        }

        statsEvents.value = resultingEvents.sortedBy { it.timestampMs }
        persistEvents(statsEvents.value)
        return importedEvents.size
    }

    private fun persistEvents(allEvents: List<ListenStatsEvent>)
    {
        mmkv.encode(EVENTS_KEY, gson.toJson(allEvents))
    }
}
