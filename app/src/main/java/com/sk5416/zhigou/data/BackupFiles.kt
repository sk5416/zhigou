package com.sk5416.zhigou.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.serialization.SerializationException

/** Limits external documents before decoding; malformed input is never echoed to the UI. */
object BackupFiles {
    const val MAX_BYTES = 20 * 1024 * 1024
    fun encode(backup: Backup): ByteArray = Codec.json.encodeToString(backup).toByteArray(Charsets.UTF_8).also {
        require(it.size <= MAX_BYTES) { "备份超过当前20MB恢复上限，未写入文件" }
    }
    fun decode(input: InputStream): Backup {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "备份超过20MB上限，未修改当前数据" }
            output.write(buffer, 0, count)
        }
        return try { Codec.json.decodeFromString<Backup>(output.toString("UTF-8")) }
        catch (_: SerializationException) { throw IllegalArgumentException("文件不是有效的知构备份，未修改当前数据") }
    }
}
