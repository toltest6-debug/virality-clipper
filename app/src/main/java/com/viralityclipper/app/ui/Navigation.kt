package com.viralityclipper.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.viralityclipper.app.data.AppContainer
import com.viralityclipper.app.ui.theme.LocalViralityColors

object Route {
    const val home = "home"
    const val saved = "saved"
    const val settings = "settings"
    const val job = "job/{jobId}"

    fun job(jobId: String) = "job/$jobId"
}

@Composable
fun ViralityClipperRoot(container: AppContainer) {
    val navController = rememberNavController()
    val colors = LocalViralityColors.current
    NavHost(
        navController = navController,
        startDestination = Route.home,
        modifier = Modifier.fillMaxSize(),
        builder = {
            composable(Route.home) {
                HomeScreen(container = container, navController = navController)
            }
            composable(Route.saved) {
                SavedShortsScreen(container = container, navController = navController)
            }
            composable(Route.settings) {
                SettingsScreen(container = container, navController = navController)
            }
            composable(Route.job) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                JobScreen(container = container, jobId = jobId, navController = navController)
            }
        }
    )
}
