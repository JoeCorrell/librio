package com.librio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.librio.ui.theme.AppIcons
import com.librio.ui.components.CoverArt
import com.librio.ui.components.CoverArtContentType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.model.Category
import com.librio.model.ContentType
import kotlinx.coroutines.launch
import com.librio.model.LibraryAudiobook
import com.librio.model.LibraryBook
import com.librio.model.LibraryComic
import com.librio.model.LibraryMusic
import com.librio.model.LibrarySeries
import com.librio.model.LibraryMovie
import com.librio.model.SortOption
import com.librio.ui.theme.*

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryListScreen(
    audiobooks: List<LibraryAudiobook>,
    books: List<LibraryBook> = emptyList(),
    music: List<LibraryMusic> = emptyList(),
    comics: List<LibraryComic> = emptyList(),
    movies: List<LibraryMovie> = emptyList(),
    selectedContentType: ContentType = ContentType.AUDIOBOOK,
    onContentTypeChange: (ContentType) -> Unit = {},
    onAddAudiobook: (Uri) -> Unit,
    onAddBook: (Uri) -> Unit = {},
    onSelectAudiobook: (LibraryAudiobook) -> Unit,
    onSelectBook: (LibraryBook) -> Unit = {},
    onSelectMusic: (LibraryMusic) -> Unit = {},
    onSelectComic: (LibraryComic) -> Unit = {},
    onSelectMovie: (LibraryMovie) -> Unit = {},
    onAddMusic: (Uri) -> Unit = {},
    onAddComic: (Uri) -> Unit = {},
    onAddMovie: (Uri) -> Unit = {},
    onDeleteAudiobook: (LibraryAudiobook) -> Unit,
    onDeleteBook: (LibraryBook) -> Unit = {},
    onEditAudiobook: (LibraryAudiobook, String, String) -> Unit,
    onEditBook: (LibraryBook, String, String) -> Unit = { _, _, _ -> },
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchVisible: Boolean,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    categories: List<Category> = emptyList(),
    selectedCategoryId: String? = null,
    onSelectCategory: (String?) -> Unit = {},
    onAddCategory: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onSetAudiobookCategory: (String, String?) -> Unit = { _, _ -> },
    onEditMusic: (LibraryMusic, String, String) -> Unit = { _, _, _ -> },
    onDeleteMusic: (LibraryMusic) -> Unit = {},
    onEditComic: (LibraryComic, String, String) -> Unit = { _, _, _ -> },
    onDeleteComic: (LibraryComic) -> Unit = {},
    onEditMovie: (LibraryMovie, String) -> Unit = { _, _ -> },
    onDeleteMovie: (LibraryMovie) -> Unit = {},
    showPlaceholderIcons: Boolean = true,
    defaultLibraryView: String = "LIST",
    // Series management
    seriesList: List<LibrarySeries> = emptyList(),
    onAddSeries: (String) -> Unit = {},
    onDeleteSeries: (String) -> Unit = {},
    onRenameSeries: (String, String) -> Unit = { _, _ -> },
    onSetAudiobookSeries: (String, String?) -> Unit = { _, _ -> },
    onSetBookSeries: (String, String?) -> Unit = { _, _ -> },
    onSetMusicSeries: (String, String?) -> Unit = { _, _ -> },
    onSetComicSeries: (String, String?) -> Unit = { _, _ -> },
    onSetMovieSeries: (String, String?) -> Unit = { _, _ -> },
    onPlaylistClick: (LibrarySeries) -> Unit = {},
    onSetSeriesCoverArt: (String, String?) -> Unit = { _, _ -> },
    // Individual item cover art callbacks
    onSetAudiobookCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetBookCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetMusicCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetComicCoverArt: (String, String?) -> Unit = { _, _ -> },
    onSetMovieCoverArt: (String, String?) -> Unit = { _, _ -> },
    collapsedSeries: Set<String> = emptySet(),
    onCollapsedSeriesChange: (Set<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)
    val shape10 = cornerRadius(10.dp)
    val shape12 = cornerRadius(12.dp)
    val shape50 = cornerRadius(50.dp)
    var showEditDialog by remember { mutableStateOf<LibraryAudiobook?>(null) }
    var showDeleteDialog by remember { mutableStateOf<LibraryAudiobook?>(null) }
    var showEditBookDialog by remember { mutableStateOf<LibraryBook?>(null) }
    var showDeleteBookDialog by remember { mutableStateOf<LibraryBook?>(null) }
    var showEditMusicDialog by remember { mutableStateOf<LibraryMusic?>(null) }
    var showEditComicDialog by remember { mutableStateOf<LibraryComic?>(null) }
    var showEditMovieDialog by remember { mutableStateOf<LibraryMovie?>(null) }
    var showRenameSeriesDialog by remember { mutableStateOf<LibrarySeries?>(null) }
    var showCoverArtPickerForSeries by remember { mutableStateOf<LibrarySeries?>(null) }
    var selectedPlaylistFilter by remember { mutableStateOf<String?>(null) } // null = "All"

    // Reset playlist filter when content type changes
    LaunchedEffect(selectedContentType) {
        selectedPlaylistFilter = null
    }

    // State for individual item cover art pickers
    var showCoverArtPickerForAudiobook by remember { mutableStateOf<LibraryAudiobook?>(null) }
    var showCoverArtPickerForBook by remember { mutableStateOf<LibraryBook?>(null) }
    var showCoverArtPickerForMusic by remember { mutableStateOf<LibraryMusic?>(null) }
    var showCoverArtPickerForComic by remember { mutableStateOf<LibraryComic?>(null) }
    var showCoverArtPickerForMovie by remember { mutableStateOf<LibraryMovie?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    // Context for loading cover art bitmaps
    val context = LocalContext.current

    // Cache for loaded cover art bitmaps - using mutableStateMapOf for recomposition
    val coverArtCache = remember { mutableStateMapOf<String, android.graphics.Bitmap?>() }

    // Image picker launcher for cover art (handles series and individual items)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            // Take persistable URI permission so we can access it later
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Some URIs don't support persistent permissions
            }

            // Handle series cover art
            showCoverArtPickerForSeries?.let { series ->
                onSetSeriesCoverArt(series.id, selectedUri.toString())
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(selectedUri)
                    )
                    coverArtCache[series.id] = bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Handle individual item cover art
            showCoverArtPickerForAudiobook?.let { audiobook ->
                onSetAudiobookCoverArt(audiobook.id, selectedUri.toString())
            }
            showCoverArtPickerForBook?.let { book ->
                onSetBookCoverArt(book.id, selectedUri.toString())
            }
            showCoverArtPickerForMusic?.let { music ->
                onSetMusicCoverArt(music.id, selectedUri.toString())
            }
            showCoverArtPickerForComic?.let { comic ->
                onSetComicCoverArt(comic.id, selectedUri.toString())
            }
            showCoverArtPickerForMovie?.let { movie ->
                onSetMovieCoverArt(movie.id, selectedUri.toString())
            }
        }
        // Clear all picker states
        showCoverArtPickerForSeries = null
        showCoverArtPickerForAudiobook = null
        showCoverArtPickerForBook = null
        showCoverArtPickerForMusic = null
        showCoverArtPickerForComic = null
        showCoverArtPickerForMovie = null
    }

    // Load cover art for series with custom cover art URIs
    LaunchedEffect(seriesList) {
        seriesList.forEach { series ->
            if (series.coverArtUri != null && !coverArtCache.containsKey(series.id)) {
                try {
                    val uri = android.net.Uri.parse(series.coverArtUri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(uri)
                    )
                    coverArtCache[series.id] = bitmap
                } catch (e: Exception) {
                    coverArtCache[series.id] = null
                }
            }
        }
    }

    // Launch image picker when any cover art picker state is set
    LaunchedEffect(
        showCoverArtPickerForSeries,
        showCoverArtPickerForAudiobook,
        showCoverArtPickerForBook,
        showCoverArtPickerForMusic,
        showCoverArtPickerForComic,
        showCoverArtPickerForMovie
    ) {
        if (showCoverArtPickerForSeries != null ||
            showCoverArtPickerForAudiobook != null ||
            showCoverArtPickerForBook != null ||
            showCoverArtPickerForMusic != null ||
            showCoverArtPickerForComic != null ||
            showCoverArtPickerForMovie != null) {
            imagePickerLauncher.launch("image/*")
        }
    }

    // Tab animation
    val tabIndicatorOffset by animateDpAsState(
        targetValue = if (selectedContentType == ContentType.AUDIOBOOK) 0.dp else 1.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tabOffset"
    )


    // Edit audiobook dialog
    showEditDialog?.let { audiobook ->
        EditMetadataDialog(
            audiobook = audiobook,
            categories = categories,
            seriesList = seriesList.filter { it.contentType == ContentType.AUDIOBOOK },
            onDismiss = { showEditDialog = null },
            onSave = { title, author, categoryId ->
                onEditAudiobook(audiobook, title, author)
                onSetAudiobookCategory(audiobook.id, categoryId)
                showEditDialog = null
            },
            onSetSeries = { seriesId ->
                onSetAudiobookSeries(audiobook.id, seriesId)
            },
            onSetCoverArt = {
                showCoverArtPickerForAudiobook = audiobook
            },
            onDelete = {
                onDeleteAudiobook(audiobook)
                showEditDialog = null
            },
            onAddSeries = onAddSeries,
            onDeleteSeries = onDeleteSeries
        )
    }

    // Edit book dialog
    showEditBookDialog?.let { book ->
        EditBookMetadataDialog(
            book = book,
            seriesList = seriesList.filter { it.contentType == ContentType.EBOOK },
            onDismiss = { showEditBookDialog = null },
            onSave = { title, author ->
                onEditBook(book, title, author)
                showEditBookDialog = null
            },
            onSetSeries = { seriesId ->
                onSetBookSeries(book.id, seriesId)
            },
            onSetCoverArt = {
                showCoverArtPickerForBook = book
            },
            onDelete = {
                onDeleteBook(book)
                showEditBookDialog = null
            },
            onAddSeries = onAddSeries,
            onDeleteSeries = onDeleteSeries
        )
    }

    // Edit music dialog
    showEditMusicDialog?.let { musicItem ->
        EditMusicMetadataDialog(
            music = musicItem,
            seriesList = seriesList.filter { it.contentType == musicItem.contentType },
            onDismiss = { showEditMusicDialog = null },
            onSave = { title: String, artist: String ->
                onEditMusic(musicItem, title, artist)
                showEditMusicDialog = null
            },
            onSetSeries = { seriesId ->
                onSetMusicSeries(musicItem.id, seriesId)
            },
            onSetCoverArt = {
                showCoverArtPickerForMusic = musicItem
            },
            onDelete = {
                onDeleteMusic(musicItem)
                showEditMusicDialog = null
            },
            onAddSeries = onAddSeries,
            onDeleteSeries = onDeleteSeries
        )
    }

    // Edit comic dialog
    showEditComicDialog?.let { comicItem ->
        EditComicMetadataDialog(
            comic = comicItem,
            seriesList = seriesList.filter { it.contentType == ContentType.COMICS },
            onDismiss = { showEditComicDialog = null },
            onSave = { title: String, author: String ->
                onEditComic(comicItem, title, author)
                showEditComicDialog = null
            },
            onSetSeries = { seriesId ->
                onSetComicSeries(comicItem.id, seriesId)
            },
            onSetCoverArt = {
                showCoverArtPickerForComic = comicItem
            },
            onDelete = {
                onDeleteComic(comicItem)
                showEditComicDialog = null
            },
            onAddSeries = onAddSeries,
            onDeleteSeries = onDeleteSeries
        )
    }

    // Edit movie dialog
    showEditMovieDialog?.let { movieItem ->
        EditMovieMetadataDialog(
            movie = movieItem,
            seriesList = seriesList.filter { it.contentType == ContentType.MOVIE },
            onDismiss = { showEditMovieDialog = null },
            onSave = { title: String ->
                onEditMovie(movieItem, title)
                showEditMovieDialog = null
            },
            onSetSeries = { seriesId ->
                onSetMovieSeries(movieItem.id, seriesId)
            },
            onSetCoverArt = {
                showCoverArtPickerForMovie = movieItem
            },
            onDelete = {
                onDeleteMovie(movieItem)
                showEditMovieDialog = null
            },
            onAddSeries = onAddSeries,
            onDeleteSeries = onDeleteSeries
        )
    }

    // Rename Series Dialog
    showRenameSeriesDialog?.let { series ->
        RenameSeriesDialog(
            series = series,
            onDismiss = { showRenameSeriesDialog = null },
            onConfirm = { newName ->
                onRenameSeries(series.id, newName)
                showRenameSeriesDialog = null
            }
        )
    }

    // Add category dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                newCategoryName = ""
            },
            containerColor = palette.surface,
            title = {
                Text("Add Category", color = palette.primary)
            },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category Name") },
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
                        if (newCategoryName.isNotBlank()) {
                            onAddCategory(newCategoryName)
                            newCategoryName = ""
                            showAddCategoryDialog = false
                        }
                    },
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text("Add", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCategoryDialog = false
                    newCategoryName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog for audiobook
    showDeleteDialog?.let { audiobook ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = palette.surface,
            title = { Text("Remove Audiobook", color = palette.primary) },
            text = { Text("Remove \"${audiobook.title}\" from your library?", color = palette.primary.copy(alpha = 0.7f)) },
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

    // Delete confirmation dialog for book
    showDeleteBookDialog?.let { book ->
        AlertDialog(
            onDismissRequest = { showDeleteBookDialog = null },
            containerColor = palette.surface,
            title = { Text("Remove Book", color = palette.primary) },
            text = { Text("Remove \"${book.title}\" from your library?", color = palette.primary.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBook(book)
                        showDeleteBookDialog = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBookDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sort menu dialog (shown on long press)
    if (showSortMenu) {
        val currentPlaylists = seriesList.filter { it.contentType == selectedContentType }
        SortMenuDialog(
            currentSort = sortOption,
            onSortSelected = onSortOptionChange,
            onDismiss = { showSortMenu = false },
            playlists = currentPlaylists,
            selectedPlaylist = selectedPlaylistFilter,
            onPlaylistSelected = { selectedPlaylistFilter = it }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Small gap before content tabs
        Spacer(modifier = Modifier.height(8.dp))

        // Content Type Tabs - Audiobooks / Books switcher
        ContentTypeTabs(
            selectedContentType = selectedContentType,
            onContentTypeChange = onContentTypeChange,
            audiobookCount = audiobooks.size,
            bookCount = books.size,
            musicCount = music.count { it.contentType == ContentType.MUSIC },
            creepypastaCount = music.count { it.contentType == ContentType.CREEPYPASTA },
            comicsCount = comics.size,
            movieCount = movies.size
        )

        // Search bar (if visible)
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(palette.surfaceMedium, shape12)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Search,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Dynamic placeholder based on content type
                    val searchPlaceholder = when (selectedContentType) {
                        ContentType.AUDIOBOOK -> "Search Audiobooks..."
                        ContentType.EBOOK -> "Search E Books..."
                        ContentType.COMICS -> "Search Comics..."
                        ContentType.MUSIC -> "Search Music..."
                        ContentType.CREEPYPASTA -> "Search Creepypasta..."
                        ContentType.MOVIE -> "Search Movies..."
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        textStyle = TextStyle(
                            color = palette.primary,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(palette.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        searchPlaceholder,
                                        color = palette.primary.copy(alpha = 0.5f),
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }

        // Category filter bar - always show so users can add categories
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            // Category chips
            items(categories, key = { it.id }) { category ->
                val isSelected = selectedCategoryId == category.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(category.id) },
                    label = { Text(category.name, color = if (isSelected) palette.onPrimary else palette.primary) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = palette.primary,
                        selectedLabelColor = palette.onPrimary,
                        containerColor = palette.primary.copy(alpha = 0.1f),
                        labelColor = palette.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = palette.primary.copy(alpha = 0.5f),
                        selectedBorderColor = palette.primary
                    )
                )
            }
        }

        // Sort/Filter indicator button - full width with border
        val displayText = if (selectedPlaylistFilter != null) {
            val playlist = seriesList.find { it.id == selectedPlaylistFilter }
            playlist?.name ?: sortOption.displayName
        } else {
            sortOption.displayName
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(
                    width = 1.dp,
                    color = palette.accent.copy(alpha = 0.3f),
                    shape = RectangleShape
                )
                .clickable { showSortMenu = true }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (selectedPlaylistFilter != null) AppIcons.Playlist else AppIcons.Sort,
                    contentDescription = if (selectedPlaylistFilter != null) "Filter" else "Sort",
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.accent,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    AppIcons.KeyboardArrowDown,
                    contentDescription = "Change sort",
                    tint = palette.accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Content based on selected type
        AnimatedContent(
            targetState = selectedContentType,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "contentSwitch"
        ) { contentType ->
            when (contentType) {
                ContentType.AUDIOBOOK -> {
                    if (audiobooks.isEmpty()) {
                        // Empty state for audiobooks
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    AppIcons.Audiobook,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = palette.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No audiobooks yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Place audiobooks in your\nprofile's Audiobooks folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.primary.copy(alpha = 0.5f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Filter and sort audiobooks by playlist
                        val filteredAudiobooks = remember(audiobooks, selectedPlaylistFilter, sortOption) {
                            val filtered = if (selectedPlaylistFilter != null) {
                                audiobooks.filter { it.seriesId == selectedPlaylistFilter }
                            } else {
                                audiobooks
                            }

                            // Sort filtered audiobooks
                            when (sortOption) {
                                SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                                SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
                                SortOption.AUTHOR_AZ -> filtered.sortedBy { it.author.lowercase() }
                                SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayed }
                                SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
                                SortOption.BOOK_NUMBER -> filtered.sortedBy { it.seriesOrder }
                            }
                        }

                        // Flat audiobook list without series dividers
                        if (defaultLibraryView == "GRID_2") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredAudiobooks, key = { _, item -> item.id }) { index, audiobook ->
                                    AudiobookGridItem(
                                        audiobook = audiobook,
                                        onClick = { onSelectAudiobook(audiobook) },
                                        onLongClick = { showEditDialog = audiobook },
                                        showPlayingIndicator = audiobook.lastPlayed > 0 && !audiobook.isCompleted,
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(filteredAudiobooks, key = { _, item -> item.id }) { index, audiobook ->
                                    AnimatedAudiobookListItem(
                                        audiobook = audiobook,
                                        index = index,
                                        onClick = { onSelectAudiobook(audiobook) },
                                        onLongClick = { showEditDialog = audiobook },
                                        showPlayingIndicator = audiobook.lastPlayed > 0 && !audiobook.isCompleted,
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                    Divider(
                                        color = palette.primaryLight.copy(alpha = 0.35f),
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clip(shape50)
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }
                ContentType.EBOOK -> {
                    if (books.isEmpty()) {
                        // Empty state for books
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    AppIcons.Book,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = palette.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No books yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Place books in your\nprofile's Books folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.primary.copy(alpha = 0.5f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Filter and sort books by playlist
                        val filteredBooks = remember(books, selectedPlaylistFilter, sortOption) {
                            val filtered = if (selectedPlaylistFilter != null) {
                                books.filter { it.seriesId == selectedPlaylistFilter }
                            } else {
                                books
                            }

                            // Sort filtered books
                            when (sortOption) {
                                SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                                SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
                                SortOption.AUTHOR_AZ -> filtered.sortedBy { it.author.lowercase() }
                                SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
                                SortOption.BOOK_NUMBER -> filtered.sortedBy { it.seriesOrder }
                            }
                        }

                        // Flat books list without series dividers
                        if (defaultLibraryView == "GRID_2") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredBooks, key = { _, item -> item.id }) { index, book ->
                                    BookGridItem(
                                        book = book,
                                        onClick = { onSelectBook(book) },
                                        onLongClick = { showEditBookDialog = book },
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(filteredBooks, key = { _, item -> item.id }) { index, book ->
                                    AnimatedBookListItem(
                                        book = book,
                                        index = index,
                                        onClick = { onSelectBook(book) },
                                        onLongClick = { showEditBookDialog = book },
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                    Divider(
                                        color = palette.primaryLight.copy(alpha = 0.35f),
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clip(shape50)
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }
                ContentType.MUSIC -> {
                    val musicItems = music.filter { it.contentType == ContentType.MUSIC }
                    if (musicItems.isEmpty()) {
                        // Music empty state
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    AppIcons.Music,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = palette.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No music yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Place music files in your\nprofile's Music folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.primary.copy(alpha = 0.5f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Filter and sort music by playlist
                        val filteredMusic = remember(musicItems, selectedPlaylistFilter, sortOption) {
                            val filtered = if (selectedPlaylistFilter != null) {
                                musicItems.filter { it.seriesId == selectedPlaylistFilter }
                            } else {
                                musicItems
                            }

                            // Sort filtered music
                            when (sortOption) {
                                SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                                SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
                                SortOption.AUTHOR_AZ -> filtered.sortedBy { it.artist.lowercase() }
                                SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayed }
                                SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
                                SortOption.BOOK_NUMBER -> filtered.sortedBy { it.seriesOrder }
                            }
                        }

                        // Flat music list without playlist dividers
                        if (defaultLibraryView == "GRID_2") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredMusic, key = { _, item -> item.id }) { _, musicItem ->
                                    MusicGridItem(
                                        music = musicItem,
                                        onClick = { onSelectMusic(musicItem) },
                                        onLongClick = { showEditMusicDialog = musicItem },
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(filteredMusic, key = { _, item -> item.id }) { _, musicItem ->
                                    MusicListItem(
                                        music = musicItem,
                                        onClick = { onSelectMusic(musicItem) },
                                        onLongClick = { showEditMusicDialog = musicItem },
                                        showPlaceholderIcons = showPlaceholderIcons,
                                        modifier = Modifier
                                            .animateItemPlacement()
                                    )
                                    Divider(
                                        color = palette.primaryLight.copy(alpha = 0.35f),
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clip(shape50)
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }
                ContentType.CREEPYPASTA -> {
                    val creepItems = music.filter { it.contentType == ContentType.CREEPYPASTA }
                    if (creepItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    AppIcons.Music,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = palette.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No creepypasta yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Place audio in your\nprofile's Creepypasta folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.primary.copy(alpha = 0.5f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Filter and sort creepypasta by playlist
                        val filteredCreep = remember(creepItems, selectedPlaylistFilter, sortOption) {
                            val filtered = if (selectedPlaylistFilter != null) {
                                creepItems.filter { it.seriesId == selectedPlaylistFilter }
                            } else {
                                creepItems
                            }

                            // Sort filtered creepypasta
                            when (sortOption) {
                                SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                                SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
                                SortOption.AUTHOR_AZ -> filtered.sortedBy { it.artist.lowercase() }
                                SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayed }
                                SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
                                SortOption.BOOK_NUMBER -> filtered.sortedBy { it.seriesOrder }
                            }
                        }

                        // Flat creepypasta list without playlist dividers
                        if (defaultLibraryView == "GRID_2") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredCreep, key = { _, item -> item.id }) { _, musicItem ->
                                    MusicGridItem(
                                        music = musicItem,
                                        onClick = { onSelectMusic(musicItem) },
                                        onLongClick = { showEditMusicDialog = musicItem },
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(filteredCreep, key = { _, item -> item.id }) { _, musicItem ->
                                    MusicListItem(
                                        music = musicItem,
                                        onClick = { onSelectMusic(musicItem) },
                                        onLongClick = { showEditMusicDialog = musicItem },
                                        showPlaceholderIcons = showPlaceholderIcons,
                                        modifier = Modifier
                                            .animateItemPlacement()
                                    )
                                    Divider(
                                        color = palette.primaryLight.copy(alpha = 0.35f),
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clip(shape50)
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }
                ContentType.COMICS -> {
                    if (comics.isEmpty()) {
                        // Comics empty state
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    AppIcons.Comic,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = palette.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No comics yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Place CBZ, CBR, or PDF comics in your\nprofile's Comics folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.primary.copy(alpha = 0.5f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Filter and sort comics by playlist
                        val filteredComics = remember(comics, selectedPlaylistFilter, sortOption) {
                            val filtered = if (selectedPlaylistFilter != null) {
                                comics.filter { it.seriesId == selectedPlaylistFilter }
                            } else {
                                comics
                            }

                            // Sort filtered comics
                            when (sortOption) {
                                SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                                SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
                                SortOption.AUTHOR_AZ -> filtered.sortedBy { it.author.lowercase() }
                                SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
                                SortOption.BOOK_NUMBER -> filtered.sortedBy { it.seriesOrder }
                            }
                        }

                        // Flat comics list without series dividers
                        if (defaultLibraryView == "GRID_2") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredComics, key = { _, item -> item.id }) { index, comicItem ->
                                    ComicGridItem(
                                        comic = comicItem,
                                        onClick = { onSelectComic(comicItem) },
                                        onLongClick = { showEditComicDialog = comicItem },
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(filteredComics, key = { _, item -> item.id }) { index, comicItem ->
                                    ComicListItem(
                                        comic = comicItem,
                                        onClick = { onSelectComic(comicItem) },
                                        onLongClick = { showEditComicDialog = comicItem },
                                        showPlaceholderIcons = showPlaceholderIcons,
                                        modifier = Modifier.animateItemPlacement()
                                    )
                                    Divider(
                                        color = palette.primaryLight.copy(alpha = 0.35f),
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clip(shape50)
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }
                ContentType.MOVIE -> {
                    if (movies.isEmpty()) {
                        // Movie empty state
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    AppIcons.Movie,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = palette.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No movies yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Place MP4, MKV, or other movie files in your\nprofile's Movies folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.primary.copy(alpha = 0.5f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Filter and sort movies by playlist
                        val filteredMovies = remember(movies, selectedPlaylistFilter, sortOption) {
                            val filtered = if (selectedPlaylistFilter != null) {
                                movies.filter { it.seriesId == selectedPlaylistFilter }
                            } else {
                                movies
                            }

                            // Sort filtered movies
                            when (sortOption) {
                                SortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                                SortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase() }
                                SortOption.AUTHOR_AZ -> filtered.sortedBy { it.title.lowercase() }
                                SortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.dateAdded }
                                SortOption.RECENTLY_PLAYED -> filtered.sortedByDescending { it.lastPlayed }
                                SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
                                SortOption.BOOK_NUMBER -> filtered.sortedBy { it.seriesOrder }
                            }
                        }

                        // Flat movies list without series dividers
                        if (defaultLibraryView == "GRID_2") {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredMovies, key = { _, item -> item.id }) { index, movieItem ->
                                    MovieGridItem(
                                        movie = movieItem,
                                        onClick = { onSelectMovie(movieItem) },
                                        onLongClick = { showEditMovieDialog = movieItem },
                                        showPlaceholderIcons = showPlaceholderIcons
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(filteredMovies, key = { _, item -> item.id }) { index, movieItem ->
                                    MovieListItem(
                                        movie = movieItem,
                                        onClick = { onSelectMovie(movieItem) },
                                        onLongClick = { showEditMovieDialog = movieItem },
                                        showPlaceholderIcons = showPlaceholderIcons,
                                        modifier = Modifier.animateItemPlacement()
                                    )
                                    Divider(
                                        color = palette.primaryLight.copy(alpha = 0.35f),
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .clip(shape50)
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-width accent-colored rounded bar with text and icon inside
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesDivider(
    seriesName: String,
    itemCount: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    isPlaylist: Boolean = false,
    series: LibrarySeries? = null,
    onEditSeries: () -> Unit = {},
    onDeleteSeries: () -> Unit = {},
    onSeriesClick: (() -> Unit)? = null,
    onSetCoverArt: (() -> Unit)? = null,
    coverArtBitmap: android.graphics.Bitmap? = null
) {
    val palette = currentPalette()
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chevron_rotation"
    )

    // Full-width accent-colored rounded bar with content inside
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape12)
            .background(palette.accentGradient())
            .combinedClickable(
                onClick = {
                    // Click on main area opens playlist screen if available, otherwise toggle
                    if (onSeriesClick != null) {
                        onSeriesClick()
                    } else {
                        onToggle()
                    }
                },
                onLongClick = {
                    if (series != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Cover art or default icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(shape8)
                    .background(palette.shade7.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (coverArtBitmap != null) {
                    Image(
                        bitmap = coverArtBitmap.asImageBitmap(),
                        contentDescription = "Cover art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaylist) AppIcons.Playlist else AppIcons.Folder,
                        contentDescription = null,
                        tint = palette.shade9,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.shade9,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$itemCount ${if (itemCount == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.shade8
                )
            }
            // Chevron arrow - clickable separately for expand/collapse
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(shape8)
                    .clickable(
                        onClick = {
                            // Arrow always toggles expand/collapse
                            onToggle()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                    tint = palette.shade9,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { rotationZ = rotationAngle }
                )
            }
        }

        // Edit/Delete/Set Cover Art dropdown menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (onSetCoverArt != null) {
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                AppIcons.Image,
                                contentDescription = null,
                                tint = palette.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Set Cover Art", color = palette.textPrimary)
                        }
                    },
                    onClick = {
                        showMenu = false
                        onSetCoverArt()
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Rename Playlist", color = palette.textPrimary)
                    }
                },
                onClick = {
                    showMenu = false
                    onEditSeries()
                }
            )
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Delete Playlist", color = MaterialTheme.colorScheme.error)
                    }
                },
                onClick = {
                    showMenu = false
                    onDeleteSeries()
                }
            )
        }
    }
}

/**
 * Content Type Tab Switcher - Horizontal scrollable category selector with arrows
 * Shows 2 full items (Audiobooks + Books) with partial 3rd item visible to indicate more
 */
@Composable
private fun ContentTypeTabs(
    selectedContentType: ContentType,
    onContentTypeChange: (ContentType) -> Unit,
    audiobookCount: Int,
    bookCount: Int,
    musicCount: Int = 0,
    creepypastaCount: Int = 0,
    comicsCount: Int = 0,
    movieCount: Int = 0
) {
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive sizing for category tabs - scale based on screen size
    val tabHeight = when {
        screenWidth < 360.dp -> 36.dp
        screenWidth < 400.dp -> 40.dp
        screenWidth < 600.dp -> 44.dp
        else -> 48.dp
    }
    val arrowSize = when {
        screenWidth < 360.dp -> 24.dp
        screenWidth < 400.dp -> 28.dp
        else -> 32.dp
    }
    val iconSizeTab = when {
        screenWidth < 360.dp -> 14.dp
        screenWidth < 400.dp -> 16.dp
        else -> 18.dp
    }
    val fontSize = when {
        screenWidth < 360.dp -> 11.sp
        screenWidth < 400.dp -> 12.sp
        else -> 14.sp
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left arrow - always visible with accent background, cycles in reverse
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(arrowSize)
                .clip(CircleShape)
                .background(palette.accentGradient())
                .clickable {
                    coroutineScope.launch {
                        // Cycle in reverse: if can't scroll backward, wrap to end
                        val targetIndex = if (!listState.canScrollBackward) {
                            ContentType.entries.size - 1
                        } else {
                            listState.firstVisibleItemIndex - 1
                        }
                        listState.animateScrollToItem(targetIndex)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AppIcons.ChevronLeft,
                contentDescription = "Previous category",
                tint = palette.shade9,
                modifier = Modifier.size(arrowSize * 0.625f)
            )
        }

        // Use BoxWithConstraints to calculate proper item widths - always exactly 2 items
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            val availableWidth = maxWidth
            val spacing = 8.dp
            val horizontalPadding = 4.dp
            // Calculate width to fit exactly 2 full items - always fill the space
            // 2*itemWidth + 1*spacing + 2*horizontalPadding = availableWidth
            val itemWidth = (availableWidth - spacing - horizontalPadding * 2) / 2

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                userScrollEnabled = false // Disable free scrolling, only use arrows
            ) {
                items(ContentType.entries.size) { index ->
                    val type = ContentType.entries[index]
                    val isSelected = selectedContentType == type
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.95f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "tabScale"
                    )

                    // Get the count for this content type
                    val count = when (type) {
                        ContentType.AUDIOBOOK -> audiobookCount
                        ContentType.EBOOK -> bookCount
                        ContentType.MUSIC -> musicCount
                        ContentType.CREEPYPASTA -> creepypastaCount
                        ContentType.COMICS -> comicsCount
                        ContentType.MOVIE -> movieCount
                    }

                    // Get the icon for this content type
                    val icon = when (type) {
                        ContentType.AUDIOBOOK -> AppIcons.Audiobook
                        ContentType.EBOOK -> AppIcons.Book
                        ContentType.MUSIC -> AppIcons.Music
                        ContentType.CREEPYPASTA -> AppIcons.Music
                        ContentType.COMICS -> AppIcons.Comic
                        ContentType.MOVIE -> AppIcons.Movie
                    }

                    // Category tab with gradient backgrounds
                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(tabHeight)
                            .scale(scale)
                            .clip(shape12)
                            .background(
                                if (isSelected) palette.accentGradient()
                                else Brush.horizontalGradient(
                                    colors = listOf(
                                        palette.shade7.copy(alpha = 0.6f),
                                        palette.shade8.copy(alpha = 0.5f),
                                        palette.shade7.copy(alpha = 0.6f)
                                    )
                                )
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onContentTypeChange(type)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) palette.shade9 else palette.shade2,
                                modifier = Modifier.size(iconSizeTab)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = fontSize * 0.9f
                                ),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) palette.shade9 else palette.shade2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Right arrow - always visible with accent background, cycles forward
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(arrowSize)
                .clip(CircleShape)
                .background(palette.accentGradient())
                .clickable {
                    coroutineScope.launch {
                        // Cycle forward: if can't scroll forward anymore, wrap to start
                        val targetIndex = if (!listState.canScrollForward) {
                            0
                        } else {
                            listState.firstVisibleItemIndex + 1
                        }
                        listState.animateScrollToItem(targetIndex)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AppIcons.ChevronRight,
                contentDescription = "Next category",
                tint = palette.shade9,
                modifier = Modifier.size(arrowSize * 0.625f)
            )
        }
    }
}

@Composable
private fun AnimatedAudiobookListItem(
    audiobook: LibraryAudiobook,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlayingIndicator: Boolean,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(audiobook.id) {
        isVisible = true
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = (index * 30).coerceAtMost(300),
            easing = EaseOutCubic
        ),
        label = "itemAlpha"
    )

    val animatedOffset by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 20.dp,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = (index * 30).coerceAtMost(300),
            easing = EaseOutCubic
        ),
        label = "itemOffset"
    )

    AudiobookListItem(
        audiobook = audiobook,
        onClick = onClick,
        onLongClick = onLongClick,
        showPlayingIndicator = showPlayingIndicator,
        showPlaceholderIcons = showPlaceholderIcons,
        modifier = modifier
            .graphicsLayer {
                alpha = animatedAlpha
                translationX = animatedOffset.toPx()
            }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AudiobookListItem(
    audiobook: LibraryAudiobook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlayingIndicator: Boolean,
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive thumbnail size
    val thumbnailSize = when {
        screenWidth < 360.dp -> 56.dp
        screenWidth < 400.dp -> 64.dp
        screenWidth < 600.dp -> 72.dp
        else -> 80.dp
    }
    val iconSize = when {
        screenWidth < 400.dp -> 24.dp
        else -> 28.dp
    }

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 100.dp)
            .scale(scale)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape12)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = {
                    isPressed = true
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Art with shadow and gradient
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .shadow(4.dp, shape8)
                .clip(shape8)
                .background(palette.coverArtGradient()),
            contentAlignment = Alignment.Center
        ) {
            val usePlaceholder = showPlaceholderIcons || audiobook.coverArt == null
            if (usePlaceholder) {
                // Placeholder with headphones icon and file type
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        AppIcons.Audiobook,
                        contentDescription = null,
                        tint = palette.shade7.copy(alpha = 0.95f),
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = audiobook.fileType.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.shade7.copy(alpha = 0.9f)
                    )
                }
            } else {
                Image(
                    bitmap = audiobook.coverArt!!.asImageBitmap(),
                    contentDescription = audiobook.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // File type badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(palette.accentGradient(), shape4)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = audiobook.fileType.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    fontWeight = FontWeight.Bold,
                    color = palette.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Book info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = audiobook.author,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.primary.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (audiobook.duration > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDurationHoursMinutes(audiobook.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.5f)
                )
            }

            // Progress bar for audiobooks
            if (audiobook.duration > 0 && audiobook.lastPosition > 0) {
                val progress = (audiobook.lastPosition.toFloat() / audiobook.duration).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(shape3),
                        color = palette.accent,
                        trackColor = palette.accent.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Playing indicator
        if (showPlayingIndicator) {
            IconButton(onClick = onClick) {
                Icon(
                    AppIcons.VolumeUp,
                    contentDescription = "Currently playing",
                    tint = palette.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMetadataDialog(
    audiobook: LibraryAudiobook,
    categories: List<Category>,
    seriesList: List<LibrarySeries> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
    onSetSeries: (String?) -> Unit = {},
    onSetCoverArt: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddSeries: (String) -> Unit = {},
    onDeleteSeries: (String) -> Unit = {}
) {
    var title by remember { mutableStateOf(audiobook.title) }
    var author by remember { mutableStateOf(audiobook.author) }
    var selectedSeriesId by remember { mutableStateOf(audiobook.seriesId) }
    var showSeriesDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddSeriesDialog by remember { mutableStateOf(false) }
    var showDeleteSeriesConfirm by remember { mutableStateOf<LibrarySeries?>(null) }
    var newSeriesName by remember { mutableStateOf("") }
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)

    val selectedSeriesName = seriesList.find { it.id == selectedSeriesId }?.name ?: "None"

    // Delete Series Confirmation Dialog
    showDeleteSeriesConfirm?.let { seriesToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteSeriesConfirm = null },
            containerColor = palette.surface,
            title = { Text("Delete Series", color = palette.primary) },
            text = {
                Text(
                    "Delete \"${seriesToDelete.name}\"? Items in this series will be moved to \"None\" but won't be deleted.",
                    color = palette.textPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSeries(seriesToDelete.id)
                    if (selectedSeriesId == seriesToDelete.id) {
                        selectedSeriesId = null
                    }
                    showDeleteSeriesConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesConfirm = null }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Add Series Dialog
    if (showAddSeriesDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSeriesDialog = false
                newSeriesName = ""
            },
            containerColor = palette.surface,
            title = { Text("New Series", color = palette.primary) },
            text = {
                OutlinedTextField(
                    value = newSeriesName,
                    onValueChange = { newSeriesName = it },
                    label = { Text("Series Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSeriesName.isNotBlank()) {
                            onAddSeries(newSeriesName.trim())
                            newSeriesName = ""
                            showAddSeriesDialog = false
                        }
                    },
                    enabled = newSeriesName.isNotBlank()
                ) {
                    Text("Create", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSeriesDialog = false
                    newSeriesName = ""
                }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = palette.surface,
            title = { Text("Remove Audiobook", color = palette.primary) },
            text = { Text("Remove \"${audiobook.title}\" from your library?", color = palette.primary.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Audiobook Info",
                    color = palette.primary
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )

                // Series selection - always show (category removed for audiobooks)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Series/Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = showSeriesDropdown,
                    onExpandedChange = { showSeriesDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedSeriesName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSeriesDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.shade4,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showSeriesDropdown,
                        onDismissRequest = { showSeriesDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedSeriesId = null
                                showSeriesDropdown = false
                            }
                        )
                        // Add New Series option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = palette.accent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add New Series...", color = palette.accent)
                                }
                            },
                            onClick = {
                                showSeriesDropdown = false
                                showAddSeriesDialog = true
                            }
                        )
                        if (seriesList.isNotEmpty()) {
                            Divider(color = palette.divider, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        seriesList.forEach { series ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(series.name, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                showSeriesDropdown = false
                                                showDeleteSeriesConfirm = series
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                AppIcons.Delete,
                                                contentDescription = "Delete series",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedSeriesId = series.id
                                    showSeriesDropdown = false
                                }
                            )
                        }
                    }
                }

                // Set Cover Art button
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onSetCoverArt()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = palette.primary
                    ),
                    border = BorderStroke(1.dp, palette.shade4)
                ) {
                    Icon(
                        AppIcons.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Cover Art")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, author, null)
                    onSetSeries(selectedSeriesId)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save", color = palette.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.primary.copy(alpha = 0.7f))
            }
        }
    )
}

private fun formatDurationHoursMinutes(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        "$hours hr $minutes min"
    } else {
        "$minutes min"
    }
}

@Composable
private fun AnimatedBookListItem(
    book: LibraryBook,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(book.id) {
        isVisible = true
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = (index * 30).coerceAtMost(300),
            easing = EaseOutCubic
        ),
        label = "bookItemAlpha"
    )

    val animatedOffset by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 20.dp,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = (index * 30).coerceAtMost(300),
            easing = EaseOutCubic
        ),
        label = "bookItemOffset"
    )

    BookListItem(
        book = book,
        onClick = onClick,
        onLongClick = onLongClick,
        showPlaceholderIcons = showPlaceholderIcons,
        modifier = modifier
            .graphicsLayer {
                alpha = animatedAlpha
                translationX = animatedOffset.toPx()
            }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive thumbnail size
    val thumbnailSize = when {
        screenWidth < 360.dp -> 56.dp
        screenWidth < 400.dp -> 64.dp
        screenWidth < 600.dp -> 72.dp
        else -> 80.dp
    }
    val iconSize = when {
        screenWidth < 400.dp -> 24.dp
        else -> 28.dp
    }

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bookPressScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 100.dp)
            .scale(scale)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape12)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = {
                    isPressed = true
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Book Cover placeholder with gradient and file type badge
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .shadow(4.dp, shape8)
                .clip(shape8)
                .background(palette.coverArtGradient()),
            contentAlignment = Alignment.Center
        ) {
            val usePlaceholder = showPlaceholderIcons || book.coverArt == null
            if (usePlaceholder) {
                // Placeholder with book icon and file type
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        AppIcons.Book,
                        contentDescription = null,
                        tint = palette.shade7.copy(alpha = 0.95f),
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = book.fileType.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.shade7.copy(alpha = 0.9f)
                    )
                }
            } else {
                Image(
                    bitmap = book.coverArt!!.asImageBitmap(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // File type badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(palette.accentGradient(), shape4)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = book.fileType.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    fontWeight = FontWeight.Bold,
                    color = palette.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Book info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.primary.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Reading progress
            if (book.totalPages > 0 && book.currentPage > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = book.progress,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(shape3),
                        color = palette.primary,
                        trackColor = palette.primary.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Chevron or reading indicator
        if (book.lastRead > 0 && !book.isCompleted) {
            Icon(
                AppIcons.AutoStories,
                contentDescription = "Reading",
                tint = palette.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MusicListItem(
    music: LibraryMusic,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive thumbnail size
    val thumbnailSize = when {
        screenWidth < 360.dp -> 56.dp
        screenWidth < 400.dp -> 64.dp
        screenWidth < 600.dp -> 72.dp
        else -> 80.dp
    }
    val iconSize = when {
        screenWidth < 400.dp -> 24.dp
        else -> 28.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 100.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape12)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover placeholder with gradient and music icon
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .shadow(4.dp, shape8)
                .clip(shape8)
                .background(palette.coverArtGradient()),
            contentAlignment = Alignment.Center
        ) {
            val usePlaceholder = showPlaceholderIcons || music.coverArt == null
            if (usePlaceholder) {
                // Placeholder with music icon and file type
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        AppIcons.Music,
                        contentDescription = null,
                        tint = palette.shade7.copy(alpha = 0.95f),
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = music.fileType.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.shade7.copy(alpha = 0.9f)
                    )
                }
            } else {
                Image(
                    bitmap = music.coverArt!!.asImageBitmap(),
                    contentDescription = music.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // File type badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(palette.accentGradient(), shape4)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = music.fileType.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    fontWeight = FontWeight.Bold,
                    color = palette.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Music info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = music.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.primary.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (music.album != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = music.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (music.duration > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDurationHoursMinutes(music.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Times listened: ${music.timesListened}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.primary
            )
        }

        // Playing indicator
        if (music.lastPlayed > 0 && !music.isCompleted) {
            Icon(
                AppIcons.PlayCircle,
                contentDescription = "Last played",
                tint = palette.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBookMetadataDialog(
    book: LibraryBook,
    seriesList: List<LibrarySeries> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSetSeries: (String?) -> Unit = {},
    onSetCoverArt: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddSeries: (String) -> Unit = {},
    onDeleteSeries: (String) -> Unit = {}
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var selectedSeriesId by remember { mutableStateOf(book.seriesId) }
    var showSeriesDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddSeriesDialog by remember { mutableStateOf(false) }
    var showDeleteSeriesConfirm by remember { mutableStateOf<LibrarySeries?>(null) }
    var newSeriesName by remember { mutableStateOf("") }
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val selectedSeriesName = seriesList.find { it.id == selectedSeriesId }?.name ?: "None"

    // Delete Series Confirmation Dialog
    showDeleteSeriesConfirm?.let { seriesToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteSeriesConfirm = null },
            containerColor = palette.surface,
            title = { Text("Delete Series", color = palette.primary) },
            text = {
                Text(
                    "Delete \"${seriesToDelete.name}\"? Items in this series will be moved to \"None\" but won't be deleted.",
                    color = palette.textPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSeries(seriesToDelete.id)
                    if (selectedSeriesId == seriesToDelete.id) {
                        selectedSeriesId = null
                    }
                    showDeleteSeriesConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesConfirm = null }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Add Series Dialog
    if (showAddSeriesDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSeriesDialog = false
                newSeriesName = ""
            },
            containerColor = palette.surface,
            title = { Text("New Series", color = palette.primary) },
            text = {
                OutlinedTextField(
                    value = newSeriesName,
                    onValueChange = { newSeriesName = it },
                    label = { Text("Series Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSeriesName.isNotBlank()) {
                            onAddSeries(newSeriesName.trim())
                            newSeriesName = ""
                            showAddSeriesDialog = false
                        }
                    },
                    enabled = newSeriesName.isNotBlank()
                ) {
                    Text("Create", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSeriesDialog = false
                    newSeriesName = ""
                }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = palette.surface,
            title = { Text("Remove Book", color = palette.primary) },
            text = { Text("Remove \"${book.title}\" from your library?", color = palette.primary.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Book Info",
                    color = palette.primary
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )

                // Series selection - always show
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Series/Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = showSeriesDropdown,
                    onExpandedChange = { showSeriesDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedSeriesName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSeriesDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.shade4,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showSeriesDropdown,
                        onDismissRequest = { showSeriesDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedSeriesId = null
                                showSeriesDropdown = false
                            }
                        )
                        // Add New Series option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = palette.accent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add New Series...", color = palette.accent)
                                }
                            },
                            onClick = {
                                showSeriesDropdown = false
                                showAddSeriesDialog = true
                            }
                        )
                        if (seriesList.isNotEmpty()) {
                            Divider(color = palette.divider, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        seriesList.forEach { series ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(series.name, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                showSeriesDropdown = false
                                                showDeleteSeriesConfirm = series
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                AppIcons.Delete,
                                                contentDescription = "Delete series",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedSeriesId = series.id
                                    showSeriesDropdown = false
                                }
                            )
                        }
                    }
                }

                // Set Cover Art button
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onSetCoverArt()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = palette.primary
                    ),
                    border = BorderStroke(1.dp, palette.shade4)
                ) {
                    Icon(
                        AppIcons.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Cover Art")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, author)
                    onSetSeries(selectedSeriesId)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save", color = palette.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.primary.copy(alpha = 0.7f))
            }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ComicListItem(
    comic: LibraryComic,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive thumbnail size
    val thumbnailSize = when {
        screenWidth < 360.dp -> 56.dp
        screenWidth < 400.dp -> 64.dp
        screenWidth < 600.dp -> 72.dp
        else -> 80.dp
    }
    val iconSize = when {
        screenWidth < 400.dp -> 24.dp
        else -> 28.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 100.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape12)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover placeholder with gradient and comic icon
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .shadow(4.dp, shape8)
                .clip(shape8)
                .background(palette.coverArtGradient()),
            contentAlignment = Alignment.Center
        ) {
            val usePlaceholder = showPlaceholderIcons || comic.coverArt == null
            if (usePlaceholder) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        AppIcons.Comic,
                        contentDescription = null,
                        tint = palette.shade7.copy(alpha = 0.95f),
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = comic.fileType.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.shade7.copy(alpha = 0.9f)
                    )
                }
            } else {
                Image(
                    bitmap = comic.coverArt!!.asImageBitmap(),
                    contentDescription = comic.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // File type badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(palette.accentGradient(), shape4)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = comic.fileType.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    fontWeight = FontWeight.Bold,
                    color = palette.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Comic info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comic.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (comic.series != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = comic.series,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.primary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (comic.totalPages > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${comic.totalPages} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.5f)
                )
            }

            // Progress bar for comics
            if (comic.totalPages > 0 && comic.currentPage > 0) {
                val progress = (comic.currentPage.toFloat() / comic.totalPages).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(shape3),
                        color = palette.accent,
                        trackColor = palette.accent.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Reading indicator
        if (comic.currentPage > 0) {
            Icon(
                AppIcons.AutoStories,
                contentDescription = "In progress",
                tint = palette.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MovieListItem(
    movie: LibraryMovie,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showPlaceholderIcons: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)
    val shape4 = cornerRadius(4.dp)
    val shape8 = cornerRadius(8.dp)
    val shape12 = cornerRadius(12.dp)
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive thumbnail size
    val thumbnailSize = when {
        screenWidth < 360.dp -> 56.dp
        screenWidth < 400.dp -> 64.dp
        screenWidth < 600.dp -> 72.dp
        else -> 80.dp
    }
    val iconSize = when {
        screenWidth < 400.dp -> 24.dp
        else -> 28.dp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 100.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape12)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover placeholder with gradient and movie icon
        Box(
            modifier = Modifier
                .size(thumbnailSize)
                .shadow(4.dp, shape8)
                .clip(shape8)
                .background(palette.coverArtGradient()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    AppIcons.Movie,
                    contentDescription = null,
                    tint = palette.shade7.copy(alpha = 0.95f),
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = movie.fileType.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = palette.shade7.copy(alpha = 0.9f)
                )
            }

            // File type badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(palette.accentGradient(), shape4)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = movie.fileType.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    fontWeight = FontWeight.Bold,
                    color = palette.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Movie info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (movie.duration > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDurationHoursMinutes(movie.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.5f)
                )
            }

            // Progress bar for movies
            if (movie.duration > 0 && movie.lastPosition > 0) {
                val progress = (movie.lastPosition.toFloat() / movie.duration).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(shape3),
                        color = palette.accent,
                        trackColor = palette.accent.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Watch indicator
        if (movie.lastPosition > 0 && movie.lastPosition < movie.duration) {
            Icon(
                AppIcons.PlayCircle,
                contentDescription = "In progress",
                tint = palette.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Styled sort menu dialog - square shape, theme colors, accent text
 */
@Composable
fun SortMenuDialog(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismiss: () -> Unit,
    playlists: List<LibrarySeries> = emptyList(),
    selectedPlaylist: String? = null,
    onPlaylistSelected: (String?) -> Unit = {}
) {
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = shape8,
        title = {
            Text(
                text = "Sort & Filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column - Sort Options
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SortOption.entries.forEach { option ->
                            val isSelected = option == currentSort
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape6)
                                    .background(
                                        if (isSelected) palette.accent.copy(alpha = 0.2f)
                                        else palette.surfaceMedium
                                    )
                                    .clickable {
                                        onSortSelected(option)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) palette.accent else palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelected) {
                                    Icon(
                                        AppIcons.Check,
                                        contentDescription = "Selected",
                                        tint = palette.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(palette.primary.copy(alpha = 0.2f))
                )

                // Right Column - Filter Options
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "Filter",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // All option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape6)
                                .background(
                                    if (selectedPlaylist == null) palette.accent.copy(alpha = 0.2f)
                                    else palette.surfaceMedium
                                )
                                .clickable {
                                    onPlaylistSelected(null)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    AppIcons.GridView,
                                    contentDescription = null,
                                    tint = if (selectedPlaylist == null) palette.accent else palette.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "All",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedPlaylist == null) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedPlaylist == null) palette.accent else palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selectedPlaylist == null) {
                                Icon(
                                    AppIcons.Check,
                                    contentDescription = "Selected",
                                    tint = palette.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Playlist options
                        playlists.forEach { playlist ->
                            val isSelected = selectedPlaylist == playlist.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape6)
                                    .background(
                                        if (isSelected) palette.accent.copy(alpha = 0.2f)
                                        else palette.surfaceMedium
                                    )
                                    .clickable {
                                        onPlaylistSelected(playlist.id)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        AppIcons.Playlist,
                                        contentDescription = null,
                                        tint = if (isSelected) palette.accent else palette.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) palette.accent else palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        AppIcons.Check,
                                        contentDescription = "Selected",
                                        tint = palette.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Empty state if no playlists
                        if (playlists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No playlists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.accent)
            }
        }
    )
}

/**
 * Edit Music Metadata Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMusicMetadataDialog(
    music: LibraryMusic,
    seriesList: List<LibrarySeries> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSetSeries: (String?) -> Unit = {},
    onSetCoverArt: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddSeries: (String) -> Unit = {},
    onDeleteSeries: (String) -> Unit = {}
) {
    var title by remember { mutableStateOf(music.title) }
    var artist by remember { mutableStateOf(music.artist) }
    var selectedSeriesId by remember { mutableStateOf(music.seriesId) }
    var showSeriesDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddSeriesDialog by remember { mutableStateOf(false) }
    var showDeleteSeriesConfirm by remember { mutableStateOf<LibrarySeries?>(null) }
    var newSeriesName by remember { mutableStateOf("") }
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val selectedSeriesName = seriesList.find { it.id == selectedSeriesId }?.name ?: "None"

    // Delete Playlist Confirmation Dialog
    showDeleteSeriesConfirm?.let { seriesToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteSeriesConfirm = null },
            containerColor = palette.surface,
            title = { Text("Delete Playlist", color = palette.primary) },
            text = {
                Text(
                    "Delete \"${seriesToDelete.name}\"? Tracks in this playlist will be moved to \"None\" but won't be deleted.",
                    color = palette.textPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSeries(seriesToDelete.id)
                    if (selectedSeriesId == seriesToDelete.id) {
                        selectedSeriesId = null
                    }
                    showDeleteSeriesConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesConfirm = null }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Add Playlist Dialog
    if (showAddSeriesDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSeriesDialog = false
                newSeriesName = ""
            },
            containerColor = palette.surface,
            title = { Text("New Playlist", color = palette.primary) },
            text = {
                OutlinedTextField(
                    value = newSeriesName,
                    onValueChange = { newSeriesName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSeriesName.isNotBlank()) {
                            onAddSeries(newSeriesName.trim())
                            newSeriesName = ""
                            showAddSeriesDialog = false
                        }
                    },
                    enabled = newSeriesName.isNotBlank()
                ) {
                    Text("Create", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSeriesDialog = false
                    newSeriesName = ""
                }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = palette.surface,
            title = { Text("Remove Music", color = palette.primary) },
            text = { Text("Remove \"${music.title}\" from your library?", color = palette.primary.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Music Info",
                    color = palette.primary
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )

                // Series/Playlist selection - always show
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = showSeriesDropdown,
                    onExpandedChange = { showSeriesDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedSeriesName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSeriesDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.shade4,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showSeriesDropdown,
                        onDismissRequest = { showSeriesDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedSeriesId = null
                                showSeriesDropdown = false
                            }
                        )
                        // Add New Playlist option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = palette.accent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add New Playlist...", color = palette.accent)
                                }
                            },
                            onClick = {
                                showSeriesDropdown = false
                                showAddSeriesDialog = true
                            }
                        )
                        if (seriesList.isNotEmpty()) {
                            Divider(color = palette.divider, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        seriesList.forEach { series ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(series.name, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                showSeriesDropdown = false
                                                showDeleteSeriesConfirm = series
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                AppIcons.Delete,
                                                contentDescription = "Delete playlist",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedSeriesId = series.id
                                    showSeriesDropdown = false
                                }
                            )
                        }
                    }
                }

                // Set Cover Art button
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onSetCoverArt()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = palette.primary
                    ),
                    border = BorderStroke(1.dp, palette.shade4)
                ) {
                    Icon(
                        AppIcons.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Cover Art")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, artist)
                    onSetSeries(selectedSeriesId)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save", color = palette.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.primary.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Edit Comic Metadata Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditComicMetadataDialog(
    comic: LibraryComic,
    seriesList: List<LibrarySeries> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSetSeries: (String?) -> Unit = {},
    onSetCoverArt: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddSeries: (String) -> Unit = {},
    onDeleteSeries: (String) -> Unit = {}
) {
    var title by remember { mutableStateOf(comic.title) }
    var author by remember { mutableStateOf(comic.series ?: "") }
    var selectedSeriesId by remember { mutableStateOf(comic.seriesId) }
    var showSeriesDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddSeriesDialog by remember { mutableStateOf(false) }
    var showDeleteSeriesConfirm by remember { mutableStateOf<LibrarySeries?>(null) }
    var newSeriesName by remember { mutableStateOf("") }
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val selectedSeriesName = seriesList.find { it.id == selectedSeriesId }?.name ?: "None"

    // Delete Collection Confirmation Dialog
    showDeleteSeriesConfirm?.let { seriesToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteSeriesConfirm = null },
            containerColor = palette.surface,
            title = { Text("Delete Collection", color = palette.primary) },
            text = {
                Text(
                    "Delete \"${seriesToDelete.name}\"? Comics in this collection will be moved to \"None\" but won't be deleted.",
                    color = palette.textPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSeries(seriesToDelete.id)
                    if (selectedSeriesId == seriesToDelete.id) {
                        selectedSeriesId = null
                    }
                    showDeleteSeriesConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesConfirm = null }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Add Series Dialog
    if (showAddSeriesDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSeriesDialog = false
                newSeriesName = ""
            },
            containerColor = palette.surface,
            title = { Text("New Collection", color = palette.primary) },
            text = {
                OutlinedTextField(
                    value = newSeriesName,
                    onValueChange = { newSeriesName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSeriesName.isNotBlank()) {
                            onAddSeries(newSeriesName.trim())
                            newSeriesName = ""
                            showAddSeriesDialog = false
                        }
                    },
                    enabled = newSeriesName.isNotBlank()
                ) {
                    Text("Create", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSeriesDialog = false
                    newSeriesName = ""
                }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = palette.surface,
            title = { Text("Remove Comic", color = palette.primary) },
            text = { Text("Remove \"${comic.title}\" from your library?", color = palette.primary.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Comic Info",
                    color = palette.primary
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Series/Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )

                // Series/Collection selection - always show
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Collection/Divider",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = showSeriesDropdown,
                    onExpandedChange = { showSeriesDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedSeriesName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSeriesDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.shade4,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showSeriesDropdown,
                        onDismissRequest = { showSeriesDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedSeriesId = null
                                showSeriesDropdown = false
                            }
                        )
                        // Add New Collection option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = palette.accent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add New Collection...", color = palette.accent)
                                }
                            },
                            onClick = {
                                showSeriesDropdown = false
                                showAddSeriesDialog = true
                            }
                        )
                        if (seriesList.isNotEmpty()) {
                            Divider(color = palette.divider, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        seriesList.forEach { series ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(series.name, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                showSeriesDropdown = false
                                                showDeleteSeriesConfirm = series
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                AppIcons.Delete,
                                                contentDescription = "Delete collection",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedSeriesId = series.id
                                    showSeriesDropdown = false
                                }
                            )
                        }
                    }
                }

                // Set Cover Art button
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onSetCoverArt()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = palette.primary
                    ),
                    border = BorderStroke(1.dp, palette.shade4)
                ) {
                    Icon(
                        AppIcons.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Cover Art")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, author)
                    onSetSeries(selectedSeriesId)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save", color = palette.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.primary.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Edit Movie Metadata Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMovieMetadataDialog(
    movie: LibraryMovie,
    seriesList: List<LibrarySeries> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSetSeries: (String?) -> Unit = {},
    onSetCoverArt: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddSeries: (String) -> Unit = {},
    onDeleteSeries: (String) -> Unit = {}
) {
    var title by remember { mutableStateOf(movie.title) }
    var selectedSeriesId by remember { mutableStateOf(movie.seriesId) }
    var showSeriesDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddSeriesDialog by remember { mutableStateOf(false) }
    var showDeleteSeriesConfirm by remember { mutableStateOf<LibrarySeries?>(null) }
    var newSeriesName by remember { mutableStateOf("") }
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val selectedSeriesName = seriesList.find { it.id == selectedSeriesId }?.name ?: "None"

    // Delete Playlist Confirmation Dialog
    showDeleteSeriesConfirm?.let { seriesToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteSeriesConfirm = null },
            containerColor = palette.surface,
            title = { Text("Delete Playlist", color = palette.primary) },
            text = {
                Text(
                    "Delete \"${seriesToDelete.name}\"? Movies in this playlist will be moved to \"None\" but won't be deleted.",
                    color = palette.textPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSeries(seriesToDelete.id)
                    if (selectedSeriesId == seriesToDelete.id) {
                        selectedSeriesId = null
                    }
                    showDeleteSeriesConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSeriesConfirm = null }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Add Playlist Dialog
    if (showAddSeriesDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSeriesDialog = false
                newSeriesName = ""
            },
            containerColor = palette.surface,
            title = { Text("New Playlist", color = palette.primary) },
            text = {
                OutlinedTextField(
                    value = newSeriesName,
                    onValueChange = { newSeriesName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newSeriesName.isNotBlank()) {
                            onAddSeries(newSeriesName.trim())
                            newSeriesName = ""
                            showAddSeriesDialog = false
                        }
                    },
                    enabled = newSeriesName.isNotBlank()
                ) {
                    Text("Create", color = palette.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSeriesDialog = false
                    newSeriesName = ""
                }) {
                    Text("Cancel", color = palette.textMuted)
                }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = palette.surface,
            title = { Text("Remove Movie", color = palette.primary) },
            text = { Text("Remove \"${movie.title}\" from your library?", color = palette.primary.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Movie Info",
                    color = palette.primary
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.shade4,
                        cursorColor = palette.primary,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )

                // Series/Playlist selection - always show
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Playlist/Collection",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = showSeriesDropdown,
                    onExpandedChange = { showSeriesDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedSeriesName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSeriesDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.shade4,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showSeriesDropdown,
                        onDismissRequest = { showSeriesDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedSeriesId = null
                                showSeriesDropdown = false
                            }
                        )
                        // Add New Playlist option
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        AppIcons.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = palette.accent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add New Playlist...", color = palette.accent)
                                }
                            },
                            onClick = {
                                showSeriesDropdown = false
                                showAddSeriesDialog = true
                            }
                        )
                        if (seriesList.isNotEmpty()) {
                            Divider(color = palette.divider, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        seriesList.forEach { series ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(series.name, modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                showSeriesDropdown = false
                                                showDeleteSeriesConfirm = series
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                AppIcons.Delete,
                                                contentDescription = "Delete playlist",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedSeriesId = series.id
                                    showSeriesDropdown = false
                                }
                            )
                        }
                    }
                }

                // Set Cover Art button
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onSetCoverArt()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = palette.primary
                    ),
                    border = BorderStroke(1.dp, palette.shade4)
                ) {
                    Icon(
                        AppIcons.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Cover Art")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title)
                    onSetSeries(selectedSeriesId)
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save", color = palette.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.primary.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Series Divider - Full-width accent-colored rounded bar with content inside
 * Scales properly for all device screen sizes
 * Supports collapse/expand functionality
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesDivider(
    series: LibrarySeries?,
    itemCount: Int,
    isExpanded: Boolean = true,
    onToggleExpand: () -> Unit = {},
    onAddSeries: () -> Unit = {},
    onEditSeries: () -> Unit = {},
    onDeleteSeries: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape10 = cornerRadius(10.dp)
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val haptic = LocalHapticFeedback.current

    // Animation for the expand/collapse arrow
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 200),
        label = "expandArrow"
    )

    // Responsive sizing
    val fontSize = when {
        screenWidth < 360.dp -> 14.sp
        screenWidth < 400.dp -> 15.sp
        screenWidth < 600.dp -> 16.sp
        else -> 18.sp
    }
    val iconSize = when {
        screenWidth < 400.dp -> 20.dp
        else -> 22.dp
    }

    var showMenu by remember { mutableStateOf(false) }

    // Full-width accent-colored rounded bar with content inside
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape10)
            .background(palette.accentGradient())
            .combinedClickable(
                onClick = { onToggleExpand() },
                onLongClick = {
                    if (series != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                }
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Folder icon
            Icon(
                AppIcons.Folder,
                contentDescription = null,
                tint = palette.onPrimary,
                modifier = Modifier.size(iconSize)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Series name (or "Uncategorized" for null)
            val seriesName = series?.name ?: "Uncategorized"
            val adjustedFontSize = when {
                seriesName.length > 28 -> fontSize * 0.8f
                seriesName.length > 20 -> fontSize * 0.9f
                else -> fontSize
            }
            Text(
                text = seriesName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = adjustedFontSize,
                    lineHeight = adjustedFontSize * 1.05f
                ),
                fontWeight = FontWeight.Bold,
                color = palette.onPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Item count
            Text(
                text = "($itemCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onPrimary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Expand/collapse arrow
            Icon(
                AppIcons.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = palette.onPrimary,
                modifier = Modifier
                    .size(iconSize + 4.dp)
                    .graphicsLayer { rotationZ = rotationAngle }
            )
        }

        // Edit/Delete dropdown menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.Edit,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(iconSize)
                        )
                        Text("Rename Series", color = palette.textPrimary)
                    }
                },
                onClick = {
                    showMenu = false
                    onEditSeries()
                }
            )
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(iconSize)
                        )
                        Text("Delete Series", color = MaterialTheme.colorScheme.error)
                    }
                },
                onClick = {
                    showMenu = false
                    onDeleteSeries()
                }
            )
        }
    }
}

/**
 * Horizontally scrollable series section with items
 * Supports collapse/expand functionality to hide/show items
 */
@Composable
fun <T> SeriesSection(
    series: LibrarySeries?,
    items: List<T>,
    isExpanded: Boolean = true,
    onToggleExpand: () -> Unit = {},
    onAddSeries: () -> Unit = {},
    onEditSeries: (LibrarySeries) -> Unit = {},
    onDeleteSeries: (LibrarySeries) -> Unit = {},
    itemContent: @Composable (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive item height for horizontal scroll
    val itemHeight = when {
        screenWidth < 360.dp -> 100.dp
        screenWidth < 400.dp -> 110.dp
        screenWidth < 600.dp -> 120.dp
        else -> 130.dp
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Series divider header with collapse/expand support
        SeriesDivider(
            series = series,
            itemCount = items.size,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            onAddSeries = onAddSeries,
            onEditSeries = { series?.let { onEditSeries(it) } },
            onDeleteSeries = { series?.let { onDeleteSeries(it) } }
        )

        // Horizontally scrollable items - only show when expanded
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(palette.background),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items.size, key = { it }) { index ->
                    itemContent(items[index])
                }
            }
        }
    }
}

/**
 * Add Series Dialog
 */
@Composable
fun AddSeriesDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var seriesName by remember { mutableStateOf("") }
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = shape12,
        title = {
            Text(
                text = "Add Series",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        },
        text = {
            OutlinedTextField(
                value = seriesName,
                onValueChange = { seriesName = it },
                label = { Text("Series Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.shade4,
                    cursorColor = palette.accent,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (seriesName.isNotBlank()) {
                        onConfirm(seriesName.trim())
                    }
                },
                enabled = seriesName.isNotBlank()
            ) {
                Text("Add", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textMuted)
            }
        }
    )
}

/**
 * Rename Series Dialog
 */
@Composable
fun RenameSeriesDialog(
    series: LibrarySeries,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var seriesName by remember { mutableStateOf(series.name) }
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = shape12,
        title = {
            Text(
                text = "Rename Series",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        },
        text = {
            OutlinedTextField(
                value = seriesName,
                onValueChange = { seriesName = it },
                label = { Text("Series Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.shade4,
                    cursorColor = palette.accent,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (seriesName.isNotBlank()) {
                        onConfirm(seriesName.trim())
                    }
                },
                enabled = seriesName.isNotBlank()
            ) {
                Text("Save", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textMuted)
            }
        }
    )
}

/**
 * Delete Series Confirmation Dialog
 */
@Composable
fun DeleteSeriesDialog(
    series: LibrarySeries,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = shape12,
        title = {
            Text(
                text = "Delete Series",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = "Delete \"${series.name}\"? Items in this series will become uncategorized.",
                color = palette.textPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textMuted)
            }
        }
    )
}

/**
 * Assign to Series Dialog - allows user to assign an item to a series
 */
@Composable
fun AssignSeriesDialog(
    seriesList: List<LibrarySeries>,
    currentSeriesId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onAddNewSeries: () -> Unit
) {
    val palette = currentPalette()
    val shape4 = cornerRadius(4.dp)
    val shape12 = cornerRadius(12.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = shape12,
        title = {
            Text(
                text = "Assign to Series",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // None option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape4)
                        .background(
                            if (currentSeriesId == null) palette.accent.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(null) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "None (Uncategorized)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (currentSeriesId == null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (currentSeriesId == null) palette.accent else palette.textPrimary
                    )
                    if (currentSeriesId == null) {
                        Icon(
                            AppIcons.Check,
                            contentDescription = "Selected",
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Series options
                seriesList.forEach { s ->
                    val isSelected = s.id == currentSeriesId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape4)
                            .background(
                                if (isSelected) palette.accent.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(s.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = s.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) palette.accent else palette.textPrimary
                        )
                        if (isSelected) {
                            Icon(
                                AppIcons.Check,
                                contentDescription = "Selected",
                                tint = palette.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Add new series button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape4)
                        .clickable { onAddNewSeries() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        AppIcons.Add,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Add New Series",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textMuted)
            }
        }
    )
}

/**
 * Grid item for audiobooks - shows cover art on top, title, author, progress below
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudiobookGridItem(
    audiobook: LibraryAudiobook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlayingIndicator: Boolean,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape8)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(8.dp)
    ) {
        // Cover art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            CoverArt(
                bitmap = audiobook.coverArt,
                contentDescription = audiobook.title,
                modifier = Modifier.fillMaxWidth(),
                cornerRadiusSize = 6.dp,
                elevation = 0.dp,
                showPlaceholderAlways = showPlaceholderIcons && audiobook.coverArt == null,
                fileExtension = audiobook.fileType,
                contentType = CoverArtContentType.AUDIOBOOK
            )

            // Progress indicator
            if (audiobook.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    LinearProgressIndicator(
                        progress = audiobook.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = palette.primary,
                        trackColor = palette.primary.copy(alpha = 0.2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = audiobook.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Author
        Text(
            text = audiobook.author,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Grid item for books
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape8)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(8.dp)
    ) {
        // Cover art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            CoverArt(
                bitmap = book.coverArt,
                contentDescription = book.title,
                modifier = Modifier.fillMaxWidth(),
                cornerRadiusSize = 6.dp,
                elevation = 0.dp,
                showPlaceholderAlways = showPlaceholderIcons && book.coverArt == null,
                fileExtension = book.fileType,
                contentType = CoverArtContentType.EBOOK
            )

            // Progress indicator
            if (book.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    LinearProgressIndicator(
                        progress = book.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = palette.primary,
                        trackColor = palette.primary.copy(alpha = 0.2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Author
        Text(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Grid item for music
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicGridItem(
    music: LibraryMusic,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape8)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(8.dp)
    ) {
        // Cover art
        CoverArt(
            bitmap = music.coverArt,
            contentDescription = music.title,
            modifier = Modifier.fillMaxWidth(),
            cornerRadiusSize = 6.dp,
            elevation = 0.dp,
            showPlaceholderAlways = showPlaceholderIcons && music.coverArt == null,
            fileExtension = music.fileType,
            contentType = CoverArtContentType.MUSIC
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = music.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Artist
        Text(
            text = music.artist,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Grid item for comics
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicGridItem(
    comic: LibraryComic,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape8)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(8.dp)
    ) {
        // Cover art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            CoverArt(
                bitmap = comic.coverArt,
                contentDescription = comic.title,
                modifier = Modifier.fillMaxWidth(),
                cornerRadiusSize = 6.dp,
                elevation = 0.dp,
                showPlaceholderAlways = showPlaceholderIcons && comic.coverArt == null,
                fileExtension = comic.fileType,
                contentType = CoverArtContentType.COMICS
            )

            // Progress indicator
            if (comic.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    LinearProgressIndicator(
                        progress = comic.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = palette.primary,
                        trackColor = palette.primary.copy(alpha = 0.2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = comic.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Author
        Text(
            text = comic.author,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Grid item for movies
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieGridItem(
    movie: LibraryMovie,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showPlaceholderIcons: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape6 = cornerRadius(6.dp)
    val shape8 = cornerRadius(8.dp)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape8)
            .background(palette.surfaceDark.copy(alpha = 0.08f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(8.dp)
    ) {
        // Cover art - movies use thumbnailUri
        val bitmap = remember(movie.thumbnailUri) {
            if (movie.thumbnailUri != null) {
                try {
                    context.contentResolver.openInputStream(movie.thumbnailUri)?.use { inputStream ->
                        android.graphics.BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            CoverArt(
                bitmap = bitmap,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxWidth(),
                cornerRadiusSize = 6.dp,
                elevation = 0.dp,
                showPlaceholderAlways = showPlaceholderIcons && bitmap == null,
                fileExtension = movie.fileType,
                contentType = CoverArtContentType.MOVIE
            )

            // Progress indicator
            if (movie.progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    LinearProgressIndicator(
                        progress = movie.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = palette.primary,
                        trackColor = palette.primary.copy(alpha = 0.2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = movie.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
