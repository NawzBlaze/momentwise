package com.nawzblaze.momentwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nawzblaze.momentwise.navigation.Destinations
import com.nawzblaze.momentwise.navigation.MomentwiseNavHost
import com.nawzblaze.momentwise.ui.theme.MomentwiseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MomentwiseApplication).container

        setContent {
            val viewModel: MomentwiseViewModel =
                viewModel(factory = MomentwiseViewModel.factory(container))
            MomentwiseRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun MomentwiseRoot(viewModel: MomentwiseViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    MomentwiseTheme(themeMode = themeMode) {
        val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()

        // Wait for the first DataStore read so the start destination is stable.
        if (onboardingCompleted == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
            return@MomentwiseTheme
        }

        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showWelcome = onboardingCompleted == false
        // Welcome is an overlay, so Home stays the graph root and the bottom
        // nav can always pop back to it.
        val showBottomNav = !showWelcome && currentRoute in Destinations.BOTTOM_NAV_ROUTES

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomNav) {
                    MomentwiseBottomNav(navController = navController, currentRoute = currentRoute)
                }
            },
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding).fillMaxSize()) {
                MomentwiseNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    startDestination = Destinations.HOME,
                )
                if (showWelcome) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                    )
                    com.nawzblaze.momentwise.ui.screens.WelcomeScreen(
                        onGetStarted = {
                            viewModel.completeOnboarding()
                            navController.navigate(Destinations.APPS) {
                                launchSingleTop = true
                            }
                        },
                        onGoToOverview = { viewModel.completeOnboarding() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentwiseBottomNav(
    navController: NavHostController,
    currentRoute: String?,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf(
            NavItem(Destinations.HOME, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
            NavItem(Destinations.APPS, R.string.nav_apps, Icons.Outlined.GridView, Icons.Filled.GridView),
            NavItem(Destinations.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
        )
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(Destinations.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(item.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)
