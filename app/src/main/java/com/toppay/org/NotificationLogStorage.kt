package com.toppay.org

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationLogStorage {
    private const val FILE_NAME = "notification.txt"
    private const val OLD_FILE_NAME = "notifications.txt"
    private const val MAX_NOTIFICATIONS = 50
    private const val ENTRY_SEPARATOR = "----------------------------------------"
    private val lock = Any()

    fun file(context: Context): File {
        val directory = context.getExternalFilesDir(null) ?: context.filesDir
        if (!directory.exists()) directory.mkdirs()
        val destination = File(directory, FILE_NAME)

        if (!destination.exists()) {
            val oldFile = File(context.filesDir, OLD_FILE_NAME)
            if (oldFile.exists()) {
                runCatching {
                    oldFile.copyTo(destination, overwrite = false)
                    oldFile.delete()
                }
            }
        }
        return destination
    }

    fun append(context: Context, appName: String, packageName: String, title: String, body: String) {
        val entry = buildString {
            append('[')
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            append("]\nApp: ").append(clean(appName))
            append("\nPackage: ").append(clean(packageName))
            append("\nTitle: ").append(clean(title))
            append("\nText: ").append(clean(body))
            append("\n$ENTRY_SEPARATOR\n")
        }

        synchronized(lock) {
            val logFile = file(context)
            val existingEntries = logFile.takeIf(File::exists)
                ?.readText()
                .orEmpty()
                .split(ENTRY_SEPARATOR)
                .map(String::trim)
                .filter(String::isNotBlank)
                .takeLast(MAX_NOTIFICATIONS - 1)

            val updated = (existingEntries + entry.substringBeforeLast(ENTRY_SEPARATOR).trim())
                .joinToString("\n$ENTRY_SEPARATOR\n", postfix = "\n$ENTRY_SEPARATOR\n")
            logFile.writeText(updated)
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        file(context).takeIf(File::exists)?.readText().orEmpty()
    }

    fun entries(context: Context): List<Map<String, String>> = synchronized(lock) {
        file(context).takeIf(File::exists)
            ?.readText()
            .orEmpty()
            .split(ENTRY_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotBlank)
            .takeLast(MAX_NOTIFICATIONS)
            .map { block ->
                val lines = block.lines()
                mapOf(
                    "timestamp" to lines.firstOrNull()?.removePrefix("[")?.removeSuffix("]").orEmpty(),
                    "app" to valueAfter(lines, "App:"),
                    "packageName" to valueAfter(lines, "Package:"),
                    "title" to valueAfter(lines, "Title:"),
                    "text" to valueAfter(lines, "Text:")
                )
            }
    }

    fun clear(context: Context) = synchronized(lock) {
        file(context).writeText("")
    }

    private fun clean(value: String): String = value
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace('\t', ' ')
        .trim()
        .take(2_000)

    private fun valueAfter(lines: List<String>, prefix: String): String = lines
        .firstOrNull { it.startsWith(prefix) }
        ?.substringAfter(prefix)
        ?.trim()
        .orEmpty()
}
