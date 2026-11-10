package com.dabcode.hal.quiet

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The list of quiet notifications, in a JSON file. The listener and the screens share one instance
 * (the listener runs in the app process). The file is small (max 100 entries).
 */
class QuietStore private constructor(private val file: AtomicFile) {

    private val _entries = MutableStateFlow(trimQuietEntries(read(), System.currentTimeMillis()))
    val entries: StateFlow<List<QuietEntry>> = _entries.asStateFlow()

    fun add(entry: QuietEntry) = update { addQuietEntry(it, entry, System.currentTimeMillis()) }

    fun remove(key: String) = update { list -> list.filter { it.key != key } }

    fun clear() = update { emptyList() }

    @Synchronized
    private fun update(change: (List<QuietEntry>) -> List<QuietEntry>) {
        val updated = change(_entries.value)
        _entries.value = updated
        write(updated)
    }

    private fun read(): List<QuietEntry> = try {
        quietEntriesFromJson(String(file.readFully(), Charsets.UTF_8))
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(entries: List<QuietEntry>) {
        val out = try { file.startWrite() } catch (_: Exception) { return }
        try {
            out.write(quietEntriesToJson(entries).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (_: Exception) {
            file.failWrite(out)
        }
    }

    companion object {
        private const val FILE_NAME = "quiet_notifications.json"
        @Volatile private var instance: QuietStore? = null

        fun get(context: Context): QuietStore = instance ?: synchronized(this) {
            instance ?: QuietStore(AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))).also { instance = it }
        }
    }
}
