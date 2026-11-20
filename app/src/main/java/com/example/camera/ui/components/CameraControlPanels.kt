package com.example.camera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MovieFilter
import androidx.compose.material.icons.rounded.RawOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.camera.model.ActiveDrawerPanel
import com.example.camera.model.AudioSourcePreset
import com.example.camera.model.BeautyPreset
import com.example.camera.model.CameraUiState
import com.example.camera.model.FlashModeOption
import com.example.camera.model.FrameRateOption
import com.example.camera.model.IosPhotoStyle
import com.example.camera.model.ResolutionOption
import com.example.camera.model.StabilizationMode
import com.example.ui.theme.AnodizedDeck
import com.example.ui.theme.BeautyRose
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.MachinedControl
import com.example.ui.theme.NightEmerald
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.OpticalCyan
import com.example.ui.theme.OpticalSurface
import com.example.ui.theme.PureOpticWhite
import com.example.ui.theme.RecordCrimson
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.TelemetryTextStyle
import com.example.ui.theme.TitaniumBorder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Top Optical HUD bar (respects top camera notch & status bar insets).
 */
@Composable
fun TopCameraHudBar(
    state: CameraUiState,
    onCycleFlash: () -> Unit,
    onOpenResolution: () -> Unit,
    onCycleStabilization: () -> Unit,
    onToggleRaw: () -> Unit,
    onToggleLowLight: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flash toggle
        val flashIcon = when (state.flashMode) {
            FlashModeOption.AUTO -> Icons.Rounded.FlashAuto
            FlashModeOption.ON -> Icons.Rounded.FlashOn
            FlashModeOption.TORCH -> Icons.Rounded.FlashlightOn
            FlashModeOption.OFF -> Icons.Rounded.FlashOff
        }
        val flashActive = state.flashMode != FlashModeOption.OFF
        HudCircleIconButton(
            icon = flashIcon,
            contentDescription = stringResource(R.string.cd_flash),
            active = flashActive,
            activeColor = CinemaAmber,
            testTag = "hud_flash_button",
            onClick = onCycleFlash
        )

        // Center Pro Pill Cluster (Resolution, OIS/EIS, RAW or Low-Light)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(ObsidianBlack.copy(alpha = 0.65f))
                .border(0.75.dp, PureOpticWhite.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudTextChip(
                text = "${state.resolution.shortLabel} ${state.frameRate.fps}",
                active = state.resolution == ResolutionOption.UHD_4K || state.resolution == ResolutionOption.UHD_8K,
                activeColor = CinemaAmber,
                testTag = "hud_resolution_chip",
                onClick = onOpenResolution
            )
            HudTextChip(
                text = state.stabilization.badge,
                active = state.stabilization != StabilizationMode.OFF,
                activeColor = OpticalCyan,
                testTag = "hud_ois_chip",
                onClick = onCycleStabilization
            )
            if (!state.activeMode.isVideoMode) {
                HudTextChip(
                    text = "RAW",
                    active = state.rawCaptureEnabled,
                    activeColor = CinemaAmber,
                    testTag = "hud_raw_chip",
                    onClick = onToggleRaw
                )
            } else {
                HudTextChip(
                    text = "NIGHT",
                    active = state.lowLightBoostEnabled,
                    activeColor = NightEmerald,
                    testTag = "hud_lowlight_chip",
                    onClick = onToggleLowLight
                )
            }
        }

        // Settings gear
        HudCircleIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.cd_settings),
            active = false,
            activeColor = CinemaAmber,
            testTag = "hud_settings_button",
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun HudCircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(testTag)
            .minimumInteractiveComponentSize()
            .size(42.dp)
            .clip(CircleShape)
            .background(ObsidianBlack.copy(alpha = 0.6f))
            .border(
                width = 0.8.dp,
                color = if (active) activeColor.copy(alpha = 0.7f) else PureOpticWhite.copy(alpha = 0.14f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) activeColor else PureOpticWhite,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HudTextChip(
    text: String,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) activeColor.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TelemetryTextStyle.copy(
                color = if (active) activeColor else SlateSecondary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * Quick Feature Strip directly above the shutter deck allowing instant access to:
 * iPhone Styles, Auto Beauty, Pro Manual Controls, Audio Mixer, and Cinematic Lens.
 */
@Composable
fun QuickFeatureTriggerRow(
    state: CameraUiState,
    onTogglePanel: (ActiveDrawerPanel) -> Unit,
    onToggleLowLight: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureQuickPill(
                icon = Icons.Rounded.AutoAwesome,
                label = if (state.selectedStyle == IosPhotoStyle.STANDARD) "Gaya iOS" else state.selectedStyle.label,
                selected = state.activePanel == ActiveDrawerPanel.STYLES || state.selectedStyle != IosPhotoStyle.STANDARD,
                accentColor = CinemaAmber,
                testTag = "quick_styles_pill",
                onClick = { onTogglePanel(ActiveDrawerPanel.STYLES) }
            )
        }
        item {
            FeatureQuickPill(
                icon = Icons.Rounded.FaceRetouchingNatural,
                label = if (state.beauty.enabled) "Beauty: ${state.beauty.preset.label}" else "Beauty",
                selected = state.activePanel == ActiveDrawerPanel.BEAUTY || state.beauty.enabled,
                accentColor = BeautyRose,
                testTag = "quick_beauty_pill",
                onClick = { onTogglePanel(ActiveDrawerPanel.BEAUTY) }
            )
        }
        item {
            FeatureQuickPill(
                icon = Icons.Rounded.Tune,
                label = if (state.manualExposure.isManualActive) "Manual ISO ${state.manualExposure.iso}" else "Eksposur Pro",
                selected = state.activePanel == ActiveDrawerPanel.PRO_MANUAL || state.manualExposure.isManualActive,
                accentColor = CinemaAmber,
                testTag = "quick_pro_pill",
                onClick = { onTogglePanel(ActiveDrawerPanel.PRO_MANUAL) }
            )
        }
        item {
            FeatureQuickPill(
                icon = if (state.audio.audioEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                label = if (state.audio.audioEnabled) "Audio ${state.audio.sourcePreset.label}" else "Audio Mati",
                selected = state.activePanel == ActiveDrawerPanel.AUDIO_MIXER,
                accentColor = OpticalCyan,
                testTag = "quick_audio_pill",
                onClick = { onTogglePanel(ActiveDrawerPanel.AUDIO_MIXER) }
            )
        }
        item {
            FeatureQuickPill(
                icon = Icons.Rounded.MovieFilter,
                label = "Cinema f/${String.format(Locale.US, "%.1f", state.cinematic.apertureFStop)}",
                selected = state.activePanel == ActiveDrawerPanel.CINEMATIC_LENS,
                accentColor = CinemaAmber,
                testTag = "quick_cinema_pill",
                onClick = { onTogglePanel(ActiveDrawerPanel.CINEMATIC_LENS) }
            )
        }
        item {
            FeatureQuickPill(
                icon = Icons.Rounded.DarkMode,
                label = "Low-Light+",
                selected = state.lowLightBoostEnabled,
                accentColor = NightEmerald,
                testTag = "quick_lowlight_pill",
                onClick = onToggleLowLight
            )
        }
    }
}

@Composable
private fun FeatureQuickPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .testTag(testTag)
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) accentColor.copy(alpha = 0.22f) else ObsidianBlack.copy(alpha = 0.64f)
            )
            .border(
                width = 0.85.dp,
                color = if (selected) accentColor else PureOpticWhite.copy(alpha = 0.16f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accentColor else PureOpticWhite,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) PureOpticWhite else SlateSecondary
        )
    }
}

