package gg.hydroid.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import gg.hydroid.app.data.i18n.localized
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.download.DownloadEngine

class HydroidApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(localized(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppStore.init(this)
        // quando a rede volta (ex.: Wi-Fi ligado), solta a fila de downloads
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    DownloadEngine.onConnectivityChanged()
                }
            })
        }
    }
}
