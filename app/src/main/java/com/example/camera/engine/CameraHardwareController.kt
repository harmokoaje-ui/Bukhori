package com.example.camera.engine

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.camera.model.FlashModeOption
import com.example.camera.model.HardwareCapabilities
import com.example.camera.model.ManualExposureSettings
import com.example.camera.model.ResolutionOption
import com.example.camera.model.StabilizationMode
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalCamera2Interop::class)
class CameraHardwareController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var lastBoundRawFormat: Boolean = false

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        state: CameraUiState,
        onCapabilitiesDetected: (HardwareCapabilities) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context)
                .awaitFuture(mainExecutor).also { cameraProvider = it }

            val extManager = extensionsManager ?: runCatching {
                ExtensionsManager.getInstanceAsync(context, provider).awaitFuture(mainExecutor)
            }.getOrNull().also { extensionsManager = it }

            val baseSelector = if (state.isBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            // Pick extension selector if Night or Beauty hardware extension is available and active
            val activeSelector = resolveCameraSelector(baseSelector, extManager, state)

            // Determine hardware stabilization flags
            val useEis = state.stabilization == StabilizationMode.OIS_EIS ||
                state.stabilization == StabilizationMode.ULTRA
            val useOis = state.stabilization != StabilizationMode.OFF

            // Configure Preview with hardware OIS/EIS & sharp edge/denoise Camera2 options
            val previewBuilder = Preview.Builder()
            if (useEis) {
                previewBuilder.setPreviewStabilizationEnabled(true)
            }
            applyOpticalCamera2Options(
                extender = Camera2Interop.Extender(previewBuilder),
                enableOis = useOis,
                enableEis = useEis,
                state = state
            )
            val preview = previewBuilder.build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            // Pre-inspect hardware capabilities on base selector
            val caps = inspectHardwareCapabilities(provider, baseSelector, extManager)
            onCapabilitiesDetected(caps)

            provider.unbindAll()

            if (state.activeMode.isVideoMode) {
                val desiredFps = if (state.activeMode == CameraMode.CINEMATIC && state.cinematic.lock24Fps) {
                    24
                } else {
                    state.frameRate.fps
                }

                val qualitySelector = buildQualitySelector(state.resolution)
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()

                val videoBuilder = VideoCapture.Builder(recorder)
                    .setTargetFrameRate(Range(desiredFps, desiredFps))
                if (useEis) {
                    videoBuilder.setVideoStabilizationEnabled(true)
                }
                applyOpticalCamera2Options(
                    extender = Camera2Interop.Extender(videoBuilder),
                    enableOis = useOis,
                    enableEis = useEis,
                    state = state
                )

                val vCapture = videoBuilder.build()
                videoCapture = vCapture
                imageCapture = null
                lastBoundRawFormat = false

                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    activeSelector,
                    preview,
                    vCapture
                )
            } else {
                val imageBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(98)
                    .setFlashMode(mapFlashMode(state.flashMode))

                val bindRaw = state.rawCaptureEnabled && caps.supportsRaw && activeSelector == baseSelector
                if (bindRaw) {
                    imageBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW)
                }
                lastBoundRawFormat = bindRaw

                applyOpticalCamera2Options(
                    extender = Camera2Interop.Extender(imageBuilder),
                    enableOis = useOis,
                    enableEis = useEis,
                    state = state
                )

                val iCapture = imageBuilder.build()
                imageCapture = iCapture
                videoCapture = null

                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    activeSelector,
                    preview,
                    iCapture
                )
            }

            // Post-bind dynamic controls: torch, zoom, exposure, low-light boost, manual overrides
            applyTorch(state.flashMode == FlashModeOption.TORCH)
            applyZoom(state.zoomRatio)
            applyExposureAndManualControls(state.manualExposure, state.lowLightBoostEnabled || state.activeMode == CameraMode.NIGHT)
        } catch (e: Exception) {
            onError("Kamera gagal diinisialisasi: ${e.localizedMessage ?: "Periksa perangkat"}")
        }
    }

    private fun resolveCameraSelector(
        baseSelector: CameraSelector,
        extManager: ExtensionsManager?,
        state: CameraUiState
    ): CameraSelector {
        if (extManager == null || state.activeMode.isVideoMode || state.manualExposure.isManualActive || state.rawCaptureEnabled) {
            return baseSelector
        }
        return when {
            state.activeMode == CameraMode.NIGHT &&
                extManager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT) -> {
                extManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
            }
            state.activeMode == CameraMode.PHOTO && state.beauty.enabled &&
                extManager.isExtensionAvailable(baseSelector, ExtensionMode.FACE_RETOUCH) -> {
                extManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.FACE_RETOUCH)
            }
            else -> baseSelector
        }
    }

    private fun <T> applyOpticalCamera2Options(
        extender: Camera2Interop.Extender<T>,
        enableOis: Boolean,
        enableEis: Boolean,
        state: CameraUiState
    ) {
        // Optical Image Stabilization (OIS)
        extender.setCaptureRequestOption(
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
            if (enableOis) {
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
            } else {
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
            }
        )
        // High-clarity noise reduction & edge sharpness so images never look muddy or pixelated
        extender.setCaptureRequestOption(
            CaptureRequest.NOISE_REDUCTION_MODE,
            CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
        )
        extender.setCaptureRequestOption(
            CaptureRequest.EDGE_MODE,
            CameraMetadata.EDGE_MODE_HIGH_QUALITY
        )
        extender.setCaptureRequestOption(
            CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
            CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
        )
        if (enableEis && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
            )
        } else if (enableEis) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
            )
        }

        // Low-light frame rate gathering if Night mode without hardware extension
        if (state.activeMode == CameraMode.NIGHT || state.lowLightBoostEnabled) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(15, 30)
            )
        }
    }

    private fun inspectHardwareCapabilities(
        provider: ProcessCameraProvider,
        selector: CameraSelector,
        extManager: ExtensionsManager?
    ): HardwareCapabilities {
        val info = runCatching { provider.getCameraInfo(selector) }.getOrNull()
            ?: return HardwareCapabilities()

        val c2Info = runCatching { Camera2CameraInfo.from(info) }.getOrNull()
        val oisModes = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        )
        val supportsOis = oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

        val eisModes = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        )
        val supportsEis = eisModes?.any { it != CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF } == true

        val imageCaps = runCatching { ImageCapture.getImageCaptureCapabilities(info) }.getOrNull()
        val supportsRaw = imageCaps?.supportedOutputFormats?.contains(ImageCapture.OUTPUT_FORMAT_RAW) == true

        val videoCaps = runCatching { Recorder.getVideoCapabilities(info) }.getOrNull()
        val supportedQualities = videoCaps?.getSupportedQualities(androidx.camera.core.DynamicRange.SDR).orEmpty()
        val supports4K = supportedQualities.contains(Quality.UHD)

        val streamConfigMap = c2Info?.getCameraCharacteristic(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        val maxOutputWidth = streamConfigMap?.getOutputSizes(android.graphics.ImageFormat.PRIVATE)
            ?.maxOfOrNull { it.width } ?: 0
        val supports8K = maxOutputWidth >= 7680

        val supportsNightExt = extManager?.isExtensionAvailable(selector, ExtensionMode.NIGHT) == true
        val supportsBeautyExt = extManager?.isExtensionAvailable(selector, ExtensionMode.FACE_RETOUCH) == true
        val supportsLlb = runCatching { info.isLowLightBoostSupported }.getOrDefault(false)

        val zoomState = info.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1.0f
        val maxZoom = (zoomState?.maxZoomRatio ?: 8.0f).coerceAtLeast(2.0f)

        val expState = info.exposureState
        val evMin = expState.exposureCompensationRange.lower
        val evMax = expState.exposureCompensationRange.upper
        val evStep = expState.exposureCompensationStep.toFloat().takeIf { it > 0f } ?: 0.33f

        val isoRange = c2Info?.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val minIso = isoRange?.lower ?: 100
        val maxIso = (isoRange?.upper ?: 3200).coerceAtMost(12800)

        val summaryParts = buildList {
            if (supportsOis) add("OIS Hardware") else add("EIS Digital")
            if (supportsRaw) add("RAW DNG")
            if (supports8K) add("8K Sensor") else if (supports4K) add("4K UHD") else add("FHD 1080p")
            if (supportsNightExt || supportsLlb) add("Night Engine")
        }

        return HardwareCapabilities(
            supportsOis = supportsOis,
            supportsEis = supportsEis,
            supportsRaw = supportsRaw,
            supportsNightExtension = supportsNightExt,
            supportsBeautyExtension = supportsBeautyExt,
            supportsLowLightBoost = supportsLlb,
            supports4K = supports4K,
            supports8K = supports8K,
            minZoom = minZoom,
            maxZoom = maxZoom,
            evMin = evMin,
            evMax = evMax,
            evStep = evStep,
            minIso = minIso,
            maxIso = maxIso,
            activeHardwareSummary = summaryParts.joinToString(" • ")
        )
    }

    private fun buildQualitySelector(resolution: ResolutionOption): QualitySelector {
        val ordered = when (resolution) {
            ResolutionOption.UHD_8K,
            ResolutionOption.UHD_4K -> listOf(Quality.UHD, Quality.FHD, Quality.HD)
            ResolutionOption.FHD_1080 -> listOf(Quality.FHD, Quality.HD, Quality.SD)
            ResolutionOption.HD_720 -> listOf(Quality.HD, Quality.FHD, Quality.SD)
        }
        return QualitySelector.fromOrderedList(
            ordered,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
        )
    }

    private fun mapFlashMode(option: FlashModeOption): Int = when (option) {
        FlashModeOption.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashModeOption.ON -> ImageCapture.FLASH_MODE_ON
        FlashModeOption.OFF, FlashModeOption.TORCH -> ImageCapture.FLASH_MODE_OFF
    }

    fun applyFlash(option: FlashModeOption) {
        imageCapture?.flashMode = mapFlashMode(option)
        applyTorch(option == FlashModeOption.TORCH)
    }

    fun applyTorch(enabled: Boolean) {
        runCatching {
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                camera?.cameraControl?.enableTorch(enabled)
            }
        }
    }

    fun applyZoom(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun tapToFocus(previewView: PreviewView, x: Float, y: Float) {
        val cam = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        )
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun applyExposureAndManualControls(
        manual: ManualExposureSettings,
        enableLowLightBoost: Boolean
    ) {
        val cam = camera ?: return

        // Hardware Low-Light Boost (CameraX 1.5+)
        runCatching {
            if (cam.cameraInfo.isLowLightBoostSupported) {
                cam.cameraControl.enableLowLightBoostAsync(enableLowLightBoost)
            }
        }

        // Standard CameraX EV exposure compensation
        val expRange = cam.cameraInfo.exposureState.exposureCompensationRange
        if (expRange.upper > expRange.lower) {
            val clampedEv = manual.evIndex.coerceIn(expRange.lower, expRange.upper)
            cam.cameraControl.setExposureCompensationIndex(clampedEv)
        }

        // Camera2Interop Manual ISO, Shutter Speed, White Balance, Focus Distance
        runCatching {
            val c2Control = Camera2CameraControl.from(cam.cameraControl)
            val optsBuilder = CaptureRequestOptions.Builder()

            if (manual.isManualActive) {
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_OFF
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    manual.iso
                )
                val exposureNs = (1_000_000_000L / manual.shutterSpeedDenom.coerceIn(2, 4000))
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    exposureNs
                )
                val awbMode = when {
                    manual.whiteBalanceKelvin <= 3500 -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
                    manual.whiteBalanceKelvin <= 4500 -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
                    manual.whiteBalanceKelvin <= 6000 -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
                    else -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                }
                optsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)

                if (manual.manualFocusNormalized > 0.02f) {
                    optsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_OFF
                    )
                    // 0.0 = infinity, up to ~10 diopters macro
                    val diopters = manual.manualFocusNormalized * 8.0f
                    optsBuilder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
                } else {
                    optsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                }
            } else {
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_AUTO
                )
                optsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }
            c2Control.setCaptureRequestOptions(optsBuilder.build())
        }
    }

    fun capturePhoto(
        state: CameraUiState,
        onRawSaved: (Uri, String) -> Unit,
        onTempJpegCaptured: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError("Kamera foto belum siap")
            return
        }

        if (lastBoundRawFormat && state.rawCaptureEnabled) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val displayName = "OptixPro_RAW_${timestamp}.dng"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OptixPro")
                }
            }
            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

            capture.takePicture(
                outputOptions,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri
                        if (savedUri != null) {
                            onRawSaved(savedUri, displayName)
                        } else {
                            onError("Gagal menyimpan file RAW DNG")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        onError("Gagal ambil RAW: ${exception.localizedMessage}")
                    }
                }
            )
            return
        }

        // Capture to temp JPEG so ImageProcessor can bake iPhone Styles + Auto Beauty + Clarity cleanly
        val tempFile = File(context.cacheDir, "optix_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        capture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onTempJpegCaptured(tempFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    tempFile.delete()
                    onError("Gagal mengambil foto: ${exception.localizedMessage}")
                }
            }
        )
    }

    fun startVideoRecording(
        audioEnabled: Boolean,
        resolutionLabel: String,
        onStarted: () -> Unit,
        onDurationTick: (Long) -> Unit,
        onPaused: (Boolean) -> Unit,
        onFinalized: (Uri?, String, String?) -> Unit
    ) {
        val vCapture = videoCapture ?: run {
            onFinalized(null, "", "Mode video belum siap")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "OptixPro_${resolutionLabel}_${timestamp}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OptixPro")
            }
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        var pendingRecording = vCapture.output.prepareRecording(context, mediaStoreOutput)
        if (audioEnabled && hasAudioPerm) {
            // Reassign immutable PendingRecording per CameraX requirement
            pendingRecording = pendingRecording.withAudioEnabled()
        }

        activeRecording = pendingRecording.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onStarted()
                is VideoRecordEvent.Pause -> onPaused(true)
                is VideoRecordEvent.Resume -> onPaused(false)
                is VideoRecordEvent.Status -> {
                    onDurationTick(event.recordingStats.recordedDurationNanos / 1_000_000L)
                }
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (event.hasError()) {
                        onFinalized(null, displayName, "Perekaman selesai dengan error (${event.error})")
                    } else {
                        onFinalized(event.outputResults.outputUri, displayName, null)
                    }
                }
            }
        }
    }

    fun pauseOrResumeRecording(currentlyPaused: Boolean) {
        if (currentlyPaused) {
            activeRecording?.resume()
        } else {
            activeRecording?.pause()
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun release() {
        stopVideoRecording()
        cameraProvider?.unbindAll()
    }
}

private suspend fun <T> ListenableFuture<T>.awaitFuture(executor: Executor): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            executor
        )
        cont.invokeOnCancellation { cancel(true) }
    }
