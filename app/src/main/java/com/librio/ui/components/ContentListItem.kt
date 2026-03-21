package com.librio.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import com.librio.ui.theme.AnimationDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.ui.theme.AppIcons
import com.librio.ui.theme.CornerSize
import com.librio.ui.theme.IconSize
import com.librio.ui.theme.Spacing
import com.librio.ui.theme.cornerRadius
import com.librio.ui.theme.currentPalette
import com.librio.ui.theme.thumbnailGradient

/**
 * Unified list item component for all content types
 * Clean row layout with divider separator - matches JSX design
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

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) AnimationDefaults.ItemPressScale else 1f,
        animationSpec = AnimationDefaults.snappySpring(),
        label = "pressScale"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
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
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 38dp thumbnail with rounded corners
            ContentThumbnail(
                coverArt = coverArt,
                contentType = contentType,
                fileType = fileType,
                size = 38.dp,
                iconSize = 14.dp,
                showPlaceholderIcons = showPlaceholderIcons
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Title + subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Progress bar (compact)
                if (progress > 0f) {
                    Spacer(modifier = Modifier.height(3.dp))
                    LibrioProgressBar(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        height = 2.dp,
                        activeColor = palette.accent,
                        trackColor = palette.accent.copy(alpha = 0.15f)
                    )
                }
            }

            // Playing indicator or duration
            if (isPlaying) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = AppIcons.VolumeUp,
                    contentDescription = "Now playing",
                    tint = palette.accent,
                    modifier = Modifier.size(14.dp)
                )
            } else if (duration != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = duration,
                    fontSize = 10.sp,
                    color = palette.textMuted.copy(alpha = 0.7f)
                )
            }
        }

        // Bottom divider
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 18.dp),
            thickness = 1.dp,
            color = palette.divider
        )
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
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(palette.thumbnailGradient()),
        contentAlignment = Alignment.Center
    ) {
        val usePlaceholder = showPlaceholderIcons || coverArt == null
        if (usePlaceholder) {
            Icon(
                imageVector = when (contentType) {
                    CoverArtContentType.AUDIOBOOK -> AppIcons.Audiobook
                    CoverArtContentType.MUSIC -> AppIcons.Music
                    CoverArtContentType.MOVIE -> AppIcons.Movie
                    CoverArtContentType.EBOOK -> AppIcons.Book
                    CoverArtContentType.COMICS -> AppIcons.Comic
                },
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(iconSize)
            )
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
