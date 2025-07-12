package com.compose.spydog.detector

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

data class WiFiInfo(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,
    val frequency: Int,
    val capabilities: String,
    val level: String,
    val channel: Int,
    val bandwidth: String,
    val isSecure: Boolean,
    val securityType: String,
    val distance: String,
    val isConnected: Boolean = false
)

data class SuspiciousDevice(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,
    val frequency: Int,
    val reason: String
)

class WiFiScanner(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val _allWiFiNetworks = mutableStateOf<List<WiFiInfo>>(emptyList())
    val allWiFiNetworks: State<List<WiFiInfo>> = _allWiFiNetworks
    
    private val _suspiciousDevices = mutableStateOf<List<SuspiciousDevice>>(emptyList())
    val suspiciousDevices: State<List<SuspiciousDevice>> = _suspiciousDevices
    
    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning
    
    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage
    
    private var isReceiverRegistered = false
    private var lastScanTime = 0L
    private val SCAN_THROTTLE_MS = 15000L // 15秒扫描间隔
    
    private val wifiScanReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure("扫描失败，请重试")
            }
            _isScanning.value = false
        }
    }
    
    fun startScan() {
        // 清除之前的错误信息
        _errorMessage.value = null
        
        // 检查是否正在扫描
        if (_isScanning.value) {
            _errorMessage.value = "正在扫描中，请稍候..."
            return
        }
        
        // 检查扫描频率限制
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < SCAN_THROTTLE_MS) {
            val remainingTime = (SCAN_THROTTLE_MS - (currentTime - lastScanTime)) / 1000
            _errorMessage.value = "请等待 ${remainingTime} 秒后再次扫描"
            return
        }
        
        // 检查WiFi是否开启
        if (!wifiManager.isWifiEnabled) {
            _errorMessage.value = "请先开启WiFi功能"
            return
        }
        
        try {
            _isScanning.value = true
            lastScanTime = currentTime
            
            // 注册广播接收器
            if (!isReceiverRegistered) {
                val intentFilter = IntentFilter()
                intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                context.registerReceiver(wifiScanReceiver, intentFilter)
                isReceiverRegistered = true
            }
            
            // 开始扫描
            val success = wifiManager.startScan()
            if (!success) {
                _isScanning.value = false
                scanFailure("扫描启动失败，可能是权限不足或系统限制")
            }
            
            // 设置超时处理
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (_isScanning.value) {
                    _isScanning.value = false
                    scanFailure("扫描超时，请检查权限设置")
                }
            }, 10000) // 10秒超时
            
        } catch (e: SecurityException) {
            _isScanning.value = false
            scanFailure("权限不足，请在设置中授予位置权限")
        } catch (e: Exception) {
            _isScanning.value = false
            scanFailure("扫描出错: ${e.message}")
        }
    }
    
    fun stopScan() {
        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(wifiScanReceiver)
                isReceiverRegistered = false
            }
        } catch (e: Exception) {
            // Receiver可能已经注销
        }
        _isScanning.value = false
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    private fun getCurrentConnectedWiFi(): String? {
        return try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo != null && wifiInfo.ssid != null) {
                // 移除SSID周围的引号
                wifiInfo.ssid.replace("\"", "")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun scanSuccess() {
        val results = wifiManager.scanResults
        val allNetworks = mutableListOf<WiFiInfo>()
        val suspicious = mutableListOf<SuspiciousDevice>()
        
        // 获取当前连接的WiFi SSID
        val currentConnectedSSID = getCurrentConnectedWiFi()
        
        results.forEach { result ->
            // 计算信号强度等级
            val level = when {
                result.level >= -50 -> "极强"
                result.level >= -60 -> "强"
                result.level >= -70 -> "中等"
                result.level >= -80 -> "弱"
                else -> "极弱"
            }
            
            // 计算大概距离（基于信号强度）
            val distance = calculateDistance(result.level, result.frequency)
            
            // 判断安全类型
            val isSecure = result.capabilities.contains("WPA") || 
                          result.capabilities.contains("WEP") || 
                          result.capabilities.contains("PSK")
            
            val securityType = when {
                result.capabilities.contains("WPA3") -> "WPA3"
                result.capabilities.contains("WPA2") -> "WPA2"
                result.capabilities.contains("WPA") -> "WPA"
                result.capabilities.contains("WEP") -> "WEP"
                else -> "开放"
            }
            
            // 计算信道
            val channel = frequencyToChannel(result.frequency)
            
            // 判断带宽
            val bandwidth = when {
                result.frequency > 5000 -> "5GHz"
                else -> "2.4GHz"
            }
            
            // 判断是否为当前连接的WiFi
            val isConnected = currentConnectedSSID != null && 
                             result.SSID == currentConnectedSSID
            
            // 添加到所有网络列表
            allNetworks.add(
                WiFiInfo(
                    ssid = result.SSID.ifEmpty { "隐藏网络" },
                    bssid = result.BSSID,
                    signalStrength = result.level,
                    frequency = result.frequency,
                    capabilities = result.capabilities,
                    level = level,
                    channel = channel,
                    bandwidth = bandwidth,
                    isSecure = isSecure,
                    securityType = securityType,
                    distance = distance,
                    isConnected = isConnected
                )
            )
            
            // 检测可疑设备
            val suspiciousReasons = mutableListOf<String>()
            
            // 检测隐藏SSID
            if (result.SSID.isEmpty() || result.SSID.isBlank()) {
                suspiciousReasons.add("隐藏SSID")
            }
            
            // 检测可疑的SSID名称
            val suspiciousNames = listOf("camera", "spy", "hidden", "cam", "surveillance", "monitor")
            if (suspiciousNames.any { result.SSID.lowercase().contains(it) }) {
                suspiciousReasons.add("可疑设备名称")
            }
            
            // 检测信号强度异常（太强可能是近距离设备）
            if (result.level > -30) {
                suspiciousReasons.add("信号强度异常")
            }
            
            // 检测异常频率
            if (result.frequency < 2400 || (result.frequency > 2500 && result.frequency < 5000)) {
                suspiciousReasons.add("异常频率")
            }
            
            if (suspiciousReasons.isNotEmpty()) {
                suspicious.add(
                    SuspiciousDevice(
                        ssid = result.SSID.ifEmpty { "隐藏网络" },
                        bssid = result.BSSID,
                        signalStrength = result.level,
                        frequency = result.frequency,
                        reason = suspiciousReasons.joinToString(", ")
                    )
                )
            }
        }
        
        // 按连接状态和信号强度排序（当前连接的WiFi排在最前面）
        _allWiFiNetworks.value = allNetworks.sortedWith(
            compareByDescending<WiFiInfo> { it.isConnected }
                .thenByDescending { it.signalStrength }
        )
        _suspiciousDevices.value = suspicious
    }
    
    private fun scanFailure(message: String) {
        _errorMessage.value = message
        _allWiFiNetworks.value = emptyList()
        _suspiciousDevices.value = emptyList()
    }
    
    private fun calculateDistance(signalLevel: Int, frequency: Int): String {
        // 简化的距离计算公式
        val distance =
            10.0.pow((27.55 - (20 * log10(frequency.toDouble())) + abs(signalLevel)) / 20.0)
        return when {
            distance < 1 -> "<1米"
            distance < 5 -> "${distance.toInt()}米"
            distance < 50 -> "${distance.toInt()}米"
            else -> ">50米"
        }
    }
    
    private fun frequencyToChannel(frequency: Int): Int {
        return when {
            frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
            frequency in 5170..5825 -> (frequency - 5000) / 5
            else -> 0
        }
    }
}