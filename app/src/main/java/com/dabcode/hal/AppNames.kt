package com.dabcode.hal

import org.json.JSONObject

// Custom app names. The key is the launcher component ("package/activity"), because one package
// can have more than one launcher activity.

/** Return [apps] with the custom names. An app with no custom name gets its original name back. */
fun applyCustomNames(apps: List<AppInfo>, customNames: Map<String, String>): List<AppInfo> =
    apps.map { it.copy(appName = customNames[it.componentKey] ?: it.originalName) }

/**
 * Return the custom name to store for the [input] text, or null to remove the custom name.
 * An empty text, or the original name, removes the custom name.
 */
fun customNameToStore(input: String, originalName: String): String? {
    val name = input.trim()
    return if (name.isEmpty() || name == originalName) null else name
}

/** Read the custom names from their JSON text. A damaged text gives no custom names. */
fun parseCustomNames(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { obj.getString(it) }
    } catch (_: Exception) {
        emptyMap()
    }
}

/** Write the custom names as JSON text. */
fun customNamesToJson(names: Map<String, String>): String = JSONObject(names as Map<*, *>).toString()

/** Set or remove (with a null [name]) the custom name of [componentKey]. */
fun withCustomName(names: Map<String, String>, componentKey: String, name: String?): Map<String, String> =
    if (name == null) names - componentKey else names + (componentKey to name)
