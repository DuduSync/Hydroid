package gg.hydroid.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import gg.hydroid.app.data.api.FeaturedCache
import gg.hydroid.app.data.api.RepackFinder
import gg.hydroid.app.data.i18n.localized
import gg.hydroid.app.data.log.AppLog
import gg.hydroid.app.data.store.AppStore
import gg.hydroid.app.data.update.UpdateManager
import gg.hydroid.app.download.DownloadEngine

class HydroidApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(localized(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppStore.init(this)
        AppLog.i("App", "estado: setup=${AppStore.setupDone.value} tema=${AppStore.theme.value} " +
            "idioma=${AppStore.language.value.ifBlank { "sistema" }} biblioteca=${AppStore.library.value.size} " +
            "fontes=${AppStore.sources.value.size} downloads=${AppStore.downloads.value.size}")
        // APK de update ja instalado nao serve mais: apaga pra nao ocupar ~45 MB no cache
        UpdateManager.cleanupOldApks(this)
        // pre-carrega o app: home do catalogo + contagem de downloads (biblioteca e home)
        // tudo em background — as abas abrem ja populadas e seguem sincronizando
        FeaturedCache.ensureLoaded()
        RepackFinder.warmUp()
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
