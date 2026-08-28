package com.example.rsq.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.rsq.auth.ui.EmailVerificationScreen
import com.example.rsq.auth.ui.ForgotPasswordScreen
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
import com.example.rsq.reporting.ui.screens.ReportDetailScreen
import com.example.rsq.reporting.viewmodel.ReportViewModel
import com.example.rsq.ui.dashboard.AuthorityDashboardScreen
import com.example.rsq.ui.viewmodel.*
import com.example.rsq.ui.donation.DonationScreen
import com.example.rsq.data.repository.*
import com.example.rsq.ui.home.VictimHomeScreen
import com.example.rsq.ui.home.VolunteerHomeScreen
import com.example.rsq.ui.notification.NotificationScreen
import com.example.rsq.ui.profile.ProfileScreen
import com.example.rsq.ui.permission.PermissionScreen
import com.example.rsq.ui.response.AssignmentScreen
import com.example.rsq.ui.role.RoleSelectionScreen
import com.example.rsq.ui.volunteer.VolunteerDashboardScreen

sealed class Screen(val route: String) {
    object Permissions : Screen("permissions")
    object Login : Screen("login")
    object Register : Screen("register")
    object EmailVerification : Screen("email_verification")
    object ForgotPassword : Screen("forgot_password")
    object RoleSelection : Screen("role_selection")

    object VictimHome : Screen("victim_home")
    object VolunteerHome : Screen("volunteer_home")
    object Profile : Screen("profile")

    object ReportSubmission : Screen("report_submission")
    object ReportHistory : Screen("report_history")
    data class ReportDetail(val reportId: String) : Screen("report_detail/$reportId") {
        companion object {
            const val routePattern = "report_detail/{reportId}"
        }
    }
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

    // Mesh dependencies
    val meshIdentityProvider = remember { NodeIdentityRepository(context) }
    val meshMessageRepository = remember { LocalMeshMessageRepository(context) }
    val meshTransport = remember { NearbyMeshTransport(context, meshIdentityProvider) }

    // Local database
    val localReportDatabase = remember { LocalReportDatabase.getDatabase(context) }
    val localReportRepository = remember { LocalReportRepository(localReportDatabase.reportDao()) }

    // Repositories
    val assignmentRepository = remember { AssignmentRepositoryImpl(localReportDatabase.assignmentDao()) }
    val volunteerRepository = remember { VolunteerRepositoryImpl(localReportDatabase.volunteerDao(), assignmentRepository) }
    val notificationRepository = remember { NotificationRepositoryImpl(localReportDatabase.notificationDao()) }

