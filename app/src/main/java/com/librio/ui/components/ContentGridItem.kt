package com.librio.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import com.librio.ui.theme.AnimationDefaults
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librio.ui.theme.CornerSize
import com.librio.ui.theme.Elevation
import com.librio.ui.theme.Spacing
import com.librio.ui.theme.cornerRadius
import com.librio.ui.theme.currentPalette

/**
 * Unified grid item component for all content types
 * Uses Material 3 ElevatedCard with consistent styling
 *
 * Replaces: AudiobookGridItem, BookGridItem, MusicGridItem, ComicGridItem, MovieGridItem
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentGridItem(
    title: String,
    subtitle: String,
    coverArt: Bitmap?,
    contentType: CoverArtContentType,
    fileType: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
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

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = cornerRadius(CornerSize.lg),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = Elevation.md
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = palette.shade10
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                .padding(Spacing.sm)
        ) {
            // Cover art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                CoverArt(
                    bitmap = coverArt,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadiusSize = CornerSize.md,
                    elevation = Elevation.none,
                    showPlaceholderAlways = showPlaceholderIcons && coverArt == null,
                    fileExtension = fileType,
                    contentType = contentType
                )
            }

            // Progress indicator
            if (progress > 0f) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                LibrioProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    height = 3.dp,
                    activeColor = palette.primary,
                    trackColor = palette.shade5.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
            } else {
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Subtitle (author/artist)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
