package com.example.camera.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoTrimmerEngine {

    data class VideoInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val thumbnails: List<Bitmap>
    )

    suspend fun inspectVideo(
        context: Context,
        uri: Uri,
        frameCount: Int = 8
    ): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val thumbs = mutableListOf<Bitmap>()
        var durationMs = 0L
        var width = 0
        var height = 0
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            if (durationMs > 0L) {
                val stepUs = (durationMs * 1000L) / frameCount.coerceAtLeast(1)
                for (i in 0 until frameCount) {
                    val timeUs = (i * stepUs).coerceAtMost(durationMs * 1000L)
                    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            160,
                            120
                        )
                    } else {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?.let { Bitmap.createScaledBitmap(it, 160, 120, true) }
                    }
                    if (frame != null) thumbs.add(frame)
                }
            }
        } catch (_: Exception) {
            // Gracefully return partial metadata if codec cannot decode a frame
        } finally {
            runCatching { retriever.release() }
        }
        VideoInfo(durationMs = durationMs, width = width, height = height, thumbnails = thumbs)
    }

    /**
     * Losslessly trims an MP4 video between [startMs] and [endMs] preserving original HD/4K/8K
     * crispness and crystal-clear audio, then writes the result directly to Movies/OptixPro.
     */
    suspend fun trimAndSaveToGallery(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        includeAudio: Boolean = true
    ): Uri? = withContext(Dispatchers.IO) {
        val tempOutput = File(context.cacheDir, "optix_trim_${System.currentTimeMillis()}.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, sourceUri, null)
            muxer = MediaMuxer(tempOutput.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Preserve original video rotation metadata
            val retriever = MediaMetadataRetriever()
            val rotation = runCatching {
                retriever.setDataSource(context, sourceUri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
            }.getOrDefault(0)
            runCatching { retriever.release() }
            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }

            val indexMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val keepTrack = mime.startsWith("video/") || (includeAudio && mime.startsWith("audio/"))
                if (keepTrack) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    indexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (size > maxBufferSize) maxBufferSize = size
                    }
                }
            }

            if (indexMap.isEmpty()) return@withContext null

            val startUs = (startMs.coerceAtLeast(0L)) * 1000L
            val endUs = (endMs.coerceAtLeast(startMs + 250L)) * 1000L

            muxer.start()
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val trackStartUs = mutableMapOf<Int, Long>()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                val trackIndex = extractor.sampleTrackIndex
                val dstTrack = indexMap[trackIndex]
                if (dstTrack != null && sampleTimeUs >= startUs) {
                    val baseTime = trackStartUs.getOrPut(dstTrack) { sampleTimeUs }
                    bufferInfo.presentationTimeUs = (sampleTimeUs - baseTime).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()

            val galleryUri = copyVideoToGallery(context, tempOutput)
            tempOutput.delete()
            galleryUri
        } catch (_: Exception) {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            tempOutput.delete()
            null
        }
    }

    private fun copyVideoToGallery(context: Context, file: File): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "OptixPro_Trim_${timestamp}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OptixPro")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
        return uri
    }
}
