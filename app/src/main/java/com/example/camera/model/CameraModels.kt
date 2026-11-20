package com.example.camera.model

import android.net.Uri
import androidx.compose.ui.graphics.Color

enum class CameraMode(val label: String, val isVideoMode: Boolean = false) {
    NIGHT("MALAM", false),
    PRO("PRO", false),
    PHOTO("FOTO", false),
    VIDEO("VIDEO", true),
    CINEMATIC("CINEMATIC", true),
    EDIT("EDIT", false)
}

enum class FlashModeOption(val label: String) {
    AUTO("Auto"),
    ON("Nyala"),
    OFF("Mati"),
    TORCH("Senter")
}

enum class ResolutionOption(
    val shortLabel: String,
    val fullLabel: String,
    val targetWidth: Int,
    val targetHeight: Int
) {
    HD_720("HD", "HD 720p", 1280, 720),
    FHD_1080("FHD", "Full HD 1080p", 1920, 1080),
    UHD_4K("4K", "4K Ultra HD", 3840, 2160),
    UHD_8K("8K", "8K Super Clear", 7680, 4320)
}

enum class FrameRateOption(val fps: Int, val label: String) {
    FPS_24(24, "24"),
    FPS_30(30, "30"),
    FPS_60(60, "60")
}

enum class StabilizationMode(val badge: String, val description: String) {
    OFF("OFF", "Tanpa stabilisasi"),
    OIS("OIS", "Optical Image Stabilization"),
    OIS_EIS("OIS+EIS", "Optical + Digital Anti-Goyang"),
    ULTRA("ULTRA", "Super Steady Cinema Lock")
}

enum class BeautyPreset(
    val label: String,
    val smooth: Float,
    val brightness: Float,
    val rosyTone: Float,
    val clarity: Float
) {
    OFF("Mati", 0f, 0f, 0f, 0f),
    NATURAL("Natural", 0.35f, 0.22f, 0.20f, 0.30f),
    STUDIO("Studio", 0.55f, 0.38f, 0.32f, 0.45f),
    GLAMOUR("Glamour", 0.72f, 0.48f, 0.45f, 0.52f)
}

data class BeautySettings(
    val enabled: Boolean = true,
    val preset: BeautyPreset = BeautyPreset.NATURAL,
    val smooth: Float = 0.35f,
    val brightness: Float = 0.22f,
    val rosyTone: Float = 0.20f,
    val clarity: Float = 0.30f
)

/**
 * Real-time iPhone Photographic Styles & Cinema LUT presets.
 * Matrix values are 4x5 Android ColorMatrix arrays used for both live viewfinder grading
 * and high-resolution JPEG export baking.
 */
