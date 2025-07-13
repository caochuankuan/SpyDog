package com.compose.spydog.ui.tab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compose.spydog.detector.BluetoothScanner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
// 添加AI分析需要的导入
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
fun BluetoothDetectionTab(scanner: BluetoothScanner, onAIResultReady: (String) -> Unit = {}) {
    AllBluetoothTab(scanner, onAIResultReady)
}

@Composable
fun AllBluetoothTab(scanner: BluetoothScanner, onAIResultReady: (String) -> Unit = {}) {
    val allDevices by scanner.allBluetoothDevices
    val suspiciousDevices by scanner.suspiciousDevices
    val isScanning by scanner.isScanning
    val errorMessage by scanner.errorMessage
    val context = LocalContext.current

    var showSuspiciousDevices by remember { mutableStateOf(false) }
    
    // AI查询相关状态
    var isAIAnalyzing by remember { mutableStateOf(false) }
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
                            put("content", "你是一个网络安全专家，专门分析蓝牙设备是否可能是隐藏摄像头或监控设备。请根据设备名称、MAC地址、设备类型等信息进行分析。回答不要md格式，普通文本格式。")
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
        // 错误信息显示
        errorMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.contains("等待")) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (message.contains("等待")) {
                            Icons.Default.Schedule
                        } else {
                            Icons.Default.Warning
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

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
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("扫描中...")
                } else {
                    Text("扫描蓝牙")
                }
            }

            Button(
                onClick = {
                    if (showSuspiciousDevices) {
                        // 粗略分析模式：复制可疑设备信息
                        if (suspiciousDevices.isNotEmpty()) {
                            val deviceList = suspiciousDevices.joinToString("\n") { device ->
                                "设备: ${device.name}\n" +
                                        "地址: ${device.address}\n" +
                                        "信号强度: ${device.signalStrength}dBm\n" +
                                        "设备类型: ${device.deviceType}\n" +
                                        "可疑原因: ${device.reason}\n"
                            }

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("可疑蓝牙设备", "可疑蓝牙设备列表:\n\n$deviceList")
                            clipboard.setPrimaryClip(clip)

                            Toast.makeText(context, "可疑设备信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "没有可疑设备信息可复制", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // 正常模式：复制所有蓝牙设备信息
                        if (allDevices.isNotEmpty()) {
                            val deviceList = allDevices.joinToString("\n") { device ->
                                "设备: ${device.name}\n" +
                                        "地址: ${device.address}\n" +
                                        "信号强度: ${device.signalStrength}dBm\n" +
                                        "距离: ${device.distance}\n" +
                                        "设备类型: ${device.deviceType}\n" +
                                        "配对状态: ${device.bondState}\n" +
                                        "设备类别: ${device.deviceClass}\n" +
                                        "连接状态: ${if (device.isConnected) "已连接" else "未连接"}\n" +
                                        "蓝牙类型: ${if (device.isLowEnergy) "低功耗蓝牙" else "经典蓝牙"}\n"
                            }

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("蓝牙设备列表", "蓝牙设备列表:\n\n$deviceList")
                            clipboard.setPrimaryClip(clip)

                            Toast.makeText(context, "蓝牙设备信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "没有蓝牙设备信息可复制", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(0.8f),
                enabled = if (showSuspiciousDevices) suspiciousDevices.isNotEmpty() else allDevices.isNotEmpty()
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
                // 构建蓝牙设备信息查询字符串
                val bluetoothInfoQuery = if (showSuspiciousDevices && suspiciousDevices.isNotEmpty()) {
                    val deviceInfo = suspiciousDevices.joinToString("; ") { device ->
                        "设备名:${device.name}, MAC:${device.address}, 信号:${device.signalStrength}dBm, 类型:${device.deviceType}, 原因:${device.reason}"
                    }
                    "请分析这些可疑蓝牙设备是否可能是隐藏摄像头或监控设备(先给结论，再分析): $deviceInfo"
                } else if (allDevices.isNotEmpty()) {
                    val deviceInfo = allDevices.joinToString("; ") { device ->
                        "${device.name}(${device.address}, ${device.signalStrength}dBm, ${device.deviceType})"
                    }
                    "请分析这些蓝牙设备是否可能是隐藏摄像头或监控设备(先给结论，再分析): $deviceInfo"
                } else {
                    "如何识别隐藏摄像头的蓝牙设备特征"
                }
                
                // 调用DeepSeek API
                isAIAnalyzing = true
                coroutineScope.launch {
                    try {
                        val result = queryDeepSeekAPI(bluetoothInfoQuery)
                        onAIResultReady(result)
                    } catch (e: Exception) {
                        onAIResultReady("分析失败: ${e.message}")
                    } finally {
                        isAIAnalyzing = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAIAnalyzing && (allDevices.isNotEmpty() || suspiciousDevices.isNotEmpty()),
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

        Spacer(modifier = Modifier.height(16.dp))

        if (showSuspiciousDevices) {
            // 显示可疑设备
            if (suspiciousDevices.isNotEmpty()) {
                Text(
                    text = "发现 ${suspiciousDevices.size} 个可疑蓝牙设备:",
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
                                    text = device.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "地址: ${device.address}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "信号强度: ${device.signalStrength}dBm",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "设备类型: ${device.deviceType}",
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
                    text = "未发现可疑蓝牙设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Green
                )
            }
        } else {
            // 显示所有蓝牙设备
            Text(
                text = "所有蓝牙设备 (${allDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (allDevices.isNotEmpty()) {
                LazyColumn {
                    items(allDevices) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .then(
                                    if (device.isConnected) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(8.dp)
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    device.isConnected -> Color.Blue.copy(alpha = 0.1f)
                                    device.bondState == "未配对" -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // 第一行：设备名称和连接状态
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (device.isConnected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (device.isConnected) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "已连接",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "已连接",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // 第二行：距离信息（突出显示）
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "距离",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "距离: ${device.distance}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // 其他信息
                                Text(
                                    text = "地址: ${device.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "信号强度: ${device.signalStrength}dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "设备类型: ${device.deviceType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "配对状态: ${device.bondState}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "设备类别: ${device.deviceClass}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "蓝牙类型: ${if (device.isLowEnergy) "低功耗蓝牙" else "经典蓝牙"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "暂未发现蓝牙设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}