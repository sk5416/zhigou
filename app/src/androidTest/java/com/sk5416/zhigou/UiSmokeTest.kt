package com.sk5416.zhigou

import android.content.Context
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
import java.io.File

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
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }
}
