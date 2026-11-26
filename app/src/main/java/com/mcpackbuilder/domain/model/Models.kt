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
    val downloadCount: Long? = null,
    val published: String = "",
    val downloadsToday: Long? = null,
    val source: String = "mcpedl"
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

enum class ModCategory(val path: String, val typeId: Int?, val mcpehubPath: String) {
    MODS("mods", 1, "mods"),
    TEXTURES("texture-packs", 3, "textures"),
    SHADERS("texture-packs/shaders", 3, "shaders"),
    MAPS("maps", 2, "maps");

    val pageUrl: String get() = "https://mcpedl.com/${path.trim('/')}/"
    val mcpehubUrl: String get() = "https://mcpehub.org/${mcpehubPath.trim('/')}"
}

enum class CatalogSort(val api: String) {
    NEW("created_at"),
    OLD("created_at"),
    TODAY("popularity_week"),
    WEEK("popularity_week"),
    MONTH("popularity_month")
}

enum class CatalogSource {
    MCPEDL, MCPEHUB
}

enum class CatalogTag(val mcpedlSlug: String, val mcpehubSlug: String, val label: String = "") {
    ALL("", ""),
    HORROR("horror", ""),
    RPG("rpg", ""),
    FURNITURE("furniture", ""),
    ANIMALS("animals", ""),
    GUNS("guns", ""),
    CARS("cars", ""),
    MAGIC("magic", ""),
    REALISTIC("realistic", ""),
    PARKOUR("parkour", ""),
    CITY("city", ""),
    ANIME("anime", ""),
    PVP("pvp", ""),
    SURVIVAL("survival", ""),
    ADVENTURE("adventure", ""),
    WEAPONS("weapons", ""),
}
