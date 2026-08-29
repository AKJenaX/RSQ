package com.example.rsq.ui.donation

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rsq.data.model.Donation
import com.example.rsq.ui.viewmodel.DonationViewModel
import com.example.rsq.ui.viewmodel.UiState
import com.example.rsq.ui.common.LoadingView
import com.example.rsq.ui.common.ErrorView
import com.example.rsq.ui.common.EmptyStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationScreen(
    viewModel: DonationViewModel,
    onBack: () -> Unit,
    onMakeDonation: (Double) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impact Fund", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                is UiState.Loading -> LoadingView()
                is UiState.Empty -> EmptyStateView("No donation history available.")
                is UiState.Error -> ErrorView(state.message) { viewModel.loadData() }
                is UiState.Success -> {
                    val (summary, donations) = state.data
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            EnhancedTotalCard(
                                amount = summary.totalAmount,
                                currency = summary.currency,
                                onDonate = { onMakeDonation(100.0) }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Recent Transactions",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                IconButton(onClick = {}) {
                                    Icon(Icons.Default.Tune, contentDescription = "Filter")
                                }
                            }
                        }

                        items(donations) { donation ->
                            EnhancedDonationCard(donation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedTotalCard(amount: Double, currency: String, onDonate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D47A1), Color(0xFF1976D2))
                    )
                )
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.VolunteerActivism,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "RECOVERY & RELIEF FUND",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$currency${String.format("%,.2f", amount)}",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDonate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Make a Contribution", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun EnhancedDonationCard(donation: Donation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = donation.donorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = donation.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DonationStatusBadge(donation.status)
                }
            }

            Text(
                text = "+$${String.format("%.2f", donation.amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF388E3C)
            )
        }
    }
}

@Composable
fun DonationStatusBadge(status: String) {
    val isCompleted = status == "Completed"
    val color = if (isCompleted) Color(0xFF388E3C) else Color(0xFFFBC02D)

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Black
        )
    }
}
