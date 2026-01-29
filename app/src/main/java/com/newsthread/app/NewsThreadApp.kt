package com.newsthread.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NewsThreadApp : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
