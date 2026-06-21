package com.example.rsq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rsq.auth.model.AuthState
import com.example.rsq.auth.ui.LoginScreen
import com.example.rsq.auth.ui.RegisterScreen
import com.example.rsq.auth.viewmodel.AuthViewModel
import com.example.rsq.ui.home.VictimHomeScreen
import com.example.rsq.ui.home.VolunteerHomeScreen
import com.example.rsq.ui.role.RoleSelectionScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object RoleSelection : Screen("role_selection")
    object VictimHome : Screen("victim_home")
    object VolunteerHome : Screen("volunteer_home")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
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
                onRoleSelected = { role ->
                    when (role) {
                        "VICTIM" -> navController.navigate(Screen.VictimHome.route)
                        "VOLUNTEER" -> navController.navigate(Screen.VolunteerHome.route)
                    }
                }
            )
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
