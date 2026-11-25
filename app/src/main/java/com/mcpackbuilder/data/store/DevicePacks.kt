package com.mcpackbuilder.data.store

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DevicePacks {
    suspend fun read(context: Context, uri: Uri): List<ModEntry> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: error("Папка недоступна")
        val candidates = if (root.findFile("manifest.json") != null) listOf(root) else {
            val dirs = root.listFiles().filter { it.isDirectory }
            val groups = dirs.filter { it.name in setOf("behavior_packs", "resource_packs", "development_behavior_packs", "development_resource_packs") }
            (dirs + groups.flatMap { it.listFiles().filter { child -> child.isDirectory } }).filter { it.findFile("manifest.json") != null }
        }
        require(candidates.isNotEmpty()) { "Не найдены папки с manifest.json" }
        val target = File(context.filesDir, "device-packs").apply { mkdirs() }
        candidates.map { pack ->
            val id = UUID.nameUUIDFromBytes(pack.uri.toString().toByteArray()).toString()
            val file = File.createTempFile("pack-$id-", ".mcpack", target)
            try {
                ZipOutputStream(file.outputStream().buffered()).use { zip ->
                    fun copy(dir: DocumentFile, prefix: String, depth: Int) {
                        require(depth < 40) { "Слишком много вложенных папок" }
                        dir.listFiles().forEach { child ->
                            val name = child.name ?: return@forEach
                            require(name !in listOf("..", ".") && '/' !in name && '\\' !in name)
                            if (child.isDirectory) copy(child, "$prefix$name/", depth+1)
                            else if (child.isFile) {
                                zip.putNextEntry(ZipEntry(prefix + name))
                                checkNotNull(context.contentResolver.openInputStream(child.uri)).use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                    copy(pack, "", 0)
                }
                ModEntry(id, pack.name ?: "Установленный пак", pageUrl = "device:$id", filePath = file.path)
            } catch (e: Exception) { file.delete(); throw e }
        }
    }
}
