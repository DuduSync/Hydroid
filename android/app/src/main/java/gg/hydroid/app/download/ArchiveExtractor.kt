package gg.hydroid.app.download

import com.github.junrar.Junrar
import gg.hydroid.app.data.log.AppLog
import java.io.File
import java.util.zip.ZipInputStream

// extrai .zip (stdlib, com zip4j pra senha) e .rar (junrar); protege contra zip-slip.
// arquivos protegidos por senha (ex.: Online-Fix) usam senhas conhecidas da cena.
// devolve os nomes do primeiro nivel extraidos (o chamador guarda SO isso no card:
// o Apagar nao pode levar a pasta toda). Nao reabre o arquivo depois: o
// java.util.zip do Android recusa zip com entrada criptografada ("invalid CEN header")
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

    fun extract(archive: File, destDir: File): List<String> {
        destDir.mkdirs()
        return when (archive.extension.lowercase()) {
            "zip" -> extractZip(archive, destDir)
            "rar" -> extractRar(archive, destDir)
            else -> error("Formato não suportado: .${archive.extension}")
        }
    }

    private fun topLevel(name: String): String? {
        val n = name.replace('\\', '/').trimStart('/')
        return n.substringBefore('/').takeIf { it.isNotBlank() }
    }

    // ---------- RAR ----------

    private fun extractRar(archive: File, destDir: File): List<String> {
        val names = LinkedHashSet<String>()
        runCatching {
            com.github.junrar.Archive(archive).use { ar ->
                ar.fileHeaders.forEach { h -> topLevel(h.fileName)?.let { names.add(it) } }
            }
        }
        val encrypted = runCatching {
            com.github.junrar.Archive(archive).use { it.isEncrypted }
        }.getOrDefault(false)
        if (!encrypted) {
            Junrar.extract(archive, destDir)
            return names.toList()
        }
        AppLog.i("Extract", "${archive.name}: RAR protegido por senha, tentando senhas conhecidas")
        var last: Exception? = null
        for (pwd in KNOWN_PASSWORDS) {
            try {
                Junrar.extract(archive, destDir, pwd)
                AppLog.i("Extract", "${archive.name}: RAR extraido com senha da cena")
                return names.toList()
            } catch (e: Exception) {
                AppLog.w("Extract", "${archive.name}: senha '$pwd' falhou: ${e.message}")
                last = e
            }
        }
        throw last ?: IllegalStateException("RAR protegido por senha (nenhuma senha conhecida funcionou)")
    }

    // ---------- ZIP ----------

    private fun extractZip(archive: File, destDir: File): List<String> {
        val encrypted = runCatching {
            net.lingala.zip4j.ZipFile(archive).isEncrypted
        }.getOrDefault(false)
        if (!encrypted) {
            return extractZipPlain(archive, destDir)
        }
        AppLog.i("Extract", "${archive.name}: ZIP protegido por senha, tentando senhas conhecidas")
        var last: Exception? = null
        for (pwd in KNOWN_PASSWORDS) {
            try {
                val names = extractZipWithPassword(archive, destDir, pwd)
                AppLog.i("Extract", "${archive.name}: ZIP extraido com senha da cena")
                return names
            } catch (e: Exception) {
                AppLog.w("Extract", "${archive.name}: senha '$pwd' falhou: ${e.message}")
                last = e
            }
        }
        throw last ?: IllegalStateException("ZIP protegido por senha (nenhuma senha conhecida funcionou)")
    }

    // extrai com senha; o FUSE do /sdcard falha com EEXIST ao abrir p/ escrita um alvo que
    // existe (e as vezes um dentry velho responde EEXIST mesmo sem o arquivo): apaga antes
    // e tenta de novo algumas vezes, como no extractZipPlain
    private fun extractZipWithPassword(archive: File, destDir: File, pwd: String): List<String> {
        var lastErr: Exception? = null
        for (round in 0 until 3) {
            try {
                val zf = net.lingala.zip4j.ZipFile(archive, pwd.toCharArray())
                val destCanonical = destDir.canonicalFile
                val names = LinkedHashSet<String>()
                zf.fileHeaders.forEach { h ->
                    val out = File(destDir, h.fileName).canonicalFile
                    if (!out.path.startsWith(destCanonical.path + File.separator)) {
                        error("Arquivo suspeito no zip: ${h.fileName}")
                    }
                    topLevel(h.fileName)?.let { names.add(it) }
                    if (!h.isDirectory) out.delete()
                }
                zf.extractAll(destDir.absolutePath)
                return names.toList()
            } catch (e: Exception) {
                lastErr = e
                if (e.message?.contains("EEXIST") == true && round < 2) {
                    Thread.sleep(200)
                } else {
                    throw e
                }
            }
        }
        throw lastErr ?: IllegalStateException("falha ao extrair ZIP com senha")
    }

    private fun extractZipPlain(zip: File, destDir: File): List<String> {
        val destCanonical = destDir.canonicalFile
        val names = LinkedHashSet<String>()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                topLevel(entry.name)?.let { names.add(it) }
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
        return names.toList()
    }
}
