package com.example.rsq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rsq.ui.home.VictimHomeScreen
import com.example.rsq.ui.home.VolunteerHomeScreen
import com.example.rsq.ui.role.RoleSelectionScreen
import com.example.rsq.ui.volunteer.VolunteerDashboardScreen
import com.example.rsq.ui.dashboard.AuthorityDashboardScreen
import com.example.rsq.ui.response.AssignmentScreen
import com.example.rsq.ui.donation.DonationScreen
import com.example.rsq.ui.notification.NotificationScreen

sealed class Screen(val route: String) {
    object RoleSelection : Screen("role_selection")
    object VictimHome : Screen("victim_home")
    object VolunteerHome : Screen("volunteer_home")
    object VolunteerDashboard : Screen("volunteer_dashboard")
    object AuthorityDashboard : Screen("authority_dashboard")
    object Assignment : Screen("assignment_screen")
    object Donation : Screen("donation_screen")
    object Notification : Screen("notification_screen")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.RoleSelection.route,
    ) {
        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen { role ->
                when (role) {
                    "VICTIM" -> navController.navigate(Screen.VictimHome.route)
                    "VOLUNTEER" -> navController.navigate(Screen.VolunteerHome.route)
                }
            }
        }
        
        composable(Screen.VictimHome.route) {
            VictimHomeScreen {
                navController.navigate(Screen.RoleSelection.route) {
                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                }
            }
        }
        
        composable(Screen.VolunteerHome.route) {
            VolunteerHomeScreen {
                // Modified entry point to go to Dashboard instead of just role selection
                navController.navigate(Screen.VolunteerDashboard.route)
            }
        }
        
        // Integrated New Modules with Improved Navigation Flow
        composable(Screen.VolunteerDashboard.route) {
            VolunteerDashboardScreen(
                onBack = { navController.popBackStack() },
                onNavigateToNotifications = { navController.navigate(Screen.Notification.route) },
                onNavigateToAssignments = { navController.navigate(Screen.Assignment.route) }
            )
        }
        
        composable(Screen.AuthorityDashboard.route) {
            AuthorityDashboardScreen(
                onBack = { navController.popBackStack() },
                onNavigateToNotifications = { navController.navigate(Screen.Notification.route) },
                onNavigateToDonations = { navController.navigate(Screen.Donation.route) }
            )
        }
        
        composable(Screen.Assignment.route) {
            AssignmentScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Donation.route) {
            DonationScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Notification.route) {
            NotificationScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
