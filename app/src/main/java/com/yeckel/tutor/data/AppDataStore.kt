package com.yeckel.tutor.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Single DataStore file shared by settings and progress persistence. */
internal val Context.tutorDataStore by preferencesDataStore(name = "tutor_settings")
