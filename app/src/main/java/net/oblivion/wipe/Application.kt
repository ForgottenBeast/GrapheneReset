package net.oblivion.wipe

import android.app.Application
import com.google.android.material.color.DynamicColors
//F-droid update trigger (hopefully this time)
class Application : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
