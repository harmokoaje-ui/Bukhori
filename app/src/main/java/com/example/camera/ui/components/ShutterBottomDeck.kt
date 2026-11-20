package com.example.camera.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.MachinedControl
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.PureOpticWhite
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.TelemetryTextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun ShutterBottomDeck(
    state: CameraUiState,
    onSelectZoom: (Float) -> Unit,
    onSelectMode: (CameraMode) -> Unit,
    onShutterClick: () -> Unit,
    onPauseResumeVideo: () -> Unit,
    onFlipCamera: () -> Unit,
    onOpenGalleryEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ObsidianBlack.copy(alpha = 0.72f),
                        ObsidianBlack.copy(alpha = 0.94f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Optical Zoom Ratio Selector Pills
        ZoomSelectorBar(
            currentZoom = state.zoomRatio,
            minZoom = state.capabilities.minZoom,
            maxZoom = state.capabilities.maxZoom,
            onSelectZoom = onSelectZoom
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Mode Carousel (MALAM, PRO, FOTO, VIDEO, CINEMATIC, EDIT)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
        ) {
            items(CameraMode.entries) { mode ->
                val isSelected = mode == state.activeMode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .testTag("mode_tab_${mode.name.lowercase(Locale.US)}")
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectMode(mode) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = if (isSelected) 13.sp else 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) CinemaAmber else SlateSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = if (isSelected) 18.dp else 0.dp, height = 2.5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) CinemaAmber else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Main Shutter Action Row (Gallery Thumbnail | Precision Shutter | Flip/Pause)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Recent Capture Thumbnail / Studio Editor Entry
            val latest = state.recentMedia.firstOrNull()
            Box(
                modifier = Modifier
                    .testTag("gallery_thumbnail_button")
                    .minimumInteractiveComponentSize()
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MachinedControl)
                    .border(1.5.dp, PureOpticWhite.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenGalleryEditor),
                contentAlignment = Alignment.Center
            ) {
                if (latest != null && !latest.isVideo) {
                    AsyncImage(
                        model = latest.uri,
                        contentDescription = stringResource(R.string.cd_open_gallery),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (latest.isRaw) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(ObsidianBlack.copy(alpha = 0.72f))
                                .padding(vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("RAW", style = TelemetryTextStyle.copy(fontSize = 8.sp, color = CinemaAmber))
                        }
                    }
                } else if (latest != null && latest.isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MachinedControl),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = stringResource(R.string.cd_open_gallery),
                            tint = CinemaAmber,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_open_gallery),
                        tint = PureOpticWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Center: Tactile Morphing Mechanical Shutter / Video Record Button
            PrecisionShutterButton(
                isVideoMode = state.activeMode.isVideoMode,
                isRecording = state.isRecording,
                isCapturingPhoto = state.isCapturingPhoto,
                onClick = onShutterClick
            )

            // Right: Pause/Resume during video recording, or Camera Flip otherwise
            if (state.isRecording) {
                IconButton(
                    onClick = onPauseResumeVideo,
                    modifier = Modifier
                        .testTag("record_pause_resume_button")
                        .minimumInteractiveComponentSize()
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MachinedControl.copy(alpha = 0.9f))
                        .border(1.2.dp, CinemaAmber.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (state.isRecordingPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(
                            if (state.isRecordingPaused) R.string.cd_record_resume else R.string.cd_record_pause
                        ),
                        tint = CinemaAmber,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onFlipCamera,
                    modifier = Modifier
                        .testTag("flip_camera_button")
                        .minimumInteractiveComponentSize()
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MachinedControl.copy(alpha = 0.85f))
                        .border(1.dp, PureOpticWhite.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = stringResource(R.string.cd_flip_camera),
                        tint = PureOpticWhite,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomSelectorBar(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onSelectZoom: (Float) -> Unit
) {
    val presets = buildList {
        if (minZoom <= 0.65f) add(0.5f)
        add(1.0f)
        if (maxZoom >= 2.0f) add(2.0f)
        if (maxZoom >= 5.0f) add(5.0f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ObsidianBlack.copy(alpha = 0.62f))
            .border(0.75.dp, PureOpticWhite.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val isClose = abs(currentZoom - preset) < 0.25f
            val label = if (isClose) {
                String.format(Locale.US, "%.1fx", currentZoom)
            } else if (preset == 0.5f) {
                ".5"
            } else {
                "${preset.toInt()}x"
            }
            Box(
                modifier = Modifier
                    .testTag("zoom_pill_${preset}")
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isClose) CinemaAmber.copy(alpha = 0.24f) else Color.Transparent)
                    .border(
                        width = if (isClose) 1.dp else 0.dp,
                        color = if (isClose) CinemaAmber else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelectZoom(preset) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = TelemetryTextStyle.copy(
                        fontSize = 10.sp,
                        fontWeight = if (isClose) FontWeight.Bold else FontWeight.Medium,
                        color = if (isClose) CinemaAmber else PureOpticWhite.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

@Composable
private fun PrecisionShutterButton(
    isVideoMode: Boolean,
    isRecording: Boolean,
    isCapturingPhoto: Boolean,
    onClick: () -> Unit
) {
    val innerSize by animateDpAsState(
        targetValue = when {
            isRecording -> 28.dp
            isCapturingPhoto -> 52.dp
            else -> 60.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "shutter_size"
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 32.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "shutter_corner"
    )
    val innerColor by animateColorAsState(
        targetValue = when {
            isVideoMode -> RecordCrimson
            else -> PureOpticWhite
        },
        label = "shutter_color"
    )
    val outerRingColor by animateColorAsState(
        targetValue = when {
            isRecording -> RecordCrimson
            isVideoMode -> PureOpticWhite
            else -> CinemaAmber
        },
        label = "outer_ring"
    )

    Box(
        modifier = Modifier
            .testTag("shutter_button")
            .minimumInteractiveComponentSize()
            .size(78.dp)
            .clip(CircleShape)
            .border(3.5.dp, outerRingColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isCapturingPhoto) {
            CircularProgressIndicator(
                color = CinemaAmber,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .clip(RoundedCornerShape(innerCorner))
                    .background(innerColor)
            )
        }
    }
}
