package com.dabcode.hal

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Remove the accents from [text]. Keep the case. For example "Éclair" gives "Eclair". */
private fun stripAccents(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

/** Make [text] easy to compare: no accents and lower case. */
fun normalizeForSearch(text: String): String = stripAccents(text).lowercase(Locale.ROOT)

/**
 * Return the indices where a word starts in [text].
 * A word starts at index 0, after a character that is not a letter or digit, and at an upper-case letter
 * after a lower-case letter ("WhatsApp" has words at 0 and 5).
 */
private fun wordStarts(text: String): List<Int> = text.indices.filter { i ->
    val c = text[i]
    if (!c.isLetterOrDigit()) return@filter false
    if (i == 0) return@filter true
    val before = text[i - 1]
    !before.isLetterOrDigit() || (before.isLowerCase() && c.isUpperCase())
}

/** Return true when each query letter matches the first letter of a word, in order ("wa" and "WhatsApp"). */
private fun matchesInitials(query: String, text: String, starts: List<Int>): Boolean {
    var q = 0
    for (i in starts) {
        if (q < query.length && text[i] == query[q]) q++
    }
    return q == query.length
}

private fun isSubsequence(query: String, text: String): Boolean {
    var q = 0
    for (c in text) {
        if (q < query.length && c == query[q]) q++
    }
    return q == query.length
}

/**
 * Return the edit distance between [a] and [b]: the number of inserted, removed or changed letters,
 * and of swapped neighbor letters ("sptoify" to "spotify" is 1). This is the optimal string alignment
 * form of the Damerau-Levenshtein distance.
 */
fun editDistance(a: String, b: String): Int {
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
    }
    return d[a.length][b.length]
}

/** Return the number of typos that a query can have: 0 below 3 letters, 1 for 3 to 5 letters, 2 for 6 or more. */
private fun allowedTypos(queryLength: Int): Int = when {
    queryLength < 3 -> 0
    queryLength <= 5 -> 1
    else -> 2
}

/** Return true when [query] is near the whole label or near one word of the label. */
private fun matchesWithTypos(query: String, text: String, starts: List<Int>): Boolean {
    val allowed = allowedTypos(query.length)
    if (allowed == 0) return false
    val words = starts.mapIndexed { n, start ->
        val end = starts.getOrNull(n + 1) ?: text.length
        text.substring(start, end).filter { it.isLetterOrDigit() }
    }
    return (words + text).any { editDistance(query, it) <= allowed }
}

/**
 * Return how well [query] matches [label], or null when it does not match.
 * 0: the label starts with the query. 1: a word in the label starts with the query.
 * 2: the query letters are the first letters of words, in order ("wa" and "WhatsApp").
 * 3: the query letters are in the label in the same order.
 * 4: the query is near the label or a word of the label, with a small number of typos.
 * Case and accents are ignored.
 */
fun matchRank(label: String, query: String): Int? {
    val q = normalizeForSearch(query.trim())
    val folded = stripAccents(label)
    val text = folded.lowercase(Locale.ROOT)
    val starts = wordStarts(folded)
    return when {
        text.startsWith(q) -> 0
        starts.any { text.startsWith(q, it) } -> 1
        matchesInitials(q, text, starts) -> 2
        isSubsequence(q, text) -> 3
        matchesWithTypos(q, text, starts) -> 4
        else -> null
    }
}

/**
 * Return the items that match [query], best match first, then in alphabetical order.
 * An empty query returns [items] unchanged.
 */
fun <T> fuzzySearch(items: List<T>, query: String, label: (T) -> String): List<T> =
    fuzzySearchNames(items, query) { listOf(label(it)) }

/**
 * Like [fuzzySearch], but each item has more than one name (for example a custom name and the original name).
 * The best rank of the names counts. The first name is the name for the alphabetical order.
 */
fun <T> fuzzySearchNames(items: List<T>, query: String, names: (T) -> List<String>): List<T> {
    if (query.isBlank()) return items
    return items
        .mapNotNull { item ->
            val itemNames = names(item)
            val rank = itemNames.mapNotNull { matchRank(it, query) }.minOrNull() ?: return@mapNotNull null
            Triple(item, rank, normalizeForSearch(itemNames.first()))
        }
        .sortedWith(compareBy({ it.second }, { it.third }))
        .map { it.first }
}

// ── The A-Z jump bar ───────────────────────────────────

/** The HAL language code for the jump bar: "no" (old code) is "nb". */
private fun barLanguage(language: String): String = if (language == "no") "nb" else language

