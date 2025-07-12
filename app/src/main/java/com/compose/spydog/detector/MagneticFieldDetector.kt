package com.compose.spydog.detector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlin.math.sqrt

class MagneticFieldDetector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    
    private val _magneticStrength = mutableStateOf(0f)
    val magneticStrength: State<Float> = _magneticStrength
    
    private val _isAnomalyDetected = mutableStateOf(false)
    val isAnomalyDetected: State<Boolean> = _isAnomalyDetected
    
    private val baselineReadings = mutableListOf<Float>()
    private var baseline = 0f
    private val anomalyThreshold = 15f // 磁场异常阈值
    
    fun startDetection() {
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
    
    fun stopDetection() {
        sensorManager.unregisterListener(this)
    }
    
    fun resetDetection() {
        baselineReadings.clear()
        baseline = 0f
        _isAnomalyDetected.value = false
        _magneticStrength.value = 0f
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                
                val magnitude = sqrt(x * x + y * y + z * z)
                _magneticStrength.value = magnitude
                
                // 建立基线
                if (baselineReadings.size < 50) {
                    baselineReadings.add(magnitude)
                    if (baselineReadings.size == 50) {
                        baseline = baselineReadings.average().toFloat()
                    }
                } else {
                    // 检测异常
                    val deviation = kotlin.math.abs(magnitude - baseline)
                    _isAnomalyDetected.value = deviation > anomalyThreshold
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 不需要处理精度变化
    }
}