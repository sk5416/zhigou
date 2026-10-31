package com.sk5416.zhigou.data

import org.junit.Assert.*
import org.junit.Test

class LearningRulesTest {
    private fun question(id: String = "C00-Q01", chapter: String = "C00", rev: Int = 1, answers: Set<String> = setOf("A", "C")) =
        Question(id, chapter, rev, "multiple", "test", listOf(Option("A", "one", "yes"), Option("B", "two", "no"), Option("C", "three", "yes")), answers, listOf("K1"), "基础", "判断", "误区")
    private val chapter = Chapter("C00", "导学", "导学", body = "content", html = "C00.html")
    private fun session(q: Question = question()) = Session("s1", "test", "v1", listOf(q), startedAt = 1, updatedAt = 1)
    private fun backup(s: Session = session()) = Backup(contentVersion = "v1", createdAt = 1, preferences = Preferences(), readings = listOf(Reading("C00")), sessions = listOf(SessionRow(s.id, Codec.json.encodeToString(s), 1)), attempts = emptyList(), mistakes = emptyList())
    private fun catalog(q: Question = question()) = Catalog("v1", listOf(chapter), listOf(q))

    @Test fun multiChoiceRequiresExactSet() {
        val q = question()
        assertTrue(LearningRules.correct(q, setOf("C", "A")))
        assertFalse(LearningRules.correct(q, setOf("A")))
        assertFalse(LearningRules.correct(q, setOf("A", "B", "C")))
        assertFalse(LearningRules.correct(q, emptySet()))
    }
    @Test fun singleChoiceAndUnanswered() {
        val q = question(answers = setOf("A")).copy(type = "single")
        assertTrue(LearningRules.correct(q, setOf("A")))
        assertFalse(LearningRules.correct(q, emptySet()))
        assertFalse(LearningRules.correct(q, setOf("B")))
    }
    @Test fun samplingRespectsScopeAndLimit() {
        val qs = (1..30).map { question("q$it", if (it < 5) "C01" else "C00") }
        val chosen = LearningRules.sample(qs, setOf("C00"))
        assertEquals(20, chosen.size)
        assertTrue(chosen.all { it.chapterId == "C00" })
        assertEquals(20, chosen.map { it.id }.distinct().size)
        assertEquals(4, LearningRules.sample(qs, setOf("C01")).size)
        assertTrue(LearningRules.sample(qs, emptySet()).isEmpty())
    }
    @Test fun contentRevisionDoesNotReuseOldResults() {
        assertTrue(LearningRules.compatible(session(), catalog()))
        assertFalse(LearningRules.compatible(session(), catalog(question(rev = 2))))
        assertFalse(LearningRules.compatible(session(), Catalog("v2", listOf(chapter), emptyList())))
    }
    @Test fun backupRoundTripPreservesUnfinishedSelections() {
        val original = backup(session().copy(selections = mapOf("C00-Q01" to setOf("A", "C"))))
        val restored = Codec.json.decodeFromString<Backup>(Codec.json.encodeToString(original))
        assertEquals(original, restored)
        LearningRules.validate(restored, catalog())
    }
    @Test fun editorialChangeDoesNotInvalidateHistoricalSnapshot() {
        LearningRules.validate(backup(), catalog(question().copy(misconception = "修订解析文字", knowledge = listOf("K2"))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectChangedAnswersWithoutRevision() {
        LearningRules.validate(backup(), catalog(question(answers = setOf("B"))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectUnknownChapterBeforeMutation() {
        LearningRules.validate(backup().copy(readings = listOf(Reading("C99"))), catalog())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectFutureFormat() {
        LearningRules.validate(backup().copy(format = 99), catalog())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectInvalidOptions() {
        LearningRules.validate(backup(session().copy(selections = mapOf("C00-Q01" to setOf("Z")))), catalog())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectDuplicateReadings() {
        LearningRules.validate(backup().copy(readings = listOf(Reading("C00"), Reading("C00"))), catalog())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectSubmissionWithoutAttempt() {
        LearningRules.validate(backup(session().copy(submitted = setOf("C00-Q01"), finished = true)), catalog())
    }
    @Test fun submittedSnapshotValidatedAgainstStoredAnswer() {
        val s = session().copy(submitted = setOf("C00-Q01"), finished = true, selections = mapOf("C00-Q01" to setOf("A", "C")))
        val b = backup(s).copy(attempts = listOf(Attempt(s.id, "C00-Q01", 1, "C00", "[\"A\",\"C\"]", true, "test", 1)))
        LearningRules.validate(b, catalog())
    }
}
