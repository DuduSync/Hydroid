package gg.hydroid.app.data.store

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

// converte a pasta escolhida no seletor do Android (SAF) para caminho real.
// funciona para o armazenamento do aparelho (provider externalstorage).
object StorageUtil {

    fun resolveTreePath(uri: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        val type = parts.getOrNull(0) ?: return null
        val rel = parts.getOrNull(1).orEmpty()
        when {
            type.equals("primary", ignoreCase = true) ->
                "/storage/emulated/0/$rel".trimEnd('/')
            type.isNotBlank() ->
                "/storage/$type/$rel".trimEnd('/')
            else -> null
        }
    }.getOrNull()

    fun persistPermission(
        context: android.content.Context,
        uri: Uri
    ) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }
}
