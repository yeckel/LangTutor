package com.yeckel.tutor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.yeckel.tutor.ui.TutorNavHost
import com.yeckel.tutor.ui.theme.TutorTheme

class MainActivity : AppCompatActivity() {

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
