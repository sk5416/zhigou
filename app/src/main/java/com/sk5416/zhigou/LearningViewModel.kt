package com.sk5416.zhigou

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sk5416.zhigou.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class LearningState(
    val catalog: Catalog? = null, val readings: List<Reading> = emptyList(),
    val sessions: List<Session> = emptyList(), val attempts: List<Attempt> = emptyList(),
    val mistakes: List<Mistake> = emptyList(), val preferences: Preferences = Preferences(),
)
class LearningViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = Repository(app)
    private val operation = Mutex()
    val state = MutableStateFlow(LearningState())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val restorePreview = MutableStateFlow<Backup?>(null)
    init {
        run {
            repository.initialize()
            viewModelScope.launch {
                combine(repository.readings, repository.sessions, repository.attempts, repository.mistakes, repository.preferences) { r, s, a, m, p -> LearningState(repository.catalog, r, s, a, m, p) }
                    .catch { error.value = it.message ?: "数据读取失败" }.collect { state.value = it }
            }
        }
    }
    private fun run(action: suspend () -> Unit) { viewModelScope.launch {
        operation.withLock {
            busy.value = true
            try { action() } catch (e: Exception) { error.value = e.message ?: "操作失败，请重试" }
            finally { busy.value = false }
        }
    } }
    fun read(id: String, completed: Boolean? = null, bookmark: Boolean? = null, scroll: Int? = null) = run { repository.updateReading(id, completed, bookmark, scroll) }
    fun preferences(p: Preferences) = run { repository.setPreferences(p) }
    fun start(mode: String, chapters: Set<String>, opened: (String) -> Unit) = run { opened(repository.start(mode, chapters).id) }
    fun select(id: String, q: String, option: String) = run { repository.select(id, q, option) }
    fun navigate(id: String, index: Int) = run { repository.navigate(id, index) }
    fun submit(id: String, finish: Boolean = false) = run { repository.submit(id, finish) }
    fun export(uri: Uri) = run {
        val text = Codec.json.encodeToString(repository.snapshot())
        withContext(Dispatchers.IO) {
            requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")) { "无法写入文件" }.bufferedWriter().use { it.write(text) }
        }
    }
    fun preview(uri: Uri) = run {
        val backup = withContext(Dispatchers.IO) {
            requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 20 * 1024 * 1024) { "备份超过 20MB 上限" }
                    output.write(buffer, 0, count)
                }
                Codec.json.decodeFromString<Backup>(output.toString("UTF-8"))
            }
        }
        LearningRules.validate(backup, repository.catalog)
        restorePreview.value = backup
    }
    fun restore() = run { restorePreview.value?.let { repository.restore(it) }; restorePreview.value = null }
    fun clear() = run { repository.clear() }
}
