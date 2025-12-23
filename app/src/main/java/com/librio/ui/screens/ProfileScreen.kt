package com.librio.ui.screens

import android.net.Uri
import android.content.Context
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.librio.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.librio.ui.theme.*
import kotlin.math.roundToInt

data class UserProfile(
    val id: String,
    val name: String,
    val isActive: Boolean = false,
    val theme: String = "TEAL", // Store theme name per profile
    val darkMode: Boolean = false, // Store dark mode per profile
    val profilePicture: String? = null, // URI string for profile picture
    // Audio settings per profile
    val playbackSpeed: Float = 1.0f,
    val skipForwardDuration: Int = 30,
    val skipBackDuration: Int = 10,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostLevel: Float = 1.0f,
    val normalizeAudio: Boolean = false,
    val bassBoostLevel: Float = 0f,
    val equalizerPreset: String = "DEFAULT",
    val sleepTimerMinutes: Int = 0 // Sleep timer default (0 = off)
)

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    currentProfileName: String,
    profiles: List<UserProfile> = listOf(
        UserProfile("1", "Default", true)
    ),
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    accentTheme: AppTheme = currentTheme,
    onAccentThemeChange: (AppTheme) -> Unit = {},
    darkMode: Boolean = false,
    onDarkModeChange: (Boolean) -> Unit = {},
    backgroundTheme: BackgroundTheme = BackgroundTheme.WHITE,
    onBackgroundThemeChange: (BackgroundTheme) -> Unit = {},
    customPrimaryColor: Int = 0x00897B,
    onCustomPrimaryColorChange: (Int) -> Unit = {},
    customAccentColor: Int = 0x26A69A,
    onCustomAccentColorChange: (Int) -> Unit = {},
    customBackgroundColor: Int = 0x121212,
    onCustomBackgroundColorChange: (Int) -> Unit = {},
    appScale: Float = 1.0f,
    onAppScaleChange: (Float) -> Unit = {},
    uiFontScale: Float = 1.0f,
    onUiFontScaleChange: (Float) -> Unit = {},
    uiFontFamily: String = "Default",
    onUiFontFamilyChange: (String) -> Unit = {},
    onProfileSelect: (UserProfile) -> Unit = {},
    onAddProfile: (String) -> Unit = {},
    onDeleteProfile: (UserProfile) -> Unit = {},
    onRenameProfile: (UserProfile, String) -> Unit = { _, _ -> },
    onSetProfilePicture: (UserProfile, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val shape3 = cornerRadius(3.dp)
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)
    val shape10 = cornerRadius(10.dp)
    val shape12 = cornerRadius(12.dp)
    val shape14 = cornerRadius(14.dp)
    val shape16 = cornerRadius(16.dp)

    // Cache themes list to avoid repeated allocation
    val cachedThemes = remember { AppTheme.entries.toList() }

    // Cache all theme palettes to avoid expensive recalculation during composition
    val cachedPalettes = remember {
        cachedThemes.associateWith { theme -> getThemePalette(theme) }
    }
    val themeCategories = remember { themeCategoriesByColor() }

    // Get active profile for picture
    val activeProfile = profiles.find { it.isActive }

    // State for image cropping
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    // Image picker launcher - use OpenDocument for persistable URI permissions
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission - this works with OpenDocument
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission might fail but continue anyway
            }
            // Show crop dialog instead of saving directly
            pendingCropUri = it
            showCropDialog = true
        }
    }
    val palette = currentPalette()
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showDeleteProfileDialog by remember { mutableStateOf<UserProfile?>(null) }
    var showRenameProfileDialog by remember { mutableStateOf<UserProfile?>(null) }
    var showProfileOptionsDialog by remember { mutableStateOf<UserProfile?>(null) }
    var newProfileName by remember { mutableStateOf("") }
    var renameProfileName by remember { mutableStateOf("") }
    // Theme dialog
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCustomThemeDialog by remember { mutableStateOf(false) }
    // Custom theme RGB sliders state - single base color that generates all shades
    var customPrimaryR by remember(customPrimaryColor) { mutableStateOf((customPrimaryColor shr 16) and 0xFF) }
    var customPrimaryG by remember(customPrimaryColor) { mutableStateOf((customPrimaryColor shr 8) and 0xFF) }
    var customPrimaryB by remember(customPrimaryColor) { mutableStateOf(customPrimaryColor and 0xFF) }

    // Image Crop Dialog
    if (showCropDialog && pendingCropUri != null) {
        AlertDialog(
            onDismissRequest = {
                showCropDialog = false
                pendingCropUri = null
            },
            containerColor = palette.surface,
            title = {
                Text(
                    "Crop Profile Picture",
                    color = palette.primary
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Position your image within the circle",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textMuted,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Crop preview area with circular mask overlay
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(palette.surfaceMedium),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = remember(pendingCropUri) {
                            try {
                                pendingCropUri?.let { uri ->
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    inputStream?.use { stream ->
                                        val rawBitmap = BitmapFactory.decodeStream(stream)

                                        // Handle EXIF rotation
                                        try {
                                            val exifInputStream = context.contentResolver.openInputStream(uri)
                                            exifInputStream?.use { exifStream ->
                                                val exif = ExifInterface(exifStream)
                                                val orientation = exif.getAttributeInt(
                                                    ExifInterface.TAG_ORIENTATION,
                                                    ExifInterface.ORIENTATION_NORMAL
                                                )
                                                val matrix = Matrix()
                                                when (orientation) {
                                                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                                }
                                                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                            }
                                        } catch (e: Exception) {
                                            rawBitmap
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            // Center-crop the image to square
                            val croppedBitmap = remember(bitmap) {
                                val size = minOf(bitmap.width, bitmap.height)
                                val xOffset = (bitmap.width - size) / 2
                                val yOffset = (bitmap.height - size) / 2
                                Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
                            }
                            Image(
                                bitmap = croppedBitmap.asImageBitmap(),
                                contentDescription = "Profile picture preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    AppIcons.Person,
                                    contentDescription = "Failed to load image",
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "Unable to load",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Circular crop preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeProfile?.let { profile ->
                            pendingCropUri?.let { uri ->
                                onSetProfilePicture(profile, uri.toString())
                            }
                        }
                        showCropDialog = false
                        pendingCropUri = null
                    }
                ) {
                    Text("Use This Image", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCropDialog = false
                    pendingCropUri = null
                }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Add Profile Dialog
    if (showAddProfileDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddProfileDialog = false
                newProfileName = ""
            },
            containerColor = palette.surface,
            title = {
                Text(
                    "Add New Profile",
                    color = palette.primary
                )
            },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        cursorColor = palette.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            onAddProfile(newProfileName)
                            newProfileName = ""
                            showAddProfileDialog = false
                        }
                    },
                    enabled = newProfileName.isNotBlank()
                ) {
                    Text("Add", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddProfileDialog = false
                    newProfileName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Profile Confirmation Dialog
    showDeleteProfileDialog?.let { profile ->
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog = null },
            containerColor = palette.surface,
            title = {
                Text(
                    "Delete Profile",
                    color = palette.primary
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to delete \"${profile.name}\"?",
                        color = palette.primary.copy(alpha = 0.7f)
                    )
                    if (profile.isActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Warning: This is the active profile. You will be switched to Default.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProfile(profile)
                    showDeleteProfileDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProfileDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Profile Options Dialog (long-press menu)
    showProfileOptionsDialog?.let { profile ->
        AlertDialog(
            onDismissRequest = { showProfileOptionsDialog = null },
            containerColor = palette.surface,
            title = {
                Text(
                    profile.name,
                    color = palette.primary
                )
            },
            text = {
                Column {
                    // Rename option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showProfileOptionsDialog = null
                                renameProfileName = profile.name
                                showRenameProfileDialog = profile
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Rename Profile",
                            color = palette.primary
                        )
                    }

                    // Delete option (only show if more than one profile)
                    if (profiles.size > 1) {
                        Divider(color = palette.divider)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showProfileOptionsDialog = null
                                    showDeleteProfileDialog = profile
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppIcons.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Delete Profile",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProfileOptionsDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Profile Dialog
    showRenameProfileDialog?.let { profile ->
        AlertDialog(
            onDismissRequest = {
                showRenameProfileDialog = null
                renameProfileName = ""
            },
            containerColor = palette.surface,
            title = {
                Text(
                    "Rename Profile",
                    color = palette.primary
                )
            },
            text = {
                OutlinedTextField(
                    value = renameProfileName,
                    onValueChange = { renameProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        cursorColor = palette.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameProfileName.isNotBlank()) {
                            onRenameProfile(profile, renameProfileName)
                            renameProfileName = ""
                            showRenameProfileDialog = null
                        }
                    },
                    enabled = renameProfileName.isNotBlank() && renameProfileName != profile.name
                ) {
                    Text("Save", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameProfileDialog = null
                    renameProfileName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Theme Selection Dialog - Grid of gradient color preview squares
    if (showThemeDialog) {
        val columnsCount = 5 // 5 squares per row for 20 themes (4 rows)
        val squareSize = 48.dp

        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = palette.surface,
            shape = shape16,
            modifier = Modifier.widthIn(max = 340.dp), // Constrain dialog width on larger screens
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        AppIcons.Palette,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    themeCategories.forEach { category ->
                        item {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(category.themes.chunked(columnsCount)) { rowThemes ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowThemes.forEach { theme ->
                                    val themePalette = cachedPalettes[theme] ?: getThemePalette(theme)
                                    val isSelected = theme == currentTheme

                                    Box(
                                        modifier = Modifier
                                            .size(squareSize)
                                            .clip(shape10)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        themePalette.shade1,
                                                        themePalette.shade3,
                                                        themePalette.shade5
                                                    )
                                                )
                                            )
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    3.dp,
                                                    Color.White,
                                                    shape10
                                                ) else Modifier
                                            )
                                            .clickable {
                                                if (theme == AppTheme.CUSTOM) {
                                                    showThemeDialog = false
                                                    showCustomThemeDialog = true
                                                } else {
                                                    // Change both primary and accent theme together
                                                    onThemeChange(theme)
                                                    onAccentThemeChange(theme)
                                                    showThemeDialog = false
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                AppIcons.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else if (theme == AppTheme.CUSTOM) {
                                            Icon(
                                                AppIcons.Edit,
                                                contentDescription = "Custom",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel", color = palette.accent)
                }
            }
        )
    }

    // Custom Theme Editor Dialog - Single color picker that generates all shades
    if (showCustomThemeDialog) {
        val baseColor = Color(customPrimaryR, customPrimaryG, customPrimaryB)
        // Hex input synced with RGB sliders
        var hexInput by remember(customPrimaryR, customPrimaryG, customPrimaryB) {
            mutableStateOf(String.format("#%02X%02X%02X", customPrimaryR, customPrimaryG, customPrimaryB))
        }
        LaunchedEffect(customPrimaryR, customPrimaryG, customPrimaryB) {
            hexInput = String.format("#%02X%02X%02X", customPrimaryR, customPrimaryG, customPrimaryB)
        }
        // Generate preview shades from base color
        val previewShades = remember(customPrimaryR, customPrimaryG, customPrimaryB) {
            createShadesFromColor(baseColor)
        }

        AlertDialog(
            onDismissRequest = { showCustomThemeDialog = false },
            containerColor = palette.surface,
            shape = shape16,
            modifier = Modifier.widthIn(max = 380.dp), // Constrain dialog width on larger screens
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        AppIcons.ColorLens,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Custom Theme",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.accent
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Generated Shades Preview - Show all 7 shades in a gradient row
                    Text(
                        text = "Generated Palette",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(shape12),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        previewShades.forEachIndexed { index, shadeColor ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(shadeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (index < 3) Color.White else Color.Black.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Dark → Base → Light",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mini Preview with generated palette
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape12,
                        colors = CardDefaults.cardColors(containerColor = previewShades[6]) // Ultra light bg
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Mini header bar using base color
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .background(baseColor)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "LIBRIO",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Mini content preview
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Book card with gradient
                                Box(
                                    modifier = Modifier
                                        .size(45.dp, 60.dp)
                                        .clip(shape6)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(previewShades[0], previewShades[2], previewShades[4])
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        AppIcons.Book,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // Button with gradient
                                Box(
                                    modifier = Modifier
                                        .size(45.dp, 60.dp)
                                        .clip(shape6)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(previewShades[1], previewShades[2], previewShades[3])
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        AppIcons.Play,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                // Progress indicator
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Now Playing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = baseColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(shape3)
                                            .background(previewShades[5])
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.65f)
                                                .height(6.dp)
                                                .clip(shape3)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(previewShades[0], previewShades[2], previewShades[4])
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Single Color RGB Sliders
                    Text(
                        text = "Choose Your Color",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primary
                    )

                    // Color preview swatch
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(shape8)
                            .background(baseColor)
                    )

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { value ->
                            val cleaned = value.trim().removePrefix("#").take(6)
                            hexInput = if (value.startsWith("#")) "#$cleaned" else "#$cleaned"
                            if (cleaned.length == 6 && cleaned.all { it in "0123456789ABCDEFabcdef" }) {
                                try {
                                    val parsed = cleaned.toInt(16)
                                    customPrimaryR = (parsed shr 16) and 0xFF
                                    customPrimaryG = (parsed shr 8) and 0xFF
                                    customPrimaryB = parsed and 0xFF
                                } catch (_: Exception) { }
                            }
                        },
                        label = { Text("Hex (#RRGGBB)", color = palette.primary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            cursorColor = palette.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Red slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("R", color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Slider(
                            value = customPrimaryR.toFloat(),
                            onValueChange = { customPrimaryR = it.toInt() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
                        )
                        Text(customPrimaryR.toString(), color = palette.textMuted, modifier = Modifier.width(36.dp))
                    }

                    // Green slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G", color = Color.Green, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Slider(
                            value = customPrimaryG.toFloat(),
                            onValueChange = { customPrimaryG = it.toInt() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
                        )
                        Text(customPrimaryG.toString(), color = palette.textMuted, modifier = Modifier.width(36.dp))
                    }

                    // Blue slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("B", color = Color.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Slider(
                            value = customPrimaryB.toFloat(),
                            onValueChange = { customPrimaryB = it.toInt() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
                        )
                        Text(customPrimaryB.toString(), color = palette.textMuted, modifier = Modifier.width(36.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Save the single base color
                        val baseColorInt = (0xFF shl 24) or (customPrimaryR shl 16) or (customPrimaryG shl 8) or customPrimaryB
                        onCustomPrimaryColorChange(baseColorInt)
                        // Also set accent to same color for legacy support
                        onCustomAccentColorChange(baseColorInt)
                        // Align background with generated palette so custom theme uses the new color
                        val generatedBackground = previewShades.getOrElse(8) { baseColor }.toArgb()
                        onCustomBackgroundColorChange(generatedBackground)
                        onThemeChange(AppTheme.CUSTOM)
                        onAccentThemeChange(AppTheme.CUSTOM)
                        showCustomThemeDialog = false
                    }
                ) {
                    Text("Apply", color = palette.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomThemeDialog = false }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Profile Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar with border - long press to change picture
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .border(4.dp, palette.accent, CircleShape)
                        .background(palette.accentGradient())
                        .combinedClickable(
                            onClick = { },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                imagePickerLauncher.launch(arrayOf("image/*"))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Show profile picture if available (async loading)
                    val profilePicUri = activeProfile?.profilePicture
                    val avatarBitmap by rememberAsyncProfileBitmap(profilePicUri, context, 256)

                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap!!.asImageBitmap(),
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = currentProfileName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineLarge,
                            color = palette.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (currentProfileName.isNotBlank()) "${currentProfileName}'s Library" else "My Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Hint text for long press
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(shape16)
                        .background(palette.surfaceMedium)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        AppIcons.TouchApp,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Long press avatar to change",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }
        }

        // Profile Selection Section
        item {
            ProfileSection(title = "Switch Profile", icon = AppIcons.SwitchAccount) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(profiles) { profile ->
                        ProfileChip(
                            profile = profile,
                            isSelected = profile.isActive,
                            onClick = { onProfileSelect(profile) },
                            onLongClick = {
                                // Show options dialog for any profile
                                showProfileOptionsDialog = profile
                            }
                        )
                    }
                    item {
                        // Add Profile Button
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(2.dp, palette.shade4, CircleShape)
                                .background(palette.shade5.copy(alpha = 0.3f))
                                .clickable(onClick = { showAddProfileDialog = true }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.Add,
                                contentDescription = "Add Profile",
                                tint = palette.shade3
                            )
                        }
                    }
                }
            }
        }

        // Appearance Section - Enhanced styling
        item {
            ProfileSection(title = "Appearance", icon = AppIcons.Palette) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    // Theme Selection Card - enhanced with gradient preview
                    val themePalette = cachedPalettes[currentTheme] ?: getThemePalette(currentTheme)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceMedium),
                        shape = shape16,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showThemeDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Large gradient preview of the theme
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(4.dp, shape14)
                                    .clip(shape14)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                themePalette.shade1,
                                                themePalette.shade3,
                                                themePalette.shade5
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    AppIcons.Palette,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Color Theme",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.primary
                                )
                                Text(
                                    text = currentTheme.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = themePalette.primary,
                                    fontWeight = FontWeight.Medium
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // App Scale Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceMedium),
                        shape = shape16,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(shape12)
                                        .background(palette.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.ZoomIn,
                                        contentDescription = null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "App Scale",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.primary
                                    )
                                    Text(
                                        text = "${(appScale * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.accent,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Slider(
                                value = appScale,
                                onValueChange = { onAppScaleChange(it.coerceIn(0.85f, 1.3f)) },
                                valueRange = 0.85f..1.3f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent,
                                    inactiveTrackColor = palette.surfaceLight
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Typography Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = palette.surfaceMedium),
                        shape = shape16,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(shape12)
                                        .background(palette.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.TextFields,
                                        contentDescription = null,
                                        tint = palette.accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Typography",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.primary
                                    )
                                    Text(
                                        text = "Font size & family",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.textMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Font Size
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Size",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.primary,
                                    modifier = Modifier.width(50.dp)
                                )
                                Slider(
                                    value = uiFontScale.coerceIn(0.85f, 1.75f),
                                    onValueChange = { onUiFontScaleChange(it.coerceIn(0.85f, 1.75f)) },
                                    valueRange = 0.85f..1.75f,
                                    steps = 17,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.accent,
                                        activeTrackColor = palette.accent,
                                        inactiveTrackColor = palette.surfaceLight
                                    )
                                )
                                Text(
                                    text = "${(uiFontScale * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.accent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(45.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Font Family
                            val fontFamilies = listOf("Default", "Sans", "Serif", "Monospace", "Cursive", "Casual")
                            val selectedFontIndex = fontFamilies.indexOfFirst { it.equals(uiFontFamily, ignoreCase = true) }.coerceAtLeast(0)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Font",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = palette.primary,
                                    modifier = Modifier.width(50.dp)
                                )
                                Slider(
                                    value = selectedFontIndex.toFloat(),
                                    onValueChange = { value ->
                                        val index = value.roundToInt().coerceIn(fontFamilies.indices)
                                        onUiFontFamilyChange(fontFamilies[index])
                                    },
                                    valueRange = 0f..(fontFamilies.size - 1).toFloat(),
                                    steps = (fontFamilies.size - 2).coerceAtLeast(0),
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.accent,
                                        activeTrackColor = palette.accent,
                                        inactiveTrackColor = palette.surfaceLight
                                    )
                                )
                                Text(
                                    text = fontFamilies.getOrElse(selectedFontIndex) { "Default" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.accent,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(70.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun rememberAsyncProfileBitmap(
    uriString: String?,
    context: Context,
    targetSizePx: Int = 256
): State<Bitmap?> {
    val state = remember(uriString) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uriString) {
        state.value = null
        if (uriString.isNullOrBlank()) return@LaunchedEffect
        // Serve from cache if available
        ProfileAvatarCache.get(uriString)?.let {
            state.value = it
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                // First decode bounds to compute sample size
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)
                    val maxDim = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
                    val sample = (maxDim / targetSizePx).coerceAtLeast(1)
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use { dataStream ->
                        val decoded = BitmapFactory.decodeStream(dataStream, null, decodeOpts)
                        if (decoded != null) {
                            // Apply EXIF rotation if needed
                            val rotated = context.contentResolver.openInputStream(uri)?.use { exifStream ->
                                val exif = ExifInterface(exifStream)
                                val orientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL
                                )
                                val rotation = when (orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                    else -> 0f
                                }
                                if (rotation != 0f) {
                                    val matrix = Matrix().apply { postRotate(rotation) }
                                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                                } else decoded
                            } ?: decoded
                            ProfileAvatarCache.put(uriString, rotated)
                            state.value = rotated
                        }
                    }
                }
            } catch (_: Exception) {
                state.value = null
            }
        }
    }
    return state
}

private object ProfileAvatarCache {
    private val cache = mutableMapOf<String, Bitmap?>()
    fun get(uri: String): Bitmap? = cache[uri]
    fun put(uri: String, bitmap: Bitmap?) { cache[uri] = bitmap }
}

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    val palette = currentPalette()

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ProfileChip(
    profile: UserProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val palette = currentPalette()
    val context = LocalContext.current

    val bitmap by rememberAsyncProfileBitmap(profile.profilePicture, context, 128)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) palette.accentGradient()
                    else Brush.linearGradient(listOf(palette.shade5.copy(alpha = 0.5f), palette.shade6.copy(alpha = 0.4f)))
                )
                .then(
                    if (isSelected) Modifier.border(3.dp, palette.primaryLight, CircleShape)
                    else Modifier.border(2.dp, palette.shade4, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Profile picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = profile.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSelected) palette.onPrimary else palette.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) palette.primary else palette.primary.copy(alpha = 0.5f)
        )
    }
}
