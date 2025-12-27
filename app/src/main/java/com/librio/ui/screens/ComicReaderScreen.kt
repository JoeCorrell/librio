package com.librio.ui.screens

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.librio.ui.theme.cornerRadius
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.R
import com.librio.model.LibraryComic
import com.librio.navigation.BottomNavItem
import com.librio.ui.theme.*
import com.librio.ui.theme.AppIcons
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    comic: LibraryComic,
    onBack: () -> Unit,
    onPageChange: (currentPage: Int, totalPages: Int) -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    showBackButton: Boolean = true,
    showSearchBar: Boolean = true,
    headerTitle: String = "Librio",
    // Persisted comic reader settings
    darkMode: Boolean = false,
    forceTwoPageMode: Boolean = false,
    onForceTwoPageModeChange: (Boolean) -> Unit = {},
    forceSinglePageMode: Boolean = false,
    onForceSinglePageModeChange: (Boolean) -> Unit = {},
    readingDirection: String = "ltr",
    onReadingDirectionChange: (String) -> Unit = {},
    pageFitMode: String = "fit",
    onPageFitModeChange: (String) -> Unit = {},
    pageGap: Int = 4,
    onPageGapChange: (Int) -> Unit = {},
    showPageIndicators: Boolean = true,
    onShowPageIndicatorsChange: (Boolean) -> Unit = {},
    enableDoubleTapZoom: Boolean = true,
    onEnableDoubleTapZoomChange: (Boolean) -> Unit = {},
    showControlsOnTap: Boolean = true,
    onShowControlsOnTapChange: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = true,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape2 = cornerRadius(2.dp)
    val shape8 = cornerRadius(8.dp)
    val shape10 = cornerRadius(10.dp)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    // Determine if we should show 2-page spread (tablets/landscape)
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLargeScreen = screenWidth >= 600
    val isLandscape = screenWidth > screenHeight
    val showTwoPages = isLargeScreen || isLandscape

    // Comic pages
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(comic.currentPage) }
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf<BottomNavItem?>(null) }

    // Track if initial page position has been restored (prevents saving page 0 on load)
    var initialPositionRestored by remember { mutableStateOf(false) }
    // Track the target page for restoration - starts with saved value, updated on page changes
    var targetRestorePage by remember { mutableIntStateOf(comic.currentPage) }

    // Effective 2-page mode based on settings
    val effectiveTwoPageMode = when {
        forceSinglePageMode -> false
        forceTwoPageMode -> true
        else -> showTwoPages
    }

    // Background color - follows theme
    val bgColor = palette.background

    // Keep screen on based on setting
    DisposableEffect(keepScreenOn) {
        val window = (context as? Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Calculate page pairs for 2-page mode
    val pagePairs = remember(pages, effectiveTwoPageMode) {
        if (effectiveTwoPageMode && pages.isNotEmpty()) {
            // First page alone, then pairs
            val pairs = mutableListOf<List<Bitmap>>()
            pairs.add(listOf(pages[0])) // Cover page alone
            for (i in 1 until pages.size step 2) {
                if (i + 1 < pages.size) {
                    pairs.add(listOf(pages[i], pages[i + 1]))
                } else {
                    pairs.add(listOf(pages[i]))
                }
            }
            pairs
        } else {
            pages.map { listOf(it) }
        }
    }

    val totalDisplayPages = pagePairs.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = if (effectiveTwoPageMode && currentPage > 0) {
            // Calculate which pair contains the current page
            ((currentPage + 1) / 2).coerceIn(0, (totalDisplayPages - 1).coerceAtLeast(0))
        } else {
            currentPage.coerceIn(0, (totalDisplayPages - 1).coerceAtLeast(0))
        },
        pageCount = { totalDisplayPages }
    )

    // Load comic pages
    LaunchedEffect(comic.uri) {
        isLoading = true
        pages = loadComicPages(context, comic.uri, comic.fileType)
        isLoading = false
    }

    // Restore position after pages load (initial load only)
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty() && !initialPositionRestored) {
            // Use targetRestorePage which tracks current position (not just initial)
            val targetPage = targetRestorePage.coerceIn(0, pages.size - 1)
            val targetPagerIndex = if (effectiveTwoPageMode) {
                if (targetPage == 0) 0 else ((targetPage + 1) / 2)
            } else {
                targetPage
            }
            // Scroll to position without animation
            pagerState.scrollToPage(targetPagerIndex.coerceIn(0, (pagePairs.size - 1).coerceAtLeast(0)))
            currentPage = targetPage
            initialPositionRestored = true
            // Save the restored position with total pages
            onPageChange(currentPage, pages.size)
        }
    }

    // Restore position when layout mode changes (after initial load)
    LaunchedEffect(effectiveTwoPageMode) {
        if (pages.isNotEmpty() && initialPositionRestored) {
            // When layout changes, restore to the current page position
            val targetPage = currentPage.coerceIn(0, pages.size - 1)
            val targetPagerIndex = if (effectiveTwoPageMode) {
                if (targetPage == 0) 0 else ((targetPage + 1) / 2)
            } else {
                targetPage
            }
            // Scroll to position without animation
            pagerState.scrollToPage(targetPagerIndex.coerceIn(0, (pagePairs.size - 1).coerceAtLeast(0)))
        }
    }

    // Track page changes - only after initial position restored
    LaunchedEffect(pagerState.currentPage, effectiveTwoPageMode) {
        if (pages.isNotEmpty() && initialPositionRestored) {
            val actualPage = if (effectiveTwoPageMode) {
                // Calculate actual page number from pair index
                if (pagerState.currentPage == 0) 0
                else (pagerState.currentPage * 2) - 1
            } else {
                pagerState.currentPage
            }
            currentPage = actualPage.coerceIn(0, pages.size - 1)
            // Update targetRestorePage so settings changes restore to current position
            targetRestorePage = currentPage
            onPageChange(currentPage, pages.size)
        }
    }

    // Track total pages for progress saving
    var totalPages by remember { mutableIntStateOf(0) }
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) {
            totalPages = pages.size
        }
    }

    // Keep track of latest values for saving on dispose
    val currentPageState = rememberUpdatedState(currentPage)
    val totalPagesState = rememberUpdatedState(pages.size)
    val onPageChangeState = rememberUpdatedState(onPageChange)

    // Save progress when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            // Always save current page and total pages when leaving
            onPageChangeState.value(currentPageState.value, totalPagesState.value)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = palette.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Loading comic...",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (pages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        AppIcons.Book,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = palette.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Unable to load comic",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This file format may not be supported",
                        color = palette.textMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            // Comic pages with horizontal paging
            val handlePageTap = {
                if (showControlsOnTap) {
                    showSettings = false
                    showControls = !showControls
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp, bottom = 85.dp),
                beyondBoundsPageCount = 1
            ) { pageIndex ->
                val pagePair = pagePairs.getOrNull(pageIndex) ?: emptyList()

                if (effectiveTwoPageMode && pagePair.size == 2) {
                    // Two-page spread
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(pageGap.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left page (or right if RTL)
                        val leftPage = if (readingDirection == "rtl") pagePair[1] else pagePair[0]
                        val rightPage = if (readingDirection == "rtl") pagePair[0] else pagePair[1]

                        ComicPage(
                            bitmap = leftPage,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fitMode = pageFitMode,
                            onTap = handlePageTap,
                            enableDoubleTapZoom = enableDoubleTapZoom
                        )
                        ComicPage(
                            bitmap = rightPage,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fitMode = pageFitMode,
                            onTap = handlePageTap,
                            enableDoubleTapZoom = enableDoubleTapZoom
                        )
                    }
                } else if (pagePair.isNotEmpty()) {
                    // Single page
                    ComicPage(
                        bitmap = pagePair[0],
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        fitMode = pageFitMode,
                        onTap = handlePageTap,
                        enableDoubleTapZoom = enableDoubleTapZoom
                    )
                }
            }

            // Page indicator dots (for small number of pages)
            if (showPageIndicators && totalDisplayPages <= 20) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 90.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(totalDisplayPages) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (pagerState.currentPage == index) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index) palette.primary
                                    else palette.textMuted.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
        }

        val headerContentHeight = 40.dp

        // Header bar matching Library style
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(palette.headerGradient())
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .height(headerContentHeight),
                contentAlignment = Alignment.Center
            ) {
                // Back button
                if (showBackButton) {
                    val backInteractionSource = remember { MutableInteractionSource() }
                    val backIsPressed by backInteractionSource.collectIsPressedAsState()
                    val backScale by animateFloatAsState(
                        targetValue = if (backIsPressed) 0.85f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "backScale"
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .scale(backScale),
                        interactionSource = backInteractionSource
                    ) {
                        Icon(
                            AppIcons.Back,
                            contentDescription = "Back",
                            tint = palette.shade11
                        )
                    }
                }

                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp
                    ),
                    color = palette.shade11,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Search/settings toggle button
                if (showSearchBar) {
                    val searchInteractionSource = remember { MutableInteractionSource() }
                    val searchIsPressed by searchInteractionSource.collectIsPressedAsState()
                    val searchScale by animateFloatAsState(
                        targetValue = if (searchIsPressed) 0.85f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "searchScale"
                    )
                    IconButton(
                        onClick = { },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .scale(searchScale),
                        interactionSource = searchInteractionSource
                    ) {
                        Icon(
                            AppIcons.Search,
                            contentDescription = "Search",
                            tint = palette.shade11
                        )
                    }
                } else {
                    Spacer(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(24.dp)
                    )
                }
            }
        }

        // Bottom controls with page info
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = palette.headerBackground,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Progress slider
                    val progress = if (pages.size > 1) {
                        currentPage.toFloat() / (pages.size - 1)
                    } else 0f

                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            val newPage = (newProgress * (pages.size - 1)).toInt().coerceIn(0, pages.size - 1)
                            scope.launch {
                                val pagerIndex = if (effectiveTwoPageMode) {
                                    if (newPage == 0) 0 else ((newPage + 1) / 2)
                                } else newPage
                                pagerState.animateScrollToPage(pagerIndex.coerceIn(0, totalDisplayPages - 1))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accent,
                            activeTrackColor = palette.accent,
                            inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page ${currentPage + 1} of ${pages.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textMuted
                        )

                        if (effectiveTwoPageMode) {
                            Text(
                                text = "2-page spread",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                        }

                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                    }
                }
            }
        }

        // Bottom navigation bar styled like the Library screen
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .background(palette.headerGradient())
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    BottomNavItem.LIBRARY to {
                        showSettings = false
                        // Don't highlight - navigating away
                        onNavigateToLibrary()
                    },
                    BottomNavItem.PROFILE to {
                        showSettings = false
                        // Don't highlight - navigating away
                        onNavigateToProfile()
                    },
                    BottomNavItem.SETTINGS to {
                        val toggled = !showSettings
                        showSettings = toggled
                        selectedNavItem = if (toggled) BottomNavItem.SETTINGS else null
                    }
                ).forEach { (item, action) ->
                    val isSelected = selectedNavItem == item
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val scale by animateFloatAsState(
                        targetValue = when {
                            isPressed -> 0.85f
                            isSelected -> 1.1f
                            else -> 1f
                        },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        ),
                        label = "navScale"
                    )

                    val offsetY by animateFloatAsState(
                        targetValue = if (isSelected) -4f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "navOffset"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scale)
                            .offset(y = offsetY.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { action() }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title,
                                modifier = Modifier.size(24.dp),
                                tint = if (isSelected) palette.shade12 else palette.shade11.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.title,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp,
                                color = if (isSelected) palette.shade12 else palette.shade11.copy(alpha = 0.7f)
                            )
                        }
                    }
            }
        }

        // Settings panel overlay - mirrored from e-reader styling
        val settingsPadding = if (screenWidth < 400) 12.dp else 16.dp
        val settingsIconSize = if (screenWidth < 400) 14.dp else 16.dp
        val settingsButtonHeight = if (screenWidth < 400) 32.dp else 36.dp
        val settingsFontSize = if (screenWidth < 400) 10.sp else 12.sp
        val settingsMaxHeight = (screenHeight * 0.6f).dp

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = palette.surface,
                shape = cornerRadiusTop(16.dp),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = settingsMaxHeight)
                    .padding(bottom = 80.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = settingsPadding, vertical = 10.dp)
                ) {
                    // Handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(shape2)
                                .background(palette.textMuted.copy(alpha = 0.3f))
                        )
                    }

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Settings,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(if (screenWidth < 400) 18.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Comic Settings",
                                style = if (screenWidth < 400) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary
                            )
                        }
                        IconButton(
                            onClick = { showSettings = false; selectedNavItem = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                AppIcons.Close,
                                contentDescription = "Close",
                                tint = palette.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Display Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.Palette,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Display",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Page dots", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                                Switch(
                                    checked = showPageIndicators,
                                    onCheckedChange = onShowPageIndicatorsChange
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Layout Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.ViewArray,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Layout",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Page fit mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(settingsButtonHeight)
                                    .clip(shape8)
                                    .background(palette.background),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("fit" to "Fit", "width" to "Width", "height" to "Height").forEach { (mode, label) ->
                                    val isSelected = pageFitMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape8)
                                            .background(if (isSelected) palette.accent else Color.Transparent)
                                            .clickable { onPageFitModeChange(mode) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = settingsFontSize,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) palette.onPrimary else palette.textPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Reading Direction
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(settingsButtonHeight)
                                    .clip(shape8)
                                    .background(palette.background),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("ltr" to "Left to Right", "rtl" to "Right to Left").forEach { (dir, label) ->
                                    val isSelected = readingDirection == dir
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape8)
                                            .background(if (isSelected) palette.accent else Color.Transparent)
                                            .clickable { onReadingDirectionChange(dir) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = settingsFontSize,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) palette.onPrimary else palette.textPrimary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Page Layout
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(settingsButtonHeight)
                                    .clip(shape8)
                                    .background(palette.background),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(
                                    Triple(false to false, "Auto", if (showTwoPages) "2pg" else "1pg"),
                                    Triple(true to false, "Single", "1pg"),
                                    Triple(false to true, "Spread", "2pg")
                                ).forEach { (mode, label, _) ->
                                    val (single, two) = mode
                                    val isSelected = (forceSinglePageMode == single && forceTwoPageMode == two && !single && !two) ||
                                                    (forceSinglePageMode == single && single) ||
                                                    (forceTwoPageMode == two && two)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape8)
                                            .background(if (isSelected) palette.accent else Color.Transparent)
                                            .clickable {
                                                onForceSinglePageModeChange(single)
                                                onForceTwoPageModeChange(two)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = settingsFontSize,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) palette.onPrimary else palette.textPrimary
                                        )
                                    }
                                }
                            }

                            if (effectiveTwoPageMode) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gap", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                                    Text("${pageGap}dp", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                                }
                                Slider(
                                    value = pageGap.toFloat(),
                                    onValueChange = { newValue: Float -> onPageGapChange(newValue.toInt()) },
                                    valueRange = 0f..16f,
                                    steps = 7,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(20.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.accent,
                                        activeTrackColor = palette.accent,
                                        inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Interaction Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.Tune,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Interaction",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Double-tap zoom", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                                Switch(
                                    checked = enableDoubleTapZoom,
                                    onCheckedChange = onEnableDoubleTapZoomChange
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tap toggles controls", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                                Switch(
                                    checked = showControlsOnTap,
                                    onCheckedChange = onShowControlsOnTapChange
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Keep screen on", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = onKeepScreenOnChange
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Current progress info
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppIcons.Book,
                                    contentDescription = null,
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Progress",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary
                                )
                            }
                            Text(
                                text = "Page ${currentPage + 1} of ${pages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.accent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicPage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    fitMode: String = "fit",
    onTap: () -> Unit = {},
    enableDoubleTapZoom: Boolean = true
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val maxScale = 2f

    fun clampOffset(target: Offset, targetScale: Float): Offset {
        if (containerSize == IntSize.Zero) return Offset.Zero
        val maxX = (containerSize.width * (targetScale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (targetScale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            x = target.x.coerceIn(-maxX, maxX),
            y = target.y.coerceIn(-maxY, maxY)
        )
    }

    LaunchedEffect(enableDoubleTapZoom) {
        if (!enableDoubleTapZoom && scale > 1f) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    val contentScale = when (fitMode) {
        "width" -> ContentScale.FillWidth
        "height" -> ContentScale.FillHeight
        else -> ContentScale.Fit
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        if (enableDoubleTapZoom) {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = maxScale
                                if (containerSize != IntSize.Zero) {
                                    val center = Offset(
                                        containerSize.width / 2f,
                                        containerSize.height / 2f
                                    )
                                    val delta = tapOffset - center
                                    val targetOffset = Offset(
                                        x = -delta.x * (targetScale - 1f),
                                        y = -delta.y * (targetScale - 1f)
                                    )
                                    offset = clampOffset(targetOffset, targetScale)
                                }
                                scale = targetScale
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Comic page",
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .pointerInput(scale, enableDoubleTapZoom, containerSize) {
                    if (enableDoubleTapZoom && scale > 1f) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset = clampOffset(
                                target = offset + dragAmount,
                                targetScale = scale
                            )
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = contentScale
        )
    }
}

/**
 * Load comic pages from CBZ/CBR file (which are ZIP/RAR files containing images)
 */
private suspend fun loadComicPages(
    context: Context,
    uri: Uri,
    fileType: String
): List<Bitmap> = withContext(Dispatchers.IO) {
    val pages = mutableListOf<Pair<String, Bitmap>>()

    try {
        when (fileType.lowercase()) {
            "cbz", "zip" -> {
                // CBZ is a ZIP file containing images
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipStream ->
                        var entry = zipStream.nextEntry
                        while (entry != null) {
                            val name = entry.name.lowercase()
                            // Check for image files
                            if (!entry.isDirectory && (
                                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".gif") ||
                                name.endsWith(".webp") || name.endsWith(".bmp"))) {
                                try {
                                    val buffer = ByteArrayOutputStream()
                                    val data = ByteArray(4096)
                                    var bytesRead: Int
                                    while (zipStream.read(data).also { bytesRead = it } != -1) {
                                        buffer.write(data, 0, bytesRead)
                                    }
                                    val imageBytes = buffer.toByteArray()

                                    // Decode bitmap with appropriate scaling
                                    val options = BitmapFactory.Options().apply {
                                        // First, get dimensions only
                                        inJustDecodeBounds = true
                                    }
                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                                    // Calculate sample size for memory efficiency
                                    val maxDimension = 2048
                                    var sampleSize = 1
                                    if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                                        val heightRatio = (options.outHeight.toFloat() / maxDimension).toInt()
                                        val widthRatio = (options.outWidth.toFloat() / maxDimension).toInt()
                                        sampleSize = if (heightRatio > widthRatio) heightRatio else widthRatio
                                    }

                                    // Decode with sample size
                                    options.inJustDecodeBounds = false
                                    options.inSampleSize = sampleSize

                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)?.let { bitmap ->
                                        pages.add(entry.name to bitmap)
                                    }
                                } catch (e: Exception) {
                                    // Skip problematic images
                                }
                            }
                            zipStream.closeEntry()
                            entry = zipStream.nextEntry
                        }
                    }
                }
            }
            "cbr", "rar" -> {
                // CBR is a RAR file - use junrar library
                var tempFile: File? = null
                try {
                    // Copy URI content to a temp file (junrar needs a File)
                    tempFile = File.createTempFile("comic_cbr", ".rar", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Extract images from RAR
                    Archive(tempFile).use { archive ->
                        var currentHeader: FileHeader? = archive.nextFileHeader()
                        while (currentHeader != null) {
                            val header = currentHeader
                            val name = header.fileName.lowercase()
                            // Check for image files
                            if (!header.isDirectory && (
                                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                name.endsWith(".png") || name.endsWith(".gif") ||
                                name.endsWith(".webp") || name.endsWith(".bmp"))) {
                                try {
                                    val buffer = ByteArrayOutputStream()
                                    archive.extractFile(header, buffer)
                                    val imageBytes = buffer.toByteArray()

                                    // Decode bitmap with appropriate scaling
                                    val options = BitmapFactory.Options().apply {
                                        inJustDecodeBounds = true
                                    }
                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                                    // Calculate sample size for memory efficiency
                                    val maxDimension = 2048
                                    var sampleSize = 1
                                    if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                                        val heightRatio = (options.outHeight.toFloat() / maxDimension).toInt()
                                        val widthRatio = (options.outWidth.toFloat() / maxDimension).toInt()
                                        sampleSize = if (heightRatio > widthRatio) heightRatio else widthRatio
                                    }

                                    // Decode with sample size
                                    options.inJustDecodeBounds = false
                                    options.inSampleSize = sampleSize

                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)?.let { bitmap ->
                                        pages.add(header.fileName to bitmap)
                                    }
                                } catch (e: Exception) {
                                    // Skip problematic images
                                }
                            }
                            currentHeader = archive.nextFileHeader()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    tempFile?.delete()
                }
            }
            "pdf" -> {
                // PDF comics - delegate to PDF loader
                val pdfPages = loadPdfAsComicPages(context, uri)
                return@withContext pdfPages
            }
            else -> {
                return@withContext emptyList()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext emptyList()
    }

    // Sort pages by filename (comics are usually numbered)
    pages.sortedBy { it.first.lowercase() }.map { it.second }
}

/**
 * Load PDF pages as comic pages
 */
private fun loadPdfAsComicPages(context: Context, uri: Uri): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    var pdfRenderer: android.graphics.pdf.PdfRenderer? = null
    var fileDescriptor: android.os.ParcelFileDescriptor? = null
    var tempFile: java.io.File? = null

    try {
        tempFile = java.io.File.createTempFile("pdf_comic", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        fileDescriptor = android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)

        val pageCount = pdfRenderer.pageCount

        for (i in 0 until pageCount) {
            pdfRenderer.openPage(i).use { page ->
                val scale = 2.0f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(
                    bitmap,
                    null,
                    null,
                    android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                pages.add(bitmap)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            tempFile?.delete()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    return pages
}
