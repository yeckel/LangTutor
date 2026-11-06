package com.yeckel.tutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yeckel.tutor.ui.TutorNavHost
import com.yeckel.tutor.ui.theme.TutorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TutorTheme {
                TutorNavHost()
            }
        }
    }
}
