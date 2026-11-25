package com.mcpackbuilder.domain.merger

import com.mcpackbuilder.domain.model.MergeException
import com.mcpackbuilder.domain.model.MergeResult
import com.mcpackbuilder.domain.model.ModEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Локальный компилятор сборок: распаковка, классификация BP/RP,
 * слияние глобальных реестров, Script API, генерация манифестов и упаковка.
 */
class AddonMerger {

    companion object {
        private val REGISTRY_FILES = setOf(
            "textures/item_texture.json",
            "textures/terrain_texture.json",
            "sounds/sound_definitions.json",
            "blocks.json",
            "biomes_client.json",
            "flipbook_textures.json"
        )
        private const val DEFAULT_MIN_ENGINE = "1,20,0"
    }

    private data class ScriptModule(val entry: String?)

    private data class PackInfo(
        val modIndex: Int,
        val modTitle: String,
        val root: File,
        val manifest: JsonObject,
        val hasData: Boolean,
        val hasResources: Boolean,
        val scriptModules: List<ScriptModule>,
        val minEngine: List<Int>?
    )

    private data class FileSource(val file: File, val pack: PackInfo, val relativePath: String)

    private class TargetState(val dir: File) {
        val registries = LinkedHashMap<String, MutableList<FileSource>>()
        val copiedBy = HashMap<String, PackInfo>()
    }

