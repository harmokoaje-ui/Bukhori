package com.example.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.camera.model.IosPhotoStyle
import com.example.camera.model.StabilizationMode
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NightEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.PureOpticWhite
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.TelemetryTextStyle
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-bleed live viewfinder visual overlays:
 * - Real-time iOS style & beauty atmosphere grade + vignette
 * - Cinematic 2.39:1 anamorphic letterbox guides & bokeh aperture indicator
 * - Rule-of-Thirds composition grid
 * - Hardware accelerometer Digital Horizon Level
 * - Tap-to-Focus animated reticle + shutter flash pulse
 */
@Composable
fun ViewfinderVisualOverlays(
    state: CameraUiState,
    focusPoint: Offset?,
    modifier: Modifier = Modifier
) {
    val style = state.selectedStyle
    var shutterFlashAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.shutterFlashTrigger) {
        if (state.shutterFlashTrigger > 0L) {
            shutterFlashAlpha = 0.72f
            delay(85L)
            shutterFlashAlpha = 0f
        }
    }
    val animatedFlash by animateFloatAsState(
        targetValue = shutterFlashAlpha,
        animationSpec = tween(140),
        label = "shutter_flash"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Live Real-Time Color Style / Beauty / Vignette Visual Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (style != IosPhotoStyle.STANDARD && style.overlayAlpha > 0f) {
                drawRect(color = style.overlayTint.copy(alpha = style.overlayAlpha))
            }
            if (state.beauty.enabled && state.beauty.brightness > 0.05f) {
                drawRect(
                    color = Color(0xFFFFE4EC).copy(alpha = (state.beauty.brightness * 0.08f).coerceAtMost(0.06f))
                )
            }
            if (style.vignette > 0.02f) {
                val radius = size.maxDimension * 0.72f
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = (style.vignette * 0.7f).coerceAtMost(0.65f))
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = radius
                    )
                )
            }
        }

        // 2. Cinematic 2.39:1 Anamorphic Frame Guides + Shallow Depth Aperture Vignette
        if (state.activeMode == CameraMode.CINEMATIC && state.cinematic.anamorphicBars) {
            val bokehEdgeAlpha = ((8.5f - state.cinematic.apertureFStop) / 7f).coerceIn(0.08f, 0.42f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barHeight = size.height * 0.115f
                drawRect(
                    color = ObsidianBlack.copy(alpha = 0.88f),
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(size.width, barHeight)
                )
                drawRect(
                    color = ObsidianBlack.copy(alpha = 0.88f),
                    topLeft = Offset(0f, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(size.width, barHeight)
                )
                // Thin cinema framing lines
                drawLine(
                    color = CinemaAmber.copy(alpha = 0.45f),
                    start = Offset(0f, barHeight),
                    end = Offset(size.width, barHeight),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = CinemaAmber.copy(alpha = 0.45f),
                    start = Offset(0f, size.height - barHeight),
                    end = Offset(size.width, size.height - barHeight),
                    strokeWidth = 1.dp.toPx()
                )
                // Subtle cinematic bokeh depth edge falloff
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            ObsidianBlack.copy(alpha = bokehEdgeAlpha)
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.72f
                    )
                )
            }
        }

        // 3. Rule-of-Thirds Composition Grid
        if (state.showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = PureOpticWhite.copy(alpha = 0.18f)
                val stroke = 0.75.dp.toPx()
                val thirdW = size.width / 3f
                val thirdH = size.height / 3f
                drawLine(gridColor, Offset(thirdW, 0f), Offset(thirdW, size.height), stroke)
                drawLine(gridColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), stroke)
                drawLine(gridColor, Offset(0f, thirdH), Offset(size.width, thirdH), stroke)
                drawLine(gridColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), stroke)
            }
        }

        // 4. Precision Accelerometer Digital Horizon Level
        if (state.showHorizonLevel) {
            val roll = state.horizonRollDegrees
            val isLevel = abs(roll) <= 1.5f
            val levelColor = if (isLevel) NightEmerald else PureOpticWhite.copy(alpha = 0.72f)
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 160.dp, height = 44.dp)
            ) {
                val cy = size.height / 2f
                val cx = size.width / 2f
                // Fixed side reference ticks
                drawLine(
                    color = PureOpticWhite.copy(alpha = 0.35f),
                    start = Offset(12.dp.toPx(), cy),
                    end = Offset(34.dp.toPx(), cy),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = PureOpticWhite.copy(alpha = 0.35f),
                    start = Offset(size.width - 34.dp.toPx(), cy),
                    end = Offset(size.width - 12.dp.toPx(), cy),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Dynamic horizon bar rotating with real gravity sensor
                rotate(degrees = -roll, pivot = Offset(cx, cy)) {
                    drawLine(
                        color = levelColor,
                        start = Offset(cx - 32.dp.toPx(), cy),
                        end = Offset(cx + 32.dp.toPx(), cy),
                        strokeWidth = if (isLevel) 2.2.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = levelColor,
                        radius = 2.5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        // 5. Animated Tap-to-Focus Reticle
        if (focusPoint != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (focusPoint.x - 36.dp.toPx()).roundToInt(),
                            (focusPoint.y - 36.dp.toPx()).roundToInt()
                        )
                    }
                    .size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 1.6.dp.toPx()
                    val corner = 14.dp.toPx()
                    drawRect(
                        color = CinemaAmber.copy(alpha = 0.88f),
                        style = Stroke(width = stroke)
                    )
                    // Crosshair center ticks
                    drawLine(
                        CinemaAmber,
                        Offset(size.width / 2f, 0f),
                        Offset(size.width / 2f, corner / 2f),
                        stroke
                    )
                    drawLine(
                        CinemaAmber,
                        Offset(size.width / 2f, size.height),
                        Offset(size.width / 2f, size.height - corner / 2f),
                        stroke
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = CinemaAmber,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 22.dp)
                        .size(16.dp)
                )
            }
        }

        // 6. Shutter Capture Flash Feedback
        if (animatedFlash > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = animatedFlash))
            )
        }
    }
}

