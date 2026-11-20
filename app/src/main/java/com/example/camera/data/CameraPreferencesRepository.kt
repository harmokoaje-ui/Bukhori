package com.example.camera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.camera.model.BeautyPreset
import com.example.camera.model.FrameRateOption
import com.example.camera.model.IosPhotoStyle
import com.example.camera.model.ResolutionOption
import com.example.camera.model.StabilizationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cameraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "optixpro_camera_prefs"
)

data class StoredCameraPrefs(
    val resolution: ResolutionOption = ResolutionOption.UHD_4K,
    val frameRate: FrameRateOption = FrameRateOption.FPS_30,
    val stabilization: StabilizationMode = StabilizationMode.OIS_EIS,
    val rawEnabled: Boolean = false,
    val beautyEnabled: Boolean = true,
    val beautyPreset: BeautyPreset = BeautyPreset.NATURAL,
    val style: IosPhotoStyle = IosPhotoStyle.STANDARD,
    val showGrid: Boolean = true,
    val showHorizon: Boolean = true,
    val isFullScreen: Boolean = true,
    val audioGainDb: Float = 2f
)

class CameraPreferencesRepository(private val context: Context) {

    private object Keys {
        val RESOLUTION = stringPreferencesKey("resolution")
        val FRAME_RATE = stringPreferencesKey("frame_rate")
        val STABILIZATION = stringPreferencesKey("stabilization")
        val RAW_ENABLED = booleanPreferencesKey("raw_enabled")
        val BEAUTY_ENABLED = booleanPreferencesKey("beauty_enabled")
        val BEAUTY_PRESET = stringPreferencesKey("beauty_preset")
        val PHOTO_STYLE = stringPreferencesKey("photo_style")
        val SHOW_GRID = booleanPreferencesKey("show_grid")
        val SHOW_HORIZON = booleanPreferencesKey("show_horizon")
        val IS_FULL_SCREEN = booleanPreferencesKey("is_full_screen")
        val AUDIO_GAIN_DB = floatPreferencesKey("audio_gain_db")
    }

    val prefsFlow: Flow<StoredCameraPrefs> = context.cameraDataStore.data.map { prefs ->
        StoredCameraPrefs(
            resolution = prefs[Keys.RESOLUTION]?.let { runCatching { ResolutionOption.valueOf(it) }.getOrNull() }
                ?: ResolutionOption.UHD_4K,
            frameRate = prefs[Keys.FRAME_RATE]?.let { runCatching { FrameRateOption.valueOf(it) }.getOrNull() }
                ?: FrameRateOption.FPS_30,
            stabilization = prefs[Keys.STABILIZATION]?.let { runCatching { StabilizationMode.valueOf(it) }.getOrNull() }
                ?: StabilizationMode.OIS_EIS,
            rawEnabled = prefs[Keys.RAW_ENABLED] ?: false,
            beautyEnabled = prefs[Keys.BEAUTY_ENABLED] ?: true,
            beautyPreset = prefs[Keys.BEAUTY_PRESET]?.let { runCatching { BeautyPreset.valueOf(it) }.getOrNull() }
                ?: BeautyPreset.NATURAL,
            style = prefs[Keys.PHOTO_STYLE]?.let { runCatching { IosPhotoStyle.valueOf(it) }.getOrNull() }
                ?: IosPhotoStyle.STANDARD,
            showGrid = prefs[Keys.SHOW_GRID] ?: true,
            showHorizon = prefs[Keys.SHOW_HORIZON] ?: true,
            isFullScreen = prefs[Keys.IS_FULL_SCREEN] ?: true,
            audioGainDb = prefs[Keys.AUDIO_GAIN_DB] ?: 2f
        )
    }

    suspend fun saveResolution(resolution: ResolutionOption) {
        context.cameraDataStore.edit { it[Keys.RESOLUTION] = resolution.name }
    }

    suspend fun saveFrameRate(fps: FrameRateOption) {
        context.cameraDataStore.edit { it[Keys.FRAME_RATE] = fps.name }
    }

    suspend fun saveStabilization(mode: StabilizationMode) {
        context.cameraDataStore.edit { it[Keys.STABILIZATION] = mode.name }
    }

    suspend fun saveRawEnabled(enabled: Boolean) {
        context.cameraDataStore.edit { it[Keys.RAW_ENABLED] = enabled }
    }

    suspend fun saveBeauty(enabled: Boolean, preset: BeautyPreset) {
        context.cameraDataStore.edit {
            it[Keys.BEAUTY_ENABLED] = enabled
            it[Keys.BEAUTY_PRESET] = preset.name
        }
    }

    suspend fun saveStyle(style: IosPhotoStyle) {
        context.cameraDataStore.edit { it[Keys.PHOTO_STYLE] = style.name }
    }

    suspend fun saveGrid(show: Boolean) {
        context.cameraDataStore.edit { it[Keys.SHOW_GRID] = show }
    }

    suspend fun saveHorizon(show: Boolean) {
        context.cameraDataStore.edit { it[Keys.SHOW_HORIZON] = show }
    }

    suspend fun saveFullScreen(enabled: Boolean) {
        context.cameraDataStore.edit { it[Keys.IS_FULL_SCREEN] = enabled }
    }

    suspend fun saveAudioGain(gainDb: Float) {
        context.cameraDataStore.edit { it[Keys.AUDIO_GAIN_DB] = gainDb }
    }
}
