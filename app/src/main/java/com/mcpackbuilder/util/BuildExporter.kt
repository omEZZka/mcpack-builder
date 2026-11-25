package com.mcpackbuilder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object BuildExporter {

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun openInMinecraft(context: Context, file: File) {
        if (!file.isFile) {
            Toast.makeText(context, "Файл сборки не найден", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = uriFor(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Открыть в Minecraft")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun share(context: Context, file: File) {
        if (!file.isFile) {
            Toast.makeText(context, "Файл сборки не найден", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = uriFor(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Поделиться сборкой")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось поделиться: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
