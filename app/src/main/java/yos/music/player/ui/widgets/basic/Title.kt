package yos.music.player.ui.widgets.basic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCirc
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cormor.overscroll.core.overScrollVertical
import com.cormor.overscroll.core.rememberOverscrollFlingBehavior
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.insets.statusBarsHeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import yos.music.player.R
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.ui.theme.withNight

/*@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun NewTitle(
    modifier: Modifier = Modifier,
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    CupertinoScaffold(
        modifier = modifier,
        topBar = {
            CupertinoTopAppBar(
                title = {
                Text(text = title)
                },
                navigationIcon = {
                        CupertinoIcon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onBack?.invoke()
                            })
                },
                actions = {
                    if (rightIcon != null) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(
                                        bounded = false
                                    ),
                                    onClick = onRightIcon!!
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = rightIcon,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize(),
                                tint = Color.Black withNight Color.White
                            )
                        }
                    }
                }
            )
        }
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}*/

/*@Composable
fun Title(
    title: String,
    subTitle: String? = null,
    individualScroll: Boolean,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    content: @Composable () -> Unit
) =
    BaseTitle(
        title = title,
        subTitle = subTitle,
        individualScroll = true,
        onBack = onBack,
        rightIcon = rightIcon,
        onRightIcon = onRightIcon,
        content as Any
    )*/

@Composable
fun Title(
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    bottomPadding: Dp = 134.dp,
    /**
     * Optional externally-owned list state. Lift this when the caller
     * needs to drive scrolling (e.g. the in-playlist pull-to-reveal
     * search, PRD §5.1, scrolls past a hidden search field on first
     * composition). When null the title creates its own state, matching
     * the long-standing default.
     */
    listState: LazyListState? = null,
    content: LazyListScope.() -> Unit
) =
    BaseTitle(
        title = title,
        subTitle = subTitle,
        onBack = onBack,
        rightIcon = rightIcon,
        onRightIcon = onRightIcon,
        rightBarIcon = rightBarIcon,
        grid = false,
        bottomPadding = bottomPadding,
        listState = listState,
        content = content
    )

@Composable
fun TitleWithLazyVerticalGrid(
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    columns: () -> Int = { 2 },
    content: LazyGridScope.() -> Unit
) =
    BaseTitle(
        title = title,
        subTitle = subTitle,
        onBack = onBack,
        rightIcon = rightIcon,
        onRightIcon = onRightIcon,
        rightBarIcon = rightBarIcon,
        columns = columns,
        grid = true,
        content = content
    )

@Composable
private fun BaseTitle(
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    columns: () -> Int = { 2 },
    grid: Boolean,
    bottomPadding: Dp = 134.dp,
    listState: LazyListState? = null,
    content: Any
) {
    if (grid) {
        BaseTitleGrid(
            title = title,
            subTitle = subTitle,
            onBack = onBack,
            rightIcon = rightIcon,
            onRightIcon = onRightIcon,
            rightBarIcon = rightBarIcon,
            columns = columns,
            content = content as LazyGridScope.() -> Unit
        )
    } else {
        BaseTitleList(
            title = title,
            subTitle = subTitle,
            onBack = onBack,
            rightIcon = rightIcon,
            onRightIcon = onRightIcon,
            rightBarIcon = rightBarIcon,
            bottomPadding = bottomPadding,
            listState = listState,
            content = content as LazyListScope.() -> Unit
        )
    }
}

@Composable
private fun BaseTitleGrid(
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    columns: () -> Int = { 2 },
    content: LazyGridScope.() -> Unit
) {
    val state = rememberLazyGridState()
    val alpha = rememberAlpha(state)
    val showSmallTitle = rememberShowSmallTitle(alpha, state)

    Box(Modifier.fillMaxSize()) {
        val hazeState = remember(title) { HazeState() }

        LazyVerticalGrid(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { state },
            columns = GridCells.Fixed(columns()),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp/*, bottom = 18.dp*/,
                top = 54.dp
            )
        ) {
            item(key = "title", span = { GridItemSpan(columns()) }) {
                Column {
                    Spacer(modifier = Modifier.statusBarsHeight())
                    TitleItem(
                        title,
                        subTitle,
                        rightIcon,
                        onRightIcon,
                        alpha,
                        true
                    )
                }
            }
            content()
            item("navbar", span = { GridItemSpan(columns()) }) {
                Spacer(modifier = Modifier.navigationBarsHeight(134.dp))
            }
        }

        TitleBar(
            title = title,
            onBack = onBack,
            showSmallTitle = showSmallTitle,
            hazeState = hazeState,
            rightBarIcon = rightBarIcon
        )
    }
}

@Composable
private fun BaseTitleList(
    title: String,
    subTitle: String? = null,
    onBack: (() -> Unit)? = null,
    rightIcon: ImageVector? = null,
    onRightIcon: (() -> Unit)? = null,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    bottomPadding: Dp = 134.dp,
    listState: LazyListState? = null,
    content: LazyListScope.() -> Unit
) {
    val state = listState ?: rememberLazyListState()
    val alpha = rememberAlpha(state)
    val showSmallTitle = rememberShowSmallTitle(alpha, state)

    Box(Modifier.fillMaxSize()) {
        val hazeState = remember(title) { HazeState() }

        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { state },
            contentPadding = PaddingValues(top = 54.dp)
        ) {
            item("title") {
                Column {
                    Spacer(modifier = Modifier.statusBarsHeight())
                    TitleItem(
                        title,
                        subTitle,
                        rightIcon,
                        onRightIcon,
                        alpha,
                        false
                    )
                }
            }
            content()
            item("navbar") {
                Spacer(modifier = Modifier.navigationBarsHeight(bottomPadding))
            }
        }

        TitleBar(
            title = title,
            onBack = onBack,
            showSmallTitle = showSmallTitle,
            hazeState = hazeState,
            rightBarIcon = rightBarIcon
        )
    }
}

