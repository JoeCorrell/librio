package com.librio.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import com.librio.ui.theme.AnimationDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.ui.theme.AppIcons
import com.librio.ui.theme.CornerSize
import com.librio.ui.theme.Elevation
import com.librio.ui.theme.IconSize
import com.librio.ui.theme.Spacing
import com.librio.ui.theme.ThumbnailSize
import com.librio.ui.theme.cornerRadius
import com.librio.ui.theme.currentPalette
import com.librio.ui.theme.thumbnailGradient

/**
 * Unified list item component for all content types
 * Uses Material 3 ElevatedCard with consistent styling
 *
 * Replaces: AudiobookListItem, BookListItem, MusicListItem, ComicListItem, MovieListItem
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentListItem(
    title: String,
    subtitle: String,
    coverArt: Bitmap?,
    contentType: CoverArtContentType,
    fileType: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    duration: String? = null,
    isPlaying: Boolean = false,
    showPlaceholderIcons: Boolean = true
) {
    val palette = currentPalette()
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Responsive thumbnail size
    val thumbnailSize = when {
        screenWidth < 360.dp -> ThumbnailSize.sm
        screenWidth < 400.dp -> ThumbnailSize.md
        screenWidth < 600.dp -> ThumbnailSize.lg
        else -> ThumbnailSize.xl
    }

    val iconSize = when {
        screenWidth < 400.dp -> IconSize.md
        else -> IconSize.lg
    }

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) AnimationDefaults.ItemPressScale else 1f,
        animationSpec = AnimationDefaults.snappySpring(),
        label = "pressScale"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .scale(scale),
        shape = cornerRadius(CornerSize.lg),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = Elevation.md
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = palette.shade10
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 88.dp)
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
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover Art thumbnail
            ContentThumbnail(
                coverArt = coverArt,
                contentType = contentType,
                fileType = fileType,
                size = thumbnailSize,
                iconSize = iconSize,
                showPlaceholderIcons = showPlaceholderIcons
            )

            Spacer(modifier = Modifier.width(Spacing.lg))

            // Content info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Duration or metadata
                if (duration != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }

                // Progress bar
                if (progress > 0f) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        LibrioProgressBar(
                            progress = progress,
                            modifier = Modifier.weight(1f),
                            height = 4.dp,
                            activeColor = palette.accent,
                            trackColor = palette.accent.copy(alpha = 0.2f)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Playing indicator
            if (isPlaying) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                Icon(
                    imageVector = AppIcons.VolumeUp,
                    contentDescription = "Now playing",
                    tint = palette.accent,
                    modifier = Modifier.size(IconSize.sm)
                )
            }
        }
    }
}

/**
 * Thumbnail component for list and grid items
 */
@Composable
fun ContentThumbnail(
    coverArt: Bitmap?,
    contentType: CoverArtContentType,
    fileType: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp = IconSize.md,
    showPlaceholderIcons: Boolean = true
) {
    val palette = currentPalette()
    val shape = cornerRadius(CornerSize.md)

    Box(
        modifier = Modifier
            .size(size)
            .shadow(Elevation.md, shape)
            .clip(shape)
            .background(palette.thumbnailGradient()),
        contentAlignment = Alignment.Center
    ) {
        val usePlaceholder = showPlaceholderIcons || coverArt == null
        if (usePlaceholder) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(Spacing.xs)
            ) {
                Icon(
                    imageVector = when (contentType) {
                        CoverArtContentType.AUDIOBOOK -> AppIcons.Audiobook
                        CoverArtContentType.MUSIC -> AppIcons.Music
                        CoverArtContentType.MOVIE -> AppIcons.Movie
                        CoverArtContentType.EBOOK -> AppIcons.Book
                        CoverArtContentType.COMICS -> AppIcons.Comic
                    },
                    contentDescription = null,
                    tint = palette.shade2,
                    modifier = Modifier.size(iconSize)
                )
                if (fileType.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = fileType.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.accent
                    )
                }
            }
        } else {
            Image(
                bitmap = coverArt!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
