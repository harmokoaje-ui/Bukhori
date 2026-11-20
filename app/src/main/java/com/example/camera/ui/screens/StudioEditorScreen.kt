package com.example.camera.ui.screens

import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.engine.ImageProcessor
import com.example.camera.model.BeautySettings
import com.example.camera.model.CapturedMedia
import com.example.camera.model.CropAspect
import com.example.camera.model.EditorState
import com.example.camera.model.IosPhotoStyle
import com.example.camera.ui.components.CompactSliderRow
import com.example.camera.ui.components.formatDurationMs
import com.example.ui.theme.AnodizedDeck
import com.example.ui.theme.BeautyRose
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.MachinedControl
import com.example.ui.theme.NightEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.OpticalSurface
import com.example.ui.theme.PureOpticWhite
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.TelemetryTextStyle
import com.example.ui.theme.TitaniumBorder
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Built-in OptixPro Studio Editor:
 * - Zero-permission Android Photo Picker (Google Play Policy compliant)
 * - Real-time iPhone Styles & Color Filter grading
 * - Photo Beauty Retouch, Light, Sharpness & Aspect Crop
 * - Visual Filmstrip Video Trimmer (Potong Video) with lossless MediaExtractor/MediaMuxer export
 */
@Composable
fun StudioEditorScreen(
    editorState: EditorState,
    recentMedia: List<CapturedMedia>,
    videoThumbnails: List<android.graphics.Bitmap>,
    onBack: () -> Unit,
    onPickMediaUri: (android.net.Uri) -> Unit,
    onSelectRecentMedia: (CapturedMedia) -> Unit,
    onSelectStyle: (IosPhotoStyle) -> Unit,
    onUpdateAdjustments: (Float?, Float?, Float?, Float?, Float?, CropAspect?) -> Unit,
    onUpdateTrimRange: (Long, Long) -> Unit,
    onExport: (Boolean) -> Unit
) {
    BackHandler(onBack = onBack)

    // Zero-permission Android Photo Picker supporting both Photos & Videos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickMediaUri(uri)
    }

    var includeVideoAudio by remember { mutableStateOf(true) }
    var activePhotoTab by remember { mutableIntStateOf(0) } // 0 = Styles, 1 = Tune/Beauty, 2 = Crop
    var activeVideoTab by remember { mutableIntStateOf(0) } // 0 = Trim, 1 = Color Filter

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Studio Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("editor_back_button")
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = PureOpticWhite
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (editorState.isVideo) "Studio Potong Video" else "Studio Edit Foto",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureOpticWhite
                )
                Text(
                    text = editorState.displayName.ifEmpty { "Pilih foto atau video dari Galeri" },
                    style = TelemetryTextStyle.copy(color = SlateSecondary, fontSize = 10.sp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier
                        .testTag("editor_pick_media_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.cd_pick_media),
                        tint = OpticalCyan
                    )
                }

                if (editorState.mediaUri != null) {
                    Button(
                        onClick = { onExport(includeVideoAudio) },
                        enabled = !editorState.isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinemaAmber,
                            contentColor = ObsidianBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        if (editorState.isProcessing) {
                            CircularProgressIndicator(
                                color = ObsidianBlack,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Feedback banner after export
        if (editorState.exportMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NightEmerald.copy(alpha = 0.2f))
                    .border(1.dp, NightEmerald.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = editorState.exportMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureOpticWhite
                )
            }
        }

        // Main Preview Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(OpticalSurface)
                .border(1.dp, TitaniumBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            val uri = editorState.mediaUri
            if (uri == null) {
                EmptyEditorPlaceholder(
                    onPickFromGallery = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                )
            } else if (editorState.isVideo) {
                VideoPreviewStage(
                    uri = uri,
                    style = editorState.selectedStyle,
                    includeAudio = includeVideoAudio,
                    onToggleAudio = { includeVideoAudio = !includeVideoAudio }
                )
            } else {
                PhotoPreviewStage(editorState = editorState)
            }
        }

        // Recent OptixPro Captures Strip (when available)
        if (recentMedia.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                items(recentMedia) { item ->
                    val selected = item.uri == editorState.mediaUri
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MachinedControl)
                            .border(
                                width = if (selected) 2.dp else 0.75.dp,
                                color = if (selected) CinemaAmber else TitaniumBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectRecentMedia(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isVideo) {
                            Icon(Icons.Rounded.Videocam, contentDescription = item.displayName, tint = CinemaAmber)
                        } else {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Bottom Editor Controls Deck
        if (editorState.mediaUri != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OpticalSurface)
                    .border(0.75.dp, TitaniumBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (editorState.isVideo) {
                    // Video Tabs: Potong Video | Filter Warna
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditorTabChip(
                            icon = Icons.Rounded.ContentCut,
                            label = "Potong Video",
                            selected = activeVideoTab == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { activeVideoTab = 0 }
                        )
                        EditorTabChip(
                            icon = Icons.Rounded.AutoAwesome,
                            label = "Filter Warna Live",
                            selected = activeVideoTab == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { activeVideoTab = 1 }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (activeVideoTab == 0) {
                        VideoTrimTimelinePanel(
                            editorState = editorState,
                            thumbnails = videoThumbnails,
                            onUpdateTrimRange = onUpdateTrimRange
                        )
                    } else {
                        EditorStylesStrip(
                            selectedStyle = editorState.selectedStyle,
                            onSelectStyle = onSelectStyle
                        )
                    }
                } else {
                    // Photo Tabs: Gaya iOS | Beauty & Cahaya | Potong
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EditorTabChip(
                            icon = Icons.Rounded.AutoAwesome,
                            label = "Gaya iOS",
                            selected = activePhotoTab == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { activePhotoTab = 0 }
                        )
                        EditorTabChip(
                            icon = Icons.Rounded.Tune,
                            label = "Beauty & Cahaya",
                            selected = activePhotoTab == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { activePhotoTab = 1 }
                        )
                        EditorTabChip(
                            icon = Icons.Rounded.Crop,
                            label = "Rasio",
                            selected = activePhotoTab == 2,
                            modifier = Modifier.weight(1f),
                            onClick = { activePhotoTab = 2 }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    when (activePhotoTab) {
                        0 -> EditorStylesStrip(
                            selectedStyle = editorState.selectedStyle,
                            onSelectStyle = onSelectStyle
                        )
                        1 -> {
                            CompactSliderRow(
                                label = "Haluskan Kulit (Beauty)",
                                valueText = "${(editorState.beautySmooth * 100).roundToInt()}%",
                                value = editorState.beautySmooth,
                                accent = BeautyRose,
                                onValueChange = { onUpdateAdjustments(it, null, null, null, null, null) }
                            )
                            CompactSliderRow(
                                label = "Kecerahan",
                                valueText = String.format(Locale.US, "%+.0f", editorState.brightnessAdjust * 100),
                                value = editorState.brightnessAdjust,
                                valueRange = -0.5f..0.5f,
                                accent = CinemaAmber,
                                onValueChange = { onUpdateAdjustments(null, it, null, null, null, null) }
                            )
                            CompactSliderRow(
                                label = "Kontras & Ketajaman",
                                valueText = String.format(Locale.US, "%+.0f", editorState.contrastAdjust * 100),
                                value = editorState.contrastAdjust,
                                valueRange = -0.4f..0.5f,
                                accent = OpticalCyan,
                                onValueChange = { onUpdateAdjustments(null, null, it, null, null, null) }
                            )
                            CompactSliderRow(
                                label = "Saturasi Warna",
                                valueText = String.format(Locale.US, "%+.0f", editorState.saturationAdjust * 100),
                                value = editorState.saturationAdjust,
                                valueRange = -0.8f..0.8f,
                                accent = NightEmerald,
                                onValueChange = { onUpdateAdjustments(null, null, null, it, null, null) }
                            )
                        }
                        2 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CropAspect.entries.forEach { aspect ->
                                    val selected = editorState.selectedCrop == aspect
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) CinemaAmber.copy(alpha = 0.22f) else AnodizedDeck)
                                            .border(
                                                1.dp,
                                                if (selected) CinemaAmber else TitaniumBorder,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onUpdateAdjustments(null, null, null, null, null, aspect) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = aspect.label,
                                            style = TelemetryTextStyle.copy(
                                                color = if (selected) CinemaAmber else PureOpticWhite
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditorPlaceholder(onPickFromGallery: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(CinemaAmber.copy(alpha = 0.16f))
                .border(1.dp, CinemaAmber.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = CinemaAmber,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = "Studio Edit Foto & Potong Video",
            style = MaterialTheme.typography.titleLarge,
            color = PureOpticWhite
        )
        Text(
            text = "Pilih foto atau video dari galeri HP Anda untuk potong durasi secara instan atau tambahkan gaya warna iPhone real-time.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateSecondary
        )
        Button(
            onClick = onPickFromGallery,
            colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = ObsidianBlack),
            modifier = Modifier.testTag("empty_pick_gallery_button")
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pilih dari Galeri", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PhotoPreviewStage(editorState: EditorState) {
    val beautySettings = remember(editorState.beautySmooth, editorState.sharpnessAdjust) {
        BeautySettings(
            enabled = editorState.beautySmooth > 0.01f,
            smooth = editorState.beautySmooth,
            brightness = editorState.beautySmooth * 0.25f,
            rosyTone = editorState.beautySmooth * 0.22f,
            clarity = editorState.sharpnessAdjust
        )
    }
    val composeFilter = remember(
        editorState.selectedStyle,
        beautySettings,
        editorState.brightnessAdjust,
        editorState.contrastAdjust,
        editorState.saturationAdjust
    ) {
        ImageProcessor.buildComposeColorFilter(
            style = editorState.selectedStyle,
            beauty = beautySettings,
            brightnessDelta = editorState.brightnessAdjust,
            contrastDelta = editorState.contrastAdjust,
            saturationDelta = editorState.saturationAdjust
        )
    }

    val aspectModifier = editorState.selectedCrop.ratio?.let { ratio ->
        Modifier.aspectRatio(ratio)
    } ?: Modifier.fillMaxSize()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = editorState.mediaUri,
            contentDescription = editorState.displayName,
            contentScale = if (editorState.selectedCrop.ratio != null) ContentScale.Crop else ContentScale.Fit,
            colorFilter = composeFilter,
            modifier = aspectModifier.clip(RoundedCornerShape(12.dp))
        )
    }
}

@Composable
private fun VideoPreviewStage(
    uri: android.net.Uri,
    style: IosPhotoStyle,
    includeAudio: Boolean,
    onToggleAudio: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(if (includeAudio) 1f else 0f, if (includeAudio) 1f else 0f)
                        start()
                        isPlaying = true
                    }
                    videoViewRef = this
                }
            },
            update = { view ->
                videoViewRef = view
            },
            modifier = Modifier.fillMaxSize()
        )

        // Real-time color filter overlay on video preview
        if (style != IosPhotoStyle.STANDARD && style.overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(style.overlayTint.copy(alpha = (style.overlayAlpha * 1.4f).coerceAtMost(0.32f)))
            )
        }

        // Playback & Audio Track Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val v = videoViewRef ?: return@IconButton
                    if (v.isPlaying) {
                        v.pause()
                        isPlaying = false
                    } else {
                        v.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ObsidianBlack.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Putar / Jeda Video",
                    tint = PureOpticWhite
                )
            }
            IconButton(
                onClick = onToggleAudio,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ObsidianBlack.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = if (includeAudio) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = "Suara Video",
                    tint = if (includeAudio) OpticalCyan else SlateSecondary
                )
            }
        }
    }
}

@Composable
private fun VideoTrimTimelinePanel(
    editorState: EditorState,
    thumbnails: List<android.graphics.Bitmap>,
    onUpdateTrimRange: (Long, Long) -> Unit
) {
    val maxDuration = editorState.videoDurationMs.coerceAtLeast(1000L).toFloat()
    val startVal = editorState.trimStartMs.toFloat().coerceIn(0f, maxDuration)
    val endVal = editorState.trimEndMs.toFloat().coerceIn(startVal, maxDuration)
    val trimmedLength = (editorState.trimEndMs - editorState.trimStartMs).coerceAtLeast(0L)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Mulai ${formatDurationMs(editorState.trimStartMs)}",
            style = TelemetryTextStyle.copy(color = CinemaAmber)
        )
        Text(
            text = "Durasi Potong: ${formatDurationMs(trimmedLength)}",
            style = TelemetryTextStyle.copy(color = NightEmerald, fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Akhir ${formatDurationMs(editorState.trimEndMs)}",
            style = TelemetryTextStyle.copy(color = CinemaAmber)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Visual Filmstrip Frame Preview
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AnodizedDeck)
            .border(1.dp, TitaniumBorder, RoundedCornerShape(12.dp))
    ) {
        if (thumbnails.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Memuat filmstrip video...",
                    style = TelemetryTextStyle.copy(color = SlateSecondary)
                )
            }
        }
    }

    RangeSlider(
        value = startVal..endVal,
        onValueChange = { range ->
            onUpdateTrimRange(range.start.roundToLong(), range.endInclusive.roundToLong())
        },
        valueRange = 0f..maxDuration,
        colors = SliderDefaults.colors(
            thumbColor = CinemaAmber,
            activeTrackColor = CinemaAmber,
            inactiveTrackColor = MachinedControl
        ),
        modifier = Modifier.testTag("video_trim_slider")
    )
}

@Composable
private fun EditorStylesStrip(
    selectedStyle: IosPhotoStyle,
    onSelectStyle: (IosPhotoStyle) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(IosPhotoStyle.entries) { style ->
            val selected = style == selectedStyle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MachinedControl else AnodizedDeck)
                    .border(
                        width = if (selected) 1.5.dp else 0.75.dp,
                        color = if (selected) CinemaAmber else TitaniumBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectStyle(style) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(style.swatchColor)
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) CinemaAmber else PureOpticWhite
                )
            }
        }
    }
}

@Composable
private fun EditorTabChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) CinemaAmber.copy(alpha = 0.22f) else AnodizedDeck)
            .border(
                1.dp,
                if (selected) CinemaAmber else TitaniumBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) CinemaAmber else SlateSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) PureOpticWhite else SlateSecondary
        )
    }
}
