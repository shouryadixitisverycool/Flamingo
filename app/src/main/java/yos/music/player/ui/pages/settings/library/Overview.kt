package yos.music.player.ui.pages.settings.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.alexzhirkevich.cupertino.CupertinoSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yos.music.player.R
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.MusicLibrary.folders
import yos.music.player.data.libraries.MusicLibrary.hideFolders
import yos.music.player.data.objects.LibraryObject
import yos.music.player.ui.UI
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.toUI
import yos.music.player.ui.widgets.basic.Title
import yos.music.player.ui.widgets.basic.YosWrapper
import yos.music.player.ui.widgets.basic.yosRoundColumn

@Composable
fun LibraryOverview(navController: NavController) =
    SettingBackground {
    val folders = folders.sortedBy { it.name }
    Title(title = stringResource(id = R.string.settings_library_overview),
        onBack = {
            navController.popBackStack()
        },
        content = {
            yosRoundColumn {
                itemsIndexed(
                    folders,
                    key = { _, folder -> folder.path }
                ) { index, folder ->
                    FolderItem(folder = folder) {
                        val targetTitle = folder.name
                        val targetList = folder.songs
                        LibraryObject.setTargetListWithTitle(targetTitle, targetList)
                        navController.toUI(UI.NormalMusic)
                    }
                    key(index) {
                        val needDivider = index < folders.size - 1
                        if (needDivider) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 102.dp)
                                    .alpha(0.15f)
                                    .height(0.5.dp)
                                    .background(Color.Black withNight Color.White)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun LazyItemScope.FolderItem(folder: Folder, itemClick: () -> Unit) {
    Row(
        modifier = Modifier
            .animateItem(fadeInSpec = null, fadeOutSpec = null)
            .height(80.dp)
            .fillMaxWidth()
            .clickable {
                itemClick()
            }
            .padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = YosRoundedCornerShape(4.dp)
        val density = LocalDensity.current

        Image(painter = painterResource(id = R.drawable.placeholder_folder), contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        val outline = shape.createOutline(
                            Size(size.width, size.height),
                            LayoutDirection.Ltr,
                            density
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.1f),
                            style = Stroke(width = 8f)
                        )
                        drawOutline(
                            outline = outline,
                            color = Color.Gray.copy(alpha = 0.5f),
                            style = Stroke(width = 8f),
                            blendMode = BlendMode.Overlay
                        )
                    }
                })

        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f)) {
            Text(
                text = folder.name,
                modifier = Modifier.padding(bottom = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
        }

        YosWrapper {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            /*Switch(checkedLambda = { !hideFolders.any { it == folder.path } }, onValueChange = {
                scope.launch(Dispatchers.IO) {
                    Vibrator.click(context)
                    if (it) {
                        MusicLibrary.unHideFolder(folder)
                    } else {
                        MusicLibrary.hideFolder(folder)
                    }
                }
            }, switchHeight = 24.dp, switchWidth = 46.dp)*/

            CupertinoSwitch(checked = !hideFolders.any { it == folder.path }, onCheckedChange = {
                scope.launch(Dispatchers.IO) {
                    Vibrator.click(context)
                    if (it) {
                        MusicLibrary.unHideFolder(folder)
                    } else {
                        MusicLibrary.hideFolder(folder)
                    }
                }
            })
        }

        Icon(
            painter = painterResource(id = R.drawable.ic_action_next), contentDescription = null,
            modifier = Modifier
                .height(12.dp)
                .alpha(0.3f)
                .padding(horizontal = 8.dp), tint = MaterialTheme.colorScheme.onBackground
        )
    }
}
