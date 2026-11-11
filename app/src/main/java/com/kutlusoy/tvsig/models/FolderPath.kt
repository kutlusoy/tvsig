package com.kutlusoy.tvsig.models

/** Folder paths are folder names joined by a slash, relative to the main folder. */
object FolderPath {
    const val MAX_DEPTH = 5

    fun segments(path: String): List<String> = path.split('/').map { it.trim() }.filter { it.isNotEmpty() }

    fun normalize(path: String): String = segments(path).joinToString("/")

    fun depth(path: String): Int = segments(path).size

    fun child(path: String, name: String): String = normalize(if (path.isBlank()) name else "$path/$name")

    fun parent(path: String): String = segments(path).dropLast(1).joinToString("/")

    fun name(path: String): String = segments(path).lastOrNull() ?: ""

    fun display(path: String, rootLabel: String): String =
        segments(path).let { if (it.isEmpty()) rootLabel else (listOf(rootLabel) + it).joinToString(" / ") }
}