/**
 * Expandable Precision Control Deck rendered smoothly above the shutter bar.
 */
@Composable
fun ActiveControlDrawer(
    state: CameraUiState,
    onSelectStyle: (IosPhotoStyle) -> Unit,
    onSelectBeautyPreset: (BeautyPreset) -> Unit,
    onUpdateBeautySliders: (Float?, Float?, Float?, Float?) -> Unit,
    onSetManualActive: (Boolean) -> Unit,
    onSetEv: (Int) -> Unit,
    onSetIso: (Int) -> Unit,
    onSetShutter: (Int) -> Unit,
    onSetKelvin: (Int) -> Unit,
    onSetFocus: (Float) -> Unit,
    onUpdateAudio: (Boolean?, AudioSourcePreset?, Float?, Boolean?, Boolean?) -> Unit,
    onUpdateCinematic: (Boolean?, Float?, Boolean?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.activePanel == ActiveDrawerPanel.NONE) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(OpticalSurface.copy(alpha = 0.90f))
            .border(1.dp, TitaniumBorder.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        when (state.activePanel) {
            ActiveDrawerPanel.STYLES -> IosStylesDrawerContent(
                selectedStyle = state.selectedStyle,
                onSelectStyle = onSelectStyle
            )
            ActiveDrawerPanel.BEAUTY -> BeautyDrawerContent(
                state = state,
                onSelectPreset = onSelectBeautyPreset,
                onUpdateSliders = onUpdateBeautySliders
            )
            ActiveDrawerPanel.PRO_MANUAL -> ProManualDrawerContent(
                state = state,
                onSetManualActive = onSetManualActive,
                onSetEv = onSetEv,
                onSetIso = onSetIso,
                onSetShutter = onSetShutter,
                onSetKelvin = onSetKelvin,
                onSetFocus = onSetFocus
            )
            ActiveDrawerPanel.AUDIO_MIXER -> AudioMixerDrawerContent(
                state = state,
                onUpdateAudio = onUpdateAudio
            )
            ActiveDrawerPanel.CINEMATIC_LENS -> CinematicDrawerContent(
                state = state,
                onUpdateCinematic = onUpdateCinematic,
                onSelectStyle = onSelectStyle
            )
            ActiveDrawerPanel.NONE -> Unit
        }
    }
}

