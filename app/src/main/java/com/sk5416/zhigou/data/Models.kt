package com.sk5416.zhigou.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable data class Catalog(val version: String, val chapters: List<Chapter>, val questions: List<Question>)
@Serializable data class Chapter(
    val id: String, val title: String, val part: String, val depth: String = "", val priority: String = "",
    val prerequisites: List<String> = emptyList(), val related: List<String> = emptyList(),
    val body: String, val html: String, val published: Boolean = true,
)
@Serializable data class Option(val id: String, val text: String, val explanation: String)
@Serializable data class Question(
    val id: String, val chapterId: String, val revision: Int, val type: String,
    val stem: String, val options: List<Option>, val answers: Set<String>,
    val knowledge: List<String>, val difficulty: String, val ability: String, val misconception: String,
)
@Serializable data class Preferences(val theme: String = "system", val font: Int = 18, val welcomed: Boolean = false)
@Serializable @Entity data class Reading(
    @PrimaryKey val chapterId: String, val completed: Boolean = false, val bookmarked: Boolean = false,
    val scroll: Int = 0, val updatedAt: Long = 0,
)
@Serializable data class Session(
    val id: String, val mode: String, val contentVersion: String, val questions: List<Question>,
    val selections: Map<String, Set<String>> = emptyMap(), val submitted: Set<String> = emptySet(),
    val index: Int = 0, val finished: Boolean = false, val startedAt: Long, val updatedAt: Long,
)
@Serializable @Entity data class SessionRow(@PrimaryKey val id: String, val payload: String, val updatedAt: Long)
@Serializable @Entity(primaryKeys = ["sessionId", "questionId"]) data class Attempt(
    val sessionId: String, val questionId: String, val revision: Int, val chapterId: String,
    val selected: String, val correct: Boolean, val mode: String, val answeredAt: Long,
)
@Serializable @Entity(primaryKeys = ["questionId", "revision"]) data class Mistake(
    val questionId: String, val revision: Int, val chapterId: String, val corrected: Boolean = false,
    val updatedAt: Long,
)
@Entity data class ChapterIndex(@PrimaryKey val id: String, val title: String, val body: String)
@Serializable data class Backup(
    val format: Int = 1, val app: String = "com.sk5416.zhigou", val contentVersion: String,
    val createdAt: Long, val preferences: Preferences, val readings: List<Reading>,
    val sessions: List<SessionRow>, val attempts: List<Attempt>, val mistakes: List<Mistake>,
)

object LearningRules {
    fun correct(question: Question, selected: Set<String>) = selected == question.answers
    fun sample(questions: List<Question>, chapters: Set<String>, limit: Int = 20): List<Question> =
        questions.filter { it.chapterId in chapters }.shuffled().take(limit.coerceAtLeast(0))
    fun compatible(session: Session, catalog: Catalog): Boolean {
        val current = catalog.questions.associateBy { it.id }
        return session.questions.all { current[it.id]?.revision == it.revision }
    }
    fun validate(backup: Backup, catalog: Catalog) {
        require(backup.format == 1 && backup.app == "com.sk5416.zhigou") { "备份格式或应用不兼容" }
        require(backup.preferences.theme in setOf("system", "light", "dark") && backup.preferences.font in 14..28) { "阅读设置无效" }
        val chapters = catalog.chapters.map { it.id }.toSet()
        require(backup.readings.map { it.chapterId }.distinct().size == backup.readings.size) { "阅读记录重复" }
        require(backup.readings.all { it.chapterId in chapters && it.scroll >= 0 }) { "备份包含不存在的章节或无效位置" }
        require(backup.sessions.map { it.id }.distinct().size == backup.sessions.size) { "练习会话重复" }
        require(backup.attempts.map { it.sessionId to it.questionId }.distinct().size == backup.attempts.size) { "答题记录重复" }
        require(backup.mistakes.map { it.questionId to it.revision }.distinct().size == backup.mistakes.size) { "错题记录重复" }
        val sessions = backup.sessions.associate { row ->
            val s = Codec.json.decodeFromString<Session>(row.payload)
            require(s.id == row.id && s.mode in setOf("practice", "test", "review") && s.questions.isNotEmpty()) { "会话无效" }
            require(s.index in s.questions.indices && s.questions.map { it.id }.distinct().size == s.questions.size) { "会话索引无效" }
            val ids = s.questions.associateBy { it.id }
            require(s.questions.all { q -> q.chapterId in chapters && q.revision > 0 && q.answers.isNotEmpty() && q.answers.all { a -> q.options.any { it.id == a } } }) { "会话题目无效" }
            val current = catalog.questions.associateBy { it.id }
            require(s.questions.all { q -> current[q.id]?.let { latest -> q.chapterId == latest.chapterId && q.revision <= latest.revision && (q.revision != latest.revision || q == latest) } == true }) { "备份包含不存在或不匹配的题目修订" }
            require(s.selections.all { (id, chosen) -> ids[id]?.let { q -> chosen.all { a -> q.options.any { it.id == a } } } == true }) { "作答选项无效" }
            require(s.submitted.all { it in ids } && (!s.finished || s.submitted.size == ids.size)) { "提交状态无效" }
            row.id to s
        }
        require(backup.attempts.all { a ->
            val s = sessions[a.sessionId]
            val q = s?.questions?.find { it.id == a.questionId }
            q != null && q.chapterId == a.chapterId && q.revision == a.revision && a.mode == s.mode &&
                a.questionId in s.submitted && Codec.json.decodeFromString<Set<String>>(a.selected) == s.selections[a.questionId].orEmpty() &&
                a.correct == correct(q, s.selections[a.questionId].orEmpty())
        }) { "答题历史与会话不一致" }
        require(sessions.values.all { s -> s.submitted.all { id -> backup.attempts.any { it.sessionId == s.id && it.questionId == id } } }) { "提交记录缺失" }
        require(backup.mistakes.all { it.chapterId in chapters && it.revision > 0 && backup.attempts.any { a -> a.questionId == it.questionId && a.revision == it.revision && !a.correct } }) { "错题引用无效" }
    }
}