    suspend fun merge(
        mods: List<ModEntry>,
        buildName: String,
        outputDir: File,
        workRoot: File,
        onProgress: (Int, String) -> Unit,
        onLog: (String) -> Unit,
        icon: File? = null
    ): MergeResult = withContext(Dispatchers.IO) {
        if (mods.isEmpty()) throw MergeException("Сборка пуста: добавьте хотя бы один мод.")
        val missing = mods.filter { it.filePath == null || !File(it.filePath).isFile }
        if (missing.isNotEmpty()) {
            throw MergeException("Не скачаны файлы: " + missing.joinToString { "«${it.title}»" })
        }

        val conflicts = mutableListOf<String>()
        val safeName = sanitizeFileName(buildName).ifBlank { "MyAddon" }
        val buildId = UUID.randomUUID().toString()
        val work = File(workRoot, "merge_$buildId")
        work.mkdirs()
        outputDir.mkdirs()

        try {
            onProgress(4, "Подготовка рабочей папки")
            val extractedRoot = File(work, "mods").apply { mkdirs() }

            mods.forEachIndexed { index, mod ->
                ensureActive()
                onProgress(6 + (22 * index) / mods.size, "Распаковка «${mod.title}»")
                val dest = File(extractedRoot, "mod_$index")
                dest.mkdirs()
                try {
                    unzip(File(mod.filePath!!), dest)
                } catch (e: Exception) {
                    throw MergeException("Не удалось распаковать «${mod.title}»: ${e.message}", e)
                }
                if (dest.walkTopDown().none { it.isFile }) {
                    throw MergeException("Архив «${mod.title}» пуст или повреждён.")
                }
                expandNestedArchives(dest, onLog)
                onLog("Распакован мод ${index + 1}/${mods.size}: ${mod.title}")
            }

            ensureActive()
            onProgress(32, "Поиск манифестов и классификация паков")
            val packs = discoverPacks(mods, extractedRoot, onLog)
            if (packs.isEmpty()) {
                throw MergeException("В выбранных модах не найдено ни одного manifest.json.")
            }
            val hasBp = packs.any { it.hasData }
            val hasRp = packs.any { it.hasResources }
            if (!hasBp && !hasRp) {
                throw MergeException("Манифесты найдены, но в них нет модулей data/script/resources.")
            }
            onLog("Найдено паков: ${packs.size} (BP: ${packs.count { it.hasData }}, RP: ${packs.count { it.hasResources }})")

            val bpDir = File(work, "merged_bp")
            val rpDir = File(work, "merged_rp")
            val bp = TargetState(bpDir)
            val rp = TargetState(rpDir)

            ensureActive()
            onProgress(42, "Копирование файлов с учётом приоритета")
            val allRoots = packs.map { it.root }.toSet()
            val ordered = packs.sortedByDescending { it.modIndex }

            for (pack in ordered) {
                ensureActive()
                val targets = buildList {
                    if (pack.hasData) add(bp)
                    if (pack.hasResources) add(rp)
                }
                for (target in targets) {
                    target.dir.mkdirs()
                    copyPackFiles(pack, target, allRoots, hasScripts = pack.scriptModules.isNotEmpty(), onLog, conflicts)
                }
            }

            ensureActive()
            onProgress(64, "Слияние глобальных реестров")
            mergeRegistries(bp, onLog)
            mergeRegistries(rp, onLog)

            ensureActive()
            onProgress(76, "Сборка Script API")
            val scriptPacks = packs
                .filter { it.hasData && it.scriptModules.isNotEmpty() }
                .sortedBy { it.modIndex }
            val scriptResult = mergeScripts(scriptPacks, bpDir, onLog)

            ensureActive()
            onProgress(84, "Генерация манифестов")
            var minEngine = DEFAULT_MIN_ENGINE.split(',').map { it.toInt() }
            for (pack in packs) {
                val candidate = pack.minEngine ?: continue
                if (JsonUtils.compareVersion(candidate, minEngine) > 0) minEngine = candidate
            }
            val bpUuid = UUID.randomUUID().toString()
            val rpUuid = UUID.randomUUID().toString()
            val packVersion = listOf(1, 0, 0)

            if (hasBp) {
                val modules = mutableListOf<JsonObject>()
                modules += moduleJson("data", UUID.randomUUID().toString(), packVersion)
                if (scriptResult.hasScripts) {
                    modules += buildJsonObject {
                        put("type", "script")
                        put("language", "javascript")
                        put("entry", "scripts/main.js")
                        put("uuid", UUID.randomUUID().toString())
                        put("version", versionArray(packVersion))
                    }
                }
                val deps = mutableListOf<JsonObject>()
                if (hasRp) {
                    deps += buildJsonObject {
                        put("uuid", rpUuid)
                        put("version", versionArray(packVersion))
                    }
                }
                if (scriptResult.hasScripts) {
                    val server = maxDependencyVersion(packs, "@minecraft/server") ?: listOf(1, 11, 0)
                    val serverUi = maxDependencyVersion(packs, "@minecraft/server-ui") ?: listOf(1, 2, 0)
                    deps += buildJsonObject {
                        put("module_name", "@minecraft/server")
                        put("version", versionArray(server))
                    }
                    deps += buildJsonObject {
                        put("module_name", "@minecraft/server-ui")
                        put("version", versionArray(serverUi))
                    }
                    onLog("Script API: @minecraft/server ${server.joinToString(".")}, @minecraft/server-ui ${serverUi.joinToString(".")}")
                }
                val manifest = manifestJson(
                    name = "$safeName BP",
                    description = "Behavior pack, собранный MCPack Builder",
                    uuid = bpUuid,
                    modules = modules,
                    minEngine = minEngine,
                    dependencies = deps
                )
                File(bpDir, "manifest.json").writeText(JsonUtils.encode(manifest))
            }

            if (hasRp) {
                val manifest = manifestJson(
                    name = "$safeName RP",
                    description = "Resource pack, собранный MCPack Builder",
                    uuid = rpUuid,
                    modules = listOf(moduleJson("resources", UUID.randomUUID().toString(), packVersion)),
                    minEngine = minEngine,
                    dependencies = emptyList()
                )
                File(rpDir, "manifest.json").writeText(JsonUtils.encode(manifest))
            }

            ensureActive()
            if (icon != null) {
                if (hasBp) icon.copyTo(File(bpDir, "pack_icon.png"), overwrite = true)
                if (hasRp) icon.copyTo(File(rpDir, "pack_icon.png"), overwrite = true)
            }
            onProgress(92, "Упаковка архивов")
            val result = packageOutputs(safeName, outputDir, bpDir.takeIf { hasBp }, rpDir.takeIf { hasRp })
            onLog("Готово: ${result.output.name} (${formatSize(result.output.length())})")
            if (conflicts.isNotEmpty()) {
                onLog("Конфликтов перезаписи: ${conflicts.size}")
            }
            onProgress(100, "Сборка завершена")
            MergeResult(result.output, conflicts.toList(), result.bpFile, result.rpFile)
        } finally {
            runCatching { work.deleteRecursively() }
        }
    }

