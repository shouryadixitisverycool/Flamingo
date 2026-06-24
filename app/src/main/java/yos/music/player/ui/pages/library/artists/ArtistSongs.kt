package yos.music.player.ui.pages.library.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.ArtistLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.pages.library.MusicList
import yos.music.player.ui.pages.library.playlists.PlayListSearch
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.SearchTextField
import yos.music.player.ui.widgets.basic.Title

@Composable
fun ArtistSongs(navController: NavController)
{
    val artistName = rememberSaveable(key = "ArtistSongs_artistName") {
        mutableStateOf(LibraryObject.getTargetArtistName())
    }
    val artistSongs = ArtistLibrary.songsForArtist(artistName.value)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searchText = rememberSaveable(artistName.value) {
        mutableStateOf("")
    }
    val requestFocusSignal = rememberSaveable(artistName.value) {
        mutableIntStateOf(if (LibraryObject.consumeArtistSongsSearchOnOpen()) 1 else 0)
    }
    val displayedSongs = remember(artistName.value) {
        mutableStateOf(artistSongs)
    }
    val showEmptyState = remember(artistName.value, artistSongs) {
        derivedStateOf {
            artistName.value.isEmpty() || artistSongs.isEmpty()
        }
    }

    if (showEmptyState.value) {
        Title(
            title = stringResource(id = R.string.page_library_songs),
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("ArtistSongs_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.tip_no_song),
                        fontSize = 18.sp,
                        modifier = Modifier.alpha(0.6f),
                    )
                }
            }
        }
        return
    }

    LaunchedEffect(artistSongs, searchText.value) {
        if (searchText.value.isNotBlank()) {
            delay(150)
        }

        withContext(Dispatchers.Default) {
            displayedSongs.value = if (searchText.value.isBlank()) {
                artistSongs
            } else {
                PlayListSearch.matchAndRank(artistSongs, searchText.value)
            }
        }
    }

    Title(
        title = artistName.value,
        subTitle = stringResource(id = R.string.page_library_songs),
        onBack = {
            navController.popBackStack()
        },
        listState = listState,
    ) {
        item("ArtistSongs_search") {
            val keyboardController = LocalSoftwareKeyboardController.current

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchTextField(
                    text = searchText.value,
                    placeholder = stringResource(id = R.string.page_library_search_songs),
                    onValueChange = {
                        searchText.value = it
                    },
                    modifier = Modifier.weight(1f),
                    onSearch = {
                        if (searchText.value.isNotEmpty()) {
                            keyboardController?.hide()
                        }
                    },
                    requestFocusSignal = requestFocusSignal.intValue,
                    onClear = {
                        searchText.value = ""
                    },
                )
            }
        }

        if (displayedSongs.value.isEmpty()) {
            item("ArtistSongs_noResults") {
                Text(
                    text = stringResource(id = R.string.tip_no_song),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .alpha(0.55f),
                )
            }
        } else {
            itemsIndexed(
                displayedSongs.value,
                key = { _, music -> music.uri ?: music.mediaId ?: music.title ?: music.hashCode() },
                contentType = { _, _ -> "ArtistSongs_song" },
            ) { index, music ->
                ArtistSongItem(
                    music = music,
                    navController = navController,
                    onPlay = {
                        scope.launch(Dispatchers.IO) {
                            MediaController.prepare(music, displayedSongs.value)
                        }
                    },
                )

                if (index < displayedSongs.value.lastIndex) {
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 88.dp)
                            .alpha(0.15f)
                            .height(0.5.dp)
                            .background(Color.Black withNight Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSongItem(
    music: YosMediaItem,
    navController: NavController,
    onPlay: () -> Unit,
)
{
    MusicList(
        music = music,
        onQueueSwipe = {
            MediaController.addToQueue(music)
        },
        navController = navController,
    ) {
        onPlay()
    }
}
