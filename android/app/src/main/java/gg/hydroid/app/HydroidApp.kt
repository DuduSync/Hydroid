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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HydroidApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(localized(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppStore.init(this)
        // captura crash: grava o stack no log ANTES do app morrer (senao o relato vem sem stack)
        runCatching {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { AppLog.e("FATAL", "crash na thread ${thread.name}", throwable) }
                prev?.uncaughtException(thread, throwable)
            }
        }
        AppLog.i("App", "estado: setup=${AppStore.setupDone.value} tema=${AppStore.theme.value} " +
            "idioma=${AppStore.language.value.ifBlank { "sistema" }} biblioteca=${AppStore.library.value.size} " +
            "fontes=${AppStore.sources.value.size} downloads=${AppStore.downloads.value.size}")
        // APK de update ja instalado nao serve mais: apaga pra nao ocupar ~45 MB no cache
        UpdateManager.cleanupOldApks(this)
        // pre-carrega o app: home do catalogo + contagem de downloads (biblioteca e home)
        // tudo em background — as abas abrem ja populadas e seguem sincronizando
        FeaturedCache.ensureLoaded()
        RepackFinder.warmUp()
        // lista de hosts do Real-Debrid (tag "Recomendado" no sheet sem pular na animacao)
        gg.hydroid.app.data.api.RdHosts.warmUp()
        // v0.9.10: fontes adicionadas pela loja usavam id "store-" (nem registrado no Hydra
        // nem tratado como local) e a busca de repacks vinha vazia. Conserta aqui no boot.
        runCatching {
            val quebradas = AppStore.sources.value.filter { it.id.startsWith("store-") }
            if (quebradas.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    for (s in quebradas) {
                        val reg = runCatching {
                            gg.hydroid.app.data.api.HydraCloudApi.registerSource(s.url)
                        }.getOrNull()
                        if (reg != null) {
                            AppStore.removeSource(s.id)
                            AppStore.addSource(reg.copy(name = s.name))
                            AppLog.i("SourceStore", "fonte da loja migrada pro Hydra: ${s.name} (${reg.id})")
                        } else {
                            AppStore.removeSource(s.id)
                            AppStore.addSource(s.copy(id = "local-${s.url.hashCode()}"))
                            AppLog.i("SourceStore", "fonte da loja migrada pra local: ${s.name}")
                        }
                    }
                }
            }
        }
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
