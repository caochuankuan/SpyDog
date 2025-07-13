package com.compose.spydog

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.compose.spydog.ui.theme.SpyDogTheme
import com.compose.spydog.ui.pages.DetectionScreen
import com.compose.spydog.ui.pages.AIResultScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpyDogTheme {
                val permissionsState = rememberMultiplePermissionsState(
                    permissions = listOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.INTERNET
                    )
                )
                
                LaunchedEffect(Unit) {
                    permissionsState.launchMultiplePermissionRequest()
                }
                
                if (permissionsState.allPermissionsGranted) {
                    SpyDogNavigation()
                } else {
                    PermissionRequestScreen {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                }
            }
        }
    }
}

@Composable
fun SpyDogNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "detection"
    ) {
        composable("detection") {
            DetectionScreen(navController = navController)
        }
        composable("ai_result/{result}") { backStackEntry ->
            val result = backStackEntry.arguments?.getString("result") ?: ""
            AIResultScreen(
                result = result,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SpyDog需要以下权限来检测隐藏摄像头：",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("• 摄像头权限 - 用于红外检测")
        Text("• 位置权限 - 用于WiFi扫描")
        Text("• WiFi权限 - 检测可疑设备")
        Text("• 蓝牙权限 - 检测蓝牙设备")
        Text("• 网络权限 - 用于AI分析")
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("授予权限")
        }
    }
}