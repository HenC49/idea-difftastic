package com.github.henc.difftastic.engine

enum class DifftSide { LEFT, RIGHT }

/** Syntax class of a novel atom, mirroring difftastic's JSON highlight names. */
enum class DifftHighlight {
    DELIMITER,
    NORMAL,
    STRING,
    TYPE,
    COMMENT,
    KEYWORD,
    TREE_SITTER_ERROR,
    ;

    companion object {
        fun fromWire(value: String?): DifftHighlight = when (value) {
            "delimiter" -> DELIMITER
            "string" -> STRING
            "type" -> TYPE
            "comment" -> COMMENT
            "keyword" -> KEYWORD
            "tree_sitter_error" -> TREE_SITTER_ERROR
            else -> NORMAL
        }
    }
}

/** A novel (added or changed) atom range within one line, in char offsets. */
data class DifftSpan(
    val side: DifftSide,
    /** 0-based line index. */
    val line: Int,
    /** Char offset within the line, inclusive. */
    val start: Int,
    /** Char offset within the line, exclusive. */
    val end: Int,
    val highlight: DifftHighlight,
)

/**
 * Normalized difft result, ready for fragment building.
 */
sealed class DifftResult {
    /** No differences. */
    object Unchanged : DifftResult()

    data class Changed(
        /**
         * Full-file line alignment as (left, right) 0-based indices; null entries are
         * unmatched lines. Null alignment means one side is empty (created/deleted file).
         */
        val alignment: List<Pair<Int?, Int?>>?,
        /** Sorted novel atoms; see [DifftResultParser]. */
        val spans: List<DifftSpan>,
        val language: String?,
    ) : DifftResult()

    /** difft could not produce a usable result; the caller should fall back. */
    data class Unsupported(val reason: String) : DifftResult()
}