@Composable
private fun IosStylesDrawerContent(
    selectedStyle: IosPhotoStyle,
    onSelectStyle: (IosPhotoStyle) -> Unit
) {
    Text(
        text = "EFEK WARNA REAL-TIME & GAYA iPHONE",
        style = TelemetryTextStyle.copy(color = CinemaAmber),
        modifier = Modifier.padding(bottom = 10.dp)
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(IosPhotoStyle.entries) { style ->
            val isSelected = style == selectedStyle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .testTag("style_item_${style.name.lowercase(Locale.US)}")
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) MachinedControl else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = if (isSelected) CinemaAmber else TitaniumBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectStyle(style) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(style.swatchColor)
                        .border(1.5.dp, PureOpticWhite.copy(alpha = 0.5f), CircleShape)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) CinemaAmber else PureOpticWhite
                )
                Text(
                    text = style.category,
                    style = TelemetryTextStyle.copy(fontSize = 9.sp, color = SlateSecondary)
                )
            }
        }
    }
}

@Composable
private fun BeautyDrawerContent(
    state: CameraUiState,
    onSelectPreset: (BeautyPreset) -> Unit,
    onUpdateSliders: (Float?, Float?, Float?, Float?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "AUTO BEAUTY STUDIO",
            style = TelemetryTextStyle.copy(color = BeautyRose)
        )
        Text(
            text = if (state.capabilities.supportsBeautyExtension) "Hardware + AI Tone" else "Optical Skin Engine",
            style = TelemetryTextStyle.copy(color = SlateSecondary, fontSize = 10.sp)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BeautyPreset.entries.forEach { preset ->
            val selected = state.beauty.preset == preset && (state.beauty.enabled || preset == BeautyPreset.OFF)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag("beauty_preset_${preset.name.lowercase(Locale.US)}")
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) BeautyRose.copy(alpha = 0.24f) else AnodizedDeck)
                    .border(
                        1.dp,
                        if (selected) BeautyRose else TitaniumBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectPreset(preset) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) PureOpticWhite else SlateSecondary
                )
            }
        }
    }
    if (state.beauty.enabled) {
        Spacer(modifier = Modifier.height(8.dp))
        CompactSliderRow(
            label = "Haluskan Kulit",
            valueText = "${(state.beauty.smooth * 100).roundToInt()}%",
            value = state.beauty.smooth,
            accent = BeautyRose,
            onValueChange = { onUpdateSliders(it, null, null, null) }
        )
        CompactSliderRow(
            label = "Cerahkan & Glow",
            valueText = "${(state.beauty.brightness * 100).roundToInt()}%",
            value = state.beauty.brightness,
            accent = BeautyRose,
            onValueChange = { onUpdateSliders(null, it, null, null) }
        )
        CompactSliderRow(
            label = "Rona Segar & Jernih",
            valueText = "${(state.beauty.clarity * 100).roundToInt()}%",
            value = state.beauty.clarity,
            accent = BeautyRose,
            onValueChange = { onUpdateSliders(null, null, it * 0.85f, it) }
        )
    }
}