enum class IosPhotoStyle(
    val label: String,
    val category: String,
    val swatchColor: Color,
    val overlayTint: Color,
    val overlayAlpha: Float,
    val contrast: Float,
    val saturation: Float,
    val warmth: Float,
    val exposureBoost: Float,
    val vignette: Float
) {
    STANDARD(
        label = "Asli",
        category = "Standard",
        swatchColor = Color(0xFFE2E8F0),
        overlayTint = Color.Transparent,
        overlayAlpha = 0f,
        contrast = 1.02f,
        saturation = 1.03f,
        warmth = 0f,
        exposureBoost = 0f,
        vignette = 0f
    ),
    IOS_VIVID(
        label = "iOS Vivid",
        category = "iPhone",
        swatchColor = Color(0xFF38BDF8),
        overlayTint = Color(0xFF38BDF8),
        overlayAlpha = 0.05f,
        contrast = 1.12f,
        saturation = 1.32f,
        warmth = 0.04f,
        exposureBoost = 0.04f,
        vignette = 0.08f
    ),
    IOS_RICH_CONTRAST(
        label = "Rich Contrast",
        category = "iPhone",
        swatchColor = Color(0xFF1E293B),
        overlayTint = Color(0xFF0F172A),
        overlayAlpha = 0.10f,
        contrast = 1.24f,
        saturation = 1.12f,
        warmth = -0.02f,
        exposureBoost = -0.04f,
        vignette = 0.18f
    ),
    IOS_WARM(
        label = "iOS Warm",
        category = "iPhone",
        swatchColor = Color(0xFFF59E0B),
        overlayTint = Color(0xFFFFB020),
        overlayAlpha = 0.11f,
        contrast = 1.06f,
        saturation = 1.14f,
        warmth = 0.24f,
        exposureBoost = 0.03f,
        vignette = 0.08f
    ),
    IOS_COOL(
        label = "iOS Cool",
        category = "iPhone",
        swatchColor = Color(0xFF60A5FA),
        overlayTint = Color(0xFF3B82F6),
        overlayAlpha = 0.11f,
        contrast = 1.06f,
        saturation = 1.08f,
        warmth = -0.22f,
        exposureBoost = 0.02f,
        vignette = 0.08f
    ),
    IOS_DRAMATIC(
        label = "Dramatic",
        category = "iPhone",
        swatchColor = Color(0xFF64748B),
        overlayTint = Color(0xFF1E293B),
        overlayAlpha = 0.16f,
        contrast = 1.28f,
        saturation = 0.72f,
        warmth = -0.08f,
        exposureBoost = -0.05f,
        vignette = 0.28f
    ),
    IOS_NOIR(
        label = "iOS Noir",
        category = "iPhone",
        swatchColor = Color(0xFF475569),
        overlayTint = Color(0xFF0B0E14),
        overlayAlpha = 0.24f,
        contrast = 1.38f,
        saturation = 0f,
        warmth = 0f,
        exposureBoost = -0.02f,
        vignette = 0.34f
    ),
    IOS_SILVERTONE(
        label = "Silvertone",
        category = "iPhone",
        swatchColor = Color(0xFFCBD5E1),
        overlayTint = Color(0xFF94A3B8),
        overlayAlpha = 0.16f,
        contrast = 1.16f,
        saturation = 0.06f,
        warmth = -0.04f,
        exposureBoost = 0.05f,
        vignette = 0.18f
    ),
    CINEMA_TEAL_ORANGE(
        label = "Cinema LUT",
        category = "Film",
        swatchColor = Color(0xFF0EA5E9),
        overlayTint = Color(0xFF0284C7),
        overlayAlpha = 0.12f,
        contrast = 1.20f,
        saturation = 1.18f,
        warmth = 0.14f,
        exposureBoost = -0.02f,
        vignette = 0.26f
    ),
    KODAK_GOLD(
        label = "Film Gold",
        category = "Film",
        swatchColor = Color(0xFFD97706),
        overlayTint = Color(0xFFF59E0B),
        overlayAlpha = 0.13f,
        contrast = 1.10f,
        saturation = 1.16f,
        warmth = 0.28f,
        exposureBoost = 0.04f,
        vignette = 0.20f
    ),
    NIGHT_CLEAR(
        label = "Night Clear",
        category = "Low-Light",
        swatchColor = Color(0xFF34D399),
        overlayTint = Color(0xFF10B981),
        overlayAlpha = 0.06f,
        contrast = 1.14f,
        saturation = 1.10f,
        warmth = 0.04f,
        exposureBoost = 0.16f,
        vignette = 0.04f
    )
}

enum class AudioSourcePreset(val label: String) {
    CAMCORDER("Stereo Mic"),
    UNPROCESSED("Studio Raw"),
    VOICE_CLEAR("Vokal Jernih")
}

data class AudioSettings(
    val audioEnabled: Boolean = true,
    val sourcePreset: AudioSourcePreset = AudioSourcePreset.CAMCORDER,
    val gainDb: Float = 2f,
    val noiseReduction: Boolean = true,
    val windCutFilter: Boolean = true,
    val liveDbLevel: Float = -48f
)

