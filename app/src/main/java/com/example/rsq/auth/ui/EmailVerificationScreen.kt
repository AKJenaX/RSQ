package com.example.rsq.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rsq.R
import com.example.rsq.auth.model.AuthState
import com.example.rsq.auth.viewmodel.AuthViewModel

@Composable
fun EmailVerificationScreen(
    viewModel: AuthViewModel,
    onBackToLogin: () -> Unit,
    onVerificationSuccess: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val email = viewModel.getCurrentUserEmail()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onVerificationSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.verify_email),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.verification_sent_to, email),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.checkEmailVerification() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.i_verified_email), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { viewModel.resendVerificationEmail() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(stringResource(R.string.resend_verification))
        }

        Spacer(modifier = Modifier.height(32.dp))

        TextButton(onClick = onBackToLogin) {
            Text(stringResource(R.string.back_to_login), fontWeight = FontWeight.SemiBold)
        }

        if (authState is AuthState.EmailNotVerified) {
            Text(
                text = stringResource(R.string.email_not_verified_yet),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