    // Shared ViewModels
    val authViewModel: AuthViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(
                    volunteerRepository = volunteerRepository
                ) as T
            }
        }
    )

    // Lifecycle-aware relay engine
    val meshRelayEngine = remember {
        MeshRelayEngine(
            transport = meshTransport,
            repository = meshMessageRepository,
            identityProvider = meshIdentityProvider,
            scope = authViewModel.internalScope
        )
    }

    // Report ViewModel
    val reportViewModel: ReportViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
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
    val userProfile by authViewModel.currentUserProfile.collectAsState()

    // Session check
    LaunchedEffect(Unit) {
        authViewModel.checkSession()
    }

    // Role-based auto-navigation after session restore or login
    LaunchedEffect(userProfile) {
        userProfile?.let { profile ->
            if (profile.role.isNotBlank()) {
                val target = when (profile.role) {
                    "VICTIM" -> Screen.VictimHome.route
                    "VOLUNTEER" -> Screen.VolunteerHome.route
                    "AUTHORITY" -> Screen.AuthorityDashboard.route
                    else -> null
                }
                target?.let {
                    navController.navigate(it) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                }
            }
        }
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
                        if (state.message == "Login successful" || state.message == "Session restored" || state.message == "Google sign-in successful") {
                            // If role is missing, go to selection, otherwise auto-nav handled above
                            if (userProfile?.role.isNullOrBlank()) {
                                navController.navigate(Screen.RoleSelection.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
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
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                },
                onNavigateToVerification = {
                    navController.navigate(Screen.EmailVerification.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToVerification = {
                    navController.navigate(Screen.EmailVerification.route)
                },
                onNavigateBackToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.EmailVerification.route) {
            EmailVerificationScreen(
                viewModel = authViewModel,
                onBackToLogin = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = false }
                    }
                },
                onVerificationSuccess = {
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ---------------------------------------------------------
        // Role Selection
        // ---------------------------------------------------------

        composable(Screen.RoleSelection.route) {
            // Handle logout
            LaunchedEffect(authState) {
                if (authState is AuthState.LoggedOut) {
                    navController.navigate(Screen.Login.route) { popUpTo(Screen.RoleSelection.route) { inclusive = true } }
                }
            }

            RoleSelectionScreen(
                isAuthorized = userProfile?.isAuthorized ?: false,
                onLogout = { authViewModel.logout() },
                onRoleSelected = { role ->
                    authViewModel.selectRole(role)
                    when (role) {
                        "VICTIM" -> navController.navigate(Screen.VictimHome.route)
                        "VOLUNTEER" -> navController.navigate(Screen.VolunteerHome.route)
                        "AUTHORITY" -> {
                            if (userProfile?.isAuthorized == true) {
                                navController.navigate(Screen.AuthorityDashboard.route)
                            }
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
                onTriggerSOS = { navController.navigate(Screen.ReportSubmission.route) },
                onViewHistory = { navController.navigate(Screen.ReportHistory.route) },
                onOpenProfile = { navController.navigate(Screen.Profile.route) },
                onOpenDonations = { navController.navigate(Screen.Donation.route) },
                onSwitchRole = {
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = authViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToHistory = { navController.navigate(Screen.ReportHistory.route) },
                onNavigateToAssignments = { navController.navigate(Screen.Assignment.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notification.route) },
                onLogout = { 
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
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
                },
                onOpenProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onViewReport = { reportId ->
                    navController.navigate(Screen.ReportDetail(reportId).route)
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

        composable(
            route = Screen.ReportDetail.routePattern,
            arguments = listOf(androidx.navigation.navArgument("reportId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            ReportDetailScreen(
                reportId = reportId,
                viewModel = reportViewModel,
                onBack = { navController.popBackStack() }
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
            val volunteerViewModel: VolunteerViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return VolunteerViewModel(
                            firebaseUid = authViewModel.getCurrentUserId(),
                            volunteerRepository = volunteerRepository,
                            assignmentRepository = assignmentRepository,
                            notificationRepository = notificationRepository
                        ) as T
                    }
                }
            )

            VolunteerDashboardScreen(
                viewModel = volunteerViewModel,
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

        composable(Screen.AuthorityDashboard.route) {
            val authorityViewModel: AuthorityViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return AuthorityViewModel(
                            firebaseUid = authViewModel.getCurrentUserId(),
                            assignmentRepository = assignmentRepository,
                            volunteerRepository = volunteerRepository,
                            notificationRepository = notificationRepository,
                            localReportRepository = localReportRepository,
                            reportRepository = ReportRepository(),
                            authorityRepository = AuthorityRepositoryImpl(
                                assignmentRepository = assignmentRepository,
                                donationRepository = DonationRepositoryImpl(),
                                localReportRepository = localReportRepository
                            )
                        ) as T
                    }
                }
            )

            AuthorityDashboardScreen(
                viewModel = authorityViewModel,
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
            val assignmentViewModel: AssignmentViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return AssignmentViewModel(repository = assignmentRepository) as T
                    }
                }
            )

            AssignmentScreen(
                viewModel = assignmentViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ---------------------------------------------------------
        // Donations
        // ---------------------------------------------------------

        composable(Screen.Donation.route) {
            val donationViewModel: DonationViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return DonationViewModel(repository = DonationRepositoryImpl()) as T
                    }
                }
            )
            DonationScreen(
                viewModel = donationViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onMakeDonation = { amount ->
                    donationViewModel.makeDonation(amount, userProfile?.name ?: "Anonymous")
                }
            )
        }

        // ---------------------------------------------------------
        // Notifications
        // ---------------------------------------------------------

        composable(Screen.Notification.route) {
            val volunteerIdFlow = remember { volunteerRepository.getVolunteerData(authViewModel.getCurrentUserId()) }
            val volunteer by volunteerIdFlow.collectAsState(initial = null)

            if (volunteer != null) {
                val notificationViewModel: NotificationViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return NotificationViewModel(
                                recipientId = volunteer!!.id,
                                repository = notificationRepository
                            ) as T
                        }
                    }
                )

                NotificationScreen(
                    viewModel = notificationViewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            } else {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}