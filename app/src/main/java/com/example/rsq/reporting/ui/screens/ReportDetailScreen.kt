package com.example.rsq.reporting.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.rsq.reporting.model.Report
import com.example.rsq.reporting.ui.components.SeverityBadge
import com.example.rsq.reporting.viewmodel.ReportViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: String,
    viewModel: ReportViewModel,
    onBack: () -> Unit
) {
    val reports by viewModel.reports.collectAsState()
    val meshReports by viewModel.meshReports.collectAsState()
    
    val report = remember(reports, meshReports, reportId) {
        reports.find { it.id == reportId } ?: meshReports.find { it.id == reportId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incident Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (report == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Report not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeverityBadge(report.severity)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = report.status.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = report.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                Text(
                    text = "Reported on ${sdf.format(Date(report.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Evidence (Multi-image Part 22)
                if (report.imageUrls.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        report.imageUrls.forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = "Evidence Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else if (report.imageUrl != null) {
                    AsyncImage(
                        model = report.imageUrl,
                        contentDescription = "Evidence Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Description
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Location
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Location", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = if (report.latitude != null) "Lat: ${String.format("%.4f", report.latitude)}, Lng: ${String.format("%.4f", report.longitude)}" else "Location Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (report.detectedHazards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "AI Assessment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 8.dp
                    ) {
                        report.detectedHazards.forEach { hazard ->
                            AssistChip(
                                onClick = {},
                                label = { Text(hazard) },
                                leadingIcon = { Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeholders.forEach { placeable ->
            if (currentRowWidth + placeable.width + mainAxisSpacing.roundToPx() > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        rows.add(currentRow)

        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * crossAxisSpacing.roundToPx()
        
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                }
                y += rowHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}
