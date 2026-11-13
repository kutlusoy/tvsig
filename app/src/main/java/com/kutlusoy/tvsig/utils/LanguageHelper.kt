package com.kutlusoy.tvsig.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.kutlusoy.tvsig.models.AppLanguage

object LanguageHelper {
    /** Sets the language of the app, "automatic" follows the language of the device and falls back to English. */
    fun apply(language: AppLanguage) {
        val locales = if (language == AppLanguage.AUTO) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(language.tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
