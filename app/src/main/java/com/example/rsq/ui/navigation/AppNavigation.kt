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
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.example.rsq.location.data.LocationRepository
import com.example.rsq.location.viewmodel.LocationViewModel
import com.google.android.gms.location.LocationServices
import com.example.rsq.ui.role.RoleSelectionScreen
import com.example.rsq.ui.volunteer.VolunteerDashboardScreen
import com.example.rsq.util.NetworkConnectivityObserver
import com.example.rsq.util.ConnectivityObserver
import kotlinx.coroutines.launch

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

@OptIn(androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi::class)
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Connectivity
    val connectivityObserver = remember { NetworkConnectivityObserver(context) }

    // Mesh dependencies
    val meshIdentityProvider = remember { NodeIdentityRepository(context) }
    val meshMessageRepository = remember { LocalMeshMessageRepository(context) }
    val meshTransport = remember { NearbyMeshTransport(context, meshIdentityProvider) }

    // Local database
    val localReportDatabase = remember { LocalReportDatabase.getDatabase(context) }
    val localReportRepository = remember { LocalReportRepository(localReportDatabase.reportDao()) }

    // Repositories
    val donationRepository = remember { FirestoreDonationRepository() }
    val assignmentRepository = remember { AssignmentRepositoryImpl(localReportDatabase.assignmentDao()) }
    val volunteerRepository = remember { VolunteerRepositoryImpl(localReportDatabase.volunteerDao(), assignmentRepository) }
    val notificationRepository = remember { NotificationRepositoryImpl(localReportDatabase.notificationDao()) }

    // Global Location Management
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationRepository = remember { LocationRepository(context, fusedLocationClient) }
    val locationViewModel: LocationViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LocationViewModel(locationRepository)
            }
        }
    )

    // Shared ViewModels
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AuthViewModel(
                    volunteerRepository = volunteerRepository
                )
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
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val savedStateHandle = extras.createSavedStateHandle()
                @Suppress("UNCHECKED_CAST")
                return ReportViewModel(
                    application = context.applicationContext as android.app.Application,
                    savedStateHandle = savedStateHandle,
                    repository = ReportRepository(),
                    localRepository = localReportRepository,
                    connectivityObserver = connectivityObserver,
                    relayEngine = meshRelayEngine,
                    identityProvider = meshIdentityProvider
                ) as T
            }
        }
    )

    val authState by authViewModel.authState.collectAsState()
    val userProfile by authViewModel.currentUserProfile.collectAsState()
    val locationState by locationViewModel.locationState.collectAsState()

    // Session check
    LaunchedEffect(Unit) {
        authViewModel.checkSession()
    }

    // Role-based auto-navigation after session restore or login
    LaunchedEffect(userProfile, currentRoute) {
        val isAtGate = currentRoute == null ||
                      currentRoute == Screen.Login.route ||
                      currentRoute == Screen.Permissions.route ||
                      currentRoute == Screen.RoleSelection.route ||
                      currentRoute == Screen.Register.route

        if (isAtGate) {
            userProfile?.let { profile ->
                if (profile.role.isNotBlank()) {
                    val target = when (profile.role) {
                        "VICTIM" -> Screen.VictimHome.route
                        "VOLUNTEER" -> Screen.VolunteerHome.route
                        "AUTHORITY" -> Screen.AuthorityDashboard.route
                        else -> null
                    }
                    if (target != null && target != currentRoute) {
                        navController.navigate(target) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            popUpTo(Screen.Permissions.route) { inclusive = true }
                        }
                    }
                }
            }
        }
    }

    // Unified Mesh + Location lifecycle management
    LaunchedEffect(userProfile != null) {
        if (userProfile != null) {
            // Start background location tracking
            locationViewModel.fetchLocation()
            launch {
                locationViewModel.locationReadiness.collect { }
            }

            // Start Mesh Communication Service automatically
            meshTransport.start()
        } else {
            meshTransport.stop()
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
        // Role Selection (RSQ HOME)
        // ---------------------------------------------------------

        composable(Screen.RoleSelection.route) {
            LaunchedEffect(authState) {
                if (authState is AuthState.LoggedOut) {
                    navController.navigate(Screen.Login.route) { popUpTo(Screen.RoleSelection.route) { inclusive = true } }
                }
            }

            RoleSelectionScreen(
                isAuthorized = userProfile?.isAuthorized ?: false,
                locationState = locationState,
                onLogout = { authViewModel.logout() },
                onOpenProfile = { navController.navigate(Screen.Profile.route) },
                onOpenDonations = { navController.navigate(Screen.Donation.route) },
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
                onBack = { navController.popBackStack() }
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
                onBack = {
                    navController.popBackStack()
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
                locationViewModel = locationViewModel,
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
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
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
                factory = viewModelFactory {
                    initializer {
                        MeshTestViewModel(
                            transport = meshTransport,
                            identityProvider = meshIdentityProvider,
                            relayEngine = meshRelayEngine
                        )
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
                factory = viewModelFactory {
                    initializer {
                        VolunteerViewModel(
                            firebaseUid = authViewModel.getCurrentUserId(),
                            volunteerRepository = volunteerRepository,
                            assignmentRepository = assignmentRepository,
                            notificationRepository = notificationRepository
                        )
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
                factory = viewModelFactory {
                    initializer {
                        AuthorityViewModel(
                            firebaseUid = authViewModel.getCurrentUserId(),
                            assignmentRepository = assignmentRepository,
                            volunteerRepository = volunteerRepository,
                            notificationRepository = notificationRepository,
                            localReportRepository = localReportRepository,
                            reportRepository = ReportRepository(),
                            authorityRepository = AuthorityRepositoryImpl(
                                assignmentRepository = assignmentRepository,
                                donationRepository = donationRepository,
                                localReportRepository = localReportRepository
                            )
                        )
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
                factory = viewModelFactory {
                    initializer {
                        AssignmentViewModel(repository = assignmentRepository)
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
                factory = viewModelFactory {
                    initializer {
                        DonationViewModel(repository = donationRepository)
                    }
                }
            )
            DonationScreen(
                viewModel = donationViewModel,
                userName = userProfile?.name ?: "Anonymous",
                userId = authViewModel.getCurrentUserId(),
                userEmail = userProfile?.email ?: "",
                onBack = {
                    navController.popBackStack()
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
                    factory = viewModelFactory {
                        initializer {
                            NotificationViewModel(
                                recipientId = volunteer!!.id,
                                repository = notificationRepository
                            )
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
