package com.walkman.tv.data.store

import android.content.Context
import com.walkman.tv.data.model.LocalFolderRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Persists user-imported local-folder records. Spec §8.2.
 *
 * Each record anchors a SAF tree-Uri the user previously granted us persistent access to.
 * The Uri itself stays a string until we need to play something inside — we lazy-resolve via
 * [com.walkman.tv.playback.local.LocalMusicStore.fileURI] then.
 */
class LocalFolderStore(context: Context) {
    private val store = JsonStore(
        File(context.filesDir, "localFolders.json"),
        ListSerializer(LocalFolderRecord.serializer()),
        emptyList(),
    )

    private val _folders = MutableStateFlow<List<LocalFolderRecord>>(emptyList())
    val folders: StateFlow<List<LocalFolderRecord>> = _folders.asStateFlow()

    suspend fun loadAll() {
        _folders.value = withContext(Dispatchers.IO) { store.load() }
    }

    suspend fun add(record: LocalFolderRecord) {
        _folders.value = _folders.value + record
        persist()
    }

    suspend fun remove(id: String) {
        _folders.value = _folders.value.filter { it.id != id }
        persist()
    }

    suspend fun rename(id: String, name: String) {
        if (name.isBlank()) return
        _folders.value = _folders.value.map { r -> if (r.id == id) r.copy(name = name) else r }
        persist()
    }

    fun find(id: String): LocalFolderRecord? = _folders.value.firstOrNull { it.id == id }

    private suspend fun persist() = withContext(Dispatchers.IO) { store.save(_folders.value) }
}
