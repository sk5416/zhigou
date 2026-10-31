package com.sk5416.zhigou

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sk5416.zhigou.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun duplicateSubmitWrongCorrectionBackupAndRecovery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LearningDatabase::class.java).build()
        val repo = Repository(context, db)
        repo.initialize()
        repo.clear()
        val p = repo.start("practice", setOf("C00"))
        val q = p.questions.first()
        val wrong = q.options.first { it.id !in q.answers }.id
        repo.select(p.id, q.id, wrong)
        repo.submit(p.id)
        // Repeated UI submits must be no-ops, including the last question.
        repo.submit(p.id)
        assertEquals(1, repo.attempts.first().size)
        assertEquals(1, repo.mistakes.first().count { !it.corrected })
        val review = repo.start("review", setOf("C00"))
        for (answer in review.questions.first().answers) repo.select(review.id, q.id, answer)
        repo.submit(review.id)
        repo.submit(review.id)
        assertEquals(2, repo.attempts.first().size)
        assertTrue(repo.mistakes.first().single().corrected)
        repo.updateReading("C00", completed = true, bookmarked = true, scroll = 150)
        repo.setPreferences(Preferences("dark", 22, true))
        val b = repo.snapshot()
        LearningRules.validate(b, repo.catalog)
        repo.clear()
        assertTrue(repo.attempts.first().isEmpty())
        repo.restore(b)
        assertEquals(2, repo.attempts.first().size)
        assertEquals(150, repo.readings.first().single().scroll)
        assertEquals(22, repo.preferences.first().font)
        try {
            repo.restore(b.copy(format = 99))
            fail("future format should fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(2, repo.attempts.first().size)
        // Simulate process death after database replacement but before journal cleanup.
        File(context.filesDir, "restore-journal.json").writeText(Codec.json.encodeToString(b))
        db.dao().clearReadings()
        val recovered = Repository(context, db)
        recovered.initialize()
        assertEquals(150, recovered.readings.first().single().scroll)
        assertFalse(File(context.filesDir, "restore-journal.json").exists())
        val test = recovered.start("test", setOf("C02"))
        recovered.submit(test.id, finish = true)
        assertEquals(test.questions.size, recovered.attempts.first().count { it.sessionId == test.id && !it.correct })
        recovered.clear()
        db.close()
    }
}