    private fun discoverPacks(mods: List<ModEntry>, extractedRoot: File, log: (String) -> Unit): List<PackInfo> {
        val result = mutableListOf<PackInfo>()
        for (modIndex in mods.indices) {
            val modDir = File(extractedRoot, "mod_$modIndex")
            if (!modDir.isDirectory) continue
            val manifests = modDir.walkTopDown()
                .filter { it.isFile && it.name.equals("manifest.json", ignoreCase = true) }
                .toList()
            val seenRoots = HashSet<String>()
            for (manifestFile in manifests) {
                val root = manifestFile.parentFile ?: continue
                if (!seenRoots.add(root.canonicalPath)) continue
                val manifest = JsonUtils.parseObject(manifestFile.readText())
                if (manifest == null) {
                    log("Пропущен manifest.json (невалидный JSON): ${root.relativeTo(extractedRoot)}")
                    continue
                }
                val modules = JsonUtils.objArray(manifest, "modules")
                val types = modules.mapNotNull { JsonUtils.stringOf(it, "type")?.lowercase() }
                val hasData = types.any { it == "data" || it == "script" }
                val hasResources = types.contains("resources")
                if (!hasData && !hasResources) {
                    log("Пропущен пак без data/resources: ${root.relativeTo(extractedRoot)}")
                    continue
                }
                val header = manifest["header"] as? JsonObject
                val minEngine = header?.let { JsonUtils.intList(it["min_engine_version"]) }
                val scriptModules = modules
                    .filter { JsonUtils.stringOf(it, "type")?.lowercase() == "script" }
                    .map { ScriptModule(JsonUtils.stringOf(it, "entry")) }
                result += PackInfo(
                    modIndex = modIndex,
                    modTitle = mods[modIndex].title,
                    root = root,
                    manifest = manifest,
                    hasData = hasData,
                    hasResources = hasResources,
                    scriptModules = scriptModules,
                    minEngine = minEngine
                )
            }
        }
        return result
    }

    private fun copyPackFiles(
        pack: PackInfo,
        target: TargetState,
        allRoots: Set<File>,
        hasScripts: Boolean,
        log: (String) -> Unit,
        conflicts: MutableList<String>
    ) {
        val files = pack.root.walkTopDown()
            .filter { it.isFile }
            .toList()
            .sortedBy { it.path }
        for (file in files) {
            val rel = relativePath(pack.root, file)
            if (rel.isBlank()) continue
            val relLower = rel.lowercase()
            if (file.name.equals("manifest.json", ignoreCase = true)) continue
            if (file.name == ".DS_Store" || file.name == "Thumbs.db") continue
            if (relLower.startsWith("__macosx/") || relLower.contains("/__macosx/")) continue
            if (hasScripts && (relLower == "scripts" || relLower.startsWith("scripts/"))) continue
            if (isInsideNestedPack(file, pack.root, allRoots)) continue

            if (relLower.endsWith(".lang") || relLower in REGISTRY_FILES) {
                target.registries.getOrPut(relLower) { mutableListOf() }
                    .add(FileSource(file, pack, rel))
                continue
            }

            val out = File(target.dir, rel)
            val previous = target.copiedBy[relLower]
            if (out.exists()) {
                val message = "«${pack.modTitle}» перезаписал $rel" +
                    (previous?.let { " (ранее из «${it.modTitle}»)" } ?: "")
                conflicts += message
                log("WARN: $message")
            }
            out.parentFile?.mkdirs()
            copyFileSmart(file, out, log)
            target.copiedBy[relLower] = pack
        }
    }

    private fun isInsideNestedPack(file: File, currentRoot: File, allRoots: Set<File>): Boolean {
        val filePath = file.canonicalPath
        val currentPath = currentRoot.canonicalPath + File.separator
        for (other in allRoots) {
            if (other == currentRoot) continue
            val otherPath = other.canonicalPath
            if (!otherPath.startsWith(currentPath)) continue
            if (filePath.startsWith(otherPath + File.separator)) return true
        }
        return false
    }

    private fun mergeRegistries(target: TargetState, log: (String) -> Unit) {
        if (!target.dir.isDirectory) return
        for ((relLower, sources) in target.registries) {
            val rel = sources.first().relativePath
            val out = File(target.dir, rel)
            out.parentFile?.mkdirs()
            if (relLower.endsWith(".lang")) {
                var text = ""
                for (source in sources) {
                    text = try {
                        JsonUtils.mergeLang(text, source.file.readText())
                    } catch (e: Exception) {
                        log("WARN: не удалось прочитать ${source.relativePath}: ${e.message}")
                        text
                    }
                }
                out.writeText(text)
            } else {
                var merged: JsonObject? = null
                for (source in sources) {
                    val obj = JsonUtils.parseObject(source.file.readText())
                    if (obj == null) {
                        log("WARN: пропущен невалидный JSON ${source.relativePath} из «${source.pack.modTitle}»")
                        continue
                    }
                    merged = merged?.let { JsonUtils.deepMerge(it, obj) } ?: obj
                }
                if (merged != null) {
                    out.writeText(JsonUtils.encode(merged))
                }
            }
            log("Объединён реестр: $rel (${sources.size} шт.)")
        }
    }

