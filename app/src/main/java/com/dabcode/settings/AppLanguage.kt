package com.dabcode.settings

import java.util.Locale

/** One app language: the BCP 47 tag and the name of the language in that language. */
data class AppLanguage(val tag: String, val nativeName: String)

/**
 * The app languages, in the order of the language picker. English is the default (values/).
 * Keep this list equal to res/xml/locales_config.xml and to the values-* folders. A unit test checks it.
 */
val APP_LANGUAGES = listOf(
    AppLanguage("en", "English"),
    AppLanguage("da", "Dansk"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("es", "Español"),
    AppLanguage("fr", "Français"),
    AppLanguage("it", "Italiano"),
    AppLanguage("ja", "日本語"),
    AppLanguage("sv", "Svenska"),
    AppLanguage("nb", "Norsk bokmål"),
    AppLanguage("pt-BR", "Português (Brasil)"),
    AppLanguage("nl", "Nederlands")
)

/**
 * Return the app language for the selected language tags, or null for the system default.
 * An exact tag wins. Else the first language with the same language code wins ("de-DE" gives "de").
 * The system setting (Android 13 and later) can give a tag with a region.
 */
fun selectedAppLanguage(languageTags: String): AppLanguage? {
    val first = languageTags.split(',').firstOrNull()?.trim().orEmpty()
    if (first.isEmpty()) return null
    APP_LANGUAGES.firstOrNull { it.tag.equals(first, ignoreCase = true) }?.let { return it }
    // Some devices use the old code "no" for Norwegian. HAL has Norwegian Bokmål ("nb").
    val language = Locale.forLanguageTag(first).language.let { if (it == "no") "nb" else it }
    return APP_LANGUAGES.firstOrNull { Locale.forLanguageTag(it.tag).language == language }
}
