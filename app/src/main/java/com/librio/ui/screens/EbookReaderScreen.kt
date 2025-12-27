package com.librio.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.*
import com.librio.ui.theme.cornerRadius
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.R
import com.librio.model.LibraryBook
import com.librio.navigation.BottomNavItem
import com.librio.ui.theme.*
import com.librio.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Text rendering settings for e-book reader
 */
data class TextRenderSettings(
    val fontSize: Int = 18,           // 12-32
    val lineSpacing: Float = 1.4f,    // 1.0-2.5
    val marginSize: Int = 16,         // 8-48
    val fontFamily: String = "serif", // serif, sans-serif, monospace
    val textAlign: String = "left",   // left, center, justify
    val paragraphSpacing: Int = 12,   // 0-32
    val boldText: Boolean = false,
    val wordSpacing: Int = 0          // -2 to 8
)

/**
 * E-Book Reader Screen - Based on ComicReaderScreen approach for reliable progress saving
 * Supports PDF, EPUB, TXT, and image-based books
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EbookReaderScreen(
    book: LibraryBook,
    onBack: () -> Unit,
    onPageChange: (currentPage: Int, totalPages: Int) -> Unit,
    appDarkMode: Boolean = false,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    showBackButton: Boolean = true,
    showSearchBar: Boolean = true,
    headerTitle: String = "Librio",
    // Reader settings from repository (persisted)
    initialFontSize: Int = 18,
    initialLineSpacing: Float = 1.4f,
    initialFontFamily: String = "serif",
    initialTextAlign: String = "left",
    initialMargins: Int = 16,
    initialParagraphSpacing: Int = 12,
    initialBrightness: Float = 1f,
    initialBoldText: Boolean = false,
    initialWordSpacing: Int = 0,
    initialPageFitMode: String = "fit",
    initialPageGap: Int = 4,
    initialForceTwoPage: Boolean = false,
    initialForceSinglePage: Boolean = false,
    // Callbacks to save settings when changed
    onFontSizeChange: (Int) -> Unit = {},
    onLineSpacingChange: (Float) -> Unit = {},
    onFontFamilyChange: (String) -> Unit = {},
    onTextAlignChange: (String) -> Unit = {},
    onMarginsChange: (Int) -> Unit = {},
    onParagraphSpacingChange: (Int) -> Unit = {},
    onBrightnessChange: (Float) -> Unit = {},
    onBoldTextChange: (Boolean) -> Unit = {},
    onWordSpacingChange: (Int) -> Unit = {},
    onPageFitModeChange: (String) -> Unit = {},
    onPageGapChange: (Int) -> Unit = {},
    onForceTwoPageChange: (Boolean) -> Unit = {},
    onForceSinglePageChange: (Boolean) -> Unit = {},
    keepScreenOn: Boolean = true,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape2 = cornerRadius(2.dp)
    val shape8 = cornerRadius(8.dp)
    val shape10 = cornerRadius(10.dp)
    val shape12 = cornerRadius(12.dp)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    // Determine if we should show 2-page spread (tablets/landscape)
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLargeScreen = screenWidth >= 600
    val isLandscape = screenWidth > screenHeight
    val showTwoPages = isLargeScreen || isLandscape

    // Book pages (rendered as bitmaps for consistent page tracking)
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(book.currentPage) }
    var showControls by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf<BottomNavItem?>(null) }

    // Track if initial page position has been restored (prevents saving page 0 on load)
    var initialPositionRestored by remember { mutableStateOf(false) }
    // Track the target page for restoration - starts with saved value, updated on page changes
    var targetRestorePage by remember { mutableIntStateOf(book.currentPage) }

    // Reader settings - Layout (initialized from persisted values)
    var forceTwoPageMode by remember { mutableStateOf(initialForceTwoPage) }
    var forceSinglePageMode by remember { mutableStateOf(initialForceSinglePage) }
    var pageFitMode by remember { mutableStateOf(initialPageFitMode) }
    var pageGap by remember { mutableIntStateOf(initialPageGap) }

    // Reader settings - Text (initialized from persisted values)
    var fontSize by remember { mutableIntStateOf(initialFontSize) }
    var lineSpacing by remember { mutableFloatStateOf(initialLineSpacing) }
    var marginSize by remember { mutableIntStateOf(initialMargins) }
    var fontFamily by remember { mutableStateOf(initialFontFamily) }
    var textAlign by remember { mutableStateOf(initialTextAlign) }
    var paragraphSpacing by remember { mutableIntStateOf(initialParagraphSpacing) }
    var brightness by remember { mutableFloatStateOf(initialBrightness) }
    var boldText by remember { mutableStateOf(initialBoldText) }
    var wordSpacing by remember { mutableIntStateOf(initialWordSpacing) }

    // Job reference for cancelling previous page load when settings change rapidly
    var pageLoadJob by remember { mutableStateOf<Job?>(null) }
    val pageLoadMutex = remember { Mutex() }

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

    // Effective 2-page mode based on settings
    val effectiveTwoPageMode = when {
        forceSinglePageMode -> false
        forceTwoPageMode -> true
        else -> showTwoPages
    }

    // Background color - follows theme
    val bgColor = palette.background

    // Calculate page pairs for 2-page mode
    val pagePairs = remember(pages, effectiveTwoPageMode) {
        if (effectiveTwoPageMode && pages.isNotEmpty()) {
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
            ((currentPage + 1) / 2).coerceIn(0, (totalDisplayPages - 1).coerceAtLeast(0))
        } else {
            currentPage.coerceIn(0, (totalDisplayPages - 1).coerceAtLeast(0))
        },
        pageCount = { totalDisplayPages }
    )

    // Debounced text settings - only update after user stops adjusting for stability
    // This prevents constant page reloads while adjusting sliders
    var debouncedTextSettings by remember {
        mutableStateOf(
            TextRenderSettings(
                fontSize = fontSize,
                lineSpacing = lineSpacing,
                marginSize = marginSize,
                fontFamily = fontFamily,
                textAlign = textAlign,
                paragraphSpacing = paragraphSpacing,
                boldText = boldText,
                wordSpacing = wordSpacing
            )
        )
    }

    // Debounce text settings changes - prevents rapid reloads while adjusting sliders
    LaunchedEffect(fontSize, lineSpacing, marginSize, fontFamily, textAlign, paragraphSpacing, boldText, wordSpacing) {
        kotlinx.coroutines.delay(1500) // Wait 1.5s after last change to prevent lag during slider adjustments
        debouncedTextSettings = TextRenderSettings(
            fontSize = fontSize,
            lineSpacing = lineSpacing,
            marginSize = marginSize,
            fontFamily = fontFamily,
            textAlign = textAlign,
            paragraphSpacing = paragraphSpacing,
            boldText = boldText,
            wordSpacing = wordSpacing
        )
    }

    // Cleanup page load job when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            pageLoadJob?.cancel()
        }
    }

    // Use debounced settings for rendering
    val textSettings = debouncedTextSettings

    // Convert theme colors to Android graphics colors for text rendering
    val themeBgColorInt = remember(palette.background) {
        val color = palette.background
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }
    val themeTextColorInt = remember(palette.textPrimary) {
        val color = palette.textPrimary
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }

    // Check if book is text-based (needs re-rendering when text settings change)
    val isTextBasedBook = remember(book.fileType) {
        book.fileType.lowercase() in listOf("txt", "epub", "html", "htm")
    }

    // Load book pages - only reload on text settings change for text-based books
    // For PDFs and image-based books, only reload when background changes
    // Uses debounced values to prevent rapid reloads causing crashes
    // Uses Mutex to prevent race conditions from concurrent page loading
    LaunchedEffect(book.uri, if (isTextBasedBook) textSettings else Unit, themeBgColorInt, themeTextColorInt) {
        // Cancel any previous page loading job to prevent race conditions
        pageLoadJob?.cancel()

        // Create a new job for this load operation with mutex synchronization
        pageLoadJob = scope.launch {
            pageLoadMutex.withLock {
                try {
                    isLoading = true
                    // Reset position tracking when settings change (pages will be re-rendered)
                    initialPositionRestored = false

                    // Check if we should continue before the expensive operation
                    if (!isActive) return@withLock

                    val newPages = loadBookPages(context, book.uri, book.fileType, "theme", textSettings, themeBgColorInt, themeTextColorInt)

                    // Check again after the operation completes
                    if (!isActive) return@withLock

                    // Only update pages if we got valid results
                    if (newPages.isNotEmpty()) {
                        pages = newPages
                    }
                } catch (e: Exception) {
                    // Handle cancellation and other exceptions gracefully
                    if (e !is kotlinx.coroutines.CancellationException) {
                        e.printStackTrace()
                    }
                } finally {
                    if (isActive) {
                        isLoading = false
                    }
                }
            }
        }
        // NOTE: Don't call onPageChange here - wait for position to be restored first
    }

    // Track total pages for progress saving
    var totalPages by remember { mutableIntStateOf(0) }
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) {
            totalPages = pages.size
        }
    }

    // Restore position after pages load (either initial load or after settings change)
    LaunchedEffect(pages.size, effectiveTwoPageMode) {
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
            // Save the restored position
            onPageChange(currentPage, pages.size)
        }
    }

    // Track page changes - CRITICAL for saving progress (only after initial position restored)
    LaunchedEffect(pagerState.currentPage, effectiveTwoPageMode) {
        if (pages.isNotEmpty() && initialPositionRestored) {
            val actualPage = if (effectiveTwoPageMode) {
                if (pagerState.currentPage == 0) 0
                else (pagerState.currentPage * 2) - 1
            } else {
                pagerState.currentPage
            }
            currentPage = actualPage.coerceIn(0, pages.size - 1)
            // Update targetRestorePage so settings changes restore to current position
            targetRestorePage = currentPage
            // Save progress on user-initiated page changes
            onPageChange(currentPage, pages.size)
        }
    }

    // Periodic auto-save every 10 seconds while reading (prevents data loss on crash/kill)
    LaunchedEffect(initialPositionRestored) {
        if (!initialPositionRestored) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            val pagesToSave = if (pages.isNotEmpty()) pages.size else totalPages.coerceAtLeast(1)
            onPageChange(currentPage.coerceIn(0, pagesToSave - 1), pagesToSave)
        }
    }

    // Keep track of latest values for saving on dispose
    // Track both current page and total pages to ensure we have valid data to save
    val currentPageState = rememberUpdatedState(currentPage)
    val pagesListState = rememberUpdatedState(pages)
    val totalPagesState = rememberUpdatedState(totalPages)
    val onPageChangeState = rememberUpdatedState(onPageChange)

    // Save progress when leaving the screen - CRITICAL
    DisposableEffect(Unit) {
        onDispose {
            // Always save current page when leaving
            // Use actual pages list size for accuracy, fall back to tracked totalPages, then book.totalPages
            // Ensure we always have at least 1 page to prevent save failures
            val pagesToSave = when {
                pagesListState.value.isNotEmpty() -> pagesListState.value.size
                totalPagesState.value > 0 -> totalPagesState.value
                book.totalPages > 0 -> book.totalPages
                else -> 1 // Fallback to 1 page minimum to ensure save works
            }
            // Always save - pagesToSave is guaranteed to be at least 1
            onPageChangeState.value(currentPageState.value.coerceIn(0, pagesToSave - 1), pagesToSave)
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
                        "Loading book...",
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
                        "Unable to load book",
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
            // Book pages with horizontal paging
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp, bottom = 85.dp)
            ) { pageIndex ->
                val pagePair = pagePairs.getOrNull(pageIndex) ?: emptyList()

                if (effectiveTwoPageMode && pagePair.size == 2) {
                    // Two-page spread
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .graphicsLayer { alpha = brightness },
                        horizontalArrangement = Arrangement.spacedBy(pageGap.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BookPage(
                            bitmap = pagePair[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fitMode = pageFitMode,
                            onTap = { }
                        )
                        BookPage(
                            bitmap = pagePair[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            fitMode = pageFitMode,
                            onTap = { }
                        )
                    }
                } else if (pagePair.isNotEmpty()) {
                    // Single page
                    BookPage(
                        bitmap = pagePair[0],
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .graphicsLayer { alpha = brightness },
                        fitMode = pageFitMode,
                        onTap = { }
                    )
                }
            }

            // Page indicator dots (for small number of pages)
            if (totalDisplayPages <= 20) {
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
                if (showBackButton) {
                    val backInteractionSource = remember { MutableInteractionSource() }
                    val backIsPressed by backInteractionSource.collectIsPressedAsState()
                    val backScale by animateFloatAsState(
                        targetValue = if (backIsPressed) 0.85f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "ebookBackScale"
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

                if (showSearchBar) {
                    val searchInteractionSource = remember { MutableInteractionSource() }
                    val searchIsPressed by searchInteractionSource.collectIsPressedAsState()
                    val searchScale by animateFloatAsState(
                        targetValue = if (searchIsPressed) 0.85f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                        label = "ebookSearchScale"
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
                        .padding(16.dp)
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
                                indication = null,
                                onClick = action
                            )
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

        // Settings panel overlay - Adaptive sizing based on screen
        val settingsPadding = if (screenWidth < 400) 12.dp else 16.dp
        val settingsIconSize = if (screenWidth < 400) 14.dp else 16.dp
        val settingsButtonHeight = if (screenWidth < 400) 32.dp else 36.dp
        val settingsFontSize = if (screenWidth < 400) 10.sp else 12.sp
        val settingsMaxHeight = (screenHeight * 0.6f).dp  // Max 60% of screen height

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
                    // Header with drag handle
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
                                "Reader Settings",
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
                            // Section header
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.Visibility,
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

                            Spacer(modifier = Modifier.height(8.dp))

                            // Brightness row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    AppIcons.BrightnessLow,
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Slider(
                                    value = brightness,
                                    onValueChange = {
                                        brightness = it
                                        onBrightnessChange(it)
                                    },
                                    valueRange = 0.3f..1f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.accent,
                                        activeTrackColor = palette.accent,
                                        inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                                    )
                                )
                                Icon(
                                    AppIcons.BrightnessHigh,
                                    contentDescription = null,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Keep screen on toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = AppIcons.LightMode,
                                        contentDescription = null,
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Keep screen on", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                                }
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = onKeepScreenOnChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onPrimary,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.shade4,
                                        uncheckedTrackColor = palette.shade5.copy(alpha = 0.5f)
                                    )
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
                                            .clickable {
                                                pageFitMode = mode
                                                onPageFitModeChange(mode)
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

                            Spacer(modifier = Modifier.height(6.dp))

                            // Page layout mode
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
                                                forceSinglePageMode = single
                                                forceTwoPageMode = two
                                                onForceSinglePageChange(single)
                                                onForceTwoPageChange(two)
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

                            // Page Gap (when in 2-page mode)
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
                                    onValueChange = {
                                        pageGap = it.toInt()
                                        onPageGapChange(it.toInt())
                                    },
                                    valueRange = 0f..16f,
                                    steps = 7,
                                    modifier = Modifier.fillMaxWidth().height(20.dp),
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

                    // Typography Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape10,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.TextFields,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(settingsIconSize)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Typography",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Font size with compact slider - 6 choices: 12, 16, 20, 24, 28, 32
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("A", fontSize = settingsFontSize, color = palette.textMuted)
                                Slider(
                                    value = fontSize.toFloat(),
                                    onValueChange = {
                                        // Snap to nearest valid value
                                        val snapped = ((it - 12f) / 4f).roundToInt() * 4 + 12
                                        fontSize = snapped.coerceIn(12, 32)
                                        onFontSizeChange(snapped.coerceIn(12, 32))
                                    },
                                    valueRange = 12f..32f,
                                    steps = 4, // 6 choices: 12, 16, 20, 24, 28, 32
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(20.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = palette.accent,
                                        activeTrackColor = palette.accent,
                                        inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                                    )
                                )
                                Text("A", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textMuted)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${fontSize}", fontSize = settingsFontSize, color = palette.accent)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Font family
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(settingsButtonHeight - 4.dp)
                                    .clip(shape8)
                                    .background(palette.background),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(
                                    "serif" to "Serif",
                                    "sans-serif" to "Sans",
                                    "monospace" to "Mono"
                                ).forEach { (family, label) ->
                                    val isSelected = fontFamily == family
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape8)
                                            .background(if (isSelected) palette.accent else Color.Transparent)
                                            .clickable {
                                                fontFamily = family
                                                onFontFamilyChange(family)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = settingsFontSize,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) palette.onPrimary else palette.textPrimary,
                                            fontFamily = when (family) {
                                                "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                                "sans-serif" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                                else -> androidx.compose.ui.text.font.FontFamily.Monospace
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Text alignment
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(settingsButtonHeight - 4.dp)
                                    .clip(shape8)
                                    .background(palette.background),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(
                                    "left" to AppIcons.FormatAlignLeft,
                                    "center" to AppIcons.FormatAlignCenter,
                                    "justify" to AppIcons.FormatAlignJustify
                                ).forEach { (align, icon) ->
                                    val isSelected = textAlign == align
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(shape8)
                                            .background(if (isSelected) palette.accent else Color.Transparent)
                                            .clickable {
                                                textAlign = align
                                                onTextAlignChange(align)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) palette.onPrimary else palette.textPrimary,
                                            modifier = Modifier.size(settingsIconSize)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Bold toggle in-line
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape8)
                                    .background(palette.background)
                                    .clickable {
                                        val newValue = !boldText
                                        boldText = newValue
                                        onBoldTextChange(newValue)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = AppIcons.FormatBold,
                                        contentDescription = null,
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(settingsIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Bold text", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                                }
                                Switch(
                                    checked = boldText,
                                    onCheckedChange = {
                                        boldText = it
                                        onBoldTextChange(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = palette.onPrimary,
                                        checkedTrackColor = palette.accent,
                                        uncheckedThumbColor = palette.shade4,
                                        uncheckedTrackColor = palette.shade5.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Spacing Settings Card
                    Surface(
                        color = palette.surfaceMedium,
                        shape = shape12,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    AppIcons.FormatLineSpacing,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Spacing",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Line spacing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Lines", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                                Text("${"%.1f".format(java.util.Locale.US, lineSpacing)}x", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                            }
                            Slider(
                                value = lineSpacing,
                                onValueChange = {
                                    lineSpacing = it
                                    onLineSpacingChange(it)
                                },
                                valueRange = 1f..2.5f,
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent,
                                    inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                                )
                            )

                            // Paragraph spacing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Paragraphs", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                                Text("${paragraphSpacing}dp", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                            }
                            Slider(
                                value = paragraphSpacing.toFloat(),
                                onValueChange = {
                                    paragraphSpacing = it.toInt()
                                    onParagraphSpacingChange(it.toInt())
                                },
                                valueRange = 0f..32f,
                                steps = 7,
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent,
                                    inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                                )
                            )

                            // Margins
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Margins", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                                Text("${marginSize}dp", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                            }
                            Slider(
                                value = marginSize.toFloat(),
                                onValueChange = {
                                    marginSize = it.toInt()
                                    onMarginsChange(it.toInt())
                                },
                                valueRange = 8f..48f,
                                steps = 9,
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent,
                                    inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                                )
                            )

                            // Word spacing
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Words", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                                Text("$wordSpacing", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                            }
                            Slider(
                                value = wordSpacing.toFloat(),
                                onValueChange = {
                                    wordSpacing = it.toInt()
                                    onWordSpacingChange(it.toInt())
                                },
                                valueRange = -2f..8f,
                                steps = 9,
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent,
                                    inactiveTrackColor = palette.accent.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress info bar
                    Surface(
                        color = palette.accent.copy(alpha = 0.1f),
                        shape = shape8,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppIcons.Book,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Progress",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textPrimary
                                )
                            }
                            Text(
                                text = "Page ${currentPage + 1} of ${pages.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun BookPage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    fitMode: String = "fit",
    onTap: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }

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
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Book page",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                ),
            contentScale = contentScale
        )
    }
}

/**
 * Load book pages as bitmaps for consistent page tracking
 * Supports PDF, EPUB (images), and image-based books
 */
