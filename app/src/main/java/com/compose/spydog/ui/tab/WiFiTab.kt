package com.compose.spydog.ui.tab

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compose.spydog.detector.SuspiciousDevice
import com.compose.spydog.detector.WiFiInfo
import com.compose.spydog.detector.WiFiScanner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPermissionsApi::class)

@Composable
fun WiFiTab(
    scanner: WiFiScanner,
    onConnectedWiFiClick: (String) -> Unit = {},
    onAIResultReady: (String) -> Unit = {}
) {
    val allWiFiNetworks by scanner.allWiFiNetworks
    val suspiciousDevices by scanner.suspiciousDevices
    val isScanning by scanner.isScanning
    val errorMessage by scanner.errorMessage
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showSuspiciousDevices by remember { mutableStateOf(false) }
    
    // AI查询相关状态
    var isAIAnalyzing by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var showAIDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // DeepSeek API调用
    suspend fun queryDeepSeekAPI(query: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                
                val json = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "你是一个网络安全专家，专门分析WiFi设备是否可能是隐藏摄像头。请根据设备名称、MAC地址等信息进行分析。回答不要md格式，普通文本格式。")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", query)
                        })
                    })
                    put("stream", false)
                }
                
                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("https://api.deepseek.com/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer sk-fd3795e918014b3dab12406a6cdce972")
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        message.getString("content")
                    } else {
                        "API返回了空结果"
                    }
                } else {
                    "API请求失败: ${response.code} - ${responseBody ?: "未知错误"}"
                }
            } catch (e: Exception) {
                "请求出错: ${e.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    scanner.clearError()
                    scanner.startScan()
                },
                modifier = Modifier.weight(1f),
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
                    Text("扫描WiFi")
                }
            }

            Button(
                onClick = {
                    if (showSuspiciousDevices) {
                        // 粗略分析模式：复制可疑设备信息
                        if (suspiciousDevices.isNotEmpty()) {
                            val suspiciousText = formatSuspiciousDevicesForClipboard(suspiciousDevices)
                            clipboardManager.setText(AnnotatedString(suspiciousText))
                            Toast.makeText(context, "可疑设备信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "没有可疑设备信息可复制", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // 正常模式：复制所有WiFi信息
                        if (allWiFiNetworks.isNotEmpty()) {
                            val wifiText = formatWiFiListForClipboard(allWiFiNetworks)
                            clipboardManager.setText(AnnotatedString(wifiText))
                            Toast.makeText(context, "WiFi信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "没有WiFi信息可复制", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(0.8f),
                enabled = if (showSuspiciousDevices) suspiciousDevices.isNotEmpty() else allWiFiNetworks.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("复制")
            }

            Button(
                onClick = {
                    showSuspiciousDevices = !showSuspiciousDevices
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showSuspiciousDevices) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = if (showSuspiciousDevices) "返回全部" else "粗略分析",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showSuspiciousDevices) "返回全部" else "粗略分析")
            }
        }

        // AI分析按钮
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                // 构建WiFi信息查询字符串
                val wifiInfoQuery = if (showSuspiciousDevices && suspiciousDevices.isNotEmpty()) {
                    val deviceInfo = suspiciousDevices.joinToString("; ") { device ->
                        "设备名:${device.ssid}, MAC:${device.bssid}, 信号:${device.signalStrength}dBm, 原因:${device.reason}"
                    }
                    "请分析这些可疑WiFi设备是否可能是隐藏摄像头(先给结论，再分析): $deviceInfo"
                } else if (allWiFiNetworks.isNotEmpty()) {
                    val wifiInfo = allWiFiNetworks.joinToString("; ") { wifi ->
                        "${wifi.ssid}(${wifi.bssid}, ${wifi.signalStrength}dBm, ${wifi.securityType})"
                    }
                    "请分析这些WiFi设备名称是否可能是隐藏摄像头(先给结论，再分析): $wifiInfo"
                } else {
                    "如何识别隐藏摄像头的WiFi设备名称特征"
                }
                
                // 调用DeepSeek API
                isAIAnalyzing = true
                coroutineScope.launch {
                    try {
                        val result = queryDeepSeekAPI(wifiInfoQuery)
                        onAIResultReady(result)
                    } catch (e: Exception) {
                        onAIResultReady("分析失败: ${e.message}")
                    } finally {
                        isAIAnalyzing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAIAnalyzing && (allWiFiNetworks.isNotEmpty() || suspiciousDevices.isNotEmpty()),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            if (isAIAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI分析中...")
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "AI查询",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI分析隐藏摄像头")
            }
        }

        // 显示错误信息（包括扫描间隔提示）
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (error.contains("等待")) {
                        Color.Blue.copy(alpha = 0.1f) // 扫描间隔用蓝色
                    } else {
                        Color.Red.copy(alpha = 0.1f) // 其他错误用红色
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (error.contains("等待")) {
                            Icons.Default.Schedule
                        } else {
                            Icons.Default.Warning
                        },
                        contentDescription = if (error.contains("等待")) "等待" else "错误",
                        tint = if (error.contains("等待")) Color.Blue else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = if (error.contains("等待")) Color.Blue else Color.Red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showSuspiciousDevices) {
            // 显示可疑设备
            if (suspiciousDevices.isNotEmpty()) {
                Text(
                    text = "发现 ${suspiciousDevices.size} 个可疑设备:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {
                    items(suspiciousDevices) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Red.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = device.ssid,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "MAC: ${device.bssid}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "信号强度: ${device.signalStrength} dBm",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "可疑原因: ${device.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "未发现可疑设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Green
                )
            }
        } else {
            // 显示所有WiFi网络
            if (allWiFiNetworks.isNotEmpty()) {
                Text(
                    text = "发现 ${allWiFiNetworks.size} 个WiFi网络:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn {
                    items(allWiFiNetworks) { wifi ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .then(
                                    if (wifi.isConnected) {
                                        Modifier
                                            .border(
                                                2.dp,
                                                Color.Blue,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                onConnectedWiFiClick(wifi.ssid)
                                            }
                                    } else Modifier
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    wifi.isConnected -> Color.Blue.copy(alpha = 0.1f)
                                    !wifi.isSecure -> Color.Yellow.copy(alpha = 0.1f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // 第一行：WiFi名称和连接状态
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = wifi.ssid,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (wifi.isConnected) Color.Blue else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (wifi.isConnected) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "当前连接",
                                                tint = Color.Blue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "已连接",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Blue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Row {
                                        if (!wifi.isSecure) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "不安全",
                                                tint = Color.Yellow,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "安全",
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = wifi.securityType,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 第二行：距离信息（突出显示）
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "距离",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "距离: ${wifi.distance}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "信号: ${wifi.level}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 第三行：信号强度详细信息
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${wifi.signalStrength} dBm",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "频段: ${wifi.bandwidth}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                // 第四行：频率和信道
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "频率: ${wifi.frequency} MHz",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "信道: ${wifi.channel}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                // 第五行：MAC地址
                                Text(
                                    text = "MAC: ${wifi.bssid}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (!isScanning) {
                Text(
                    text = "点击扫描WiFi开始检测",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.stopScan()
        }
    }
}

// 格式化WiFi列表为文本
fun formatWiFiListForClipboard(wifiList: List<WiFiInfo>): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append("=== WiFi扫描结果 ===\n")
    stringBuilder.append("扫描时间: ${
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(
            Date()
        )}\n")
    stringBuilder.append("发现网络数量: ${wifiList.size}\n\n")

    wifiList.forEachIndexed { index, wifi ->
        stringBuilder.append("${index + 1}. ${wifi.ssid}")
        if (wifi.isConnected) {
            stringBuilder.append(" [已连接]")
        }
        stringBuilder.append("\n")
        stringBuilder.append("   MAC地址: ${wifi.bssid}\n")
        stringBuilder.append("   安全类型: ${wifi.securityType}")
        if (!wifi.isSecure) {
            stringBuilder.append(" [不安全]")
        }
        stringBuilder.append("\n")
        stringBuilder.append("   信号强度: ${wifi.signalStrength} dBm (${wifi.level})\n")
        stringBuilder.append("   距离: ${wifi.distance}\n")
        stringBuilder.append("   频段: ${wifi.bandwidth}\n")
        stringBuilder.append("   信道: ${wifi.channel}\n")
        stringBuilder.append("   频率: ${wifi.frequency} MHz\n")
        stringBuilder.append("\n")
    }

    stringBuilder.append("=== 扫描完成 ===")
    return stringBuilder.toString()
}

// 格式化可疑设备列表为文本
fun formatSuspiciousDevicesForClipboard(suspiciousDevices: List<SuspiciousDevice>): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append("=== 可疑设备扫描结果 ===\n")
    stringBuilder.append("扫描时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
    stringBuilder.append("发现可疑设备数量: ${suspiciousDevices.size}\n\n")

    suspiciousDevices.forEachIndexed { index, device ->
        stringBuilder.append("${index + 1}. ${device.ssid}\n")
        stringBuilder.append("   MAC地址: ${device.bssid}\n")
        stringBuilder.append("   信号强度: ${device.signalStrength} dBm\n")
        stringBuilder.append("   频率: ${device.frequency} MHz\n")
        stringBuilder.append("   可疑原因: ${device.reason}\n")
        stringBuilder.append("\n")
    }

    stringBuilder.append("=== 扫描完成 ===")
    return stringBuilder.toString()
}