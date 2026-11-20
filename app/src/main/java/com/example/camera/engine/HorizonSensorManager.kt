package com.example.camera.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2

/**
 * Streams real-time device roll angle in degrees from the hardware gravity/accelerometer sensor
 * for the pro viewfinder digital horizon level.
 */
class HorizonSensorManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun rollDegreesFlow(): Flow<Float> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (manager == null || sensor == null) {
            trySend(0f)
            close()
            return@callbackFlow
        }

        var smoothedRoll = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val angleRad = atan2(x.toDouble(), y.toDouble())
                val rawDeg = Math.toDegrees(angleRad).toFloat().coerceIn(-45f, 45f)
                smoothedRoll = smoothedRoll * 0.82f + rawDeg * 0.18f
                trySend(smoothedRoll)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose {
            manager.unregisterListener(listener)
        }
    }
}
