package com.zero.universalir

import com.zero.universalir.data.codes.CodeLibrary
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLibraryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `index json is read as plain text`() {
        val text = """
            {"version":1,"sources":[],"entries":[
              {"id":"e1","kind":"flipper","deviceType":"tv","brand":"B","model":"M","file":"e1.gz"}
            ]}
        """.trimIndent()
        val decoded = CodeLibrary.decodeAsset(text.toByteArray(Charsets.UTF_8))
        val index = json.decodeFromString(CodeLibrary.LibraryIndex.serializer(), decoded)
        assertEquals(1, index.entries.size)
        assertEquals("e1", index.entries[0].id)
    }

    @Test
    fun `bundled index parses strictly`() {
        val file = File("src/main/assets/codes/index.json")
        assertTrue("缺少内置索引：${file.absolutePath}", file.exists())
        val index = json.decodeFromString(
            CodeLibrary.LibraryIndex.serializer(),
            file.readText(Charsets.UTF_8),
        )
        assertTrue(index.entries.isNotEmpty())
        assertTrue(index.entries.any { it.minTemperature != null && it.maxTemperature != null })
    }

    @Test
    fun `gz code table is gunzipped`() {
        val text = "name: Power\ntype: parsed\nprotocol: NEC\naddress: 0x00\ncommand: 0x01\n"
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        assertEquals(text, CodeLibrary.decodeAsset(out.toByteArray()))
    }

    @Test
    fun `plain text that aapt already ungzipped is read directly`() {
        val text = "Filetype: IR signals file\nname: Power\nprotocol: Kaseikyo\n"
        assertEquals(text, CodeLibrary.decodeAsset(text.toByteArray(Charsets.UTF_8)))
    }
}
