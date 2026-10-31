package com.sk5416.zhigou

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
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
        }
    }
    private fun screenshot(device: UiDevice, name: String) {
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
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }
}
