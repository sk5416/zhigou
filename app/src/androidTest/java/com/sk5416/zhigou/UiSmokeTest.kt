package com.sk5416.zhigou

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.os.SystemClock
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
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
        WindowEvidence.prepare(device)
        device.executeShellCommand("svc wifi disable")
        device.executeShellCommand("svc data disable")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
            device.wait(Until.hasObject(By.text("开始学习")), 10_000)
            device.findObject(By.text("开始学习"))?.click()
            assertTrue("home should be visible", device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 20_000))
            screenshot(device, "home")
            device.findObject(By.text("继续阅读 · C00")).click()
            assertTrue(device.wait(Until.hasObject(By.text("本章练习")), 10_000))
            // Inspect the rendered local document without enabling JavaScript.
            ReaderProbe.assertText(scenario, "C00", "结论先行")
            screenshot(device, "reader")
            device.findObject(By.text("本章练习")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("管理员演示订单查询")), 10_000))
            screenshot(device, "practice")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
            device.findObject(By.text("课程")).click()
            assertTrue(device.wait(Until.hasObject(By.text("搜索课程和附录")), 10_000))
            screenshot(device, "courses")
            // This phrase occurs only in C06 body, after a fenced Python comment.
            // It verifies that search and the renderer agree on actual heading anchors.
            val codeSearch = device.findObject(By.clazz("android.widget.EditText"))
            codeSearch.click()
            codeSearch.text = "错误行不能静默丢弃"
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.textStartsWith("C06 ·")), 10_000))
            device.findObject(By.textStartsWith("C06 ·")).click()
            ReaderProbe.assertText(scenario, "C06", "错误行不能静默丢弃。")
            screenshot(device, "code-search-reader")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("搜索课程和附录")), 10_000))
            val databaseSearch = device.findObject(By.clazz("android.widget.EditText"))
            databaseSearch.click()
            databaseSearch.text = "把前提放进写入"
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.textStartsWith("C24 ·")), 10_000))
            device.findObject(By.textStartsWith("C24 ·")).click()
            ReaderProbe.assertText(scenario, "C24", "UPDATE stock")
            screenshot(device, "database-reader")
            val relation = requireNotNull(device.findObject(By.text("C26")))
            assertTrue("related chapter code should fit on one line on a small screen", relation.visibleBounds.width() >= relation.visibleBounds.height())
            device.findObject(By.text("本章练习")).click()
            assertTrue("new question bank must be usable offline", device.wait(Until.hasObject(By.textContains("关于事务的原子性")), 10_000))
            screenshot(device, "database-practice")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
            device.findObject(By.text("课程")).click()
            assertTrue(device.wait(Until.hasObject(By.text("搜索课程和附录")), 10_000))
            val frontendSearch = device.findObject(By.clazz("android.widget.EditText"))
            frontendSearch.click()
            frontendSearch.text = "静态HTML片段"
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.textStartsWith("C27 ·")), 10_000))
            device.findObject(By.textStartsWith("C27 ·")).click()
            ReaderProbe.assertText(scenario, "C27", "project-code")
            screenshot(device, "frontend-reader")
            device.findObject(By.text("本章练习")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("HTML、CSS与JavaScript的职责")), 10_000))
            screenshot(device, "frontend-practice")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
            device.findObject(By.text("课程")).click()
            assertTrue(device.wait(Until.hasObject(By.text("搜索课程和附录")), 10_000))
            val architectureSearch = device.findObject(By.clazz("android.widget.EditText"))
            architectureSearch.click()
            architectureSearch.text = "虚构决策示例"
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.textStartsWith("C36 ·")), 10_000))
            device.findObject(By.textStartsWith("C36 ·")).click()
            ReaderProbe.assertText(scenario, "C36", "ADR-007")
            screenshot(device, "architecture-reader")
            device.findObject(By.text("本章练习")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("ADR最主要保存什么")), 10_000))
            screenshot(device, "architecture-practice")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
            device.findObject(By.text("课程")).click()
            assertTrue(device.wait(Until.hasObject(By.text("搜索课程和附录")), 10_000))
            openNewLesson(scenario, device, "登录不等于拥有所有数据", "C43", "登录不等于拥有所有数据", "认证与授权的区别是什么", "security")
            openNewLesson(scenario, device, "原型证明部分可行", "C72", "原型证明部分可行", "原型与可交接应用主要差在哪", "complete")
            openTimeoutChapter(device)
            ReaderProbe.assertText(scenario, "C14", "调用超时只说明")
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
            screenshot(device, "self-test")
            assertTrue("self-test submit button must be reachable", scrollToText(device, "交卷并查看解析"))
            device.findObject(By.text("交卷并查看解析")).click()
            assertTrue("confirm must disclose all eight unanswered questions", device.wait(Until.hasObject(By.textContains("还有 8 题未作答")), 10_000))
            screenshot(device, "submit-confirm")
            device.findObjects(By.text("确认交卷")).last().click()
            assertTrue(device.wait(Until.gone(By.text("继续作答")), 10_000))
            repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 4 / 5, 20) }
            screenshot(device, "self-test-result")
            assertTrue("all unanswered questions must score zero", device.wait(Until.hasObject(By.textContains("已完成 · 0/8")), 10_000))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
            device.findObject(By.text("复习")).click()
            assertTrue(device.wait(Until.hasObject(By.text("复习 8 道待订正题")), 10_000))
            screenshot(device, "review")
            device.findObject(By.text("设置")).click()
            assertTrue(device.wait(Until.hasObject(By.text("深色")), 10_000))
            device.findObject(By.text("深色")).click()
            screenshot(device, "settings-before-font")
            val slider = findFontSlider()
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 28f) }
            assertTrue("font slider must support setting its accessible value", slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
            assertTrue("font should reach the requested size 28", device.wait(Until.hasObject(By.text("正文字号：28")), 10_000))
            screenshot(device, "settings-dark")
            device.findObject(By.text("学习")).click()
            assertTrue(device.wait(Until.hasObject(By.text("继续阅读 · C14")), 10_000))
            device.findObject(By.text("继续阅读 · C14")).click()
            assertTrue(device.wait(Until.hasObject(By.text("本章练习")), 10_000))
            screenshot(device, "dark-reader")
            } catch (e: Throwable) {
                WindowEvidence.capture(device, "smoke-failure")
                throw e
            }
        }
    }
    private fun openNewLesson(scenario: ActivityScenario<MainActivity>, device: UiDevice, query: String, chapter: String, body: String, question: String, name: String) {
        val search = device.findObject(By.clazz("android.widget.EditText"))
        search.click()
        search.text = query
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.textStartsWith("$chapter ·")), 10_000))
        device.findObject(By.textStartsWith("$chapter ·")).click()
        ReaderProbe.assertText(scenario, chapter, body)
        screenshot(device, "$name-reader")
        device.findObject(By.text("本章练习")).click()
        assertTrue("$chapter question bank must be usable offline", device.wait(Until.hasObject(By.textContains(question)), 10_000))
        screenshot(device, "$name-practice")
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("把知识，构成判断力。")), 10_000))
        device.findObject(By.text("课程")).click()
        assertTrue(device.wait(Until.hasObject(By.text("搜索课程和附录")), 10_000))
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
            if (node != null && node.visibleBounds.height() >= 8 && node.visibleBounds.width() > 20) return true
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5, device.displayWidth / 2, device.displayHeight / 3, 20)
            device.waitForIdle(1_000)
        }
        return false
    }
    private fun findFontSlider(): AccessibilityNodeInfo {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className == "android.widget.SeekBar" && node.rangeInfo != null) return node
            repeat(node.childCount) { node.getChild(it)?.let(queue::add) }
        }
        error("Font slider not found in accessibility tree")
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