@Composable
private fun ProManualDrawerContent(
    state: CameraUiState,
    onSetManualActive: (Boolean) -> Unit,
    onSetEv: (Int) -> Unit,
    onSetIso: (Int) -> Unit,
    onSetShutter: (Int) -> Unit,
    onSetKelvin: (Int) -> Unit,
    onSetFocus: (Float) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val manual = state.manualExposure
    val caps = state.capabilities

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "KONTROL EKSPOSUR MANUAL",
            style = TelemetryTextStyle.copy(color = CinemaAmber)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (manual.isManualActive) "MANUAL" else "AUTO",
                style = TelemetryTextStyle.copy(
                    color = if (manual.isManualActive) CinemaAmber else SlateSecondary
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Switch(
                checked = manual.isManualActive,
                onCheckedChange = onSetManualActive,
                modifier = Modifier.testTag("pro_manual_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = CinemaAmber
                )
            )
        }
    }

    val tabs = listOf(
        "EV" to String.format(Locale.US, "%+.1f", manual.evIndex * caps.evStep),
        "ISO" to "${manual.iso}",
        "S" to "1/${manual.shutterSpeedDenom}",
        "WB" to "${manual.whiteBalanceKelvin}K",
        "MF" to if (manual.manualFocusNormalized <= 0.02f) "AF" else "${(manual.manualFocusNormalized * 100).roundToInt()}%"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, (title, valStr) ->
            val active = selectedTab == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("pro_tab_${title.lowercase(Locale.US)}")
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) CinemaAmber.copy(alpha = 0.2f) else AnodizedDeck)
                    .border(
                        1.dp,
                        if (active) CinemaAmber else TitaniumBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { selectedTab = index }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = TelemetryTextStyle.copy(color = if (active) CinemaAmber else SlateSecondary))
                Text(valStr, style = TelemetryTextStyle.copy(color = PureOpticWhite, fontSize = 10.sp))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    when (selectedTab) {
        0 -> {
            val evMin = caps.evMin.toFloat()
            val evMax = caps.evMax.toFloat().coerceAtLeast(evMin + 1f)
            CompactSliderRow(
                label = "Exposure Value (EV)",
                valueText = String.format(Locale.US, "%+.1f EV", manual.evIndex * caps.evStep),
                value = manual.evIndex.toFloat(),
                valueRange = evMin..evMax,
                accent = CinemaAmber,
                onValueChange = { onSetEv(it.roundToInt()) }
            )
        }
        1 -> {
            CompactSliderRow(
                label = "Sensor ISO",
                valueText = "ISO ${manual.iso}",
                value = manual.iso.toFloat(),
                valueRange = caps.minIso.toFloat()..caps.maxIso.toFloat().coerceAtLeast(1600f),
                accent = CinemaAmber,
                onValueChange = { onSetIso((it / 50f).roundToInt() * 50) }
            )
        }
        2 -> {
            val shutterStops = listOf(4, 8, 15, 30, 60, 125, 250, 500, 1000, 2000, 4000)
            val currentIdx = shutterStops.indexOfFirst { it >= manual.shutterSpeedDenom }.takeIf { it >= 0 } ?: 5
            CompactSliderRow(
                label = "Shutter Speed",
                valueText = "1/${shutterStops[currentIdx]}s",
                value = currentIdx.toFloat(),
                valueRange = 0f..(shutterStops.lastIndex.toFloat()),
                accent = CinemaAmber,
                onValueChange = { onSetShutter(shutterStops[it.roundToInt().coerceIn(0, shutterStops.lastIndex)]) }
            )
        }
        3 -> {
            CompactSliderRow(
                label = "White Balance",
                valueText = "${manual.whiteBalanceKelvin}K",
                value = manual.whiteBalanceKelvin.toFloat(),
                valueRange = 2800f..7500f,
                accent = CinemaAmber,
                onValueChange = { onSetKelvin((it / 100f).roundToInt() * 100) }
            )
        }
        4 -> {
            CompactSliderRow(
                label = "Manual Focus (0% = Auto Focus)",
                valueText = if (manual.manualFocusNormalized <= 0.02f) "AUTO" else "MAKRO ${(manual.manualFocusNormalized * 100).roundToInt()}%",
                value = manual.manualFocusNormalized,
                valueRange = 0f..1f,
                accent = CinemaAmber,
                onValueChange = onSetFocus
            )
        }
    }
}

