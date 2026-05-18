package yos.music.player.ui.pages.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.artistsName
import yos.music.player.data.libraries.defaultArtistsName
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.ShadowImageWithCache

@Composable
fun /*LazyItemScope.*/MusicList(
    music: YosMediaItem,
    modifier: Modifier = Modifier,
    titleText: String = music.title ?: defaultTitle,
    subtitleText: String? = music.artistsName ?: defaultArtistsName,
    showArtwork: Boolean = true,
    artworkSize: Dp = 52.dp,
    leadingWidth: Dp = 24.dp,
    horizontalPadding: Dp = 22.dp,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable (RowScope.() -> Unit))? = null,
    itemClick: () -> Unit,
) {
    /*rememberSaveable(stateSaver = object : Saver<String?, Any> {
    override fun restore(value: Any): String? {
        return value as String?
    }

    override fun SaverScope.save(value: String?): Any {
        return value ?: ""
    }
}) {
    mutableStateOf(null)
}

LaunchedEffect(Unit) {
    val path = context.filesDir.absolutePath + "/${music().ID}.png"
    thumb.value = path
}*/

    /*val background = Color(Color.Transparent.toArgb())
    val originalColor = rememberSaveable(music()) {
        mutableStateOf(0)
    }
    val duration = rememberSaveable(music()) {
        mutableStateOf("00:00")
    }
    if (duration.value == "00:00") {
        YosWrapper {
            LaunchedEffect(Unit) {
                val time = MusicScanner(context).timeConversion(music().duration)
                duration.value = time.min + ":" + time.sec
            }
        }
    }
    if (originalColor.value == 0) {
        if (thumb != null && music() == musicPlaying.value) {
            val thumb = context.filesDir.absolutePath + "/${music().ID}.jpg"
            val bitmap = if (File(thumb).exists()) BitmapFactory.decodeFile(thumb) else null
            if (bitmap != null) {
                val builder = Palette.from(bitmap)
                val palette = builder.generate()
                originalColor.value =
                    Color(palette.getLightVibrantColor(background.toArgb())).toArgb()
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getVibrantColor(background.toArgb())).toArgb()
                }
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getLightMutedColor(background.toArgb())).toArgb()
                }
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getMutedColor(background.toArgb())).toArgb()
                }
                if (originalColor.value == background.toArgb()) {
                    originalColor.value =
                        Color(palette.getDominantColor(background.toArgb())).toArgb()
                }
            }
        }
    }
    val color by animateColorAsState(
        targetValue = if ((music() == musicPlaying.value)) Color(originalColor.value) else Color(
            MaterialTheme.colorScheme.background.toArgb()
        )
    )
    val alpha by animateFloatAsState(if (music() == musicPlaying.value) 0.3F else 1F)*/
    Row(
        modifier = modifier
            /*.animateItem(fadeInSpec = null, fadeOutSpec = null)*/
            .heightIn(min = 64.dp)
            .fillMaxWidth()
            .clickable {
                itemClick()
            }
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        println("重组：歌曲列表 ${music.title}")

        when {
            showArtwork -> {
                ShadowImageWithCache(
                    dataLambda = { music.thumb },
                    contentDescription = null,
                    modifier = Modifier.size(artworkSize),
                    cornerRadius = 3.5.dp,
                    shadowAlpha = 0f,
                    imageQuality = ImageQuality.LOW
                )
            }

            leadingContent != null -> {
                Box(
                    modifier = Modifier.size(width = leadingWidth, height = artworkSize),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingContent()
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (showArtwork || leadingContent != null) 16.dp else 0.dp)
                .padding(end = if (trailingContent != null) 12.dp else 0.dp)
        ) {
            Text(
                text = titleText,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )

            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    modifier = Modifier.alpha(0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                )
            }
        }

        if (trailingContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailingContent,
            )
        }
    }
}
