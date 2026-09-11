package gg.hydroid.app

import android.app.Application
import gg.hydroid.app.data.store.AppStore

class HydroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStore.init(this)
    }
}