@Composable
private fun AudioMixerDrawerContent(
    state: CameraUiState,
    onUpdateAudio: (Boolean?, AudioSourcePreset?, Float?, Boolean?, Boolean?) -> Unit
) {
    val audio = state.audio
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = OpticalCyan, modifier = Modifier.size(18.dp))
            Text("KONTROL AUDIO MANUAL", style = TelemetryTextStyle.copy(color = OpticalCyan))
        }
        Switch(
            checked = audio.audioEnabled,
            onCheckedChange = { onUpdateAudio(it, null, null, null, null) },
            modifier = Modifier.testTag("audio_enable_switch"),
            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBlack, checkedTrackColor = OpticalCyan)
        )
    }

    if (audio.audioEnabled) {
        // Live multi-segment stereo VU Meter bar
        val levelNorm = ((audio.liveDbLevel + 60f) / 60f).coerceIn(0.02f, 1f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${audio.liveDbLevel.roundToInt()} dB",
                style = TelemetryTextStyle.copy(color = PureOpticWhite),
                modifier = Modifier.width(44.dp)
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (i in 0 until 18) {
                    val active = levelNorm >= (i + 1) / 18f
                    val segColor = when {
                        !active -> MachinedControl
                        i >= 15 -> RecordCrimson
                        i >= 12 -> CinemaAmber
                        else -> NightEmerald
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(segColor)
                    )
                }
            }
        }

        // Mic source presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioSourcePreset.entries.forEach { preset ->
                val selected = audio.sourcePreset == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) OpticalCyan.copy(alpha = 0.22f) else AnodizedDeck)
                        .border(1.dp, if (selected) OpticalCyan else TitaniumBorder, RoundedCornerShape(10.dp))
                        .clickable { onUpdateAudio(null, preset, null, null, null) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) PureOpticWhite else SlateSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        CompactSliderRow(
            label = "Mic Gain Boost",
            valueText = String.format(Locale.US, "%+.0f dB", audio.gainDb),
            value = audio.gainDb,
            valueRange = -12f..12f,
            accent = OpticalCyan,
            onValueChange = { onUpdateAudio(null, null, it, null, null) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToggleChipSmall(
                label = "Peredam Noise",
                active = audio.noiseReduction,
                accent = OpticalCyan,
                modifier = Modifier.weight(1f),
                onClick = { onUpdateAudio(null, null, null, !audio.noiseReduction, null) }
            )
            ToggleChipSmall(
                label = "Filter Angin",
                active = audio.windCutFilter,
                accent = OpticalCyan,
                modifier = Modifier.weight(1f),
                onClick = { onUpdateAudio(null, null, null, null, !audio.windCutFilter) }
            )
        }
    }
}

