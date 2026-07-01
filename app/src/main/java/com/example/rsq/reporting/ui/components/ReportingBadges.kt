package com.example.rsq.reporting.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rsq.reporting.model.ReportStatus

@Composable
fun StatusBadge(status: ReportStatus) {
    val color = when (status) {
        ReportStatus.RESOLVED -> Color(0xFF4CAF50) // Success Green
        ReportStatus.IN_PROGRESS -> Color(0xFF2196F3) // Info Blue
        ReportStatus.OPEN -> Color(0xFFFFC107) // Warning Yellow
        ReportStatus.ASSIGNED -> Color(0xFF9C27B0) // Purple
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun SeverityBadge(severity: String) {
    val color = when (severity.uppercase()) {
        "CRITICAL" -> MaterialTheme.colorScheme.error
        "HIGH" -> Color(0xFFF44336) // Light Red
        "MEDIUM" -> Color(0xFFFF9800) // Orange
        "LOW" -> Color(0xFF4CAF50) // Green
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = severity,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}
