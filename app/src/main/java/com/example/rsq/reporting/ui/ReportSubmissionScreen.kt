package com.example.rsq.reporting.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.model.ReportState
import com.example.rsq.reporting.viewmodel.ReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSubmissionScreen(
    viewModel: ReportViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var severity by rememberSaveable { mutableStateOf("MEDIUM") }
    var expanded by remember { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val reportState by viewModel.reportState.collectAsState()
    val severityOptions = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")

    // Handle form reset and ViewModel state reset on success
    LaunchedEffect(reportState) {
        if (reportState is ReportState.Success) {
            title = ""
            description = ""
            severity = "MEDIUM"
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
                    // Title Field
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

                    // Description Field
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

                    // Severity Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = severity,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Severity Level") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            severityOptions.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        severity = selectionOption
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Submit Button
            Button(
                onClick = {
                    when {
                        title.isBlank() -> validationError = "Title is required"
                        description.isBlank() -> validationError = "Description is required"
                        else -> {
                            val report = Report(
                                userId = currentUserId,
                                title = title,
                                description = description,
                                severity = severity,
                                status = "PENDING",
                                timestamp = System.currentTimeMillis()
                            )
                            viewModel.submitReport(report)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = reportState !is ReportState.Loading
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
                    Text("Submit Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
