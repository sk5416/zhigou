package com.sk5416.zhigou

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @Test fun launchReadPracticeAndSearchOffline() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("svc wifi disable")
        device.executeShellCommand("svc data disable")
        ActivityScenario.launch(MainActivity::class.java).use {
            device.wait(Until.hasObject(By.text("开始学习")), 10_000)
            device.findObject(By.text("开始学习"))?.click()
            assertTrue("home should be visible", device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 20_000))
            screenshot(device, "home")
            device.findObject(By.text("继续阅读 · C00")).click()
            assertTrue(device.wait(Until.hasObject(By.text("本章练习")), 10_000))
            // Wait for the local WebView and inspect its accessible text without enabling JavaScript.
            assertTrue("offline HTML should render", device.wait(Until.hasObject(By.textContains("结论先行")), 15_000))
            screenshot(device, "reader")
            device.findObject(By.text("本章练习")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("管理员演示订单查询")), 10_000))
            screenshot(device, "practice")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
            device.findObject(By.text("课程")).click()
            assertTrue(device.wait(Until.hasObject(By.text("搜索课程和附录")), 10_000))
            screenshot(device, "courses")
            openTimeoutChapter(device)
            assertTrue(device.wait(Until.hasObject(By.textContains("调用超时只说明")), 15_000))
            screenshot(device, "c14-reader")
            device.findObject(By.text("标记已读")).click()
            assertTrue(device.wait(Until.hasObject(By.text("已读 ✓")), 10_000))
            device.findObject(By.text("收藏")).click()
            assertTrue(device.wait(Until.hasObject(By.text("已收藏 ★")), 10_000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("只看书签")), 10_000))
            device.findObject(By.text("只看书签")).click()
            assertTrue(device.wait(Until.hasObject(By.textStartsWith("C14 ·")), 10_000))
            assertFalse(device.hasObject(By.textStartsWith("C00 ·")))
            screenshot(device, "bookmarks")
            device.findObject(By.text("选择已读")).click()
            assertTrue(device.wait(Until.hasObject(By.text("所选 1 章自测 · 最多 20 题")), 10_000))
            device.findObject(By.text("所选 1 章自测 · 最多 20 题")).click()
            assertTrue(device.wait(Until.hasObject(By.text("自测 · 1/8")), 10_000))
            assertTrue(scrollToText(device, "交卷并查看解析"))
            device.findObject(By.text("交卷并查看解析")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("还有 8 题未作答")), 10_000))
            device.findObjects(By.text("确认交卷")).last().click()
            assertTrue(device.wait(Until.gone(By.text("继续作答")), 10_000))
            repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 4 / 5, 20) }
            assertTrue(device.wait(Until.hasObject(By.textContains("已完成 · 0/8")), 10_000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
            device.findObject(By.text("复习")).click()
            assertTrue(device.wait(Until.hasObject(By.text("复习 8 道待订正题")), 10_000))
            screenshot(device, "review")
            device.findObject(By.text("设置")).click()
            assertTrue(device.wait(Until.hasObject(By.text("深色")), 10_000))
            device.findObject(By.text("深色")).click()
            val slider = device.findObject(By.clazz("android.widget.SeekBar"))
            val bounds = slider.visibleBounds
            device.click(bounds.right - 4, bounds.centerY())
            assertTrue(device.wait(Until.hasObject(By.text("正文字号：28")), 10_000))
            screenshot(device, "settings-dark")
            device.findObject(By.text("学习")).click()
            assertTrue(device.wait(Until.hasObject(By.text("继续阅读 · C14")), 10_000))
            device.findObject(By.text("继续阅读 · C14")).click()
            assertTrue(device.wait(Until.hasObject(By.text("本章练习")), 10_000))
            screenshot(device, "dark-reader")
        }
    }
    private fun openTimeoutChapter(device: UiDevice) {
        val search = device.findObject(By.clazz("android.widget.EditText"))
        search.click()
        search.text = "超时、重试、幂等与限流"
        device.pressBack() // dismiss the keyboard, keeping the results on screen
        assertTrue(device.wait(Until.hasObject(By.textStartsWith("C14 ·")), 10_000))
        device.findObject(By.textStartsWith("C14 ·")).click()
    }
    private fun scrollToText(device: UiDevice, text: String): Boolean {
        // Compose scroll containers need not expose the legacy SCROLLABLE selector.
        // Exercise the real gesture and keep a strict bound instead of weakening assertions.
        repeat(8) {
            val node = device.findObject(By.text(text))
            if (node != null && node.visibleBounds.height() > 20) return true
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 3, 20)
            device.waitForIdle(1_000)
        }
        return false
    }
    private fun screenshot(device: UiDevice, name: String) {
        device.waitForIdle(2_000)
        // Accessibility can become ready before Chromium submits its first drawn frame.
        SystemClock.sleep(800)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/zhigou-tests")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        var visibleReader = true
        if (name.endsWith("reader")) {
            var ink = 0
            for (y in bitmap.height / 4 until bitmap.height * 3 / 4) {
                for (x in 8 until bitmap.width - 8) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) < 120 && android.graphics.Color.green(pixel) < 120 && android.graphics.Color.blue(pixel) < 120) ink++
                }
            }
            val bounds = device.findObject(By.clazz("android.webkit.WebView"))?.visibleBounds
            println("Reader drawn text pixels: $ink; WebView bounds: $bounds")
            visibleReader = ink > 80
        }
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        assertTrue("reader must draw visible text, not only expose accessibility nodes", visibleReader)
    }
}
