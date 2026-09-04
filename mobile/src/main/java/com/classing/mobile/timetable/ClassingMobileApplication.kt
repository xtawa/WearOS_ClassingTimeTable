package com.xtawa.classingtime

import android.app.Application
import com.xtawa.classingtime.usage.UsageReporter

class ClassingMobileApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UsageReporter.onAppStart(this)
    }
}
