package com.sk5416.zhigou

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

object WindowEvidence {
    fun prepare(device: UiDevice) {
        device.setCompressedLayoutHierarchy(false)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }
    fun capture(device: UiDevice, name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        println("WINDOW_SERVICE_FLAGS ${automation.serviceInfo.flags}")
        for (window in automation.windows) {
            val root = window.root
            println("WINDOW id=${window.id} type=${window.type} title=${window.title} active=${window.isActive} focused=${window.isFocused} package=${root?.packageName} children=${root?.childCount}")
        }
        val dump = java.io.ByteArrayOutputStream(); device.dumpWindowHierarchy(dump)
        println("FAILURE_WINDOW_HIERARCHY " + dump.toString("UTF-8"))
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/zhigou-tests")
        }))
        val bitmap = requireNotNull(automation.takeScreenshot())
        resolver.openOutputStream(uri)!!.use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
