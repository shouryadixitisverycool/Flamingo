package yos.music.player.ui.pages.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import com.google.accompanist.insets.navigationBarsHeight
import yos.music.player.R
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.SearchTextField

@Composable
fun MusicDetailPage(
    title: String,
    listState: LazyListState,
    searchText: String,
    searchPlaceholder: String,
    showSearch: Boolean,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    artwork: @Composable BoxScope.() -> Unit,
    headerContent: @Composable ColumnScope.() -> Unit,
    actionContent: @Composable () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val heroHeight = (configuration.screenHeightDp.dp * 0.52f).coerceIn(320.dp, 468.dp)
    val heroHeightPx = with(density) { heroHeight.toPx() }

    val collapseProgress by remember(listState, heroHeightPx) {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                heroHeightPx == 0f -> 0f
                else -> {
                    (listState.firstVisibleItemScrollOffset / (heroHeightPx * 0.62f)).coerceIn(0f, 1f)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { listState },
            contentPadding = PaddingValues(bottom = 0.dp),
        ) {
            item("MusicDetailHero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        artwork()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.22f),
                                        Color.Black.copy(alpha = 0.46f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                        MaterialTheme.colorScheme.background,
                                    ),
                                ),
                            ),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(88.dp))
                        Spacer(modifier = Modifier.weight(1f))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content = headerContent,
                        )

                        Spacer(modifier = Modifier.height(28.dp))
                        actionContent()
                    }
                }
            }

            if (showSearch) {
                item("MusicDetailSearch") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .padding(top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchTextField(
                            text = searchText,
                            placeholder = searchPlaceholder,
                            onValueChange = onSearchTextChange,
                            onSearch = {
                                if (searchText.isNotEmpty()) {
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )

                        if (searchText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        onSearchTextChange("")
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_action_close),
                                    contentDescription = stringResource(R.string.playlist_search_clear_cd),
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            content()

            item("MusicDetailBottomInset") {
                Spacer(modifier = Modifier.navigationBarsHeight(134.dp))
            }
        }

        MusicDetailTopBar(
            title = title,
            collapseProgress = collapseProgress,
            searchVisible = showSearch,
            onBack = onBack,
            onSort = onSort,
            onSearchToggle = onSearchToggle,
        )
    }
}

@Composable
fun MusicDetailCircleButton(
    painter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val backgroundColor = if (accent) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.36f)
    }
    val tint = if (accent) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.42f)
            .size(56.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun MusicDetailActionPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(YosRoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun RowScope.MusicDetailPillButton(
    painter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .then(modifier)
            .alpha(if (enabled) 1f else 0.42f)
            .fillMaxSize()
            .clip(YosRoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun MusicDetailPillDivider() {
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .alpha(0.18f)
            .background(Color.White),
    )
}

@Composable
private fun MusicDetailTopBar(
    title: String,
    collapseProgress: Float,
    searchVisible: Boolean,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onSearchToggle: () -> Unit,
) {
    val surfaceColor = lerp(
        start = Color.Black.copy(alpha = 0.34f),
        stop = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
        fraction = collapseProgress,
    )
    val iconTint = lerp(
        start = Color.White,
        stop = MaterialTheme.colorScheme.onBackground,
        fraction = collapseProgress,
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = collapseProgress * 0.96f))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MusicDetailTopBarButton(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = null,
                    surfaceColor = surfaceColor,
                    iconTint = iconTint,
                    onClick = onBack,
                )

                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(YosRoundedCornerShape(18.dp))
                        .background(surfaceColor)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MusicDetailTopBarInlineButton(
                        painter = painterResource(id = R.drawable.ic_action_sort),
                        contentDescription = stringResource(R.string.playlist_sort_title),
                        iconTint = iconTint,
                        onClick = onSort,
                    )

                    Spacer(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .width(1.dp)
                            .fillMaxHeight()
                            .alpha(0.12f)
                            .background(iconTint),
                    )

                    MusicDetailTopBarInlineButton(
                        painter = painterResource(
                            id = if (searchVisible) {
                                R.drawable.ic_action_close
                            } else {
                                R.drawable.ic_action_search
                            },
                        ),
                        contentDescription = if (searchVisible) {
                            stringResource(R.string.playlist_search_clear_cd)
                        } else {
                            stringResource(R.string.playlist_search_placeholder)
                        },
                        iconTint = iconTint,
                        onClick = onSearchToggle,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 84.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = collapseProgress >= 0.74f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    androidx.compose.material3.Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = collapseProgress >= 0.94f,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .alpha(0.12f)
                    .background(Color.Black withNight Color.White),
            )
        }
    }
}

@Composable
private fun MusicDetailTopBarButton(
    painter: Painter,
    contentDescription: String?,
    surfaceColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(surfaceColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MusicDetailTopBarInlineButton(
    painter: Painter,
    contentDescription: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}
