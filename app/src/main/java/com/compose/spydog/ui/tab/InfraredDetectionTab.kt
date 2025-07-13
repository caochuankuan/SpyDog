package com.compose.spydog.ui.tab

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.compose.spydog.detector.InfraredDetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InfraredDetectionTab(detector: InfraredDetector) {
    var isCameraOpen by remember { mutableStateOf(false) }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var detectionMode by remember { mutableStateOf<String?>(null) } // "infrared" 或 "reflection"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera permission check
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    // 关闭手电筒
    fun turnOffFlashlight() {
        try {
            camera?.cameraControl?.enableTorch(false)
            isFlashlightOn = false
        } catch (e: Exception) {
            Log.e("Flashlight", "Error turning off flashlight: ${e.message}", e)
        }
    }

    // 释放相机资源
    fun releaseCamera() {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider.unbindAll()
            turnOffFlashlight()
            camera = null
            Log.d("Camera", "Camera resources released")
        } catch (e: Exception) {
            Log.e("Camera", "Error releasing camera: ${e.message}", e)
        }
    }

    // 使用CameraX控制手电筒
    fun toggleFlashlight() {
        try {
            camera?.let { cam ->
                val newState = !isFlashlightOn
                cam.cameraControl.enableTorch(newState)
                isFlashlightOn = newState
                Toast.makeText(
                    context,
                    if (newState) "手电筒已打开" else "手电筒已关闭",
                    Toast.LENGTH_SHORT
                ).show()
            } ?: run {
                Toast.makeText(context, "请先打开相机", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("Flashlight", "Error toggling flashlight: ${e.message}", e)
            Toast.makeText(context, "手电筒控制失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 当组件销毁时关闭手电筒和释放相机
    DisposableEffect(Unit) {
        onDispose {
            releaseCamera()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera preview area
        if (isCameraOpen && cameraPermissionState.status.isGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            this.scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        try {
                            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraPreview", "Use case binding failed", exc)
                        }
                    }
                )
            }
        } else if (isCameraOpen && !cameraPermissionState.status.isGranted) {
            // Permission not granted
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "需要相机权限",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "请授予相机权限以使用检测功能",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() }
                    ) {
                        Text("授予权限")
                    }
                }
            }
        } else {
            // Standby state
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "隐藏摄像头检测",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "选择检测模式开始扫描",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons row
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 红外检测按钮
            Button(
                onClick = {
                    if (cameraPermissionState.status.isGranted) {
                        if (detectionMode == "infrared" && isCameraOpen) {
                            // 停止红外检测
                            isCameraOpen = false
                            detectionMode = null
                            releaseCamera()
                        } else {
                            // 开始红外检测
                            detectionMode = "infrared"
                            isCameraOpen = true
                        }
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (detectionMode == "infrared" && isCameraOpen) 
                        MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (detectionMode == "infrared" && isCameraOpen) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (cameraPermissionState.status.isGranted) {
                        if (detectionMode == "infrared" && isCameraOpen) "停止检测" else "红外检测"
                    } else {
                        "红外检测"
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 反光检测按钮
            Button(
                onClick = {
                    if (cameraPermissionState.status.isGranted) {
                        if (detectionMode == "reflection" && isCameraOpen) {
                            // 停止反光检测
                            isCameraOpen = false
                            detectionMode = null
                            releaseCamera()
                        } else {
                            // 开始反光检测
                            detectionMode = "reflection"
                            if (!isCameraOpen) {
                                isCameraOpen = true
                                // 等待相机初始化后再开启手电筒
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    toggleFlashlight()
                                }, 1000)
                            } else {
                                toggleFlashlight()
                            }
                        }
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (detectionMode == "reflection" && isCameraOpen) 
                        MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                ),
                enabled = cameraPermissionState.status.isGranted
            ) {
                Icon(
                    imageVector = if (detectionMode == "reflection" && isCameraOpen) Icons.Default.Stop else Icons.Default.FlashOn,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (detectionMode == "reflection" && isCameraOpen) "停止检测" else "反光检测"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Usage instructions - 根据检测模式显示对应说明
        if (detectionMode != null) {
            Card {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (detectionMode) {
                        "infrared" -> {
                            Text(
                                text = "红外检测模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "检测原理: 利用摄像头感应器检测红外光谱，隐藏摄像头的红外补光灯会被识别",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "使用方法: 1.关闭房间所有灯光 2.缓慢移动手机扫描 3.观察屏幕上的异常亮点",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "检测重点: 烟雾报警器、路由器、机顶盒、充电器、装饰品等电子设备",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "注意事项: 在完全黑暗环境下效果最佳，移动要缓慢，注意红色光点",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.Red
                            )
                        }

                        "reflection" -> {
                            Text(
                                text = "反光检测模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "检测原理: 使用手电筒照射，摄像头镜头会产生强烈反光，形成明显光斑",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "使用方法: 1.关闭房间灯光 2.手电筒自动开启 3.缓慢扫描可疑区域 4.寻找强烈反光点",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "检测重点: 空调出风口、插座面板、相框、镜子、装饰品、电器缝隙",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "注意事项: 寻找异常强烈的反光点，正常物体反光较弱且分散",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.Red
                            )
                        }
                    }
                }
            }
        } else {
            // 默认说明
            Card {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                ) {
                    Text(
                        text = "隐藏摄像头检测说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "🔍 红外检测: 检测隐藏摄像头的红外补光灯，适用于夜视摄像头",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "💡 反光检测: 利用手电筒照射检测镜头反光，适用于所有类型摄像头",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "请选择检测模式开始扫描",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}