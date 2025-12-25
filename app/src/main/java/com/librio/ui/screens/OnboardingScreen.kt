package com.librio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.ui.theme.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.Configuration
import android.Manifest
import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Multi-step onboarding flow with:
 * 0. Permissions request
 * 1. Welcome + Profile setup
 * 2. Theme picker
 * 3. Library overview
 * 4. Profile screen overview
 * 5. Settings overview
 * 6. Getting started (file management)
 * 7. Category swipe gesture hint
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    currentProfile: UserProfile?,
    onRenameProfile: (UserProfile, String) -> Unit,
    onSetProfilePicture: (UserProfile, String?) -> Unit,
    currentTheme: AppTheme = AppTheme.TEAL,
    onThemeChange: (AppTheme) -> Unit = {},
    onAccentThemeChange: (AppTheme) -> Unit = {},
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()

    // Current step (0-7)
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 8

    // Profile data
    var profileName by remember { mutableStateOf(currentProfile?.name ?: "") }
    var hasEditedName by remember { mutableStateOf(false) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageUri by remember { mutableStateOf<String?>(currentProfile?.profilePicture) }

    val context = LocalContext.current

    // Load profile picture bitmap
    LaunchedEffect(selectedImageUri) {
        if (selectedImageUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(selectedImageUri))
                    profileBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                } catch (e: Exception) {
                    profileBitmap = null
                }
            }
        }
    }

    fun goToNext() {
        if (currentStep < totalSteps - 1) {
            currentStep++
        } else {
            // Save profile name and complete
            if (hasEditedName && profileName.isNotBlank() && currentProfile != null) {
                onRenameProfile(currentProfile, profileName.trim())
            }
            onComplete()
        }
    }

    fun goToPrevious() {
        if (currentStep > 0) {
            currentStep--
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.shade11,
                        palette.shade10,
                        palette.shade9
                    )
                )
            )
    ) {
        // Main content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            label = "onboarding_step"
        ) { step ->
            when (step) {
                0 -> PermissionsStep(
                    onNext = ::goToNext
                )
                1 -> WelcomeStep(
                    profileName = profileName,
                    onProfileNameChange = {
                        profileName = it
                        hasEditedName = true
                    },
                    profileBitmap = profileBitmap,
                    onPickImage = { uri ->
                        selectedImageUri = uri
                        currentProfile?.let { profile ->
                            onSetProfilePicture(profile, uri)
                        }
                    },
                    onNext = ::goToNext,
                    onBack = ::goToPrevious
                )
                2 -> ThemePickerStep(
                    currentTheme = currentTheme,
                    onThemeChange = { theme ->
                        onThemeChange(theme)
                        onAccentThemeChange(theme)
                    },
                    onNext = ::goToNext,
                    onBack = ::goToPrevious
                )
                3 -> LibraryOverviewStep(
                    onNext = ::goToNext,
                    onBack = ::goToPrevious
                )
                4 -> ProfileOverviewStep(
                    onNext = ::goToNext,
                    onBack = ::goToPrevious
                )
                5 -> SettingsOverviewStep(
                    onNext = ::goToNext,
                    onBack = ::goToPrevious
                )
                6 -> GettingStartedStep(
                    onNext = ::goToNext,
                    onBack = ::goToPrevious
                )
                7 -> SwipeGestureStep(
                    onComplete = {
                        if (hasEditedName && profileName.isNotBlank() && currentProfile != null) {
                            onRenameProfile(currentProfile, profileName.trim())
                        }
                        onComplete()
                    },
                    onBack = ::goToPrevious
                )
            }
        }

        // Progress indicators at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStep) 24.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index == currentStep) palette.accent
                            else Color.White.copy(alpha = 0.3f)
                        )
                        .animateContentSize()
                )
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    onNext: () -> Unit
) {
    val palette = currentPalette()
    val context = LocalContext.current
    val shape16 = cornerRadius(16.dp)
    val shape12 = cornerRadius(12.dp)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val topSpacing = if (isLandscape) 16.dp else 48.dp
    val cardPadding = if (isLandscape) 12.dp else 16.dp

    // Permission states
    var storageGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }

    // Check current permission status
    LaunchedEffect(Unit) {
        storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older Android
        }
    }

    // Permission launchers
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        storageGranted = permissions.values.any { it }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    val permissions = listOf(
        PermissionItem(
            icon = AppIcons.Storage,
            title = "Storage Access",
            description = "Required to read your audiobooks, e-books, music, and videos from your device",
            isGranted = storageGranted,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    storagePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_IMAGES
                        )
                    )
                } else {
                    storagePermissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    )
                }
            }
        ),
        PermissionItem(
            icon = AppIcons.Notifications,
            title = "Notifications",
            description = "Show playback controls and progress when playing media in the background",
            isGranted = notificationGranted,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            isOptional = true
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = topSpacing, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 56.dp else 80.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 28.dp else 40.dp),
                    tint = palette.accent
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))

            Text(
                text = "Permissions",
                style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

            Text(
                text = "Librio needs a few permissions to work properly",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

            // Permission cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                permissions.forEach { permission ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape12,
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(cardPadding),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isLandscape) 36.dp else 44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (permission.isGranted) palette.accent.copy(alpha = 0.3f)
                                        else Color.White.copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (permission.isGranted) AppIcons.Check else permission.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isLandscape) 18.dp else 22.dp),
                                    tint = if (permission.isGranted) palette.accent else Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = permission.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    if (permission.isOptional) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Optional",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                Text(
                                    text = permission.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (!permission.isGranted) {
                                TextButton(
                                    onClick = permission.onRequest,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = palette.accent
                                    )
                                ) {
                                    Text("Grant", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info text
            Text(
                text = "You can change these permissions later in your device settings",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Continue button fixed at bottom
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = Color.White
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = if (isLandscape) 16.dp else 60.dp)
                .height(if (isLandscape) 44.dp else 52.dp),
            shape = shape16
        ) {
            Text(
                text = if (storageGranted) "Continue" else "Continue Anyway",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(AppIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

private data class PermissionItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val onRequest: () -> Unit,
    val isOptional: Boolean = false
)

@Composable
private fun WelcomeStep(
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    profileBitmap: Bitmap?,
    onPickImage: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val palette = currentPalette()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val shape16 = cornerRadius(16.dp)
    val shape24 = cornerRadius(24.dp)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Use OpenDocument for persistable URI permissions
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so the image remains accessible after app restart
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission might fail but continue anyway
            }
            onPickImage(it.toString())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = if (isLandscape) 16.dp else 48.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        // App icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(palette.shade3, palette.shade4, palette.shade5)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Library,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to",
            style = MaterialTheme.typography.titleLarge,
            color = palette.shade3
        )

        Text(
            text = "LIBRIO",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            ),
            color = palette.shade1
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your personal media library",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.shade4
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Feature highlights
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureChip(icon = AppIcons.Audiobook, label = "Audiobooks")
            FeatureChip(icon = AppIcons.Book, label = "E-books")
            FeatureChip(icon = AppIcons.Music, label = "Music")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Profile setup card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape24,
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set up your profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.shade2,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Profile picture
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(palette.shade10)
                        .border(3.dp, palette.accent, CircleShape)
                        .clickable {
                            val mimeTypes = arrayOf("image/*")
                            imagePickerLauncher.launch(mimeTypes)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (profileBitmap != null) {
                        Image(
                            bitmap = profileBitmap.asImageBitmap(),
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = palette.accent
                            )
                            Text(
                                text = "Add photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.shade4
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "What should we call you?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.shade3
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = profileName,
                    onValueChange = onProfileNameChange,
                    placeholder = { Text("Enter your name", color = palette.shade6) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.shade8,
                        focusedTextColor = palette.shade1,
                        unfocusedTextColor = palette.shade2,
                        cursorColor = palette.accent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = shape16,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        }

        // Navigation buttons fixed at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = if (isLandscape) 16.dp else 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(AppIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }

            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(AppIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ThemePickerStep(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val palette = currentPalette()
    val shape10 = cornerRadius(10.dp)
    val shape16 = cornerRadius(16.dp)

    // Cache theme palettes
    val cachedThemes = remember { AppTheme.entries.filter { it != AppTheme.CUSTOM }.toList() }
    val cachedPalettes = remember(cachedThemes) {
        cachedThemes.associateWith { theme -> getThemePalette(theme) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = AppIcons.Palette,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = palette.accent
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pick Your Theme",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose a color scheme that suits you",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Theme grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cachedThemes) { theme ->
                val themePalette = cachedPalettes[theme] ?: getThemePalette(theme)
                val isSelected = theme == currentTheme

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
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
                            if (isSelected) Modifier.border(3.dp, Color.White, shape10)
                            else Modifier
                        )
                        .clickable { onThemeChange(theme) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            AppIcons.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = shape16,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(AppIcons.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }

            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = shape16
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(AppIcons.ArrowForward, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun LibraryOverviewStep(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    WalkthroughStep(
        icon = AppIcons.Library,
        title = "Your Library",
        description = "This is where all your media lives. Add audiobooks, e-books, music, movies, and comics to build your personal collection.",
        features = listOf(
            "Audiobooks" to "Listen to your favorite books with chapter navigation",
            "E-books" to "Read EPUB and PDF files with customizable fonts",
            "Music" to "Play your music library with playlists",
            "Movies" to "Watch videos with subtitle support",
            "Comics" to "Read CBZ/CBR comic archives"
        ),
        onNext = onNext,
        onBack = onBack
    )
}

@Composable
private fun ProfileOverviewStep(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    WalkthroughStep(
        icon = AppIcons.Person,
        title = "Your Profile",
        description = "Personalize your experience. Each profile has its own theme, audio settings, and reading preferences.",
        features = listOf(
            "Multiple Profiles" to "Create profiles for family members",
            "Custom Theme" to "Choose from 20+ color themes",
            "Audio Settings" to "Equalizer, bass boost, and volume control",
            "Reading Prefs" to "Font size, line spacing, and margins"
        ),
        onNext = onNext,
        onBack = onBack
    )
}

@Composable
private fun SettingsOverviewStep(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    WalkthroughStep(
        icon = AppIcons.Settings,
        title = "Settings",
        description = "Fine-tune your app experience with playback controls, display options, and more.",
        features = listOf(
            "Playback" to "Skip intervals, sleep timer, auto-play",
            "Audio" to "Fade effects, gapless playback, mono audio",
            "Display" to "Dark mode, square corners, font size",
            "Storage" to "Manage your media and playlists"
        ),
        onNext = onNext,
        onBack = onBack
    )
}

@Composable
private fun WalkthroughStep(
    icon: ImageVector,
    title: String,
    description: String,
    features: List<Pair<String, String>>,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val palette = currentPalette()
    val shape16 = cornerRadius(16.dp)
    val shape12 = cornerRadius(12.dp)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Adjust sizes for landscape
    val iconSize = if (isLandscape) 56.dp else 80.dp
    val iconInnerSize = if (isLandscape) 28.dp else 40.dp
    val topSpacing = if (isLandscape) 16.dp else 48.dp
    val cardPadding = if (isLandscape) 12.dp else 16.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = topSpacing, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon in circle
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconInnerSize),
                    tint = palette.accent
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))

            Text(
                text = title,
                style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

            // Feature list - use grid for landscape
            if (isLandscape && features.size > 2) {
                // Two-column grid for landscape
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    features.chunked(2).forEach { rowFeatures ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFeatures.forEach { (featureTitle, featureDesc) ->
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = shape12,
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(cardPadding),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(palette.accent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = featureTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = featureDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                            // Fill empty space if odd number
                            if (rowFeatures.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // Single column for portrait
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    features.forEach { (featureTitle, featureDesc) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape12,
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(cardPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(palette.accent)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = featureTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = featureDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Navigation buttons fixed at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = if (isLandscape) 16.dp else 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(AppIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }

            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(AppIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun GettingStartedStep(
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val palette = currentPalette()
    val shape16 = cornerRadius(16.dp)
    val shape12 = cornerRadius(12.dp)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val topSpacing = if (isLandscape) 16.dp else 48.dp
    val cardPadding = if (isLandscape) 12.dp else 16.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = topSpacing, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 56.dp else 80.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 28.dp else 40.dp),
                    tint = palette.accent
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))

            Text(
                text = "Getting Started",
                style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

            Text(
                text = "Adding your media is simple!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

            // Instructions
            val instructions = listOf(
                Triple(AppIcons.Folder, "Find Your Folder", "Each profile has its own Librio folder in your device storage"),
                Triple(AppIcons.Add, "Drop Your Files", "Copy audiobooks, e-books, music, movies, or comics into the folder"),
                Triple(AppIcons.Refresh, "Auto Updates", "The app automatically detects new files and updates your library"),
                Triple(AppIcons.Person, "Per-Profile Data", "All content, progress, and settings are saved separately for each profile")
            )

            if (isLandscape) {
                // Two-column grid for landscape
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    instructions.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (icon, title, desc) ->
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = shape12,
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(cardPadding),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(palette.accent.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // Single column for portrait
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    instructions.forEach { (icon, title, desc) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape12,
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(cardPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(palette.accent.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Navigation buttons fixed at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = if (isLandscape) 16.dp else 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(AppIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }

            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16
            ) {
                Text("Next")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(AppIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SwipeGestureStep(
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val palette = currentPalette()
    val shape16 = cornerRadius(16.dp)
    val shape12 = cornerRadius(12.dp)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Animation for horizontal swipe hint
    val infiniteTransition = rememberInfiniteTransition(label = "swipe")
    val horizontalSwipeOffset by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "horizontal_swipe_offset"
    )

    // Animation for vertical swipe hint (offset timing)
    val verticalSwipeOffset by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic, delayMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vertical_swipe_offset"
    )

    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_alpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = if (isLandscape) 16.dp else 32.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Swipe Gestures",
                style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Quick navigation with simple gestures",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 16.dp))

            // Horizontal swipe section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = shape12,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(if (isLandscape) 12.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Horizontal arrows animation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 48.dp else 64.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.ChevronLeft,
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (isLandscape) 32.dp else 40.dp)
                                .alpha(arrowAlpha),
                            tint = palette.accent
                        )
                        Row(
                            modifier = Modifier.graphicsLayer { translationX = horizontalSwipeOffset },
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(AppIcons.Audiobook, AppIcons.Book, AppIcons.Music, AppIcons.Movie).forEach { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp),
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (isLandscape) 32.dp else 40.dp)
                                .alpha(arrowAlpha),
                            tint = palette.accent
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Swipe Left / Right",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent
                        )
                        Box(
                            modifier = Modifier
                                .clip(cornerRadius(4.dp))
                                .background(palette.accent.copy(alpha = 0.3f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "1 Finger",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(
                        text = "Switch between categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vertical swipe section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = shape12,
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(if (isLandscape) 12.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Vertical arrows animation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isLandscape) 48.dp else 64.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = AppIcons.ExpandLess,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(if (isLandscape) 28.dp else 32.dp)
                                    .alpha(arrowAlpha),
                                tint = palette.accent
                            )
                            Column(
                                modifier = Modifier.graphicsLayer { translationY = verticalSwipeOffset },
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                listOf("All", "Playlist 1", "Playlist 2").forEach { label ->
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = AppIcons.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(if (isLandscape) 28.dp else 32.dp)
                                    .alpha(arrowAlpha),
                                tint = palette.accent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Swipe Up / Down",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent
                        )
                        Box(
                            modifier = Modifier
                                .clip(cornerRadius(4.dp))
                                .background(palette.accent.copy(alpha = 0.3f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "2 Fingers",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(
                        text = "Switch between playlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 20.dp))

            Text(
                text = "2-finger vertical swipes won't\ninterfere with normal scrolling",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }

        // Navigation buttons fixed at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = if (isLandscape) 16.dp else 60.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(AppIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }

            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(if (isLandscape) 44.dp else 52.dp),
                shape = shape16
            ) {
                Text("Get Started")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FeatureChip(
    icon: ImageVector,
    label: String
) {
    val palette = currentPalette()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}
