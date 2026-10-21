package com.matejhoracek.gtinav

import android.app.Application
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp

// One navigation engine for the whole app: the phone screens and Android Auto share it.
class GtiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!MapboxNavigationApp.isSetup()) {
            MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())
        }
    }
}
