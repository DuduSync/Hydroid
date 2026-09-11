package gg.hydroid.app

import android.app.Application
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.store.AppStore

class HydroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppStore.init(this)
    }
}
