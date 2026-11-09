package com.yeckel.tutor

import android.app.Application
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.yeckel.tutor.data.DebugPresetReceiver

class TutorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Debug helper: apply configuration presets from adb broadcasts.
        if (BuildConfig.DEBUG) {
            ContextCompat.registerReceiver(
                this,
                DebugPresetReceiver(),
                IntentFilter(DebugPresetReceiver.ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }
}
