package com.sk5416.zhigou

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*

/** Queries Chromium's rendered document, without JavaScript or reading the source asset. */
object ReaderProbe {
    fun assertText(scenario: ActivityScenario<MainActivity>, text: String) {
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        val done = CountDownLatch(1)
        var matches = 0
        var before = 0
        var height = 0
        var web: WebView? = null
        scenario.onActivity { activity ->
            web = requireNotNull(find(activity.window.decorView))
            before = web!!.scrollY; height = web!!.height
            web!!.setFindListener { _, count, complete -> if (complete) { matches = count; done.countDown() } }
            web!!.findAllAsync(text)
        }
        assertTrue("native rendered-document search must complete: $text", done.await(15, TimeUnit.SECONDS))
        assertTrue("rendered document must contain $text", matches > 0)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity {
            // Native find may reveal a line just below the heading; reject a distant/wrong anchor.
            val delta = kotlin.math.abs(web!!.scrollY - before)
            assertTrue("search anchor must be within one viewport of $text (delta=$delta, height=$height)", delta <= height)
            println("READER_NATIVE_TEXT text=$text matches=$matches delta=$delta height=$height")
            web!!.setFindListener(null)
            web!!.clearMatches()
        }
    }
}
