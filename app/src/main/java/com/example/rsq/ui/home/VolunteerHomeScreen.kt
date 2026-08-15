package com.example.rsq.ui.home

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rsq.data.model.EmergencyRequest
import com.example.rsq.data.model.Priority
import com.example.rsq.ui.theme.RSQTheme
import com.example.rsq.ui.viewmodel.MessageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerHomeScreen(
    messageViewModel: MessageViewModel = viewModel(),
    onSwitchRole: () -> Unit,
) {
    val messages by messageViewModel.messages.collectAsState()
    
    val requests = messages.map { message ->
        EmergencyRequest(
            id = message.id,
            title = message.message,
            distance = 0.0,
            priority = message.priority,
            latitude = message.latitude,
            longitude = message.longitude,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Requests", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onSwitchRole) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Dashboard")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Header Section
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = "Nearby Requests",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Real-time emergency alerts in your area",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📡", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No active requests nearby",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(requests, key = { it.id }) { request ->
                        EmergencyRequestCard(request) {
                            Log.d("VolunteerHome", "Clicked request: ${request.title}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyRequestCard(request: EmergencyRequest, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PriorityBadge(request.priority)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = request.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn, 
                        contentDescription = null, 
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${request.distance} km away",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("VIEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun PriorityBadge(priority: Priority) {
    val (color, label) = when (priority) {
        Priority.HIGH -> Color(0xFFD32F2F) to "HIGH"
        Priority.MEDIUM -> Color(0xFFFBC02D) to "MEDIUM"
        Priority.LOW -> Color(0xFF388E3C) to "LOW"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Preview(showBackground = true)
@Composable
fun VolunteerHomeScreenPreview() {
    RSQTheme {
        VolunteerHomeScreen(onSwitchRole = {})
    }
}
