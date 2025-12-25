package com.librio.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.librio.ui.theme.AppIcons

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Main : Screen("main/{initialTab}") {
        fun createRoute(initialTab: String = "library"): String = "main/$initialTab"
    }
    object Library : Screen("library")
    object Profile : Screen("profile")
    object Player : Screen("player")
    object EbookReader : Screen("ebook_reader")
    object MusicPlayer : Screen("music_player")
    object MoviePlayer : Screen("movie_player")
    object ComicReader : Screen("comic_reader")
    object Settings : Screen("settings")
    object PlaylistDetail : Screen("playlist_detail/{seriesId}") {
        fun createRoute(seriesId: String): String = "playlist_detail/$seriesId"
    }
}

/**
 * Bottom navigation items
 */
enum class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LIBRARY(
        route = Screen.Library.route,
        title = "Library",
        selectedIcon = AppIcons.LibraryMusic,
        unselectedIcon = AppIcons.LibraryMusicOutlined
    ),
    PROFILE(
        route = Screen.Profile.route,
        title = "Profile",
        selectedIcon = AppIcons.Person,
        unselectedIcon = AppIcons.PersonOutlined
    ),
    SETTINGS(
        route = Screen.Settings.route,
        title = "Settings",
        selectedIcon = AppIcons.Settings,
        unselectedIcon = AppIcons.SettingsOutlined
    )
}
