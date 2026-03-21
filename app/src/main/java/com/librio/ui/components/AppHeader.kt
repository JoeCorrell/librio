package com.librio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.ui.theme.*

/**
 * App header with logo and action buttons - JSX-style clean design
 */
@Composable
fun AppHeader(
    audiobookCount: Int,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()
    val palette = currentPalette()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.horizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo: "librio" with accent-colored "rio"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "lib",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = palette.textPrimary
            )
            Text(
                text = "rio",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = palette.accent
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.surfaceCard),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSearchClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = "Search",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.surfaceCard),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = AppIcons.Settings,
                        contentDescription = "Settings",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Section header with optional action
 */
@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dimens = rememberResponsiveDimens()
    val palette = currentPalette()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.horizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            color = palette.textPrimary
        )

        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
            }
        }
    }
}
