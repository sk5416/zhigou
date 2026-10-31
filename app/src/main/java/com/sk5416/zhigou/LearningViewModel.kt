package com.sk5416.zhigou

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sk5416.zhigou.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

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
    val notice = MutableStateFlow<String?>(null)
    init {
        run {
            repository.initialize()
            viewModelScope.launch {
                combine(repository.readings, repository.sessions, repository.attempts, repository.mistakes, repository.preferences) { r, s, a, m, p -> LearningState(repository.catalog, r, s, a, m, p) }
                    .catch { error.value = it.message ?: "数据读取失败" }.collect { state.value = it }
            }
        }
    }
    private fun run(showBusy: Boolean = true, action: suspend () -> Unit) { viewModelScope.launch {
        operation.withLock {
            if (showBusy) busy.value = true
            try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.message ?: "操作失败，请重试" }
            finally { if (showBusy) busy.value = false }
        }
    } }
    fun read(id: String, completed: Boolean? = null, bookmark: Boolean? = null, scroll: Int? = null) = run(false) { repository.updateReading(id, completed, bookmark, scroll) }
    fun preferences(p: Preferences) = run(false) { repository.setPreferences(p) }
    fun start(mode: String, chapters: Set<String>, opened: (String) -> Unit) = run { opened(repository.start(mode, chapters).id) }
    fun select(id: String, q: String, option: String) = run(false) { repository.select(id, q, option) }
    fun navigate(id: String, index: Int) = run(false) { repository.navigate(id, index) }
    fun submit(id: String, finish: Boolean = false) = run { repository.submit(id, finish) }
    fun export(uri: Uri) = run {
        val bytes = BackupFiles.encode(repository.snapshot())
        try {
            withContext(Dispatchers.IO) {
                requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt")) { "无法写入文件" }.use { it.write(bytes) }
            }
        } catch (_: IOException) { throw IllegalStateException("备份导出失败，文件可能不完整，请重新导出") }
        catch (_: SecurityException) { throw IllegalStateException("没有所选文件的写入权限，请重新选择保存位置") }
        notice.value = "学习备份已导出，请保管所选文件。"
    }
    fun preview(uri: Uri) = run {
        restorePreview.value = null
        val backup = try { withContext(Dispatchers.IO) {
            requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)).use { input ->
                BackupFiles.decode(input)
            }
        } } catch (_: IOException) { throw IllegalStateException("无法完整读取备份，请检查文件后重新选择；当前数据未修改") }
        catch (_: SecurityException) { throw IllegalStateException("没有所选文件的读取权限，请重新选择；当前数据未修改") }
        LearningRules.validate(backup, repository.catalog)
        restorePreview.value = backup
    }
    fun restore() = run { restorePreview.value?.let { repository.restore(it); notice.value = "学习数据和阅读设置已恢复。" }; restorePreview.value = null }
    fun clear() = run { repository.clear() }
}
