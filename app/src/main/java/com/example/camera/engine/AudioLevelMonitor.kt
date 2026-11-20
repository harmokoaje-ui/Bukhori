package com.example.camera.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.camera.model.AudioSourcePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Monitors live microphone RMS amplitude in decibels for the pro audio VU meter.
 * Automatically releases the microphone before active video recording starts so CameraX
 * Recorder has exclusive low-latency audio capture.
 */
class AudioLevelMonitor(private val context: Context) {

    private val _dbLevel = MutableStateFlow(-54f)
    val dbLevel: StateFlow<Float> = _dbLevel.asStateFlow()

    private var monitorJob: Job? = null
    private var audioRecord: AudioRecord? = null

    fun start(
        scope: CoroutineScope,
        preset: AudioSourcePreset,
        gainDb: Float
    ) {
        stop()
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return

        val audioSource = when (preset) {
            AudioSourcePreset.CAMCORDER -> MediaRecorder.AudioSource.CAMCORDER
            AudioSourcePreset.UNPROCESSED -> MediaRecorder.AudioSource.UNPROCESSED
            AudioSourcePreset.VOICE_CLEAR -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return

        val recorder = try {
            AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (_: SecurityException) {
            return
        } catch (_: Exception) {
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        audioRecord = recorder
        monitorJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(minBuf / 2)
            try {
                recorder.startRecording()
                var smoothedDb = -54f
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sumSq += sample * sample
                        }
                        val rms = sqrt(sumSq / read)
                        val rawDb = if (rms > 1.0) {
                            (20.0 * log10(rms / 32767.0)).toFloat() + gainDb
                        } else {
                            -60f
                        }
                        val clamped = rawDb.coerceIn(-60f, 0f)
                        smoothedDb = smoothedDb * 0.65f + clamped * 0.35f
                        _dbLevel.value = smoothedDb
                    }
                }
            } catch (_: Exception) {
                _dbLevel.value = -60f
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        audioRecord?.let { rec ->
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        audioRecord = null
        _dbLevel.value = -56f
    }
}
