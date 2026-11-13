package com.kutlusoy.tvsig.models

/** The languages of the app. The order is the order of the choice in the settings. */
enum class AppLanguage(val id: Int, val tag: String) {
    AUTO(0, ""),
    ENGLISH(1, "en"),
    GERMAN(2, "de"),
    TURKISH(3, "tr");

    companion object {
        val DEFAULT = AUTO

        fun fromId(id: Int): AppLanguage = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
