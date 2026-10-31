package com.sk5416.zhigou.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BackupFilesTest {
    private fun backup() = Backup(contentVersion = "中文课程", createdAt = 1, preferences = Preferences("dark", 22, true), readings = listOf(Reading("C24", bookmarked = true)), sessions = emptyList(), attempts = emptyList(), mistakes = emptyList())
    @Test fun utf8BackupRoundTripPreservesPreferencesAndRecords() {
        val expected = backup()
        assertEquals(expected, BackupFiles.decode(ByteArrayInputStream(BackupFiles.encode(expected))))
    }
    @Test fun invalidDocumentDoesNotEchoItsContents() {
        val secret = "PRIVATE-FIXTURE-NOT-A-REAL-SECRET"
        try { BackupFiles.decode(ByteArrayInputStream("{\"$secret\":broken}".toByteArray())); fail() }
        catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("未修改当前数据"))
            assertFalse(e.message!!.contains(secret))
        }
    }
    @Test fun oversizedInputIsRejectedBeforeJsonDecoding() {
        val stream = object : InputStream() {
            var remaining = BackupFiles.MAX_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 32 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, length); bytes.fill(32, offset, offset + count); remaining -= count; return count
            }
        }
        try { BackupFiles.decode(stream); fail() }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("20MB")) }
    }
    @Test fun interruptedInputCannotBecomeAValidBackup() {
        val failure = IOException("fixture disconnected")
        try { BackupFiles.decode(object : InputStream() { override fun read(): Int = throw failure }); fail() }
        catch (e: IOException) { assertSame(failure, e) }
    }
}
