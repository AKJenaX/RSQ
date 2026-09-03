package com.example.rsq.ui.donation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rsq.data.model.Donation
import com.example.rsq.ui.common.EmptyStateView
import com.example.rsq.ui.common.ErrorView
import com.example.rsq.ui.common.LoadingView
import com.example.rsq.ui.viewmodel.DonationViewModel
import com.example.rsq.ui.viewmodel.PaymentState
import com.example.rsq.ui.viewmodel.UiState
import com.razorpay.Checkout
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationScreen(
    viewModel: DonationViewModel,
    userName: String,
    userId: String,
    userEmail: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val paymentState by viewModel.paymentState.collectAsState()
    val context = LocalContext.current

    var showAmountDialog by remember { mutableStateOf(false) }

    // Trigger Razorpay Checkout when Order is created
    LaunchedEffect(paymentState) {
        val currentState = paymentState
        when (currentState) {
            is PaymentState.OrderCreated -> {
                Log.d("DONATION", "[DONATION] PaymentState.OrderCreated received")
                startRazorpayCheckout(context, currentState.orderId, currentState.amount, userName, userEmail)
            }
            is PaymentState.Success -> {
                Log.d("DONATION", "[DONATION] PaymentState.Success received")
                Toast.makeText(context, "Thank you! Contribution verified successfully.", Toast.LENGTH_LONG).show()
                viewModel.resetPaymentState()
            }
            is PaymentState.Failure -> {
                Log.e("DONATION", "[DONATION] PaymentState.Failure: ${currentState.message}")
                Toast.makeText(context, currentState.message, Toast.LENGTH_LONG).show()
                viewModel.resetPaymentState()
            }
            else -> {}
        }
    }

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
                is UiState.Empty -> EmptyStateView(
                    message = "No donation history available.",
                    actionLabel = "Make a Contribution",
                    onAction = { showAmountDialog = true }
                )
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
                                onDonate = { showAmountDialog = true }
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

    if (showAmountDialog) {
        DonationAmountDialog(
            onDismiss = { showAmountDialog = false },
            onConfirm = { amount ->
                Log.d("DONATION", "[DONATION] Donate clicked")
                Log.d("DONATION", "[DONATION] amount = $amount")
                showAmountDialog = false
                viewModel.startDonationFlow(amount, userName, userId)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationAmountDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var selectedAmount by remember { mutableStateOf<Double?>(null) }
    var customAmount by remember { mutableStateOf("") }
    
    val presets = listOf(100.0, 500.0, 1000.0, 2000.0)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Donation Amount") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presets.take(2).forEach { preset ->
                        FilterChip(
                            selected = selectedAmount == preset,
                            onClick = { 
                                selectedAmount = preset
                                customAmount = ""
                            },
                            label = { Text("₹${preset.toInt()}") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presets.drop(2).forEach { preset ->
                        FilterChip(
                            selected = selectedAmount == preset,
                            onClick = { 
                                selectedAmount = preset
                                customAmount = ""
                            },
                            label = { Text("₹${preset.toInt()}") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { 
                        customAmount = it
                        selectedAmount = null
                    },
                    label = { Text("Custom Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = selectedAmount ?: customAmount.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        onConfirm(amount)
                    }
                },
                enabled = (selectedAmount != null) || (customAmount.toDoubleOrNull()?.let { it > 0 } == true)
            ) {
                Text("Donate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                
                donation.paymentId?.let { pid ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ref: $pid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                donation.orderId?.let { oid ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Order: $oid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "+₹${String.format("%.2f", donation.amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF388E3C)
            )
        }
    }
}

fun startRazorpayCheckout(
    context: Context,
    orderId: String, 
    amount: Double, 
    userName: String,
    userEmail: String
) {
    var activity: Activity? = context as? Activity
    if (activity == null && context is ContextWrapper) {
        var base = context.baseContext
        while (base is ContextWrapper && base !is Activity) {
            base = base.baseContext
        }
        activity = base as? Activity
    }

    if (activity == null) {
        Log.e("DONATION", "[DONATION] Error: Cannot find Activity from Context to start Razorpay!")
        Toast.makeText(context, "Error: Cannot open payment - Activity not found", Toast.LENGTH_LONG).show()
        return
    }

    val checkout = Checkout()
    checkout.setKeyID("rzp_test_TVvZzrCuli56f5") // Razorpay TEST Key ID

    try {
        val options = JSONObject()
        options.put("name", "RSQ Relief Fund")
        options.put("description", "Emergency Response Contribution")
        options.put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png")
        options.put("order_id", orderId)
        options.put("theme.color", "#1976D2")
        options.put("currency", "INR")
        
        // Use Math.round to avoid floating point precision issues during conversion
        val amountInPaise = Math.round(amount * 100).toInt()
        options.put("amount", amountInPaise)
        
        // Prefill details improve payment method visibility (especially UPI)
        val prefill = JSONObject()
        prefill.put("name", userName)
        prefill.put("email", userEmail.ifBlank { "test@example.com" })
        prefill.put("contact", "9999999999") // Required for some UPI flows in test mode
        options.put("prefill", prefill)

        // Force local to India to ensure domestic test cards work
        options.put("send_sms_hash", true)
        
        Log.d("DONATION", "[DONATION] opening Razorpay Checkout")
        checkout.open(activity, options)
    } catch (e: Exception) {
        Log.e("Razorpay", "Error in starting Razorpay Checkout", e)
        Toast.makeText(context, "Error starting Razorpay Checkout: ${e.message}", Toast.LENGTH_LONG).show()
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