package com.walkman.tv.data.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

private const val MAX_HISTORY = 20

/** Recent search keywords, most-recent first, deduplicated and capped at [MAX_HISTORY]. */
class SearchHistoryStore(context: Context) {
    private val store = JsonStore(
        File(context.filesDir, "search_history.json"),
        ListSerializer(String.serializer()),
        emptyList<String>(),
    )

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _items.value = store.load()
    }

    suspend fun record(keyword: String) = withContext(Dispatchers.IO) {
        val k = keyword.trim()
        if (k.isEmpty()) return@withContext
        val next = (listOf(k) + _items.value.filter { it != k }).take(MAX_HISTORY)
        _items.value = next
        store.save(next)
    }

    suspend fun remove(keyword: String) = withContext(Dispatchers.IO) {
        val next = _items.value.filter { it != keyword }
        _items.value = next
        store.save(next)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        _items.value = emptyList()
        store.save(emptyList())
    }
}