    private data class ScriptsResult(val hasScripts: Boolean)

    private fun mergeScripts(scriptPacks: List<PackInfo>, bpDir: File, log: (String) -> Unit): ScriptsResult {
        if (scriptPacks.isEmpty()) return ScriptsResult(false)
        bpDir.mkdirs()
        val scriptsOut = File(bpDir, "scripts").apply { mkdirs() }
        val imports = mutableListOf<String>()
        scriptPacks.forEachIndexed { idx, pack ->
            val sourceScripts = File(pack.root, "scripts")
            if (!sourceScripts.isDirectory) {
                log("WARN: у «${pack.modTitle}» объявлен script-модуль, но папка scripts/ отсутствует")
                return@forEachIndexed
            }
            val dest = File(scriptsOut, "mod_$idx")
            try {
                sourceScripts.copyRecursively(dest, overwrite = true)
            } catch (e: Exception) {
                log("WARN: не удалось скопировать скрипты «${pack.modTitle}»: ${e.message}")
                return@forEachIndexed
            }
            var entry = pack.scriptModules.firstOrNull()?.entry?.replace('\\', '/')?.trimStart('/')
                ?: "scripts/main.js"
            if (entry.startsWith("scripts/")) entry = entry.removePrefix("scripts/")
            var entryFile = File(dest, entry)
            if (!entryFile.isFile) {
                entryFile = listOf(File(dest, "main.js"), File(dest, "index.js"))
                    .firstOrNull { it.isFile }
                    ?: dest.walkTopDown().firstOrNull { it.isFile && it.extension.equals("js", true) }
                    ?: return@forEachIndexed
                log("WARN: entry «$entry» не найден у «${pack.modTitle}», использую ${entryFile.name}")
            }
            val shimName = "mod_${idx}_entry.js"
            val shimPath = relativePath(dest, entryFile)
            File(scriptsOut, shimName).writeText("import \"./mod_$idx/$shimPath\";\n")
            imports += shimName
            log("Script API: «${pack.modTitle}» → scripts/mod_$idx/$shimPath")
        }
        if (imports.isEmpty()) return ScriptsResult(false)
        val main = buildString {
            appendLine("// Сгенерировано MCPack Builder")
            imports.forEach { appendLine("import \"./$it\";") }
        }
        File(scriptsOut, "main.js").writeText(main)
        return ScriptsResult(true)
    }

    private fun maxDependencyVersion(packs: List<PackInfo>, moduleName: String): List<Int>? {
        var best: List<Int>? = null
        for (pack in packs) {
            for (dep in JsonUtils.objArray(pack.manifest, "dependencies")) {
                if (!JsonUtils.stringOf(dep, "module_name").equals(moduleName, ignoreCase = true)) continue
                val version = JsonUtils.intList(dep["version"]) ?: continue
                best = best?.let { if (JsonUtils.compareVersion(version, it) > 0) version else it } ?: version
            }
        }
        return best
    }

    private fun moduleJson(type: String, uuid: String, version: List<Int>): JsonObject = buildJsonObject {
        put("type", type)
        put("uuid", uuid)
        put("version", versionArray(version))
    }

    private fun manifestJson(
        name: String,
        description: String,
        uuid: String,
        modules: List<JsonObject>,
        minEngine: List<Int>,
        dependencies: List<JsonObject>
    ): JsonObject = buildJsonObject {
        put("format_version", 2)
        put("header", buildJsonObject {
            put("name", name)
            put("description", description)
            put("uuid", uuid)
            put("version", versionArray(listOf(1, 0, 0)))
            put("min_engine_version", versionArray(minEngine))
        })
        put("modules", JsonArray(modules))
        if (dependencies.isNotEmpty()) put("dependencies", JsonArray(dependencies))
    }

    private fun versionArray(version: List<Int>): JsonArray =
        JsonArray(version.map { JsonPrimitive(it) })

