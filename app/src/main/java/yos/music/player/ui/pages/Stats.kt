package yos.music.player.ui.pages

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import yos.music.player.R
import yos.music.player.code.ListenStatsManager
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.ListenStatsLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.StatsAlbumEntry
import yos.music.player.data.libraries.StatsArtistEntry
import yos.music.player.data.libraries.StatsPeriod
import yos.music.player.data.libraries.StatsTrackEntry
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.RollingNumberText
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.widgets.basic.YosWrapper

private const val STATS_ROW_MAX_ITEMS = 10

fun selectedStatsPeriod(): StatsPeriod
{
    val storedOrdinal = SettingsLibrary.StatsSelectedPeriod
    return StatsPeriod.entries.getOrElse(storedOrdinal) { StatsPeriod.Today }
}

@Composable
fun Stats(navController: NavController) =
    Title(
        title = stringResource(id = R.string.page_stats_title),
        content = {
            item("StatsContent") {
                StatsContent(navController)
            }
        })

@Composable
private fun StatsContent(navController: NavController)
{
    YosWrapper {
        val selectedPeriod = selectedStatsPeriod()

        val committedEvents = ListenStatsManager.statsEvents.value
        val liveEvents = ListenStatsManager.liveSessionEvents.value
        val allEvents = remember(committedEvents, liveEvents) { committedEvents + liveEvents }

        val periodEvents = remember(allEvents, selectedPeriod) {
            ListenStatsLibrary.filterEventsForPeriod(allEvents, selectedPeriod)
        }

        val summary = remember(periodEvents) { ListenStatsLibrary.buildSummary(periodEvents) }
        val artistEntries = remember(periodEvents) { ListenStatsLibrary.buildArtistEntries(periodEvents) }
        val albumEntries = remember(periodEvents) { ListenStatsLibrary.buildAlbumEntries(periodEvents) }
        val trackEntries = remember(periodEvents) { ListenStatsLibrary.buildTrackEntries(periodEvents) }

        Column(Modifier.fillMaxWidth()) {
            StatsPeriodPills(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { SettingsLibrary.StatsSelectedPeriod = it.ordinal }
            )

            StatsHeadline(
                totalMinutes = summary.totalListenedMs / 60000L,
                playCount = summary.playCount,
                uniqueAlbumCount = summary.uniqueAlbumCount,
                selectedPeriod = selectedPeriod,
                hasData = periodEvents.isNotEmpty()
            )

            if (artistEntries.isNotEmpty())
            {
                StatsSectionHeader(title = stringResource(id = R.string.stats_artists_title)) {
                    navController.toUI(UI.StatsArtists)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    items(
                        count = artistEntries.size.coerceAtMost(STATS_ROW_MAX_ITEMS),
                        key = { rowIndex -> artistEntries[rowIndex].artistName }
                    ) { rowIndex ->
                        val artistEntry = artistEntries[rowIndex]
                        StatsArtistCard(artistEntry = artistEntry) {
                            openStatsArtist(navController, artistEntry)
                        }
                    }
                }
            }

            if (albumEntries.isNotEmpty())
            {
                StatsSectionHeader(title = stringResource(id = R.string.stats_albums_title)) {
                    navController.toUI(UI.StatsAlbums)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    items(
                        count = albumEntries.size.coerceAtMost(STATS_ROW_MAX_ITEMS),
                        key = { rowIndex -> albumEntries[rowIndex].albumName }
                    ) { rowIndex ->
                        val albumEntry = albumEntries[rowIndex]
                        StatsAlbumCard(albumEntry = albumEntry) {
                            openStatsAlbum(navController, albumEntry)
                        }
                    }
                }
            }

            if (trackEntries.isNotEmpty())
            {
                StatsSectionHeader(title = stringResource(id = R.string.stats_tracks_title)) {
                    navController.toUI(UI.StatsTracks)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    items(
                        count = trackEntries.size.coerceAtMost(STATS_ROW_MAX_ITEMS),
                        key = { rowIndex -> trackEntries[rowIndex].trackKey }
                    ) { rowIndex ->
                        val trackEntry = trackEntries[rowIndex]
                        StatsTrackCard(trackEntry = trackEntry, allTrackEntries = trackEntries)
                    }
                }
            }
        }
    }
}

internal fun openStatsArtist(navController: NavController, artistEntry: StatsArtistEntry)
{
    if (!artistEntry.inLibrary) {return}
    LibraryObject.setTargetArtistName(artistEntry.artistName)
    LibraryObject.setArtistSongsSearchOnOpen(false)
    navController.toUI(UI.ArtistInfo)
}

internal fun openStatsAlbum(navController: NavController, albumEntry: StatsAlbumEntry)
{
    if (!albumEntry.inLibrary) {return}
    LibraryObject.setTargetAlbumName(albumEntry.albumName)
    navController.toUI(UI.AlbumInfo)
}

