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
