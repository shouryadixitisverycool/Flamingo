package yos.music.player.code

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumMap
import yos.music.player.data.libraries.ListenStatsEvent
import yos.music.player.data.libraries.ListenStatsLibrary
import yos.music.player.data.libraries.StatsLibraryIndex
import yos.music.player.data.libraries.StatsPeriod
import yos.music.player.data.libraries.StatsPeriodSnapshot

@Stable
object ListenStatsManager
{
    private const val MMKV_ID = "yos_listen_stats"
    private const val EVENTS_KEY = "listen_stats_events"
    private const val PENDING_KEY = "listen_stats_pending_events"

    val statsEvents = mutableStateOf<List<ListenStatsEvent>>(emptyList())
    val liveSessionEvents = mutableStateOf<List<ListenStatsEvent>>(emptyList())
    val statsCacheVersion = mutableIntStateOf(0)

    private val gson by lazy { GsonBuilder().create() }

    private val mmkv by lazy { MMKV.mmkvWithID(MMKV_ID) }

    private val eventListType = object : TypeToken<List<ListenStatsEvent>>() {}.type

    private val cacheLock = Any()
    private val cachedPeriodSnapshots = EnumMap<StatsPeriod, StatsPeriodSnapshot>(StatsPeriod::class.java)
    private var cachedStatsEvents: List<ListenStatsEvent>? = null
    private var cachedLibraryIndex: StatsLibraryIndex? = null

    suspend fun loadEvents()
    {
        val (loadedEvents, pendingEvents) = withContext(Dispatchers.IO)
        {
            decodeEventList(EVENTS_KEY) to decodeEventList(PENDING_KEY)
        }

        if (pendingEvents.isNotEmpty())
        {
            val mergedEvents = loadedEvents + pendingEvents
            statsEvents.value = mergedEvents
            invalidateStatsCache()
            persistEventsAsync(mergedEvents)
            CoroutineScope(Dispatchers.IO).launch { mmkv.removeValueForKey(PENDING_KEY) }
        }
        else
        {
            statsEvents.value = loadedEvents
            invalidateStatsCache()
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
        invalidateStatsCache()
        persistEventsAsync(updatedEvents)
        CoroutineScope(Dispatchers.IO).launch { mmkv.removeValueForKey(PENDING_KEY) }
    }

    fun persistPendingEvents(pendingEvents: List<ListenStatsEvent>)
    {
        if (pendingEvents.isEmpty())
        {
            mmkv.removeValueForKey(PENDING_KEY)
            return
        }
        CoroutineScope(Dispatchers.IO).launch { mmkv.encode(PENDING_KEY, gson.toJson(pendingEvents)) }
    }

    fun clearPendingEvents()
    {
        mmkv.removeValueForKey(PENDING_KEY)
    }

    fun clearAllEvents()
    {
        statsEvents.value = emptyList()
        liveSessionEvents.value = emptyList()
        invalidateStatsCache()
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
        invalidateStatsCache()
        persistEvents(statsEvents.value)
        return importedEvents.size
    }

    fun warmStatsCache()
    {
        synchronized(cacheLock)
        {
            ensureStatsCacheSourceIsFresh()
            val libraryIndex = cachedLibraryIndex()
            for (period in StatsPeriod.entries)
            {
                if (!cachedPeriodSnapshots.containsKey(period))
                {
                    val periodEvents = ListenStatsLibrary.filterEventsForPeriod(statsEvents.value, period)
                    cachedPeriodSnapshots[period] = ListenStatsLibrary.buildSnapshot(periodEvents, libraryIndex)
                }
            }
        }
    }

    fun snapshotForPeriod(period: StatsPeriod, liveEvents: List<ListenStatsEvent>): StatsPeriodSnapshot
    {
        val cachedSnapshot = cachedSnapshotForPeriod(period)
        if (liveEvents.isEmpty()) {return cachedSnapshot}

        val livePeriodEvents = ListenStatsLibrary.filterEventsForPeriod(liveEvents, period)
        if (livePeriodEvents.isEmpty()) {return cachedSnapshot}

        val liveSnapshot = synchronized(cacheLock)
        {
            ListenStatsLibrary.buildSnapshot(livePeriodEvents, cachedLibraryIndex())
        }
        return ListenStatsLibrary.mergeSnapshots(cachedSnapshot, liveSnapshot)
    }

    private fun cachedSnapshotForPeriod(period: StatsPeriod): StatsPeriodSnapshot
    {
        synchronized(cacheLock)
        {
            ensureStatsCacheSourceIsFresh()
            cachedPeriodSnapshots[period]?.let { return it }

            val periodEvents = ListenStatsLibrary.filterEventsForPeriod(statsEvents.value, period)
            val snapshot = ListenStatsLibrary.buildSnapshot(periodEvents, cachedLibraryIndex())
            cachedPeriodSnapshots[period] = snapshot
            return snapshot
        }
    }

    private fun cachedLibraryIndex(): StatsLibraryIndex
    {
        cachedLibraryIndex?.let { return it }
        val libraryIndex = ListenStatsLibrary.buildLibraryIndex()
        cachedLibraryIndex = libraryIndex
        return libraryIndex
    }

    private fun ensureStatsCacheSourceIsFresh()
    {
        if (cachedStatsEvents === statsEvents.value) {return}
        cachedStatsEvents = statsEvents.value
        cachedPeriodSnapshots.clear()
    }

    private fun invalidateStatsCache()
    {
        synchronized(cacheLock)
        {
            cachedStatsEvents = null
            cachedLibraryIndex = null
            cachedPeriodSnapshots.clear()
            statsCacheVersion.intValue++
        }
    }

    private fun persistEvents(allEvents: List<ListenStatsEvent>)
    {
        mmkv.encode(EVENTS_KEY, gson.toJson(allEvents))
    }

    private fun persistEventsAsync(allEvents: List<ListenStatsEvent>)
    {
        CoroutineScope(Dispatchers.IO).launch { persistEvents(allEvents) }
    }
}
