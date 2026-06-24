@file:OptIn(ExperimentalSharedTransitionApi::class)

package yos.music.player

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.accompanist.insets.navigationBarsHeight
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yos.music.player.code.ListenHistoryManager
import yos.music.player.code.ListenStatsManager
import yos.music.player.code.MediaController
import yos.music.player.code.SystemMediaControlResolver
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.code.utils.player.FadeExo.fadePause
import yos.music.player.code.utils.player.FadeExo.fadePlay
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.defaultTitle
import yos.music.player.data.models.ImageViewModel
import yos.music.player.data.models.MainViewModel
import yos.music.player.data.models.MediaViewModel
import yos.music.player.data.objects.MediaViewModelObject
import yos.music.player.ui.UI
import yos.music.player.ui.UI.Settings.Companion.ExoplayerSetting
import yos.music.player.ui.pages.HomeNav
import yos.music.player.ui.pages.NowPlaying
import yos.music.player.ui.pages.NowPlayingPage.Album
import yos.music.player.ui.pages.StatsAlbums
import yos.music.player.ui.pages.StatsArtists
import yos.music.player.ui.pages.StatsTracks
import yos.music.player.ui.pages.library.Library
import yos.music.player.ui.pages.library.NormalMusic
import yos.music.player.ui.pages.library.albums.AlbumInfo
import yos.music.player.ui.pages.library.albums.LocalAlbums
import yos.music.player.ui.pages.library.artists.ArtistInfo
import yos.music.player.ui.pages.library.artists.ArtistSongs
import yos.music.player.ui.pages.library.artists.LocalArtists
import yos.music.player.ui.pages.library.playlists.PlayLists
import yos.music.player.ui.pages.settings.Settings
import yos.music.player.ui.pages.settings.audio.exoPlayer.ExoPlayerSettings
import yos.music.player.ui.pages.settings.audio.exoPlayer.MediaCodec
import yos.music.player.ui.pages.settings.extend.statusBarLyric.LyricGetter
import yos.music.player.ui.pages.settings.library.ArtistSplitSetting
import yos.music.player.ui.pages.settings.library.LibraryOverview
import yos.music.player.ui.pages.settings.others.About
import yos.music.player.ui.pages.settings.performance.LyricSetting
import yos.music.player.ui.pages.settings.performance.NotificationSetting
import yos.music.player.ui.pages.settings.performance.userinterface.ScreenCornerSetDialog
import yos.music.player.ui.pages.settings.performance.userinterface.UserInterfaceSetting
import yos.music.player.ui.theme.YosMusicTheme
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.isFlamingoInDarkMode
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.BottomNavigator
import yos.music.player.ui.widgets.basic.ImageQuality
import yos.music.player.ui.widgets.basic.NavItem
import yos.music.player.ui.widgets.basic.ShadowImageWithCache
import yos.music.player.ui.widgets.basic.YosWrapper
import java.io.File
import kotlin.math.abs

/*//MediaPlayer全局控制器
var mediaController = yos.music.player.code.MediaController*/

class MainActivity : BaseActivity() {

    private val mediaViewModel: MediaViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    private val imageViewModel: ImageViewModel by viewModels()

