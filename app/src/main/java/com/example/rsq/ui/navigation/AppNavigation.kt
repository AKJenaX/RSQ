package com.example.rsq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rsq.ui.home.VictimHomeScreen
import com.example.rsq.ui.home.VolunteerHomeScreen
import com.example.rsq.ui.role.RoleSelectionScreen

sealed class Screen(val route: String) {
    object RoleSelection : Screen("role_selection")
    object VictimHome : Screen("victim_home")
    object VolunteerHome : Screen("volunteer_home")
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
                navController.navigate(Screen.RoleSelection.route) {
                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                }
            }
        }
    }
}
