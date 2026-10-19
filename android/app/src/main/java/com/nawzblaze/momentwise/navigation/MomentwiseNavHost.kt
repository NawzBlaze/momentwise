package com.nawzblaze.momentwise.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nawzblaze.momentwise.MomentwiseViewModel
import com.nawzblaze.momentwise.ui.screens.AppsScreen
import com.nawzblaze.momentwise.ui.screens.ConfigureRuleScreen
import com.nawzblaze.momentwise.ui.screens.DisclosureScreen
import com.nawzblaze.momentwise.ui.screens.HomeScreen
import com.nawzblaze.momentwise.ui.screens.OpeningNoticePreviewScreen
import com.nawzblaze.momentwise.ui.screens.ReminderPreviewScreen
import com.nawzblaze.momentwise.ui.screens.SettingsScreen

/**
 * Navigation graph for the primary screens, mirroring the approved prototype:
 * Welcome, Home, Apps, Configure Rule, Opening-notice preview, Reminder preview,
 * and Settings.
 */
@Composable
fun MomentwiseNavHost(
    navController: NavHostController,
    viewModel: MomentwiseViewModel,
    startDestination: String,
    snackbarHostState: SnackbarHostState,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Destinations.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateApps = { navController.navigate(Destinations.APPS) },
                onNavigateSettings = { navController.navigate(Destinations.SETTINGS) },
                onTestOpeningNotice = { appId ->
                    navController.navigate(Destinations.previewOpening(appId))
                },
                onTestReminder = { appId ->
                    navController.navigate(Destinations.previewReminder(appId))
                },
                onConfigureRule = { appId ->
                    navController.navigate(Destinations.configure(appId))
                },
            )
        }

        composable(Destinations.APPS) {
            AppsScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                onConfigureRule = { appId ->
                    navController.navigate(Destinations.configure(appId))
                },
                onRequestGuard = { appId ->
                    navController.navigate(Destinations.guard(appId))
                },
            )
        }

        composable(
            route = Destinations.GUARD,
            arguments = listOf(navArgument(Destinations.GUARD_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(Destinations.GUARD_ARG).orEmpty()
            DisclosureScreen(
                viewModel = viewModel,
                appId = appId,
                onGuarded = { firstTime ->
                    navController.popBackStack()
                    if (firstTime) {
                        navController.navigate(Destinations.configure(appId))
                    }
                },
                onDecline = { navController.popBackStack() },
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
            )
        }

        composable(
            route = Destinations.CONFIGURE,
            arguments = listOf(navArgument(Destinations.CONFIGURE_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(Destinations.CONFIGURE_ARG).orEmpty()
            ConfigureRuleScreen(
                viewModel = viewModel,
                appId = appId,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onTestPreview = { navController.navigate(Destinations.previewOpening(appId)) },
            )
        }

        composable(
            route = Destinations.PREVIEW_OPENING,
            arguments = listOf(navArgument(Destinations.PREVIEW_OPENING_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(Destinations.PREVIEW_OPENING_ARG).orEmpty()
            OpeningNoticePreviewScreen(
                viewModel = viewModel,
                appId = appId,
                snackbarHostState = snackbarHostState,
                onExit = { navController.popBackStack() },
                onEditRule = { navController.navigate(Destinations.configure(appId)) },
                onQuitResultBack = {
                    navController.navigate(Destinations.APPS) {
                        popUpTo(Destinations.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onContinueResultBackHome = {
                    navController.popBackStack(Destinations.HOME, inclusive = false)
                },
                onTestCheckIn = { navController.navigate(Destinations.previewReminder(appId)) },
            )
        }

        composable(
            route = Destinations.PREVIEW_REMINDER,
            arguments = listOf(navArgument(Destinations.PREVIEW_REMINDER_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(Destinations.PREVIEW_REMINDER_ARG).orEmpty()
            ReminderPreviewScreen(
                viewModel = viewModel,
                appId = appId,
                snackbarHostState = snackbarHostState,
                onExitToHome = {
                    navController.popBackStack(Destinations.HOME, inclusive = false)
                },
                onEditReminderSchedule = { navController.navigate(Destinations.configure(appId)) },
            )
        }
    }
}
