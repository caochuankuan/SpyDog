package com.compose.spydog.ui.pages

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.compose.spydog.detector.BluetoothScanner
import com.compose.spydog.detector.InfraredDetector
import com.compose.spydog.detector.MagneticFieldDetector
import com.compose.spydog.detector.WiFiScanner
import com.compose.spydog.ui.tab.BluetoothDetectionTab
import com.compose.spydog.ui.tab.InfraredDetectionTab
import com.compose.spydog.ui.tab.MagneticDetectionTab
import com.compose.spydog.ui.tab.WiFiTab
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(navController: NavHostController) {
    val context = LocalContext.current
    val infraredDetector = remember { InfraredDetector() }
    val magneticFieldDetector = remember { MagneticFieldDetector(context) }
    val wifiScanner = remember { WiFiScanner(context) }
    val bluetoothScanner = remember { BluetoothScanner(context) }
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showNetworkDevicesScreen by remember { mutableStateOf(false) }
    var selectedWiFiSSID by remember { mutableStateOf("") }
    
    DisposableEffect(Unit) {
        onDispose {
            magneticFieldDetector.stopDetection()
            wifiScanner.stopScan()
            bluetoothScanner.stopScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpyDog - 隐藏摄像头检测") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (showNetworkDevicesScreen) {
            // 显示网络设备扫描页面
            NetworkDevicesScreen(
                wifiSSID = selectedWiFiSSID,
                onBackClick = {
                    showNetworkDevicesScreen = false
                }
            )
        } else {
            // 显示主检测页面
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 标签页
                val mainTabs = listOf("WiFi检测", "蓝牙检测", "磁场检测", "红外检测")

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    mainTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 标签页内容
                when (selectedTabIndex) {
                    0 -> WiFiTab(
                        scanner = wifiScanner,
                        onConnectedWiFiClick = { ssid ->
                            selectedWiFiSSID = ssid
                            showNetworkDevicesScreen = true
                        },
                        onAIResultReady = { result ->
                            // 导航到AI结果页面
                            val encodedResult = URLEncoder.encode(result, StandardCharsets.UTF_8.toString())
                            navController.navigate("ai_result/$encodedResult")
                        }
                    )
                    1 -> BluetoothDetectionTab(bluetoothScanner)
                    2 -> MagneticDetectionTab(magneticFieldDetector)
                    3 -> InfraredDetectionTab(infraredDetector)
                }
            }
        }
    }
}

@Composable
fun WiFiScanTab(
    scanner: WiFiScanner,
    onConnectedWiFiClick: (String) -> Unit = {}
) {
    WiFiTab(
        scanner = scanner,
        onConnectedWiFiClick = onConnectedWiFiClick
    )
}
