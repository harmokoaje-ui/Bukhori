package com.example.camera.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.camera.ui.CameraViewModel
import com.example.camera.ui.components.ActiveControlDrawer
import com.example.camera.ui.components.CameraSettingsSheet
import com.example.camera.ui.components.FloatingStatusBanner
import com.example.camera.ui.components.OpticalTelemetryStrip
import com.example.camera.ui.components.QuickFeatureTriggerRow
import com.example.camera.ui.components.ResolutionPickerSheet
import com.example.camera.ui.components.ShutterBottomDeck
import com.example.camera.ui.components.TopCameraHudBar
import com.example.camera.ui.components.ViewfinderVisualOverlays
import com.example.ui.theme.AnodizedDeck
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalSurface
import com.example.ui.theme.PureOpticWhite
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.TitaniumBorder
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    onFullScreenChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val videoThumbnails by viewModel.videoThumbnails.collectAsStateWithLifecycle()

    LaunchedEffect(state.isImmersiveFullScreen, state.showEditorScreen) {
        onFullScreenChanged(state.isImmersiveFullScreen && !state.showEditorScreen)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startAudioMonitoringIfNeeded()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (state.showEditorScreen) {
        StudioEditorScreen(
            editorState = state.editorState,
            recentMedia = state.recentMedia,
            videoThumbnails = videoThumbnails,
            onBack = viewModel::closeEditor,
            onPickMediaUri = viewModel::onGalleryMediaPicked,
            onSelectRecentMedia = viewModel::openEditorWithMedia,
            onSelectStyle = viewModel::updateEditorStyle,
            onUpdateAdjustments = viewModel::updateEditorAdjustments,
            onUpdateTrimRange = viewModel::updateVideoTrimRange,
            onExport = viewModel::exportEditedMedia
        )
        return
    }

    if (!hasCameraPermission) {
        PermissionRequestScreen(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }
        )
        return
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }

    // Auto-hide tap-to-focus reticle cleanly after 2.2s
    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(2200L)
            focusTapOffset = null
        }
    }

    // Only rebind CameraX when actual hardware pipeline config changes (prevents preview stutter!)
    val needsBeautyExtensionRebind = state.beauty.enabled && state.capabilities.supportsBeautyExtension
    val effectiveVideoResolution = if (state.activeMode.isVideoMode) state.resolution else null
    val effectiveVideoFps = if (state.activeMode.isVideoMode) {
        if (state.activeMode == com.example.camera.model.CameraMode.CINEMATIC && state.cinematic.lock24Fps) 24 else state.frameRate.fps
    } else null

    LaunchedEffect(
        previewViewRef,
        state.activeMode,
        state.isBackCamera,
        effectiveVideoResolution,
        effectiveVideoFps,
        state.stabilization,
        state.rawCaptureEnabled,
        needsBeautyExtensionRebind
    ) {
        val pv = previewViewRef ?: return@LaunchedEffect
        viewModel.bindCamera(lifecycleOwner, pv)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAudioMonitoring()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // 1. Live Full-Bleed Hardware CameraX PreviewView (PERFORMANCE SurfaceView for ultra-smooth 60fps!)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.zoomRatio) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) {
                            viewModel.setZoomRatio(state.zoomRatio * zoomChange)
                        }
                    }
                }
                .pointerInput(previewViewRef) {
                    detectTapGestures { offset ->
                        focusTapOffset = offset
                        previewViewRef?.let { pv ->
                            viewModel.tapToFocus(pv, offset.x, offset.y)
                        }
                    }
                }
        )

        // 2. Real-time Viewfinder Overlays (iPhone Style Grade, 2.39:1 Guides, Grid, Horizon, Reticle)
        ViewfinderVisualOverlays(
            state = state,
            focusPoint = focusTapOffset,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top Optical HUD & Telemetry Strip
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopCameraHudBar(
                state = state,
                onCycleFlash = viewModel::cycleFlashMode,
                onOpenResolution = { viewModel.setShowResolutionSheet(true) },
                onCycleStabilization = viewModel::cycleStabilization,
                onToggleRaw = viewModel::toggleRawCapture,
                onToggleLowLight = viewModel::toggleLowLightBoost,
                onOpenSettings = { viewModel.setShowSettingsSheet(true) }
            )

            OpticalTelemetryStrip(state = state)

            Spacer(modifier = Modifier.height(8.dp))

            FloatingStatusBanner(message = state.statusBanner)
        }

        // 4. Bottom Precision Control Stack (Active Drawer + Feature Pills + Shutter Deck)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = state.activePanel != com.example.camera.model.ActiveDrawerPanel.NONE,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                ActiveControlDrawer(
                    state = state,
                    onSelectStyle = viewModel::selectStyle,
                    onSelectBeautyPreset = viewModel::selectBeautyPreset,
                    onUpdateBeautySliders = viewModel::updateBeautySliders,
                    onSetManualActive = viewModel::setManualExposureActive,
                    onSetEv = viewModel::setEvIndex,
                    onSetIso = viewModel::updateManualIso,
                    onSetShutter = viewModel::updateManualShutter,
                    onSetKelvin = viewModel::updateManualKelvin,
                    onSetFocus = viewModel::updateManualFocus,
                    onUpdateAudio = viewModel::updateAudioSettings,
                    onUpdateCinematic = viewModel::updateCinematicSettings,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            if (!state.isRecording) {
                QuickFeatureTriggerRow(
                    state = state,
                    onTogglePanel = viewModel::togglePanel,
                    onToggleLowLight = viewModel::toggleLowLightBoost,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            ShutterBottomDeck(
                state = state,
                onSelectZoom = viewModel::setZoomRatio,
                onSelectMode = viewModel::selectMode,
                onShutterClick = viewModel::triggerShutterAction,
                onPauseResumeVideo = viewModel::pauseOrResumeVideoRecording,
                onFlipCamera = viewModel::toggleCameraLens,
                onOpenGalleryEditor = viewModel::openEditorWithLatestOrEmpty
            )
        }

        // 5. Modal Bottom Sheets (Resolution/FPS & Camera Settings)
        if (state.showResolutionSheet) {
            ResolutionPickerSheet(
                state = state,
                onSelectResolution = viewModel::selectResolution,
                onSelectFrameRate = viewModel::selectFrameRate,
                onDismiss = { viewModel.setShowResolutionSheet(false) }
            )
        }

        if (state.showSettingsSheet) {
            CameraSettingsSheet(
                state = state,
                onToggleGrid = viewModel::toggleGrid,
                onToggleHorizon = viewModel::toggleHorizonLevel,
                onToggleFullScreen = viewModel::toggleImmersiveFullScreen,
                onToggleRaw = viewModel::toggleRawCapture,
                onCycleStabilization = viewModel::cycleStabilization,
                onDismiss = { viewModel.setShowSettingsSheet(false) }
            )
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(OpticalSurface)
                .border(1.dp, TitaniumBorder, RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AnodizedDeck)
                    .border(1.5.dp, CinemaAmber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = CinemaAmber,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = stringResource(R.string.perm_camera_title),
                style = MaterialTheme.typography.headlineMedium,
                color = PureOpticWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.perm_camera_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = SlateSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaAmber,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_camera_permission_button")
            ) {
                Text(
                    text = stringResource(R.string.perm_grant_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_app_settings_button")
            ) {
                Text(
                    text = stringResource(R.string.perm_open_settings),
                    color = PureOpticWhite
                )
            }
        }
    }
}
