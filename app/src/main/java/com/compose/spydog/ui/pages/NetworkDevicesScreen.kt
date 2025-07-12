package com.compose.spydog.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compose.spydog.detector.NetworkDevice
import com.compose.spydog.detector.NetworkDeviceScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDevicesScreen(
    wifiSSID: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scanner = remember { NetworkDeviceScanner(context) }
    val devices by scanner.devices
    val isScanning by scanner.isScanning
    val errorMessage by scanner.errorMessage
    val currentWifiInfo by scanner.currentWifiInfo
    
    DisposableEffect(Unit) {
        onDispose {
            scanner.stopScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 顶部导航栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "网络设备扫描",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "WiFi: ${currentWifiInfo ?: wifiSSID}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 扫描按钮
        Button(
            onClick = {
                scanner.clearError()
                scanner.startScan()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isScanning
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("扫描中...")
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "扫描",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("扫描网络设备")
            }
        }

        // 错误信息
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "错误",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 设备列表
        if (devices.isNotEmpty()) {
            Text(
                text = "发现 ${devices.size} 个设备:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(devices) { device ->
                    DeviceCard(device = device)
                }
            }
        } else if (!isScanning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DeviceHub,
                        contentDescription = "无设备",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击扫描按钮开始搜索网络设备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: NetworkDevice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                device.isCurrentDevice -> Color.Blue.copy(alpha = 0.1f)
                device.deviceType.contains("摄像头") -> Color.Red.copy(alpha = 0.1f)
                device.deviceType.contains("未知") -> Color.Yellow.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 第一行：设备类型和IP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (device.deviceType) {
                            "路由器" -> Icons.Default.Router
                            "手机" -> Icons.Default.PhoneAndroid
                            "电脑" -> Icons.Default.Computer
                            "摄像头" -> Icons.Default.Videocam
                            "打印机" -> Icons.Default.Print
                            "智能设备" -> Icons.Default.Tv
                            else -> Icons.Default.DeviceUnknown
                        },
                        contentDescription = device.deviceType,
                        modifier = Modifier.size(20.dp),
                        tint = when {
                            device.isCurrentDevice -> Color.Blue
                            device.deviceType.contains("摄像头") -> Color.Red
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.deviceType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            device.isCurrentDevice -> Color.Blue
                            device.deviceType.contains("摄像头") -> Color.Red
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (device.isCurrentDevice) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(本机)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Blue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${device.responseTime}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 第二行：IP地址
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "IP地址",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "IP: ${device.ipAddress}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // 第三行：MAC地址（如果有）
            device.macAddress?.let { mac ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "MAC地址",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "MAC: $mac",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 第四行：主机名（如果有）
            device.hostname?.let { hostname ->
                if (hostname != device.ipAddress) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = "主机名",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "主机名: $hostname",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}