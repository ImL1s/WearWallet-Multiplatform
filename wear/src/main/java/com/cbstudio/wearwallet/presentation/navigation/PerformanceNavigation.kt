package com.cbstudio.wearwallet.presentation.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.wear.compose.navigation.composable
import com.cbstudio.wearwallet.presentation.screens.performance.PerformanceMonitorScreen
import com.cbstudio.wearwallet.presentation.screens.performance.PerformanceViewModel

/**
 * 效能監控導航路徑
 */
object PerformanceRoute {
    const val PERFORMANCE_MONITOR = "performance_monitor"
}

/**
 * 效能監控導航圖
 */
fun NavGraphBuilder.performanceNavigation(
    navController: NavController
) {
    composable(PerformanceRoute.PERFORMANCE_MONITOR) {
        val viewModel: PerformanceViewModel = viewModel()
        PerformanceMonitorScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() }
        )
    }
}

/**
 * 導航到效能監控畫面
 */
fun NavController.navigateToPerformanceMonitor() {
    navigate(PerformanceRoute.PERFORMANCE_MONITOR)
}