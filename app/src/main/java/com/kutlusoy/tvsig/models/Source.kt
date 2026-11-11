package com.kutlusoy.tvsig.models

enum class SourceType(val id: Int) {
    WEB(0),
    DRIVE(1);

    companion object {
        fun fromId(id: Int): SourceType = values().firstOrNull { it.id == id } ?: WEB
    }
}

/** Where the media come from: a web server or a shared Google Drive folder, plus the chosen folder path. */
data class Source(val type: SourceType, val base: String, val path: String) {
    val isConfigured: Boolean get() = base.isNotBlank()
}
