package com.newsworld.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NewsWorldApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
