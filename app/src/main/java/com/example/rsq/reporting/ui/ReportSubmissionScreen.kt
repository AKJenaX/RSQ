package com.example.rsq.reporting.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportStatus
import com.example.rsq.reporting.model.ReportState
import com.example.rsq.reporting.viewmodel.ReportViewModel
import com.example.rsq.location.viewmodel.LocationViewModel
import com.example.rsq.location.data.LocationRepository
import com.example.rsq.location.model.LocationReadiness
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSubmissionScreen(
    viewModel: ReportViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationRepository = remember { LocationRepository(context, fusedLocationClient) }
    val locationViewModel: LocationViewModel = remember { LocationViewModel(locationRepository) }
    
    val locationState by locationViewModel.locationReadiness.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) selectedImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (!granted) {
            locationViewModel.updateReadiness(LocationReadiness.PERMISSION_DENIED)
        }
    }

    // Lifecycle observer to re-check location when returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                   ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                
                if (hasPermission) {
                    locationViewModel.fetchLocation()
                } else {
                    locationViewModel.updateReadiness(LocationReadiness.PERMISSION_DENIED)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial permission request
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                           ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            locationViewModel.fetchLocation()
        }
    }

    val reportState by viewModel.reportState.collectAsState()

    // Phase 3: Blocking Location Dialog
    if (locationState.readiness != LocationReadiness.READY) {
        Dialog(
            onDismissRequest = { /* Non-dismissible */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Location Required",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (locationState.readiness) {
                            LocationReadiness.SERVICES_DISABLED -> "RSQ needs your location to submit an emergency report. Please turn on Location Services."
                            LocationReadiness.PERMISSION_DENIED -> "Location permission is required for emergency reporting."
                            LocationReadiness.ACQUIRING -> "Acquiring reliable GPS signal. Please stay in an open area."
                            LocationReadiness.LOW_ACCURACY -> "Improving location accuracy for precise response..."
                            else -> "Initializing location services..."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (locationState.readiness == LocationReadiness.SERVICES_DISABLED) {
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Turn On Location")
                        }
                    } else if (locationState.readiness == LocationReadiness.PERMISSION_DENIED) {
                        Button(
                            onClick = { 
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission")
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                    
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Cancel Report")
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
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
                            title = it 
                            validationError = null
                        },
                        label = { Text("Title") },
                        placeholder = { Text("e.g., Medical Emergency, Fire") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { 
                            description = it 
                            validationError = null
                        },
                        label = { Text("Description") },
                        placeholder = { Text("Describe the situation in detail...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = false,
                        maxLines = 5
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Image Selection Section (Phase 4: Camera First)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Evidence",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Selected image preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                val photoFile = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Camera")
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Photos")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Submit Button gated by location readiness
            Button(
                onClick = {
                    when {
                        title.isBlank() -> validationError = "Title is required"
                        description.isBlank() -> validationError = "Description is required"
                        locationState.readiness != LocationReadiness.READY -> validationError = "Waiting for reliable location..."
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
                                    imageUrl = null
                                )
                                viewModel.submitReport(report, selectedImageUri)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = reportState !is ReportState.Loading && locationState.readiness == LocationReadiness.READY
            ) {
                if (reportState is ReportState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Submitting...")
                } else {
                    val label = if (locationState.readiness == LocationReadiness.READY) "Submit Report" else "Waiting for GPS..."
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Status feedback
            val statusMessage = validationError ?: when (val state = reportState) {
                is ReportState.Success -> state.message
                is ReportState.Error -> state.message
                else -> null
            }

            if (statusMessage != null) {
                val isError = validationError != null || reportState is ReportState.Error
                Surface(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth(),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = statusMessage,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
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
                    Text(
                        text = when (state.readiness) {
                            LocationReadiness.READY -> "Location Ready"
                            LocationReadiness.SERVICES_DISABLED -> "Location Services Disabled"
                            LocationReadiness.PERMISSION_DENIED -> "Location Permission Required"
                            LocationReadiness.ACQUIRING -> "Acquiring GPS Signal..."
                            LocationReadiness.LOW_ACCURACY -> "Improving Accuracy..."
                            else -> "Location Error"
                        },
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
