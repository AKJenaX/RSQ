package com.example.rsq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.rsq.ai.data.SeverityEngine
import com.example.rsq.ui.navigation.AppNavigation
import com.example.rsq.ui.theme.RSQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AI Severity Engine with ONNX Runtime
        SeverityEngine.initialize(this)

        enableEdgeToEdge()
        setContent {
            RSQTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier.padding(innerPadding),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
