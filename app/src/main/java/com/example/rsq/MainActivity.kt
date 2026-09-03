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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.example.rsq.ai.data.SeverityEngine
import com.example.rsq.ui.navigation.AppNavigation
import com.example.rsq.ui.theme.RSQTheme
import com.example.rsq.util.PaymentErrorData
import com.example.rsq.util.PaymentResultHandler
import com.example.rsq.util.PaymentSuccessData
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Preload Razorpay Checkout for faster UI rendering
        Checkout.preload(applicationContext)

        // Initialize AI Severity Engine with ONNX Runtime on a background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i("MainActivity", "AI_INIT_STARTED: Initializing SeverityEngine...")
                SeverityEngine.initialize(this@MainActivity)
                Log.i("MainActivity", "AI_INIT_SUCCESS: SeverityEngine initialized.")
            } catch (t: Throwable) {
                Log.e("MainActivity", "AI_INIT_CRITICAL_FAILURE: SeverityEngine failed to initialize", t)
            }
        }

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

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        paymentData?.let {
            lifecycleScope.launch {
                PaymentResultHandler.emitSuccess(
                    PaymentSuccessData(
                        orderId = it.orderId ?: "",
                        paymentId = razorpayPaymentId ?: "",
                        signature = it.signature ?: ""
                    )
                )
            }
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        lifecycleScope.launch {
            PaymentResultHandler.emitError(
                PaymentErrorData(
                    code = code,
                    message = response ?: "Unknown error"
                )
            )
        }
    }
}
