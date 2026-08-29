package com.example.rsq.ui.role

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rsq.R
import com.example.rsq.location.model.LocationReadiness
import com.example.rsq.location.model.LocationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectionScreen(
    isAuthorized: Boolean,
    locationState: LocationState,
    onLogout: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDonations: () -> Unit,
    onRoleSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val TAG = "RoleSelection"

    // Mandatory Location Services Popup
    if (locationState.readiness == LocationReadiness.SERVICES_DISABLED) {
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
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.location_required),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.location_required_description),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            Log.d(TAG, "LOCATION_SETTINGS_OPENED")
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.enable_location), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name) + " HOME", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = onOpenDonations) {
                        Icon(imageVector = Icons.Default.VolunteerActivism, contentDescription = "Impact Fund")
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Section
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            // Location Status Indicator
            Surface(
                color = when (locationState.readiness) {
                    LocationReadiness.READY -> Color(0xFFE8F5E9)
                    else -> Color(0xFFFFF3E0)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (locationState.readiness != LocationReadiness.READY) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = when (locationState.readiness) {
                            LocationReadiness.READY -> stringResource(R.string.location_ready)
                            else -> stringResource(R.string.getting_location)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (locationState.readiness) {
                            LocationReadiness.READY -> Color(0xFF2E7D32)
                            else -> Color(0xFFEF6C00)
                        }
                    )
                }
            }

            // Selection Section
            Text(
                text = stringResource(R.string.choose_mode),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Button(
                onClick = {
                    Log.d(TAG, "Selected role: VICTIM")
                    onRoleSelected("VICTIM")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F), // SOS Red
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).padding(end = 12.dp)
                    )
                    Column {
                        Text(text = stringResource(R.string.need_help), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = stringResource(R.string.need_help_description), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    Log.d(TAG, "Selected role: VOLUNTEER")
                    onRoleSelected("VOLUNTEER")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF388E3C), // Rescue Green
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Handshake,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).padding(end = 12.dp)
                    )
                    Column {
                        Text(text = stringResource(R.string.can_help), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = stringResource(R.string.can_help_description), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isAuthorized) {
                OutlinedButton(
                    onClick = {
                        Log.d(TAG, "Selected role: AUTHORITY")
                        onRoleSelected("AUTHORITY")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).padding(end = 12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(text = stringResource(R.string.authority_mode), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = stringResource(R.string.authority_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            Text(
                text = stringResource(R.string.change_anytime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 48.dp)
            )
        }
    }
}
