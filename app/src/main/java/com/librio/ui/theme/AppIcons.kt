package com.librio.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized icon definitions for consistent visual language throughout the app.
 * Uses Icons.Rounded for a flat, semi-rounded, cohesive style.
 * Outlined variants available for unfocused/unselected states.
 */
object AppIcons {
    // Content Type Icons
    val Audiobook: ImageVector = Icons.Rounded.Headphones
    val Book: ImageVector = Icons.Rounded.MenuBook
    val Music: ImageVector = Icons.Rounded.MusicNote
    val Comic: ImageVector = Icons.Rounded.CollectionsBookmark

    // Navigation & Actions
    val Back: ImageVector = Icons.Rounded.ArrowBack
    val ArrowBack: ImageVector = Icons.Rounded.ArrowBack
    val ArrowForward: ImageVector = Icons.Rounded.ArrowForward
    val Search: ImageVector = Icons.Rounded.Search
    val Settings: ImageVector = Icons.Rounded.Settings
    val Add: ImageVector = Icons.Rounded.Add
    val Delete: ImageVector = Icons.Rounded.Delete
    val Edit: ImageVector = Icons.Rounded.Edit
    val Close: ImageVector = Icons.Rounded.Close
    val Check: ImageVector = Icons.Rounded.Check
    val Clear: ImageVector = Icons.Rounded.Clear
    val Sort: ImageVector = Icons.Rounded.Sort
    val Refresh: ImageVector = Icons.Rounded.Refresh
    val MoreVert: ImageVector = Icons.Rounded.MoreVert
    val Remove: ImageVector = Icons.Rounded.RemoveCircleOutline

    // Playback Controls
    val Play: ImageVector = Icons.Rounded.PlayArrow
    val Pause: ImageVector = Icons.Rounded.Pause
    val SkipNext: ImageVector = Icons.Rounded.SkipNext
    val SkipPrevious: ImageVector = Icons.Rounded.SkipPrevious
    val Forward10: ImageVector = Icons.Rounded.Forward10
    val Forward30: ImageVector = Icons.Rounded.Forward30
    val Replay10: ImageVector = Icons.Rounded.Replay10
    val Replay30: ImageVector = Icons.Rounded.Replay30
    val Replay: ImageVector = Icons.Rounded.Replay
    val Shuffle: ImageVector = Icons.Rounded.Shuffle
    val Repeat: ImageVector = Icons.Rounded.Repeat
    val RepeatOne: ImageVector = Icons.Rounded.RepeatOne

    // Library & Organization
    val Folder: ImageVector = Icons.Rounded.Folder
    val FolderOpen: ImageVector = Icons.Rounded.FolderOpen
    val Playlist: ImageVector = Icons.Rounded.QueueMusic
    val Library: ImageVector = Icons.Rounded.LibraryBooks
    val LibraryMusic: ImageVector = Icons.Rounded.LibraryMusic

    // UI & Display
    val ChevronRight: ImageVector = Icons.Rounded.ChevronRight
    val ChevronLeft: ImageVector = Icons.Rounded.ChevronLeft
    val ExpandMore: ImageVector = Icons.Rounded.ExpandMore
    val ExpandLess: ImageVector = Icons.Rounded.ExpandLess
    val KeyboardArrowDown: ImageVector = Icons.Rounded.KeyboardArrowDown
    val GridView: ImageVector = Icons.Rounded.GridView
    val ListView: ImageVector = Icons.Rounded.ViewList

    // Profile & User
    val Person: ImageVector = Icons.Rounded.Person
    val SwitchAccount: ImageVector = Icons.Rounded.SwitchAccount

    // Theme & Appearance
    val Palette: ImageVector = Icons.Rounded.Palette
    val DarkMode: ImageVector = Icons.Rounded.DarkMode
    val LightMode: ImageVector = Icons.Rounded.LightMode
    val ColorLens: ImageVector = Icons.Rounded.ColorLens
    val ZoomIn: ImageVector = Icons.Rounded.ZoomIn

    // Text & Formatting
    val TextFields: ImageVector = Icons.Rounded.TextFields
    val FormatBold: ImageVector = Icons.Rounded.FormatBold
    val FormatAlignLeft: ImageVector = Icons.Rounded.FormatAlignLeft
    val FormatAlignCenter: ImageVector = Icons.Rounded.FormatAlignCenter
    val FormatAlignJustify: ImageVector = Icons.Rounded.FormatAlignJustify
    val FormatLineSpacing: ImageVector = Icons.Rounded.FormatLineSpacing

    // Settings Categories
    val Timer: ImageVector = Icons.Rounded.Timer
    val Equalizer: ImageVector = Icons.Rounded.Equalizer
    val VolumeUp: ImageVector = Icons.Rounded.VolumeUp
    val Vibration: ImageVector = Icons.Rounded.Vibration
    val Notifications: ImageVector = Icons.Rounded.Notifications
    val Bookmark: ImageVector = Icons.Rounded.Bookmark
    val Extension: ImageVector = Icons.Rounded.Extension
    val Storage: ImageVector = Icons.Rounded.Storage
    val Image: ImageVector = Icons.Rounded.Image
    val TouchApp: ImageVector = Icons.Rounded.TouchApp

    // Reader Controls
    val Visibility: ImageVector = Icons.Rounded.Visibility
    val VisibilityOff: ImageVector = Icons.Rounded.VisibilityOff
    val BrightnessLow: ImageVector = Icons.Rounded.BrightnessLow
    val BrightnessHigh: ImageVector = Icons.Rounded.BrightnessHigh
    val ViewArray: ImageVector = Icons.Rounded.ViewArray
    val Tune: ImageVector = Icons.Rounded.Tune
    val AutoStories: ImageVector = Icons.Rounded.AutoStories

    // Media States
    val PlayCircle: ImageVector = Icons.Rounded.PlayCircle
    val Movie: ImageVector = Icons.Rounded.Movie

    // Fullscreen & Screen Controls
    val Fullscreen: ImageVector = Icons.Rounded.Fullscreen
    val FullscreenExit: ImageVector = Icons.Rounded.FullscreenExit
    val ScreenRotation: ImageVector = Icons.Rounded.ScreenRotation

    // Outlined variants for bottom navigation (unselected states)
    val LibraryMusicOutlined: ImageVector = Icons.Outlined.LibraryMusic
    val PersonOutlined: ImageVector = Icons.Outlined.Person
    val SettingsOutlined: ImageVector = Icons.Outlined.Settings
}
