package com.librio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.librio.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    // Playback Controls
    headsetControls: Boolean = true,
    onHeadsetControlsChange: (Boolean) -> Unit = {},
    pauseOnDisconnect: Boolean = true,
    onPauseOnDisconnectChange: (Boolean) -> Unit = {},
    hapticFeedback: Boolean = true,
    onHapticFeedbackChange: (Boolean) -> Unit = {},
    showPlaybackNotification: Boolean = true,
    onShowPlaybackNotificationChange: (Boolean) -> Unit = {},
    // Playback Behavior
    autoPlayNext: Boolean = true,
    onAutoPlayNextChange: (Boolean) -> Unit = {},
    resumePlayback: Boolean = true,
    onResumePlaybackChange: (Boolean) -> Unit = {},
    rememberLastPosition: Boolean = true,
    onRememberLastPositionChange: (Boolean) -> Unit = {},
    autoRewind: Int = 0,
    onAutoRewindChange: (Int) -> Unit = {},
    // Library & Display
    showPlaceholderIcons: Boolean = true,
    onShowPlaceholderIconsChange: (Boolean) -> Unit = {},
    showFileSize: Boolean = true,
    onShowFileSizeChange: (Boolean) -> Unit = {},
    showDuration: Boolean = true,
    onShowDurationChange: (Boolean) -> Unit = {},
    showBackButton: Boolean = true,
    onShowBackButtonChange: (Boolean) -> Unit = {},
    showSearchBar: Boolean = true,
    onShowSearchBarChange: (Boolean) -> Unit = {},
    useSquareCorners: Boolean = false,
    onUseSquareCornersChange: (Boolean) -> Unit = {},
    defaultLibraryView: String = "LIST",
    onDefaultLibraryViewChange: (String) -> Unit = {},
    // General
    autoBookmark: Boolean = true,
    onAutoBookmarkChange: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = false,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    confirmBeforeDelete: Boolean = true,
    onConfirmBeforeDeleteChange: (Boolean) -> Unit = {},
    // Library actions
    onUpdateLibrary: () -> Unit = {},
    onRescanLibrary: () -> Unit = {},
    onBackupProfile: () -> Unit = {},
    // Current profile name for display
    currentProfileName: String = "",
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    var showRescanConfirmDialog by remember { mutableStateOf(false) }

    // Rescan Library Confirmation Dialog
    if (showRescanConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRescanConfirmDialog = false },
            containerColor = palette.surface,
            title = {
                Text(
                    "Rescan Library",
                    color = palette.primary
                )
            },
            text = {
                Text(
                    "This will refresh metadata for all media files in your library. Continue?",
                    color = palette.primary.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRescanLibrary()
                    showRescanConfirmDialog = false
                }) {
                    Text("Rescan", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescanConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Header with profile indicator
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.primary
                )
                if (currentProfileName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.Person,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Settings for $currentProfileName",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted
                        )
                    }
                }
            }
        }

        // Playback Controls Section
        item {
            SettingsSection(title = "Playback Controls", icon = AppIcons.Audiobook) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        SettingsSwitchRow(
                            title = "Headset controls",
                            subtitle = "Allow play/pause and skip from headphones",
                            checked = headsetControls,
                            onCheckedChange = onHeadsetControlsChange
                        )
                        SettingsSwitchRow(
                            title = "Pause on disconnect",
                            subtitle = "Stop playback when headphones unplug",
                            checked = pauseOnDisconnect,
                            onCheckedChange = onPauseOnDisconnectChange
                        )
                        SettingsSwitchRow(
                            title = "Haptic feedback",
                            subtitle = "Vibrate on key actions",
                            checked = hapticFeedback,
                            onCheckedChange = onHapticFeedbackChange
                        )
                        SettingsSwitchRow(
                            title = "Playback notification",
                            subtitle = "Show controls while media plays",
                            checked = showPlaybackNotification,
                            onCheckedChange = onShowPlaybackNotificationChange
                        )
                    }
                }
            }
        }

        // Playback Behavior Section
        item {
            SettingsSection(title = "Playback Behavior", icon = AppIcons.Play) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        SettingsSwitchRow(
                            title = "Auto play next",
                            subtitle = "Advance through the playlist automatically",
                            checked = autoPlayNext,
                            onCheckedChange = onAutoPlayNextChange
                        )
                        SettingsSwitchRow(
                            title = "Resume playback",
                            subtitle = "Continue where you left off",
                            checked = resumePlayback,
                            onCheckedChange = onResumePlaybackChange
                        )
                        SettingsSwitchRow(
                            title = "Remember position",
                            subtitle = "Save playback position for all files",
                            checked = rememberLastPosition,
                            onCheckedChange = onRememberLastPositionChange
                        )
                        Text(
                            text = "Auto-rewind",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        Slider(
                            value = autoRewind.toFloat(),
                            onValueChange = { onAutoRewindChange(it.toInt()) },
                            valueRange = 0f..60f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accent,
                                activeTrackColor = palette.accent,
                                inactiveTrackColor = palette.surfaceLight
                            )
                        )
                        Text(
                            text = if (autoRewind == 0) "Off" else "${autoRewind}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted
                        )
                    }
                }
            }
        }

        // Library & Display Section
        item {
            SettingsSection(title = "Library & Display", icon = AppIcons.Library) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        SettingsSwitchRow(
                            title = "Show placeholder icons",
                            subtitle = "Replace all cover art with placeholders",
                            checked = showPlaceholderIcons,
                            onCheckedChange = onShowPlaceholderIconsChange
                        )
                        SettingsSwitchRow(
                            title = "Show file size",
                            subtitle = "Display file sizes in lists",
                            checked = showFileSize,
                            onCheckedChange = onShowFileSizeChange
                        )
                        SettingsSwitchRow(
                            title = "Show duration",
                            subtitle = "Display durations in lists",
                            checked = showDuration,
                            onCheckedChange = onShowDurationChange
                        )
                        SettingsSwitchRow(
                            title = "Show back button",
                            subtitle = "Display back button in headers",
                            checked = showBackButton,
                            onCheckedChange = onShowBackButtonChange
                        )
                        SettingsSwitchRow(
                            title = "Show search bar",
                            subtitle = "Display search icon in headers",
                            checked = showSearchBar,
                            onCheckedChange = onShowSearchBarChange
                        )
                        Divider(
                            color = palette.divider,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        SettingsSwitchRow(
                            title = "Square corners",
                            subtitle = "Use square corners instead of rounded",
                            checked = useSquareCorners,
                            onCheckedChange = onUseSquareCornersChange
                        )

                        // Library View Mode selector
                        Text(
                            text = "Library View",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = defaultLibraryView == "LIST",
                                onClick = { onDefaultLibraryViewChange("LIST") },
                                label = { Text("List") },
                                leadingIcon = if (defaultLibraryView == "LIST") {
                                    { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.accent.copy(alpha = 0.2f),
                                    selectedLabelColor = palette.accent
                                )
                            )
                            FilterChip(
                                selected = defaultLibraryView == "GRID_2",
                                onClick = { onDefaultLibraryViewChange("GRID_2") },
                                label = { Text("Grid (2 columns)") },
                                leadingIcon = if (defaultLibraryView == "GRID_2") {
                                    { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.accent.copy(alpha = 0.2f),
                                    selectedLabelColor = palette.accent
                                )
                            )
                        }
                    }
                }
            }
        }

        // General Section
        item {
            SettingsSection(title = "General", icon = AppIcons.Settings) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        SettingsSwitchRow(
                            title = "Auto-save progress",
                            subtitle = "Save position when closing app",
                            checked = autoBookmark,
                            onCheckedChange = onAutoBookmarkChange
                        )
                        SettingsSwitchRow(
                            title = "Keep screen on",
                            subtitle = "Prevent screen from sleeping during playback",
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChange
                        )
                        SettingsSwitchRow(
                            title = "Confirm before delete",
                            subtitle = "Ask before removing items",
                            checked = confirmBeforeDelete,
                            onCheckedChange = onConfirmBeforeDeleteChange
                        )
                    }
                }
            }
        }

        // Library Actions Section
        item {
            SettingsSection(title = "Library Actions", icon = AppIcons.FolderOpen) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    // Update Library Button
                    SettingsActionRow(
                        icon = AppIcons.FolderOpen,
                        title = "Update Library",
                        subtitle = "Check for new files and playlists",
                        onClick = onUpdateLibrary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rescan Library Button
                    SettingsActionRow(
                        icon = AppIcons.Refresh,
                        title = "Rescan Library",
                        subtitle = "Refresh metadata for all media files",
                        onClick = { showRescanConfirmDialog = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Backup Profile Button
                    SettingsActionRow(
                        icon = AppIcons.Storage,
                        title = "Backup Profile",
                        subtitle = "Save settings, progress, and playlists",
                        onClick = onBackupProfile
                    )

                    Text(
                        text = "Restore by dropping the backup file into Librio/Profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    val palette = currentPalette()

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        }
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val palette = currentPalette()
    val shape16 = cornerRadius(16.dp)

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surfaceMedium),
        shape = shape16,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onPrimary,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.shade5,
                uncheckedTrackColor = palette.surfaceLight
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape12)
            .background(palette.surfaceMedium)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(shape12)
                .background(palette.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = palette.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.primary.copy(alpha = 0.5f)
            )
        }
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            tint = palette.textMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}
