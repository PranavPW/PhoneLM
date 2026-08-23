package com.phonelm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.phonelm.ui.HomeScreen
import com.phonelm.ui.ChatScreen
import com.phonelm.ui.theme.PhoneLMTheme
import com.phonelm.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private val chatViewModel by viewModels<ChatViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneLMTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onModelSelected = { path ->
                                chatViewModel.loadModel(path)
                                navController.navigate("chat")
                            }
                        )
                    }
                    composable("chat") {
                        ChatScreen(
                            viewModel = chatViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
