package com.librio.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

    // Animation steps matching JSX intro
    var introStep by remember { mutableIntStateOf(0) }

    // Logo scale + alpha
    val logoScale by animateFloatAsState(
        targetValue = if (introStep >= 1) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (introStep >= 1) 1f else 0f,
        animationSpec = tween(600, easing = EaseOutCubic),
        label = "logoAlpha"
    )

    // Tagline alpha
    val taglineAlpha by animateFloatAsState(
        targetValue = if (introStep >= 2) 1f else 0f,
        animationSpec = tween(500, delayMillis = 100, easing = EaseOutCubic),
        label = "taglineAlpha"
    )

    // Dots alpha
    val dotsAlpha by animateFloatAsState(
        targetValue = if (introStep >= 2) 1f else 0f,
        animationSpec = tween(500, delayMillis = 300, easing = EaseOutCubic),
        label = "dotsAlpha"
    )

    // Pulse animation for dots
    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "dot1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "dot2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "dot3"
    )

    // Exit animation
    var exitAnimation by remember { mutableStateOf(false) }
    val exitAlpha by animateFloatAsState(
        targetValue = if (exitAnimation) 0f else 1f,
        animationSpec = tween(250, easing = EaseInCubic),
        label = "exitAlpha"
    )

    LaunchedEffect(Unit) {
        delay(400)
        introStep = 1
        delay(800)
        introStep = 2
        delay(1200)
        exitAnimation = true
        delay(250)
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .graphicsLayer { alpha = exitAlpha },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo text: "librio" with accent-colored "rio"
            Row(
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(logoAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "lib",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                        letterSpacing = (-0.8).sp
                    ),
                    color = palette.textPrimary
                )
                Text(
                    text = "rio",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                        letterSpacing = (-0.8).sp
                    ),
                    color = palette.accent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Your unified media experience",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
                modifier = Modifier.alpha(taglineAlpha)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pulsing dots
            Row(
                modifier = Modifier.alpha(dotsAlpha),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(dotAlpha1, dotAlpha2, dotAlpha3).forEach { dotAlpha ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .alpha(dotAlpha)
                            .clip(CircleShape)
                            .background(palette.accent)
                    )
                }
            }
        }
    }
}
