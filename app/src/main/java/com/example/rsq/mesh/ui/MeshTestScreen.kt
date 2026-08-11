package com.example.rsq.mesh.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.rsq.mesh.viewmodel.MeshTestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTestScreen(
    viewModel: MeshTestViewModel,
    onNavigateBack: () -> Unit
) {
    val nodeId = viewModel.nodeId
    val peerCount by viewModel.connectedPeerCount.collectAsState()
    val lastMessage by viewModel.lastReceivedMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val context = LocalContext.current

    // Permissions handling
    val requiredPermissions = remember {
        val list = mutableListOf<String>()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+
                list.add(Manifest.permission.BLUETOOTH_SCAN)
                list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                list.add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+
                    list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10-11
                list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                // Android 6-9
                list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        list
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) {
            viewModel.startMesh()
        }
    }

    LaunchedEffect(Unit) {
        if (permissionsGranted) {
            viewModel.startMesh()
        } else {
            launcher.launch(requiredPermissions.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Mesh Test", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Info", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Node ID: $nodeId", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val displayStatus = if (permissionsGranted) statusMessage else "Nearby permissions required"
                    Text("Status: $displayStatus", fontWeight = FontWeight.SemiBold, color = if (permissionsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Connectivity", fontWeight = FontWeight.Bold)
                    Text(
                        text = peerCount.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (peerCount > 0) Color(0xFF388E3C) else Color.Gray
                    )
                    Text("Peers Connected", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = { viewModel.sendTestMessage() },
                modifier = Modifier.fillMaxWidth(),
                enabled = peerCount > 0
            ) {
                Text("Send Test SOS Message")
            }

            if (peerCount == 0) {
                Text("Connect to another device to send messages", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            HorizontalDivider()

            Text("Last Received Message", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))

            if (lastMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ID: ${lastMessage!!.id}", style = MaterialTheme.typography.labelSmall)
                        Text("From: ${lastMessage!!.senderNodeId}", fontWeight = FontWeight.Bold)
                        Text("Origin: ${lastMessage!!.originNodeId}")
                        Text("Type: ${lastMessage!!.messageType}")
                        Text("Priority: ${lastMessage!!.priority}")
                        Text("Payload: ${lastMessage!!.payload}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("TTL: ${lastMessage!!.ttl}")
                        Text("Loc: ${lastMessage!!.latitude}, ${lastMessage!!.longitude}")
                        Text("Time: ${java.util.Date(lastMessage!!.timestamp)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Text("No messages received yet", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
