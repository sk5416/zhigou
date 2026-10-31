package com.sk5416.zhigou

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sk5416.zhigou.data.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only the dev.7 repository ABI, so this same test can seed the genuine old APK. */
@RunWith(AndroidJUnit4::class)
class UpgradeTest {
    @Test fun recordsSurviveCoveringInstall() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("upgradePhase")
        require(phase == null || phase in setOf("seed", "verify"))
        args.getString("expectedVersion")?.let {
            assertEquals(it.toLong(), context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
        }
        val file = File(context.filesDir, "upgrade-test.expected.json")
        val db = Room.databaseBuilder(context, LearningDatabase::class.java, "learning.db").build()
        val repo = Repository(context, db)
        try {
            repo.initialize()
            if (phase != "verify") {
                repo.clear(); repo.setPreferences(Preferences("dark", 24, true))
                repo.updateReading("C24", true, true, 640)
                val s = repo.start("practice", setOf("C24")); val q = s.questions.first()
                repo.select(s.id, q.id, q.options.first { it.id !in q.answers }.id)
                repo.submit(s.id); repo.navigate(s.id, 1)
                repo.select(s.id, s.questions[1].id, s.questions[1].answers.first())
                val test = repo.start("test", setOf("C02"))
                repo.select(test.id, test.questions.first().id, test.questions.first().answers.first())
                file.writeText(Codec.json.encodeToString(repo.snapshot()), Charsets.UTF_8)
            }
            if (phase != "seed") {
                val expected = Codec.json.decodeFromString<Backup>(file.readText(Charsets.UTF_8))
                val actual = repo.snapshot()
                assertEquals(expected.preferences, actual.preferences)
                assertEquals(expected.readings, actual.readings)
                assertEquals(expected.sessions.sortedBy { it.id }, actual.sessions.sortedBy { it.id })
                assertEquals(expected.attempts, actual.attempts)
                assertEquals(expected.mistakes, actual.mistakes)
                val practice = actual.sessions.map { Codec.json.decodeFromString<Session>(it.payload) }.single { it.mode == "practice" }
                repo.navigate(practice.id, 0); repo.submit(practice.id)
                assertEquals(1, repo.snapshot().attempts.size)
                if (phase == "verify") InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("upgrade_verified", "true") })
                repo.clear(); file.delete()
            }
        } finally { db.close() }
    }
}
