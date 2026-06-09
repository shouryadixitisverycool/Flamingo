package yos.music.player.ui

import androidx.compose.runtime.Stable
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/*object UI {
    const val HomePage = "HomePage"

    const val Settings = "Settings"

    object Settings {
        const val LyricGetter = "LyricGetter"
    }
}*/

@Stable
interface UI {
    companion object {
        const val HomePage = "HomePage"
        const val Library = "Library"

        const val NormalMusic = "NormalMusic"
        const val PlayLists = "PlayLists"
        const val LocalArtists = "LocalArtists"
        const val LocalAlbums = "LocalAlbums"

        const val AlbumInfo = "AlbumInfo"
        const val ArtistInfo = "ArtistInfo"
        const val ArtistSongs = "ArtistSongs"
    }

    @Stable
    interface Settings {
        companion object {
            const val Main = "Main"
            const val LibraryOverview = "LibraryOverview"
            const val ArtistSplit = "ArtistSplit"

            const val LyricGetter = "LyricGetter"
            const val ExoplayerSetting = "ExoplayerSetting"
            const val About = "About"
            const val MediaCodec = "MediaCodec"

            const val LyricSetting = "LyricSetting"
            const val UserInterfaceSetting = "UserInterfaceSetting"
            const val NotificationSetting = "NotificationSetting"
        }
    }
}

private const val ReturnToNowPlayingOnBackKey = "ReturnToNowPlayingOnBackKey"


fun NavController.toUI(route: String, data: String? = null) {
    if (data.isNullOrEmpty()) {
        this.navigate(route)
    } else {
        this.navigate(getNavUri(route, data))
    }
}

fun getNavUri(route: String, data: String? = null): String {
    return if (data == null) {
        route
    } else {
        "$route/$data"
    }
}

fun NavController.markNextNavigationFromNowPlaying()
{
    currentBackStackEntry?.savedStateHandle?.set(ReturnToNowPlayingOnBackKey, true)
}

fun NavController.consumeNowPlayingNavigationMarker(): Boolean
{
    return previousBackStackEntry?.savedStateHandle?.remove<Boolean>(ReturnToNowPlayingOnBackKey) == true
}

fun NavController.returnToLibraryFromNowPlaying()
{
    if (popBackStack(UI.Library, false)) {
        return
    }

    navigate(UI.Library) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
