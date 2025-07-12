package com.compose.spydog.detector

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.get

data class DetectionResult(
    val isDetected: Boolean,
    val confidence: Float,
    val suspiciousPoints: List<Pair<Int, Int>>
)

class InfraredDetector {
    private val _detectionResult = mutableStateOf(DetectionResult(false, 0f, emptyList()))
    val detectionResult: State<DetectionResult> = _detectionResult
    
    suspend fun analyzeFrame(bitmap: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        val suspiciousPoints = mutableListOf<Pair<Int, Int>>()
        val width = bitmap.width
        val height = bitmap.height
        
        // 转换为灰度并检测亮点
        var brightPixelCount = 0
        val threshold = 200 // 亮度阈值
        
        for (x in 0 until width step 10) {
            for (y in 0 until height step 10) {
                val pixel = bitmap[x, y]
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                
                if (brightness > threshold) {
                    // 检查周围像素是否也很亮（可能是反射点）
                    val surroundingBrightness = checkSurroundingBrightness(bitmap, x, y)
                    if (surroundingBrightness > threshold * 0.8) {
                        suspiciousPoints.add(Pair(x, y))
                        brightPixelCount++
                    }
                }
            }
        }
        
        val confidence = minOf(brightPixelCount / 10f, 1f)
        val isDetected = confidence > 0.3f
        
        val result = DetectionResult(isDetected, confidence, suspiciousPoints)
        _detectionResult.value = result
        result
    }
    
    private fun checkSurroundingBrightness(bitmap: Bitmap, centerX: Int, centerY: Int): Float {
        var totalBrightness = 0f
        var pixelCount = 0
        
        for (dx in -2..2) {
            for (dy in -2..2) {
                val x = centerX + dx
                val y = centerY + dy
                if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                    val pixel = bitmap[x, y]
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    totalBrightness += brightness
                    pixelCount++
                }
            }
        }
        
        return if (pixelCount > 0) totalBrightness / pixelCount else 0f
    }
}