package com.example.camera.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.data.CameraPreferencesRepository
import com.example.camera.engine.AudioLevelMonitor
import com.example.camera.engine.CameraHardwareController
import com.example.camera.engine.HorizonSensorManager
import com.example.camera.engine.ImageProcessor
import com.example.camera.engine.VideoTrimmerEngine
import com.example.camera.model.ActiveDrawerPanel
import com.example.camera.model.AudioSourcePreset
import com.example.camera.model.BeautyPreset
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.camera.model.CapturedMedia
import com.example.camera.model.CropAspect
import com.example.camera.model.EditorState
import com.example.camera.model.FlashModeOption
import com.example.camera.model.FrameRateOption
import com.example.camera.model.IosPhotoStyle
import com.example.camera.model.ResolutionOption
import com.example.camera.model.StabilizationMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = CameraPreferencesRepository(application)
    val hardwareController = CameraHardwareController(application)
    private val audioMonitor = AudioLevelMonitor(application)
    private val horizonSensor = HorizonSensorManager(application)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _videoThumbnails = MutableStateFlow<List<android.graphics.Bitmap>>(emptyList())
    val videoThumbnails: StateFlow<List<android.graphics.Bitmap>> = _videoThumbnails.asStateFlow()

    private var bannerJob: Job? = null

    init {
        loadSavedPreferences()
        observeHorizonSensor()
        observeAudioLevel()
    }

    private fun loadSavedPreferences() {
        viewModelScope.launch {
            val prefs = prefsRepo.prefsFlow.first()
            _uiState.update { state ->
                state.copy(
                    resolution = prefs.resolution,
                    frameRate = prefs.frameRate,
                    stabilization = prefs.stabilization,
                    rawCaptureEnabled = prefs.rawEnabled,
                    beauty = state.beauty.copy(
                        enabled = prefs.beautyEnabled,
                        preset = prefs.beautyPreset,
                        smooth = prefs.beautyPreset.smooth,
                        brightness = prefs.beautyPreset.brightness,
                        rosyTone = prefs.beautyPreset.rosyTone,
                        clarity = prefs.beautyPreset.clarity
                    ),
                    selectedStyle = prefs.style,
                    showGrid = prefs.showGrid,
                    showHorizonLevel = prefs.showHorizon,
                    isImmersiveFullScreen = prefs.isFullScreen,
                    audio = state.audio.copy(gainDb = prefs.audioGainDb)
                )
            }
        }
    }

    private fun observeHorizonSensor() {
        viewModelScope.launch {
            horizonSensor.rollDegreesFlow().collect { deg ->
                _uiState.update { it.copy(horizonRollDegrees = deg) }
            }
        }
    }

    private fun observeAudioLevel() {
        viewModelScope.launch {
            audioMonitor.dbLevel.collect { db ->
                _uiState.update { it.copy(audio = it.audio.copy(liveDbLevel = db)) }
            }
        }
    }

    fun startAudioMonitoringIfNeeded() {
        val state = _uiState.value
        if (!state.isRecording && state.audio.audioEnabled && (state.activeMode.isVideoMode || state.activePanel == ActiveDrawerPanel.AUDIO_MIXER)) {
            audioMonitor.start(viewModelScope, state.audio.sourcePreset, state.audio.gainDb)
        } else if (state.isRecording || !state.audio.audioEnabled) {
            audioMonitor.stop()
        }
    }

    fun stopAudioMonitoring() {
        audioMonitor.stop()
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            val state = _uiState.value
            hardwareController.bindCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                state = state,
                onCapabilitiesDetected = { caps ->
                    _uiState.update { current ->
                        current.copy(
                            capabilities = caps,
                            zoomRatio = current.zoomRatio.coerceIn(caps.minZoom, caps.maxZoom)
                        )
                    }
                },
                onError = { msg -> showBanner(msg) }
            )
            startAudioMonitoringIfNeeded()
        }
    }

    fun selectMode(mode: CameraMode) {
        if (mode == CameraMode.EDIT) {
            openEditorWithLatestOrEmpty()
            return
        }
        if (_uiState.value.isRecording) {
            showBanner("Hentikan perekaman sebelum berpindah mode")
            return
        }
        _uiState.update { state ->
            val defaultPanel = when (mode) {
                CameraMode.PRO -> ActiveDrawerPanel.PRO_MANUAL
                CameraMode.CINEMATIC -> ActiveDrawerPanel.CINEMATIC_LENS
                else -> ActiveDrawerPanel.NONE
            }
            val updatedStyle = if (mode == CameraMode.NIGHT && state.selectedStyle == IosPhotoStyle.STANDARD) {
                IosPhotoStyle.NIGHT_CLEAR
            } else if (mode != CameraMode.NIGHT && state.selectedStyle == IosPhotoStyle.NIGHT_CLEAR) {
                IosPhotoStyle.STANDARD
            } else {
                state.selectedStyle
            }
            state.copy(
                activeMode = mode,
                lowLightBoostEnabled = mode == CameraMode.NIGHT || state.lowLightBoostEnabled,
                selectedStyle = updatedStyle,
                activePanel = defaultPanel,
                manualExposure = state.manualExposure.copy(
                    isManualActive = mode == CameraMode.PRO && state.manualExposure.isManualActive
                )
            )
        }
        startAudioMonitoringIfNeeded()
    }

    fun toggleCameraLens() {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(isBackCamera = !it.isBackCamera, zoomRatio = 1.0f) }
    }

    fun cycleFlashMode() {
        val next = when (_uiState.value.flashMode) {
            FlashModeOption.AUTO -> FlashModeOption.ON
            FlashModeOption.ON -> FlashModeOption.TORCH
            FlashModeOption.TORCH -> FlashModeOption.OFF
            FlashModeOption.OFF -> FlashModeOption.AUTO
        }
        _uiState.update { it.copy(flashMode = next) }
        hardwareController.applyFlash(next)
        showBanner("Flash: ${next.label}")
    }

    fun cycleStabilization() {
        val next = when (_uiState.value.stabilization) {
            StabilizationMode.OIS_EIS -> StabilizationMode.ULTRA
            StabilizationMode.ULTRA -> StabilizationMode.OIS
            StabilizationMode.OIS -> StabilizationMode.OFF
            StabilizationMode.OFF -> StabilizationMode.OIS_EIS
        }
        _uiState.update { it.copy(stabilization = next) }
        viewModelScope.launch { prefsRepo.saveStabilization(next) }
        showBanner("Stabilisasi: ${next.description}")
    }

    fun toggleRawCapture() {
        val state = _uiState.value
        val next = !state.rawCaptureEnabled
        _uiState.update { it.copy(rawCaptureEnabled = next) }
        viewModelScope.launch { prefsRepo.saveRawEnabled(next) }
        if (next) {
            if (state.capabilities.supportsRaw) {
                showBanner("RAW DNG 12-bit Aktif (Hardware Sensor)")
            } else {
                showBanner("RAW Mode Aktif (Sensor perangkat ini menyimpan JPEG 98% Lossless)")
            }
        } else {
            showBanner("Format Foto: JPEG Super Jernih")
        }
    }

    fun toggleLowLightBoost() {
        val next = !_uiState.value.lowLightBoostEnabled
        _uiState.update { it.copy(lowLightBoostEnabled = next) }
        hardwareController.applyExposureAndManualControls(_uiState.value.manualExposure, next)
        showBanner(if (next) "Low-Light Enhancement Aktif" else "Low-Light Enhancement Mati")
    }

    fun selectResolution(resolution: ResolutionOption) {
        _uiState.update { it.copy(resolution = resolution) }
        viewModelScope.launch { prefsRepo.saveResolution(resolution) }
        val caps = _uiState.value.capabilities
        val note = when {
            resolution == ResolutionOption.UHD_8K && !caps.supports8K ->
                "Target 8K Ultra (Otomatis menyesuaikan resolusi maksimum sensor perangkat)"
            resolution == ResolutionOption.UHD_4K && !caps.supports4K ->
                "Target 4K UHD (Otomatis menyesuaikan resolusi tertinggi sensor)"
            else -> "Resolusi: ${resolution.fullLabel}"
        }
        showBanner(note)
    }

    fun selectFrameRate(fps: FrameRateOption) {
        _uiState.update { it.copy(frameRate = fps) }
        viewModelScope.launch { prefsRepo.saveFrameRate(fps) }
        showBanner("Frame Rate: ${fps.fps} FPS")
    }

    fun setZoomRatio(ratio: Float) {
        val caps = _uiState.value.capabilities
        val clamped = ratio.coerceIn(caps.minZoom, caps.maxZoom)
        _uiState.update { it.copy(zoomRatio = clamped) }
        hardwareController.applyZoom(clamped)
    }

    fun tapToFocus(previewView: PreviewView, x: Float, y: Float) {
        hardwareController.tapToFocus(previewView, x, y)
    }

    fun togglePanel(panel: ActiveDrawerPanel) {
        _uiState.update { state ->
            val next = if (state.activePanel == panel) ActiveDrawerPanel.NONE else panel
            state.copy(activePanel = next)
        }
        startAudioMonitoringIfNeeded()
    }

    fun selectStyle(style: IosPhotoStyle) {
        _uiState.update { it.copy(selectedStyle = style) }
        viewModelScope.launch { prefsRepo.saveStyle(style) }
    }

    fun selectBeautyPreset(preset: BeautyPreset) {
        val enabled = preset != BeautyPreset.OFF
        _uiState.update { state ->
            state.copy(
                beauty = state.beauty.copy(
                    enabled = enabled,
                    preset = preset,
                    smooth = preset.smooth,
                    brightness = preset.brightness,
                    rosyTone = preset.rosyTone,
                    clarity = preset.clarity
                )
            )
        }
        viewModelScope.launch { prefsRepo.saveBeauty(enabled, preset) }
    }

    fun updateBeautySliders(
        smooth: Float? = null,
        brightness: Float? = null,
        rosyTone: Float? = null,
        clarity: Float? = null
    ) {
        _uiState.update { state ->
            val updated = state.beauty.copy(
                enabled = true,
                smooth = smooth ?: state.beauty.smooth,
                brightness = brightness ?: state.beauty.brightness,
                rosyTone = rosyTone ?: state.beauty.rosyTone,
                clarity = clarity ?: state.beauty.clarity
            )
            state.copy(beauty = updated)
        }
    }

    fun setEvIndex(ev: Int) {
        val caps = _uiState.value.capabilities
        val clamped = ev.coerceIn(caps.evMin, caps.evMax)
        _uiState.update { state ->
            state.copy(manualExposure = state.manualExposure.copy(evIndex = clamped))
        }
        val st = _uiState.value
        hardwareController.applyExposureAndManualControls(
            st.manualExposure,
            st.lowLightBoostEnabled || st.activeMode == CameraMode.NIGHT
        )
    }

    fun setManualExposureActive(active: Boolean) {
        _uiState.update { state ->
            state.copy(manualExposure = state.manualExposure.copy(isManualActive = active))
        }
        val st = _uiState.value
        hardwareController.applyExposureAndManualControls(
            st.manualExposure,
            st.lowLightBoostEnabled || st.activeMode == CameraMode.NIGHT
        )
    }

    fun updateManualIso(iso: Int) {
        _uiState.update { state ->
            state.copy(
                manualExposure = state.manualExposure.copy(
                    isManualActive = true,
                    iso = iso
                )
            )
        }
        applyCurrentManualControls()
    }

    fun updateManualShutter(denom: Int) {
        _uiState.update { state ->
            state.copy(
                manualExposure = state.manualExposure.copy(
                    isManualActive = true,
                    shutterSpeedDenom = denom
                )
            )
        }
        applyCurrentManualControls()
    }

    fun updateManualKelvin(kelvin: Int) {
        _uiState.update { state ->
            state.copy(
                manualExposure = state.manualExposure.copy(
                    isManualActive = true,
                    whiteBalanceKelvin = kelvin
                )
            )
        }
        applyCurrentManualControls()
    }

    fun updateManualFocus(normalized: Float) {
        _uiState.update { state ->
            state.copy(
                manualExposure = state.manualExposure.copy(
                    isManualActive = true,
                    manualFocusNormalized = normalized
                )
            )
        }
        applyCurrentManualControls()
    }

    private fun applyCurrentManualControls() {
        val st = _uiState.value
        hardwareController.applyExposureAndManualControls(
            st.manualExposure,
            st.lowLightBoostEnabled || st.activeMode == CameraMode.NIGHT
        )
    }

    fun updateCinematicSettings(
        anamorphicBars: Boolean? = null,
        apertureFStop: Float? = null,
        lock24Fps: Boolean? = null
    ) {
        _uiState.update { state ->
            state.copy(
                cinematic = state.cinematic.copy(
                    anamorphicBars = anamorphicBars ?: state.cinematic.anamorphicBars,
                    apertureFStop = apertureFStop ?: state.cinematic.apertureFStop,
                    lock24Fps = lock24Fps ?: state.cinematic.lock24Fps
                )
            )
        }
    }

    fun updateAudioSettings(
        audioEnabled: Boolean? = null,
        sourcePreset: AudioSourcePreset? = null,
        gainDb: Float? = null,
        noiseReduction: Boolean? = null,
        windCutFilter: Boolean? = null
    ) {
        _uiState.update { state ->
            state.copy(
                audio = state.audio.copy(
                    audioEnabled = audioEnabled ?: state.audio.audioEnabled,
                    sourcePreset = sourcePreset ?: state.audio.sourcePreset,
                    gainDb = gainDb ?: state.audio.gainDb,
                    noiseReduction = noiseReduction ?: state.audio.noiseReduction,
                    windCutFilter = windCutFilter ?: state.audio.windCutFilter
                )
            )
        }
        gainDb?.let { g -> viewModelScope.launch { prefsRepo.saveAudioGain(g) } }
        startAudioMonitoringIfNeeded()
    }

    fun toggleGrid(show: Boolean) {
        _uiState.update { it.copy(showGrid = show) }
        viewModelScope.launch { prefsRepo.saveGrid(show) }
    }

    fun toggleHorizonLevel(show: Boolean) {
        _uiState.update { it.copy(showHorizonLevel = show) }
        viewModelScope.launch { prefsRepo.saveHorizon(show) }
    }

    fun toggleImmersiveFullScreen(enabled: Boolean) {
        _uiState.update { it.copy(isImmersiveFullScreen = enabled) }
        viewModelScope.launch { prefsRepo.saveFullScreen(enabled) }
        showBanner(if (enabled) "Mode Layar Penuh Aktif" else "Bilah Sistem Ditampilkan")
    }

    fun setShowSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun setShowResolutionSheet(show: Boolean) {
        _uiState.update { it.copy(showResolutionSheet = show) }
    }

    fun triggerShutterAction() {
        val state = _uiState.value
        if (state.activeMode.isVideoMode) {
            if (state.isRecording) {
                stopVideoRecording()
            } else {
                startVideoRecording()
            }
        } else {
            capturePhoto()
        }
    }

    private fun capturePhoto() {
        val state = _uiState.value
        if (state.isCapturingPhoto) return

        _uiState.update {
            it.copy(isCapturingPhoto = true, shutterFlashTrigger = System.currentTimeMillis())
        }

        hardwareController.capturePhoto(
            state = state,
            onRawSaved = { uri, displayName ->
                val media = CapturedMedia(
                    uri = uri,
                    displayName = displayName,
                    isVideo = false,
                    isRaw = true,
                    resolutionLabel = "RAW DNG"
                )
                _uiState.update {
                    it.copy(
                        isCapturingPhoto = false,
                        recentMedia = listOf(media) + it.recentMedia.take(19)
                    )
                }
                showBanner("RAW DNG tersimpan ke Galeri (Pictures/OptixPro)")
            },
            onTempJpegCaptured = { tempFile ->
                viewModelScope.launch {
                    val savedUri = ImageProcessor.processCapturedFileAndSaveToGallery(
                        context = getApplication(),
                        tempFile = tempFile,
                        style = state.selectedStyle,
                        beauty = state.beauty,
                        lowLightBoost = state.lowLightBoostEnabled || state.activeMode == CameraMode.NIGHT,
                        cinematicCrop = state.activeMode == CameraMode.CINEMATIC && state.cinematic.anamorphicBars,
                        mirrorHorizontally = !state.isBackCamera
                    )
                    if (savedUri != null) {
                        val media = CapturedMedia(
                            uri = savedUri,
                            displayName = "OptixPro_${System.currentTimeMillis()}.jpg",
                            isVideo = false,
                            isRaw = false,
                            resolutionLabel = state.resolution.shortLabel
                        )
                        _uiState.update {
                            it.copy(
                                isCapturingPhoto = false,
                                recentMedia = listOf(media) + it.recentMedia.take(19)
                            )
                        }
                        showBanner("Foto jernih tersimpan otomatis ke Galeri")
                    } else {
                        _uiState.update { it.copy(isCapturingPhoto = false) }
                        showBanner("Gagal menyimpan foto ke Galeri")
                    }
                }
            },
            onError = { err ->
                _uiState.update { it.copy(isCapturingPhoto = false) }
                showBanner(err)
            }
        )
    }

    private fun startVideoRecording() {
        val state = _uiState.value
        // Release AudioRecord VU monitor so CameraX Recorder owns microphone exclusively
        audioMonitor.stop()

        val resLabel = state.resolution.shortLabel
        hardwareController.startVideoRecording(
            audioEnabled = state.audio.audioEnabled,
            resolutionLabel = resLabel,
            onStarted = {
                _uiState.update {
                    it.copy(isRecording = true, isRecordingPaused = false, recordingDurationMs = 0L)
                }
            },
            onDurationTick = { durationMs ->
                _uiState.update { it.copy(recordingDurationMs = durationMs) }
            },
            onPaused = { paused ->
                _uiState.update { it.copy(isRecordingPaused = paused) }
            },
            onFinalized = { uri, displayName, err ->
                _uiState.update {
                    val updatedList = if (uri != null) {
                        listOf(
                            CapturedMedia(
                                uri = uri,
                                displayName = displayName,
                                isVideo = true,
                                resolutionLabel = "$resLabel ${state.frameRate.fps}fps"
                            )
                        ) + it.recentMedia.take(19)
                    } else {
                        it.recentMedia
                    }
                    it.copy(
                        isRecording = false,
                        isRecordingPaused = false,
                        recordingDurationMs = 0L,
                        recentMedia = updatedList
                    )
                }
                if (err != null) {
                    showBanner(err)
                } else if (uri != null) {
                    showBanner("Video stabil tersimpan ke Galeri (Movies/OptixPro)")
                }
                startAudioMonitoringIfNeeded()
            }
        )
    }

    fun pauseOrResumeVideoRecording() {
        val state = _uiState.value
        if (!state.isRecording) return
        hardwareController.pauseOrResumeRecording(state.isRecordingPaused)
    }

    fun stopVideoRecording() {
        hardwareController.stopVideoRecording()
    }

    // --- Built-in Studio Photo & Video Editor ---

    fun openEditorWithLatestOrEmpty() {
        val latest = _uiState.value.recentMedia.firstOrNull()
        _uiState.update { it.copy(showEditorScreen = true) }
        if (latest != null) {
            loadMediaIntoEditor(latest.uri, latest.isVideo, latest.displayName)
        }
    }

    fun openEditorWithMedia(media: CapturedMedia) {
        _uiState.update { it.copy(showEditorScreen = true) }
        loadMediaIntoEditor(media.uri, media.isVideo, media.displayName)
    }

    fun onGalleryMediaPicked(uri: Uri) {
        val resolver: ContentResolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())?.let {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
            } ?: ""
        val isVideo = mime.startsWith("video/")
        loadMediaIntoEditor(uri, isVideo, if (isVideo) "Video Galeri" else "Foto Galeri")
    }

    fun loadMediaIntoEditor(uri: Uri, isVideo: Boolean, name: String) {
        _videoThumbnails.value = emptyList()
        _uiState.update { state ->
            state.copy(
                editorState = EditorState(
                    mediaUri = uri,
                    isVideo = isVideo,
                    displayName = name,
                    selectedStyle = state.selectedStyle,
                    beautySmooth = if (isVideo) 0f else if (state.beauty.enabled) state.beauty.smooth else 0f
                )
            )
        }
        if (isVideo) {
            viewModelScope.launch {
                val info = VideoTrimmerEngine.inspectVideo(getApplication(), uri, frameCount = 8)
                _videoThumbnails.value = info.thumbnails
                _uiState.update { state ->
                    state.copy(
                        editorState = state.editorState.copy(
                            videoDurationMs = info.durationMs,
                            trimStartMs = 0L,
                            trimEndMs = info.durationMs.coerceAtLeast(1000L)
                        )
                    )
                }
            }
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                showEditorScreen = false,
                editorState = it.editorState.copy(exportMessage = null)
            )
        }
    }

    fun updateEditorStyle(style: IosPhotoStyle) {
        _uiState.update { it.copy(editorState = it.editorState.copy(selectedStyle = style)) }
    }

    fun updateEditorAdjustments(
        beautySmooth: Float? = null,
        brightness: Float? = null,
        contrast: Float? = null,
        saturation: Float? = null,
        sharpness: Float? = null,
        crop: CropAspect? = null
    ) {
        _uiState.update { state ->
            val e = state.editorState
            state.copy(
                editorState = e.copy(
                    beautySmooth = beautySmooth ?: e.beautySmooth,
                    brightnessAdjust = brightness ?: e.brightnessAdjust,
                    contrastAdjust = contrast ?: e.contrastAdjust,
                    saturationAdjust = saturation ?: e.saturationAdjust,
                    sharpnessAdjust = sharpness ?: e.sharpnessAdjust,
                    selectedCrop = crop ?: e.selectedCrop
                )
            )
        }
    }

    fun updateVideoTrimRange(startMs: Long, endMs: Long) {
        _uiState.update { state ->
            val maxDur = state.editorState.videoDurationMs.coerceAtLeast(500L)
            val safeStart = startMs.coerceIn(0L, (endMs - 300L).coerceAtLeast(0L))
            val safeEnd = endMs.coerceIn((safeStart + 300L).coerceAtMost(maxDur), maxDur)
            state.copy(
                editorState = state.editorState.copy(
                    trimStartMs = safeStart,
                    trimEndMs = safeEnd
                )
            )
        }
    }

    fun exportEditedMedia(includeVideoAudio: Boolean = true) {
        val editor = _uiState.value.editorState
        val uri = editor.mediaUri ?: return
        if (editor.isProcessing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(editorState = it.editorState.copy(isProcessing = true, exportMessage = null))
            }
            val savedUri = if (editor.isVideo) {
                VideoTrimmerEngine.trimAndSaveToGallery(
                    context = getApplication(),
                    sourceUri = uri,
                    startMs = editor.trimStartMs,
                    endMs = editor.trimEndMs,
                    includeAudio = includeVideoAudio
                )
            } else {
                ImageProcessor.processUriAndSaveToGallery(
                    context = getApplication(),
                    sourceUri = uri,
                    style = editor.selectedStyle,
                    beautySmooth = editor.beautySmooth,
                    brightnessDelta = editor.brightnessAdjust,
                    contrastDelta = editor.contrastAdjust,
                    saturationDelta = editor.saturationAdjust,
                    sharpnessDelta = editor.sharpnessAdjust,
                    cropAspect = editor.selectedCrop
                )
            }

            if (savedUri != null) {
                val media = CapturedMedia(
                    uri = savedUri,
                    displayName = if (editor.isVideo) "OptixPro_Trim.mp4" else "OptixPro_Edit.jpg",
                    isVideo = editor.isVideo,
                    resolutionLabel = if (editor.isVideo) "Trim HD" else "Edited"
                )
                _uiState.update { state ->
                    state.copy(
                        recentMedia = listOf(media) + state.recentMedia.take(19),
                        editorState = state.editorState.copy(
                            isProcessing = false,
                            exportMessage = if (editor.isVideo) {
                                "Video hasil potong tersimpan ke Galeri!"
                            } else {
                                "Foto hasil edit tersimpan ke Galeri!"
                            }
                        )
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        editorState = state.editorState.copy(
                            isProcessing = false,
                            exportMessage = "Gagal menyimpan hasil edit. Coba rentang atau media lain."
                        )
                    )
                }
            }
        }
    }

    fun showBanner(message: String) {
        bannerJob?.cancel()
        _uiState.update { it.copy(statusBanner = message) }
        bannerJob = viewModelScope.launch {
            delay(2800L)
            _uiState.update { it.copy(statusBanner = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioMonitor.stop()
        hardwareController.release()
    }
}
