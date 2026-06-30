package com.studentclubs.android

import android.app.Application
import dev.core.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class StudentClubsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@StudentClubsApp)
        }
    }
}