@Composable
private fun rememberAlpha(state: LazyListState): State<Float> {
    return remember("BaseTitle_alpha") {
        derivedStateOf {
            val currentOffsetY =
                state.layoutInfo.visibleItemsInfo.find { it.index == 0 }?.offset ?: -1
            val height = state.layoutInfo.visibleItemsInfo.find { it.index == 0 }?.size ?: -1
            when {
                currentOffsetY + height <= 0 -> 0f
                else -> (1f + ((currentOffsetY.toFloat() / height) * 1.8f)).coerceAtLeast(0f)
            }
        }
    }
}

@Composable
private fun rememberAlpha(state: LazyGridState): State<Float> {
    return remember("BaseTitle_alpha") {
        derivedStateOf {
            val currentOffsetY =
                state.layoutInfo.visibleItemsInfo.find { it.index == 0 }?.offset?.y ?: -1
            val height =
                state.layoutInfo.visibleItemsInfo.find { it.index == 0 }?.size?.height ?: -1
            when {
                currentOffsetY + height <= 0 -> 0f
                else -> (1f + ((currentOffsetY.toFloat() / height) * 1.8f)).coerceAtLeast(0f)
            }
        }
    }
}

@Composable
private fun rememberShowSmallTitle(alpha: State<Float>, state: LazyListState): State<Boolean> {
    return remember("BaseTitle_showSmallTitle") {
        derivedStateOf {
            //println(alpha.value)
            alpha.value <= 0f && state.layoutInfo.visibleItemsInfo.isNotEmpty()
        }
    }
}

@Composable
private fun rememberShowSmallTitle(alpha: State<Float>, state: LazyGridState): State<Boolean> {
    return remember("BaseTitle_showSmallTitle") {
        derivedStateOf {
            //println(alpha.value)

            alpha.value <= 0f && state.layoutInfo.visibleItemsInfo.isNotEmpty()
        }
    }
}

@Composable
fun TitleBarIcon(modifier: Modifier = Modifier, icon: ImageVector? = null, onBack: (() -> Unit)? = null) {
    if (icon != null) {
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(end = 10.dp)
                .size(28.dp)
                .background((Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f), shape = CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onBack?.invoke()
                }.then(modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(19.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun TitleBar(
    title: String,
    onBack: (() -> Unit)?,
    rightBarIcon: @Composable (RowScope.() -> Unit)? = null,
    showSmallTitle: State<Boolean>,
    hazeState: HazeState
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsHeight(54.dp)
            .clickable(enabled = false, onClick = {})
    ) {
        AnimatedVisibility(
            visible = showSmallTitle.value,
            modifier = Modifier.fillMaxWidth(),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (SettingsLibrary.BarBlurEffect)
                            Modifier.hazeChild(
                                hazeState,
                                HazeMaterials
                                    .thick(Color.White withNight Color.Black)
                                    .copy(
                                        blurRadius = 32.dp,
                                        backgroundColor = Color.White withNight Color.Black,
                                        tint = (Color.White withNight Color.Black).copy(alpha = 0.7f)
                                    )
                            )
                        else
                            Modifier.background(Color.White withNight Color.Black)
                    )
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
        ) {
            Box(Modifier.statusBarsHeight(48.dp), contentAlignment = Alignment.CenterStart) {
                if (onBack != null) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = null,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 10.dp)
                            .size(17.5.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack
                            ),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedVisibility(
                        visible = showSmallTitle.value,
                        enter = fadeIn(animationSpec = tween(120, easing = EaseOut)) +
                                expandVertically(
                                    expandFrom = Alignment.Top,
                                    clip = false,
                                    animationSpec = tween(120, easing = EaseOut)
                                ),
                        exit = fadeOut(animationSpec = tween(120, easing = EaseInCirc)) +
                                shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    clip = false,
                                    animationSpec = tween(120, easing = EaseInCirc)
                                )
                    ) {
                        Text(
                            text = title,
                            fontSize = 18.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (rightBarIcon != null) {
                    Row(Modifier.fillMaxSize().padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        rightBarIcon()
                    }
                }
            }

            AnimatedVisibility(
                visible = showSmallTitle.value,
                enter = fadeIn(animationSpec = tween(120, easing = EaseOut)),
                exit = fadeOut(animationSpec = tween(120, easing = EaseInCirc))
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.12f)
                        .height(1.dp)
                        .background(Color.Black withNight Color.White)
                )
            }
        }
    }
}

@Composable
private fun TitleItem(
    title: String,
    subTitle: String?,
    rightIcon: ImageVector?,
    onRightIcon: (() -> Unit)?,
    alpha: State<Float>,
    grid: Boolean = false
) {
    Row(
        Modifier
            .padding(horizontal = if (grid) 0.dp else 20.dp)
            .padding(bottom = if (grid) 0.dp else 12.dp, top = 8.dp)
            .graphicsLayer {
                //compositingStrategy = CompositingStrategy.Offscreen
                this.alpha = alpha.value
            }) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 35.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 40.sp
            )
            if (subTitle != null) {
                Text(
                    text = subTitle,
                    modifier = Modifier
                        .alpha(0.5f)
                        .padding(horizontal = 2.5.dp)
                )
            }
        }

        if (rightIcon != null) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            enabled = onRightIcon != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRightIcon ?: {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rightIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
