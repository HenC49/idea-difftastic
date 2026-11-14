package com.github.henc.difftastic.engine

/**
 * Converts UTF-8 byte offsets within a line to char offsets, as used by Kotlin/Java
 * strings and IntelliJ documents. difftastic (tree-sitter) reports byte offsets,
 * so multi-byte content such as Chinese comments needs this translation.
 */
object Utf8Offsets {
    /** Returns the char offset for [byteOffset], clamped to the line length. */
    fun byteToCharOffset(line: String, byteOffset: Int): Int {
        if (byteOffset <= 0) return 0
        var bytes = 0
        var index = 0
        while (index < line.length) {
            if (bytes >= byteOffset) return index
            val codePoint = line.codePointAt(index)
            bytes += when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            index += Character.charCount(codePoint)
        }
        return line.length
    }
}

/** Line utilities matching IntelliJ document line semantics (no phantom trailing line). */
object TextLines {
    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val parts = text.split('\n')
        return if (text.endsWith('\n')) parts.dropLast(1) else parts
    }

    fun count(text: String): Int = split(text).size

    /**
     * Start offsets of lines [0..lineCount]; element `i` is the offset of line `i`,
     * element `lineCount` is `text.length`. A changed block ending at line `end`
     * therefore includes its trailing newline, matching IntelliJ fragment semantics.
     */
    fun startOffsets(text: String, lineCount: Int): IntArray {
        val offsets = IntArray(lineCount + 1)
        offsets[0] = 0
        var offset = 0
        for (i in 0 until lineCount) {
            val next = text.indexOf('\n', offset)
            offset = if (next == -1) text.length else next + 1
            offsets[i + 1] = offset
        }
        // A file not ending with '\n' has its last line ending at text.length already.
        if (lineCount > 0) offsets[lineCount] = text.length
        return offsets
    }
}
