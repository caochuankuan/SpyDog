package com.compose.spydog.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compose.spydog.detector.MagneticFieldDetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi

@OptIn(ExperimentalPermissionsApi::class)


@Composable
fun MagneticDetectionTab(detector: MagneticFieldDetector) {
    val magneticStrength by detector.magneticStrength
    val isAnomalyDetected by detector.isAnomalyDetected
    var isDetecting by remember { mutableStateOf(false) }

    LaunchedEffect(isDetecting) {
        if (isDetecting) {
            detector.startDetection()
        } else {
            detector.stopDetection()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 检测状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAnomalyDetected)
                    Color.Red.copy(alpha = 0.1f) else Color.Blue.copy(alpha = 0.1f)
            ),
        ) {
            Row {
                Spacer(modifier = Modifier.weight(1f))
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isAnomalyDetected) Icons.Default.Warning else Icons.Default.Sensors,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = if (isAnomalyDetected) Color.Red else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isAnomalyDetected) "检测到磁场异常!" else "磁场环境正常",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isAnomalyDetected) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "磁场强度: ${magneticStrength.toInt()} μT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAnomalyDetected) {
                        Text(
                            text = "可能存在隐藏的电子设备",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 控制按钮
        Button(
            onClick = {
                isDetecting = !isDetecting
                if (isDetecting) {
                    detector.startDetection()
                } else {
                    detector.stopDetection()
                    detector.resetDetection()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isDetecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isDetecting) "停止检测" else "开始检测")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 使用说明
        Card {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "磁场检测说明:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "检测原理:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text("• 利用手机磁力传感器检测环境磁场变化")
                Text("• 隐藏摄像头等电子设备会产生磁场干扰")
                Text("• 正常环境磁场强度通常在25-65μT之间")

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "使用方法:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text("1. 点击'开始检测'按钮")
                Text("2. 缓慢移动手机靠近可疑区域")
                Text("3. 观察磁场强度数值变化")
                Text("4. 注意红色异常警告提示")

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "检测重点:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text("• 电视机、空调出风口")
                Text("• 烟雾报警器、插座面板")
                Text("• 装饰品、相框背后")
                Text("• 天花板角落、灯具附近")

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "注意事项:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.Red
                )
                Text(
                    text = "• 远离大型电器和金属物品",
                    color = Color.Red
                )
                Text(
                    text = "• 移除手机保护壳以提高精度",
                    color = Color.Red
                )
                Text(
                    text = "• 检测时保持手机水平移动",
                    color = Color.Red
                )
            }
        }
    }
}