    private data class Packaged(val output: File, val bpFile: File?, val rpFile: File?)

    private fun packageOutputs(name: String, outputDir: File, bpDir: File?, rpDir: File?): Packaged {
        return when {
            bpDir == null && rpDir == null -> throw MergeException("Нечего упаковывать.")
            bpDir != null && rpDir != null -> {
                val bpFile = File(outputDir, "merged_bp.mcpack")
                val rpFile = File(outputDir, "merged_rp.mcpack")
                zipDirectory(bpDir, bpFile)
                zipDirectory(rpDir, rpFile)
                val addon = File(outputDir, "$name.mcaddon")
                ZipOutputStream(BufferedOutputStream(FileOutputStream(addon))).use { zos ->
                    listOf(bpFile, rpFile).forEach { pack ->
                        zos.putNextEntry(ZipEntry(pack.name))
                        pack.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                Packaged(addon, bpFile, rpFile)
            }
            bpDir != null -> {
                val out = File(outputDir, "$name.mcpack")
                zipDirectory(bpDir, out)
                Packaged(out, out, null)
            }
            else -> {
                val out = File(outputDir, "$name.mcpack")
                zipDirectory(rpDir!!, out)
                Packaged(out, null, out)
            }
        }
    }

    private fun zipDirectory(sourceDir: File, output: File) {
        output.parentFile?.mkdirs()
        val temp = File(output.parentFile, output.name + ".tmp")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { zos ->
            val files = sourceDir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
            for (file in files) {
                val entryName = relativePath(sourceDir, file)
                if (entryName.isBlank()) continue
                val entry = ZipEntry(entryName)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        if (output.exists()) output.delete()
        if (!temp.renameTo(output)) {
            temp.copyTo(output, overwrite = true)
            temp.delete()
        }
    }

    private fun unzip(zipFile: File, dest: File) {
        dest.mkdirs()
        val canonicalDest = dest.canonicalFile
        var entries = 0
        BufferedInputStream(zipFile.inputStream()).use { bis ->
            ZipInputStream(bis).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    if (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/")) {
                        zis.closeEntry()
                        continue
                    }
                    val target = File(dest, name).canonicalFile
                    if (target != canonicalDest && !target.path.startsWith(canonicalDest.path + File.separator)) {
                        throw SecurityException("Небезопасный путь в архиве: $name")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { fos ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = zis.read(buffer)
                                if (read <= 0) break
                                fos.write(buffer, 0, read)
                            }
                        }
                        entries++
                    }
                    zis.closeEntry()
                }
            }
        }
        if (entries == 0) throw IllegalArgumentException("архив не содержит файлов")
    }

    private fun expandNestedArchives(dir: File, log: (String) -> Unit, depth: Int = 0) {
        if (depth > 3) return
        val nested = dir.walkTopDown()
            .filter {
                it.isFile && it.extension.lowercase() in setOf("mcpack", "mcaddon")
            }
            .toList()
        for (archive in nested) {
            val out = File(archive.parentFile, archive.nameWithoutExtension + "__unpacked")
            try {
                if (out.exists()) out.deleteRecursively()
                unzip(archive, out)
                archive.delete()
                log("Распакован вложенный архив: ${archive.name}")
                expandNestedArchives(out, log, depth + 1)
            } catch (e: Exception) {
                log("WARN: вложенный архив ${archive.name} не распакован: ${e.message}")
            }
        }
    }

    private fun copyFileSmart(source: File, target: File, log: (String) -> Unit) {
        if (source.extension.equals("json", ignoreCase = true)) {
            val text = runCatching { source.readText() }.getOrNull()
            val parsed = text?.let { JsonUtils.parseElement(it) }
            if (parsed != null) {
                target.writeText(JsonUtils.encode(parsed))
                return
            }
            if (text != null) {
                log("WARN: JSON с ошибками скопирован как есть: ${source.name}")
            }
        }
        source.copyTo(target, overwrite = true)
    }

    private fun relativePath(root: File, file: File): String {
        val rootPath = root.canonicalPath
        val filePath = file.canonicalPath
        val rel = if (filePath.startsWith(rootPath)) filePath.substring(rootPath.length) else file.name
        return rel.replace('\\', '/').trimStart('/')
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
        return cleaned.take(80)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes Б"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f КБ", kb)
        return String.format("%.2f МБ", kb / 1024.0)
    }
}
