package com.mcpackbuilder.data.download

import com.mcpackbuilder.AppContainer
import com.mcpackbuilder.domain.model.SavedBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

object MapImporter {
    suspend fun download(app: AppContainer, title: String, url: String) {
        val file = FileDownloader.download(app.app, url, null, null, null)
        try { save(app, title, file) } finally { file.delete() }
    }

    suspend fun save(app: AppContainer, title: String, file: File) = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dir = File(app.buildsDir, id).apply { mkdirs() }
        val out = File(dir, (app.merger.sanitizeFileName(title).ifBlank { "World" }) + ".mcworld")
        try {
            ZipFile(file).use { zip ->
                val level = zip.entries().asSequence().filter { !it.isDirectory && it.name.substringAfterLast('/') == "level.dat" }.minByOrNull { it.name.length }
                    ?: error("В архиве не найден мир Bedrock (level.dat)")
                val prefix = level.name.removeSuffix("level.dat")
                var bytes = 0L
                ZipOutputStream(out.outputStream().buffered()).use { dest ->
                    val buffer = ByteArray(65536)
                    zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) }.forEach { entry ->
                        val name = entry.name.removePrefix(prefix)
                        require(!name.contains('\\') && name.split('/').none { it == ".." || it == "." || it.isEmpty() })
                        dest.putNextEntry(ZipEntry(name))
                        zip.getInputStream(entry).use { input ->
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                bytes += n
                                require(bytes <= 2_000_000_000L) { "Карта слишком большая" }
                                dest.write(buffer, 0, n)
                            }
                        }
                        dest.closeEntry()
                    }
                }
            }
            app.library.save(SavedBuild(id, title, System.currentTimeMillis(), out.path, out.length(), emptyList(), "mcworld"))
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }
}
