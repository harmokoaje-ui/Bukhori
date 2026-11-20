package com.example.camera.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ColorFilter
import com.example.camera.model.BeautySettings
import com.example.camera.model.CropAspect
import com.example.camera.model.IosPhotoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    /**
     * Builds a combined 4x5 Android ColorMatrix for the selected iPhone style,
     * optional Auto Beauty tone adjustments, low-light clarity boost, and manual editor sliders.
     */
    fun buildAndroidColorMatrix(
        style: IosPhotoStyle,
        beauty: BeautySettings? = null,
        lowLightBoost: Boolean = false,
        brightnessDelta: Float = 0f,
        contrastDelta: Float = 0f,
        saturationDelta: Float = 0f
    ): ColorMatrix {
        val result = ColorMatrix()

        // 1. Saturation
        val beautySatBoost = if (beauty != null && beauty.enabled) beauty.rosyTone * 0.14f else 0f
        val totalSat = (style.saturation + beautySatBoost + saturationDelta).coerceIn(0f, 2.2f)
        val satMatrix = ColorMatrix().apply { setSaturation(totalSat) }
        result.postConcat(satMatrix)

        // 2. Contrast & Brightness / Exposure / Low-Light Lift
        val beautyBright = if (beauty != null && beauty.enabled) beauty.brightness * 22f else 0f
        val beautyClarity = if (beauty != null && beauty.enabled) beauty.clarity * 0.10f else 0f
        val lowLightLift = if (lowLightBoost) 24f else 0f
        val lowLightContrast = if (lowLightBoost) 0.08f else 0f

        val totalContrast = (style.contrast + beautyClarity + lowLightContrast + contrastDelta)
            .coerceIn(0.6f, 1.85f)
        val contrastTranslate = (-0.5f * totalContrast + 0.5f) * 255f
        val totalTranslate = contrastTranslate +
            (style.exposureBoost * 55f) +
            beautyBright +
            lowLightLift +
            (brightnessDelta * 65f)

        // 3. Warmth & Rosy Skin Tone Balance (R/G/B channel gains)
        val rosyRed = if (beauty != null && beauty.enabled) beauty.rosyTone * 0.07f else 0f
        val rosyBlue = if (beauty != null && beauty.enabled) -beauty.rosyTone * 0.03f else 0f
        val rGain = (1f + style.warmth * 0.22f + rosyRed).coerceIn(0.75f, 1.35f) * totalContrast
        val gGain = (1f + style.warmth * 0.04f).coerceIn(0.80f, 1.30f) * totalContrast
        val bGain = (1f - style.warmth * 0.24f + rosyBlue).coerceIn(0.75f, 1.35f) * totalContrast

        val toneMatrix = ColorMatrix(
            floatArrayOf(
                rGain, 0f, 0f, 0f, totalTranslate,
                0f, gGain, 0f, 0f, totalTranslate,
                0f, 0f, bGain, 0f, totalTranslate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(toneMatrix)
        return result
    }

    /**
     * Converts the Android ColorMatrix to a Compose ColorFilter for real-time Image previews.
     */
    fun buildComposeColorFilter(
        style: IosPhotoStyle,
        beauty: BeautySettings? = null,
        lowLightBoost: Boolean = false,
        brightnessDelta: Float = 0f,
        contrastDelta: Float = 0f,
        saturationDelta: Float = 0f
    ): ColorFilter {
        val cm = buildAndroidColorMatrix(
            style = style,
            beauty = beauty,
            lowLightBoost = lowLightBoost,
            brightnessDelta = brightnessDelta,
            contrastDelta = contrastDelta,
            saturationDelta = saturationDelta
        )
        return ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(cm.array))
    }

    /**
     * Processes a freshly captured temporary JPEG file with iPhone Styles, Auto Beauty,
     * Low-Light Clarity, and optional cinematic aspect crop, then writes a crisp high-res
     * JPEG straight to the device Gallery (Pictures/OptixPro).
     */
    suspend fun processCapturedFileAndSaveToGallery(
        context: Context,
        tempFile: File,
        style: IosPhotoStyle,
        beauty: BeautySettings,
        lowLightBoost: Boolean,
        cinematicCrop: Boolean,
        mirrorHorizontally: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeOrientedBitmap(tempFile.absolutePath, mirrorHorizontally)
                ?: return@withContext null

            val cropAspect = if (cinematicCrop) CropAspect.ANAMORPHIC_239 else CropAspect.ORIGINAL
            val processed = applyFiltersAndEnhancements(
                source = bitmap,
                style = style,
                beauty = beauty,
                lowLightBoost = lowLightBoost,
                cropAspect = cropAspect
            )
            if (processed !== bitmap) {
                bitmap.recycle()
            }

            val savedUri = saveBitmapToGallery(
                context = context,
                bitmap = processed,
                prefix = if (lowLightBoost) "OptixPro_Night" else "OptixPro"
            )
            processed.recycle()
            tempFile.delete()
            savedUri
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Processes an existing gallery photo URI inside the built-in Studio Editor
     * and saves the edited high-res copy to Pictures/OptixPro.
     */
    suspend fun processUriAndSaveToGallery(
        context: Context,
        sourceUri: Uri,
        style: IosPhotoStyle,
        beautySmooth: Float,
        brightnessDelta: Float,
        contrastDelta: Float,
        saturationDelta: Float,
        sharpnessDelta: Float,
        cropAspect: CropAspect
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val sourceBitmap = decodeOrientedBitmapFromUri(context, sourceUri)
                ?: return@withContext null

            val beauty = BeautySettings(
                enabled = beautySmooth > 0.01f,
                smooth = beautySmooth,
                brightness = beautySmooth * 0.25f,
                rosyTone = beautySmooth * 0.22f,
                clarity = sharpnessDelta
            )

            val processed = applyFiltersAndEnhancements(
                source = sourceBitmap,
                style = style,
                beauty = beauty,
                lowLightBoost = false,
                brightnessDelta = brightnessDelta,
                contrastDelta = contrastDelta,
                saturationDelta = saturationDelta,
                cropAspect = cropAspect
            )
            if (processed !== sourceBitmap) {
                sourceBitmap.recycle()
            }

            val savedUri = saveBitmapToGallery(
                context = context,
                bitmap = processed,
                prefix = "OptixPro_Edit"
            )
            processed.recycle()
            savedUri
        } catch (_: Exception) {
            null
        }
    }

    private fun applyFiltersAndEnhancements(
        source: Bitmap,
        style: IosPhotoStyle,
        beauty: BeautySettings,
        lowLightBoost: Boolean,
        brightnessDelta: Float = 0f,
        contrastDelta: Float = 0f,
        saturationDelta: Float = 0f,
        cropAspect: CropAspect = CropAspect.ORIGINAL
    ): Bitmap {
        val cropped = cropBitmap(source, cropAspect)
        val width = cropped.width
        val height = cropped.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Step 1: Apply color grade matrix (iPhone style + beauty tone + low light)
        val colorMatrix = buildAndroidColorMatrix(
            style = style,
            beauty = beauty,
            lowLightBoost = lowLightBoost,
            brightnessDelta = brightnessDelta,
            contrastDelta = contrastDelta,
            saturationDelta = saturationDelta
        )
        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(cropped, 0f, 0f, mainPaint)

        // Step 2: Soft optical skin-smoothing bloom pass (preserves edge clarity, avoids blurry mush)
        if (beauty.enabled && beauty.smooth > 0.04f) {
            val scaleFactor = 0.18f
            val smallW = (width * scaleFactor).roundToInt().coerceAtLeast(32)
            val smallH = (height * scaleFactor).roundToInt().coerceAtLeast(32)
            val softLayer = Bitmap.createScaledBitmap(output, smallW, smallH, true)
            val bloomAlpha = (beauty.smooth * 68f).roundToInt().coerceIn(8, 72)
            val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = bloomAlpha
            }
            val dstRect = android.graphics.Rect(0, 0, width, height)
            canvas.drawBitmap(softLayer, null, dstRect, bloomPaint)
            softLayer.recycle()
        }

        // Step 3: Subtle optical lens vignette for cinematic/dramatic iOS styles
        if (style.vignette > 0.02f) {
            val radius = (min(width, height) * 0.78f).coerceAtLeast(1f)
            val edgeAlpha = (style.vignette * 170f).roundToInt().coerceIn(0, 180)
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    width / 2f,
                    height / 2f,
                    radius,
                    intArrayOf(0x00000000, 0x00000000, (edgeAlpha shl 24)),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        }

        if (cropped !== source) {
            cropped.recycle()
        }
        return output
    }

    private fun cropBitmap(source: Bitmap, aspect: CropAspect): Bitmap {
        val targetRatio = aspect.ratio ?: return source
        val srcW = source.width
        val srcH = source.height
        val srcRatio = srcW.toFloat() / srcH.toFloat()

        return if (srcRatio > targetRatio) {
            val newW = (srcH * targetRatio).roundToInt().coerceIn(1, srcW)
            val x = ((srcW - newW) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, x, 0, newW, srcH)
        } else {
            val newH = (srcW / targetRatio).roundToInt().coerceIn(1, srcH)
            val y = ((srcH - newH) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, 0, y, srcW, newH)
        }
    }

    private fun decodeOrientedBitmap(path: String, mirrorHorizontally: Boolean): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = BitmapFactory.decodeFile(path, opts) ?: return null
        val exifOrientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyExifTransform(raw, exifOrientation, mirrorHorizontally)
    }

    private fun decodeOrientedBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxDim / sampleSize > 4096) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val exifOrientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        return applyExifTransform(raw, exifOrientation, mirrorHorizontally = false)
    }

    private fun applyExifTransform(
        bitmap: Bitmap,
        orientation: Int,
        mirrorHorizontally: Boolean
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (mirrorHorizontally) {
            matrix.postScale(-1f, 1f)
        }
        if (matrix.isIdentity) return bitmap
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    private fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        prefix: String
    ): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "${prefix}_${timestamp}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OptixPro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            // 98% high-clarity JPEG compression prevents macro-blocking / pixelation
            bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        return uri
    }
}
