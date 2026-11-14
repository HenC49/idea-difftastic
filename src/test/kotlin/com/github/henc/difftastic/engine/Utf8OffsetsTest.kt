package com.github.henc.difftastic.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class Utf8OffsetsTest {
    @Test
    fun `ascii is identity`() {
        assertEquals(0, Utf8Offsets.byteToCharOffset("hello", 0))
        assertEquals(4, Utf8Offsets.byteToCharOffset("hello", 4))
        assertEquals(5, Utf8Offsets.byteToCharOffset("hello", 5))
    }

    @Test
    fun `multibyte chars consume more bytes`() {
        // 3 Chinese chars + 'a': bytes 3+3+1=7, chars 4.
        val line = "注释a"
        assertEquals(0, Utf8Offsets.byteToCharOffset(line, 0))
        assertEquals(1, Utf8Offsets.byteToCharOffset(line, 3))
        assertEquals(2, Utf8Offsets.byteToCharOffset(line, 6))
        assertEquals(3, Utf8Offsets.byteToCharOffset(line, 7))
    }

    @Test
    fun `out of range offsets clamp to line length`() {
        assertEquals(3, Utf8Offsets.byteToCharOffset("注释a", 100))
        assertEquals(0, Utf8Offsets.byteToCharOffset("注释a", -1))
    }
}

class TextLinesTest {
    @Test
    fun `splits without phantom trailing line`() {
        assertEquals(listOf("a", "b"), TextLines.split("a\nb"))
        assertEquals(listOf("a"), TextLines.split("a\n"))
        assertEquals(listOf<String>(), TextLines.split(""))
        assertEquals(listOf(""), TextLines.split("\n"))
    }

    @Test
    fun `start offsets include trailing newline of last line`() {
        assertEquals(0, TextLines.startOffsets("a\nb", 2)[0])
        assertEquals(2, TextLines.startOffsets("a\nb", 2)[1])
        assertEquals(3, TextLines.startOffsets("a\nb", 2)[2])
        // "a\nb" has no trailing newline: last line ends at text end.
        assertEquals(listOf(0, 2, 3), TextLines.startOffsets("a\nb", 2).toList())
        assertEquals(listOf(0, 2), TextLines.startOffsets("a\n", 1).toList())
        assertEquals(listOf(0), TextLines.startOffsets("", 0).toList())
    }
}
