package com.example.rsq.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val requiredPermissions = remember { getRequiredPermissions() }
    
    var permissionsState by remember {
        mutableStateOf(checkPermissions(context, requiredPermissions))
    }

    // Authoritative check and transition
    fun updateAndCheck() {
        val newState = checkPermissions(context, requiredPermissions)
        permissionsState = newState
        if (newState.allGranted) {
            onPermissionsGranted()
        }
    }

    // Handle return from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateAndCheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Always re-check from system status, ignore the result map
        updateAndCheck()
    }

    // Initial check on launch
    LaunchedEffect(Unit) {
        updateAndCheck()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "RSQ",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )
            
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = "To provide life-saving offline connectivity and location reporting, RSQ requires the following permissions:",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            if (permissionsState.hasLocationRequired) {
                PermissionItem(
                    icon = Icons.Default.LocationOn,
                    title = "Location",
                    description = "Required for emergency tracking and peer discovery.",
                    isGranted = permissionsState.hasLocation
                )
            }

            if (permissionsState.hasBluetoothRequired) {
                PermissionItem(
                    icon = Icons.Default.Bluetooth,
                    title = "Nearby Devices",
                    description = "Required for offline mesh networking without internet.",
                    isGranted = permissionsState.hasBluetooth
                )
            }

            if (permissionsState.hasNotificationRequired) {
                PermissionItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    description = "Required to receive alerts and mission updates.",
                    isGranted = permissionsState.hasNotification
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            if (!permissionsState.allGranted) {
                Button(
                    onClick = { launcher.launch(requiredPermissions.toTypedArray()) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Grant Permissions", fontWeight = FontWeight.Bold)
                }
                
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open App Settings")
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
            if (isGranted) {
                Text("Granted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private data class PermissionsState(
    val hasLocation: Boolean,
    val hasBluetooth: Boolean,
    val hasNotification: Boolean,
    val hasLocationRequired: Boolean,
    val hasBluetoothRequired: Boolean,
    val hasNotificationRequired: Boolean,
    val allGranted: Boolean
)

private fun checkPermissions(context: android.content.Context, list: List<String>): PermissionsState {
    val location = list.filter { it.contains("LOCATION") }.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val bluetooth = list.filter { it.contains("BLUETOOTH") || it.contains("WIFI") }.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    
    val all = list.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    
    return PermissionsState(
        hasLocation = location,
        hasBluetooth = bluetooth,
        hasNotification = notification,
        hasLocationRequired = list.any { it.contains("LOCATION") },
        hasBluetoothRequired = list.any { it.contains("BLUETOOTH") || it.contains("WIFI") },
        hasNotificationRequired = list.any { it.contains("POST_NOTIFICATIONS") },
        allGranted = all
    )
}

private fun getRequiredPermissions(): List<String> {
    val list = mutableListOf<String>()
    
    // Location
    list.add(Manifest.permission.ACCESS_FINE_LOCATION)
    list.add(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Bluetooth / Nearby
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list.add(Manifest.permission.BLUETOOTH_SCAN)
        list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        list.add(Manifest.permission.BLUETOOTH)
        list.add(Manifest.permission.BLUETOOTH_ADMIN)
    }

    // Nearby Wifi (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    // Notifications
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    return list
}
