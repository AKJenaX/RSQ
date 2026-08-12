package com.example.rsq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rsq.auth.model.AuthState
import com.example.rsq.auth.ui.LoginScreen
import com.example.rsq.auth.ui.RegisterScreen
import com.example.rsq.auth.viewmodel.AuthViewModel
import com.example.rsq.mesh.data.LocalMeshMessageRepository
import com.example.rsq.mesh.data.NearbyMeshTransport
import com.example.rsq.mesh.data.NodeIdentityRepository
import com.example.rsq.mesh.domain.MeshRelayEngine
import com.example.rsq.mesh.ui.MeshTestScreen
import com.example.rsq.mesh.viewmodel.MeshTestViewModel
import com.example.rsq.reporting.ui.ReportHistoryScreen
import com.example.rsq.reporting.ui.ReportSubmissionScreen
import com.example.rsq.reporting.viewmodel.ReportViewModel
import com.example.rsq.ui.home.VictimHomeScreen
import com.example.rsq.ui.home.VolunteerHomeScreen
import com.example.rsq.ui.role.RoleSelectionScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object RoleSelection : Screen("role_selection")
    object VictimHome : Screen("victim_home")
    object VolunteerHome : Screen("volunteer_home")
    object ReportSubmission : Screen("report_submission")
    object ReportHistory : Screen("report_history")
    object MeshTest : Screen("mesh_test")
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val reportViewModel: ReportViewModel = viewModel()
    
    // Mesh dependencies for testing
    val meshIdentityProvider = remember { NodeIdentityRepository(context) }
    val meshMessageRepository = remember { LocalMeshMessageRepository(context) }
    val meshTransport = remember { NearbyMeshTransport(context, meshIdentityProvider) }
    
    // Lifecycle-aware Relay Engine
    val meshRelayEngine = remember {
        MeshRelayEngine(
            transport = meshTransport,
            repository = meshMessageRepository,
            identityProvider = meshIdentityProvider,
            scope = authViewModel.internalScope // Use a persistent scope
        )
    }

    val authState by authViewModel.authState.collectAsState()

    // TODO: Introduce AuthGate/Splash route for cleaner startup authentication flow.
    // Session check on app launch
    LaunchedEffect(Unit) {
        authViewModel.checkSession()
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
    ) {
        composable(Screen.Login.route) {
            // Observe authState for successful login or existing session
            LaunchedEffect(authState) {
                when (val state = authState) {
                    is AuthState.Success -> {
                        if (state.message == "Login successful" || state.message == "Session restored") {
                            navController.navigate(Screen.RoleSelection.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    }
                    else -> Unit
                }
            }
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            // Navigate back to login after successful registration
            LaunchedEffect(authState) {
                when (val state = authState) {
                    is AuthState.Success -> {
                        if (state.message == "Registration successful") {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Register.route) { inclusive = true }
                            }
                            // Reset state so LoginScreen doesn't immediately navigate to RoleSelection
                            authViewModel.resetState()
                        }
                    }
                    else -> Unit
                }
            }
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.RoleSelection.route) {
            // Navigate to login if user logs out
            LaunchedEffect(authState) {
                if (authState is AuthState.LoggedOut) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            }
            RoleSelectionScreen(
                onLogout = { authViewModel.logout() },
                onDebugMesh = { navController.navigate(Screen.MeshTest.route) },
                onRoleSelected = { role ->
                    when (role) {
                        "VICTIM" -> navController.navigate(Screen.VictimHome.route)
                        "VOLUNTEER" -> navController.navigate(Screen.VolunteerHome.route)
                    }
                }
            )
        }

        composable(Screen.VictimHome.route) {
            VictimHomeScreen(
                onTriggerSOS = {
                    navController.navigate(Screen.ReportSubmission.route)
                },
                onViewHistory = {
                    navController.navigate(Screen.ReportHistory.route)
                },
                onSwitchRole = {
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.VolunteerHome.route) {
            VolunteerHomeScreen {
                navController.navigate(Screen.RoleSelection.route) {
                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                }
            }
        }

        composable(Screen.ReportSubmission.route) {
            ReportSubmissionScreen(
                viewModel = reportViewModel,
                currentUserId = authViewModel.getCurrentUserId(),
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ReportHistory.route) {
            ReportHistoryScreen(
                viewModel = reportViewModel,
                currentUserId = authViewModel.getCurrentUserId(),
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.MeshTest.route) {
            val meshViewModel: MeshTestViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return MeshTestViewModel(
                            transport = meshTransport,
                            identityProvider = meshIdentityProvider,
                            relayEngine = meshRelayEngine
                        ) as T
                    }
                }
            )
            MeshTestScreen(
                viewModel = meshViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
