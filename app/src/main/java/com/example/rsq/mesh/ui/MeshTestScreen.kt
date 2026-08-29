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
import com.example.rsq.mesh.model.MeshRelayEvent
import com.example.rsq.mesh.model.MeshTransportStatus
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
    val diagnostics by viewModel.diagnostics.collectAsState()
    val relayEvents by viewModel.relayEvents.collectAsState()

    // MeshTestScreen assumes permissions were granted at the app-level gate.
    LaunchedEffect(Unit) {
        viewModel.startMesh()
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

                    val displayStatus = statusMessage
                    val statusColor = when (diagnostics.status) {
                        MeshTransportStatus.READY -> Color(0xFF388E3C)
                        MeshTransportStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text("Status: $displayStatus", fontWeight = FontWeight.SemiBold, color = statusColor)
                }
            }

            // Mesh Diagnostics Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mesh Diagnostics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    DiagnosticRow("Transport", diagnostics.status.name)
                    DiagnosticRow("Advertising", if (diagnostics.isAdvertising) "RUNNING" else "STOPPED")
                    DiagnosticRow("Discovery", if (diagnostics.isDiscovering) "RUNNING" else "STOPPED")
                    DiagnosticRow("Connected Peers", diagnostics.connectedPeerCount.toString())

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

                    DiagnosticRow("Last Endpoint", diagnostics.lastDiscoveredEndpoint ?: "None")
                    DiagnosticRow("Last Event", diagnostics.lastConnectionEvent ?: "None")

                    if (diagnostics.lastError != null) {
                        Text("Error: ${diagnostics.lastError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
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

            HorizontalDivider()

            Text("Relay Trace (Latest 20)", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))

            if (relayEvents.isEmpty()) {
                Text("No relay events recorded", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            } else {
                relayEvents.forEach { event ->
                    RelayEventItem(event)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RelayEventItem(event: MeshRelayEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (event.action) {
                "RECEIVED" -> Color(0xFFE3F2FD)
                "RELAYED" -> Color(0xFFE8F5E9)
                "DUPLICATE_DISCARDED" -> Color(0xFFFFF3E0)
                "OUTBOUND" -> Color(0xFFF3E5F5)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.action,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (event.action) {
                        "RECEIVED" -> Color(0xFF1976D2)
                        "RELAYED" -> Color(0xFF388E3C)
                        "DUPLICATE_DISCARDED" -> Color(0xFFF57C00)
                        "OUTBOUND" -> Color(0xFF7B1FA2)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Msg ID: ${event.messageId}", style = MaterialTheme.typography.bodySmall)
            Text("From: ${event.senderNodeId} | Origin: ${event.originNodeId}", style = MaterialTheme.typography.bodySmall)

            val ttlText = if (event.ttlAfter != null) "${event.ttlBefore} -> ${event.ttlAfter}" else "${event.ttlBefore}"
            Text("TTL: $ttlText", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
