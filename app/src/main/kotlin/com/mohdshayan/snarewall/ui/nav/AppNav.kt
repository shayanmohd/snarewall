package com.mohdshayan.snarewall.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.game.Access
import com.mohdshayan.snarewall.ui.board.BoardScreen
import com.mohdshayan.snarewall.ui.daily.DailyScreen
import com.mohdshayan.snarewall.ui.home.HomeScreen
import com.mohdshayan.snarewall.ui.levels.LevelsScreen
import com.mohdshayan.snarewall.ui.result.ResultScreen
import com.mohdshayan.snarewall.ui.settings.LicencesScreen
import com.mohdshayan.snarewall.ui.settings.SettingsScreen
import com.mohdshayan.snarewall.ui.unlock.UnlockScreen
import kotlinx.serialization.Serializable

@Serializable object Home
@Serializable object Levels
@Serializable object Daily
@Serializable object Settings
@Serializable object Licences
@Serializable object Unlock

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
                onUnlock = { navController.navigate(Unlock) },
            )
        }
        composable<Levels> {
            LevelsScreen(
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(it) },
                onUnlock = { navController.navigate(Unlock) },
            )
        }
        // The only enforcement point: every start goes through this route (the briefing, Home, Result's
        // buttons, the Daily screen and a back stack restored after process death).
        composable<Board> { entry ->
            val args = entry.toRoute<Board>()
            val unlock by ServiceLocator.unlock.state.collectAsStateWithLifecycle()
            // Latched per back stack entry; rememberSaveable survives rotation and process death, so a run that was
            // allowed to start keeps going after a refund. A locked start never builds a BoardViewModel.
            var latched by rememberSaveable { mutableStateOf(false) }
            val open = Access.boardOpen(latched, args.levelId, args.daily, unlock.unlocked)
            LaunchedEffect(open) { if (open) latched = true }
            if (open) {
                BoardScreen(
                    args = args,
                    onQuit = { navController.popBackStack(Home, inclusive = false) },
                    onFinished = { result ->
                        navController.navigate(result) {
                            popUpTo<Board> { inclusive = true }
                        }
                    },
                )
            } else {
                UnlockScreen(onBack = { navController.popBackStack() }, onLevels = null)
            }
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
                onUnlock = { navController.navigate(Unlock) },
            )
        }
        composable<Daily> {
            DailyScreen(onBack = { navController.popBackStack() }, onPlay = { navController.navigate(it) })
        }
        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLicences = { navController.navigate(Licences) },
                onUnlock = { navController.navigate(Unlock) },
            )
        }
        composable<Licences> {
            LicencesScreen(onBack = { navController.popBackStack() })
        }
        composable<Unlock> {
            UnlockScreen(
                onBack = { navController.popBackStack() },
                onLevels = { navController.navigate(Levels) { popUpTo(Home) } },
            )
        }
    }
}
