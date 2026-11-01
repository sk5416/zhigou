package com.sk5416.zhigou

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.sk5416.zhigou.data.*
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real DocumentsUI interactions on a disposable emulator, with fictional learning records. */
@RunWith(AndroidJUnit4::class)
class BackupUiTest {
    @Test fun systemPickerRoundTripCancelAndCorruptDocument() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        WindowEvidence.prepare(device)
        val db = Room.databaseBuilder(context, LearningDatabase::class.java, "learning.db").build()
        val repo = Repository(context, db)
        repo.initialize(); repo.clear()
        repo.setPreferences(Preferences("dark", 22, true))
        repo.updateReading("C24", true, true, 320)
        val s = repo.start("practice", setOf("C24"))
        val q = s.questions.first()
        repo.select(s.id, q.id, q.options.first { it.id !in q.answers }.id)
        repo.submit(s.id); repo.navigate(s.id, 1)
        val unfinished = repo.start("test", setOf("C02"))
        repo.select(unfinished.id, unfinished.questions.first().id, unfinished.questions.first().answers.first())
        val expected = repo.snapshot()
        val name = "zhigou-ui-${System.currentTimeMillis()}.backup.json"
        val brokenName = "zhigou-ui-broken-${System.currentTimeMillis()}.json"
        val resolver = context.contentResolver
        val broken = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, brokenName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download")
        }))
        resolver.openOutputStream(broken)!!.use { it.write("{broken fixture".toByteArray()) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                try {
                assertTrue(device.wait(Until.hasObject(By.text("设置")), 20_000))
                device.findObject(By.text("设置")).click()
                assertTrue(device.wait(Until.hasObject(By.text("导出学习备份")), 10_000))
                device.findObject(By.text("导出学习备份")).click()
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10_000))
                device.pressBack()
                if (!device.wait(Until.hasObject(By.text("导出学习备份")), 1_000)) device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.text("导出学习备份")), 10_000))
                same(expected, repo.snapshot())
                device.findObject(By.text("导出学习备份")).click()
                downloads(device)
                val filename = requireNotNull(device.findObject(By.clazz("android.widget.EditText")))
                filename.click(); filename.text = name
                device.pressBack()
                val save = requireNotNull(device.findObject(By.text(Pattern.compile("(?i)save|保存"))))
                save.click()
                assertTrue(device.wait(Until.hasObject(By.text("学习备份已导出，请保管所选文件。")), 15_000))
                capture(device, "backup-exported")
                device.findObject(By.text("知道了")).click()
                // Change every data category; preview/cancel must preserve this replacement.
                repo.restore(expected.copy(readings = listOf(Reading("C02", bookmarked = true)), sessions = emptyList(), attempts = emptyList(), mistakes = emptyList(), preferences = Preferences("light", 18, true)))
                val before = repo.snapshot()
                choose(device, name)
                assertTrue(device.wait(Until.hasObject(By.text("确认替换学习数据")), 10_000))
                capture(device, "backup-preview")
                same(before, repo.snapshot())
                device.findObject(By.text("取消")).click()
                same(before, repo.snapshot())
                choose(device, name)
                assertTrue(device.wait(Until.hasObject(By.text("替换恢复")), 10_000))
                device.findObject(By.text("替换恢复")).click()
                assertTrue(device.wait(Until.hasObject(By.text("学习数据和阅读设置已恢复。")), 15_000))
                same(expected, repo.snapshot())
                device.findObject(By.text("知道了")).click()
                choose(device, brokenName)
                assertTrue("corrupt-document error must appear after returning from DocumentsUI", device.wait(Until.hasObject(By.text("文件不是有效的知构备份，未修改当前数据")), 10_000))
                capture(device, "backup-corrupt")
                same(expected, repo.snapshot())
                device.findObject(By.text("知道了")).click()
                device.findObject(By.text("选择备份并预览恢复")).click()
                assertNotNull(device.wait(Until.findObject(By.desc(Pattern.compile("Show roots|Open navigation drawer|显示根目录|打开导航抽屉"))), 10_000))
                device.pressBack()
                assertTrue(device.wait(Until.hasObject(By.text("导出学习备份")), 10_000))
                same(expected, repo.snapshot())
                } catch (e: Throwable) {
                    // Capture while the failing activity/window still exists, before use closes it.
                    WindowEvidence.capture(device, "backup-failure")
                    throw e
                }
            }
        } finally {
            resolver.delete(broken, null, null)
            // These files and learning.db are test fixtures on a disposable emulator.
            repo.clear(); db.close()
        }
    }
    private fun downloads(device: UiDevice) {
        val roots = device.wait(Until.findObject(By.desc(Pattern.compile("Show roots|Open navigation drawer|显示根目录|打开导航抽屉"))), 10_000)
        requireNotNull(roots) { "DocumentsUI roots button missing" }.click()
        assertTrue(device.wait(Until.hasObject(By.text(Pattern.compile("Downloads|下载"))), 10_000))
        device.findObjects(By.text(Pattern.compile("Downloads|下载"))).last().click()
        device.waitForIdle(1_000)
    }
    private fun choose(device: UiDevice, name: String) {
        device.findObject(By.text("选择备份并预览恢复")).click()
        downloads(device)
        assertTrue("exported document must be visible in Downloads", device.wait(Until.hasObject(By.text(name)), 10_000))
        device.findObject(By.text(name)).click()
    }
    private fun same(expected: Backup, actual: Backup) {
        assertEquals(expected.readings, actual.readings)
        assertEquals(expected.sessions.sortedBy { it.id }, actual.sessions.sortedBy { it.id })
        assertEquals(expected.attempts, actual.attempts)
        assertEquals(expected.mistakes, actual.mistakes)
        assertEquals(expected.preferences, actual.preferences)
    }
    private fun capture(device: UiDevice, name: String) {
        device.waitForIdle(1_000); SystemClock.sleep(500)
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/zhigou-tests")
        }))
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        resolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