/**
 * The letters that a language sorts as other letters (as java.text.Collator does).
 * Danish and Norwegian sort "Ä" as "Æ" and "Aa" as "Å". Swedish sorts "Æ" as "Ä".
 */
private fun letterAliases(language: String): Map<Char, Char> = when (language) {
    "da", "nb" -> mapOf('Ä' to 'Æ', 'Ö' to 'Ø', 'Ü' to 'Y')
    "sv" -> mapOf('Æ' to 'Ä', 'Ø' to 'Ö', 'Ü' to 'Y')
    else -> emptyMap()
}

/** Letters that have no accent to remove, but sort as a base letter in the A-Z languages. */
private val BASE_LETTERS = mapOf('Æ' to 'A', 'Ø' to 'O', 'Œ' to 'O', 'ß' to 'S')

/**
 * Return the letters of the jump bar for [language] (an ISO 639 code). "#" is first, for names that do not
 * start with a letter. da and nb: Æ Ø Å after Z. sv: Å Ä Ö after Z. es: Ñ after N. Other languages: A-Z.
 */
fun jumpLetters(language: String): List<Char> {
    val az = ('A'..'Z').toList()
    val letters = when (barLanguage(language)) {
        "da", "nb" -> az + listOf('Æ', 'Ø', 'Å')
        "sv" -> az + listOf('Å', 'Ä', 'Ö')
        "es" -> az.flatMap { if (it == 'N') listOf('N', 'Ñ') else listOf(it) }
        else -> az
    }
    return listOf('#') + letters
}

/**
 * Return the jump bar letter of [label] for [language]: the letter of the first character.
 * A letter of the language stays (Å in Danish). Other accents fold to the base letter (É gives E).
 * Digits and other characters give "#".
 */
fun jumpLetter(label: String, language: String): Char {
    val lang = barLanguage(language)
    val letters = jumpLetters(lang)
    val trimmed = label.trimStart()
    if ((lang == "da" || lang == "nb") && trimmed.startsWith("aa", ignoreCase = true)) return 'Å'
    val first = trimmed.firstOrNull()?.uppercaseChar() ?: return '#'
    val letter = letterAliases(lang)[first] ?: first
    if (letter in letters) return letter
    val base = BASE_LETTERS[letter] ?: stripAccents(letter.toString()).firstOrNull() ?: return '#'
    return if (base in 'A'..'Z') base else '#'
}

/**
 * Return the list index for a tap on [letter] in the jump bar.
 * [sortedLabels] must be sorted as [sortAppLabels] sorts them for [language].
 * Return the first label with the letter, or with a later letter. Return the last index when no label is at
 * or after the letter. Return -1 for an empty list. Japanese keeps the first rule (see [jumpIndexJapanese]).
 */
fun jumpIndex(sortedLabels: List<String>, letter: Char, language: String): Int {
    if (barLanguage(language) == "ja") return jumpIndexJapanese(sortedLabels, letter)
    if (sortedLabels.isEmpty()) return -1
    if (letter == '#') return 0
    val letters = jumpLetters(language)
    val target = letters.indexOf(letter)
    val index = sortedLabels.indexOfFirst { letters.indexOf(jumpLetter(it, language)) >= target }
    return if (index >= 0) index else sortedLabels.lastIndex
}

/**
 * The jump rule for Japanese. [sortedLabels] must be sorted by [normalizeForSearch].
 * Return the first label that starts with the letter, or with a later letter (kana is after Z).
 */
private fun jumpIndexJapanese(sortedLabels: List<String>, letter: Char): Int {
    if (sortedLabels.isEmpty()) return -1
    if (letter == '#') return 0
    val target = letter.lowercaseChar()
    val index = sortedLabels.indexOfFirst { label ->
        val first = normalizeForSearch(label).firstOrNull() ?: return@indexOfFirst false
        first.isLetter() && first >= target
    }
    return if (index >= 0) index else sortedLabels.lastIndex
}

/**
 * Return [items] sorted by [label] for [locale], so that the list and the jump bar agree.
 * java.text.Collator gives the order of the language (Danish: Zoom, Ærø, Øresund, Åben).
 * Japanese keeps the first order: by [normalizeForSearch].
 */
fun <T> sortAppLabels(items: List<T>, locale: Locale, label: (T) -> String): List<T> =
    if (locale.language == "ja") items.sortedBy { normalizeForSearch(label(it)) }
    else items.sortedWith(compareBy(Collator.getInstance(locale)) { label(it) })
