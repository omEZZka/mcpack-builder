package com.mcpackbuilder.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f КБ", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.2f МБ", mb)
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

fun formatDownloads(count: Long?): String? {
    if (count == null) return null
    return when {
        count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f млн", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.getDefault(), "%.1f тыс.", count / 1_000.0)
        else -> count.toString()
    }
}
