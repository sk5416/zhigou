package com.sk5416.zhigou

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sk5416.zhigou.data.Catalog
import com.sk5416.zhigou.data.Codec
import java.net.URI
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurriculumAssetsTest {
    @Test fun completeCurriculumIsPackagedOffline() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("svc wifi disable")
        device.executeShellCommand("svc data disable")
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val catalog = assets.open("catalog.json").bufferedReader(Charsets.UTF_8).use {
            Codec.json.decodeFromString<Catalog>(it.readText())
        }
        val expected = (0..72).map { "C%02d".format(it) }.toSet() + ('A'..'H').map { "A$it" }
        assertEquals(expected, catalog.chapters.map { it.id }.toSet())
        assertEquals(81, catalog.chapters.size)
        assertTrue(catalog.chapters.all { it.published })
        assertTrue(catalog.questions.size >= 584)
        catalog.chapters.forEach { chapter ->
            assertTrue("${chapter.id} body missing", chapter.body.isNotBlank())
            val html = assets.open(chapter.html).bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertTrue("${chapter.id} HTML missing", html.startsWith("<!doctype html>"))
            assertTrue("${chapter.id} title missing", html.contains("<h1"))
            assertTrue("${chapter.id} CSP missing", html.contains("Content-Security-Policy"))
            val base = URI("https://appassets.androidplatform.net/assets/${chapter.html}")
            Regex("<img[^>]+src=\"([^\"]+)\"").findAll(html).forEach { match ->
                val image = base.resolve(match.groupValues[1]).normalize()
                assertEquals("appassets.androidplatform.net", image.host)
                assertTrue(image.path.startsWith("/assets/"))
                assets.open(image.path.removePrefix("/assets/")).use { assertTrue(it.read() >= 0) }
            }
            if (chapter.id.startsWith("C")) {
                assertTrue("${chapter.id} questions missing", catalog.questions.count { it.chapterId == chapter.id } in 8..12)
            }
        }
        println("Packaged offline curriculum: ${catalog.chapters.size} units, ${catalog.questions.size} questions")
    }
}
