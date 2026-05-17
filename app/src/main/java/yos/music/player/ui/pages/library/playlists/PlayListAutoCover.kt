package yos.music.player.ui.pages.library.playlists

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import yos.music.player.R
import yos.music.player.data.libraries.YosMediaItem

/**
 * Auto-generated 2×2 collage of the playlist's first 4 unique album
 * arts (PRD FR-E-05). Fallbacks:
 *   3 unique → 2 on top, 1 spanning the bottom row
 *   2 unique → side-by-side split
 *   1 unique → single image filling the square
 *   0 unique → default star placeholder
 *
 * Shared between [yos.music.player.ui.pages.library.NormalMusic]'s
 * playlist detail header and the Edit Playlist cover carousel.
 */
@Composable
fun PlayListAutoCover(songs: List<YosMediaItem>) {
    val context = LocalContext.current
    val thumbs = remember(songs) {
        songs.mapNotNull { it.thumb }.distinct().take(4)
    }
    if (thumbs.isEmpty()) {
        Image(
            painter = painterResource(id = R.drawable.placeholder_playlist_default),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    when (thumbs.size) {
        1 -> AsyncImage(
            model = ImageRequest.Builder(context).data(thumbs[0]).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        2 -> Row(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbs[0]).build(),
                contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbs[1]).build(),
                contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
        3 -> Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[0]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[1]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbs[2]).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        else -> Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[0]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[1]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[2]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbs[3]).build(),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}
