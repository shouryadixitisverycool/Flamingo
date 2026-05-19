package yos.music.player.ui.pages.settings.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import yos.music.player.R
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.pages.settings.ListHeader
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@Composable
fun ArtistSplitSetting(navController: NavController)
{
    SettingBackground {
        Title(
            title = stringResource(id = R.string.settings_library_artist_split_title),
            onBack = {
                navController.popBackStack()
            },
        ) {
            item("ArtistSplitField") {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RoundColumn {
                        BasicTextField(
                            value = SettingsLibrary.ArtistSplitSeparators,
                            onValueChange = {
                                SettingsLibrary.ArtistSplitSeparators = it
                            },
                            textStyle = TextStyle(
                                color = Color.Black withNight Color.White,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (SettingsLibrary.ArtistSplitSeparators.isEmpty()) {
                                        Text(
                                            text = stringResource(id = R.string.settings_library_artist_split_placeholder),
                                            fontSize = 16.sp,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.alpha(0.45f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    ListHeader(content = stringResource(id = R.string.settings_library_artist_split_hint))
                }
            }
        }
    }
}
