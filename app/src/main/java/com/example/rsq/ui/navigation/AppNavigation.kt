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
import com.example.rsq.reporting.data.LocalReportRepository
import com.example.rsq.reporting.data.ReportRepository
import com.example.rsq.reporting.data.local.LocalReportDatabase
import com.example.rsq.reporting.ui.ReportHistoryScreen
import com.example.rsq.reporting.ui.ReportSubmissionScreen
import com.example.rsq.reporting.viewmodel.ReportViewModel
import com.example.rsq.ui.dashboard.AuthorityDashboardScreen
import com.example.rsq.ui.donation.DonationScreen
import com.example.rsq.ui.home.VictimHomeScreen
import com.example.rsq.ui.home.VolunteerHomeScreen
import com.example.rsq.ui.notification.NotificationScreen
import com.example.rsq.ui.permission.PermissionScreen
import com.example.rsq.ui.response.AssignmentScreen
import com.example.rsq.ui.role.RoleSelectionScreen
import com.example.rsq.ui.volunteer.VolunteerDashboardScreen

sealed class Screen(val route: String) {
    object Permissions : Screen("permissions")
    object Login : Screen("login")
    object Register : Screen("register")
    object RoleSelection : Screen("role_selection")

    object VictimHome : Screen("victim_home")
    object VolunteerHome : Screen("volunteer_home")

    object ReportSubmission : Screen("report_submission")
    object ReportHistory : Screen("report_history")
    object MeshTest : Screen("mesh_test")

    object VolunteerDashboard : Screen("volunteer_dashboard")
    object AuthorityDashboard : Screen("authority_dashboard")
    object Assignment : Screen("assignment_screen")
    object Donation : Screen("donation_screen")
    object Notification : Screen("notification_screen")
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()

    // Mesh dependencies
    val meshIdentityProvider = remember {
        NodeIdentityRepository(context)
    }

    val meshMessageRepository = remember {
        LocalMeshMessageRepository(context)
    }

    val meshTransport = remember {
        NearbyMeshTransport(context, meshIdentityProvider)
    }

    // Lifecycle-aware relay engine
    val meshRelayEngine = remember {
        MeshRelayEngine(
            transport = meshTransport,
            repository = meshMessageRepository,
            identityProvider = meshIdentityProvider,
            scope = authViewModel.internalScope
        )
    }

    // Local report database/repository
    val localReportDatabase = remember {
        LocalReportDatabase.getDatabase(context)
    }

    val localReportRepository = remember {
        LocalReportRepository(localReportDatabase.reportDao())
    }

    // Report ViewModel
    val reportViewModel: ReportViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                @Suppress("UNCHECKED_CAST")
                return ReportViewModel(
                    application = context.applicationContext as android.app.Application,
                    repository = ReportRepository(),
                    localRepository = localReportRepository,
                    relayEngine = meshRelayEngine,
                    identityProvider = meshIdentityProvider
                ) as T
            }
        }
    )

    val authState by authViewModel.authState.collectAsState()

    // Session check on app launch
    LaunchedEffect(Unit) {
        authViewModel.checkSession()
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Permissions.route
    ) {

        // ---------------------------------------------------------
        // Permissions
        // ---------------------------------------------------------

        composable(Screen.Permissions.route) {
            PermissionScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Permissions.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // ---------------------------------------------------------
        // Authentication
        // ---------------------------------------------------------

        composable(Screen.Login.route) {
            LaunchedEffect(authState) {
                when (val state = authState) {
                    is AuthState.Success -> {
                        if (
                            state.message == "Login successful" ||
                            state.message == "Session restored"
                        ) {
                            navController.navigate(Screen.RoleSelection.route) {
                                popUpTo(Screen.Login.route) {
                                    inclusive = true
                                }
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
            LaunchedEffect(authState) {
                when (val state = authState) {
                    is AuthState.Success -> {
                        if (state.message == "Registration successful") {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Register.route) {
                                    inclusive = true
                                }
                            }

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

        // ---------------------------------------------------------
        // Role Selection
        // ---------------------------------------------------------

        composable(Screen.RoleSelection.route) {

            // Handle logout
            LaunchedEffect(authState) {
                if (authState is AuthState.LoggedOut) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.RoleSelection.route) {
                            inclusive = true
                        }
                    }
                }
            }

            RoleSelectionScreen(
                onLogout = {
                    authViewModel.logout()
                },

                onDebugMesh = {
                    navController.navigate(Screen.MeshTest.route)
                },

                onRoleSelected = { role ->
                    when (role) {
                        "VICTIM" -> {
                            navController.navigate(Screen.VictimHome.route)
                        }

                        "VOLUNTEER" -> {
                            navController.navigate(Screen.VolunteerHome.route)
                        }

                        "AUTHORITY" -> {
                            navController.navigate(Screen.AuthorityDashboard.route)
                        }
                    }
                }
            )
        }

        // ---------------------------------------------------------
        // Victim
        // ---------------------------------------------------------

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
                        popUpTo(Screen.RoleSelection.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // ---------------------------------------------------------
        // Volunteer
        // ---------------------------------------------------------

        composable(Screen.VolunteerHome.route) {
            VolunteerHomeScreen(
                reportViewModel = reportViewModel,
                onOpenDashboard = {
                    navController.navigate(Screen.VolunteerDashboard.route)
                }
            )
        }

        // ---------------------------------------------------------
        // Reporting
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // Mesh testing
        // ---------------------------------------------------------

        composable(Screen.MeshTest.route) {
            val meshViewModel: MeshTestViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(
                        modelClass: Class<T>
                    ): T {
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

        // ---------------------------------------------------------
        // Volunteer Dashboard
        // ---------------------------------------------------------

        composable(Screen.VolunteerDashboard.route) {
            VolunteerDashboardScreen(
                onBack = {
                    navController.popBackStack()
                },

                onNavigateToNotifications = {
                    navController.navigate(Screen.Notification.route)
                },

                onNavigateToAssignments = {
                    navController.navigate(Screen.Assignment.route)
                }
            )
        }

        // ---------------------------------------------------------
        // Authority Dashboard
        // ---------------------------------------------------------

        composable(Screen.AuthorityDashboard.route) {
            AuthorityDashboardScreen(
                onBack = {
                    navController.popBackStack()
                },

                onNavigateToNotifications = {
                    navController.navigate(Screen.Notification.route)
                },

                onNavigateToDonations = {
                    navController.navigate(Screen.Donation.route)
                }
            )
        }

        // ---------------------------------------------------------
        // Assignments
        // ---------------------------------------------------------

        composable(Screen.Assignment.route) {
            AssignmentScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ---------------------------------------------------------
        // Donations
        // ---------------------------------------------------------

        composable(Screen.Donation.route) {
            DonationScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ---------------------------------------------------------
        // Notifications
        // ---------------------------------------------------------

        composable(Screen.Notification.route) {
            NotificationScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}