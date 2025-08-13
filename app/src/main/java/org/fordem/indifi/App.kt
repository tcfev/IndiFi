package org.fordem.indifi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.fordem.indifi.ui.utils.MessageRouterHelper.initialize

@HiltAndroidApp
class MyApp : Application(){
    override fun onCreate() {
        super.onCreate()

        initialize(this)
    }
}
