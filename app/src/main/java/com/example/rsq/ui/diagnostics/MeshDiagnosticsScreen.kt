package com.example.rsq.ui.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.rsq.mesh.service.MeshForegroundService
import com.example.rsq.mesh.service.MeshServiceState
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.local.LocalReportDatabase
import com.example.rsq.util.NetworkConnectivityObserver
import com.example.rsq.util.ConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshDiagnosticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val serviceState by MeshForegroundService.serviceState.collectAsState()

    var pendingReportsCount by remember { mutableStateOf(0) }
    var networkStatus by remember { mutableStateOf("OFFLINE") }

    val bluetoothPerm = remember(context) { checkPermission(context, Manifest.permission.BLUETOOTH_CONNECT) }
    val locationPerm = remember(context) { checkPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) }
    val notificationPerm = remember(context) { checkPermission(context, Manifest.permission.POST_NOTIFICATIONS) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = LocalReportDatabase.getDatabase(context)
            val repo = LocalReportRepository(db.reportDao())
            pendingReportsCount = repo.getPendingReports().size
        }
        val observer = NetworkConnectivityObserver(context)
        observer.observe().collect { status ->
            networkStatus = if (status == ConnectivityObserver.Status.Available) "ONLINE" else "OFFLINE"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesh Diagnostics & System Status", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Core Mesh & Service Lifecycle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }

            item {
                DiagnosticCard(
                    title = "Mesh Foreground Service",
                    value = serviceState.name,
                    statusColor = when (serviceState) {
                        MeshServiceState.RUNNING -> Color(0xFF388E3C)
                        MeshServiceState.STARTING, MeshServiceState.RECOVERING -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    },
                    icon = Icons.Default.Router
                )
            }

            item {
                DiagnosticCard(
                    title = "Network Connectivity",
                    value = networkStatus,
                    statusColor = if (networkStatus == "ONLINE") Color(0xFF388E3C) else Color(0xFFD32F2F),
                    icon = if (networkStatus == "ONLINE") Icons.Default.Wifi else Icons.Default.WifiOff
                )
            }

            item {
                Text("Persistence & Sync Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }

            item {
                DiagnosticCard(
                    title = "Pending Offline Reports (Room DB)",
                    value = "$pendingReportsCount Pending",
                    statusColor = if (pendingReportsCount > 0) Color(0xFF1976D2) else Color(0xFF388E3C),
                    icon = Icons.Default.CloudSync
                )
            }

            item {
                Text("System Permissions Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }

            item {
                PermissionStatusCard("Bluetooth / Nearby Permission", bluetoothPerm)
            }

            item {
                PermissionStatusCard("Fine GPS Location Permission", locationPerm)
            }

            item {
                PermissionStatusCard("System Notification Permission", notificationPerm)
            }
        }
    }
}

@Composable
fun DiagnosticCard(
    title: String,
    value: String,
    statusColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = statusColor)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}

@Composable
fun PermissionStatusCard(title: String, granted: Boolean) {
    DiagnosticCard(
        title = title,
        value = if (granted) "GRANTED" else "DENIED",
        statusColor = if (granted) Color(0xFF388E3C) else Color(0xFFD32F2F),
        icon = if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel
    )
}

private fun checkPermission(context: Context, permission: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && permission == Manifest.permission.BLUETOOTH_CONNECT) {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && permission == Manifest.permission.POST_NOTIFICATIONS) {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    } else if (permission == Manifest.permission.ACCESS_FINE_LOCATION) {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
