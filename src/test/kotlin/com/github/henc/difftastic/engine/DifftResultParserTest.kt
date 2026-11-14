package com.github.henc.difftastic.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifftResultParserTest {
    private fun resource(name: String): String =
        javaClass.getResourceAsStream(name)!!.readBytes().decodeToString()

    @Test
    fun `changed file with novel atoms on both sides`() {
        val left = "fun main() {\n    println(\"hello\")\n}"
        val right = "fun main() {\n    println(\"world\")\n}"
        // difft emits 0-based line numbers everywhere (verified against 0.72.0).
        val json = """
            {
              "aligned_lines": [[0,0],[1,1],[2,2]],
              "chunks": [[
                {"lhs": {"line_number": 1, "changes": [
                  {"start": 13, "end": 18, "content": "hello", "highlight": "string"}
                ]},
                 "rhs": {"line_number": 1, "changes": [
                  {"start": 13, "end": 18, "content": "world", "highlight": "string"}
                ]}}
              ]],
              "language": "Kotlin",
              "path": "left.kt",
              "status": "changed"
            }
        """.trimIndent()

        val result = DifftResultParser.parse(json, left, right) as DifftResult.Changed

        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), result.alignment)
        assertEquals(2, result.spans.size)
        assertEquals(DifftSpan(DifftSide.LEFT, 1, 13, 18, DifftHighlight.STRING), result.spans[0])
        assertEquals(DifftSpan(DifftSide.RIGHT, 1, 13, 18, DifftHighlight.STRING), result.spans[1])
        assertEquals("Kotlin", result.language)
    }

    @Test
    fun `utf8 byte offsets are converted to char offsets`() {
        // Line "// 注释一": "// " is 3 bytes, each CJK char 3 bytes. 注释一 spans bytes 3..12.
        val left = "// 注释一"
        val right = "// 注释二"
        val json = """
            {
              "aligned_lines": [[0,0]],
              "chunks": [[
                {"lhs": {"line_number": 0, "changes": [
                  {"start": 3, "end": 12, "content": "注释一", "highlight": "comment"}
                ]},
                 "rhs": {"line_number": 0, "changes": [
                  {"start": 3, "end": 12, "content": "注释二", "highlight": "comment"}
                ]}}
              ]],
              "language": "Kotlin",
              "path": "x.kt",
              "status": "changed"
            }
        """.trimIndent()

        val result = DifftResultParser.parse(json, left, right) as DifftResult.Changed

        assertEquals(DifftSpan(DifftSide.LEFT, 0, 3, 6, DifftHighlight.COMMENT), result.spans[0])
        assertEquals(DifftSpan(DifftSide.RIGHT, 0, 3, 6, DifftHighlight.COMMENT), result.spans[1])
    }

    @Test
    fun `unchanged status`() {
        val result = DifftResultParser.parse("""{"language":"Kotlin","path":"x","status":"unchanged"}""", "a", "a")
        assertEquals(DifftResult.Unchanged, result)
    }

    @Test
    fun `created and deleted have no alignment`() {
        val created = DifftResultParser.parse("""{"language":"Kotlin","path":"x","status":"created"}""", "", "a")
        assertEquals(null, (created as DifftResult.Changed).alignment)
        assertEquals(emptyList<DifftSpan>(), created.spans)

        val deleted = DifftResultParser.parse("""{"language":"Kotlin","path":"x","status":"deleted"}""", "a", "")
        assertEquals(null, (deleted as DifftResult.Changed).alignment)
    }

    @Test
    fun `phantom trailing line numbers are dropped per side`() {
        // Left "a\nb" has 2 lines (indices 0..1); difft reports a third trailing entry.
        val json = """
            {
              "aligned_lines": [[0,0],[1,1],[2,2]],
              "chunks": [],
              "language": "Text",
              "path": "x",
              "status": "changed"
            }
        """.trimIndent()
        val result = DifftResultParser.parse(json, "a\nb", "a\nb\nc") as DifftResult.Changed
        assertEquals(listOf(0 to 0, 1 to 1, null to 2), result.alignment)
    }

    @Test
    fun `spans are sorted deterministically regardless of chunk order`() {
        val json = """
            {
              "aligned_lines": [[0,0],[1,1],[2,2]],
              "chunks": [[
                {"rhs": {"line_number": 2, "changes": [{"start": 0, "end": 1, "highlight": "normal"}]}},
                {"rhs": {"line_number": 1, "changes": [{"start": 0, "end": 1, "highlight": "normal"}]}}
              ]],
              "language": "Text",
              "path": "x",
              "status": "changed"
            }
        """.trimIndent()
        val result = DifftResultParser.parse(json, "a\nb\nc", "a\nb\nc") as DifftResult.Changed
        assertEquals(listOf(1, 2), result.spans.map { it.line })
    }

    @Test
    fun `changed without aligned_lines is unsupported`() {
        val json = """{"language":"Text","path":"x","status":"changed"}"""
        val result = DifftResultParser.parse(json, "a", "b")
        assertTrue(result is DifftResult.Unsupported)
    }

    @Test
    fun `unknown status is unsupported`() {
        val result = DifftResultParser.parse("""{"status":"weird"}""", "a", "b")
        assertTrue(result is DifftResult.Unsupported)
    }

    @Test
    fun `array output is tolerated`() {
        val json = """[{"aligned_lines":[[0,0]],"chunks":[],"language":"Text","path":"x","status":"changed"}]"""
        val result = DifftResultParser.parse(json, "a", "a") as DifftResult.Changed
        assertEquals(listOf(0 to 0), result.alignment)
    }

    @Test
    fun `unknown highlight maps to normal`() {
        assertEquals(DifftHighlight.NORMAL, DifftHighlight.fromWire("nonsense"))
        assertEquals(DifftHighlight.NORMAL, DifftHighlight.fromWire(null))
        assertEquals(DifftHighlight.KEYWORD, DifftHighlight.fromWire("keyword"))
        assertEquals(DifftHighlight.TREE_SITTER_ERROR, DifftHighlight.fromWire("tree_sitter_error"))
    }

    /**
     * Regression against real `difft --display json` output (difftastic 0.72.0) for a
     * Kotlin file whose expression body was collapsed and strings/comments rewritten.
     */
    @Test
    fun `real difft 0_72 output parses end to end`() {
        val json = resource("/difftastic/kotlin-real.json")
        val left = """
            package demo

            // 计算器工具
            object Calc {
                fun add(a: Int, b: Int): Int {
                    return a + b
                }

                fun verboseDescription(): String {
                    return "a simple calculator"
                }
            }
        """.trimIndent() + "\n"
        val right = """
            package demo

            // 计算器工具箱
            object Calc {
                fun add(a: Int, b: Int): Int = a + b

                fun verboseDescription(): String {
                    return "a fancy calculator"
                }
            }
        """.trimIndent() + "\n"

        val result = DifftResultParser.parse(json, left, right) as DifftResult.Changed

        assertEquals("Kotlin", result.language)

        // 12 real lines per the alignment; deleted lines 5 and 6; the phantom trailing
        // pair [12, 10] is dropped.
        val alignment = result.alignment!!
        assertEquals(12, alignment.size)
        assertEquals(5 to null, alignment[5])
        assertEquals(6 to null, alignment[6])
        assertEquals(7 to 5, alignment[7])
        assertEquals(11 to 9, alignment[11])

        // "// 计算器工具" is 18 bytes / 8 chars; the whole line is novel.
        assertEquals(DifftSpan(DifftSide.LEFT, 2, 0, 8, DifftHighlight.COMMENT), result.spans.first())
        // "// 计算器工具箱" is 21 bytes / 9 chars. Spans are sorted left side first.
        assertEquals(
            DifftSpan(DifftSide.RIGHT, 2, 0, 9, DifftHighlight.COMMENT),
            result.spans.first { it.side == DifftSide.RIGHT },
        )
        // ASCII atoms keep byte==char offsets: `{` on lhs line 4 and `=` on rhs line 4.
        assertTrue(result.spans.contains(DifftSpan(DifftSide.LEFT, 4, 33, 34, DifftHighlight.DELIMITER)))
        assertTrue(result.spans.contains(DifftSpan(DifftSide.RIGHT, 4, 33, 34, DifftHighlight.KEYWORD)))
        // All 7 atoms of the replaced string literal on rhs line 7 are novel.
        assertEquals(7, result.spans.count { it.side == DifftSide.RIGHT && it.line == 7 && it.highlight == DifftHighlight.STRING })
    }
}
