package com.sk5416.zhigou.data

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Context.preferencesStore by preferencesDataStore("preferences")
internal enum class WritePoint { ATTEMPT_INSERTED, RESTORE_DATABASE_COMMITTED, RESTORE_PREFERENCES_COMMITTED }
class Repository(private val context: Context, database: LearningDatabase?, journalFile: File) {
    constructor(context: Context, database: LearningDatabase? = null) : this(context, database, File(context.filesDir, "restore-journal.json"))
    private val db = database ?: Room.databaseBuilder(context, LearningDatabase::class.java, "learning.db").build()
    private val dao = db.dao()
    private val mutex = Mutex()
    private val journal = AtomicFile(journalFile)
    // Per-instance fault injection for instrumented transaction/process-death tests.
    internal var checkpoint: suspend (WritePoint) -> Unit = {}
    lateinit var catalog: Catalog; private set
    val readings = dao.readings()
    val sessions = dao.sessions().map { rows -> rows.map { Codec.json.decodeFromString<Session>(it.payload) } }
    val attempts = dao.attempts()
    val mistakes = dao.mistakes()
    val preferences = context.preferencesStore.data.map { p -> Preferences(p[stringPreferencesKey("theme")] ?: "system", p[intPreferencesKey("font")] ?: 18, p[booleanPreferencesKey("welcomed")] ?: false) }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        catalog = context.assets.open("catalog.json").bufferedReader().use { Codec.json.decodeFromString<Catalog>(it.readText()) }
        // A durable rollback journal covers the Room + DataStore boundary, including process death.
        if (journal.baseFile.exists() || File(journal.baseFile.path + ".bak").exists()) {
            val old = journal.openRead().bufferedReader().use { Codec.json.decodeFromString<Backup>(it.readText()) }
            replaceRows(old)
            setPreferences(old.preferences)
            journal.delete()
        }
        db.withTransaction { dao.clearIndex(); dao.putIndex(catalog.chapters.filter { it.published }.map { ChapterIndex(it.id, it.title, it.body) }) }
    }
    suspend fun setPreferences(p: Preferences) {
        require(p.theme in setOf("system", "light", "dark") && p.font in 14..28)
        context.preferencesStore.edit { it[stringPreferencesKey("theme")] = p.theme; it[intPreferencesKey("font")] = p.font; it[booleanPreferencesKey("welcomed")] = p.welcomed }
    }
    suspend fun updateReading(id: String, completed: Boolean? = null, bookmarked: Boolean? = null, scroll: Int? = null) = mutex.withLock {
        val old = dao.reading(id) ?: Reading(id)
        dao.putReading(old.copy(completed = completed ?: old.completed, bookmarked = bookmarked ?: old.bookmarked, scroll = scroll?.coerceAtLeast(0) ?: old.scroll, updatedAt = System.currentTimeMillis()))
    }
    suspend fun start(mode: String, chapters: Set<String>): Session = mutex.withLock {
        val qs = when (mode) {
            "test" -> LearningRules.sample(catalog.questions, chapters)
            "review" -> {
                val wrong = dao.allMistakes().filter { !it.corrected }.map { it.questionId to it.revision }.toSet()
                catalog.questions.filter { (it.id to it.revision) in wrong && it.chapterId in chapters }
            }
            else -> catalog.questions.filter { it.chapterId in chapters }
        }
        require(qs.isNotEmpty()) { "所选范围没有可练习题目" }
        val now = System.currentTimeMillis()
        Session(UUID.randomUUID().toString(), mode, catalog.version, qs, startedAt = now, updatedAt = now).also { save(it) }
    }
    private suspend fun save(s: Session) { dao.putSession(SessionRow(s.id, Codec.json.encodeToString(s), s.updatedAt)) }
    private suspend fun get(id: String) = Codec.json.decodeFromString<Session>(requireNotNull(dao.session(id)).payload)
    suspend fun select(id: String, question: String, option: String) = mutex.withLock {
        val s = get(id)
        require(LearningRules.compatible(s, catalog)) { "题目已修订，请新建练习；原记录仍保留" }
        if (!s.finished && question !in s.submitted) {
            val q = s.questions.first { it.id == question }
            require(q.options.any { it.id == option })
            val old = s.selections[question].orEmpty()
            val selected = if (q.type == "single") setOf(option) else if (option in old) old - option else old + option
            save(s.copy(selections = s.selections + (question to selected), updatedAt = System.currentTimeMillis()))
        }
    }
    suspend fun navigate(id: String, index: Int) = mutex.withLock {
        val s = get(id); save(s.copy(index = index.coerceIn(s.questions.indices), updatedAt = System.currentTimeMillis()))
    }
    suspend fun submit(id: String, finish: Boolean = false) = mutex.withLock {
        db.withTransaction {
            val s = get(id)
            if (s.finished) return@withTransaction
            require(LearningRules.compatible(s, catalog)) { "题目已修订，请新建练习" }
            val pending = if (finish) s.questions.filter { it.id !in s.submitted } else listOf(s.questions[s.index]).filter { it.id !in s.submitted }
            if (pending.isEmpty()) return@withTransaction
            if (!finish) require(s.selections[pending.firstOrNull()?.id].orEmpty().isNotEmpty()) { "请先选择答案" }
            val now = System.currentTimeMillis()
            for (q in pending) {
                val chosen = s.selections[q.id].orEmpty()
                val correct = LearningRules.correct(q, chosen)
                val inserted = dao.putAttempt(Attempt(s.id, q.id, q.revision, q.chapterId, Codec.json.encodeToString(chosen), correct, s.mode, now))
                checkpoint(WritePoint.ATTEMPT_INSERTED)
                if (inserted != -1L) {
                    val old = dao.mistake(q.id, q.revision)
                    if (!correct || (old != null && s.mode == "review")) dao.putMistake(Mistake(q.id, q.revision, q.chapterId, correct, now))
                }
            }
            val submitted = s.submitted + pending.map { it.id }
            save(s.copy(submitted = submitted, finished = finish || submitted.size == s.questions.size, updatedAt = now))
        }
    }
    suspend fun snapshot(): Backup = mutex.withLock { snapshotUnlocked() }
    private suspend fun snapshotUnlocked() = db.withTransaction {
        Backup(contentVersion = catalog.version, createdAt = System.currentTimeMillis(), preferences = preferences.first(), readings = dao.allReadings(), sessions = dao.allSessions(), attempts = dao.allAttempts(), mistakes = dao.allMistakes())
    }
    private suspend fun replaceRows(b: Backup) = db.withTransaction {
        dao.clearReadings(); dao.clearSessions(); dao.clearAttempts(); dao.clearMistakes()
        dao.putReadings(b.readings); dao.putSessions(b.sessions); dao.putAttempts(b.attempts); dao.putMistakes(b.mistakes)
    }
    suspend fun restore(b: Backup) = mutex.withLock {
        withContext(Dispatchers.IO) {
            LearningRules.validate(b, catalog)
            val old = snapshotUnlocked()
            val stream = journal.startWrite()
            try { stream.write(Codec.json.encodeToString(old).toByteArray()); journal.finishWrite(stream) }
            catch (e: Exception) { journal.failWrite(stream); throw e }
            try {
                replaceRows(b)
                checkpoint(WritePoint.RESTORE_DATABASE_COMMITTED)
                setPreferences(b.preferences)
                checkpoint(WritePoint.RESTORE_PREFERENCES_COMMITTED)
                journal.delete()
            }
            catch (e: Exception) { replaceRows(old); setPreferences(old.preferences); journal.delete(); throw e }
        }
    }
    suspend fun clear() = restore(Backup(contentVersion = catalog.version, createdAt = System.currentTimeMillis(), preferences = Preferences(), readings = emptyList(), sessions = emptyList(), attempts = emptyList(), mistakes = emptyList()))
}
