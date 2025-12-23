package com.librio.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val shape3 = cornerRadius(3.dp)

    // Entry animation
    var startAnimation by remember { mutableStateOf(false) }

    // Loading progress
    var targetProgress by remember { mutableFloatStateOf(0f) }
    var loadingText by remember { mutableStateOf("Initializing...") }

    // Smooth animated progress
    val loadingProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "loadingProgress"
    )

    // Loading bar fade in
    val barAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(400, easing = EaseOutCubic),
        label = "barAlpha"
    )

    // Shimmer effect across the loading bar
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )


    // Exit animation
    var exitAnimation by remember { mutableStateOf(false) }
    val exitAlpha by animateFloatAsState(
        targetValue = if (exitAnimation) 0f else 1f,
        animationSpec = tween(250, easing = EaseInCubic),
        label = "exitAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(50)

        // Stage 1: Quick initial progress
        targetProgress = 0.3f
        delay(200)

        // Stage 2: Loading library
        loadingText = "Loading library..."
        targetProgress = 0.6f
        delay(300)

        // Stage 3: Preparing
        loadingText = "Preparing..."
        targetProgress = 0.85f
        delay(250)

        // Stage 4: Complete
        loadingText = "Ready"
        targetProgress = 1f
        delay(200)

        // Exit
        exitAnimation = true
        delay(150)
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.shade2,
                        palette.shade4,
                        palette.shade5
                    )
                )
            )
            .graphicsLayer { alpha = exitAlpha },
        contentAlignment = Alignment.Center
    ) {

        // Main content - centered loading bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp)
                .alpha(barAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App name - simple and clean
            Text(
                text = "LIBRIO",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp
                ),
                color = palette.shade11.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading bar with glow effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            ) {

                // Track background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(shape3)
                        .background(palette.shade11.copy(alpha = 0.1f))
                )

                // Progress fill with shimmer
                Box(
                    modifier = Modifier
                        .fillMaxWidth(loadingProgress)
                        .height(6.dp)
                        .clip(shape3)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    palette.accent,
                                    palette.accent.copy(alpha = 0.8f),
                                    palette.accent
                                ),
                                startX = shimmerOffset * 300f,
                                endX = (shimmerOffset + 0.5f) * 300f
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Loading text - subtle and minimal
            Text(
                text = loadingText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                ),
                color = palette.shade11.copy(alpha = 0.5f)
            )
        }
    }
}