data class ManualExposureSettings(
    val isManualActive: Boolean = false,
    val evIndex: Int = 0,
    val iso: Int = 200,
    val shutterSpeedDenom: Int = 125, // represents 1/125s
    val whiteBalanceKelvin: Int = 5500,
    val manualFocusNormalized: Float = 0f // 0f = AF, >0f = manual focus distance
)

data class CinematicSettings(
    val anamorphicBars: Boolean = true,
    val apertureFStop: Float = 2.0f,
    val lock24Fps: Boolean = true
)

data class HardwareCapabilities(
    val supportsOis: Boolean = false,
    val supportsEis: Boolean = true,
    val supportsRaw: Boolean = false,
    val supportsNightExtension: Boolean = false,
    val supportsBeautyExtension: Boolean = false,
    val supportsLowLightBoost: Boolean = false,
    val supports4K: Boolean = false,
    val supports8K: Boolean = false,
    val minZoom: Float = 1.0f,
    val maxZoom: Float = 8.0f,
    val evMin: Int = -6,
    val evMax: Int = 6,
    val evStep: Float = 0.33f,
    val minIso: Int = 100,
    val maxIso: Int = 3200,
    val activeHardwareSummary: String = "Menginisialisasi sensor..."
)

data class CapturedMedia(
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val isRaw: Boolean = false,
    val resolutionLabel: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

enum class ActiveDrawerPanel {
    NONE,
    STYLES,
    BEAUTY,
    PRO_MANUAL,
    AUDIO_MIXER,
    CINEMATIC_LENS
}

enum class CropAspect(val label: String, val ratio: Float?) {
    ORIGINAL("Asli", null),
    SQUARE("1:1", 1f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    WIDESCREEN_16_9("16:9", 16f / 9f),
    ANAMORPHIC_239("2.39:1", 2.39f)
}

data class EditorState(
    val mediaUri: Uri? = null,
    val isVideo: Boolean = false,
    val displayName: String = "",
    val selectedStyle: IosPhotoStyle = IosPhotoStyle.STANDARD,
    val beautySmooth: Float = 0f,
    val brightnessAdjust: Float = 0f,
    val contrastAdjust: Float = 0f,
    val saturationAdjust: Float = 0f,
    val sharpnessAdjust: Float = 0.15f,
    val selectedCrop: CropAspect = CropAspect.ORIGINAL,
    val videoDurationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val isProcessing: Boolean = false,
    val exportMessage: String? = null
)

data class CameraUiState(
    val activeMode: CameraMode = CameraMode.PHOTO,
    val isBackCamera: Boolean = true,
    val flashMode: FlashModeOption = FlashModeOption.AUTO,
    val resolution: ResolutionOption = ResolutionOption.UHD_4K,
    val frameRate: FrameRateOption = FrameRateOption.FPS_30,
    val stabilization: StabilizationMode = StabilizationMode.OIS_EIS,
    val rawCaptureEnabled: Boolean = false,
    val lowLightBoostEnabled: Boolean = false,
    val beauty: BeautySettings = BeautySettings(),
    val selectedStyle: IosPhotoStyle = IosPhotoStyle.STANDARD,
    val manualExposure: ManualExposureSettings = ManualExposureSettings(),
    val cinematic: CinematicSettings = CinematicSettings(),
    val audio: AudioSettings = AudioSettings(),
    val capabilities: HardwareCapabilities = HardwareCapabilities(),
    val zoomRatio: Float = 1.0f,
    val showGrid: Boolean = true,
    val showHorizonLevel: Boolean = true,
    val isImmersiveFullScreen: Boolean = true,
    val horizonRollDegrees: Float = 0f,
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val isCapturingPhoto: Boolean = false,
    val shutterFlashTrigger: Long = 0L,
    val activePanel: ActiveDrawerPanel = ActiveDrawerPanel.NONE,
    val showSettingsSheet: Boolean = false,
    val showResolutionSheet: Boolean = false,
    val showEditorScreen: Boolean = false,
    val recentMedia: List<CapturedMedia> = emptyList(),
    val statusBanner: String? = null,
    val editorState: EditorState = EditorState()
)
