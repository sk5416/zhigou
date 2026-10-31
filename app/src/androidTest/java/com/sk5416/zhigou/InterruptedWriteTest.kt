package com.sk5416.zhigou

import android.content.Context
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sk5416.zhigou.data.*
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable emulator only: dedicated Room/journal files; shared preferences are a fixture. */
@RunWith(AndroidJUnit4::class)
class InterruptedWriteTest {
    @Test fun interruptionsKeepLearningAtomic() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("writePhase")
        val points = args.getString("writePoint")?.let { listOf(WritePoint.valueOf(it)) } ?: WritePoint.entries
        require(phase == null || phase in setOf("interrupt", "verify"))
        val expectedFile = File(context.filesDir, "write-test.expected.json")
        val journalFile = File(context.filesDir, "write-test.journal.json")
        val marker = File(context.filesDir, "write-test.marker.json")
        fun durable(file: File, value: String) = FileOutputStream(file).use { it.write(value.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        for (point in points) {
            val db = Room.databaseBuilder(context, LearningDatabase::class.java, "interrupted-write-test.db").build()
            val repo = Repository(context, db, journalFile)
            try {
                repo.initialize()
                if (phase != "verify") {
                    repo.clear()
                    repo.updateReading("C24", true, true, 420)
                    repo.setPreferences(Preferences("dark", 22, true))
                    val s = repo.start("practice", setOf("C24"))
                    val q = s.questions.first()
                    repo.select(s.id, q.id, q.options.first { it.id !in q.answers }.id)
                    durable(expectedFile, Codec.json.encodeToString(repo.snapshot()))
                    repo.checkpoint = { reached ->
                        if (reached == point) {
                            if (phase == "interrupt") {
                                durable(marker, "{\"pid\":${Process.myPid()},\"point\":\"$point\"}")
                                awaitCancellation() // host confirms this point then kills the process
                            } else throw IllegalStateException("controlled write interruption")
                        }
                    }
                    try {
                        if (point == WritePoint.ATTEMPT_INSERTED) repo.submit(s.id)
                        else repo.restore(repo.snapshot().copy(readings = emptyList(), sessions = emptyList(), attempts = emptyList(), mistakes = emptyList(), preferences = Preferences("light", 14, true)))
                        fail("checkpoint must interrupt the write")
                    } catch (e: IllegalStateException) { assertEquals("controlled write interruption", e.message) }
                    repo.checkpoint = {}
                }
                val expected = Codec.json.decodeFromString<Backup>(expectedFile.readText(Charsets.UTF_8))
                val actual = repo.snapshot()
                assertEquals(expected.readings, actual.readings)
                assertEquals(expected.sessions, actual.sessions)
                assertEquals(expected.attempts, actual.attempts)
                assertEquals(expected.mistakes, actual.mistakes)
                assertEquals(expected.preferences, actual.preferences)
                assertFalse("recovery journal should be consumed", journalFile.exists())
                // Retrying after an uncommitted submission creates exactly one complete result.
                val s = Codec.json.decodeFromString<Session>(actual.sessions.single().payload)
                repo.submit(s.id); repo.submit(s.id)
                val after = repo.snapshot()
                assertEquals(1, after.attempts.size)
                assertEquals(1, after.mistakes.size)
                assertTrue(Codec.json.decodeFromString<Session>(after.sessions.single().payload).submitted.contains(s.questions.first().id))
                if (phase == "verify") InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                    putString("write_verified_pid", Process.myPid().toString())
                    putString("write_verified_point", point.name)
                })
                repo.clear()
                marker.delete(); expectedFile.delete()
            } finally { db.close() }
        }
    }
}
