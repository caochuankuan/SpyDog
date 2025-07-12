package com.compose.spydog.detector

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import kotlin.math.pow

data class BluetoothInfo(
    val name: String,
    val address: String,
    val signalStrength: Int,
    val deviceType: String,
    val bondState: String,
    val deviceClass: String,
    val distance: String,
    val isConnected: Boolean = false,
    val isLowEnergy: Boolean = false
)

data class SuspiciousBluetoothDevice(
    val name: String,
    val address: String,
    val signalStrength: Int,
    val deviceType: String,
    val reason: String
)

class BluetoothScanner(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    private val _allBluetoothDevices = mutableStateOf<List<BluetoothInfo>>(emptyList())
    val allBluetoothDevices: State<List<BluetoothInfo>> = _allBluetoothDevices
    
    private val _suspiciousDevices = mutableStateOf<List<SuspiciousBluetoothDevice>>(emptyList())
    val suspiciousDevices: State<List<SuspiciousBluetoothDevice>> = _suspiciousDevices
    
    private val _isScanning = mutableStateOf(false)
    val isScanning: State<Boolean> = _isScanning
    
    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage
    
    private var isReceiverRegistered = false
    private var lastScanTime = 0L
    private val SCAN_THROTTLE_MS = 15000L // 15秒扫描间隔
    private val SCAN_TIMEOUT_MS = 12000L // 12秒扫描超时
    
    private val handler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null
    
    private val discoveredDevices = mutableSetOf<BluetoothDevice>()
    
    // 经典蓝牙扫描接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let {
                        discoveredDevices.add(it)
                        processBluetoothDevice(it, rssi, false)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanComplete()
                }
            }
        }
    }
    
    // BLE扫描回调
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { device ->
                discoveredDevices.add(device)
                processBluetoothDevice(device, result.rssi, true)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行中"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持BLE扫描"
                SCAN_FAILED_INTERNAL_ERROR -> "内部错误"
                else -> "BLE扫描失败: $errorCode"
            }
            scanFailure(errorMsg)
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
        
        // 检查蓝牙是否可用
        if (bluetoothAdapter == null) {
            _errorMessage.value = "设备不支持蓝牙功能"
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            _errorMessage.value = "请先开启蓝牙功能"
            return
        }
        
        // 检查权限
        if (!hasBluetoothPermissions()) {
            _errorMessage.value = "缺少蓝牙权限，请在设置中授予权限"
            return
        }
        
        try {
            _isScanning.value = true
            lastScanTime = currentTime
            discoveredDevices.clear()
            
            // 注册广播接收器
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                context.registerReceiver(bluetoothReceiver, filter)
                isReceiverRegistered = true
            }
            
            // 开始经典蓝牙扫描
            val classicScanStarted = bluetoothAdapter.startDiscovery()
            
            // 开始BLE扫描
            var bleScanStarted = false
            bluetoothLeScanner?.let { scanner ->
                try {
                    scanner.startScan(leScanCallback)
                    bleScanStarted = true
                } catch (e: Exception) {
                    // BLE扫描可能失败，但不影响经典蓝牙扫描
                }
            }
            
            if (!classicScanStarted && !bleScanStarted) {
                _isScanning.value = false
                scanFailure("扫描启动失败，可能是权限不足或系统限制")
                return
            }
            
            // 设置超时处理
            scanTimeoutRunnable = Runnable {
                if (_isScanning.value) {
                    stopScan()
                    scanComplete()
                }
            }
            handler.postDelayed(scanTimeoutRunnable!!, SCAN_TIMEOUT_MS)
            
        } catch (e: SecurityException) {
            _isScanning.value = false
            scanFailure("权限不足，请在设置中授予蓝牙权限")
        } catch (e: Exception) {
            _isScanning.value = false
            scanFailure("扫描出错: ${e.message}")
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        try {
            // 停止经典蓝牙扫描
            bluetoothAdapter?.cancelDiscovery()
            
            // 停止BLE扫描
            bluetoothLeScanner?.stopScan(leScanCallback)
            
            // 注销广播接收器
            if (isReceiverRegistered) {
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
            }
            
            // 取消超时任务
            scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
            
        } catch (e: Exception) {
            // 忽略停止扫描时的异常
        }
        _isScanning.value = false
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        // Android 12+ 需要额外权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        }
        
        return permissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun processBluetoothDevice(device: BluetoothDevice, rssi: Int, isLowEnergy: Boolean) {
        try {
            val deviceName = if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED) {
                device.name ?: "未知设备"
            } else {
                "未知设备"
            }
            
            val deviceType = getDeviceType(device)
            val bondState = getBondState(device)
            val deviceClass = getDeviceClass(device)
            val distance = calculateDistance(rssi)
            val isConnected = isDeviceConnected(device)
            
            val bluetoothInfo = BluetoothInfo(
                name = deviceName,
                address = device.address,
                signalStrength = rssi,
                deviceType = deviceType,
                bondState = bondState,
                deviceClass = deviceClass,
                distance = distance,
                isConnected = isConnected,
                isLowEnergy = isLowEnergy
            )
            
            // 更新设备列表
            val currentDevices = _allBluetoothDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                currentDevices[existingIndex] = bluetoothInfo
            } else {
                currentDevices.add(bluetoothInfo)
            }
            
            // 按连接状态和信号强度排序
            _allBluetoothDevices.value = currentDevices.sortedWith(
                compareByDescending<BluetoothInfo> { it.isConnected }
                    .thenByDescending { it.signalStrength }
            )
            
            // 检测可疑设备
            checkSuspiciousDevice(bluetoothInfo)
            
        } catch (e: Exception) {
            // 处理设备信息获取异常
        }
    }
    
    private fun checkSuspiciousDevice(device: BluetoothInfo) {
        val suspiciousReasons = mutableListOf<String>()
        
        // 检测可疑设备名称
        val suspiciousNames = listOf(
            "camera", "spy", "hidden", "cam", "surveillance", "monitor",
            "recorder", "bug", "listening", "mic", "audio"
        )
        if (suspiciousNames.any { device.name.lowercase().contains(it) }) {
            suspiciousReasons.add("可疑设备名称")
        }
        
        // 检测未知设备
        if (device.name == "未知设备" && device.bondState == "未配对") {
            suspiciousReasons.add("未知未配对设备")
        }
        
        // 检测信号强度异常（太强可能是近距离隐藏设备）
        if (device.signalStrength > -30) {
            suspiciousReasons.add("信号强度异常")
        }
        
        // 检测可疑设备类型
        if (device.deviceClass.contains("未知") || device.deviceClass.contains("其他")) {
            suspiciousReasons.add("设备类型可疑")
        }
        
        if (suspiciousReasons.isNotEmpty()) {
            val suspiciousDevice = SuspiciousBluetoothDevice(
                name = device.name,
                address = device.address,
                signalStrength = device.signalStrength,
                deviceType = device.deviceType,
                reason = suspiciousReasons.joinToString(", ")
            )
            
            val currentSuspicious = _suspiciousDevices.value.toMutableList()
            val existingIndex = currentSuspicious.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                currentSuspicious[existingIndex] = suspiciousDevice
            } else {
                currentSuspicious.add(suspiciousDevice)
            }
            _suspiciousDevices.value = currentSuspicious
        }
    }
    
    private fun scanComplete() {
        _isScanning.value = false
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
    }
    
    private fun scanFailure(message: String) {
        _errorMessage.value = message
        _isScanning.value = false
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun getDeviceType(device: BluetoothDevice): String {
        return try {
            when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "经典蓝牙"
                BluetoothDevice.DEVICE_TYPE_LE -> "低功耗蓝牙"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "双模蓝牙"
                else -> "未知类型"
            }
        } catch (e: Exception) {
            "未知类型"
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun getBondState(device: BluetoothDevice): String {
        return try {
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "已配对"
                BluetoothDevice.BOND_BONDING -> "配对中"
                BluetoothDevice.BOND_NONE -> "未配对"
                else -> "未知状态"
            }
        } catch (e: Exception) {
            "未知状态"
        }
    }
    
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun getDeviceClass(device: BluetoothDevice): String {
        return try {
            val deviceClass = device.bluetoothClass
            when (deviceClass?.majorDeviceClass) {
                0x0100 -> "计算机"
                0x0200 -> "手机"
                0x0300 -> "网络设备"
                0x0400 -> "音频/视频"
                0x0500 -> "外设"
                0x0600 -> "成像设备"
                0x0700 -> "可穿戴设备"
                0x0800 -> "玩具"
                0x0900 -> "健康设备"
                else -> "其他设备"
            }
        } catch (e: Exception) {
            "未知设备"
        }
    }
    
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.bondedDevices?.any { it.address == device.address } == true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun calculateDistance(rssi: Int): String {
        // 基于RSSI计算大概距离（简化公式）
        val distance = 10.0.pow((-69 - rssi) / 20.0)
        return when {
            distance < 1 -> "<1米"
            distance < 5 -> "${distance.toInt()}米"
            distance < 50 -> "${distance.toInt()}米"
            else -> ">50米"
        }
    }
}