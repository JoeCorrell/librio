package com.librio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.R
import com.librio.model.LibraryAudiobook
import com.librio.model.SortOption
import com.librio.ui.components.rememberResponsiveDimens
import com.librio.ui.theme.*
import com.librio.ui.theme.AppIcons
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    audiobooks: List<LibraryAudiobook>,
    onAddAudiobook: (Uri) -> Unit,
    onSelectAudiobook: (LibraryAudiobook) -> Unit,
    onDeleteAudiobook: (LibraryAudiobook) -> Unit,
    onOpenSettings: () -> Unit = {},
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isSearchVisible: Boolean = false,
    onToggleSearch: () -> Unit = {},
    sortOption: SortOption = SortOption.RECENTLY_ADDED,
    onSortOptionChange: (SortOption) -> Unit = {},
    libraryOwnerName: String = "",
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dimens = rememberResponsiveDimens()
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<LibraryAudiobook?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onAddAudiobook(it) }
    }

    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }

    // Delete confirmation dialog
    showDeleteDialog?.let { audiobook ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Remove Audiobook") },
            text = { Text("Remove \"${audiobook.title}\" from your library? The file will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAudiobook(audiobook)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val palette = currentPalette()
    // Remember shapes for performance
    val shape12 = cornerRadius(12.dp)
    val shape16 = cornerRadius(16.dp)
    val shape28 = cornerRadius(28.dp)
    val shape35 = cornerRadius(35.dp)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isCompact = screenWidth < 400

    // Responsive sizes
    val logoSize = if (isCompact) 36.dp else 44.dp
    val titleSize = if (isCompact) 18.sp else (dimens.titleTextSize + 4).sp
    val iconButtonSize = if (isCompact) 36.dp else 40.dp
    val addButtonSize = if (isCompact) 40.dp else 48.dp

    // Display library name - use custom name or default
    val displayLibraryName = if (libraryOwnerName.isNotBlank()) {
        "${libraryOwnerName}'s Library"
    } else {
        "My Library"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.surfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top navbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimens.horizontalPadding,
                        vertical = if (isCompact) 12.dp else 16.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Logo and title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // App logo - no border, more visible
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Librio",
                            modifier = Modifier.size(logoSize)
                        )

                        Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 12.dp))

                        Column {
                            Text(
                                text = displayLibraryName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = titleSize
                                ),
                                fontWeight = FontWeight.Bold,
                                color = palette.primary,
                                maxLines = 1
                            )
                            Text(
                                text = "${audiobooks.size} audiobook${if (audiobooks.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = if (isCompact) 11.sp else 12.sp
                                ),
                                color = palette.primary.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Right side: Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 0.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search button
                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = if (isSearchVisible) AppIcons.Close else AppIcons.Search,
                                contentDescription = "Search",
                                tint = palette.primaryLight,
                                modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
                            )
                        }

                        // Sort button
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(iconButtonSize)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Sort,
                                    contentDescription = "Sort",
                                    tint = palette.accent,
                                    modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(option.displayName)
                                                if (option == sortOption) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        AppIcons.Check,
                                                        contentDescription = null,
                                                        tint = palette.accent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onSortOptionChange(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Settings button
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = AppIcons.Settings,
                                contentDescription = "Settings",
                                tint = palette.primaryLight,
                                modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Add button
                        FloatingActionButton(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*", "application/octet-stream")) },
                            containerColor = palette.accent,
                            contentColor = palette.surfaceDark,
                            modifier = Modifier.size(addButtonSize)
                        ) {
                            Icon(
                                AppIcons.Add,
                                contentDescription = "Add audiobook",
                                modifier = Modifier.size(if (isCompact) 20.dp else 24.dp)
                            )
                        }
                    }
                }
            }

            // Search bar
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.horizontalPadding, vertical = 12.dp)
                        .background(palette.surfaceCard, shape12)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            textStyle = TextStyle(
                                color = palette.textPrimary,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(palette.accent),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search audiobooks...",
                                            color = palette.primary.copy(alpha = 0.5f),
                                            fontSize = 16.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    AppIcons.Clear,
                                    contentDescription = "Clear",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (audiobooks.isEmpty()) {
                EmptyLibraryState(
                    onAddClick = { audioPickerLauncher.launch(arrayOf("audio/*", "application/octet-stream")) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Recently played section
                val recentlyPlayed = audiobooks.filter { it.lastPlayed > 0 && !it.isCompleted }
                    .sortedByDescending { it.lastPlayed }
                    .take(5)

                if (recentlyPlayed.isNotEmpty()) {
                    Text(
                        text = "Continue Listening",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = dimens.titleTextSize.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryLight,
                        modifier = Modifier.padding(horizontal = dimens.horizontalPadding)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Horizontal carousel
                    Box(modifier = Modifier.height(dimens.carouselHeight)) {
                        LazyRow(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = dimens.horizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(recentlyPlayed, key = { it.id }) { audiobook ->
                                AudiobookCard(
                                    audiobook = audiobook,
                                    onClick = { onSelectAudiobook(audiobook) },
                                    onLongClick = { showDeleteDialog = audiobook },
                                    cardWidth = dimens.cardWidth,
                                    showProgress = true,
                                    showPlaceholderIcons = showPlaceholderIcons
                                )
                            }
                        }

                        // Scroll arrows
                        if (canScrollBackward) {
                            ScrollArrowButton(
                                direction = ScrollDirection.LEFT,
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            (listState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                                        )
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                            )
                        }
                        if (canScrollForward) {
                            ScrollArrowButton(
                                direction = ScrollDirection.RIGHT,
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(
                                            (listState.firstVisibleItemIndex + 2).coerceAtMost(recentlyPlayed.size - 1)
                                        )
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // All audiobooks grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Audiobooks",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = dimens.titleTextSize.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryLight
                    )
                    Text(
                        text = sortOption.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(dimens.columns),
                    contentPadding = PaddingValues(
                        horizontal = dimens.horizontalPadding,
                        vertical = 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(audiobooks, key = { it.id }) { audiobook ->
                        AudiobookGridItem(
                            audiobook = audiobook,
                            onClick = { onSelectAudiobook(audiobook) },
                            onLongClick = { showDeleteDialog = audiobook },
                            showPlaceholderIcons = showPlaceholderIcons
                        )
                    }
                }
            }
        }
    }
}

enum class ScrollDirection { LEFT, RIGHT }

@Composable
private fun ScrollArrowButton(
    direction: ScrollDirection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowOffset"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .shadow(6.dp, CircleShape)
            .background(palette.surfaceCard.copy(alpha = 0.95f), CircleShape)
            .offset(x = if (direction == ScrollDirection.LEFT) -arrowOffset.dp else arrowOffset.dp)
    ) {
        Icon(
            imageVector = if (direction == ScrollDirection.LEFT)
                AppIcons.ChevronLeft else AppIcons.ChevronRight,
            contentDescription = if (direction == ScrollDirection.LEFT) "Scroll left" else "Scroll right",
            tint = palette.primaryLight,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun AudiobookCard(
    audiobook: LibraryAudiobook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    showProgress: Boolean = false,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    // Remember shapes for performance
    val shape12 = cornerRadius(12.dp)
    val shape16 = cornerRadius(16.dp)

    var isPressed by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 10000f),
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .width(cardWidth)
            .scale(cardScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onClickLabel = "Play ${audiobook.title}"
            ),
        shape = shape16,
        colors = CardDefaults.cardColors(containerColor = palette.surfaceCard)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape12)
                    .background(palette.coverArtGradient()),
                contentAlignment = Alignment.Center
            ) {
                val usePlaceholder = showPlaceholderIcons || audiobook.coverArt == null
                if (usePlaceholder) {
                    Icon(
                        imageVector = AppIcons.Audiobook,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = palette.shade7.copy(alpha = 0.95f)
                    )
                } else {
                    Image(
                        bitmap = audiobook.coverArt!!.asImageBitmap(),
                        contentDescription = audiobook.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (showProgress && audiobook.progress > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(palette.shade6)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(audiobook.progress)
                                .fillMaxHeight()
                                .background(palette.progressGradient())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = audiobook.author,
                style = MaterialTheme.typography.bodySmall,
                color = palette.primary.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (audiobook.duration > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (audiobook.progress > 0) "${audiobook.formattedRemaining} left" else audiobook.formattedDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun AudiobookGridItem(
    audiobook: LibraryAudiobook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    // Remember shape for performance
    val shape12 = cornerRadius(12.dp)

    var isPressed by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 10000f),
        label = "cardScale"
    )

    Column(
        modifier = modifier
            .scale(cardScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(8.dp, shape12)
                .clip(shape12)
                .background(palette.coverArtGradient()),
            contentAlignment = Alignment.Center
        ) {
            val usePlaceholder = showPlaceholderIcons || audiobook.coverArt == null
            if (usePlaceholder) {
                Icon(
                    imageVector = AppIcons.Audiobook,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = palette.shade7.copy(alpha = 0.95f)
                )
            } else {
                Image(
                    bitmap = audiobook.coverArt!!.asImageBitmap(),
                    contentDescription = audiobook.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Progress indicator
            if (audiobook.progress > 0 && !audiobook.isCompleted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(palette.shade6)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(audiobook.progress)
                            .fillMaxHeight()
                            .background(palette.progressGradient())
                    )
                }
            }

            // Completed badge
            if (audiobook.isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(palette.accentGradient(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        AppIcons.Check,
                        contentDescription = "Completed",
                        tint = palette.surfaceDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = audiobook.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = palette.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        Text(
            text = audiobook.author,
            style = MaterialTheme.typography.labelSmall,
            color = palette.primary.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyLibraryState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val dimens = rememberResponsiveDimens()
    // Remember shapes for performance
    val shape28 = cornerRadius(28.dp)
    val shape35 = cornerRadius(35.dp)

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { isVisible = true }

    val iconScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, delayMillis = 100),
        label = "contentAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(dimens.splashLogoSize * 0.8f)
                .scale(iconScale)
                .offset(y = -floatOffset.dp)
                .background(
                    color = palette.primary.copy(alpha = 0.15f),
                    shape = shape35
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(dimens.splashLogoSize * 0.4f),
                tint = palette.primaryLight
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Your Library is Empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = palette.primary,
            modifier = Modifier.graphicsLayer(alpha = contentAlpha)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Add your first audiobook to get started",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.primary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .graphicsLayer(alpha = contentAlpha)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Supports M4B, MP3, M4A, AAC, OGG,\nFLAC, WAV, OPUS, WMA and more",
            style = MaterialTheme.typography.bodySmall,
            color = palette.primary.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer(alpha = contentAlpha)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.surfaceDark),
            shape = shape28,
            modifier = Modifier
                .height(dimens.buttonHeight)
                .graphicsLayer(alpha = contentAlpha)
        ) {
            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Add Audiobook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
