package gg.hydroid.app.download

import com.github.junrar.Junrar
import gg.hydroid.app.data.log.AppLog
import java.io.File
import java.util.zip.ZipInputStream

// extrai .zip (stdlib, com zip4j pra senha) e .rar (junrar); protege contra zip-slip.
// arquivos protegidos por senha (ex.: Online-Fix) usam senhas conhecidas da cena
object ArchiveExtractor {

    // senhas conhecidas de fontes da comunidade (a primeira e a do Online-Fix)
    private val KNOWN_PASSWORDS = listOf(
        "online-fix.me",
        "www.skidrowreloaded.com",
        "igg-games.com",
        "pcgames-download.com",
        "gamepciso.com",
        "dodi-repacks.site"
    )

    fun extract(archive: File, destDir: File): File {
        destDir.mkdirs()
        when (archive.extension.lowercase()) {
            "zip" -> extractZip(archive, destDir)
            "rar" -> extractRar(archive, destDir)
            else -> error("Formato não suportado: .${archive.extension}")
        }
        return destDir
    }

    // nomes do primeiro nivel do arquivo (ex.: ["Hello-World-master"]), para saber
    // exatamente o que este download criou no destino
    fun topLevelEntries(archive: File): List<String> = runCatching {
        when (archive.extension.lowercase()) {
            "zip" -> java.util.zip.ZipFile(archive).use { zf ->
                zf.entries().asSequence().mapNotNull { e ->
                    val n = e.name.replace('\\', '/').trimStart('/')
                    n.substringBefore('/').takeIf { it.isNotBlank() }
                }.distinct().toList()
            }
            "rar" -> com.github.junrar.Archive(archive).use { ar ->
                ar.fileHeaders.mapNotNull { h ->
                    val n = h.fileName.replace('\\', '/').trimStart('/')
                    n.substringBefore('/').takeIf { it.isNotBlank() }
                }.distinct().toList()
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    // ---------- RAR ----------

    private fun extractRar(archive: File, destDir: File) {
        val encrypted = runCatching {
            com.github.junrar.Archive(archive).use { it.isEncrypted }
        }.getOrDefault(false)
        if (!encrypted) {
            Junrar.extract(archive, destDir)
            return
        }
        AppLog.i("Extract", "${archive.name}: RAR protegido por senha, tentando senhas conhecidas")
        var last: Exception? = null
        for (pwd in KNOWN_PASSWORDS) {
            try {
                Junrar.extract(archive, destDir, pwd)
                AppLog.i("Extract", "${archive.name}: RAR extraido com senha da cena")
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("RAR protegido por senha (nenhuma senha conhecida funcionou)")
    }

    // ---------- ZIP ----------

    private fun extractZip(archive: File, destDir: File) {
        val encrypted = runCatching {
            net.lingala.zip4j.ZipFile(archive).isEncrypted
        }.getOrDefault(false)
        if (!encrypted) {
            extractZipPlain(archive, destDir)
            return
        }
        AppLog.i("Extract", "${archive.name}: ZIP protegido por senha, tentando senhas conhecidas")
        var last: Exception? = null
        for (pwd in KNOWN_PASSWORDS) {
            try {
                val zf = net.lingala.zip4j.ZipFile(archive, pwd.toCharArray())
                // zip-slip: confere os caminhos antes de extrair
                val destCanonical = destDir.canonicalFile
                zf.fileHeaders.forEach { h ->
                    val out = File(destDir, h.fileName).canonicalFile
                    if (!out.path.startsWith(destCanonical.path + File.separator)) {
                        error("Arquivo suspeito no zip: ${h.fileName}")
                    }
                }
                zf.extractAll(destDir.absolutePath)
                AppLog.i("Extract", "${archive.name}: ZIP extraido com senha da cena")
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("ZIP protegido por senha (nenhuma senha conhecida funcionou)")
    }

    private fun extractZipPlain(zip: File, destDir: File) {
        val destCanonical = destDir.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(destDir, entry.name)
                val outCanonical = out.canonicalFile
                if (!outCanonical.path.startsWith(destCanonical.path + File.separator)) {
                    error("Arquivo suspeito no zip: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    // sobrescreve extracoes anteriores (open falha com EEXIST se o alvo
                    // existe) e tenta de novo: FUSE as vezes segura um dentry antigo
                    var attempt = 0
                    while (true) {
                        if (out.exists() && !out.isDirectory) out.delete()
                        try {
                            out.outputStream().buffered().use { zis.copyTo(it) }
                            break
                        } catch (e: java.io.FileNotFoundException) {
                            if (attempt >= 2) throw e
                            attempt++
                            Thread.sleep(120)
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
    }
}
