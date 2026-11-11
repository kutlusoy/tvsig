package com.kutlusoy.tvsig.repositories

/** Reads publicly shared Google Drive folders ("anyone with the link") without an API key. */
object DriveSource {

    data class Entry(val id: String, val name: String, val isFolder: Boolean)

    private const val ID = "[A-Za-z0-9_-]"
    private val FOLDER_PATH = Regex("/folders/($ID{10,})")
    private val ID_PARAM = Regex("[?&]id=($ID{10,})")
    private val RAW_ID = Regex("^$ID{20,}$")
    private val ENTRY_START = Regex("""id="entry-($ID{10,})"""")
    private val ENTRY_TITLE = Regex("""flip-entry-title[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    fun isDriveReference(base: String): Boolean {
        val text = base.trim()
        return text.contains("drive.google.com", ignoreCase = true) ||
                text.contains("docs.google.com/drive", ignoreCase = true) ||
                RAW_ID.matches(text)
    }

    /** Accepts a folder link in any of the usual forms, or the bare folder ID. */
    fun folderId(base: String): String? {
        val text = base.trim()
        if (RAW_ID.matches(text)) return text
        return FOLDER_PATH.find(text)?.groupValues?.get(1) ?: ID_PARAM.find(text)?.groupValues?.get(1)
    }

    fun listUrl(folderId: String) = "https://drive.google.com/embeddedfolderview?id=$folderId"

    fun downloadUrl(fileId: String) = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"

    fun parseListing(html: String): List<Entry> {
        val starts = ENTRY_START.findAll(html).toList()
        return starts.mapIndexedNotNull { index, start ->
            val end = starts.getOrNull(index + 1)?.range?.first ?: html.length
            val segment = html.substring(start.range.first, end)
            val name = ENTRY_TITLE.find(segment)?.groupValues?.get(1)?.let { unescape(it).trim() }
            if (name.isNullOrEmpty()) null
            else Entry(start.groupValues[1], name, segment.contains("/folders/"))
        }.distinctBy { it.id }
    }

    fun unescape(text: String): String {
        val cleaned = text.replace(Regex("<[^>]*>"), "")
        return Regex("&(#x?[0-9A-Fa-f]+|amp|lt|gt|quot|apos);").replace(cleaned) { match ->
            val code = match.groupValues[1]
            when {
                code == "amp" -> "&"
                code == "lt" -> "<"
                code == "gt" -> ">"
                code == "quot" -> "\""
                code == "apos" -> "'"
                code.startsWith("#x") -> code.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: match.value
                else -> code.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
            }
        }
    }
}