@Composable
private fun CinematicDrawerContent(
    state: CameraUiState,
    onUpdateCinematic: (Boolean?, Float?, Boolean?) -> Unit,
    onSelectStyle: (IosPhotoStyle) -> Unit
) {
    val cine = state.cinematic
    Text(
        text = "CINEMATIC FILMMAKER CONTROLS",
        style = TelemetryTextStyle.copy(color = CinemaAmber),
        modifier = Modifier.padding(bottom = 6.dp)
    )
    CompactSliderRow(
        label = "Depth Bokeh Aperture",
        valueText = "f/${String.format(Locale.US, "%.1f", cine.apertureFStop)}",
        value = cine.apertureFStop,
        valueRange = 1.4f..8.0f,
        accent = CinemaAmber,
        onValueChange = { onUpdateCinematic(null, it, null) }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToggleChipSmall(
            label = "Frame 2.39:1",
            active = cine.anamorphicBars,
            accent = CinemaAmber,
            modifier = Modifier.weight(1f),
            onClick = { onUpdateCinematic(!cine.anamorphicBars, null, null) }
        )
        ToggleChipSmall(
            label = "24fps Cinema",
            active = cine.lock24Fps,
            accent = CinemaAmber,
            modifier = Modifier.weight(1f),
            onClick = { onUpdateCinematic(null, null, !cine.lock24Fps) }
        )
        ToggleChipSmall(
            label = "Cinema LUT",
            active = state.selectedStyle == IosPhotoStyle.CINEMA_TEAL_ORANGE,
            accent = OpticalCyan,
            modifier = Modifier.weight(1f),
            onClick = {
                val next = if (state.selectedStyle == IosPhotoStyle.CINEMA_TEAL_ORANGE) {
                    IosPhotoStyle.STANDARD
                } else {
                    IosPhotoStyle.CINEMA_TEAL_ORANGE
                }
                onSelectStyle(next)
            }
        )
    }
}

@Composable
private fun ToggleChipSmall(
    label: String,
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.22f) else AnodizedDeck)
            .border(1.dp, if (active) accent else TitaniumBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) PureOpticWhite else SlateSecondary
        )
    }
}

