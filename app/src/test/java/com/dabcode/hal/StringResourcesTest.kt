package com.dabcode.hal

import com.dabcode.settings.APP_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Check the translations against values/strings.xml: the same keys, the same format arguments,
 * and no text that aapt does not accept. Also check that the language list, locales_config.xml
 * and the values-* folders agree.
 */
class StringResourcesTest {

    // Gradle runs the unit tests in the module folder. The IDE can run them in the project folder.
    private val resDir: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    /** One resource: a string has one text, a plurals has one text for each quantity. */
    private data class Resource(val texts: Map<String, String>)

    private fun parse(file: File): Map<String, Resource> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, Resource>()
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            if (el.getAttribute("translatable") == "false") continue
            val name = el.getAttribute("name")
            when (el.tagName) {
                "string" -> result["string/$name"] = Resource(mapOf("" to el.textContent))
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    result["plurals/$name"] = Resource(
                        (0 until items.length).associate {
                            val item = items.item(it) as Element
                            item.getAttribute("quantity") to item.textContent
                        }
                    )
                }
                else -> fail("${file.path}: unexpected element <${el.tagName}>")
            }
        }
        return result
    }

    private val formatArg = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

    /** Return the format arguments of [text], for example ["%1$s", "%2$d"]. "%%" is not an argument. */
    private fun formatArgs(text: String): Set<String> =
        formatArg.findAll(text).map { it.value }.filter { it != "%%" }.toSet()

    /** Return the format arguments of a resource. For a plurals, use the "other" text. */
    private fun mainArgs(resource: Resource): Set<String> =
        formatArgs(resource.texts["other"] ?: resource.texts.getValue(""))

    private val defaultStrings by lazy { parse(File(resDir, "values/strings.xml")) }

    private val translationFiles: List<File> by lazy {
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
            .sortedBy { it.parentFile!!.name }
    }

    @Test
    fun translationsExist() {
        assertEquals(APP_LANGUAGES.size - 1, translationFiles.size)
    }

    @Test
    fun translationsHaveTheSameKeys() {
        for (file in translationFiles) {
            val translated = parse(file)
            val missing = defaultStrings.keys - translated.keys
            val extra = translated.keys - defaultStrings.keys
            assertTrue("${file.parentFile!!.name}: missing $missing, extra $extra", missing.isEmpty() && extra.isEmpty())
        }
    }

    @Test
    fun translationsHaveTheSameFormatArguments() {
        for (file in translationFiles) {
            val folder = file.parentFile!!.name
            for ((key, resource) in parse(file)) {
                val default = defaultStrings[key] ?: continue
                val expected = mainArgs(default)
                assertEquals("$folder $key", expected, mainArgs(resource))
                // A quantity text (for example "one") can leave out the number, but it cannot add an argument.
                for ((quantity, text) in resource.texts) {
                    assertTrue("$folder $key $quantity: ${formatArgs(text)}", expected.containsAll(formatArgs(text)))
                }
            }
        }
    }

    @Test
    fun pluralsHaveAnOtherQuantity() {
        for (file in translationFiles + File(resDir, "values/strings.xml")) {
            for ((key, resource) in parse(file)) {
                if (key.startsWith("plurals/")) assertTrue("${file.path} $key", "other" in resource.texts)
            }
        }
    }

    @Test
    fun formatArgumentsArePositional() {
        // aapt rejects more than one argument without a position. Positional arguments let a translation change the order.
        for (file in translationFiles + File(resDir, "values/strings.xml")) {
            for ((key, resource) in parse(file)) {
                for (text in resource.texts.values) {
                    val bad = formatArgs(text).filter { !Regex("""%\d+\$.*""").matches(it) }
                    assertTrue("${file.path} $key: $bad", bad.isEmpty())
                }
            }
        }
    }

    @Test
    fun noUnescapedQuotes() {
        // aapt fails on an apostrophe without a backslash. A double quote without a backslash disappears.
        val unescaped = Regex("""(?<!\\)['"]""")
        for (file in translationFiles + File(resDir, "values/strings.xml")) {
            for ((key, resource) in parse(file)) {
                for (text in resource.texts.values) {
                    assertTrue("${file.path} $key: $text", !unescaped.containsMatchIn(text))
                }
            }
        }
    }

    @Test
    fun reviewLineIsNoQuestion() {
        // Google Play does not allow a question before a rating request. The line only opens the Play listing.
        for (file in translationFiles + File(resDir, "values/strings.xml")) {
            val text = parse(file).getValue("string/review_prompt").texts.getValue("")
            assertTrue("${file.path}: $text", !text.contains('?') && !text.contains('？') && !text.contains('¿'))
            assertTrue("${file.path}: $text", text.contains("Google Play"))
        }
    }

    @Test
    fun languageListMatchesLocalesConfigAndFolders() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(resDir, "xml/locales_config.xml"))
        val locales = doc.getElementsByTagName("locale")
        val configTags = (0 until locales.length).map {
            (locales.item(it) as Element).getAttributeNS("http://schemas.android.com/apk/res/android", "name")
        }
        assertEquals(APP_LANGUAGES.map { it.tag }, configTags)

        // The folder qualifier of "pt-BR" is "pt-rBR". English is the default folder "values".
        val expectedFolders = APP_LANGUAGES.map { it.tag }.filter { it != "en" }
            .map { "values-" + it.replace(Regex("-([A-Z]{2})$"), "-r$1") }.sorted()
        assertEquals(expectedFolders, translationFiles.map { it.parentFile!!.name })
    }
}
