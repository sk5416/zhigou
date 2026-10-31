package com.sk5416.zhigou

import android.content.Context
import android.os.Process
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sk5416.zhigou.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses a dedicated database fixture, never the learner's learning.db. */
@RunWith(AndroidJUnit4::class)
class ProcessRecoveryTest {
    @Test fun persistedLearningAcrossRepositoryAndProcessRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val phase = InstrumentationRegistry.getArguments().getString("recoveryPhase")
        require(phase == null || phase in setOf("seed", "verify"))
        val fixture = File(context.filesDir, "process-recovery.expected.json")
        suspend fun seed() {
            val db = Room.databaseBuilder(context, LearningDatabase::class.java, "process-recovery-test.db").build()
            try {
                val repo = Repository(context, db)
                repo.initialize()
                repo.clear()
                repo.updateReading("C24", completed = true, bookmarked = true, scroll = 360)
                repo.setPreferences(Preferences("dark", 22, true))
                val practice = repo.start("practice", setOf("C24"))
                val first = practice.questions.first()
                repo.select(practice.id, first.id, first.options.first { it.id !in first.answers }.id)
                repo.submit(practice.id)
                repo.navigate(practice.id, 1)
                val second = practice.questions[1]
                second.answers.forEach { repo.select(practice.id, second.id, it) }
                val test = repo.start("test", setOf("C02"))
                val q = test.questions.first()
                q.answers.forEach { repo.select(test.id, q.id, it) }
                repo.navigate(test.id, 2)
                val expected = repo.snapshot()
                LearningRules.validate(expected, repo.catalog)
                assertEquals(1, expected.attempts.size)
                assertEquals(1, expected.mistakes.count { !it.corrected })
                fixture.writeText(Codec.json.encodeToString(expected), Charsets.UTF_8)
            } finally { db.close() }
        }
        suspend fun verify() {
            assertTrue("seed must survive process termination", fixture.isFile)
            val expected = Codec.json.decodeFromString<Backup>(fixture.readText(Charsets.UTF_8))
            val db = Room.databaseBuilder(context, LearningDatabase::class.java, "process-recovery-test.db").build()
            try {
                val repo = Repository(context, db)
                repo.initialize()
                val actual = repo.snapshot()
                assertEquals(expected.preferences, actual.preferences)
                assertEquals(expected.readings.sortedBy { it.chapterId }, actual.readings.sortedBy { it.chapterId })
                assertEquals(expected.sessions.sortedBy { it.id }, actual.sessions.sortedBy { it.id })
                assertEquals(expected.attempts, actual.attempts)
                assertEquals(expected.mistakes, actual.mistakes)
                assertEquals(2, actual.sessions.size)
                val practice = actual.sessions.map { Codec.json.decodeFromString<Session>(it.payload) }.single { it.mode == "practice" }
                assertFalse(practice.finished)
                assertEquals(1, practice.index)
                val selected = practice.questions[1]
                assertEquals(selected.answers.toSet(), practice.selections[selected.id])
                // Previously committed submission must remain idempotent after restart.
                repo.navigate(practice.id, 0)
                repo.submit(practice.id)
                assertEquals(1, repo.snapshot().attempts.size)
                val killedPid = InstrumentationRegistry.getArguments().getString("killedPid")
                if (phase == "verify") {
                    assertNotNull("host must supply the process it actually stopped", killedPid)
                    assertNotEquals(killedPid!!.toInt(), Process.myPid())
                    println("Verified committed learning in pid=${Process.myPid()}, stopped pid=$killedPid")
                    InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                        putString("recovery_verified_pid", Process.myPid().toString())
                    })
                }
                repo.clear()
                assertTrue(fixture.delete())
            } finally { db.close() }
        }
        when (phase) {
            "seed" -> seed()
            "verify" -> verify()
            else -> { seed(); verify() }
        }
    }
}
