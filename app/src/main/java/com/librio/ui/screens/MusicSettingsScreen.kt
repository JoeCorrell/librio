package com.librio.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.librio.ui.theme.cornerRadius
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librio.ui.theme.*
import com.librio.ui.theme.AppIcons
import kotlin.math.abs
import kotlin.math.roundToInt

@Suppress("UNUSED_PARAMETER")
@Composable
fun MusicSettingsScreen(
    onBack: () -> Unit = {},
    title: String = "Music Settings",
    icon: ImageVector = AppIcons.Music,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    skipForward: Int,
    onSkipForwardChange: (Int) -> Unit,
    skipBack: Int,
    onSkipBackChange: (Int) -> Unit,
    autoRewind: Int,
    onAutoRewindChange: (Int) -> Unit,
    autoPlayNext: Boolean,
    onAutoPlayNextChange: (Boolean) -> Unit,
    resumePlayback: Boolean,
    onResumePlaybackChange: (Boolean) -> Unit,
    sleepTimerMinutes: Int,
    onSleepTimerChange: (Int) -> Unit,
    crossfadeDuration: Int,
    onCrossfadeDurationChange: (Int) -> Unit,
    volumeBoostEnabled: Boolean,
    onVolumeBoostEnabledChange: (Boolean) -> Unit,
    volumeBoostLevel: Float,
    onVolumeBoostLevelChange: (Float) -> Unit,
    normalizeAudio: Boolean,
    onNormalizeAudioChange: (Boolean) -> Unit,
    bassBoostLevel: Float,
    onBassBoostLevelChange: (Float) -> Unit,
    equalizerPreset: String,
    onEqualizerPresetChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = currentPalette()
    val speedValues = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val cardColors = CardDefaults.cardColors(containerColor = palette.surfaceMedium, contentColor = palette.textPrimary)
    val panelMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.55f).coerceAtMost(520.dp)
    val equalizerOptions = listOf(
        "DEFAULT" to "Default",
        "BASS_INCREASED" to "Bass Increased",
        "BASS_REDUCED" to "Bass Reduced",
        "TREBLE_INCREASED" to "Treble Increase",
        "TREBLE_REDUCED" to "Treble Reduced",
        "FLAT" to "Flat"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // Transparent scrim so the underlying player/header/nav stay visible
            .background(Color.Transparent)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = panelMaxHeight)
                .padding(bottom = 96.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = palette.surfaceMedium,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        AppIcons.Close,
                        contentDescription = "Close",
                        tint = palette.textMuted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onBack() }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionCard(
                        title = "Playback",
                        subtitle = "Speed, skipping, queue behavior",
                        icon = AppIcons.Play,
                        colors = cardColors
                    ) {
                        SliderRow(
                            title = "Playback speed",
                            valueLabel = String.format("%.2fx", speedValues.minBy { abs(it - playbackSpeed) }),
                            value = speedValues.minBy { abs(it - playbackSpeed) },
                            onChange = { new ->
                                val snapped = speedValues.minBy { abs(it - new) }
                                onPlaybackSpeedChange(snapped)
                            },
                            valueRange = 0.25f..2f,
                            step = 6
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderRow(
                            title = "Skip back",
                            valueLabel = "${skipBack}s",
                            value = skipBack.toFloat().coerceAtLeast(0f),
                            onChange = { onSkipBackChange(it.roundToInt().coerceIn(5, 90)) },
                            valueRange = 5f..90f,
                            step = 17
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderRow(
                            title = "Skip forward",
                            valueLabel = "${skipForward}s",
                            value = skipForward.toFloat().coerceAtLeast(0f),
                            onChange = { onSkipForwardChange(it.roundToInt().coerceIn(5, 90)) },
                            valueRange = 5f..90f,
                            step = 17
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderRow(
                            title = "Auto-rewind on resume",
                            valueLabel = "${autoRewind}s",
                            value = autoRewind.coerceAtLeast(0).toFloat(),
                            onChange = { onAutoRewindChange(it.toInt()) },
                            valueRange = 0f..60f,
                            step = 12
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderRow(
                            title = "Crossfade",
                            valueLabel = "${crossfadeDuration}s",
                            value = crossfadeDuration.coerceAtLeast(0).toFloat(),
                            onChange = { onCrossfadeDurationChange(it.toInt()) },
                            valueRange = 0f..10f,
                            step = 10
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ToggleRow(
                            title = "Auto play next",
                            subtitle = "Advance through the current playlist automatically",
                            checked = autoPlayNext,
                            onCheckedChange = onAutoPlayNextChange
                        )
                        ToggleRow(
                            title = "Resume where you left off",
                            subtitle = "Continue playback after app relaunch",
                            checked = resumePlayback,
                            onCheckedChange = onResumePlaybackChange
                        )
                    }

                    SectionCard(
                        title = "Audio Enhancements",
                        subtitle = "Boost and EQ",
                        icon = AppIcons.Equalizer,
                        colors = cardColors
                    ) {
                        ToggleRow(
                            title = "Volume boost",
                            subtitle = "Increase loudness beyond system volume",
                            checked = volumeBoostEnabled,
                            onCheckedChange = onVolumeBoostEnabledChange
                        )
                        SliderRow(
                            title = "Boost level",
                            valueLabel = String.format("%.1fx", volumeBoostLevel.coerceIn(1f, 3f)),
                            value = volumeBoostLevel.coerceIn(1f, 3f),
                            onChange = { onVolumeBoostLevelChange(it.coerceIn(1f, 3f)) },
                            valueRange = 1f..3f,
                            step = 4
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderRow(
                            title = "Bass boost",
                            valueLabel = "${(bassBoostLevel * 100).toInt()}%",
                            value = bassBoostLevel.coerceIn(0f, 1f),
                            onChange = { onBassBoostLevelChange(it.coerceIn(0f, 1f)) },
                            valueRange = 0f..1f,
                            step = 10
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        EqualizerList(
                            title = "Equalizer preset",
                            options = equalizerOptions,
                            selected = equalizerPreset.uppercase(),
                            onSelect = { onEqualizerPresetChange(it) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ToggleRow(
                            title = "Normalize audio",
                            subtitle = "Keep volume consistent between tracks",
                            checked = normalizeAudio,
                            onCheckedChange = onNormalizeAudioChange
                        )
                    }

                    SectionCard(
                        title = "Sleep & timers",
                        subtitle = "Drift off safely",
                        icon = AppIcons.Timer,
                        colors = cardColors
                    ) {
                        SliderRow(
                            title = "Sleep timer",
                            valueLabel = if (sleepTimerMinutes == 0) "Off" else "${sleepTimerMinutes}m",
                            value = sleepTimerMinutes.coerceAtLeast(0).toFloat(),
                            onChange = { onSleepTimerChange(it.toInt()) },
                            valueRange = 0f..120f,
                            step = 12
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: CardColors,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)
    val shape18 = cornerRadius(18.dp)
    Card(
        colors = colors,
        shape = shape18,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(shape12)
                        .background(palette.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = palette.accent)
                }
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = palette.textPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = currentPalette()
    val thumbScale by animateFloatAsState(targetValue = if (checked) 1.05f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "thumbScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(thumbScale)
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    onChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Int = 0
) {
    val palette = currentPalette()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueLabel, color = palette.textMuted, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            steps = step,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.accent.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun EqualizerList(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val palette = currentPalette()
    val shape12 = cornerRadius(12.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = palette.textPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        options.forEach { (value, label) ->
            val isSelected = value.equals(selected, ignoreCase = true)
            val bg by animateColorAsState(
                targetValue = if (isSelected) palette.accent.copy(alpha = 0.14f) else palette.surfaceMedium,
                label = "eqBg"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape12)
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelect(value) },
                    colors = RadioButtonDefaults.colors(selectedColor = palette.accent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    color = if (isSelected) palette.accent else palette.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
