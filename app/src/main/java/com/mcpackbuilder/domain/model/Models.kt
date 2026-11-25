package com.mcpackbuilder.domain.model

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ModEntry(
    val id: String,
    val title: String,
    val author: String? = null,
    val category: String? = null,
    val version: String? = null,
    val coverUrl: String? = null,
    val pageUrl: String,
    val description: String? = null,
    val screenshots: List<String> = emptyList(),
    val downloadLinks: List<String> = emptyList(),
    val filePath: String? = null,
    val typeId: Int = 1,
    val downloadCount: Long? = null
) {
    val isDownloaded: Boolean get() = filePath != null && File(filePath).isFile
}

@Serializable
data class DownloadedFile(
    val url: String,
    val path: String,
    val modId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class SavedBuild(
    val id: String,
    val name: String,
    val createdAt: Long,
    val filePath: String,
    val fileSize: Long,
    val modTitles: List<String>,
    val kind: String
)

data class MergeResult(
    val output: File,
    val conflicts: List<String>,
    val bpFile: File?,
    val rpFile: File?
)

class MergeException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class ModCategory(val title: String, val path: String, val typeId: Int?) {
    ADDONS("Аддоны", "addons", 1),
    TEXTURES("Текстуры", "addons/texture-packs", 3),
    SHADERS("Шейдеры", "texture-packs/shaders", 3),
    ALL("Всё", "addons", null);

    val pageUrl: String
        get() = "https://mcpedl.com/${path.trim('/')}/"
}
