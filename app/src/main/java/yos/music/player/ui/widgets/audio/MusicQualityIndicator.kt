package yos.music.player.ui.widgets.audio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.R
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.effects.overlayEffect

@Composable
fun MusicQualityIndicator() {
    val music = remember("MusicQualityIndicator_music") {
        musicPlaying
    }
    val show = remember("MusicQualityIndicator_show") {
        derivedStateOf {
            music.value != null
        }
    }

    if (show.value) {

        val musicBitrate = remember("MusicQualityIndicator_musicBitrate") {
            MediaViewModelObject.bitrate
        }
        val musicSamplingRate = remember("MusicQualityIndicator_musicSamplingRate") {
            MediaViewModelObject.samplingRate
        }

        val isLossless = remember("MusicQualityIndicator_isLossless") {
            derivedStateOf {
                musicBitrate.intValue >= 700 && musicSamplingRate.intValue >= 44100
            }
        }
        val isHiRes = remember("MusicQualityIndicator_isHiRes") {
            derivedStateOf {
                musicBitrate.intValue >= 2000 && musicSamplingRate.intValue >= 96000
            }
        }
        val isDolby = remember("MusicQualityIndicator_isDolby") {
            MediaViewModelObject.isDolby
        }

        val showDetail = remember("MusicQualityIndicator_showDetail") {
            derivedStateOf {
                isLossless.value || isHiRes.value || isDolby.value
            }
        }

        //println("质量接收 ${musicBitrate.intValue} ${musicSamplingRate.intValue}")
        //println("质量接收 无损 ${isLossless.value} ${isHiRes.value} ${isDolby.value}")

        AnimatedVisibility(
            showDetail.value,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            Crossfade(targetState = isDolby.value) {
                if (it) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nowplaying_dolby_atmos),
                            contentDescription = "dolby_atmos",
                            modifier = Modifier
                                .height(19.dp)
                                .overlayEffect()
                                .alpha(0.45f)
                        )
                    }
                } else if (isLossless.value || isHiRes.value) {
                    //println("重组 音质标签")
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .overlayEffect()
                                .background(
                                    color = Color(0x14FFFFFF),
                                    shape = YosRoundedCornerShape(5.dp)
                                )
                                .height(20.dp)
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_quality_lossless),
                                    contentDescription = "quality_lossless",
                                    modifier = Modifier
                                        .height(9.dp)
                                        .alpha(0.45f)
                                )
                                Spacer(modifier = Modifier.width(5.dp))

                                YosWrapper {

                                    val text =
                                        if (isHiRes.value) stringResource(id = R.string.music_quality_hi_res) else stringResource(id = R.string.music_quality_lossless)

                                    AnimatedContent(targetState = text) { thisText ->
                                        Text(
                                            text = thisText,
                                            modifier = Modifier.alpha(0.45f),
                                            fontSize = 11.sp,
                                            lineHeight = 11.sp
                                        )
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }
}