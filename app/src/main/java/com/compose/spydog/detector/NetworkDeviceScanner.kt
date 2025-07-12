package com.compose.spydog.detector

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

data class NetworkDevice(
    val ipAddress: String,
    val macAddress: String?,
    val hostname: String?,
    val isReachable: Boolean,
    val responseTime: Long,
    val deviceType: String,
    val isCurrentDevice: Boolean = false
)

class NetworkDeviceScanner(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val _devices = mutableStateOf<List<NetworkDevice>>(emptyList())
    val devices: State<List<NetworkDevice>> = _devices
    
    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning
    
    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage
    
    private val _currentWifiInfo = mutableStateOf<String?>(null)
    val currentWifiInfo: State<String?> = _currentWifiInfo
    
    private var scanJob: Job? = null
    
    fun startScan() {
        if (_isScanning.value) {
            _errorMessage.value = "正在扫描中，请稍候..."
            return
        }
        
        // 检查WiFi连接状态
        if (!wifiManager.isWifiEnabled) {
            _errorMessage.value = "请先开启WiFi功能"
            return
        }
        
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo == null || wifiInfo.networkId == -1) {
            _errorMessage.value = "请先连接到WiFi网络"
            return
        }
        
        _currentWifiInfo.value = wifiInfo.ssid?.replace("\"", "")
        _errorMessage.value = null
        _isScanning.value = true
        _devices.value = emptyList()
        
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                scanNetworkDevices()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "扫描出错: ${e.message}"
                    _isScanning.value = false
                }
            }
        }
    }
    
    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    private suspend fun scanNetworkDevices() {
        val currentDeviceIP = getCurrentDeviceIP()
        val networkPrefix = getNetworkPrefix(currentDeviceIP)
        
        if (networkPrefix == null) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = "无法获取网络信息"
                _isScanning.value = false
            }
            return
        }
        
        val devices = mutableListOf<NetworkDevice>()
        val jobs = mutableListOf<Job>()
        
        // 扫描网络范围 (通常是 192.168.x.1-254)
        for (i in 1..254) {
            val ip = "$networkPrefix.$i"
            
            val job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val address = InetAddress.getByName(ip)
                    val startTime = System.currentTimeMillis()
                    val isReachable = address.isReachable(1000) // 1秒超时
                    val responseTime = System.currentTimeMillis() - startTime
                    
                    if (isReachable) {
                        val hostname = try {
                            address.canonicalHostName
                        } catch (e: Exception) {
                            null
                        }
                        
                        val macAddress = getMacAddress(ip)
                        val deviceType = determineDeviceType(hostname, macAddress)
                        val isCurrentDevice = ip == currentDeviceIP
                        
                        val device = NetworkDevice(
                            ipAddress = ip,
                            macAddress = macAddress,
                            hostname = hostname,
                            isReachable = true,
                            responseTime = responseTime,
                            deviceType = deviceType,
                            isCurrentDevice = isCurrentDevice
                        )
                        
                        synchronized(devices) {
                            devices.add(device)
                        }
                        
                        // 实时更新UI
                        withContext(Dispatchers.Main) {
                            _devices.value = devices.sortedWith(
                                compareByDescending<NetworkDevice> { it.isCurrentDevice }
                                    .thenBy { it.ipAddress }
                            )
                        }
                    }
                } catch (e: Exception) {
                    // 忽略单个IP的扫描错误
                }
            }
            jobs.add(job)
        }
        
        // 等待所有扫描完成
        jobs.joinAll()
        
        withContext(Dispatchers.Main) {
            _isScanning.value = false
            if (devices.isEmpty()) {
                _errorMessage.value = "未发现其他设备"
            }
        }
    }
    
    private fun getCurrentDeviceIP(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == false) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略错误
        }
        return null
    }
    
    private fun getNetworkPrefix(ip: String?): String? {
        if (ip == null) return null
        val parts = ip.split(".")
        return if (parts.size >= 3) {
            "${parts[0]}.${parts[1]}.${parts[2]}"
        } else null
    }
    
    private fun getMacAddress(ip: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 $ip")
            process.waitFor()
            
            val arpProcess = Runtime.getRuntime().exec("arp -n $ip")
            val reader = arpProcess.inputStream.bufferedReader()
            val output = reader.readText()
            arpProcess.waitFor()
            
            // 解析ARP表输出获取MAC地址
            val lines = output.split("\n")
            for (line in lines) {
                if (line.contains(ip)) {
                    val parts = line.split("\\s+".toRegex())
                    for (part in parts) {
                        if (part.matches("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})".toRegex())) {
                            return part
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun determineDeviceType(hostname: String?, macAddress: String?): String {
        val host = hostname?.lowercase() ?: ""
        val mac = macAddress?.uppercase() ?: ""
        
        return when {
            host.contains("router") || host.contains("gateway") -> "路由器"
            host.contains("phone") || host.contains("android") || host.contains("iphone") -> "手机"
            host.contains("laptop") || host.contains("pc") || host.contains("computer") -> "电脑"
            host.contains("tv") || host.contains("smart") -> "智能设备"
            host.contains("camera") || host.contains("cam") -> "摄像头"
            host.contains("printer") -> "打印机"
            mac.startsWith("00:50:56") || mac.startsWith("00:0C:29") -> "虚拟机"
            mac.startsWith("B8:27:EB") || mac.startsWith("DC:A6:32") -> "树莓派"
            else -> "未知设备"
        }
    }
}