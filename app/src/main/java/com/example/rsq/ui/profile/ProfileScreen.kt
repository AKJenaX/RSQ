package com.example.rsq.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rsq.R
import com.example.rsq.auth.viewmodel.AuthViewModel
import com.example.rsq.data.repository.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAssignments: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMeshTest: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {},
    onLogout: () -> Unit
) {
    val userProfile by viewModel.currentUserProfile.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_profile), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = userProfile?.name ?: "User Name",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = userProfile?.role ?: "NO ROLE",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // User Info
            ProfileInfoCard(
                email = userProfile?.email ?: "No email provided"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Text(
                text = stringResource(R.string.account_activity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            ProfileActionButton(
                icon = Icons.Default.History,
                label = stringResource(R.string.emergency_history),
                onClick = onNavigateToHistory
            )
            
            if (userProfile?.role == "VOLUNTEER") {
                ProfileActionButton(
                    icon = Icons.Default.Assignment,
                    label = stringResource(R.string.my_assignments),
                    onClick = onNavigateToAssignments
                )
            }

            ProfileActionButton(
                icon = Icons.Default.Notifications,
                label = stringResource(R.string.alert_notifications),
                onClick = onNavigateToNotifications
            )

            ProfileActionButton(
                icon = Icons.Default.Router,
                label = "Mesh Diagnostics & Status",
                onClick = onNavigateToDiagnostics
            )

            ProfileActionButton(
                icon = Icons.Default.CellTower,
                label = "Offline Mesh Test",
                onClick = onNavigateToMeshTest
            )

            ProfileActionButton(
                icon = Icons.Default.Settings,
                label = "App Settings",
                onClick = { showSettingsDialog = true }
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Logout
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.logout_session), fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showSettingsDialog) {
        val currentRadius by settingsRepository.visibilityRadiusKm.collectAsState()
        val currentDuration by settingsRepository.visibilityDurationHours.collectAsState()
        
        var tempRadius by remember { mutableStateOf(currentRadius.toFloat()) }
        var tempDuration by remember { mutableStateOf(currentDuration.toFloat()) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("App Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Visibility Radius: ${tempRadius.toInt()} km", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = tempRadius,
                        onValueChange = { tempRadius = it },
                        valueRange = 5f..100f,
                        steps = 19
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Report Duration: ${tempDuration.toInt()} hrs", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = tempDuration,
                        onValueChange = { tempDuration = it },
                        valueRange = 1f..72f,
                        steps = 71
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsRepository.setVisibilityRadiusKm(tempRadius.toInt())
                    settingsRepository.setVisibilityDurationHours(tempDuration.toInt())
                    showSettingsDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProfileInfoCard(email: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = email, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ProfileActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
