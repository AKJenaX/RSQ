package com.example.rsq.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rsq.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VictimHomeScreen(
    onTriggerSOS: () -> Unit,
    onViewHistory: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.emergency_mode), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onViewHistory) {
                        Icon(imageVector = Icons.Default.History, contentDescription = "View History")
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulsing Animation Setup
            val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            // SOS Button with Gradient and Animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(scale)
            ) {
                // Outer Glow
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .background(
                            color = Color(0xFFD32F2F).copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                )

                Button(
                    onClick = onTriggerSOS,
                    modifier = Modifier.size(180.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = CircleShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF44336), Color(0xFFB71C1C))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SOS",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.ready_to_help),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.location_share_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
