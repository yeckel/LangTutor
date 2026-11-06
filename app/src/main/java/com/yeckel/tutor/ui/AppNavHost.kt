package com.yeckel.tutor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yeckel.tutor.ui.chat.ChatScreen
import com.yeckel.tutor.ui.settings.SettingsScreen
import com.yeckel.tutor.ui.start.StartScreen

object Routes {
    const val START = "start"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

@Composable
fun TutorNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.START) {
        composable(Routes.START) {
            StartScreen(
                onStartChat = { navController.navigate(Routes.CHAT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CHAT) {
            ChatScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