@Composable
private fun StatsPeriodPills(
    selectedPeriod: StatsPeriod,
    onPeriodSelected: (StatsPeriod) -> Unit
)
{
    val pillLabels = listOf(
        StatsPeriod.Today to stringResource(id = R.string.stats_period_today),
        StatsPeriod.ThisWeek to stringResource(id = R.string.stats_period_this_week),
        StatsPeriod.ThisMonth to stringResource(id = R.string.stats_period_this_month),
        StatsPeriod.ThisYear to stringResource(id = R.string.stats_period_this_year),
        StatsPeriod.AllTime to stringResource(id = R.string.stats_period_all_time)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        pillLabels.forEach { (period, pillLabel) ->
            val isSelected = period == selectedPeriod
            val pillShape = YosRoundedCornerShape(50.dp)
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .then(
                        if (isSelected)
                        {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, pillShape)
                        }
                        else
                        {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f), pillShape)
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPeriodSelected(period) }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = pillLabel,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun StatsHeadline(
    totalMinutes: Long,
    playCount: Int,
    uniqueAlbumCount: Int,
    selectedPeriod: StatsPeriod,
    hasData: Boolean
)
{
    val minutesLabel = stringResource(
        id = when (selectedPeriod)
        {
            StatsPeriod.Today -> R.string.stats_minutes_today
            StatsPeriod.ThisWeek -> R.string.stats_minutes_this_week
            StatsPeriod.ThisMonth -> R.string.stats_minutes_this_month
            StatsPeriod.ThisYear -> R.string.stats_minutes_this_year
            StatsPeriod.AllTime -> R.string.stats_minutes_all_time
        }
    )

    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumberText(
                targetValue = totalMinutes,
                style = TextStyle(fontSize = 58.sp, fontWeight = FontWeight.Bold, lineHeight = 62.sp)
            )
            Text(
                text = minutesLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 10.dp, bottom = 9.dp)
                    .alpha(0.8f)
            )
        }

        if (hasData)
        {
            Text(
                text = stringResource(
                    id = R.string.stats_across_summary,
                    String.format("%,d", playCount),
                    String.format("%,d", uniqueAlbumCount)
                ),
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .alpha(0.6f)
            )
        }
        else
        {
            Text(
                text = stringResource(id = R.string.stats_empty_hint),
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .alpha(0.6f)
            )
        }
    }
}

@Composable
internal fun StatsSectionHeader(title: String, onMore: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMore
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_next),
                contentDescription = title,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(0.55f),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
internal fun StatsMinutesText(listenedMs: Long, fontSize: Int = 13)
{
    Text(
        text = stringResource(id = R.string.stats_minutes_label, String.format("%,d", listenedMs / 60000L)),
        fontSize = fontSize.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.alpha(0.56f)
    )
}

@Composable
internal fun StatsArtistCard(artistEntry: StatsArtistEntry, onClick: () -> Unit)
{
    Column(
        modifier = Modifier
            .width(142.dp)
            .clickable(onClick = onClick)
    ) {
        ShadowImageWithCache(
            dataLambda = { artistEntry.thumb },
            contentDescription = artistEntry.artistName,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (artistEntry.inLibrary) 1f else 0.4f),
            cornerRadius = 8.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.HIGH
        )
        Text(
            text = artistEntry.artistName,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(0.94f)
        )
        Box(Modifier.padding(top = 3.dp)) {
            StatsMinutesText(listenedMs = artistEntry.listenedMs)
        }
    }
}

@Composable
internal fun StatsAlbumCard(albumEntry: StatsAlbumEntry, onClick: () -> Unit)
{
    Column(
        modifier = Modifier
            .width(142.dp)
            .clickable(onClick = onClick)
    ) {
        ShadowImageWithCache(
            dataLambda = { albumEntry.thumb },
            contentDescription = albumEntry.albumName,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (albumEntry.inLibrary) 1f else 0.4f),
            cornerRadius = 8.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.HIGH
        )
        Text(
            text = albumEntry.albumName,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(0.94f)
        )
        Text(
            text = albumEntry.artistName,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 2.dp)
                .alpha(0.56f)
        )
        Box(Modifier.padding(top = 2.dp)) {
            StatsMinutesText(listenedMs = albumEntry.listenedMs)
        }
    }
}

@Composable
internal fun StatsTrackCard(trackEntry: StatsTrackEntry, allTrackEntries: List<StatsTrackEntry>)
{
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .width(142.dp)
            .clickable {
                playStatsTrack(trackEntry, allTrackEntries, scope, context)
            }
    ) {
        ShadowImageWithCache(
            dataLambda = { trackEntry.thumb },
            contentDescription = trackEntry.title,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (trackEntry.libraryItem != null) 1f else 0.4f),
            cornerRadius = 8.dp,
            shadowAlpha = 0f,
            imageQuality = ImageQuality.HIGH
        )
        Text(
            text = trackEntry.title,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .alpha(0.94f)
        )
        Text(
            text = trackEntry.artistName,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 2.dp)
                .alpha(0.56f)
        )
        if (trackEntry.libraryItem != null)
        {
            Box(Modifier.padding(top = 2.dp)) {
                StatsMinutesText(listenedMs = trackEntry.listenedMs)
            }
        }
        else
        {
            Text(
                text = stringResource(id = R.string.stats_missing_indicator),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(0.8f)
            )
        }
    }
}

internal fun playStatsTrack(
    trackEntry: StatsTrackEntry,
    allTrackEntries: List<StatsTrackEntry>,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
)
{
    val libraryItem = trackEntry.libraryItem
    if (libraryItem == null)
    {
        Toast.makeText(context, R.string.stats_track_missing_toast, Toast.LENGTH_SHORT).show()
        return
    }

    val rankedPlayableQueue = allTrackEntries.mapNotNull { it.libraryItem }
    scope.launch(Dispatchers.IO) {
        MediaController.prepare(libraryItem, rankedPlayableQueue)
    }
}
