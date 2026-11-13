package com.kutlusoy.tvsig

import com.kutlusoy.tvsig.models.AppLanguage
import com.kutlusoy.tvsig.models.ErrorMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** English is the default, German and Turkish must have every text, with the same placeholders. */
class LocalizationTest {

    private class Texts(val strings: Map<String, String>, val translatable: Set<String>, val arrays: Map<String, List<String>>)

    private fun load(folder: String): Texts {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue("missing $file", file.exists())
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, String>()
        val translatable = mutableSetOf<String>()
        val nodes = document.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as Element
            strings[node.getAttribute("name")] = node.textContent
            if (node.getAttribute("translatable") != "false") translatable.add(node.getAttribute("name"))
        }
        val arrays = mutableMapOf<String, List<String>>()
        val arrayNodes = document.getElementsByTagName("string-array")
        for (i in 0 until arrayNodes.length) {
            val node = arrayNodes.item(i) as Element
            val items = node.getElementsByTagName("item")
            arrays[node.getAttribute("name")] = (0 until items.length).map { items.item(it).textContent }
        }
        return Texts(strings, translatable, arrays)
    }

    private val placeholder = Regex("%(\\d+\\$)?[sd]")
    private fun placeholders(text: String) = placeholder.findAll(text).map { it.value }.sorted().toList()

    private val english = load("values")

    private fun check(folder: String) {
        val other = load(folder)
        val expected = english.translatable
        assertEquals("$folder is missing texts", emptySet<String>(), expected - other.strings.keys)
        assertEquals("$folder has texts that do not exist in English", emptySet<String>(), other.strings.keys - expected)
        for (name in expected) {
            val value = other.strings.getValue(name)
            assertTrue("$folder/$name is empty", value.isNotBlank())
            assertEquals("$folder/$name has other placeholders than English", placeholders(english.strings.getValue(name)), placeholders(value))
        }
        for ((name, items) in english.arrays) {
            val translated = other.arrays[name]
            assertNotNull("$folder is missing the list $name", translated)
            assertEquals("$folder/$name has another number of entries", items.size, translated!!.size)
            assertTrue("$folder/$name has an empty entry", translated.none { it.isBlank() })
        }
    }

    @Test
    fun germanIsComplete() = check("values-de")

    @Test
    fun turkishIsComplete() = check("values-tr")

    @Test
    fun theTextsAreNotJustCopiedFromEnglish() {
        val german = load("values-de")
        val turkish = load("values-tr")
        val samples = listOf("settings_title", "button_save", "button_cancel", "pin_enter", "settings_section_playback")
        for (name in samples) {
            assertTrue("$name is not German", german.strings.getValue(name) != english.strings.getValue(name))
            assertTrue("$name is not Turkish", turkish.strings.getValue(name) != english.strings.getValue(name))
        }
    }

    @Test
    fun theLanguageOrderIsEnglishGermanTurkish() {
        assertEquals(listOf("", "en", "de", "tr"), AppLanguage.values().map { it.tag })
        assertEquals(AppLanguage.AUTO, AppLanguage.fromId(99))
    }

    @Test
    fun everyKnownErrorHasATextInEveryLanguage() {
        val codes = listOf(
            ErrorMessages.OFFLINE, ErrorMessages.LISTING, ErrorMessages.NO_MEDIA,
            ErrorMessages.BLOCKED, ErrorMessages.DRIVE_LINK, ErrorMessages.DRIVE_FOLDER
        ).map { ErrorMessages.codeOf(it) }
        codes.forEach { assertNotNull(it) }
        assertNull(ErrorMessages.codeOf("something else"))
        listOf("values", "values-de", "values-tr").forEach { folder ->
            val strings = load(folder).strings
            codes.forEach { assertTrue("$folder lacks error_$it", strings.containsKey("error_$it")) }
        }
    }
}
