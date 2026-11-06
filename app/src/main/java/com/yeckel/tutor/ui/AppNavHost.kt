package com.yeckel.tutor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yeckel.tutor.ui.chat.ChatScreen
import com.yeckel.tutor.ui.flashcards.FlashcardsScreen
import com.yeckel.tutor.ui.placement.PlacementScreen
import com.yeckel.tutor.ui.settings.SettingsScreen
import com.yeckel.tutor.ui.start.StartScreen
import com.yeckel.tutor.ui.words.WordsScreen

object Routes {
    const val START = "start"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PLACEMENT = "placement"
    const val WORDS = "words"
    const val FLASHCARDS = "flashcards"
}

@Composable
fun TutorNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.START) {
        composable(Routes.START) {
            StartScreen(
                onStartChat = { navController.navigate(Routes.CHAT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onFindLevel = { navController.navigate(Routes.PLACEMENT) },
                onOpenWords = { navController.navigate(Routes.WORDS) },
            )
        }
        composable(Routes.CHAT) {
            ChatScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PLACEMENT) {
            PlacementScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.WORDS) {
            WordsScreen(
                onBack = { navController.popBackStack() },
                onTrain = { navController.navigate(Routes.FLASHCARDS) },
            )
        }
        composable(Routes.FLASHCARDS) {
            FlashcardsScreen(onBack = { navController.popBackStack() })
        }
    }
}
