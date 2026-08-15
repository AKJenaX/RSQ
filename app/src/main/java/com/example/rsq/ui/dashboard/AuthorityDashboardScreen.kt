package com.example.rsq.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rsq.data.model.*
import com.example.rsq.ui.viewmodel.AuthorityViewModel
import com.example.rsq.ui.viewmodel.UiState
import com.example.rsq.ui.common.LoadingView
import com.example.rsq.ui.common.ErrorView
import com.example.rsq.ui.common.EmptyStateView
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorityDashboardScreen(
    viewModel: AuthorityViewModel = viewModel(),
    onBack: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToDonations: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedReportId: String? by remember { mutableStateOf(null) }
    var showVolunteerPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Command Console", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToDonations) {
                        Icon(Icons.Default.VolunteerActivism, contentDescription = "Donations")
                    }
                    IconButton(onClick = onNavigateToNotifications) {
                        val unreadCount = (uiState as? UiState.Success)?.data?.unreadNotifications ?: 0
                        BadgedBox(badge = { 
                            if (unreadCount > 0) {
                                Badge { Text(unreadCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                is UiState.Loading -> LoadingView()
                is UiState.Empty -> EmptyStateView("No dashboard data available.")
                is UiState.Error -> ErrorView(state.message) { viewModel.loadData() }
                is UiState.Success -> {
                    val data = state.data
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            AuthorityStatsGrid(data.stats)
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Real-time Incident Feed",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                IconButton(onClick = {}) {
                                    Icon(Icons.Default.Map, contentDescription = "Map View")
                                }
                            }
                        }

                        items(data.reports) { report ->
                            val assignment = data.assignments.find { it.reportId == report.id }
                            IncidentReportCard(
                                report = report,
                                assignment = assignment,
                                onAssignClick = {
                                    selectedReportId = report.id
                                    showVolunteerPicker = true
                                }
                            )
                        }
                    }

                    if (showVolunteerPicker && selectedReportId != null) {
                        VolunteerPicker(
                            volunteers = data.volunteers,
                            onDismiss = { showVolunteerPicker = false },
                            onVolunteerSelected = { volunteer ->
                                viewModel.assignVolunteer(selectedReportId!!, volunteer)
                                showVolunteerPicker = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VolunteerPicker(
    volunteers: List<Volunteer>,
    onDismiss: () -> Unit,
    onVolunteerSelected: (Volunteer) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Assign Volunteer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(volunteers) { volunteer ->
                        OutlinedButton(
                            onClick = { onVolunteerSelected(volunteer) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(volunteer.name, fontWeight = FontWeight.Bold)
                                    Text("${volunteer.completedAssignments} Rescues", style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun AuthorityStatsGrid(stats: AuthorityDashboardStats) {
    val statItems = listOf(
        Quadruple("Total Reports", stats.totalReports.toString(), Icons.Default.DataUsage, MaterialTheme.colorScheme.primary),
        Quadruple("Active Cases", stats.activeCases.toString(), Icons.Default.GppMaybe, Color(0xFFD32F2F)),
        Quadruple("Pending Triage", stats.pendingCases.toString(), Icons.Default.Update, Color(0xFFFBC02D)),
        Quadruple("Success Rate", stats.resolvedCases.toString(), Icons.Default.Verified, Color(0xFF388E3C)),
        Quadruple("Rescuers", stats.activeVolunteers.toString(), Icons.Default.Shield, Color(0xFF1976D2)),
        Quadruple("Donations", "$${String.format("%.0f", stats.totalDonations)}", Icons.Default.AccountBalanceWallet, Color(0xFF7B1FA2))
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in statItems.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                statItems[i].let { (label, value, icon, color) ->
                    AuthoritySmallStatCard(label, value, icon, color, Modifier.weight(1f))
                }
                if (i + 1 < statItems.size) {
                    statItems[i + 1].let { (label, value, icon, color) ->
                        AuthoritySmallStatCard(label, value, icon, color, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun AuthoritySmallStatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun IncidentReportCard(
    report: RecentReport,
    assignment: Assignment?,
    onAssignClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = when(report.priority) {
                            Priority.HIGH -> Color(0xFFD32F2F).copy(alpha = 0.1f)
                            else -> Color.Gray.copy(alpha = 0.1f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = report.type.take(1),
                                fontWeight = FontWeight.Black,
                                color = if(report.priority == Priority.HIGH) Color(0xFFD32F2F) else Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = report.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = report.type,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                IncidentStatusBadge(assignment?.status?.name ?: report.status)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(report.location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (assignment?.volunteerName != null && assignment.status != AssignmentStatus.AVAILABLE) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Assigned to: ${assignment.volunteerName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(
                    text = "Reported at ${sdf.format(Date(report.timestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                
                if (assignment?.status == AssignmentStatus.AVAILABLE) {
                    Button(
                        onClick = onAssignClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Assign Volunteer", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Button(
                        onClick = {},
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Analyze", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun IncidentStatusBadge(status: String) {
    val color = when(status) {
        "Active", "IN_PROGRESS" -> Color(0xFFD32F2F)
        "Resolved", "RESOLVED" -> Color(0xFF388E3C)
        "Pending", "ASSIGNED" -> Color(0xFF1976D2)
        "AVAILABLE" -> Color(0xFFFBC02D)
        else -> Color.Gray
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Text(
            text = status.uppercase(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