private suspend fun loadBookPages(
    context: Context,
    uri: Uri,
    fileType: String,
    bgColorName: String = "light",
    textSettings: TextRenderSettings = TextRenderSettings(),
    themeBgColor: Int? = null,
    themeTextColor: Int? = null
): List<Bitmap> = withContext(Dispatchers.IO) {
    val result = try {
        when (fileType.lowercase()) {
            "pdf" -> loadPdfPages(context, uri)
            "epub" -> loadEpubPages(context, uri, bgColorName, textSettings, themeBgColor, themeTextColor)
            "cbz", "zip" -> loadZipPages(context, uri)
            "txt" -> loadTextAsPages(context, uri, bgColorName, textSettings, themeBgColor, themeTextColor)
            else -> emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    // Always return at least one placeholder page to ensure progress saving works
    if (result.isEmpty()) {
        val bgColor = when {
            bgColorName == "theme" && themeBgColor != null -> themeBgColor
            bgColorName == "dark" -> android.graphics.Color.rgb(26, 26, 26)
            bgColorName == "sepia" -> android.graphics.Color.rgb(245, 230, 211)
            bgColorName == "amoled" -> android.graphics.Color.BLACK
            else -> android.graphics.Color.WHITE
        }
        val placeholder = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        placeholder.eraseColor(bgColor)
        listOf(placeholder)
    } else {
        result
    }
}

/**
 * Load PDF pages as bitmaps
 */
private fun loadPdfPages(context: Context, uri: Uri): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    var pdfRenderer: PdfRenderer? = null
    var fileDescriptor: ParcelFileDescriptor? = null
    var tempFile: File? = null

    try {
        tempFile = File.createTempFile("pdf_book", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor)

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
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
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

/**
 * Load EPUB content as pages - handles mixed text and image content
 * Shows images for image-heavy pages and rendered text for text-heavy pages
 */
private fun loadEpubPages(
    context: Context,
    uri: Uri,
    bgColorName: String = "light",
    textSettings: TextRenderSettings = TextRenderSettings(),
    themeBgColor: Int? = null,
    themeTextColor: Int? = null
): List<Bitmap> {
    val imageMap = mutableMapOf<String, ByteArray>() // path -> image bytes
    val htmlFiles = mutableListOf<Pair<String, String>>() // path -> html content
    val resultPages = mutableListOf<Bitmap>()

    try {
        // First pass: extract all images and HTML content
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    val originalName = entry.name

                    // Extract images (store bytes for later)
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
                            // Store with multiple path variations for matching
                            val imageBytes = buffer.toByteArray()
                            imageMap[originalName] = imageBytes
                            imageMap[originalName.lowercase()] = imageBytes
                            // Also store just the filename
                            val fileName = originalName.substringAfterLast("/")
                            imageMap[fileName] = imageBytes
                            imageMap[fileName.lowercase()] = imageBytes
                        } catch (e: Exception) {
                            // Skip problematic images
                        }
                    }
                    // Extract HTML/XHTML files
                    else if (!entry.isDirectory && (
                        name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm"))) {
                        try {
                            val buffer = ByteArrayOutputStream()
                            val data = ByteArray(4096)
                            var bytesRead: Int
                            while (zipStream.read(data).also { bytesRead = it } != -1) {
                                buffer.write(data, 0, bytesRead)
                            }
                            val htmlContent = buffer.toString(Charsets.UTF_8.name())
                            htmlFiles.add(originalName to htmlContent)
                        } catch (e: Exception) {
                            // Skip problematic files
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }

        // Process HTML files in order
        val sortedHtmlFiles = htmlFiles.sortedBy { it.first.lowercase() }

        for ((htmlPath, html) in sortedHtmlFiles) {
            // Check if this HTML is primarily an image page
            val imageRefs = extractImageReferences(html)
            val textContent = extractTextFromHtml(html)
            val textLength = textContent.trim().length

            // Determine if this is an image page or text page
            // Image page: has image reference(s) and minimal text (< 100 chars)
            // Text page: has substantial text

            if (imageRefs.isNotEmpty() && textLength < 100) {
                // This is an image page - find and decode the referenced image
                val htmlDir = htmlPath.substringBeforeLast("/", "")

                for (imageRef in imageRefs) {
                    // Try to find the image with various path combinations
                    val possiblePaths = listOf(
                        imageRef,
                        imageRef.lowercase(),
                        if (htmlDir.isNotEmpty()) "$htmlDir/$imageRef" else imageRef,
                        if (htmlDir.isNotEmpty()) "$htmlDir/${imageRef.substringAfterLast("/")}" else imageRef,
                        imageRef.substringAfterLast("/"),
                        imageRef.removePrefix("../").removePrefix("./"),
                        // Handle relative paths going up directories
                        resolveRelativePath(htmlPath, imageRef)
                    )

                    var foundImage = false
                    for (path in possiblePaths) {
                        val imageBytes = imageMap[path] ?: imageMap[path.lowercase()]
                        if (imageBytes != null) {
                            decodeImageBytes(imageBytes)?.let { bitmap ->
                                resultPages.add(bitmap)
                                foundImage = true
                            }
                            break
                        }
                    }

                    if (foundImage) break
                }
            } else if (textContent.isNotBlank()) {
                // This is a text page - render the text
                val textPages = renderTextAsPages(textContent, bgColorName, textSettings, themeBgColor, themeTextColor)
                resultPages.addAll(textPages)
            }
        }

        // If no pages were created from HTML processing but we have images, use them
        if (resultPages.isEmpty() && imageMap.isNotEmpty()) {
            val sortedImages = imageMap.entries
                .distinctBy { it.value.contentHashCode() }
                .sortedBy { it.key.lowercase() }

            for ((_, bytes) in sortedImages) {
                decodeImageBytes(bytes)?.let { bitmap ->
                    resultPages.add(bitmap)
                }
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }

    return resultPages
}

/**
 * Extract image references from HTML (src attributes from img tags)
 */
private fun extractImageReferences(html: String): List<String> {
    val imageRefs = mutableListOf<String>()
    // Match src="..." or src='...' in img tags
    val imgPattern = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    imgPattern.findAll(html).forEach { match ->
        val src = match.groupValues[1]
        if (src.isNotBlank() && !src.startsWith("data:")) {
            imageRefs.add(src)
        }
    }
    // Also check for SVG images with xlink:href
    val svgPattern = Regex("""xlink:href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    svgPattern.findAll(html).forEach { match ->
        val href = match.groupValues[1]
        if (href.isNotBlank() && !href.startsWith("data:") &&
            (href.endsWith(".jpg", true) || href.endsWith(".jpeg", true) ||
             href.endsWith(".png", true) || href.endsWith(".gif", true))) {
            imageRefs.add(href)
        }
    }
    return imageRefs
}

/**
 * Resolve a relative path like "../images/cover.jpg" from an HTML file path
 */
private fun resolveRelativePath(htmlPath: String, relativePath: String): String {
    if (!relativePath.startsWith("..")) return relativePath

    val htmlDir = htmlPath.substringBeforeLast("/", "")
    val parts = htmlDir.split("/").toMutableList()
    val relParts = relativePath.split("/")

    for (part in relParts) {
        when (part) {
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            "." -> { /* skip */ }
            else -> parts.add(part)
        }
    }

    return parts.joinToString("/")
}

/**
 * Decode image bytes to bitmap with appropriate scaling
 */
private fun decodeImageBytes(imageBytes: ByteArray): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        val maxDimension = 2048
        var sampleSize = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val heightRatio = (options.outHeight.toFloat() / maxDimension).toInt()
            val widthRatio = (options.outWidth.toFloat() / maxDimension).toInt()
            sampleSize = maxOf(heightRatio, widthRatio)
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize

        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    } catch (e: Exception) {
        null
    }
}

/**
 * Extract plain text from HTML content
 */
private fun extractTextFromHtml(html: String): String {
    var text = html

    // Remove script and style blocks
    text = text.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
    text = text.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

    // Replace common block elements with line breaks
    text = text.replace(Regex("<br[^>]*>", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    text = text.replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(Regex("</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n")
    text = text.replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
    text = text.replace(Regex("</tr>", RegexOption.IGNORE_CASE), "\n")

    // Remove all remaining HTML tags
    text = text.replace(Regex("<[^>]+>"), "")

    // Decode common HTML entities
    text = text.replace("&nbsp;", " ")
    text = text.replace("&amp;", "&")
    text = text.replace("&lt;", "<")
    text = text.replace("&gt;", ">")
    text = text.replace("&quot;", "\"")
    text = text.replace("&apos;", "'")
    text = text.replace("&#39;", "'")
    text = text.replace("&mdash;", "—")
    text = text.replace("&ndash;", "–")
    text = text.replace("&hellip;", "...")
    text = text.replace("&ldquo;", """)
    text = text.replace("&rdquo;", """)
    text = text.replace("&lsquo;", "'")
    text = text.replace("&rsquo;", "'")

    // Clean up whitespace
    text = text.replace(Regex("[ \\t]+"), " ")
    text = text.replace(Regex("\\n{3,}"), "\n\n")

    return text.trim()
}

/**
 * Render text content as bitmap pages with customizable settings
 */
private fun renderTextAsPages(
    text: String,
    bgColorName: String = "light",
    settings: TextRenderSettings = TextRenderSettings(),
    themeBgColor: Int? = null,
    themeTextColor: Int? = null
): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()

    val pageWidth = 1080
    val pageHeight = 1920
    // Use margin setting - scale from dp (8-48) to pixels
    val padding = (settings.marginSize * 2.5f).toInt().coerceIn(20, 120)
    val textWidth = pageWidth - (padding * 2)
    val textHeight = pageHeight - (padding * 2)

    // Determine colors based on background theme
    val (bgColor, textColor) = when {
        bgColorName == "theme" && themeBgColor != null && themeTextColor != null -> themeBgColor to themeTextColor
        bgColorName == "dark" -> android.graphics.Color.rgb(26, 26, 26) to android.graphics.Color.rgb(230, 230, 230)
        bgColorName == "sepia" -> android.graphics.Color.rgb(245, 230, 211) to android.graphics.Color.rgb(60, 40, 20)
        bgColorName == "amoled" -> android.graphics.Color.BLACK to android.graphics.Color.rgb(200, 200, 200)
        bgColorName == "theme" -> android.graphics.Color.rgb(50, 50, 50) to android.graphics.Color.rgb(230, 230, 230) // Fallback
        else -> android.graphics.Color.WHITE to android.graphics.Color.BLACK
    }

    // Get typeface based on font family setting
    val typeface = when (settings.fontFamily) {
        "sans-serif" -> android.graphics.Typeface.SANS_SERIF
        "monospace" -> android.graphics.Typeface.MONOSPACE
        else -> android.graphics.Typeface.SERIF
    }

    // Apply bold if enabled
    val finalTypeface = if (settings.boldText) {
        android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
    } else {
        typeface
    }

    // Font size scaled from sp (12-32) to pixels
    val scaledFontSize = settings.fontSize * 2.5f

    val textPaint = android.graphics.Paint().apply {
        color = textColor
        textSize = scaledFontSize
        isAntiAlias = true
        this.typeface = finalTypeface
        // Word spacing (letter spacing affects character spacing, we simulate word spacing with extra spaces)
        letterSpacing = settings.wordSpacing * 0.02f
    }

    // Line height with configurable spacing
    val baseFontSpacing = textPaint.fontSpacing
    val lineHeight = baseFontSpacing * settings.lineSpacing

    // Paragraph extra spacing scaled
    val paragraphExtraSpacing = settings.paragraphSpacing * 2f

    val linesPerPage = (textHeight / lineHeight).toInt().coerceAtLeast(1)

    // Text alignment
    val alignment = when (settings.textAlign) {
        "center" -> android.graphics.Paint.Align.CENTER
        "justify" -> android.graphics.Paint.Align.LEFT // We'll handle justify manually
        else -> android.graphics.Paint.Align.LEFT
    }
    textPaint.textAlign = alignment

    // Split text into lines that fit the page width
    data class TextLine(val text: String, val isLastInParagraph: Boolean)
    val allLines = mutableListOf<TextLine>()
    val paragraphs = text.split("\n")

    for (paragraph in paragraphs) {
        if (paragraph.isBlank()) {
            allLines.add(TextLine("", true))
            continue
        }

        val words = paragraph.trim().split(Regex("\\s+"))
        var currentLine = StringBuilder()
        val paragraphLines = mutableListOf<String>()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val textWidthMeasured = textPaint.measureText(testLine)

            if (textWidthMeasured > textWidth) {
                if (currentLine.isNotEmpty()) {
                    paragraphLines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    // Word is too long, force add it
                    paragraphLines.add(word)
                }
            } else {
                currentLine = StringBuilder(testLine)
            }
        }

        if (currentLine.isNotEmpty()) {
            paragraphLines.add(currentLine.toString())
        }

        // Add lines with paragraph marking
        for ((lineIdx, line) in paragraphLines.withIndex()) {
            val isLast = lineIdx == paragraphLines.size - 1
            allLines.add(TextLine(line, isLast))
        }
    }

    // Function to draw justified text
    fun drawJustifiedText(canvas: android.graphics.Canvas, line: String, x: Float, y: Float, maxWidth: Float, paint: android.graphics.Paint, isLastLine: Boolean) {
        if (isLastLine || line.isBlank()) {
            // Don't justify last line of paragraph
            canvas.drawText(line, x, y, paint)
            return
        }

        val words = line.split(" ")
        if (words.size <= 1) {
            canvas.drawText(line, x, y, paint)
            return
        }

        val totalTextWidth = words.sumOf { paint.measureText(it).toDouble() }.toFloat()
        val totalSpacing = maxWidth - totalTextWidth
        val spaceWidth = totalSpacing / (words.size - 1)

        var currentX = x
        for ((idx, word) in words.withIndex()) {
            canvas.drawText(word, currentX, y, paint)
            currentX += paint.measureText(word) + if (idx < words.size - 1) spaceWidth else 0f
        }
    }

    // Create pages from lines
    var lineIndex = 0
    while (lineIndex < allLines.size) {
        val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Fill with background color
        canvas.drawColor(bgColor)

        var y = padding + baseFontSpacing
        var linesOnPage = 0

        while (lineIndex < allLines.size && linesOnPage < linesPerPage) {
            val textLine = allLines[lineIndex]
            val line = textLine.text

            // Calculate x position based on alignment
            val x = when (alignment) {
                android.graphics.Paint.Align.CENTER -> (pageWidth / 2).toFloat()
                else -> padding.toFloat()
            }

            // Draw the line
            if (settings.textAlign == "justify" && !textLine.isLastInParagraph) {
                drawJustifiedText(canvas, line, x, y, textWidth.toFloat(), textPaint, textLine.isLastInParagraph)
            } else {
                canvas.drawText(line, x, y, textPaint)
            }

            // Add extra paragraph spacing if this is the last line of a paragraph
            y += if (textLine.isLastInParagraph && line.isNotBlank()) {
                lineHeight + paragraphExtraSpacing
            } else {
                lineHeight
            }

            lineIndex++
            linesOnPage++
        }

        pages.add(bitmap)
    }

    return pages
}

/**
 * Load ZIP/CBZ images as pages
 */
private fun loadZipPages(context: Context, uri: Uri): List<Bitmap> {
    val pages = mutableListOf<Pair<String, Bitmap>>()

    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
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

                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                            val maxDimension = 2048
                            var sampleSize = 1
                            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                                val heightRatio = (options.outHeight.toFloat() / maxDimension).toInt()
                                val widthRatio = (options.outWidth.toFloat() / maxDimension).toInt()
                                sampleSize = maxOf(heightRatio, widthRatio)
                            }

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
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return pages.sortedBy { it.first.lowercase() }.map { it.second }
}

/**
 * Load text file as page images (for consistent page-based tracking)
 */
private fun loadTextAsPages(
    context: Context,
    uri: Uri,
    bgColorName: String = "light",
    textSettings: TextRenderSettings = TextRenderSettings(),
    themeBgColor: Int? = null,
    themeTextColor: Int? = null
): List<Bitmap> {
    try {
        val text = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    text.appendLine(line)
                    if (text.length > 100000) break // Limit for performance
                }
            }
        }

        // Use the same text rendering as EPUB
        if (text.isNotBlank()) {
            return renderTextAsPages(text.toString(), bgColorName, textSettings, themeBgColor, themeTextColor)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Return at least one placeholder page
    val bgColor = when {
        bgColorName == "theme" && themeBgColor != null -> themeBgColor
        bgColorName == "dark" -> android.graphics.Color.rgb(26, 26, 26)
        bgColorName == "sepia" -> android.graphics.Color.rgb(245, 230, 211)
        bgColorName == "amoled" -> android.graphics.Color.BLACK
        else -> android.graphics.Color.WHITE
    }
    val placeholder = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    placeholder.eraseColor(bgColor)
    return listOf(placeholder)
}
