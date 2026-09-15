package com.example.financeplanner.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.financeplanner.ui.screens.detail.TransactionDetailScreen
import com.example.financeplanner.ui.screens.home.HomeScreen
import com.example.financeplanner.ui.screens.payments.PaymentsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Payments : Screen("payments")
    object Detail : Screen("detail/{transactionId}") {
        fun createRoute(id: Long) = "detail/$id"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.History.route,
        Screen.Payments.route
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = Color(0xFF1E1B4B).copy(alpha = 0.95f),
                        contentColor = Color.White,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == Screen.Home.route,
                            onClick = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                            label = { Text("Главная") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.History.route,
                            onClick = {
                                navController.navigate(Screen.History.route) {
                                    popUpTo(Screen.Home.route)
                                }
                            },
                            icon = { Icon(Icons.Default.List, contentDescription = "История") },
                            label = { Text("История") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        NavigationBarItem(
                            selected = currentRoute == Screen.Payments.route,
                            onClick = {
                                navController.navigate(Screen.Payments.route) {
                                    popUpTo(Screen.Home.route)
                                }
                            },
                            icon = { Icon(Icons.Default.Add, contentDescription = "Платежи") },
                            label = { Text("Платежи") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onTransactionClick = { id ->
                            navController.navigate(Screen.Detail.createRoute(id))
                        },
                        onSeeAllClick = {
                            navController.navigate(Screen.History.route)
                        },
                        isHistoryMode = false
                    )
                }
                composable(Screen.History.route) {
                    HomeScreen(
                        onTransactionClick = { id ->
                            navController.navigate(Screen.Detail.createRoute(id))
                        },
                        isHistoryMode = true
                    )
                }
                composable(Screen.Payments.route) {
                    PaymentsScreen()
                }
                composable(
                    route = Screen.Detail.route,
                    arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("transactionId") ?: 0L
                    TransactionDetailScreen(
                        transactionId = id,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}