    @Suppress("DEPRECATION")
    @OptIn(
        ExperimentalAnimationApi::class,
        ExperimentalHazeMaterialsApi::class, ExperimentalSharedTransitionApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContent {
            YosMusicTheme {
                ProvideWindowInsets {
                    val context = LocalContext.current
                    val density = LocalDensity.current

                    LaunchedEffect(Unit) {
                        withFrameNanos {}
                        MediaController.ensureInitialized(context)
                        ListenHistoryManager.loadHistory()
                        ListenStatsManager.loadEvents()
                    }

                    val offsetY = remember("MainActivity_offsetY") { Animatable(0f) }
                    val parentHeight =
                        remember("MainActivity_parentHeight") { mutableIntStateOf(0) }
                    val screenCorner = remember("MainActivity_screenCorner") {
                        val corner = SettingsLibrary.ScreenCorner.toInt()
                        if (corner == 0) 1 else corner
                    }

                    val scope = rememberCoroutineScope()

                    /*val parentHeightDp = remember(parentHeight.intValue) {
                        with(density) {
                            parentHeight.intValue.toDp()
                        }
                    }*/

                    /*Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = Color.Black withNight Color.White
                    ) {*/
                        val miniPlayerHeight = 62.dp
                        val height = remember("MainActivity_height") { mutableIntStateOf(0) }

                        val navHeight = remember("MainActivity_navHeight") {
                            with(density) {
                                height.intValue.toDp().plus(miniPlayerHeight)
                            }
                        }
                        // 逻辑初始化区域
                        val navController = rememberNavController()
                        val navSpec = spring(
                            stiffness = 400f,
                            dampingRatio = 1f,
                            visibilityThreshold = 0.001f
                        )
                        // val scaffoldState = rememberBottomSheetScaffoldState()
                        val route = rememberSaveable(key = "MainActivity_route") {
                            mutableStateOf(UI.HomePage)
                        }
                        // 记录当前路线

                        YosWrapper {
                            val backstackEntry =
                                navController.currentBackStackEntryAsState()
                            route.value =
                                backstackEntry.value?.destination?.route ?: UI.HomePage
                        }

                        // 显示控制区域
                        val yosBottomSheetConfig = object {
                            val progress
                                get() = if (parentHeight.intValue == 0) 0f else abs(offsetY.value / parentHeight.intValue).coerceIn(
                                    0f,
                                    1f
                                )
                            val menuAlpha
                                get() = 1f - progress
                            val mainContainerCardScale
                                get() = 0.9f + (0.1f * menuAlpha)
                            val thisShowCorner
                                get() = progress > 0f

                            val barShowCorner
                                get() = progress < 1f
                            val showMenu
                                get() = progress < 1f

                            val barShapeValue
                                get() = lerp(12, screenCorner, progress)

                            val RTCorner
                                get() = screenCorner == 0
                        }

                        val showNowPlaying = remember("MainActivity_showNowPlaying") {
                            derivedStateOf {
                                yosBottomSheetConfig.menuAlpha < 0.3f
                            }
                        }

                        val nowPageNowPlaying =
                            rememberSaveable(key = "MainActivity_nowPageNowPlaying") {
                                mutableStateOf(Album)
                            }

                        val hazeState = remember { HazeState() }
                        YosWrapper {
                            val isNight = isFlamingoInDarkMode()
                            if (showNowPlaying.value) {
                                rememberSystemUiController().run {
                                    setNavigationBarColor(
                                        Color.Transparent,
                                        darkIcons = false,
                                        navigationBarContrastEnforced = false
                                    )
                                    setStatusBarColor(Color.Transparent, darkIcons = false)
                                    var activity = LocalContext.current as? Activity
                                    DisposableEffect(Unit) {
                                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                        onDispose {
                                            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                            activity = null
                                        }
                                    }
                                }
                            } else {
                                rememberSystemUiController().run {
                                    setNavigationBarColor(
                                        color = Color.Transparent,
                                        darkIcons = !isNight,
                                        navigationBarContrastEnforced = false
                                    )

                                    setStatusBarColor(Color.Transparent, darkIcons = !isNight)
                                }
                            }
                        }

                        // 导航区域
                        val defaultHome = stringResource(id = R.string.page_home_title)

                        val nowLabel = rememberSaveable(key = "MainActivity_nowLabel") {
                            mutableStateOf(defaultHome)
                        }

                        val pagerState = rememberPagerState(pageCount = { 3 })

                        // 以下为实际显示

                        // 主界面
                        YosWrapper {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val thisMainContainerCardScale =
                                            yosBottomSheetConfig.mainContainerCardScale
                                        scaleX = thisMainContainerCardScale
                                        scaleY = thisMainContainerCardScale
                                    }
                                    .graphicsLayer {
                                        //compositingStrategy = CompositingStrategy.Offscreen
                                        //transformOrigin = TransformOrigin(0.5f, 1f)

                                        if (yosBottomSheetConfig.thisShowCorner && !yosBottomSheetConfig.RTCorner) {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                            clip = true
                                            shape = YosRoundedCornerShape(screenCorner.dp)
                                        } else {
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                            clip = false
                                        }

                                        if (!yosBottomSheetConfig.barShowCorner) {
                                            alpha = 0f
                                        }
                                    }

                                ,
                                color = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ) {
                                // 主界面本体
                                SharedTransitionLayout {
                                    YosWrapper {
                                        BackHandler(showNowPlaying.value) {
                                            // println("isVisible: ${showNowPlaying.value}, nowPageNowPlaying.value: ${nowPageNowPlaying.value}")
                                            if (nowPageNowPlaying.value != Album) {
                                                nowPageNowPlaying.value =
                                                    Album
                                            } else {
                                                scope.launch {
                                                    // scaffoldState.bottomSheetState.partialExpand()
                                                    offsetY.animateTo(0f, animationSpec = navSpec)
                                                }
                                            }
                                        }
                                    }

                                    YosWrapper {
                                        val animateSpeed = 340
                                        val animationSpec: FiniteAnimationSpec<IntSize> =
                                            tween(
                                                durationMillis = animateSpeed,
                                                easing = EaseOutQuart
                                            )
                                        val fadeAnimationSpec: FiniteAnimationSpec<Float> =
                                            tween(
                                                durationMillis = animateSpeed - 160,
                                                easing = EaseOutQuart
                                            )
                                        AnimatedNavHost(
                                            modifier = Modifier.then(
                                                if (SettingsLibrary.BarBlurEffect && !showNowPlaying.value) {
                                                    Modifier.haze(state = hazeState)
                                                } else {
                                                    //println("haze 父控件效果关闭")
                                                    Modifier
                                                }
                                            ),
                                            navController = navController,
                                            startDestination = UI.HomePage,
                                            enterTransition = {
                                                fadeIn(animationSpec = fadeAnimationSpec) + expandHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    expandFrom = Alignment.Start
                                                ) {
                                                    -it / 2
                                                }
                                            },
                                            exitTransition = {
                                                fadeOut(animationSpec = fadeAnimationSpec) + shrinkHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    shrinkTowards = Alignment.End
                                                ) {
                                                    it / 2
                                                }
                                            },
                                            popEnterTransition = {
                                                fadeIn(animationSpec = fadeAnimationSpec) + expandHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    expandFrom = Alignment.End
                                                ) {
                                                    it / 2
                                                }
                                            },
                                            popExitTransition = {
                                                fadeOut(animationSpec = fadeAnimationSpec) + shrinkHorizontally(
                                                    animationSpec = animationSpec,
                                                    clip = false,
                                                    shrinkTowards = Alignment.Start
                                                ) {
                                                    -it / 2
                                                }
                                            }) {

                                            composable(UI.HomePage) {
                                                HomeNav(
                                                    navController,
                                                    pagerState,
                                                    imageViewModel
                                                ) {
                                                    nowLabel.value = it
                                                }
                                            }
                                            composable(UI.Library) {
                                                Library(
                                                    navController
                                                )
                                            }

                                            composable(UI.PlayLists) {
                                                PlayLists(navController)
                                            }
                                            composable(UI.NormalMusic) {
                                                NormalMusic(navController)
                                            }
                                            composable(UI.LocalAlbums) {
                                                LocalAlbums(
                                                    navController,
                                                    this@SharedTransitionLayout,
                                                    this@composable
                                                )
                                            }
                                            composable(UI.LocalArtists) {
                                                LocalArtists(navController)
                                            }
                                            composable(UI.ArtistInfo) {
                                                ArtistInfo(navController = navController)
                                            }
                                            composable(UI.ArtistSongs) {
                                                ArtistSongs(navController)
                                            }

                                            composable(UI.AlbumInfo) {
                                                AlbumInfo(
                                                    navController = navController,
                                                    sharedTransitionScope = this@SharedTransitionLayout,
                                                    animatedContentScope = this@composable,
                                                )
                                            }

                                            composable(UI.StatsArtists) {
                                                StatsArtists(navController)
                                            }
                                            composable(UI.StatsAlbums) {
                                                StatsAlbums(navController)
                                            }
                                            composable(UI.StatsTracks) {
                                                StatsTracks(navController)
                                            }

                                            composable(UI.Settings.Main) {
                                                Settings(
                                                    navController
                                                )
                                            }
                                            composable(UI.Settings.LibraryOverview) {
                                                LibraryOverview(navController)
                                            }
                                            composable(UI.Settings.ArtistSplit) {
                                                ArtistSplitSetting(navController)
                                            }
                                            composable(UI.Settings.LyricGetter) {
                                                LyricGetter(navController)
                                            }
                                            composable(ExoplayerSetting) {
                                                ExoPlayerSettings(navController)
                                            }
                                            composable(UI.Settings.About) {
                                                About(
                                                    navController
                                                )
                                            }
                                            composable(UI.Settings.MediaCodec) {
                                                MediaCodec(navController)
                                            }

                                            composable(UI.Settings.LyricSetting) {
                                                LyricSetting(navController)
                                            }
                                            composable(UI.Settings.UserInterfaceSetting) {
                                                UserInterfaceSetting(navController)
                                            }
                                            composable(UI.Settings.NotificationSetting) {
                                                NotificationSetting(navController)
                                            }
                                        }


                                    }
                                }

                                // 底部导航栏
                                YosWrapper {
                                    val color =
                                        Color(0xFFF5F5F5) withNight /*Color(0xFF111111)*/ Color.Black
                                    Box(
                                        Modifier
                                            .fillMaxSize(),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        // 背景
                                        if (!showNowPlaying.value && SettingsLibrary.BarBlurEffect) {
                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .navigationBarsHeight(128.dp)
                                                    .graphicsLayer {
                                                        compositingStrategy =
                                                            CompositingStrategy.Offscreen
                                                    }
                                                    .hazeChild(
                                                        hazeState,
                                                        HazeMaterials
                                                            .regular(
                                                                color
                                                            )
                                                            .copy(
                                                                blurRadius = 48.dp
                                                            )
                                                    )
                                                    .drawWithCache {
                                                        onDrawWithContent {
                                                            val colors = listOf(
                                                                Color.Transparent,
                                                                color.copy(alpha = 0.3f),
                                                                color.copy(alpha = 0.6f),
                                                                color,
                                                                color,
                                                                color,
                                                                color,
                                                                color
                                                            )

                                                            drawContent()

                                                            drawRect(
                                                                brush = Brush.verticalGradient(
                                                                    colors
                                                                ),
                                                                blendMode = BlendMode.DstIn
                                                            )
                                                        }
                                                    }
                                            )
                                        } else {
                                            //println("haze 底栏效果关闭")
                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .navigationBarsHeight(128.dp)
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                color.copy(alpha = 0.3f),
                                                                color.copy(alpha = 0.6f),
                                                                color,
                                                                color,
                                                                color,
                                                                color,
                                                                color
                                                            ),
                                                            startY = 0f,
                                                            endY = Float.POSITIVE_INFINITY
                                                        )
                                                    )
                                            )
                                        }

                                        BottomNavigator(
                                            nowLabel = { nowLabel.value },
                                            onLabelChange = {
                                                nowLabel.value = it

                                                val home =
                                                    context.getString(R.string.page_home_title)
                                                val library =
                                                    context.getString(R.string.page_library_title)
                                                val stats =
                                                    context.getString(R.string.page_stats_title)
                                                if (route.value == UI.HomePage) {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(
                                                            when (it) {
                                                                home -> 0
                                                                library -> 1
                                                                stats -> 2
                                                                else -> 0
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    scope.launch {
                                                        pagerState.scrollToPage(
                                                            when (it) {
                                                                home -> 0
                                                                library -> 1
                                                                stats -> 2
                                                                else -> 0
                                                            }
                                                        )
                                                    }
                                                    navController.popBackStack(
                                                        UI.HomePage,
                                                        false
                                                    )
                                                }
                                            },
                                            items = listOf(
                                                NavItem(
                                                    stringResource(id = R.string.page_home_title),
                                                    R.drawable.flamingo_icon
                                                ),
                                                NavItem(
                                                    stringResource(id = R.string.page_library_title),
                                                    R.drawable.ic_uitabbar_library
                                                ),
                                                NavItem(
                                                    stringResource(id = R.string.page_stats_title),
                                                    R.drawable.ic_uitabbar_stats
                                                )
                                            ),
                                            modifier = Modifier
                                                .onSizeChanged {
                                                    height.intValue = it.height
                                                })
                                    }
                                }
                            }

                            // 弹窗
                            YosWrapper {
                                val showCornerSetDialog =
                                    remember("MainActivity_showCornerSetDialog") {
                                        mutableStateOf(!SettingsLibrary.ScreenCornerSet)
                                    }

                                if (showCornerSetDialog.value) {
                                    ScreenCornerSetDialog {
                                        showCornerSetDialog.value = false
                                    }
                                } else {
                                    CheckAndRequestPermission()
                                }
                            }
                        }

                        // 播放条&播放界面
                        YosWrapper {
                            if (height.intValue == 0) return@YosWrapper

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onSizeChanged {
                                        parentHeight.intValue = it.height
                                    }
                                    .graphicsLayer {
                                        //compositingStrategy = CompositingStrategy.Offscreen
                                        val plus =
                                            0.07f * yosBottomSheetConfig.progress
                                        this.scaleX = 0.93f + plus
                                        this.scaleY = 0.93f + plus
                                        this.translationY =
                                            -(height.intValue + 10) * (yosBottomSheetConfig.menuAlpha)
                                        this.transformOrigin = TransformOrigin(0.5f, 1f)
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                YosWrapper {
                                    val miniPlayerHeightPx = remember("MainActivity_miniPlayerHeightPx") {
                                        with(density) {
                                            miniPlayerHeight.toPx()
                                        }
                                    }

                                    val miniPlayerShadow = remember("MainActivity_miniPlayerShadow") {
                                        with(density) {
                                            7.5.dp.toPx()
                                        }
                                    }

                                    val color = Color.White withNight Color(0xFF1C1C1E)

                                    val dragState = rememberDraggableState { delta ->
                                        scope.launch {
                                            offsetY.snapTo(offsetY.value + delta)
                                        }
                                    }

                                    /*.graphicsLayer {
                                        if (yosBottomSheetConfig.barShowCorner) {
                                            shadowElevation = 7.5.dp.toPx()
                                            spotShadowColor = Color.Black.copy(alpha = 0.2f)

                                            *//*compositingStrategy =
                                                        CompositingStrategy.Offscreen*//*
                                                }
                                            }*/
                                    /*.drawWithContent {
                                        drawContent()
                                        drawOutline(
                                            outline = navShape.createOutline(size, layoutDirection, this),
                                            color = color.copy(alpha = yosBottomSheetConfig.menuAlpha),
                                            style = Stroke(width = 0.3.dp.toPx())
                                        )
                                    }*/

                                    Surface(
                                        modifier = Modifier.graphicsLayer {
                                            translationY =
                                                ((parentHeight.intValue - miniPlayerHeightPx) * (1 - yosBottomSheetConfig.progress))

                                            if (yosBottomSheetConfig.barShowCorner) {
                                                shape = YosRoundedCornerShape(
                                                    roundSize = yosBottomSheetConfig.barShapeValue.dp,
                                                    mSize = Size(
                                                        width = size.width,
                                                        height = (miniPlayerHeightPx + (parentHeight.intValue - miniPlayerHeightPx) * yosBottomSheetConfig.progress)
                                                    )
                                                )
                                                shadowElevation = miniPlayerShadow
                                                spotShadowColor = Color.Black.copy(alpha = 0.2f)
                                            }
                                        }
                                            .graphicsLayer {
                                                //translationY = ((parentHeight.intValue - miniPlayerHeightPx) * (1 - yosBottomSheetConfig.progress))

                                                if (yosBottomSheetConfig.barShowCorner) {
                                                    compositingStrategy = CompositingStrategy.Offscreen
                                                    clip = true
                                                    shape = YosRoundedCornerShape(
                                                        roundSize = yosBottomSheetConfig.barShapeValue.dp,
                                                        mSize = Size(
                                                            width = size.width,
                                                            height = (miniPlayerHeightPx + (parentHeight.intValue - miniPlayerHeightPx) * yosBottomSheetConfig.progress)
                                                        )
                                                    )
                                                } else {
                                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                                    clip = false
                                                }
                                            }
                                            .draggable(
                                                reverseDirection = true,
                                                orientation = Orientation.Vertical,
                                                state = dragState,
                                                onDragStopped = { velocity ->
                                                    offsetY.updateBounds(
                                                        0f,
                                                        parentHeight.intValue.toFloat()
                                                    )
                                                    scope.launch {
                                                        //println("速度：$velocity 高度：${miniPlayerHeight + (parentHeightDp - miniPlayerHeight) * progress}")
                                                        if (velocity < 0f) {
                                                            offsetY.animateTo(
                                                                0f,
                                                                initialVelocity = velocity
                                                            )
                                                        } else {
                                                            offsetY.animateTo(
                                                                parentHeight.intValue.toFloat(),
                                                                initialVelocity = velocity
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                            .layout { measurable, constraints ->
                                                val placeable = measurable.measure(
                                                    constraints.copy(
                                                        minHeight = parentHeight.intValue,
                                                        maxHeight = parentHeight.intValue
                                                    )
                                                )
                                                layout(
                                                    placeable.width,
                                                    placeable.height
                                                ) {
                                                    placeable.placeRelative(0, 0)
                                                }
                                            },
                                        color = Color.Transparent
                                    ) {
                                        val isPlaying =
                                            rememberSaveable(key = "MainActivity_isPlaying") {
                                                MediaViewModelObject.isPlaying
                                            }
                                        val miniPlayerTrack = MediaController.musicPlaying.value
                                        val miniPlayerTrackKey =
                                            miniPlayerTrack?.mediaId ?: miniPlayerTrack?.uri?.toString()
                                            ?: ""
                                        val systemMediaControlResolver = remember(context) {
                                            SystemMediaControlResolver(context)
                                        }
                                        val miniPlayerWidth = remember("MainActivity_miniPlayerWidth") {
                                            mutableIntStateOf(0)
                                        }
                                        val miniPlayerHorizontalOffset =
                                            remember("MainActivity_miniPlayerHorizontalOffset") {
                                                Animatable(0f)
                                            }
                                        val miniPlayerHorizontalActionDirection =
                                            remember("MainActivity_miniPlayerHorizontalActionDirection") {
                                                mutableIntStateOf(0)
                                            }
                                        val miniPlayerHorizontalState = rememberDraggableState { delta ->
                                            if (
                                                yosBottomSheetConfig.menuAlpha != 1f ||
                                                miniPlayerHorizontalActionDirection.intValue != 0
                                            ) {
                                                return@rememberDraggableState
                                            }

                                            val maxOffset =
                                                (miniPlayerWidth.intValue * 0.35f).coerceAtLeast(1f)

                                            scope.launch {
                                                miniPlayerHorizontalOffset.snapTo(
                                                    (miniPlayerHorizontalOffset.value + delta).coerceIn(
                                                        -maxOffset,
                                                        maxOffset
                                                    )
                                                )
                                            }
                                        }

                                        LaunchedEffect(miniPlayerTrackKey) {
                                            if (miniPlayerHorizontalActionDirection.intValue == 0) {
                                                return@LaunchedEffect
                                            }

                                            val widthPx =
                                                miniPlayerWidth.intValue.toFloat().coerceAtLeast(1f)

                                            miniPlayerHorizontalOffset.snapTo(
                                                -miniPlayerHorizontalActionDirection.intValue * widthPx
                                            )
                                            miniPlayerHorizontalOffset.animateTo(
                                                0f,
                                                animationSpec = tween(
                                                    durationMillis = 65,
                                                    easing = EaseOutQuart
                                                )
                                            )
                                            miniPlayerHorizontalActionDirection.intValue = 0
                                        }

                                        // NowPlaying
                                        YosWrapper {
                                            NowPlaying(
                                                mainViewModel = mainViewModel,
                                                mediaViewModel = mediaViewModel,
                                                navController = navController,
                                                onMinimizeNowPlaying = {
                                                    nowPageNowPlaying.value = Album
                                                    offsetY.animateTo(0f, animationSpec = navSpec)
                                                },
                                                isPlayingStatusLambda = { isPlaying.value },
                                                isPlayingOnChanged = {
                                                    isPlaying.value = it
                                                },
                                                nowPageLambda = { nowPageNowPlaying.value },
                                                showMiniPlayer = { yosBottomSheetConfig.showMenu }
                                            ) {
                                                nowPageNowPlaying.value = it
                                            }
                                        }

                                        // 迷你播放状态
                                        YosWrapper {
                                            //println("变换：菜单透明度 $menuAlpha")
                                            if (yosBottomSheetConfig.showMenu) {
                                                YosWrapper {
                                                    Column(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .graphicsLayer {
                                                                compositingStrategy =
                                                                    CompositingStrategy.ModulateAlpha
                                                                this.alpha =
                                                                    yosBottomSheetConfig.menuAlpha
                                                            }
                                                            .background(color)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(miniPlayerHeight)
                                                                .clickable(
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null,
                                                                    onClick = {
                                                                        scope.launch {
                                                                            offsetY.animateTo(
                                                                                parentHeight.intValue.toFloat(),
                                                                                animationSpec = navSpec
                                                                            )
                                                                        }
                                                                    })
                                                        ) {

                                                            YosWrapper {
                                                                val showMiniBarHaze = remember(
                                                                    "MainActivity_showMiniBarHaze",
                                                                    yosBottomSheetConfig.menuAlpha
                                                                ) {
                                                                    derivedStateOf {
                                                                        yosBottomSheetConfig.menuAlpha == 1f && SettingsLibrary.BarBlurEffect
                                                                    }
                                                                }
                                                                this@Column.AnimatedVisibility(
                                                                    visible = showMiniBarHaze.value,
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(
                                                                            navHeight
                                                                        ),
                                                                    enter = fadeIn(tween(100)),
                                                                    exit = fadeOut(tween(100))
                                                                ) {
                                                                    Spacer(
                                                                        modifier = Modifier
                                                                            .fillMaxSize()
                                                                            .hazeChild(
                                                                                hazeState,
                                                                                HazeMaterials
                                                                                    .thick(
                                                                                       color
                                                                                    )
                                                                                    .copy(
                                                                                        blurRadius = 48.dp
                                                                                    )
                                                                            )
                                                                    )
                                                                }
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .onSizeChanged {
                                                                        miniPlayerWidth.intValue = it.width
                                                                    }
                                                                    .draggable(
                                                                        orientation = Orientation.Horizontal,
                                                                        state = miniPlayerHorizontalState,
                                                                        enabled = yosBottomSheetConfig.menuAlpha == 1f,
                                                                        onDragStopped = { velocity ->
                                                                            scope.launch {
                                                                                val widthPx =
                                                                                    miniPlayerWidth.intValue.toFloat()
                                                                                        .coerceAtLeast(1f)
                                                                                val threshold = widthPx * 0.18f
                                                                                val velocityThreshold = 1400f
                                                                                val dragOffset =
                                                                                    miniPlayerHorizontalOffset.value
                                                                                val shouldTrigger =
                                                                                    abs(dragOffset) > threshold || abs(
                                                                                        velocity
                                                                                    ) > velocityThreshold

                                                                                if (!shouldTrigger || miniPlayerTrack == null) {
                                                                                    miniPlayerHorizontalOffset.animateTo(
                                                                                        0f,
                                                                                        animationSpec = tween(
                                                                                            durationMillis = 55,
                                                                                            easing = EaseOutQuart
                                                                                        )
                                                                                    )
                                                                                    return@launch
                                                                                }

                                                                                val actionDirection =
                                                                                    if (abs(velocity) > velocityThreshold) {
                                                                                        if (velocity < 0f) {
                                                                                            -1
                                                                                        } else {
                                                                                            1
                                                                                        }
                                                                                    } else if (dragOffset < 0f) {
                                                                                        -1
                                                                                    } else {
                                                                                        1
                                                                                    }
                                                                                val mediaControl =
                                                                                    MediaController.mediaControl
                                                                                val canSkip =
                                                                                    if (actionDirection < 0) {
                                                                                        mediaControl?.hasNextMediaItem() == true
                                                                                    } else {
                                                                                        mediaControl?.hasPreviousMediaItem() == true
                                                                                    }

                                                                                if (!canSkip) {
                                                                                    miniPlayerHorizontalOffset.animateTo(
                                                                                        0f,
                                                                                        animationSpec = tween(
                                                                                            durationMillis = 55,
                                                                                            easing = EaseOutQuart
                                                                                        )
                                                                                    )
                                                                                    return@launch
                                                                                }

                                                                                miniPlayerHorizontalActionDirection.intValue =
                                                                                    actionDirection
                                                                                miniPlayerHorizontalOffset.animateTo(
                                                                                    actionDirection * widthPx,
                                                                                    animationSpec = tween(
                                                                                        durationMillis = 45,
                                                                                        easing = EaseOutQuart
                                                                                    )
                                                                                )

                                                                                if (actionDirection < 0) {
                                                                                    mediaControl?.seekToNextMediaItem()
                                                                                } else {
                                                                                    mediaControl?.seekToPreviousMediaItem()
                                                                                }
                                                                            }
                                                                        }
                                                                    )
                                                                    .graphicsLayer {
                                                                        clip = true
                                                                    }
                                                            ) {
                                                                Row(
                                                                    Modifier
                                                                        .height(miniPlayerHeight)
                                                                        .fillMaxWidth()
                                                                        .padding(
                                                                            horizontal = 8.dp
                                                                        )
                                                                ) {
                                                                    Row(
                                                                        Modifier
                                                                            .height(
                                                                                miniPlayerHeight
                                                                            )
                                                                            .weight(1f),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        YosWrapper {
                                                                            ShadowImageWithCache(
                                                                                dataLambda = { MediaViewModelObject.bitmap.value },
                                                                                contentDescription = null,
                                                                                modifier = Modifier
                                                                                    .size(47.dp),
                                                                                cornerRadius = 6.dp,
                                                                                shadowAlpha = 0f,
                                                                                imageQuality = ImageQuality.LOW
                                                                            )
                                                                        }
                                                                        Box(
                                                                            Modifier
                                                                                .weight(1f)
                                                                                .padding(
                                                                                    start = 10.dp,
                                                                                    end = 5.dp
                                                                                )
                                                                                .graphicsLayer {
                                                                                    clip = true
                                                                                },
                                                                            contentAlignment = Alignment.CenterStart
                                                                        ) {
                                                                            Column(
                                                                                Modifier.graphicsLayer {
                                                                                    val widthPx =
                                                                                        miniPlayerWidth.intValue.toFloat()
                                                                                            .coerceAtLeast(1f)

                                                                                    translationX =
                                                                                        miniPlayerHorizontalOffset.value
                                                                                    alpha =
                                                                                        1f - (
                                                                                            abs(
                                                                                                miniPlayerHorizontalOffset.value
                                                                                            ) / widthPx
                                                                                        ).coerceIn(0f, 0.35f)
                                                                                }
                                                                            ) {
                                                                                Text(
                                                                                    text = miniPlayerTrack?.title
                                                                                        ?: defaultTitle,
                                                                                    fontWeight = FontWeight.Medium,
                                                                                    fontSize = 16.sp,
                                                                                    lineHeight = 16.sp,
                                                                                    maxLines = 1,
                                                                                    overflow = TextOverflow.Ellipsis,
                                                                                    color = Color.Black withNight Color.White
                                                                                )
                                                                                /*Text(
                                                                     text = musicPlaying.value?.Artist
                                                                         ?: "未知艺术家",
                                                                     fontSize = 13.5.sp,
                                                                     lineHeight = 13.5.sp,
                                                                     modifier = Modifier.alpha(
                                                                         0.6f
                                                                     ),
                                                                     maxLines = 1,
                                                                     overflow = TextOverflow.Ellipsis,
                                                                     color = Color.Black withNight Color.White
                                                                 )*/
                                                                            }
                                                                        }
                                                                    }
                                                                    Row(
                                                                        Modifier
                                                                            .fillMaxHeight()
                                                                            .padding(end = 10.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(34.dp)
                                                                                .clickable(
                                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                                    indication = ripple(
                                                                                        bounded = false
                                                                                    ),
                                                                                    onClick = {
                                                                                        Vibrator.click(
                                                                                            context
                                                                                        )
                                                                                        isPlaying.value =
                                                                                            !isPlaying.value
                                                                                        if (isPlaying.value) {
                                                                                            MediaController.mediaControl?.fadePlay()
                                                                                        } else {
                                                                                            MediaController.mediaControl?.fadePause()
                                                                                        }
                                                                                    }),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            AnimatedContent(
                                                                                targetState = isPlaying.value,
                                                                                transitionSpec = {
                                                                                    (scaleIn(
                                                                                        initialScale = 0.3f
                                                                                    ) + fadeIn()).togetherWith(
                                                                                        scaleOut(
                                                                                            targetScale = 0.3f
                                                                                        ) + fadeOut()
                                                                                    )
                                                                                }) {
                                                                                if (it) {
                                                                                    Icon(
                                                                                        painterResource(
                                                                                            id = R.drawable.ic_nowplaying_mp_pause
                                                                                        ),
                                                                                        contentDescription = "Pause",
                                                                                        modifier = Modifier
                                                                                            .fillMaxSize(),
                                                                                        tint = Color.Black withNight Color.White
                                                                                    )
                                                                                } else {
                                                                                    Icon(
                                                                                        painterResource(
                                                                                            id = R.drawable.ic_nowplaying_mp_play
                                                                                        ),
                                                                                        contentDescription = "Play",
                                                                                        modifier = Modifier
                                                                                            .fillMaxSize(),
                                                                                        tint = Color.Black withNight Color.White
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        Spacer(
                                                                            modifier = Modifier.width(
                                                                                18.dp
                                                                            )
                                                                        )
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(36.dp)
                                                                                .clickable(
                                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                                    indication = ripple(
                                                                                        bounded = false
                                                                                    ),
                                                                                    onClick = {
                                                                                        Vibrator.click(
                                                                                            context
                                                                                        )
                                                                                        systemMediaControlResolver.intentSystemMediaDialog()
                                                                                    }),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            val bluetoothAudioConnected = rememberBluetoothAudioConnected()
                                                                            AnimatedContent(
                                                                                targetState = bluetoothAudioConnected,
                                                                                transitionSpec = {
                                                                                    (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                                                                                        scaleOut(targetScale = 0.3f) + fadeOut()
                                                                                    )
                                                                                }
                                                                            ) { connected ->
                                                                                Icon(
                                                                                    painterResource(
                                                                                        id = if (connected) {
                                                                                            R.drawable.ic_earphone
                                                                                        } else {
                                                                                            R.drawable.ic_nowplaying_airplay
                                                                                        }
                                                                                    ),
                                                                                    contentDescription = "AirPlay",
                                                                                    modifier = Modifier
                                                                                        .size(if (connected) 27.dp else 21.5.dp),
                                                                                    tint = Color.Black withNight Color.White
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
                                    }
                                }
                            }
                        }

                        // Activity-level host for the playlist
                        // delete-undo snackbar. Mounted here so the
                        // 5s undo window survives navigation: the
                        // user can leave Library while the timer
                        // is running and the snackbar stays visible
                        // above the mini-player until they tap Undo
                        // or it auto-dismisses. Wrapped in a
                        // fullscreen Box anchored to BottomCenter so
                        // the inner Row sits at the bottom of the
                        // screen, padded above the mini-player.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            yos.music.player.ui.pages.library.playlists.UndoSnackbarHost(
                                bottomOffset = miniPlayerHeight,
                            )
                        }
                    /*}*/
                }
            }
    }
}

@Composable
private fun rememberBluetoothAudioConnected(): Boolean
{
    val context = LocalContext.current
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val bluetoothAudioConnected = remember("MainActivity_bluetoothAudioConnected") {
        mutableStateOf(false)
    }

    DisposableEffect(context, bluetoothAdapter) {
        val refreshBluetoothAudioConnected = {
            bluetoothAudioConnected.value = if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                false
            } else {
                bluetoothAdapter
                    ?.bondedDevices
                    ?.any { device ->
                        device.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO &&
                            device.isConnected()
                    } == true
            }
        }
        val receiver = object : BroadcastReceiver()
        {
            override fun onReceive(context: Context?, intent: Intent?)
            {
                val action = intent?.action
                if (action == BluetoothDevice.ACTION_ACL_CONNECTED ||
                    action == BluetoothDevice.ACTION_ACL_DISCONNECTED ||
                    action == "yos.music.player.BLUETOOTH_STATUS_REFRESH") {
                    refreshBluetoothAudioConnected()
                }
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("yos.music.player.BLUETOOTH_STATUS_REFRESH")
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        refreshBluetoothAudioConnected()

        onDispose {
            runCatching {
                context.unregisterReceiver(receiver)
            }
        }
    }

    return bluetoothAudioConnected.value
}

private fun BluetoothDevice.isConnected(): Boolean
{
    return runCatching {
        val isConnectedMethod = BluetoothDevice::class.java.getMethod("isConnected")
        isConnectedMethod.isAccessible = true
        isConnectedMethod.invoke(this) as Boolean
    }.getOrDefault(false)
}

        @Composable
    fun CheckAndRequestPermission() {
        val context = LocalContext.current
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val audioPermissionGranted = MusicLibrary.hasAudioPermission(context)
            if (audioPermissionGranted) {
                loadMusic(context, enforce = true)
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || permissions[Manifest.permission.BLUETOOTH_CONNECT] != false) {
                sendBroadcast(Intent("yos.music.player.BLUETOOTH_STATUS_REFRESH"))
            }
        }

        YosWrapper {
            LaunchedEffect(Unit) {

                var permissions = emptyArray<String>()

                if (!MusicLibrary.hasAudioPermission(context)) {
                    permissions += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissions += Manifest.permission.BLUETOOTH_CONNECT
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions += Manifest.permission.POST_NOTIFICATIONS
                }

                if (permissions.isNotEmpty()) {
                    requestPermissionLauncher.launch(permissions)
                }
                else {
                    loadMusic(context)
                }
            }
        }
    }


    /*LaunchedEffect(permissionState.value) {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            if (hasPermission) {
                permissionState.value = PermissionState.DENIED
                loadMusic(context)
            }
        }

        val showDialog = remember("CheckPermission_showDialog") {
            derivedStateOf { permissionState.value != PermissionState.DENIED  }
        }

        if (showDialog.value) {
            val dialogProperties = ModalBottomSheetProperties(
                securePolicy = SecureFlagPolicy.Inherit,
                isFocusable = true,
                shouldDismissOnBackPress = false
            )

            val bottomSheetState = rememberModalBottomSheetState()
            val scope = rememberCoroutineScope()

            YosWrapper {
                OptionDialog(
                    title = stringResource(id = R.string.permission_grant_title),
                    subTitle = stringResource(id = R.string.permission_grant_subtitle),
                    content = {
                        Text(text = stringResource(id = R.string.permission_grant_desc))
                    },
                    positiveContent = stringResource(id = R.string.permission_grant_button_positive),
                    properties = dialogProperties,
                    bottomSheetState = bottomSheetState,
                    onPositive = {
                        scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                            if (!bottomSheetState.isVisible) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionState.value = PermissionState.DENIED
                            }
                        }
                    },
                    negativeContent = stringResource(id = R.string.permission_grant_button_negative),
                    onNegative = {
                        scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                            if (!bottomSheetState.isVisible) {
                                mainMusicList.value = mutableListOf()
                                permissionState.value = PermissionState.DENIED
                            }
                        }
                    }
                ) {
                    mainMusicList.value = mutableListOf()
                    permissionState.value = PermissionState.DENIED
                }
            }
        }*/

    /*enum class PermissionState {
        UNKNOWN,
        GRANTED,
        DENIED
    }*/

    private fun loadMusic(context: Context, enforce: Boolean = false) {
        if (!MusicLibrary.hasAudioPermission(context)) {
            return
        }

        val needRefresh = SettingsLibrary.RefreshEveryTime
        if (needRefresh || enforce) {
            mediaViewModel.viewModelScope.launch(Dispatchers.IO) {
                // Application中已还原，这里算是后台扫描
                // mainMusicList.value = MusicScanner(context).getMusicList()
                MusicLibrary.scanMedia(context)
            }
        }
    }

}

fun readFile(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        //e.printStackTrace()
        null
    }
}
