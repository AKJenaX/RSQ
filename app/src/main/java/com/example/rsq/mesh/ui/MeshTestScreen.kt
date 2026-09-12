package com.example.rsq.mesh.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.rsq.mesh.model.MediaTransferUiState
import com.example.rsq.mesh.model.MeshRelayEvent
import com.example.rsq.mesh.model.MeshTransportStatus
import com.example.rsq.mesh.viewmodel.MeshTestViewModel
import com.example.rsq.util.BluetoothStateHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val mediaTransfers by viewModel.activeMediaTransfers.collectAsState()

    var previewImagePath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (BluetoothStateHelper.isBluetoothEnabled(context)) {
            viewModel.startMesh()
        }
    }

    // MeshTestScreen assumes permissions were granted at the app-level gate.
    LaunchedEffect(Unit) {
        viewModel.startMesh()
    }

    if (previewImagePath != null) {
        Dialog(onDismissRequest = { previewImagePath = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box {
                    AsyncImage(
                        model = File(previewImagePath!!),
                        contentDescription = "Full Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { previewImagePath = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
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
                colors = CardDefaults.cardColors(
                    containerColor = if (diagnostics.isMockMode)
                        Color(0xFFFFF8E1) // Light yellow tint for mock test mode
                    else
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mesh Diagnostics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        if (diagnostics.isMockMode) {
                            Surface(
                                color = Color(0xFFF57F17),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "EMULATOR TEST MODE",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    DiagnosticRow("Transport Mode", if (diagnostics.isMockMode) "MOCK (SIMULATED)" else "REAL (NEARBY)")
                    DiagnosticRow("Status", diagnostics.status.name)
                    DiagnosticRow("Advertising", if (diagnostics.isAdvertising) "RUNNING" else "STOPPED")
                    DiagnosticRow("Discovery", if (diagnostics.isDiscovering) "RUNNING" else "STOPPED")
                    DiagnosticRow("Connected Peers", diagnostics.connectedPeerCount.toString())

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

                    DiagnosticRow("Last Endpoint", diagnostics.lastDiscoveredEndpoint ?: "None")
                    DiagnosticRow("Last Event", diagnostics.lastConnectionEvent ?: "None")
                    DiagnosticRow("Active Sessions", diagnostics.activeNodeSessionsSummary)
                    DiagnosticRow("Active Payloads", diagnostics.activeFilePayloadsSummary)

                    if (!BluetoothStateHelper.isBluetoothEnabled(context) || diagnostics.lastError == "Bluetooth is turned off") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bluetooth is Turned Off",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = {
                                        bluetoothLauncher.launch(BluetoothStateHelper.createEnableBluetoothIntent())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Turn On", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (diagnostics.lastError != null) {
                        Text("Error: ${diagnostics.lastError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Media Transfer Diagnostic Section
            if (mediaTransfers.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Media Transfer", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))

                        mediaTransfers.forEachIndexed { index, media ->
                            MediaTransferCard(
                                index = index + 1,
                                total = mediaTransfers.size,
                                media = media,
                                onImageClick = { path -> previewImagePath = path }
                            )
                        }
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
                        Text("Media Items: ${lastMessage!!.mediaItems.size}")
                        Text("Loc: ${lastMessage!!.latitude}, ${lastMessage!!.longitude}")
                        Text("Time: ${Date(lastMessage!!.timestamp)}", style = MaterialTheme.typography.labelSmall)
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
private fun MediaTransferCard(
    index: Int,
    total: Int,
    media: MediaTransferUiState,
    onImageClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Image $index of $total", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    color = when (media.status) {
                        "PERSISTED", "CHECKSUM VERIFIED" -> Color(0xFFE8F5E9)
                        "FAILED" -> Color(0xFFFFEBEE)
                        else -> Color(0xFFFFF3E0)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = media.status,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = when (media.status) {
                            "PERSISTED", "CHECKSUM VERIFIED" -> Color(0xFF2E7D32)
                            "FAILED" -> Color(0xFFC62828)
                            else -> Color(0xFFEF6C00)
                        }
                    )
                }
            }

            DiagnosticRow("Report ID", media.reportId)
            DiagnosticRow("Filename", media.filename)
            DiagnosticRow("MIME Type", media.mimeType)

            val expectedKb = media.expectedSizeBytes / 1024
            val actualKbText = when {
                media.status == "FAILED" || media.failureReason != null -> "ERROR"
                media.actualSizeBytes != null && media.actualSizeBytes >= 0 -> "${media.actualSizeBytes / 1024} KB"
                else -> "${media.bytesTransferred / 1024} KB (Transferring)"
            }
            DiagnosticRow("Size (KB)", "$actualKbText / $expectedKb KB")

            val actualBytesText = when {
                media.status == "FAILED" || media.failureReason != null -> "ERROR"
                media.actualSizeBytes != null && media.actualSizeBytes >= 0 -> "${media.actualSizeBytes} B"
                else -> "${media.bytesTransferred} B (Transferring)"
            }
            DiagnosticRow("Exact Bytes", "$actualBytesText / ${media.expectedSizeBytes} B")
            DiagnosticRow("Size Match", media.sizeMatchStatus)

            val expHash = if (media.expectedSha256.isNotBlank()) media.expectedSha256.take(16) + "..." else "NONE"
            val actHash = when {
                media.actualSha256 == "ERROR" -> "ERROR"
                media.actualSha256.isNotBlank() -> media.actualSha256.take(16) + "..."
                else -> "PENDING"
            }
            DiagnosticRow("Expected SHA-256", expHash)
            DiagnosticRow("Actual SHA-256", actHash)
            DiagnosticRow("Checksum Match", media.checksumMatchStatus)
            DiagnosticRow("Saved", if (media.saved) "YES" else "NO")

            if (media.failureReason != null) {
                Text(
                    text = "Reason: ${media.failureReason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (media.localFilePath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Received Image Preview:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                AsyncImage(
                    model = File(media.localFilePath),
                    contentDescription = "Received Media Evidence",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImageClick(media.localFilePath) },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun RelayEventItem(event: MeshRelayEvent) {
    val isMediaAction = event.action.startsWith("MEDIA")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (event.action) {
                "RECEIVED" -> Color(0xFFE3F2FD)
                "RELAYED" -> Color(0xFFE8F5E9)
                "DUPLICATE_DISCARDED" -> Color(0xFFFFF3E0)
                "OUTBOUND" -> Color(0xFFF3E5F5)
                "MEDIA CHECKSUM VERIFIED", "MEDIA PERSISTED", "MEDIA TRANSFER COMPLETED" -> Color(0xFFE8F5E9)
                "MEDIA TRANSFER STARTED", "MEDIA ANNOUNCED" -> Color(0xFFFFF8E1)
                "MEDIA CHECKSUM FAILED", "MEDIA TRANSFER FAILED" -> Color(0xFFFFEBEE)
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
                        "MEDIA CHECKSUM VERIFIED", "MEDIA PERSISTED", "MEDIA TRANSFER COMPLETED" -> Color(0xFF2E7D32)
                        "MEDIA TRANSFER STARTED", "MEDIA ANNOUNCED" -> Color(0xFFEF6C00)
                        "MEDIA CHECKSUM FAILED", "MEDIA TRANSFER FAILED" -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Msg ID: ${event.messageId}", style = MaterialTheme.typography.bodySmall)
            if (!isMediaAction) {
                Text("From: ${event.senderNodeId} | Origin: ${event.originNodeId}", style = MaterialTheme.typography.bodySmall)
                val ttlText = if (event.ttlAfter != null) "${event.ttlBefore} -> ${event.ttlAfter}" else "${event.ttlBefore}"
                Text("TTL: $ttlText", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
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
