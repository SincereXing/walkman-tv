package com.walkman.tv.data.store

import android.content.Context
import com.walkman.tv.data.model.UserScript
import com.walkman.tv.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Stores imported custom-source scripts and (re)loads enabled ones into [SourceManager]. */
class ScriptStore(private val context: Context, private val sourceManager: SourceManager) {
    private val store = JsonStore(
        File(context.filesDir, "scripts.json"),
        ListSerializer(UserScript.serializer()),
        emptyList(),
    )

    private val _scripts = MutableStateFlow<List<UserScript>>(emptyList())
    val scripts: StateFlow<List<UserScript>> = _scripts.asStateFlow()

    /** Load persisted scripts and start the enabled ones. First-run users start with no
     *  custom source — they configure their own via Settings (URL / paste / file / QR upload). */
    suspend fun loadAll() {
        val list = withContext(Dispatchers.IO) { store.load() }
        _scripts.value = list
        list.filter { it.enabled }.forEach { sourceManager.load(it) }
    }

    /** Result of [import]: [replaced] is the older copy of the same script (same name + author)
     *  that this import updated in place, or null when it was added as a new source. */
    data class Imported(val script: UserScript, val replaced: UserScript?, val unchanged: Boolean = false) {
        val message: String
            get() = if (unchanged) "「${script.name}」已是最新版本"
            else if (replaced == null) "已添加音源：${script.name}"
            else "已更新音源：${script.name}（替换了同名旧版本${replaced.version.takeIf { it.isNotBlank() }?.let { " v$it" } ?: ""}）"
    }

    /**
     * Import a raw script: parse its header, load it, persist. Several sources can coexist; only
     * a script with the same name AND author counts as a new version of an existing one and
     * replaces it (keeping its place in the list). The old runtime is unloaded — previously it
     * stayed loaded in [SourceManager] invisibly — and same-named scripts by different authors
     * are kept side by side instead of silently overwriting each other.
     */
    suspend fun import(raw: String, sourceUrl: String? = null, replaceId: String? = null): Result<Imported> {
        val meta = parseHeader(raw)
        val parsed = UserScript(
            name = meta["name"] ?: "未命名脚本",
            description = meta["description"] ?: "",
            version = meta["version"] ?: "",
            author = meta["author"] ?: "",
            homepage = meta["homepage"] ?: "",
            rawScript = raw,
            enabled = true,
        )
        // [replaceId]：「更新」时明确替换哪一个（新版本改了名字 / 作者也照样替换）；否则按名字 + 作者匹配。
        val old = replaceId?.let { id -> _scripts.value.firstOrNull { it.id == id } }
            ?: _scripts.value.firstOrNull { it.name == parsed.name && it.author == parsed.author }
        if (old != null && old.rawScript == raw) {
            // 内容没变：不重新加载，只补记链接（以前从链接导入的音源，用同一链接再导入一次就能「更新」了）。
            if (sourceUrl != null && old.sourceUrl != sourceUrl) {
                _scripts.value = _scripts.value.map { if (it.id == old.id) it.copy(sourceUrl = sourceUrl) else it }
                persist()
            }
            return Result.success(Imported(old, old, unchanged = true))
        }
        // 粘贴代码更新一个原本从链接导入的音源时，保留原链接，之后还能继续「更新」。
        val script = parsed.copy(sourceUrl = sourceUrl ?: old?.sourceUrl)
        val result = sourceManager.load(script)
        return result.fold(
            onSuccess = {
                if (old != null) {
                    sourceManager.unload(old.id)
                    _scripts.value = _scripts.value.map { if (it.id == old.id) script else it }
                } else {
                    _scripts.value = _scripts.value + script
                }
                persist()
                Result.success(Imported(script, old))
            },
            onFailure = { Result.failure(it) },
        )
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val script = _scripts.value.firstOrNull { it.id == id } ?: return
        _scripts.value = _scripts.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        if (enabled) sourceManager.load(script.copy(enabled = true)) else sourceManager.unload(id)
        persist()
    }

    suspend fun remove(id: String) {
        sourceManager.unload(id)
        _scripts.value = _scripts.value.filter { it.id != id }
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) { store.save(_scripts.value) }

    companion object {
        private val headerBlock = Regex("""/\*[\s\S]*?\*/""")
        private val tagLine = Regex("""@(\w+)\s+(.+)""")

        /** Parse the leading KDoc-style header of an lx v4 script (@name / @version / etc). */
        fun parseHeader(raw: String): Map<String, String> {
            val block = headerBlock.find(raw)?.value ?: return emptyMap()
            val out = mutableMapOf<String, String>()
            tagLine.findAll(block).forEach { m ->
                out[m.groupValues[1].lowercase()] = m.groupValues[2].trim().removeSuffix("*").trim()
            }
            return out
        }
    }
}
