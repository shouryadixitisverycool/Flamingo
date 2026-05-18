package yos.music.player.ui.pages.library.albums

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.insets.statusBarsHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.code.MediaController
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsList
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtists
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImage
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.effects.ShadowType

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumInfo(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) =
    Box(
        Modifier
            .fillMaxSize()
        /*.statusBarsPadding()*/
    ) {
        val albumName = rememberSaveable(key = "AlbumInfo_albumName") {
            mutableStateOf(LibraryObject.getTargetAlbumName())
        }

        val hideMusic = remember("AlbumInfo_showMusic") {
            derivedStateOf {
                albumName.value.isEmpty()
            }
        }
        if (hideMusic.value) {
            val message = stringResource(id = R.string.tip_no_album_info)
            Title(
                title = stringResource(id = R.string.page_library_album_info_title), onBack = {
                    navController.popBackStack()
                }
            ) {
                item("tip_no_song") {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(text = message, fontSize = 18.sp, modifier = Modifier.alpha(0.6f))
                    }
                }
            }
        } else {
            val songs = MusicLibrary.Album[albumName.value].sortedWith(compareBy<YosMediaItem>{ it.trackNumber?:0 }.thenBy { it.title })

            /*Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            navController.popBackStack()
                        },
                    tint = MaterialTheme.colorScheme.primary
                )
            }*/

            val state = rememberLazyListState()

            val mainArtists = rememberSaveable(key = "AlbumInfo_mainArtists") {
                mutableStateOf(songs.first().artistsList ?: defaultArtists)
            }
            val mainArtistsName = rememberSaveable(key = "AlbumInfo_mainArtistsName") {
                mutableStateOf(songs.first().artistsName ?: defaultArtistsName)
            }

            val (songCount, totalMinutes) = rememberSaveable(songs) {
                val totalDuration = songs.sumOf { it.duration }
                val totalMinutes = totalDuration / 60000
                val songCount = songs.size
                songCount to totalMinutes
            }

            val scope = rememberCoroutineScope()

            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical(),
                flingBehavior = rememberOverscrollFlingBehavior { state },
                contentPadding = PaddingValues(bottom = 18.dp, top = 54.dp)
            ) {
                item("AlbumInfo") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 9.5.dp)
                            .padding(horizontal = 18.dp)
                            .statusBarsPadding(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        /*with(sharedTransitionScope) {*/
                        ShadowImage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 54.5.dp)
                            /*.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "image-$albumName"),
                                animatedVisibilityScope = animatedContentScope
                            )*/,
                            dataLambda = { songs.getOrNull(0)?.thumb },
                            contentDescription = null,
                            cornerRadius = 7.dp,
                            imageQuality = ImageQuality.RAW,
                            shadowType = ShadowType.Medium
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = albumName.value,
                            fontSize = 20.sp,
                            /*modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = "album_name-$albumName"),
                                animatedVisibilityScope = animatedContentScope
                            ),*/
                            textAlign = TextAlign.Center,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Medium
                        )
                        /*}*/

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = mainArtistsName.value,
                            fontSize = 17.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 23.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "ALBUM",
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .alpha(0.4f)
                                .padding(top = 2.dp)
                        )

                        YosWrapper {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 15.dp)
                            ) {

                                NormalButton(
                                    icon = painterResource(id = R.drawable.button_icon_play),
                                    label = stringResource(id = R.string.normal_button_play),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            songs.first(),
                                            songs
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(15.dp))
                                NormalButton(
                                    icon = painterResource(id = R.drawable.button_icon_shuffle),
                                    label = stringResource(id = R.string.normal_button_shuffle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MediaController.mediaControl?.shuffleModeEnabled = true
                                    scope.launch(Dispatchers.IO) {
                                        MediaController.prepare(
                                            songs.random(),
                                            songs
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    AlbumDivider()
                }

                itemsIndexed(
                    songs,
                    key = { indexOfMusic, music -> "$indexOfMusic:${music.uri}" }/*,
                    contentType = { _, _ -> "AlbumInfo_item" }*/
                ) { index, music ->
                    key(music) {
                        AlbumSongsItem(
                            music = music,
                            mainArtists = mainArtists.value
                        ) {
                            scope.launch(Dispatchers.IO) {
                                MediaController.prepare(
                                    music,
                                    songs
                                )
                            }
                        }
                    }

                    key(index) {
                        val needDivider = index < songs.size - 1
                        if (needDivider) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 50.dp, end = 18.dp)
                                    .alpha(0.25f)
                                    .height(0.5.dp)
                                    .background(Color.Black withNight Color.White)
                            )
                        }
                    }
                }

                item {
                    AlbumDivider()
                }

                item("AlbumInfo_others") {
                    Text(
                        text = stringResource(
                            id = R.string.page_library_album_info_others,
                            songCount,
                            totalMinutes
                        ), fontSize = 15.sp, modifier = Modifier
                            .alpha(0.4f)
                            .padding(horizontal = 18.dp)
                            .padding(top = 18.dp)
                    )
                }

                item("navbar") {
                    Spacer(modifier = Modifier.navigationBarsHeight(134.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsHeight(54.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)
                ) {
                    Box(
                        Modifier.statusBarsHeight(48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = null,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 10.dp)
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        navController.popBackStack()
                                    }
                                ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

@Composable
fun NormalButton(icon: Painter, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .background(
                color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp
        )
    }
}

/**
 * 专辑页面的横向分割线
 */
@Composable
private fun AlbumDivider(modifier: Modifier = Modifier) =
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .alpha(0.2f)
            .height(0.5.dp)
            .background(Color.Black withNight Color.White)
    )

@Composable
private fun AlbumSongsItem(
    modifier: Modifier = Modifier,
    music: YosMediaItem,
    mainArtists: List<String>,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.TopCenter, modifier = Modifier
                .width(24.dp)
                /*.height(21.dp)*/
                .fillMaxHeight()
        ) {
            Text(
                text = "${music.trackNumber?:"-"}",
                fontSize = 16.sp,
                modifier = Modifier.alpha(0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                text = music.title ?: defaultTitle,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            YosWrapper {
                val needShowArtists = remember(music) {
                    derivedStateOf {
                        !mainArtists.containsAll(music.artistsList ?: defaultArtists)
                    }
                }
                if (needShowArtists.value) {
                    Text(
                        text = music.artistsName ?: defaultArtistsName,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                        modifier = Modifier.alpha(0.4f)
                    )
                }
            }
        }
    }
}