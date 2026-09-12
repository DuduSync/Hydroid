package gg.hydroid.app.download

import com.github.junrar.Junrar
import java.io.File
import java.util.zip.ZipInputStream

// extrai .zip (stdlib) e .rar (junrar); protege contra zip-slip
object ArchiveExtractor {

    fun extract(archive: File, destDir: File): File {
        destDir.mkdirs()
        when (archive.extension.lowercase()) {
            "zip" -> extractZip(archive, destDir)
            "rar" -> Junrar.extract(archive, destDir)
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

    private fun extractZip(zip: File, destDir: File) {
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
                    out.outputStream().buffered().use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
    }
}
