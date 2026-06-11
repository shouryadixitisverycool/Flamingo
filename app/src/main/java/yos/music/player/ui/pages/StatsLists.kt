package yos.music.player.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import yos.music.player.R
import yos.music.player.code.ListenStatsManager
import yos.music.player.data.libraries.ListenStatsLibrary
import yos.music.player.data.libraries.StatsAlbumEntry
import yos.music.player.data.libraries.StatsArtistEntry
import yos.music.player.data.libraries.StatsTrackEntry
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.Title

@Composable
private fun rememberStatsPeriodEvents() = run {
    val committedEvents = ListenStatsManager.statsEvents.value
    val liveEvents = ListenStatsManager.liveSessionEvents.value
    val selectedPeriod = selectedStatsPeriod()
    remember(committedEvents, liveEvents, selectedPeriod) {
        ListenStatsLibrary.filterEventsForPeriod(committedEvents + liveEvents, selectedPeriod)
    }
}

@Composable
fun StatsArtists(navController: NavController)
{
    val periodEvents = rememberStatsPeriodEvents()
    val artistEntries = remember(periodEvents) { ListenStatsLibrary.buildArtistEntries(periodEvents) }

    Title(
        title = stringResource(id = R.string.stats_artists_title),
        onBack = { navController.popBackStack() },
        content = {
            items(
                count = artistEntries.size,
                key = { listIndex -> artistEntries[listIndex].artistName }
            ) { listIndex ->
                val artistEntry = artistEntries[listIndex]
                StatsArtistListRow(artistEntry = artistEntry) {
                    openStatsArtist(navController, artistEntry)
                }
            }
        })
}

@Composable
fun StatsAlbums(navController: NavController)
{
    val periodEvents = rememberStatsPeriodEvents()
    val albumEntries = remember(periodEvents) { ListenStatsLibrary.buildAlbumEntries(periodEvents) }

    Title(
        title = stringResource(id = R.string.stats_albums_title),
        onBack = { navController.popBackStack() },
        content = {
            items(
                count = albumEntries.size,
                key = { listIndex -> albumEntries[listIndex].albumName }
            ) { listIndex ->
                val albumEntry = albumEntries[listIndex]
                StatsAlbumListRow(albumEntry = albumEntry) {
                    openStatsAlbum(navController, albumEntry)
                }
            }
        })
}

@Composable
fun StatsTracks(navController: NavController)
{
    val periodEvents = rememberStatsPeriodEvents()
    val trackEntries = remember(periodEvents) { ListenStatsLibrary.buildTrackEntries(periodEvents) }

    Title(
        title = stringResource(id = R.string.stats_tracks_title),
        onBack = { navController.popBackStack() },
        content = {
            items(
                count = trackEntries.size,
                key = { listIndex -> trackEntries[listIndex].trackKey }
            ) { listIndex ->
                val trackEntry = trackEntries[listIndex]
                StatsTrackListRow(trackEntry = trackEntry, allTrackEntries = trackEntries)
            }
        })
}

@Composable
private fun StatsArtistListRow(artistEntry: StatsArtistEntry, onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(64.dp)) {
            ShadowImageWithCache(
                dataLambda = { artistEntry.thumb },
                contentDescription = artistEntry.artistName,
                modifier = Modifier.alpha(if (artistEntry.inLibrary) 1f else 0.4f),
                cornerRadius = 8.dp,
                shadowAlpha = 0f,
                imageQuality = ImageQuality.LOW
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = artistEntry.artistName,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(Modifier.padding(top = 2.dp)) {
                StatsMinutesText(listenedMs = artistEntry.listenedMs, fontSize = 13)
            }
            if (!artistEntry.inLibrary)
            {
                Text(
                    text = stringResource(id = R.string.stats_missing_indicator),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatsAlbumListRow(albumEntry: StatsAlbumEntry, onClick: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(64.dp)) {
            ShadowImageWithCache(
                dataLambda = { albumEntry.thumb },
                contentDescription = albumEntry.albumName,
                modifier = Modifier.alpha(if (albumEntry.inLibrary) 1f else 0.4f),
                cornerRadius = 8.dp,
                shadowAlpha = 0f,
                imageQuality = ImageQuality.LOW
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = albumEntry.albumName,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                StatsMinutesText(listenedMs = albumEntry.listenedMs, fontSize = 13)
            }
            if (!albumEntry.inLibrary)
            {
                Text(
                    text = stringResource(id = R.string.stats_missing_indicator),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(0.8f)
                )
            }
        }
    }
}

@Composable
private fun StatsTrackListRow(trackEntry: StatsTrackEntry, allTrackEntries: List<StatsTrackEntry>)
{
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                playStatsTrack(trackEntry, allTrackEntries, scope, context)
            }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(64.dp)) {
            ShadowImageWithCache(
                dataLambda = { trackEntry.thumb },
                contentDescription = trackEntry.title,
                modifier = Modifier.alpha(if (trackEntry.libraryItem != null) 1f else 0.4f),
                cornerRadius = 8.dp,
                shadowAlpha = 0f,
                imageQuality = ImageQuality.LOW
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = trackEntry.title,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            Box(Modifier.padding(top = 2.dp)) {
                StatsMinutesText(listenedMs = trackEntry.listenedMs, fontSize = 13)
            }
            if (trackEntry.libraryItem == null)
            {
                Text(
                    text = stringResource(id = R.string.stats_missing_indicator),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(0.8f)
                )
            }
        }
    }
}
