package com.example

import android.app.Application
import android.util.Log
import com.example.data.auth.AuthRepository

class CitraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            AuthRepository.ensureFirebaseInitialized(this)
            Log.d("CitraApplication", "Firebase inicializado no Application.onCreate")
        } catch (e: Exception) {
            Log.e("CitraApplication", "Falha ao inicializar Firebase no Application.onCreate", e)
        }
    }
}
