package com.mohdshayan.snarewall.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mohdshayan.snarewall.ui.board.BoardScreen
import com.mohdshayan.snarewall.ui.daily.DailyScreen
import com.mohdshayan.snarewall.ui.home.HomeScreen
import com.mohdshayan.snarewall.ui.levels.LevelsScreen
import com.mohdshayan.snarewall.ui.result.ResultScreen
import com.mohdshayan.snarewall.ui.settings.LicencesScreen
import com.mohdshayan.snarewall.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object Home
@Serializable object Levels
@Serializable object Daily
@Serializable object Settings
@Serializable object Licences

/** A run. [levelId] 0 with [daily] true is a daily map: [dateKey], or today's when null. [fresh] ignores any saved run. */
@Serializable
data class Board(val levelId: Int, val difficulty: String, val daily: Boolean, val fresh: Boolean, val dateKey: String? = null)

@Serializable
data class Result(
    val levelId: Int,
    val difficulty: String,
    val daily: Boolean,
    val won: Boolean,
    val score: Int,
    val wave: Int,
    val totalWaves: Int,
    val kills: Int,
    val hearts: Int,
    val medal: String,
    val newBest: Boolean,
    val silver: Int,
    val gold: Int,
    val dateKey: String? = null,
)

@Composable
fun AppNav(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onPlay = { navController.navigate(it) },
                onLevels = { navController.navigate(Levels) },
                onDaily = { navController.navigate(Daily) },
                onSettings = { navController.navigate(Settings) },
            )
        }
        composable<Levels> {
            LevelsScreen(
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(it) },
            )
        }
        composable<Board> { entry ->
            val args = entry.toRoute<Board>()
            BoardScreen(
                args = args,
                onQuit = { navController.popBackStack(Home, inclusive = false) },
                onFinished = { result ->
                    navController.navigate(result) {
                        popUpTo<Board> { inclusive = true }
                    }
                },
            )
        }
        composable<Result> { entry ->
            ResultScreen(
                args = entry.toRoute<Result>(),
                onPlay = { board ->
                    navController.navigate(board) { popUpTo<Result> { inclusive = true } }
                },
                onHome = { navController.popBackStack(Home, inclusive = false) },
                onLevels = {
                    navController.navigate(Levels) { popUpTo(Home) }
                },
            )
        }
        composable<Daily> {
            DailyScreen(onBack = { navController.popBackStack() }, onPlay = { navController.navigate(it) })
        }
        composable<Settings> {
            SettingsScreen(onBack = { navController.popBackStack() }, onLicences = { navController.navigate(Licences) })
        }
        composable<Licences> {
            LicencesScreen(onBack = { navController.popBackStack() })
        }
    }
}