/**
 * Live Optical Telemetry Strip: shows active Resolution/FPS, OIS+EIS badge, ISO/Shutter/EV,
 * Cinema Aperture or Night mode badge, and real-time stereo audio dB VU meter!
 */
@Composable
fun OpticalTelemetryStrip(
    state: CameraUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isRecording) {
            // Live REC Timer Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(RecordCrimson.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PureOpticWhite)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatDurationMs(state.recordingDurationMs),
                    style = TelemetryTextStyle.copy(fontSize = 12.sp, color = PureOpticWhite)
                )
                if (state.isRecordingPaused) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JEDA",
                        style = TelemetryTextStyle.copy(fontSize = 10.sp, color = PureOpticWhite)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(ObsidianBlack.copy(alpha = 0.62f))
                .border(0.75.dp, PureOpticWhite.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val fpsVal = if (state.activeMode == CameraMode.CINEMATIC && state.cinematic.lock24Fps) {
                24
            } else {
                state.frameRate.fps
            }
            Text(
                text = "${state.resolution.shortLabel}•${fpsVal}",
                style = TelemetryTextStyle.copy(color = CinemaAmber)
            )

            if (state.stabilization != StabilizationMode.OFF) {
                Text(
                    text = state.stabilization.badge,
                    style = TelemetryTextStyle.copy(color = OpticalCyan)
                )
            }

            if (state.rawCaptureEnabled && !state.activeMode.isVideoMode) {
                Text(
                    text = "RAW",
                    style = TelemetryTextStyle.copy(color = CinemaAmber)
                )
            }

            if (state.activeMode == CameraMode.PRO || state.manualExposure.isManualActive) {
                val evFormatted = String.format(
                    Locale.US,
                    "%+.1f",
                    state.manualExposure.evIndex * state.capabilities.evStep
                )
                Text(
                    text = "ISO ${state.manualExposure.iso}  1/${state.manualExposure.shutterSpeedDenom}s  EV$evFormatted",
                    style = TelemetryTextStyle.copy(color = PureOpticWhite.copy(alpha = 0.88f))
                )
            } else if (state.activeMode == CameraMode.CINEMATIC) {
                Text(
                    text = "f/${String.format(Locale.US, "%.1f", state.cinematic.apertureFStop)} • 2.39:1",
                    style = TelemetryTextStyle.copy(color = PureOpticWhite.copy(alpha = 0.9f))
                )
            } else if (state.activeMode == CameraMode.NIGHT || state.lowLightBoostEnabled) {
                Text(
                    text = "LOW-LIGHT+",
                    style = TelemetryTextStyle.copy(color = NightEmerald)
                )
            } else if (state.selectedStyle != IosPhotoStyle.STANDARD) {
                Text(
                    text = state.selectedStyle.label.uppercase(Locale.US),
                    style = TelemetryTextStyle.copy(color = state.selectedStyle.swatchColor)
                )
            }

            // Live Audio VU Meter mini-bars for video / audio monitor
            if (state.activeMode.isVideoMode && state.audio.audioEnabled) {
                LiveMiniAudioMeter(dbLevel = state.audio.liveDbLevel, isRecording = state.isRecording)
            }
        }
    }
}

@Composable
private fun LiveMiniAudioMeter(dbLevel: Float, isRecording: Boolean) {
    val normalized = if (isRecording) {
        0.55f
    } else {
        ((dbLevel + 60f) / 60f).coerceIn(0.05f, 1f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = if (normalized > 0.85f) RecordCrimson else NightEmerald,
            modifier = Modifier.size(13.dp)
        )
        for (i in 0 until 5) {
            val threshold = (i + 1) / 5f
            val active = normalized >= threshold
            val barColor = when {
                !active -> PureOpticWhite.copy(alpha = 0.18f)
                i == 4 -> RecordCrimson
                i == 3 -> CinemaAmber
                else -> NightEmerald
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((5 + i * 2).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun FloatingStatusBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(240)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(ObsidianBlack.copy(alpha = 0.82f))
                .border(1.dp, CinemaAmber.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.orEmpty(),
                color = PureOpticWhite,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            )
        }
    }
}

fun formatDurationMs(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