@Composable
fun CompactSliderRow(
    label: String,
    valueText: String,
    value: Float,
    accent: Color,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = SlateSecondary)
            Text(valueText, style = TelemetryTextStyle.copy(color = accent))
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MachinedControl
            ),
            modifier = Modifier.height(34.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionPickerSheet(
    state: CameraUiState,
    onSelectResolution: (ResolutionOption) -> Unit,
    onSelectFrameRate: (FrameRateOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OpticalSurface,
        contentColor = PureOpticWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Resolusi & Frame Rate",
                style = MaterialTheme.typography.titleLarge,
                color = PureOpticWhite
            )
            Text(
                text = "Sensor perangkat: ${state.capabilities.activeHardwareSummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = OpticalCyan,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Text("RESOLUSI GAMBAR & VIDEO", style = TelemetryTextStyle.copy(color = SlateSecondary))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionOption.entries.forEach { res ->
                    val selected = state.resolution == res
                    val hwBadge = when (res) {
                        ResolutionOption.UHD_8K -> if (state.capabilities.supports8K) "NATIVE" else "AUTO-MAX"
                        ResolutionOption.UHD_4K -> if (state.capabilities.supports4K) "NATIVE" else "AUTO-MAX"
                        else -> "READY"
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("res_option_${res.shortLabel.lowercase(Locale.US)}")
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) CinemaAmber.copy(alpha = 0.2f) else AnodizedDeck)
                            .border(
                                1.2.dp,
                                if (selected) CinemaAmber else TitaniumBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectResolution(res) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = res.shortLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) CinemaAmber else PureOpticWhite
                        )
                        Text(
                            text = hwBadge,
                            style = TelemetryTextStyle.copy(fontSize = 9.sp, color = SlateSecondary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("FRAME RATE PEREKAMAN", style = TelemetryTextStyle.copy(color = SlateSecondary))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FrameRateOption.entries.forEach { fps ->
                    val selected = state.frameRate == fps
                    val subtitle = when (fps) {
                        FrameRateOption.FPS_24 -> "Sinematik"
                        FrameRateOption.FPS_30 -> "Standar Jernih"
                        FrameRateOption.FPS_60 -> "Super Halus"
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fps_option_${fps.fps}")
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) OpticalCyan.copy(alpha = 0.2f) else AnodizedDeck)
                            .border(
                                1.2.dp,
                                if (selected) OpticalCyan else TitaniumBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectFrameRate(fps) }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${fps.fps} FPS",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) OpticalCyan else PureOpticWhite
                        )
                        Text(
                            text = subtitle,
                            style = TelemetryTextStyle.copy(fontSize = 9.sp, color = SlateSecondary)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsSheet(
    state: CameraUiState,
    onToggleGrid: (Boolean) -> Unit,
    onToggleHorizon: (Boolean) -> Unit,
    onToggleFullScreen: (Boolean) -> Unit,
    onToggleRaw: () -> Unit,
    onCycleStabilization: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OpticalSurface,
        contentColor = PureOpticWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pengaturan OptixPro",
                style = MaterialTheme.typography.titleLarge,
                color = PureOpticWhite
            )

            // Hardware Capabilities & Offline Full Version Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AnodizedDeck)
                    .border(1.dp, TitaniumBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("DIAGNOSTIK SENSOR PERANGKAT", style = TelemetryTextStyle.copy(color = CinemaAmber))
                    Text("100% OFFLINE • FULL", style = TelemetryTextStyle.copy(color = NightEmerald))
                }
                Text(
                    text = state.capabilities.activeHardwareSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureOpticWhite
                )
                Text(
                    text = "Semua fitur aktif penuh tanpa internet • Otomatis simpan ke Galeri (Pictures/OptixPro & Movies/OptixPro)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightEmerald
                )
            }

            SettingsSwitchItem(
                icon = Icons.Rounded.Fullscreen,
                title = "Layar Penuh (Full Screen)",
                subtitle = "Tampilan viewfinder penuh tanpa batas bar sistem",
                checked = state.isImmersiveFullScreen,
                onCheckedChange = onToggleFullScreen
            )
            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f))
            SettingsSwitchItem(
                icon = Icons.Rounded.GridOn,
                title = "Garis Bantu Rule-of-Thirds",
                subtitle = "Komposisi presisi saat memotret & merekam",
                checked = state.showGrid,
                onCheckedChange = onToggleGrid
            )
            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f))
            SettingsSwitchItem(
                icon = Icons.Rounded.Straighten,
                title = "Waterpass Horizon Digital",
                subtitle = "Menggunakan sensor akselerometer perangkat",
                checked = state.showHorizonLevel,
                onCheckedChange = onToggleHorizon
            )
            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f))
            SettingsSwitchItem(
                icon = Icons.Rounded.RawOn,
                title = "Tangkap Foto RAW DNG",
                subtitle = if (state.capabilities.supportsRaw) "Didukung penuh oleh sensor perangkat ini" else "Fallback otomatis ke JPEG 98% super tajam",
                checked = state.rawCaptureEnabled,
                onCheckedChange = { onToggleRaw() }
            )
            HorizontalDivider(color = TitaniumBorder.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onCycleStabilization)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Vibration, contentDescription = null, tint = OpticalCyan)
                    Column {
                        Text("Stabilisasi OIS & EIS", style = MaterialTheme.typography.titleMedium, color = PureOpticWhite)
                        Text(state.stabilization.description, style = MaterialTheme.typography.bodyMedium, color = SlateSecondary)
                    }
                }
                Text(
                    text = state.stabilization.badge,
                    style = TelemetryTextStyle.copy(color = OpticalCyan, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = CinemaAmber)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = PureOpticWhite)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SlateSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = CinemaAmber
            )
        )
    }
}
