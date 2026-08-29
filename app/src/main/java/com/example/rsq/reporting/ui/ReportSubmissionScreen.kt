package com.example.rsq.reporting.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import java.io.File
import com.example.rsq.R
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.ReportState
import com.example.rsq.reporting.viewmodel.ReportViewModel
import com.example.rsq.location.viewmodel.LocationViewModel
import com.example.rsq.location.data.LocationRepository
import com.example.rsq.location.model.LocationReadiness
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSubmissionScreen(
    viewModel: ReportViewModel,
    locationViewModel: LocationViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val locationState by locationViewModel.locationReadiness.collectAsState()
    val reportState by viewModel.reportState.collectAsState()

    val title by viewModel.title.collectAsState()
    val description by viewModel.description.collectAsState()
    val selectedImageUris by viewModel.selectedImageUris.collectAsState()
    val tempCameraUri by viewModel.tempCameraUri.collectAsState()
    
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addImageUris(uris)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            viewModel.addImageUris(listOf(tempCameraUri!!))
        }
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val photoFile = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "com.example.rsq.provider", photoFile)
            viewModel.updateTempCameraUri(uri)
            cameraLauncher.launch(uri)
        } else {
            validationError = "Camera permission is required to take photos."
        }
    }

    // Success Overlay
    if (reportState is ReportState.Success) {
        Dialog(
            onDismissRequest = { viewModel.resetState(); onNavigateBack() },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = Color(0xFFE8F5E9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Report Submitted",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your emergency report has been sent successfully to the responders network.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.resetState(); onNavigateBack() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("DONE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submit Emergency Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Location Readiness UI
            LocationReadinessSection(
                state = locationState,
                onEnableLocation = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                onGrantPermission = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                },
                onRetry = {
                    locationViewModel.fetchLocation()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Provide details about the emergency. Your report will be broadcasted to nearby volunteers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { 
                            viewModel.updateTitle(it)
                            validationError = null
                        },
                        label = { Text("Title") },
                        placeholder = { Text("e.g., Medical Emergency, Fire") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        enabled = reportState is ReportState.Idle || reportState is ReportState.Error
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { 
                            viewModel.updateDescription(it)
                            validationError = null
                        },
                        label = { Text("Description") },
                        placeholder = { Text("Describe the situation in detail...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = false,
                        maxLines = 5,
                        enabled = reportState is ReportState.Idle || reportState is ReportState.Error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Evidence Photos Section
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Evidence Photos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedImageUris.size}/5",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedImageUris.size == 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    if (selectedImageUris.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedImageUris.forEach { uri ->
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeImageUri(uri) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(28.dp)
                                            .padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                        enabled = reportState is ReportState.Idle || reportState is ReportState.Error
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    val photoFile = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(context, "com.example.rsq.provider", photoFile)
                                    viewModel.updateTempCameraUri(uri)
                                    cameraLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            enabled = selectedImageUris.size < 5 && (reportState is ReportState.Idle || reportState is ReportState.Error)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Camera")
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            enabled = selectedImageUris.size < 5 && (reportState is ReportState.Idle || reportState is ReportState.Error)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Photos")
                        }
                    }
                    
                    if (selectedImageUris.size == 5) {
                        Text(
                            text = "Maximum of 5 photos reached",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Submit Button gated by location availability
            val hasLocation = locationState.latitude != null && locationState.longitude != null
            val isLocationReliable = locationState.readiness == LocationReadiness.READY
            val canSubmit = hasLocation && isLocationReliable
            val isProcessing = reportState !is ReportState.Idle && reportState !is ReportState.Error && reportState !is ReportState.Success && reportState !is ReportState.PendingSync
            
            Button(
                onClick = {
                    when {
                        title.isBlank() -> validationError = context.getString(R.string.title_required)
                        description.isBlank() -> validationError = context.getString(R.string.description_required)
                        !canSubmit -> validationError = context.getString(R.string.location_required_error)
                        else -> {
                            coroutineScope.launch {
                                val report = Report(
                                    userId = currentUserId,
                                    title = title,
                                    description = description,
                                    severity = "MEDIUM",
                                    status = ReportStatus.OPEN,
                                    timestamp = System.currentTimeMillis(),
                                    latitude = locationState.latitude,
                                    longitude = locationState.longitude,
                                    imageUrl = null,
                                    imageUrls = emptyList()
                                )
                                viewModel.submitReport(report, selectedImageUris)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = !isProcessing && canSubmit
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val loadingLabel = when (reportState) {
                        is ReportState.UploadingEvidence -> stringResource(R.string.uploading_evidence)
                        is ReportState.CreatingCloudReport -> stringResource(R.string.sending_report)
                        else -> stringResource(R.string.submitting)
                    }
                    Text(text = loadingLabel)
                } else {
                    val label = if (canSubmit) stringResource(R.string.submit_report) else stringResource(R.string.acquiring_location)
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Status feedback
            val statusMessage = validationError ?: when (val state = reportState) {
                is ReportState.PendingSync -> state.reason
                is ReportState.Error -> state.message
                else -> null
            }

            if (statusMessage != null) {
                val isError = validationError != null || reportState is ReportState.Error
                Surface(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Warning else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = statusMessage,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationReadinessSection(
    state: com.example.rsq.location.model.LocationState,
    onEnableLocation: () -> Unit,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit
) {
    val color = when (state.readiness) {
        LocationReadiness.READY -> MaterialTheme.colorScheme.primaryContainer
        LocationReadiness.ACQUIRING -> MaterialTheme.colorScheme.secondaryContainer
        LocationReadiness.LOW_ACCURACY -> Color(0xFFFFF9C4) // Light Yellow
        else -> MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (state.readiness) {
                        LocationReadiness.READY -> Icons.Default.GpsFixed
                        LocationReadiness.SERVICES_DISABLED -> Icons.Default.GpsOff
                        LocationReadiness.PERMISSION_DENIED -> Icons.Default.NoEncryption
                        LocationReadiness.ACQUIRING -> Icons.Default.GpsNotFixed
                        LocationReadiness.LOW_ACCURACY -> Icons.Default.MyLocation
                        else -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = if (state.readiness == LocationReadiness.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val statusText = when (state.readiness) {
                        LocationReadiness.READY -> stringResource(R.string.location_ready)
                        LocationReadiness.SERVICES_DISABLED -> "Location Services Disabled"
                        LocationReadiness.PERMISSION_DENIED -> "Location Permission Required"
                        LocationReadiness.ACQUIRING -> "Acquiring GPS Signal..."
                        LocationReadiness.LOW_ACCURACY -> "Improving Accuracy..."
                        else -> "Location Error"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (state.readiness == LocationReadiness.READY && state.latitude != null) {
                        Text(
                            text = "Accuracy: ${state.accuracy?.toInt()}m • Lat: ${String.format("%.4f", state.latitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (state.error != null) {
                        Text(text = state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                when (state.readiness) {
                    LocationReadiness.SERVICES_DISABLED -> {
                        TextButton(onClick = onEnableLocation) { Text("Enable") }
                    }
                    LocationReadiness.PERMISSION_DENIED -> {
                        TextButton(onClick = onGrantPermission) { Text("Grant") }
                    }
                    LocationReadiness.LOW_ACCURACY, LocationReadiness.ERROR -> {
                        IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Retry") }
                    }
                    LocationReadiness.ACQUIRING -> {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    else -> {}
                }
            }
        }
    }
}
